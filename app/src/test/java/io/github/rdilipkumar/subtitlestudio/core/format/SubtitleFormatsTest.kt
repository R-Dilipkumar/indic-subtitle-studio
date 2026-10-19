package io.github.rdilipkumar.subtitlestudio.core.format

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.core.model.Word
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.core.timing.WordTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFormatsTest {

  private val doc = SubtitleDoc(
    cues = listOf(
      Cue("a", 1_234, 3_500, listOf(Word("w1", "வணக்கம்", "vanakkam", 1_234, 2_000), Word("w2", "நண்பர்களே", "nanbargale", 2_000, 3_400)), translation = "Hello friends"),
      Cue("b", 3_600_000, 3_601_000, listOf(Word("w3", "hi", "hi", 3_600_000, 3_601_000))),
    ),
  )

  @Test fun srt_has_times_and_all_lines() {
    val srt = SubtitleFormats.toSrt(doc, listOf(TrackKind.NATIVE, TrackKind.PHONETIC, TrackKind.TRANSLATION))
    assertTrue(srt.startsWith("1\n00:00:01,234 --> 00:00:03,500\nவணக்கம் நண்பர்களே\nvanakkam nanbargale\nHello friends\n\n"))
    // English: the English-letters line is the same as the words, so it is not repeated.
    assertTrue(srt.contains("2\n01:00:00,000 --> 01:00:01,000\nhi\n\n"))
  }

  @Test fun srt_carries_the_translation_piece_shown_with_each_caption() {
    val oneWord = SubtitleDoc(
      cues = listOf(
        Cue("a", 0, 1000, listOf(Word("w1", "வணக்கம்", "vanakkam", 0, 1000)), translation = "Hello"),
        Cue("b", 1000, 2000, listOf(Word("w2", "நண்பர்களே", "nanbargale", 1000, 2000)), translation = "my friends"),
      ),
    )
    // With the captions: each its own piece.
    assertTrue(SubtitleFormats.toSrt(oneWord, listOf(TrackKind.NATIVE, TrackKind.TRANSLATION)).contains("நண்பர்களே\nmy friends\n"))
    // A whole line of meaning at a time: the line is on screen during both captions.
    val line = oneWord.copy(tracks = oneWord.tracks.copy(translationSize = CaptionLength.LINE))
    val srt = SubtitleFormats.toSrt(line, listOf(TrackKind.NATIVE, TrackKind.TRANSLATION))
    assertTrue(srt.contains("வணக்கம்\nHello my friends\n"))
    assertTrue(srt.contains("நண்பர்களே\nHello my friends\n"))
  }

  @Test fun ass_karaoke_on_translation_when_it_is_the_top_line() {
    val ass = SubtitleFormats.toAss(doc, listOf(TrackKind.TRANSLATION, TrackKind.NATIVE), 1080, 1920, "Roboto")
    assertTrue(ass.contains("Hello"))
    assertTrue(Regex("\\{\\\\k\\d+\\}Hello").containsMatchIn(ass))
  }

  @Test fun parse_reads_back_srt_and_vtt() {
    val srt = SubtitleFormats.toSrt(doc, listOf(TrackKind.NATIVE, TrackKind.TRANSLATION))
    val parsed = SubtitleFormats.parse(srt)
    assertEquals(2, parsed.size)
    assertEquals(1_234L, parsed[0].startMs)
    assertEquals(listOf("வணக்கம் நண்பர்களே", "Hello friends"), parsed[0].lines)
    val vtt = SubtitleFormats.parse(SubtitleFormats.toVtt(doc, listOf(TrackKind.PHONETIC)))
    assertEquals("vanakkam nanbargale", vtt[0].lines[0])
    assertEquals(1, SubtitleFormats.parse("00:01.500 --> 00:02.000\n<i>short</i>\n").size)
  }

  @Test fun ass_has_karaoke_and_position() {
    val ass = SubtitleFormats.toAss(doc, listOf(TrackKind.NATIVE, TrackKind.PHONETIC), 1080, 1920, "Noto Sans Tamil")
    assertTrue(ass.contains("PlayResY: 1920"))
    assertTrue(ass.contains("\\pos(540,1382)"))
    assertTrue(ass.contains("{\\k76}வணக்கம்"))
  }

  @Test fun token_times_map_to_words() {
    val words = WordTiming.wordsFromTokens(" hello world", listOf(" hel", "lo", " world"), listOf(0, 200, 500), 900)
    assertNotNull(words)
    assertEquals(listOf("hello" to 0L, "world" to 500L), words!!.map { it.first to it.second })
    assertEquals(900L, words[1].third)
  }

  @Test fun spread_covers_span() {
    val t = WordTiming.spread(listOf(1f, 1f), 0, 1000)
    assertEquals(0L, t[0].first)
    assertEquals(1000L, t[1].second)
    assertTrue(t[0].second <= t[1].first)
  }
}
