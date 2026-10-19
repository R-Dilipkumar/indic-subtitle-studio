package io.github.rdilipkumar.subtitlestudio.core.timing

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import kotlin.math.max

/**
 * Shares one sentence's translation between the captions that say it. Word order differs
 * between languages, so the cut follows speaking time: a caption spoken for twice as long gets
 * about twice as many translated words.
 */
object TranslationSplit {

  private val SPACES = Regex("\\s+")

  /** From the first word to the last (captions stay a moment longer on screen). */
  fun speechStart(c: Cue): Long = c.words.firstOrNull()?.startMs ?: c.startMs

  fun speechEnd(c: Cue): Long = max(c.words.lastOrNull()?.endMs ?: c.endMs, speechStart(c) + 1)

  fun words(text: String): List<String> = text.trim().split(SPACES).filter { it.isNotEmpty() }

  /**
   * One piece of [text] for each of [cues], in order. Every caption gets at least one word when
   * there are enough; with fewer words than captions a caption repeats the piece before it, so
   * the line never blinks off in the middle of a sentence.
   */
  fun share(text: String, cues: List<Cue>): List<String> {
    val tokens = words(text)
    val n = cues.size
    if (n == 0) return emptyList()
    if (n == 1 || tokens.isEmpty()) return List(n) { tokens.joinToString(" ") }
    val m = tokens.size

    val cueW = cues.map { (speechEnd(it) - speechStart(it)).toDouble().coerceAtLeast(1.0) }
    val tokW = tokens.map { WordTiming.weight(it).toDouble() }
    val totalCue = cueW.sum()
    val totalTok = tokW.sum()

    // cuts[i]: words given to captions 0..i together.
    val cuts = IntArray(n)
    cuts[n - 1] = m
    var j = 0
    var tokAcc = 0.0
    var acc = 0.0
    for (i in 0 until n - 1) {
      acc += cueW[i]
      val target = acc / totalCue * totalTok
      while (j < m && tokAcc + tokW[j] / 2 < target) {
        tokAcc += tokW[j]
        j++
      }
      val prev = if (i == 0) 0 else cuts[i - 1]
      val c = if (m >= n) j.coerceIn(prev + 1, m - (n - 1 - i)) else j.coerceIn(prev, m)
      cuts[i] = c
      while (j < c) tokAcc += tokW[j++]
      while (j > c) tokAcc -= tokW[--j]
    }

    val pieces = ArrayList<String>(n)
    var start = 0
    for (i in 0 until n) {
      pieces.add(tokens.subList(start, cuts[i]).joinToString(" "))
      start = cuts[i]
    }
    // Fewer words than captions: carry a piece over instead of leaving a gap.
    for (i in pieces.indices) if (pieces[i].isEmpty() && i > 0) pieces[i] = pieces[i - 1]
    val firstFull = pieces.firstOrNull { it.isNotEmpty() } ?: return pieces
    for (i in pieces.indices) if (pieces[i].isEmpty()) pieces[i] = firstFull else break
    return pieces
  }
}
