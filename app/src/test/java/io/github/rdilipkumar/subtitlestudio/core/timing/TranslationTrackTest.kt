package io.github.rdilipkumar.subtitlestudio.core.timing

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationTrackTest {

  private fun cue(id: String, start: Long, end: Long, text: String = "சொல்", translation: String = "") =
    Cue(id, start, end, listOf(Word("w$id", text, text, start, end)), translation = translation)

  /** The setup sample: Tanglish spoken one word per caption, the English sentence shared out. */
  private fun sample(length: CaptionLength): List<Cue> {
    val words = "Hi மக்களே இன்னைக்கு நாம ஒரு new place போறோம்".split(' ')
    var n = 0
    val timed = words.mapIndexed { i, w -> TimedWord(w, 300 + i * 420L, 300 + i * 420L + 380) }
    val cues = CueBuilder.build(timed, length, { it }, { "c${n++}" })
    val pieces = TranslationSplit.share("Hi folks, today we are going to a new place", cues)
    return cues.zip(pieces) { c, p -> c.copy(translation = p) }
  }

  @Test fun share_follows_speaking_time() {
    val cues = listOf(cue("a", 0, 1000), cue("b", 1000, 4000))
    val pieces = TranslationSplit.share("go go go go go go go go", cues)
    assertEquals(listOf(2, 6), pieces.map { it.split(' ').size })
  }

  @Test fun share_gives_every_caption_a_word() {
    val cues = listOf(cue("a", 0, 100), cue("b", 100, 5000), cue("c", 5000, 5100))
    val pieces = TranslationSplit.share("one two three", cues)
    assertEquals(listOf("one", "two", "three"), pieces)
  }

  @Test fun share_repeats_when_words_run_out() {
    val cues = listOf(cue("a", 0, 500), cue("b", 500, 1000), cue("c", 1000, 1500))
    assertEquals(listOf("Yes", "Yes", "Yes"), TranslationSplit.share("Yes", cues))
    assertEquals(listOf("all of it"), TranslationSplit.share(" all  of it ", cues.take(1)))
  }

  @Test fun share_keeps_every_word_in_order() {
    for (len in CaptionLength.entries) {
      val joined = sample(len).joinToString(" ") { it.translation }
      assertEquals("Hi folks, today we are going to a new place", joined)
    }
  }

  @Test fun one_word_translation_over_bigger_captions() {
    val cues = sample(CaptionLength.SENTENCE)
    assertEquals(1, cues.size)
    val chunks = TranslationTrack.build(cues, CaptionLength.ONE_WORD)
    assertEquals(10, chunks.size)
    assertTrue(chunks.all { it.words.size == 1 })
    for (i in 0 until chunks.lastIndex) assertTrue(chunks[i].endMs <= chunks[i + 1].startMs)
    // At each moment the piece shown is the word being said.
    assertEquals("Hi", TranslationTrack.at(chunks, cues[0].startMs, cues[0])!!.text)
    assertEquals("place", TranslationTrack.at(chunks, cues[0].words.last().endMs - 1, cues[0])!!.text)
  }

  @Test fun whole_line_translation_over_one_word_captions() {
    val cues = sample(CaptionLength.ONE_WORD)
    assertEquals(8, cues.size)
    val chunks = TranslationTrack.build(cues, CaptionLength.SENTENCE)
    assertEquals(1, chunks.size)
    // The same line stays while every one-word caption shows.
    for (c in cues) assertEquals(chunks[0], TranslationTrack.at(chunks, c.startMs + 10, c))
    assertEquals("Hi folks, today we are going to a new place", TranslationTrack.textDuring(chunks, cues[3]))
  }

  @Test fun pieces_never_cross_a_sentence_end() {
    val cues = listOf(
      cue("a", 0, 1000, "முதல்.", "This is the first one."),
      cue("b", 1100, 2000, "இரண்டு", "And here is the second"),
    )
    val chunks = TranslationTrack.build(cues, CaptionLength.SENTENCE)
    assertEquals(listOf("This is the first one.", "And here is the second"), chunks.map { it.text })
    assertEquals("This is the first one.", TranslationTrack.textDuring(chunks, cues[0]))
    assertNotNull(TranslationTrack.at(chunks, 1500, cues[1]))
  }

  @Test fun repeated_pieces_show_once() {
    val cues = listOf(cue("a", 0, 500, translation = "Yes"), cue("b", 500, 1000, translation = "Yes"))
    val chunks = TranslationTrack.build(cues, CaptionLength.ONE_WORD)
    assertEquals(listOf("Yes"), chunks.map { it.text })
  }
}
