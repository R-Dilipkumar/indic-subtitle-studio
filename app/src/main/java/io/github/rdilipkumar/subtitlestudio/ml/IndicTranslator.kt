package io.github.rdilipkumar.subtitlestudio.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.LongBuffer

/**
 * Offline translation with AI4Bharat's IndicTrans2 (distilled, 8-bit) on ONNX Runtime.
 * One direction per pack: Indian languages → English, or English → Indian languages.
 *
 * Greedy decoding with the key/value cache: the first step runs the plain decoder, later steps
 * the "with past" decoder fed with the previous step's cache.
 */
class IndicTranslator(dir: File, threads: Int = 4) : AutoCloseable {

  private val env = OrtEnvironment.getEnvironment()
  private val opts = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(threads)
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
  }
  private val encoder = env.createSession(File(dir, "encoder_model.onnx").path, opts)
  private val decoder = env.createSession(File(dir, "decoder_model.onnx").path, opts)
  private val decoderPast = env.createSession(File(dir, "decoder_with_past_model.onnx").path, opts)
  private val srcSpm = SentencePieceBpe.load(File(dir, "model.SRC"))
  private val srcVocab: Map<String, Int> = readDict(File(dir, "dict.SRC.json"))
  private val tgtVocab: Array<String?> = readDict(File(dir, "dict.TGT.json")).let { m ->
    arrayOfNulls<String>((m.values.maxOrNull() ?: 0) + 1).also { arr -> m.forEach { (k, v) -> arr[v] = k } }
  }

  /** Translates one sentence. */
  fun translate(text: String, from: Lang, to: Lang, maxTokens: Int = 160): String {
    if (text.isBlank()) return ""
    val prepared = IndicTransText.prepare(text, from, to)
    val parts = prepared.split(' ', limit = 3)
    val pieces = listOf(parts[0], parts[1]) + srcSpm.encode(parts.getOrElse(2) { "" })
    val ids = LongArray(pieces.size + 1)
    for ((i, p) in pieces.withIndex()) ids[i] = (srcVocab[p] ?: UNK).toLong()
    ids[pieces.size] = EOS.toLong()
    val n = ids.size.coerceAtMost(MAX_SOURCE)
    val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(ids, 0, n), longArrayOf(1, n.toLong()))
    val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { 1L }), longArrayOf(1, n.toLong()))
    val out = ArrayList<Int>()
    try {
      encoder.run(mapOf("input_ids" to inputIds, "attention_mask" to mask)).use { encResult ->
        val hidden = encResult.get(0) as OnnxTensor
        // Step 0's result also owns the encoder part of the cache, so it lives until the end.
        var first: OrtSession.Result? = null
        var prev: OrtSession.Result? = null
        val encoderCache = HashMap<String, OnnxTensor>()
        var token = DECODER_START
        val limit = minOf(maxTokens, n * 3 + 10)
        try {
          for (step in 0 until limit) {
            val tok = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token.toLong())), longArrayOf(1, 1))
            val result = try {
              if (step == 0) {
                decoder.run(feed(decoder, tok, mask, hidden, null, encoderCache))
              } else {
                decoderPast.run(feed(decoderPast, tok, mask, hidden, prev, encoderCache))
              }
            } finally {
              tok.close()
            }
            if (step == 0) {
              first = result
              for ((name, value) in result) {
                if (name.contains("encoder") && value is OnnxTensor) encoderCache[name.replace("present", "past_key_values")] = value
              }
            }
            val next = argmaxLast(result.get(0) as OnnxTensor)
            if (prev != null && prev !== first) prev.close()
            prev = result
            if (next == EOS) break
            out.add(next)
            token = next
          }
        } finally {
          if (prev != null && prev !== first) prev.close()
          first?.close()
        }
      }
    } finally {
      inputIds.close()
      mask.close()
    }
    val decoded = out.filter { it > UNK }.joinToString("") { tgtVocab.getOrNull(it) ?: "" }
      .replace(SentencePieceBpe.SPACE, ' ')
    return IndicTransText.finish(decoded, to)
  }

  private fun feed(
    session: OrtSession,
    token: OnnxTensor,
    mask: OnnxTensor,
    hidden: OnnxTensor,
    prev: OrtSession.Result?,
    encoderCache: Map<String, OnnxTensor>,
  ): Map<String, OnnxTensorLike> {
    val m = HashMap<String, OnnxTensorLike>()
    val prevByName = HashMap<String, OnnxTensor>()
    prev?.forEach { (name, value) ->
      if (name.startsWith("present") && value is OnnxTensor) prevByName[name.replace("present", "past_key_values")] = value
    }
    for (name in session.inputNames) {
      when {
        name == "input_ids" -> m[name] = token
        name == "encoder_attention_mask" -> m[name] = mask
        name == "encoder_hidden_states" -> m[name] = hidden
        name.startsWith("past_key_values") -> {
          val v = prevByName[name] ?: encoderCache[name] ?: error("Translator model is missing $name")
          m[name] = v
        }
      }
    }
    return m
  }

  private fun argmaxLast(logits: OnnxTensor): Int {
    val shape = logits.info.shape
    val vocab = shape.last().toInt()
    val fb = logits.floatBuffer
    val start = fb.limit() - vocab
    var best = 0
    var bestV = Float.NEGATIVE_INFINITY
    for (i in 0 until vocab) {
      val v = fb.get(start + i)
      if (v > bestV) {
        bestV = v
        best = i
      }
    }
    return best
  }

  override fun close() {
    decoderPast.close()
    decoder.close()
    encoder.close()
    opts.close()
  }

  companion object {
    private const val UNK = 3
    private const val EOS = 2
    private const val DECODER_START = 2
    private const val MAX_SOURCE = 256

    private fun readDict(f: File): Map<String, Int> =
      Json.parseToJsonElement(f.readText()).jsonObject.mapValues { it.value.jsonPrimitive.int }
  }
}
