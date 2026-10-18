package io.github.rdilipkumar.subtitlestudio.core.edit

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TranslationState
import io.github.rdilipkumar.subtitlestudio.core.model.Word
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.core.timing.CueBuilder
import io.github.rdilipkumar.subtitlestudio.core.timing.IdGen
import io.github.rdilipkumar.subtitlestudio.core.timing.TimedWord
import io.github.rdilipkumar.subtitlestudio.core.timing.WordTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocOpsTest {

  private var n = 0
  private val ids = IdGen { "id${n++}" }
  private val ph = DocOps.Phonetic { Romanizer.romanize(it) }

  private fun word(id: String, t: String, s: Long, e: Long) = Word(id, t, ph.of(t), s, e)

  private val fixture = SubtitleDoc(
    cues = listOf(
      Cue("a", 0, 1000, listOf(word("w0", "வணக்கம்", 0, 500), word("w1", "நண்பர்களே", 520, 1000)), translation = "Hello friends", translationState = TranslationState.AUTO),
      Cue("b", 2000, 3000, listOf(word("w2", "எப்படி", 2000, 2400), word("w3", "இருக்கீங்க", 2450, 3000))),
    ),
  )

  private fun doc(): SubtitleDoc = fixture

  private fun assertValid(d: SubtitleDoc) {
    for ((i, c) in d.cues.withIndex()) {
      assertTrue("cue ${c.id} too short", c.endMs - c.startMs >= DocOps.MIN_CUE_MS)
      d.cues.getOrNull(i + 1)?.let { assertTrue("overlap at ${c.id}", c.endMs <= it.startMs) }
      for (w in c.words) assertTrue("word outside cue ${c.id}", w.startMs >= c.startMs && w.endMs <= c.endMs)
    }
  }

  @Test fun editWord_marks_translation_stale_and_regenerates_phonetic() {
    val d = DocOps.editWord(doc(), "a", "w0", "வணக்கம்!", ph, ids)
    val c = d.cue("a")!!
    assertEquals("வணக்கம்!", c.words[0].text)
    assertEquals("vanakkam!", c.words[0].phonetic)
    assertTrue(c.words[0].edited)
    assertEquals(TranslationState.STALE, c.translationState)
  }

  @Test fun editWord_with_space_makes_two_words_in_same_time() {
    val d = DocOps.editWord(doc(), "b", "w2", "எப்படி டா", ph, ids)
    val c = d.cue("b")!!
    assertEquals(3, c.words.size)
    assertEquals(2000L, c.words[0].startMs)
    assertTrue(c.words[1].endMs <= 2400)
    assertValid(d)
  }

  @Test fun blank_word_deletes_it_and_empty_cue_goes() {
    var d = DocOps.editWord(doc(), "b", "w2", "", ph, ids)
    assertEquals(1, d.cue("b")!!.words.size)
    d = DocOps.editWord(d, "b", "w3", " ", ph, ids)
    assertNull(d.cue("b"))
  }

  @Test fun split_then_merge() {
    val split = DocOps.splitCue(doc(), "a", 0, ids)
    assertEquals(3, split.cues.size)
    assertEquals(TranslationState.STALE, split.cues[0].translationState)
    assertEquals(510L, split.cues[0].endMs) // halfway between the words
    assertEquals(510L, split.cues[1].startMs)
    assertValid(split)
    val merged = DocOps.mergeWithNext(split, "a")
    assertEquals(2, merged.cues.size)
    assertEquals(2, merged.cue("a")!!.words.size)
    assertValid(merged)
  }

  @Test fun split_short_cue_never_runs_into_the_next() {
    val short = SubtitleDoc(
      cues = listOf(
        Cue("a", 1000, 1300, listOf(word("w0", "ஒன்று", 1000, 1150), word("w1", "இரண்டு", 1150, 1300))),
        Cue("b", 1350, 2000, listOf(word("w2", "மூன்று", 1350, 2000))),
      ),
    )
    // Not enough room for two 200 ms captions before "b": left alone.
    assertEquals(short, DocOps.splitCue(short, "a", 0, ids))
    // With free time after it, the halves may grow into it.
    val roomy = short.copy(cues = listOf(short.cues[0], short.cues[1].copy(startMs = 1600, words = listOf(word("w2", "மூன்று", 1600, 2000)))))
    val split = DocOps.splitCue(roomy, "a", 0, ids)
    assertEquals(3, split.cues.size)
    assertValid(split)
  }

  @Test fun moveCue_stops_at_neighbours() {
    val d = DocOps.moveCue(doc(), "b", -5000, 10_000)
    assertEquals(1000L, d.cue("b")!!.startMs)
    assertEquals(1000L, d.cue("b")!!.words[0].startMs)
    assertValid(d)
    val d2 = DocOps.moveCue(doc(), "b", 50_000, 4_000)
    assertEquals(4000L, d2.cue("b")!!.endMs)
  }

  @Test fun setCueTiming_squeezes_words_when_needed() {
    val d = DocOps.setCueTiming(doc(), "b", 2200, 2800, 10_000)
    val c = d.cue("b")!!
    assertEquals(2200L, c.startMs)
    assertEquals(2800L, c.endMs)
    assertValid(d)
    val tooShort = DocOps.setCueTiming(doc(), "b", 2500, 2510, 10_000)
    assertTrue(tooShort.cue("b")!!.durationMs >= DocOps.MIN_CUE_MS)
  }

  @Test fun insertCue_only_in_free_space() {
    val d = DocOps.insertCue(doc(), 1200, "புதிய வரி", ph, ids, 10_000)
    assertEquals(3, d.cues.size)
    assertEquals(1200L, d.cues[1].startMs)
    assertEquals(2000L, d.cues[1].endMs) // stopped by the next caption
    assertValid(d)
    assertEquals(doc(), DocOps.insertCue(doc(), 500, "x", ph, ids, 10_000)) // inside a caption
  }

  @Test fun shiftAll_never_before_zero() {
    val d = DocOps.shiftAll(doc(), -300, 10_000)
    assertEquals(0L, d.cues[0].startMs)
    val later = DocOps.shiftAll(doc(), 250, 10_000)
    assertEquals(2250L, later.cues[1].startMs)
    assertEquals(2250L, later.cues[1].words[0].startMs)
  }

  @Test fun regroup_keeps_every_word() {
    val d = DocOps.regroup(doc(), CaptionLength.ONE_WORD, ids)
    assertEquals(4, d.cues.size)
    assertEquals(listOf("வணக்கம்", "நண்பர்களே", "எப்படி", "இருக்கீங்க"), d.cues.map { it.text })
    assertValid(d)
  }

  @Test fun regroup_keeps_word_ids_even_for_same_word_at_same_time() {
    val twins = SubtitleDoc(
      cues = listOf(
        Cue("a", 0, 1000, listOf(word("x1", "நா", 0, 300), word("x2", "நா", 0, 300), word("x3", "வா", 400, 1000))),
      ),
    )
    val d = DocOps.regroup(twins, CaptionLength.ONE_WORD, ids)
    assertEquals(listOf("x1", "x2", "x3"), d.cues.flatMap { c -> c.words.map { it.id } })
  }

  @Test fun loadEdited_replaces_words_and_keeps_look() {
    val styled = doc().copy(cues = doc().cues.map { if (it.id == "b") it.copy(transform = io.github.rdilipkumar.subtitlestudio.core.model.Transform(y = 0.3f)) else it })
    val srt = """
1
00:00:00,000 --> 00:00:01,000
வணக்கம் நண்பர்களே

2
00:00:02,000 --> 00:00:03,000
எப்படி இருக்கீங்க எல்லாரும்
"""
    val d = DocOps.loadEdited(styled, io.github.rdilipkumar.subtitlestudio.core.format.SubtitleFormats.parse(srt), ph, ids, phoneticEdit = false)
    assertEquals(2, d.cues.size)
    assertEquals("vanakkam nanbargale", d.cues[0].phonetic)
    assertEquals(TranslationState.AUTO, d.cues[0].translationState) // same words: translation still good
    assertFalse(d.cues[0].edited)
    assertEquals("எப்படி இருக்கீங்க எல்லாரும்", d.cues[1].text)
    assertTrue(d.cues[1].edited)
    assertEquals(0.3f, d.cues[1].transform!!.y) // its own position kept
    assertValid(d)
  }

  @Test fun loadEdited_english_letters_stay_as_typed_and_overlaps_are_fixed() {
    val srt = """
1
00:00:00,000 --> 00:00:01,500
Hi makkale vibe pannalaamaa

2
00:00:01,200 --> 00:00:01,250
super
"""
    val d = DocOps.loadEdited(doc(), io.github.rdilipkumar.subtitlestudio.core.format.SubtitleFormats.parse(srt), ph, ids, phoneticEdit = true)
    assertEquals("Hi makkale vibe pannalaamaa", d.cues[0].phonetic)
    assertEquals(1500L, d.cues[1].startMs) // pushed after the first one
    assertEquals(TranslationState.STALE, d.cues[0].translationState)
    assertValid(d)
  }

  @Test fun replaceAll_counts() {
    val (d, count) = DocOps.replaceAll(doc(), "வணக்கம்", "ஹாய்", ph)
    assertEquals(1, count)
    assertEquals("ஹாய்", d.cues[0].words[0].text)
  }

  @Test fun cueAt_finds_by_time() {
    assertEquals("a", doc().cueAt(999)?.id)
    assertNull(doc().cueAt(1500))
    assertEquals("b", doc().cueAt(2000)?.id)
  }

  @Test fun builder_balances_and_respects_pauses() {
    val words = (0 until 7).map { TimedWord("சொல்$it", it * 300L, it * 300L + 250) } +
      TimedWord("பின்", 5000, 5300)
    val cues = CueBuilder.build(words, CaptionLength.PUNCHY, { Romanizer.romanize(it) }, ids)
    assertEquals(listOf(3, 2, 2, 1), cues.map { it.words.size }.let { it.take(3).sortedDescending() + it.drop(3) })
    assertEquals("பின்", cues.last().text) // the pause starts a new caption
    for (i in 0 until cues.lastIndex) assertTrue(cues[i].endMs <= cues[i + 1].startMs)
  }

  @Test fun history_undo_redo_and_merge() {
    val h = EditHistory(doc())
    val d1 = DocOps.moveCue(doc(), "b", 100, 10_000)
    h.push("Move", d1, key = "move-b", now = 1000)
    val d2 = DocOps.moveCue(d1, "b", 100, 10_000)
    h.push("Move", d2, key = "move-b", now = 1500) // folds into the same entry
    assertEquals(2, h.all.size)
    assertEquals(d2, h.current)
    assertEquals(doc(), h.undo())
    assertFalse(h.canUndo)
    assertEquals(d2, h.redo())
    h.jumpTo(0)
    h.push("Delete", DocOps.deleteCue(doc(), "a"), now = 9000) // drops the redo branch
    assertEquals(2, h.all.size)
    assertEquals("Delete", h.all.last().label)
  }

  @Test fun history_seal_starts_a_new_entry() {
    val h = EditHistory(doc())
    val d1 = DocOps.moveCue(doc(), "b", 100, 10_000)
    h.push("Move", d1, key = "drag-b", now = 1000)
    h.seal() // finger lifted
    h.push("Move", DocOps.moveCue(d1, "b", 100, 10_000), key = "drag-b", now = 1500)
    assertEquals(3, h.all.size)
    assertEquals(d1, h.undo())
  }

  @Test fun token_word_times_never_overlap() {
    val words = WordTiming.wordsFromTokens("அ ஆ இ", listOf("அ", " ஆ", " இ"), listOf(100L, 100L, 120L), 500)!!
    for (i in 0 until words.lastIndex) assertTrue(words[i].third <= words[i + 1].second)
    for (w in words) assertTrue(w.third - w.second >= 40)
  }
}
