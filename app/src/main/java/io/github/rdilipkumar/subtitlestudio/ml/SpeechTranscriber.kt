package io.github.rdilipkumar.subtitlestudio.ml

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.github.rdilipkumar.subtitlestudio.core.phonetic.WordCheck
import io.github.rdilipkumar.subtitlestudio.core.timing.TimedWord
import io.github.rdilipkumar.subtitlestudio.core.timing.WordTiming
import io.github.rdilipkumar.subtitlestudio.media.AudioDecoder
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Offline speech to text with timing, using sherpa-onnx:
 *
 * 1. The Silero voice detector marks where people speak.
 * 2. Speech is cut into pieces at the speaker's pauses, at most [MAX_PIECE_SEC] long. The
 *    Indian-language Whisper models were trained on short sentences: given several sentences
 *    at once they stop early and drop the rest, so pieces stay sentence-sized.
 * 3. Whisper writes each piece.
 * 4. Word times: from Whisper's own token times when it gives them; otherwise words are
 *    shared out only over the speaking parts of the piece, by how long each word takes to say.
 */
class SpeechTranscriber(
  assets: AssetManager,
  modelDir: File,
  encoder: String,
  decoder: String,
  tokens: String,
  private val language: String,
  threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
) : AutoCloseable {

  data class Piece(val startSample: Int, val endSample: Int, val speech: List<IntRange>)

  data class PieceResult(val text: String, val words: List<TimedWord>, val suspicious: Boolean)

  private val vadAssets = assets
  private var tokenTimes = true
  private val recognizer: OfflineRecognizer

  init {
    fun config(withTimes: Boolean) = OfflineRecognizerConfig(
      featConfig = FeatureConfig(sampleRate = AudioDecoder.RATE, featureDim = 80),
      modelConfig = OfflineModelConfig(
        whisper = OfflineWhisperModelConfig(
          encoder = File(modelDir, encoder).path,
          decoder = File(modelDir, decoder).path,
          language = language,
          task = "transcribe",
          enableTokenTimestamps = withTimes,
        ),
        tokens = File(modelDir, tokens).path,
        numThreads = threads,
        debug = false,
        provider = "cpu",
        modelType = "whisper",
      ),
      decodingMethod = "greedy_search",
    )
    // Token times need extra outputs some converted models do not have; fall back without them.
    recognizer = try {
      OfflineRecognizer(config = config(true))
    } catch (e: Throwable) {
      Log.w(TAG, "Token timestamps unavailable, estimating word times", e)
      tokenTimes = false
      OfflineRecognizer(config = config(false))
    }
  }

  /** Finds the speaking parts and groups them into pieces for Whisper. */
  fun findPieces(samples: FloatArray): List<Piece> {
    val vad = Vad(
      assetManager = vadAssets,
      config = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
          model = "silero_vad.onnx",
          threshold = 0.45f,
          minSilenceDuration = 0.25f,
          minSpeechDuration = 0.2f,
          windowSize = VAD_WINDOW,
          maxSpeechDuration = MAX_PIECE_SEC,
        ),
        sampleRate = AudioDecoder.RATE,
        numThreads = 1,
        provider = "cpu",
      ),
    )
    val regions = ArrayList<IntRange>()
    try {
      var i = 0
      val window = FloatArray(VAD_WINDOW)
      while (i + VAD_WINDOW <= samples.size) {
        System.arraycopy(samples, i, window, 0, VAD_WINDOW)
        vad.acceptWaveform(window)
        while (!vad.empty()) {
          val seg = vad.front()
          regions.add(seg.start until seg.start + seg.samples.size)
          vad.pop()
        }
        i += VAD_WINDOW
      }
      vad.flush()
      while (!vad.empty()) {
        val seg = vad.front()
        regions.add(seg.start until seg.start + seg.samples.size)
        vad.pop()
      }
    } finally {
      vad.release()
    }
    return groupRegions(regions.sortedBy { it.first }, samples.size)
  }

  private fun groupRegions(regions: List<IntRange>, total: Int): List<Piece> {
    val rate = AudioDecoder.RATE
    val maxLen = (MAX_PIECE_SEC * rate).toInt()
    val maxGap = (JOIN_GAP_SEC * rate).toInt()
    val pieces = ArrayList<Piece>()
    var cur = ArrayList<IntRange>()
    for (r in regions) {
      val start = cur.firstOrNull()?.first
      if (start != null && (r.last - start > maxLen || r.first - cur.last().last > maxGap)) {
        pieces.add(toPiece(cur, total))
        cur = ArrayList()
      }
      cur.add(r)
    }
    if (cur.isNotEmpty()) pieces.add(toPiece(cur, total))
    return pieces
  }

  private fun toPiece(regions: List<IntRange>, total: Int): Piece {
    // A little audio before and after: the detector notices speech slightly late, and a
    // clipped first sound makes Whisper drop the whole first syllable.
    val pre = (0.35f * AudioDecoder.RATE).toInt()
    val post = (0.20f * AudioDecoder.RATE).toInt()
    return Piece(max(0, regions.first().first - pre), min(total, regions.last().last + 1 + post), regions)
  }

  /** Writes one piece and times its words (in milliseconds from the video start). */
  suspend fun transcribe(samples: FloatArray, piece: Piece): PieceResult {
    coroutineContext.ensureActive()
    // sherpa-onnx stops Whisper after 6 tokens per second of input. Tamil and other Indian
    // scripts need several times that, so the words got cut off. Whisper always looks at a
    // 30 s window anyway, so padding the piece with silence costs nothing and lifts the limit.
    val len = piece.endSample - piece.startSample
    val chunk = FloatArray(maxOf(len, PADDED_SAMPLES))
    System.arraycopy(samples, piece.startSample, chunk, 0, len)
    val stream = recognizer.createStream()
    val result = try {
      stream.acceptWaveform(chunk, AudioDecoder.RATE)
      recognizer.decode(stream)
      recognizer.getResult(stream)
    } finally {
      stream.release()
    }
    val (text, repeated) = cleanUp(result.text)
    if (text.isBlank()) return PieceResult("", emptyList(), false)

    val pieceStartMs = piece.startSample * 1000L / AudioDecoder.RATE
    val pieceEndMs = piece.endSample * 1000L / AudioDecoder.RATE
    val seconds = (piece.endSample - piece.startSample).toFloat() / AudioDecoder.RATE
    // Whisper sometimes invents text over music or silence: far too many letters for the time.
    val suspicious = repeated || text.length > seconds * 28 + 10
    val confidence = if (suspicious) 0.45f else 1f

    val fromTokens = if (tokenTimes && !repeated && result.timestamps.size == result.tokens.size && result.tokens.isNotEmpty()) {
      // Only usable when the tokens join back into exactly the text (no split letters).
      WordTiming.wordsFromTokens(
        text = result.text,
        tokens = result.tokens.toList(),
        tokenStartsMs = result.timestamps.map { pieceStartMs + (it * 1000).toLong() },
        endMs = pieceEndMs,
      )
    } else {
      null
    }
    val words = (
      fromTokens?.mapIndexed { i, (w, s, e) ->
        TimedWord(w, s.coerceIn(pieceStartMs, pieceEndMs), e.coerceIn(pieceStartMs, pieceEndMs), confidence, breakAfter = i == fromTokens.lastIndex)
      } ?: spreadOverSpeech(text, piece, confidence)
      ).map { if (WordCheck.looksBroken(it.text)) it.copy(confidence = minOf(it.confidence, 0.4f)) else it }
    return PieceResult(text, words, suspicious)
  }

  /** Shares words over the speaking parts only, and marks the pauses between them. */
  private fun spreadOverSpeech(text: String, piece: Piece, confidence: Float): List<TimedWord> {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val rate = AudioDecoder.RATE
    val regions = piece.speech
    val weights = words.map { WordTiming.weight(it) }
    val totalWeight = weights.sum()
    val lengths = regions.map { (it.last - it.first + 1).toFloat() }
    val totalLen = lengths.sum().coerceAtLeast(1f)
    // Which region each word falls in, by where its middle lands in "speaking time".
    val regionOf = IntArray(words.size)
    var acc = 0f
    for (i in words.indices) {
      val mid = (acc + weights[i] / 2) / totalWeight * totalLen
      acc += weights[i]
      var r = 0
      var start = 0f
      while (r < regions.lastIndex && mid > start + lengths[r]) {
        start += lengths[r]
        r++
      }
      regionOf[i] = r
    }
    val out = ArrayList<TimedWord>(words.size)
    for (r in regions.indices) {
      val idx = words.indices.filter { regionOf[it] == r }
      if (idx.isEmpty()) continue
      val reg = regions[r]
      val sMs = reg.first * 1000L / rate
      val eMs = (reg.last + 1) * 1000L / rate
      val times = WordTiming.spread(idx.map { weights[it] }, sMs, eMs)
      val gapAfter = regions.getOrNull(r + 1)?.let { (it.first - reg.last) * 1000L / rate } ?: Long.MAX_VALUE
      for ((k, wi) in idx.withIndex()) {
        out.add(TimedWord(words[wi], times[k].first, times[k].second, confidence, breakAfter = k == idx.lastIndex && gapAfter >= 400))
      }
    }
    return out
  }

  /** Removes Whisper's loops ("the the the the …") and stray tags. Returns (text, wasLooping). */
  private fun cleanUp(raw: String): Pair<String, Boolean> {
    val words = raw.replace(Regex("<\\|[^|]*\\|>"), " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "" to false
    var looped = false
    val out = ArrayList<String>(words.size)
    for (w in words) {
      val n = out.size
      if (n >= 2 && out[n - 1] == w && out[n - 2] == w) {
        looped = true
        continue
      }
      out.add(w)
    }
    // Repeating phrases of 2–4 words.
    for (len in 2..4) {
      var i = 0
      while (i + 3 * len <= out.size) {
        val a = out.subList(i, i + len)
        if (a == out.subList(i + len, i + 2 * len) && a == out.subList(i + 2 * len, i + 3 * len)) {
          repeat(len) { out.removeAt(i + 2 * len) }
          looped = true
        } else {
          i++
        }
      }
    }
    return out.joinToString(" ") to looped
  }

  override fun close() {
    recognizer.release()
  }

  companion object {
    private const val TAG = "SpeechTranscriber"
    private const val VAD_WINDOW = 512
    const val MAX_PIECE_SEC = 9f
    /** 28 s: just under sherpa-onnx's 29.5 s Whisper limit. */
    private const val PADDED_SAMPLES = 28 * AudioDecoder.RATE
    private const val JOIN_GAP_SEC = 0.45f
  }
}
