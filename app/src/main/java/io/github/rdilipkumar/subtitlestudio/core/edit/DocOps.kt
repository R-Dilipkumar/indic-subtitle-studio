package io.github.rdilipkumar.subtitlestudio.core.edit

import io.github.rdilipkumar.subtitlestudio.core.format.SubtitleFormats
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Origin
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.Transform
import io.github.rdilipkumar.subtitlestudio.core.model.TranslationState
import io.github.rdilipkumar.subtitlestudio.core.model.Word
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.core.timing.CueBuilder
import io.github.rdilipkumar.subtitlestudio.core.timing.IdGen
import io.github.rdilipkumar.subtitlestudio.core.timing.TimedWord
import io.github.rdilipkumar.subtitlestudio.core.timing.WordTiming
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Every subtitle edit, as a pure function from one [SubtitleDoc] to the next. The editor keeps
 * each result in its history, which is what makes undo, redo and "jump back" work.
 *
 * Rules every edit keeps: captions stay sorted, never overlap, and are never shorter than
 * [MIN_CUE_MS]; words stay inside their caption.
 */
object DocOps {

  const val MIN_CUE_MS = 200L
  private const val MIN_WORD_MS = 40L

  /** Turns native text into English letters (set up with the project's mode and dictionary). */
  fun interface Phonetic {
    fun of(text: String): String
  }

  // ---------------------------------------------------------------------------------------
  // Text

  /** Changes one word. Blank text deletes it; text with spaces becomes several words. */
  fun editWord(doc: SubtitleDoc, cueId: String, wordId: String, newText: String, ph: Phonetic, ids: IdGen): SubtitleDoc =
    updateCue(doc, cueId) { cue ->
      val idx = cue.words.indexOfFirst { it.id == wordId }
      if (idx < 0) return@updateCue cue
      val old = cue.words[idx]
      val parts = newText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
      if (parts.size == 1 && parts[0] == old.text) return@updateCue cue
      val replacement = if (parts.isEmpty()) {
        emptyList()
      } else {
        val times = WordTiming.spreadWords(parts, old.startMs, old.endMs)
        parts.mapIndexed { k, p ->
          Word(
            id = if (k == 0) old.id else ids.next(),
            text = p,
            phonetic = ph.of(p),
            startMs = times[k].first,
            endMs = times[k].second,
            confidence = 1f,
            edited = true,
          )
        }
      }
      val words = cue.words.toMutableList().apply {
        removeAt(idx)
        addAll(idx, replacement)
      }
      if (words.isEmpty()) null else cue.textChanged(words)
    }

  /** The user's own English-letters spelling for one word. */
  fun editWordPhonetic(doc: SubtitleDoc, cueId: String, wordId: String, phonetic: String): SubtitleDoc =
    updateCue(doc, cueId) { cue ->
      cue.copy(
        words = cue.words.map {
          if (it.id == wordId) it.copy(phonetic = phonetic.trim(), phoneticByUser = true) else it
        },
        edited = true,
      )
    }

  /** Retypes a whole caption. Word times are kept when the word count matches. */
  fun editCueText(doc: SubtitleDoc, cueId: String, newText: String, ph: Phonetic, ids: IdGen): SubtitleDoc =
    updateCue(doc, cueId) { cue ->
      val parts = newText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
      if (parts.isEmpty()) return@updateCue null
      if (parts == cue.words.map { it.text }) return@updateCue cue
      val words = if (parts.size == cue.words.size) {
        cue.words.mapIndexed { k, w ->
          if (w.text == parts[k]) w
          else w.copy(text = parts[k], phonetic = ph.of(parts[k]), phoneticByUser = false, edited = true, confidence = 1f)
        }
      } else {
        val from = cue.words.firstOrNull()?.startMs ?: cue.startMs
        val to = cue.words.lastOrNull()?.endMs ?: cue.endMs
        val times = WordTiming.spreadWords(parts, from, to)
        parts.mapIndexed { k, p ->
          val keep = cue.words.getOrNull(k)
          Word(
            id = keep?.id ?: ids.next(),
            text = p,
            phonetic = if (keep != null && keep.text == p) keep.phonetic else ph.of(p),
            startMs = times[k].first,
            endMs = times[k].second,
            edited = keep?.text != p,
          )
        }
      }
      cue.textChanged(words)
    }

  fun editTranslation(doc: SubtitleDoc, cueId: String, text: String): SubtitleDoc =
    updateCue(doc, cueId) { cue ->
      val t = text.trim()
      cue.copy(
        translation = t,
        translationState = if (t.isEmpty()) TranslationState.NONE else TranslationState.USER,
        edited = true,
      )
    }

  /** Sets machine translations (from the offline translator) for many captions at once. */
  fun setAutoTranslations(doc: SubtitleDoc, byCueId: Map<String, String>): SubtitleDoc =
    doc.copy(
      cues = doc.cues.map { c ->
        val t = byCueId[c.id] ?: return@map c
        c.copy(translation = t.trim(), translationState = TranslationState.AUTO)
      },
    )

  /** Rewrites every English-letters word the user has not typed by hand. */
  fun regeneratePhonetics(doc: SubtitleDoc, ph: Phonetic): SubtitleDoc =
    doc.copy(
      cues = doc.cues.map { c ->
        c.copy(words = c.words.map { w -> if (w.phoneticByUser) w else w.copy(phonetic = ph.of(w.text)) })
      },
    )

  /** Find and replace inside words, in every caption. Returns the new doc and how many changed. */
  fun replaceAll(doc: SubtitleDoc, find: String, replace: String, ph: Phonetic): Pair<SubtitleDoc, Int> {
    if (find.isEmpty()) return doc to 0
    var count = 0
    val cues = doc.cues.mapNotNull { c ->
      if (c.words.none { it.text.contains(find) }) return@mapNotNull c
      val words = c.words.mapNotNull { w ->
        if (!w.text.contains(find)) return@mapNotNull w
        count++
        val t = w.text.replace(find, replace).trim()
        if (t.isEmpty()) null else w.copy(text = t, phonetic = ph.of(t), phoneticByUser = false, edited = true, confidence = 1f)
      }
      if (words.isEmpty()) null else c.textChanged(words)
    }
    return doc.copy(cues = cues) to count
  }

  // ---------------------------------------------------------------------------------------
  // Structure

  /**
   * Splits a caption after word [afterWordIndex]. A caption too short for two halves may grow
   * into free time after it, but never into the next caption; with no room it is left alone.
   */
  fun splitCue(doc: SubtitleDoc, cueId: String, afterWordIndex: Int, ids: IdGen): SubtitleDoc {
    val i = doc.indexOf(cueId)
    if (i < 0) return doc
    val cue = doc.cues[i]
    if (afterWordIndex !in 0 until cue.words.lastIndex) return doc
    val nextStart = doc.cues.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
    val end = max(cue.endMs, min(nextStart, cue.startMs + 2 * MIN_CUE_MS))
    if (end - cue.startMs < 2 * MIN_CUE_MS) return doc
    val a = cue.words[afterWordIndex]
    val b = cue.words[afterWordIndex + 1]
    val gapMid = if (b.startMs > a.endMs) (a.endMs + b.startMs) / 2 else a.endMs
    val cut = gapMid.coerceIn(cue.startMs + MIN_CUE_MS, end - MIN_CUE_MS)
    val stale = if (cue.translation.isNotEmpty()) TranslationState.STALE else TranslationState.NONE
    val first = cue.copy(
      endMs = cut,
      words = cue.words.subList(0, afterWordIndex + 1),
      translationState = stale,
      edited = true,
    )
    val second = cue.copy(
      id = ids.next(),
      startMs = cut,
      endMs = end,
      words = cue.words.subList(afterWordIndex + 1, cue.words.size),
      translation = "",
      translationState = stale,
      edited = true,
    )
    return doc.copy(cues = doc.cues.toMutableList().apply { set(i, clampWords(first)); add(i + 1, clampWords(second)) })
  }

  /** Splits at the word gap nearest to [timeMs]. */
  fun splitCueAt(doc: SubtitleDoc, cueId: String, timeMs: Long, ids: IdGen): SubtitleDoc {
    val cue = doc.cue(cueId) ?: return doc
    if (cue.words.size < 2) return doc
    var best = 0
    var bestDist = Long.MAX_VALUE
    for (k in 0 until cue.words.lastIndex) {
      val gapMid = (cue.words[k].endMs + cue.words[k + 1].startMs) / 2
      val d = kotlin.math.abs(gapMid - timeMs)
      if (d < bestDist) {
        bestDist = d
        best = k
      }
    }
    return splitCue(doc, cueId, best, ids)
  }

  /** Joins a caption with the one after it. */
  fun mergeWithNext(doc: SubtitleDoc, cueId: String): SubtitleDoc {
    val i = doc.indexOf(cueId)
    if (i < 0 || i >= doc.cues.lastIndex) return doc
    val a = doc.cues[i]
    val b = doc.cues[i + 1]
    val translation = listOf(a.translation, b.translation).filter { it.isNotBlank() }.joinToString(" ")
    val merged = a.copy(
      endMs = b.endMs,
      words = a.words + b.words,
      translation = translation,
      translationState = if (translation.isEmpty()) TranslationState.NONE else TranslationState.STALE,
      origin = if (a.origin == b.origin) a.origin else Origin.USER,
      edited = true,
    )
    return doc.copy(cues = doc.cues.toMutableList().apply { removeAt(i + 1); set(i, merged) })
  }

  fun deleteCue(doc: SubtitleDoc, cueId: String): SubtitleDoc =
    doc.copy(cues = doc.cues.filterNot { it.id == cueId })

  /**
   * Adds a new caption in the free space at [atMs]. Returns the doc unchanged when there is
   * no room (less than [MIN_CUE_MS] free).
   */
  fun insertCue(
    doc: SubtitleDoc,
    atMs: Long,
    text: String,
    ph: Phonetic,
    ids: IdGen,
    videoDurationMs: Long,
    lengthMs: Long = 1500,
  ): SubtitleDoc {
    val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return doc
    if (doc.cueAt(atMs) != null) return doc
    val prevEnd = doc.cues.lastOrNull { it.endMs <= atMs }?.endMs ?: 0L
    val nextStart = doc.cues.firstOrNull { it.startMs > atMs }?.startMs ?: max(videoDurationMs, atMs + lengthMs)
    val start = max(atMs, prevEnd)
    val end = min(start + lengthMs, nextStart)
    if (end - start < MIN_CUE_MS) return doc
    val times = WordTiming.spreadWords(parts, start, end)
    val cue = Cue(
      id = ids.next(),
      startMs = start,
      endMs = end,
      words = parts.mapIndexed { k, p ->
        Word(ids.next(), p, ph.of(p), times[k].first, times[k].second, edited = true)
      },
      origin = Origin.USER,
      edited = true,
    )
    return doc.copy(cues = (doc.cues + cue).sortedBy { it.startMs })
  }

  /**
   * Rebuilds all captions to a new size (e.g. "2–3 words" for Reels), keeping every word and
   * its fixes. Translations no longer match the new captions, so they are cleared.
   */
  fun regroup(doc: SubtitleDoc, length: CaptionLength, ids: IdGen): SubtitleDoc {
    // Same filter and (stable) order as CueBuilder.build, so its words line up one to one.
    val all = doc.cues.flatMap { c -> c.words.mapIndexed { k, w -> Triple(c, k, w) } }
      .filter { it.third.text.isNotBlank() }
      .sortedBy { it.third.startMs }
    if (all.isEmpty()) return doc
    val timed = all.map { (c, k, w) ->
      TimedWord(w.text, w.startMs, w.endMs, w.confidence, breakAfter = k == c.words.lastIndex && c.origin == Origin.USER)
    }
    val rebuilt = CueBuilder.build(timed, length, { it }, ids)
    // Put the original word objects back, in order (keeps ids, spellings and edit marks).
    var next = 0
    val cues = rebuilt.map { c ->
      val words = c.words.map { all[next++].third }
      c.copy(words = words, edited = words.any { it.edited })
    }
    return doc.copy(cues = cues)
  }

  /**
   * Replaces the captions with ones edited outside the app (an .srt the user changed), keeping
   * the look: each new caption takes the own look, position and translation of the old caption
   * it overlaps most. A changed caption's translation is marked out of date. When the edited
   * lines are in English letters ([phoneticEdit]), they become both lines as typed.
   */
  fun loadEdited(doc: SubtitleDoc, edited: List<SubtitleFormats.ParsedCue>, ph: Phonetic, ids: IdGen, phoneticEdit: Boolean): SubtitleDoc {
    // Keep the doc's rules even if the file breaks them: sorted, no overlaps, not too short.
    val fixed = ArrayList<Triple<Long, Long, String>>()
    for (p in edited.sortedBy { it.startMs }) {
      val text = p.lines.joinToString(" ").trim()
      if (text.isEmpty()) continue
      val start = max(p.startMs, fixed.lastOrNull()?.second ?: 0L)
      fixed.add(Triple(start, max(p.endMs, start + MIN_CUE_MS), text))
    }
    val cues = fixed.map { (start, end, text) ->
      val parts = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
      val times = WordTiming.spreadWords(parts, start, end)
      val before = doc.cues.maxByOrNull { min(it.endMs, end) - max(it.startMs, start) }
        ?.takeIf { min(it.endMs, end) > max(it.startMs, start) }
      val same = before != null && (if (phoneticEdit) before.phonetic else before.text) == parts.joinToString(" ")
      Cue(
        id = ids.next(),
        startMs = start,
        endMs = end,
        words = parts.mapIndexed { k, w ->
          Word(ids.next(), w, if (phoneticEdit) w else ph.of(w), times[k].first, times[k].second, phoneticByUser = phoneticEdit, edited = !same)
        },
        translation = before?.translation ?: "",
        translationState = when {
          before == null || before.translation.isEmpty() -> TranslationState.NONE
          same -> before.translationState
          else -> TranslationState.STALE
        },
        origin = Origin.IMPORTED,
        edited = !same,
        style = before?.style,
        transform = before?.transform,
      )
    }
    return doc.copy(cues = cues)
  }

  // ---------------------------------------------------------------------------------------
  // Timing

  /** Moves a caption (and its words) in time, stopping at its neighbours and the video ends. */
  fun moveCue(doc: SubtitleDoc, cueId: String, deltaMs: Long, videoDurationMs: Long): SubtitleDoc {
    val i = doc.indexOf(cueId)
    if (i < 0 || deltaMs == 0L) return doc
    val cue = doc.cues[i]
    val lo = doc.cues.getOrNull(i - 1)?.endMs ?: 0L
    val hi = doc.cues.getOrNull(i + 1)?.startMs ?: if (videoDurationMs > 0) videoDurationMs else Long.MAX_VALUE
    val d = deltaMs.coerceIn(lo - cue.startMs, hi - cue.endMs)
    if (d == 0L) return doc
    val moved = cue.copy(
      startMs = cue.startMs + d,
      endMs = cue.endMs + d,
      words = cue.words.map { it.copy(startMs = it.startMs + d, endMs = it.endMs + d) },
      edited = true,
    )
    return doc.copy(cues = doc.cues.toMutableList().apply { set(i, moved) })
  }

  /**
   * Sets a caption's start and end (dragging its edges). Words keep their times when they
   * still fit; otherwise they are squeezed into the new span.
   */
  fun setCueTiming(doc: SubtitleDoc, cueId: String, startMs: Long, endMs: Long, videoDurationMs: Long): SubtitleDoc {
    val i = doc.indexOf(cueId)
    if (i < 0) return doc
    val cue = doc.cues[i]
    val lo = doc.cues.getOrNull(i - 1)?.endMs ?: 0L
    val hi = doc.cues.getOrNull(i + 1)?.startMs ?: if (videoDurationMs > 0) videoDurationMs else Long.MAX_VALUE
    var s = startMs.coerceIn(lo, hi - MIN_CUE_MS)
    var e = endMs.coerceIn(s + MIN_CUE_MS, hi)
    if (e - s < MIN_CUE_MS) {
      s = max(lo, e - MIN_CUE_MS)
      e = min(hi, s + MIN_CUE_MS)
    }
    if (s == cue.startMs && e == cue.endMs) return doc
    val updated = clampWords(cue.copy(startMs = s, endMs = e, edited = true))
    return doc.copy(cues = doc.cues.toMutableList().apply { set(i, updated) })
  }

  /** Shifts every caption by [deltaMs] (fixes captions that are early or late overall). */
  fun shiftAll(doc: SubtitleDoc, deltaMs: Long, videoDurationMs: Long): SubtitleDoc {
    if (doc.cues.isEmpty() || deltaMs == 0L) return doc
    val first = doc.cues.first().startMs
    val last = doc.cues.last().endMs
    val maxForward = if (videoDurationMs > 0) videoDurationMs - last else Long.MAX_VALUE
    val d = deltaMs.coerceIn(-first, max(0L, maxForward))
    if (d == 0L) return doc
    return doc.copy(
      cues = doc.cues.map { c ->
        c.copy(
          startMs = c.startMs + d,
          endMs = c.endMs + d,
          words = c.words.map { it.copy(startMs = it.startMs + d, endMs = it.endMs + d) },
        )
      },
    )
  }

  /** Sets one word's time inside its caption (fine karaoke timing). */
  fun setWordTiming(doc: SubtitleDoc, cueId: String, wordId: String, startMs: Long, endMs: Long): SubtitleDoc =
    updateCue(doc, cueId) { cue ->
      val k = cue.words.indexOfFirst { it.id == wordId }
      if (k < 0) return@updateCue cue
      val lo = cue.words.getOrNull(k - 1)?.endMs ?: cue.startMs
      val hi = cue.words.getOrNull(k + 1)?.startMs ?: cue.endMs
      val s = startMs.coerceIn(lo, max(lo, hi - MIN_WORD_MS))
      val e = endMs.coerceIn(s + MIN_WORD_MS, max(s + MIN_WORD_MS, hi))
      cue.copy(words = cue.words.toMutableList().apply { set(k, cue.words[k].copy(startMs = s, endMs = e)) }, edited = true)
    }

  // ---------------------------------------------------------------------------------------
  // Look and position

  /** Applies a look to one caption ([cueId]) or, with null, to all (clearing per-caption looks). */
  fun applyStyle(doc: SubtitleDoc, style: CaptionStyle, cueId: String?): SubtitleDoc =
    if (cueId == null) {
      doc.copy(style = style, cues = doc.cues.map { if (it.style != null) it.copy(style = null) else it })
    } else {
      updateCue(doc, cueId) { it.copy(style = style, edited = true) }
    }

  /** Moves one caption ([cueId]) or, with null, all captions (clearing per-caption positions). */
  fun applyTransform(doc: SubtitleDoc, transform: Transform, cueId: String?): SubtitleDoc =
    if (cueId == null) {
      doc.copy(transform = transform, cues = doc.cues.map { if (it.transform != null) it.copy(transform = null) else it })
    } else {
      updateCue(doc, cueId) { it.copy(transform = transform, edited = true) }
    }

  // ---------------------------------------------------------------------------------------

  private inline fun updateCue(doc: SubtitleDoc, cueId: String, change: (Cue) -> Cue?): SubtitleDoc {
    val i = doc.indexOf(cueId)
    if (i < 0) return doc
    val updated = change(doc.cues[i])
    if (updated === doc.cues[i]) return doc
    return doc.copy(cues = doc.cues.toMutableList().apply { if (updated == null) removeAt(i) else set(i, updated) })
  }

  private fun Cue.textChanged(words: List<Word>): Cue = copy(
    words = words,
    edited = true,
    translationState = if (translationState == TranslationState.AUTO) TranslationState.STALE else translationState,
  )

  /** Keeps words inside the caption's span, squeezing them in proportionally if needed. */
  private fun clampWords(cue: Cue): Cue {
    if (cue.words.isEmpty()) return cue
    val ws = cue.words.first().startMs
    val we = cue.words.last().endMs
    if (ws >= cue.startMs && we <= cue.endMs) return cue
    val ns = max(ws, cue.startMs)
    val ne = min(we, cue.endMs)
    val words = if (ne - ns >= cue.words.size * MIN_WORD_MS) {
      val scale = (ne - ns).toDouble() / (we - ws).coerceAtLeast(1)
      cue.words.map { w ->
        w.copy(
          startMs = ns + ((w.startMs - ws) * scale).roundToLong(),
          endMs = ns + ((w.endMs - ws) * scale).roundToLong(),
        )
      }
    } else {
      val times = WordTiming.spreadWords(cue.words.map { it.text }, cue.startMs, cue.endMs)
      cue.words.mapIndexed { k, w -> w.copy(startMs = times[k].first, endMs = times[k].second) }
    }
    return cue.copy(words = words)
  }
}
