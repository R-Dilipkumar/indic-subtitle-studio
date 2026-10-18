package io.github.rdilipkumar.subtitlestudio.core.timing

import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Word timing helpers.
 *
 * The speech model tells us when each phrase starts and ends. When it cannot say when each
 * word inside starts, we share the phrase time out by how long each word takes to say,
 * measured by its vowel sounds ("vanakkam" has three, "naan" one long one).
 */
object WordTiming {

  private const val VOWELS = "aeiouAEIOU"

  /** How long a word takes to say, in rough syllable units. Works on any script. */
  fun weight(word: String): Float {
    val latin = Romanizer.romanize(word, PhoneticMode.CASUAL)
    var w = 0f
    var consonants = 0
    var i = 0
    while (i < latin.length) {
      val c = latin[i]
      if (c in VOWELS) {
        var j = i
        while (j < latin.length && latin[j] in VOWELS) j++
        w += if (j - i >= 2) 1.5f else 1f
        i = j
      } else {
        if (c.isLetter()) consonants++
        i++
      }
    }
    w += consonants * 0.12f
    // A comma or full stop is usually followed by a short breath.
    if (word.any { it in ",.!?।" }) w += 0.3f
    return max(w, 0.6f)
  }

  /**
   * Share [startMs]..[endMs] between words by their [weights]. Returns start/end pairs that
   * cover the whole span in order, with a tiny gap between words.
   */
  fun spread(weights: List<Float>, startMs: Long, endMs: Long): List<Pair<Long, Long>> {
    if (weights.isEmpty()) return emptyList()
    val span = max(endMs - startMs, weights.size * 40L)
    val total = weights.sum().coerceAtLeast(0.001f)
    val gap = if (weights.size > 1) minOf(30L, span / (weights.size * 10L)) else 0L
    val result = ArrayList<Pair<Long, Long>>(weights.size)
    var acc = 0f
    for ((i, w) in weights.withIndex()) {
      val s = startMs + (span * (acc / total)).roundToLong()
      acc += w
      val rawEnd = if (i == weights.lastIndex) startMs + span else startMs + (span * (acc / total)).roundToLong()
      val e = if (i == weights.lastIndex) rawEnd else max(s + 20, rawEnd - gap)
      result.add(s to e)
    }
    return result
  }

  fun spreadWords(words: List<String>, startMs: Long, endMs: Long): List<Pair<Long, Long>> =
    spread(words.map { weight(it) }, startMs, endMs)

  /**
   * Turns per-token times from the speech model into per-word times. Only works when the
   * tokens join back into exactly the text (the model sometimes splits a letter between two
   * tokens, which then cannot be mapped); returns null otherwise.
   */
  fun wordsFromTokens(
    text: String,
    tokens: List<String>,
    tokenStartsMs: List<Long>,
    endMs: Long,
  ): List<Triple<String, Long, Long>>? {
    if (tokens.isEmpty() || tokens.size != tokenStartsMs.size) return null
    val joined = tokens.joinToString("")
    if (joined.trim() != text.trim()) return null
    // Character position -> start time of the token holding it.
    val charStart = LongArray(joined.length)
    var pos = 0
    for ((k, tok) in tokens.withIndex()) {
      for (c in tok.indices) charStart[pos + c] = tokenStartsMs[k]
      pos += tok.length
    }
    val words = ArrayList<Triple<String, Long, Long>>()
    var i = 0
    while (i < joined.length) {
      while (i < joined.length && joined[i].isWhitespace()) i++
      if (i >= joined.length) break
      var j = i
      while (j < joined.length && !joined[j].isWhitespace()) j++
      words.add(Triple(joined.substring(i, j), charStart[i], 0L))
      i = j
    }
    if (words.isEmpty()) return null
    // Two words can start in the same token; push later ones along so words never overlap.
    var prevEnd = Long.MIN_VALUE
    return words.mapIndexed { idx, (w, s0, _) ->
      val s = max(s0, prevEnd)
      val e = max(s + 40, if (idx < words.lastIndex) words[idx + 1].second else endMs)
      prevEnd = e
      Triple(w, s, e)
    }
  }
}
