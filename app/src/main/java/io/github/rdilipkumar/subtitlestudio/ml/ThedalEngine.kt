package io.github.rdilipkumar.subtitlestudio.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Thedal AED (by Badri0510): one model for Tamil, Tanglish and English speech. Unlike the
 * Tamil-only Whisper it writes English words in English letters ("lunch finish பண்ணிட்டு"), so
 * the English-letters line comes out as real Tanglish ("lunch finish pannittu").
 *
 * Three ONNX graphs: sound to features, features to the encoder's attention keys and values,
 * then one decoder step at a time with a key/value cache, always taking the likeliest token.
 */
class ThedalEngine(dir: File, threads: Int) : SpeechEngine {

  private val env = OrtEnvironment.getEnvironment()
  private val opts = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(threads)
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
  }
  private val frontend = env.createSession(File(dir, "frontend.onnx").path, opts)
  private val encoder = env.createSession(File(dir, "encoder.onnx").path, opts)
  private val decoder = env.createSession(File(dir, "decoder_step.onnx").path, opts)
  private val pieces: List<String> = SentencePieceBpe.pieces(File(dir, "tokenizer.model"))

  private val config: JsonObject = Json.parseToJsonElement(File(dir, "config.json").readText()).jsonObject
  private val spec = config.getValue("spec").jsonObject
  private val bos = config.int("bos")
  private val eos = config.int("eos")
  private val maxTokens = config.int("max_new_tokens")
  private val vocab = spec.int("vocab")
  private val layers = spec.int("layers")
  private val heads = spec.int("heads")
  private val headDim = spec.int("width") / heads

  override val maxLettersPerSecond: Int get() = 45

  override fun recognize(chunk: FloatArray): Heard {
    val ids = ArrayList<Int>()
    val audio = OnnxTensor.createTensor(env, FloatBuffer.wrap(chunk), longArrayOf(1, chunk.size.toLong()))
    val audioLen = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(chunk.size.toLong())), longArrayOf(1))
    try {
      frontend.run(mapOf("audio" to audio, "audio_lengths" to audioLen)).use { fe ->
        val features = fe.get("features").get() as OnnxTensor
        val featureLen = fe.get("feature_lengths").get() as OnnxTensor
        encoder.run(mapOf("features" to features, "feature_lengths" to featureLen), setOf("memory_lengths", "cross_k", "cross_v")).use { enc ->
          decode(
            enc.get("memory_lengths").get() as OnnxTensor,
            enc.get("cross_k").get() as OnnxTensor,
            enc.get("cross_v").get() as OnnxTensor,
            ids,
          )
        }
      }
    } finally {
      audio.close()
      audioLen.close()
    }
    return Heard(text = textOf(ids))
  }

  private fun decode(memoryLen: OnnxTensor, crossK: OnnxTensor, crossV: OnnxTensor, ids: MutableList<Int>) {
    // The cache starts empty: [layers, batch, heads, 0 tokens, head size].
    val emptyShape = longArrayOf(layers.toLong(), 1, heads.toLong(), 0, headDim.toLong())
    val emptyK = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyShape)
    val emptyV = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyShape)
    var pastK = emptyK
    var pastV = emptyV
    var last: OrtSession.Result? = null
    var token = bos
    try {
      for (position in 0 until maxTokens) {
        val tok = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token.toLong())), longArrayOf(1, 1))
        val pos = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(position.toLong())), longArrayOf(1))
        // No padding: every token fed so far is real.
        val padding = OnnxTensor.createTensor(env, arrayOf(BooleanArray(position + 1)))
        val step = try {
          decoder.run(
            mapOf(
              "token" to tok,
              "position" to pos,
              "cross_k" to crossK,
              "cross_v" to crossV,
              "memory_lengths" to memoryLen,
              "past_k" to pastK,
              "past_v" to pastV,
              "self_padding" to padding,
            ),
            setOf("logits", "present_k", "present_v"),
          )
        } finally {
          tok.close()
          pos.close()
          padding.close()
        }
        last?.close() // the cache it held has just been read
        last = step
        pastK = step.get("present_k").get() as OnnxTensor
        pastV = step.get("present_v").get() as OnnxTensor
        val next = argmaxLast(step.get("logits").get() as OnnxTensor)
        if (next == eos) break
        ids.add(next)
        token = next
      }
    } finally {
      last?.close()
      emptyK.close()
      emptyV.close()
    }
  }

  private fun argmaxLast(logits: OnnxTensor): Int {
    val size = logits.info.shape.last().toInt()
    val fb = logits.floatBuffer
    val start = fb.limit() - size
    var best = 0
    var bestV = Float.NEGATIVE_INFINITY
    for (i in 0 until size) {
      val v = fb.get(start + i)
      if (v > bestV) {
        bestV = v
        best = i
      }
    }
    return best
  }

  /** Ids back to text (special ids and the unknown piece are left out). */
  private fun textOf(ids: List<Int>): String =
    ids.filter { it in 1 until vocab && it < pieces.size }
      .joinToString("") { pieces[it] }
      .replace(SentencePieceBpe.SPACE, ' ')
      .replace(Regex("\\s+"), " ")
      .trim()

  override fun close() {
    decoder.close()
    encoder.close()
    frontend.close()
    opts.close()
  }

  private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
}
