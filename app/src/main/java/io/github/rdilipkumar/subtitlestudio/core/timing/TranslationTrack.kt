package io.github.rdilipkumar.subtitlestudio.core.timing

import io.github.rdilipkumar.subtitlestudio.core.model.Cue

/**
 * The translation line cut to its own size, which can differ from the captions: Tamil one word
 * at a time with the English meaning a whole line at a time, or the other way round.
 *
 * Each caption's translation is spread over the time its words are spoken, then grouped exactly
 * like captions are ([CueBuilder]), never across the end of a sentence. The result is a list of
 * "translation captions" whose words are the translated words with their times.
 */
object TranslationTrack {

  /** Same as the translator's sentence rule: a pause this long ends a sentence. */
  private const val SENTENCE_PAUSE_MS = 450L

  fun build(cues: List<Cue>, size: CaptionLength): List<Cue> {
    val timed = ArrayList<TimedWord>()
    var i = 0
    while (i < cues.size) {
      val text = cues[i].translation.trim()
      // Neighbouring captions with the same translation (a carried-over piece, or an older
      // project that copied the sentence) show it once, across all of them.
      var k = i
      while (text.isNotEmpty() && k + 1 < cues.size && cues[k + 1].translation.trim() == text) k++
      if (text.isNotEmpty()) {
        val tokens = TranslationSplit.words(text)
        val times = WordTiming.spreadWords(tokens, TranslationSplit.speechStart(cues[i]), TranslationSplit.speechEnd(cues[k]))
        val ends = sentenceEndsAfter(cues, k)
        for ((x, tok) in tokens.withIndex()) {
          timed.add(TimedWord(tok, times[x].first, times[x].second, breakAfter = ends && x == tokens.lastIndex))
        }
      }
      i = k + 1
    }
    var n = 0
    return CueBuilder.build(timed, size, { it }, { "t${n++}" })
  }

  private fun sentenceEndsAfter(cues: List<Cue>, k: Int): Boolean {
    val c = cues[k]
    val next = cues.getOrNull(k + 1) ?: return true
    if (c.text.trimEnd().lastOrNull()?.let { it in ".!?।" } == true) return true
    return TranslationSplit.speechStart(next) - TranslationSplit.speechEnd(c) >= SENTENCE_PAUSE_MS
  }

  /** The translation piece on screen at [timeMs] while [cue] shows, or null for none. */
  fun at(chunks: List<Cue>, timeMs: Long, cue: Cue): Cue? {
    var best: Cue? = null
    for (ch in overlapping(chunks, cue)) {
      if (ch.startMs <= timeMs) best = ch else return best ?: ch
    }
    return best
  }

  /** Every piece shown while [cue] is spoken, joined: what a subtitle file can carry. */
  fun textDuring(chunks: List<Cue>, cue: Cue): String =
    overlapping(chunks, cue).joinToString(" ") { ch -> ch.words.joinToString(" ") { it.text } }

  /** Pieces whose words are said while [cue] is on screen, in order. */
  private fun overlapping(chunks: List<Cue>, cue: Cue): List<Cue> {
    val s = TranslationSplit.speechStart(cue)
    val e = TranslationSplit.speechEnd(cue)
    // Chunks are sorted and never overlap: find the first that may reach this caption.
    var lo = 0
    var hi = chunks.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (chunks[mid].endMs <= s) lo = mid + 1 else hi = mid
    }
    val out = ArrayList<Cue>(2)
    for (idx in lo until chunks.size) {
      val ch = chunks[idx]
      val ws = ch.words.firstOrNull()?.startMs ?: ch.startMs
      if (ws >= e) break
      val we = ch.words.lastOrNull()?.endMs ?: ch.endMs
      if (we > s) out.add(ch)
    }
    return out
  }
}
