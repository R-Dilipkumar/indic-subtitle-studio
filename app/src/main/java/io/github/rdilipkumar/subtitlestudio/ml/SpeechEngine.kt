package io.github.rdilipkumar.subtitlestudio.ml

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.github.rdilipkumar.subtitlestudio.media.AudioDecoder
import java.io.File

/** What a speech model heard in one piece of sound. */
data class Heard(
  val text: String,
  /** The model's own pieces of the text with their start times, when it gives them. */
  val tokens: List<String> = emptyList(),
  val tokenStartsSec: List<Float> = emptyList(),
)

/** A speech model that writes down one short piece of sound (16 kHz mono, under 30 s). */
interface SpeechEngine : AutoCloseable {
  fun recognize(chunk: FloatArray): Heard

  /**
   * More letters than this per second of sound means the model is inventing text (it does
   * that over music or silence). Mixed Tamil and English text is longer for the same speech.
   */
  val maxLettersPerSecond: Int get() = 28
}

/** Whisper models on sherpa-onnx (one language each). */
class WhisperEngine(
  modelDir: File,
  encoder: String,
  decoder: String,
  tokens: String,
  language: String,
  threads: Int,
) : SpeechEngine {

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

  override fun recognize(chunk: FloatArray): Heard {
    // sherpa-onnx stops Whisper after 6 tokens per second of input. Tamil and other Indian
    // scripts need several times that, so the words got cut off. Whisper always looks at a
    // 30 s window anyway, so padding the piece with silence costs nothing and lifts the limit.
    val padded = if (chunk.size >= PADDED_SAMPLES) chunk else chunk.copyOf(PADDED_SAMPLES)
    val stream = recognizer.createStream()
    val result = try {
      stream.acceptWaveform(padded, AudioDecoder.RATE)
      recognizer.decode(stream)
      recognizer.getResult(stream)
    } finally {
      stream.release()
    }
    val timed = tokenTimes && result.timestamps.size == result.tokens.size
    return Heard(
      text = result.text,
      tokens = if (timed) result.tokens.toList() else emptyList(),
      tokenStartsSec = if (timed) result.timestamps.toList() else emptyList(),
    )
  }

  override fun close() {
    recognizer.release()
  }

  private companion object {
    const val TAG = "WhisperEngine"
    /** 28 s: just under sherpa-onnx's 29.5 s Whisper limit. */
    const val PADDED_SAMPLES = 28 * AudioDecoder.RATE
  }
}
