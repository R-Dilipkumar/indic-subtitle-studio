package io.github.rdilipkumar.subtitlestudio.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A growable float array without boxing. */
class FloatList(initial: Int = 16_000) {
  var data = FloatArray(initial)
    private set
  var size = 0
    private set

  fun add(v: Float) {
    if (size == data.size) data = data.copyOf(max(16, data.size * 2))
    data[size++] = v
  }

  fun toArray(): FloatArray = data.copyOf(size)
}

/**
 * Turns any audio rate into 16 kHz (what speech models need): a low-pass filter removes sounds
 * too high for 16 kHz, then samples are picked by linear interpolation. Works on a stream, so
 * long videos never sit in memory at their full rate.
 */
class StreamingResampler(srcRate: Int, private val dstRate: Int = 16_000) {
  private val step = srcRate.toDouble() / dstRate
  private val taps: FloatArray = if (srcRate > dstRate) lowPass(dstRate * 0.45, srcRate.toDouble(), 31) else floatArrayOf(1f)
  private val hist = FloatArray(taps.size)
  private var histPos = 0
  private var n = 0L
  private var nextOut = 0.0
  private var prev = 0f

  fun push(x: Float, out: FloatList) {
    hist[histPos] = x
    var acc = 0f
    var idx = histPos
    for (k in taps.indices) {
      acc += taps[k] * hist[idx]
      idx = if (idx == 0) hist.size - 1 else idx - 1
    }
    histPos = (histPos + 1) % hist.size
    val cur = acc
    // Output samples whose time falls in (n-1, n].
    while (nextOut <= n) {
      val frac = (nextOut - (n - 1)).toFloat()
      out.add(prev + (cur - prev) * frac)
      nextOut += step
    }
    prev = cur
    n++
  }

  private fun lowPass(cutoffHz: Double, rate: Double, count: Int): FloatArray {
    val fc = cutoffHz / rate
    val m = count - 1
    val h = FloatArray(count)
    var sum = 0.0
    for (i in 0 until count) {
      val x = i - m / 2.0
      val sinc = if (x == 0.0) 2 * fc else sin(2 * PI * fc * x) / (PI * x)
      val window = 0.54 - 0.46 * cos(2 * PI * i / m)
      h[i] = (sinc * window).toFloat()
      sum += h[i]
    }
    for (i in h.indices) h[i] = (h[i] / sum).toFloat()
    return h
  }
}

/** Reads a video's sound as 16 kHz mono floats in -1..1, using the phone's own decoders. */
object AudioDecoder {

  const val RATE = 16_000

  class NoAudioException : IOException("This video has no sound track")

  suspend fun decode(file: File, onProgress: (Float) -> Unit = {}): FloatArray = withContext(Dispatchers.Default) {
    val extractor = MediaExtractor()
    extractor.setDataSource(file.path)
    val track = (0 until extractor.trackCount).firstOrNull {
      extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: run {
      extractor.release()
      throw NoAudioException()
    }
    extractor.selectTrack(track)
    val format = extractor.getTrackFormat(track)
    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
    val codec = MediaCodec.createDecoderByType(mime)
    val out = FloatList(if (durationUs > 0) (durationUs / 1_000_000L * RATE + RATE).toInt().coerceAtMost(60_000_000) else 16_000 * 60)
    try {
      codec.configure(format, null, null, 0)
      codec.start()
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      var outputDone = false
      var channels = format.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 1)
      var rate = format.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
      // Phone decoders give 16-bit or float; raw PCM tracks (some cameras record 24-bit) pass
      // through as they are.
      var encoding = format.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
      var resampler = StreamingResampler(rate)
      var lastProgress = 0L
      while (!outputDone) {
        coroutineContext.ensureActive()
        if (!inputDone) {
          val inIdx = codec.dequeueInputBuffer(10_000)
          if (inIdx >= 0) {
            val buf = codec.getInputBuffer(inIdx)!!
            val n = extractor.readSampleData(buf, 0)
            if (n < 0) {
              codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val outIdx = codec.dequeueOutputBuffer(info, 10_000)
        when {
          outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val f = codec.outputFormat
            channels = f.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
            rate = f.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, rate)
            encoding = f.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            resampler = StreamingResampler(rate)
          }
          outIdx >= 0 -> {
            val buf = codec.getOutputBuffer(outIdx)!!
            buf.position(info.offset)
            buf.limit(info.offset + info.size)
            buf.order(ByteOrder.nativeOrder())
            val ch = max(1, channels)
            when (encoding) {
              AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = buf.asFloatBuffer()
                val frames = fb.remaining() / ch
                for (i in 0 until frames) {
                  var s = 0f
                  for (c in 0 until ch) s += fb.get()
                  resampler.push(s / ch, out)
                }
              }
              AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT -> {
                val bytes = when (encoding) {
                  AudioFormat.ENCODING_PCM_8BIT -> 1
                  AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                  else -> 4
                }
                val frames = buf.remaining() / (bytes * ch)
                for (i in 0 until frames) {
                  var s = 0f
                  for (c in 0 until ch) s += readSample(buf, bytes)
                  resampler.push(s / ch, out)
                }
              }
              else -> {
                val sb = buf.asShortBuffer()
                val frames = sb.remaining() / ch
                for (i in 0 until frames) {
                  var s = 0f
                  for (c in 0 until ch) s += sb.get()
                  resampler.push(s / (ch * 32768f), out)
                }
              }
            }
            codec.releaseOutputBuffer(outIdx, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            if (durationUs > 0 && info.presentationTimeUs - lastProgress > 500_000) {
              lastProgress = info.presentationTimeUs
              onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
            }
          }
        }
      }
    } finally {
      runCatching { codec.stop() }
      codec.release()
      extractor.release()
    }
    onProgress(1f)
    out.toArray()
  }

  /** One little-endian PCM sample of [bytes] bytes (8-bit is unsigned), as -1..1. */
  private fun readSample(b: java.nio.ByteBuffer, bytes: Int): Float = when (bytes) {
    1 -> ((b.get().toInt() and 0xFF) - 128) / 128f
    3 -> {
      val lo = b.get().toInt() and 0xFF
      val mid = b.get().toInt() and 0xFF
      val hi = b.get().toInt()
      ((hi shl 16) or (mid shl 8) or lo) / 8_388_608f
    }
    else -> b.int / 2_147_483_648f
  }

  private fun MediaFormat.getIntegerOr(key: String, fallback: Int): Int =
    if (containsKey(key)) getInteger(key) else fallback

  /** Loudness peaks for the timeline: one value (0..255) per 10 ms. */
  fun peaks(samples: FloatArray): ByteArray {
    val hop = RATE / 100
    val n = (samples.size + hop - 1) / hop
    val out = ByteArray(n)
    var globalMax = 1e-4f
    val raw = FloatArray(n)
    for (i in 0 until n) {
      var m = 0f
      val end = min(samples.size, (i + 1) * hop)
      for (j in i * hop until end) m = max(m, abs(samples[j]))
      raw[i] = m
      globalMax = max(globalMax, m)
    }
    for (i in 0 until n) {
      // Square-root scale so quiet speech still shows.
      out[i] = (kotlin.math.sqrt(raw[i] / globalMax) * 255f).toInt().coerceIn(0, 255).toByte()
    }
    return out
  }
}
