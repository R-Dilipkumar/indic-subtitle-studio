package io.github.rdilipkumar.subtitlestudio.core.phonetic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordCheckTest {

  @Test fun flags_words_missing_their_first_letter() {
    assertTrue(WordCheck.looksBroken("ங்கிட்டேயும்")) // எங்கிட்ட… lost its எ
    assertTrue(WordCheck.looksBroken("ன்னங்க")) // என்னங்க
    assertTrue(WordCheck.looksBroken("ி")) // a lone vowel sign
    assertTrue(WordCheck.looksBroken("முடி்சதுங்களா")) // vowel sign + virama
  }

  @Test fun leaves_real_words_alone() {
    for (w in listOf("வணக்கம்", "தமிழ்", "ஞாயிறு", "டீ", "நண்பர்களே", "నమస్కారం", "ಕನ್ನಡ", "മലയാളം", "नमस्ते", "super", "\"வா\"")) {
      assertFalse(w, WordCheck.looksBroken(w))
    }
  }

  @Test fun malayalam_old_short_u_is_fine() {
    assertFalse(WordCheck.looksBroken("അതു്")) // u-sign + virama, old spelling of അത്
    assertTrue(WordCheck.looksBroken("അതാ്")) // aa-sign + virama is still wrong
  }
}
