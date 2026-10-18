package io.github.rdilipkumar.subtitlestudio.ml

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.PriorityQueue

/**
 * A small SentencePiece BPE tokenizer: reads a ".model" file (protobuf) and splits text into
 * the same pieces the Python library would, so the translator sees what it was trained on.
 *
 * BPE in SentencePiece: start from single characters, then keep joining the neighbouring pair
 * whose joined piece has the highest score, until no pair is in the vocabulary.
 */
class SentencePieceBpe private constructor(
  private val scores: HashMap<String, Float>,
  private val addDummyPrefix: Boolean,
) {

  fun encode(text: String): List<String> {
    var s = Normalizer.normalize(text, Normalizer.Form.NFKC)
    s = s.replace(Regex("[\\p{Cc}&&[^\\t\\n]]"), "").trim().replace(Regex("\\s+"), " ")
    if (s.isEmpty()) return emptyList()
    if (addDummyPrefix) s = " $s"
    s = s.replace(' ', SPACE)

    // One symbol per code point.
    val syms = ArrayList<String>(s.length)
    var i = 0
    while (i < s.length) {
      val cp = s.codePointAt(i)
      val n = Character.charCount(cp)
      syms.add(s.substring(i, i + n))
      i += n
    }
    val n = syms.size
    val prev = IntArray(n) { it - 1 }
    val next = IntArray(n) { if (it + 1 < n) it + 1 else -1 }
    val alive = BooleanArray(n) { true }
    val text = syms.toTypedArray<String?>()

    data class Cand(val left: Int, val right: Int, val piece: String, val score: Float)
    val queue = PriorityQueue<Cand>(compareByDescending<Cand> { it.score }.thenBy { it.left })
    fun offer(l: Int, r: Int) {
      if (l < 0 || r < 0) return
      val piece = text[l]!! + text[r]!!
      val sc = scores[piece] ?: return
      queue.add(Cand(l, r, piece, sc))
    }
    for (k in 0 until n - 1) offer(k, k + 1)
    while (queue.isNotEmpty()) {
      val c = queue.poll()!!
      // Skip pairs that changed since they were queued.
      if (!alive[c.left] || !alive[c.right] || next[c.left] != c.right) continue
      if (text[c.left]!! + text[c.right]!! != c.piece) continue
      text[c.left] = c.piece
      alive[c.right] = false
      text[c.right] = null
      val rn = next[c.right]
      next[c.left] = rn
      if (rn >= 0) prev[rn] = c.left
      offer(prev[c.left], c.left)
      offer(c.left, rn)
    }
    // The first symbol is never merged away (only right halves are), so walk from it.
    val out = ArrayList<String>()
    var k = 0
    while (k != -1) {
      out.add(text[k]!!)
      k = next[k]
    }
    return out
  }

  companion object {
    const val SPACE = '▁'

    fun load(file: File): SentencePieceBpe {
      val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
      val scores = HashMap<String, Float>(200_000)
      var addDummyPrefix = true
      while (buf.hasRemaining()) {
        val tag = readVarint(buf).toInt()
        val field = tag ushr 3
        val wire = tag and 7
        when {
          field == 1 && wire == 2 -> { // SentencePiece piece
            val len = readVarint(buf).toInt()
            val end = buf.position() + len
            var piece: String? = null
            var score = 0f
            var type = 1
            while (buf.position() < end) {
              val t = readVarint(buf).toInt()
              when (t ushr 3) {
                1 -> piece = readString(buf)
                2 -> score = buf.float
                3 -> type = readVarint(buf).toInt()
                else -> skip(buf, t and 7)
              }
            }
            buf.position(end)
            // Normal and user-defined pieces can be produced by merging.
            if (piece != null && (type == 1 || type == 4)) scores[piece] = score
          }
          field == 3 && wire == 2 -> { // NormalizerSpec
            val len = readVarint(buf).toInt()
            val end = buf.position() + len
            while (buf.position() < end) {
              val t = readVarint(buf).toInt()
              if (t ushr 3 == 3 && t and 7 == 0) addDummyPrefix = readVarint(buf) != 0L else skip(buf, t and 7)
            }
            buf.position(end)
          }
          else -> skip(buf, wire)
        }
      }
      return SentencePieceBpe(scores, addDummyPrefix)
    }

    /** Every piece of a ".model" file in id order (for turning model output ids back into text). */
    fun pieces(file: File): List<String> {
      val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
      val out = ArrayList<String>()
      while (buf.hasRemaining()) {
        val tag = readVarint(buf).toInt()
        if (tag ushr 3 == 1 && tag and 7 == 2) {
          val len = readVarint(buf).toInt()
          val end = buf.position() + len
          var piece = ""
          while (buf.position() < end) {
            val t = readVarint(buf).toInt()
            if (t ushr 3 == 1) piece = readString(buf) else skip(buf, t and 7)
          }
          buf.position(end)
          out.add(piece)
        } else {
          skip(buf, tag and 7)
        }
      }
      return out
    }

    private fun readVarint(b: ByteBuffer): Long {
      var result = 0L
      var shift = 0
      while (true) {
        val x = b.get().toInt() and 0xFF
        result = result or ((x and 0x7F).toLong() shl shift)
        if (x and 0x80 == 0) return result
        shift += 7
      }
    }

    private fun readString(b: ByteBuffer): String {
      val len = readVarint(b).toInt()
      val bytes = ByteArray(len)
      b.get(bytes)
      return String(bytes, Charsets.UTF_8)
    }

    private fun skip(b: ByteBuffer, wire: Int) {
      when (wire) {
        0 -> readVarint(b)
        1 -> b.position(b.position() + 8)
        2 -> {
          val len = readVarint(b).toInt()
          b.position(b.position() + len)
        }
        5 -> b.position(b.position() + 4)
        else -> throw IllegalStateException("Bad SentencePiece model file")
      }
    }
  }
}
