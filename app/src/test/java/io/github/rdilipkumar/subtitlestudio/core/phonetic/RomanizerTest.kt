package io.github.rdilipkumar.subtitlestudio.core.phonetic

import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import org.junit.Assert.assertEquals
import org.junit.Test

class RomanizerTest {

  private fun casual(s: String) = Romanizer.romanize(s, PhoneticMode.CASUAL)
  private fun exact(s: String) = Romanizer.romanize(s, PhoneticMode.EXACT)

  @Test fun tamil_basics() {
    assertEquals("vanakkam", casual("வணக்கம்"))
    assertEquals("nanbargale", casual("நண்பர்களே"))
    assertEquals("thamizh", casual("தமிழ்"))
    assertEquals("eppadi irukkeenga", casual("எப்படி இருக்கீங்க"))
  }

  @Test fun tamil_sound_changes() {
    assertEquals("adhu", casual("அது")) // த between vowels
    assertEquals("magan", casual("மகன்")) // க between vowels
    assertEquals("thambi", casual("தம்பி")) // ப after a nasal
    assertEquals("indha", casual("இந்த")) // த after a nasal
    assertEquals("nandri", casual("நன்றி")) // ன்ற
    assertEquals("kaatru", casual("காற்று")) // ற்ற
    assertEquals("kaatchi", casual("காட்சி")) // closed ட stays hard
    assertEquals("pachai", casual("பச்சை")) // ச்ச
    assertEquals("vendaam", casual("வேண்டாம்"))
    assertEquals("saappaadu", casual("சாப்பாடு")) // ச at the start is "s"
    assertEquals("sri", casual("ஸ்ரீ"))
    assertEquals("vignaanam", casual("விஞ்ஞானம்")) // ஞ்ஞ is jña, not "njnj"
    assertEquals("muyarchi", casual("முயற்சி")) // the usual spelling (as in "Vidaamuyarchi")
  }

  @Test fun tamil_mixed_with_english() {
    assertEquals("idhu super aa irukku", casual("இது super ஆ இருக்கு"))
    // Tanglish speech model output: English words stay, Tamil endings split off.
    assertEquals("lunch finish pannittu rush aa irukku", casual("lunch finish பண்ணிட்டு rush-ஆ இருக்கு"))
    assertEquals("Bangalore laye", casual("Bangalore-லயே"))
    assertEquals("well-known", casual("well-known"))
  }

  @Test fun tamil_exact() {
    assertEquals("vaṇakkam", exact("வணக்கம்"))
    assertEquals("naṇparkaḷē", exact("நண்பர்களே"))
    assertEquals("tamiḻ", exact("தமிழ்"))
  }

  @Test fun telugu() {
    assertEquals("namaskaaram", casual("నమస్కారం"))
    assertEquals("elaa unnaaru", casual("ఎలా ఉన్నారు"))
    assertEquals("thelugu", casual("తెలుగు"))
    assertEquals("gnaanam", casual("జ్ఞానం"))
  }

  @Test fun kannada() {
    assertEquals("namaskaara", casual("ನಮಸ್ಕಾರ"))
    assertEquals("kannada", casual("ಕನ್ನಡ"))
    assertEquals("hegiddeeraa", casual("ಹೇಗಿದ್ದೀರಾ"))
  }

  @Test fun malayalam() {
    assertEquals("malayaalam", casual("മലയാളം"))
    assertEquals("ningal", casual("നിങ്ങൾ"))
    assertEquals("ente", casual("എന്റെ"))
    assertEquals("njaan", casual("ഞാൻ"))
    assertEquals("kaattu", casual("കാറ്റ്")) // final virama is a short "u"
    assertEquals("ithu", casual("ഇത്"))
  }

  @Test fun malayalam_voiced_t_and_mb() {
    assertEquals("vada", casual("വട")) // ട between vowels
    assertEquals("naadu", casual("നാട്")) // final ട് with the short "u"
    assertEquals("venda", casual("വേണ്ട")) // ണ്ട
    assertEquals("undu", casual("ഉണ്ട്"))
    assertEquals("pattam", casual("പട്ടം")) // doubled stays hard
    assertEquals("kashtam", casual("കഷ്ടം")) // after another consonant stays hard
    assertEquals("ambalam", casual("അമ്പലം")) // മ്പ
    assertEquals("paapam", casual("പാപം")) // plain പ stays "p"
  }

  @Test fun hindi_drops_silent_a() {
    assertEquals("namaste", casual("नमस्ते"))
    assertEquals("kamal", casual("कमल"))
    assertEquals("apnaa", casual("अपना"))
    assertEquals("bhaarat", casual("भारत"))
    assertEquals("doston", casual("दोस्तों"))
    assertEquals("samajhnaa", casual("समझना"))
    assertEquals("zindagee", casual("ज़िंदगी"))
    assertEquals("namaste.", casual("नमस्ते।"))
    assertEquals("satya", casual("सत्य")) // final "a" kept after a cluster ending in y/r/l/v
    assertEquals("mantra", casual("मन्त्र"))
    assertEquals("dharm", casual("धर्म"))
    assertEquals("uttar", casual("उत्तर"))
  }

  @Test fun digits_dictionary_and_latin() {
    assertEquals("12", casual("௧௨"))
    assertEquals("chennai", Romanizer.romanize("சென்னை", dictionary = mapOf("சென்னை" to "chennai")))
    assertEquals("Hello, world!", casual("Hello, world!"))
  }

  @Test fun dominant_script() {
    assertEquals(Script.TAMIL, Romanizer.dominantScript("இது super ஆ இருக்கு"))
    assertEquals(Script.LATIN, Romanizer.dominantScript("hello"))
  }
}
