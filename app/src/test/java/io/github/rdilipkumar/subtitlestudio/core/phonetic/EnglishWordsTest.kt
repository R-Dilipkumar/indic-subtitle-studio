package io.github.rdilipkumar.subtitlestudio.core.phonetic

import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishWordsTest {

  private fun casual(s: String) = Romanizer.romanize(s, PhoneticMode.CASUAL)

  @Test fun english_words_in_tamil_letters_get_english_spelling() {
    assertEquals("super", casual("சூப்பர்"))
    assertEquals("welcome", casual("வெல்கம்"))
    assertEquals("premium", casual("பிரீமியம்"))
    assertEquals("golden fifty", casual("கோல்டன் பிப்டி"))
    assertEquals("friend", casual("ப்ரெண்ட்"))
    assertEquals("school", casual("ஸ்கூல்"))
    assertEquals("chocolate", casual("சாக்லேட்"))
    assertEquals("taste", casual("டேஸ்ட்"))
    assertEquals("normal aa irukku", casual("நார்மலா இருக்கு")) // English word + Tamil ending
    assertEquals("bike ku", casual("பைக்கு"))
    assertEquals("bus", casual("பஸ்"))
  }

  @Test fun english_words_in_hindi_letters_get_english_spelling() {
    assertEquals("kyaa baat hai", casual("क्या बात है")) // plain Hindi stays as sounds
    assertEquals("phone", casual("फ़ोन"))
    assertEquals("mobile", casual("मोबाइल"))
    assertEquals("ticket", casual("टिकट"))
    assertEquals("office", casual("ऑफिस"))
  }

  @Test fun other_scripts() {
    assertEquals("bus", casual("ಬಸ್ಸು")) // Kannada adds a final u
    assertEquals("ticket", casual("టికెట్"))
    assertEquals("school", casual("സ്കൂൾ"))
  }

  @Test fun native_words_stay_as_sounds() {
    // Tamil
    for ((w, sound) in listOf(
      "வணக்கம்" to "vanakkam", "நண்பர்களே" to "nanbargale", "தமிழ்" to "thamizh", "அது" to "adhu",
      "மகன்" to "magan", "தம்பி" to "thambi", "இந்த" to "indha", "நன்றி" to "nandri", "காற்று" to "kaatru",
      "பச்சை" to "pachai", "வேண்டாம்" to "vendaam", "சாப்பாடு" to "saappaadu", "இருக்கு" to "irukku",
      "வந்து" to "vandhu", "நம்ம" to "namma", "பாருங்க" to "paarunga", "எனக்கு" to "enakku", "போன" to "pona",
      "புல்" to "pul", "பால்" to "paal", "கால்" to "kaal", "மணி" to "mani", "சரி" to "sari", "நான்" to "naan",
      "பேர்" to "per", "பாட்டு" to "paattu", "வீடு" to "veedu", "படம்" to "padam", "செம" to "sema",
      "கடை" to "kadai", "பணம்" to "panam", "காசு" to "kaasu", "நல்லா" to "nallaa", "ரொம்ப" to "romba",
    )) assertEquals(w, sound, casual(w))
    // Hindi
    for ((w, sound) in listOf(
      "क्या" to "kyaa", "बात" to "baat", "है" to "hai", "पल" to "pal", "कल" to "kal", "सब" to "sab",
      "आप" to "aap", "लाल" to "laal", "पानी" to "paanee", "दोस्त" to "dost", "प्यार" to "pyaar", "नहीं" to "naheen",
      "अच्छा" to "achchhaa", "बहुत" to "bahut", "खाना" to "khaanaa", "घर" to "ghar", "दिल" to "dil",
    )) assertEquals(w, sound, casual(w))
  }

  @Test fun exact_mode_is_never_changed() {
    assertEquals("cūppar", Romanizer.romanize("சூப்பர்", PhoneticMode.EXACT))
  }
}
