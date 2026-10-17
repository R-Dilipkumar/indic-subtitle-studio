package io.github.rdilipkumar.subtitlestudio.core.timing

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Origin
import io.github.rdilipkumar.subtitlestudio.core.model.Word
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A word with its time, before it is put into a caption. */
data class TimedWord(
  val text: String,
  val startMs: Long,
  val endMs: Long,
  val confidence: Float = 1f,
  /** The speaker paused here (end of a speech piece), so a caption should end. */
  val breakAfter: Boolean = false,
)

/** How much text one caption holds. */
@kotlinx.serialization.Serializable
enum class CaptionLength(val label: String, val maxWords: Int, val maxChars: Int) {
  ONE_WORD("1 word", 1, 16),
  PUNCHY("2–3 words", 3, 22),
  LINE("One line", 6, 38),
  SENTENCE("Full sentence", 14, 76),
}

fun interface IdGen {
  fun next(): String
}

/**
 * Groups words into captions: never across a pause or a sentence end, never more than the
 * chosen size, and split evenly (7 words at 3 per caption become 3+2+2, not 3+3+1).
 */
object CueBuilder {

  private const val PAUSE_MS = 700L
  private const val LINGER_MS = 250L
  private const val MIN_SHOW_MS = 400L
  private const val GUARD_MS = 40L

  fun build(
    words: List<TimedWord>,
    length: CaptionLength,
    phonetic: (String) -> String,
    ids: IdGen,
    origin: Origin = Origin.AI,
  ): List<Cue> {
    val clean = words.filter { it.text.isNotBlank() }.sortedBy { it.startMs }
    if (clean.isEmpty()) return emptyList()

    val groups = ArrayList<List<TimedWord>>()
    for (phrase in splitPhrases(clean, length)) groups.addAll(balance(phrase, length, phonetic))

    val cues = groups.map { g ->
      Cue(
        id = ids.next(),
        startMs = g.first().startMs,
        endMs = g.last().endMs,
        words = g.map { w ->
          Word(
            id = ids.next(),
            text = w.text,
            phonetic = phonetic(w.text),
            startMs = w.startMs,
            endMs = w.endMs,
            confidence = w.confidence,
          )
        },
        origin = origin,
      )
    }
    return settleTimes(cues)
  }

  /** Lets each caption stay a moment after its last word, without running into the next. */
  fun settleTimes(cues: List<Cue>): List<Cue> {
    val out = ArrayList<Cue>(cues.size)
    for ((i, c) in cues.withIndex()) {
      val prevEnd = out.lastOrNull()?.endMs ?: 0L
      val nextStart = cues.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
      val start = max(c.startMs, prevEnd)
      val lastWordEnd = c.words.lastOrNull()?.endMs ?: c.endMs
      var end = max(lastWordEnd + LINGER_MS, start + MIN_SHOW_MS)
      end = min(end, nextStart - GUARD_MS)
      end = max(end, min(lastWordEnd, nextStart))
      if (end <= start) end = start + 1
      out.add(c.copy(startMs = start, endMs = end))
    }
    return out
  }

  private fun endsSentence(text: String): Boolean = text.trimEnd().lastOrNull()?.let { it in ".!?।|" } == true

  private fun splitPhrases(words: List<TimedWord>, length: CaptionLength): List<List<TimedWord>> {
    val phrases = ArrayList<List<TimedWord>>()
    var cur = ArrayList<TimedWord>()
    for ((i, w) in words.withIndex()) {
      cur.add(w)
      val next = words.getOrNull(i + 1)
      val pause = next != null && next.startMs - w.endMs > PAUSE_MS
      val comma = length.maxWords >= 6 && w.text.trimEnd().endsWith(",") && cur.size >= length.maxWords / 2
      if (next == null || w.breakAfter || pause || endsSentence(w.text) || comma) {
        phrases.add(cur)
        cur = ArrayList()
      }
    }
    if (cur.isNotEmpty()) phrases.add(cur)
    return phrases
  }

  private fun charsOf(w: TimedWord, phonetic: (String) -> String): Int =
    max(w.text.length, phonetic(w.text).length)

  /** Splits one phrase into the fewest captions that fit, with even sizes. */
  private fun balance(
    phrase: List<TimedWord>,
    length: CaptionLength,
    phonetic: (String) -> String,
  ): List<List<TimedWord>> {
    val n = phrase.size
    val chars = phrase.map { charsOf(it, phonetic) + 1 }
    val totalChars = chars.sum()
    var k = max(ceil(n / length.maxWords.toDouble()).toInt(), ceil(totalChars / length.maxChars.toDouble()).toInt())
    k = k.coerceIn(1, n)
    while (k < n) {
      val parts = splitEvenly(phrase, chars, k)
      if (parts.all { it.size <= length.maxWords && it.sumOf { w -> charsOf(w, phonetic) + 1 } <= length.maxChars + 6 }) {
        return parts
      }
      k++
    }
    return phrase.map { listOf(it) }
  }

  /** Cuts into [k] runs whose letter counts are as equal as possible. */
  private fun splitEvenly(words: List<TimedWord>, chars: List<Int>, k: Int): List<List<TimedWord>> {
    if (k <= 1) return listOf(words)
    val total = chars.sum().toDouble()
    val parts = ArrayList<List<TimedWord>>(k)
    var start = 0
    var acc = 0.0
    for (part in 1 until k) {
      val target = total * part / k
      var end = start + 1
      acc += chars[start]
      // Leave at least one word for each remaining part.
      val maxEnd = words.size - (k - part)
      while (end < maxEnd && acc + chars[end] / 2.0 < target) {
        acc += chars[end]
        end++
      }
      parts.add(words.subList(start, end))
      start = end
    }
    parts.add(words.subList(start, words.size))
    return parts
  }
}
