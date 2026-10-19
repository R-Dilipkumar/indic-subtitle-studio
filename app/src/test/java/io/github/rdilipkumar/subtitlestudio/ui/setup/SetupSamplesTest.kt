package io.github.rdilipkumar.subtitlestudio.ui.setup

import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupSamplesTest {

  private fun choices(lang: Lang, main: TrackKind = TrackKind.PHONETIC, length: CaptionLength = CaptionLength.PUNCHY) = SetupChoices(
    language = lang,
    translateTo = if (lang == Lang.ENGLISH) Lang.TAMIL else Lang.ENGLISH,
    length = length,
    showNative = true,
    showPhonetic = true,
    showTranslation = false,
    phoneticMode = PhoneticMode.CASUAL,
    main = main,
  )

  @Test
  fun everyLanguageHasASampleCutByCaptionSize() {
    for (l in Lang.entries) {
      for (len in CaptionLength.entries) {
        val (doc, loop) = SetupSamples.build(choices(l, length = len))
        assertTrue("$l $len", doc.cues.isNotEmpty())
        assertTrue("$l $len", doc.cues.all { it.words.size <= len.maxWords })
        assertTrue(loop > doc.cues.last().endMs)
      }
      println("$l: " + SetupSamples.phonetic(l, SetupSamples.SENTENCES.getValue(l).first, PhoneticMode.CASUAL))
    }
  }

  @Test
  fun mainLineComesFirst() {
    val t = SetupSamples.tracks(choices(Lang.TAMIL, main = TrackKind.TRANSLATION).copy(showTranslation = true))
    assertEquals(TrackKind.TRANSLATION, t.visibleInOrder().first())
  }

  @Test
  fun firstCaptionFollowsSize() {
    assertEquals("Hi", SetupSamples.firstCaptionText(choices(Lang.TANGLISH, length = CaptionLength.ONE_WORD), CaptionLength.ONE_WORD))
    assertEquals(1, SetupSamples.firstCaptionText(choices(Lang.TAMIL), CaptionLength.ONE_WORD).split(' ').size)
  }

  @Test
  fun translationHasItsOwnSize() {
    val c = choices(Lang.TANGLISH, main = TrackKind.TRANSLATION, length = CaptionLength.ONE_WORD).copy(showTranslation = true)
    assertEquals("Hi", SetupSamples.firstTranslationText(c, null)) // with each one-word caption
    assertEquals("Hi folks, today we are going to a new place", SetupSamples.firstTranslationText(c, CaptionLength.SENTENCE))
    assertEquals("Hi", SetupSamples.firstTranslationText(c.copy(length = CaptionLength.SENTENCE), CaptionLength.ONE_WORD))
    // The preview doc carries the size, and every word of the meaning.
    val (doc, _) = SetupSamples.build(c.copy(translationLength = CaptionLength.LINE))
    assertEquals(CaptionLength.LINE, doc.tracks.translationSize)
    assertEquals("Hi folks, today we are going to a new place", doc.cues.joinToString(" ") { it.translation })
  }

  @Test
  fun translationSampleMatchesTarget() {
    assertEquals("Hello friends, today we are going to a new place", SetupSamples.translation(Lang.TAMIL, Lang.ENGLISH))
    assertEquals(SetupSamples.SENTENCES.getValue(Lang.HINDI).first, SetupSamples.translation(Lang.TAMIL, Lang.HINDI))
  }
}
