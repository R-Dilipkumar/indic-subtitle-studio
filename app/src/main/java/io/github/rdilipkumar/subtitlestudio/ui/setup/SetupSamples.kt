package io.github.rdilipkumar.subtitlestudio.ui.setup

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.core.model.TrackSettings
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.core.timing.CueBuilder
import io.github.rdilipkumar.subtitlestudio.core.timing.IdGen
import io.github.rdilipkumar.subtitlestudio.core.timing.TimedWord
import io.github.rdilipkumar.subtitlestudio.render.StylePresets

/** A made-up sentence in each language, cut into captions exactly as the real run would. */
object SetupSamples {

  /** Spoken sentence and its English meaning. All say the same thing, so any pair translates. */
  val SENTENCES: Map<Lang, Pair<String, String>> = mapOf(
    Lang.TAMIL to ("வணக்கம் நண்பர்களே இன்னைக்கு நாம ஒரு புது இடத்துக்கு போறோம்" to "Hello friends, today we are going to a new place"),
    Lang.TANGLISH to ("Hi மக்களே இன்னைக்கு நாம ஒரு new place போறோம்" to "Hi folks, today we are going to a new place"),
    Lang.TELUGU to ("నమస్కారం స్నేహితులారా ఈ రోజు మనం ఒక కొత్త ప్రదేశానికి వెళ్తున్నాం" to "Hello friends, today we are going to a new place"),
    Lang.KANNADA to ("ನಮಸ್ಕಾರ ಸ್ನೇಹಿತರೇ ಇಂದು ನಾವು ಒಂದು ಹೊಸ ಜಾಗಕ್ಕೆ ಹೋಗುತ್ತಿದ್ದೇವೆ" to "Hello friends, today we are going to a new place"),
    Lang.MALAYALAM to ("നമസ്കാരം സുഹൃത്തുക്കളേ ഇന്ന് നമ്മൾ ഒരു പുതിയ സ്ഥലത്തേക്ക് പോകുന്നു" to "Hello friends, today we are going to a new place"),
    Lang.HINDI to ("नमस्ते दोस्तों आज हम एक नई जगह पर जा रहे हैं" to "Hello friends, today we are going to a new place"),
    Lang.ENGLISH to ("Hello friends, today we are going to a new place" to "Hello friends, today we are going to a new place"),
  )

  private const val WORD_MS = 420L

  fun words(l: Lang): List<String> = SENTENCES.getValue(l).first.split(' ')

  fun phonetic(l: Lang, text: String, mode: PhoneticMode): String = if (l.isIndic) Romanizer.romanize(text, mode) else text

  /** The sentence as the translation line would show it. */
  fun translation(from: Lang, to: Lang?): String = when {
    to == null -> ""
    to == Lang.ENGLISH -> SENTENCES.getValue(from).second
    else -> SENTENCES.getValue(to).first
  }

  /** The visible lines, with [main] on top. */
  fun tracks(c: SetupChoices): TrackSettings {
    val t = TrackSettings(
      showNative = c.showNative,
      showPhonetic = c.showPhonetic && c.language.isIndic,
      showTranslation = c.showTranslation && c.translateTo != null,
    )
    return t.copy(order = listOf(c.main) + t.order.filter { it != c.main })
  }

  /** The sample sentence as captions of [length] in look [presetId], plus how long it plays. */
  fun build(c: SetupChoices, presetId: String = c.presetId, length: CaptionLength = c.length): Pair<SubtitleDoc, Long> {
    var n = 0
    val ids = IdGen { "s${n++}" }
    val timed = words(c.language).mapIndexed { i, w -> TimedWord(w, 300 + i * WORD_MS, 300 + i * WORD_MS + WORD_MS - 40) }
    val translation = translation(c.language, c.translateTo)
    val cues = CueBuilder.build(timed, length, { phonetic(c.language, it, c.phoneticMode) }, ids)
      .map { it.copy(translation = translation) }
    val preset = StylePresets.byId(presetId)
    val doc = SubtitleDoc(cues = cues, style = preset.style, transform = preset.transform, tracks = tracks(c), phoneticMode = c.phoneticMode)
    return doc to ((cues.lastOrNull()?.endMs ?: 0L) + 700)
  }

  /** The first caption's words as the user will read them (English letters when shown). */
  fun firstCaptionText(c: SetupChoices, length: CaptionLength): String {
    val cue: Cue = build(c, length = length).first.cues.firstOrNull() ?: return ""
    return if (c.language.isIndic && (c.main == TrackKind.PHONETIC || (c.showPhonetic && c.main != TrackKind.NATIVE))) cue.phonetic else cue.text
  }
}

/** Plain-words details for the language picker. */
object LanguageInfo {

  enum class Group(val title: String) { SOUTH("South Indian languages"), MORE("More languages") }

  val order: List<Lang> = listOf(Lang.TAMIL, Lang.TANGLISH, Lang.MALAYALAM, Lang.TELUGU, Lang.KANNADA, Lang.HINDI, Lang.ENGLISH)

  fun group(l: Lang): Group = if (l == Lang.HINDI || l == Lang.ENGLISH) Group.MORE else Group.SOUTH

  /** The letter "a" in the language's own script. */
  fun glyph(l: Lang): String = when (l) {
    Lang.TAMIL -> "அ"
    Lang.TANGLISH -> "அA"
    Lang.TELUGU -> "అ"
    Lang.KANNADA -> "ಅ"
    Lang.MALAYALAM -> "അ"
    Lang.HINDI -> "अ"
    Lang.ENGLISH -> "A"
  }

  fun description(l: Lang): String = when (l) {
    Lang.TAMIL -> "Tamil talk with only a few English words. Good for films, speeches, news and pure Tamil vlogs."
    Lang.TANGLISH -> "Tamil talk mixed with lots of English words, like most YouTube vlogs. English words come out in English."
    Lang.MALAYALAM -> "Malayalam talk: vlogs, films and speeches."
    Lang.TELUGU -> "Telugu talk. Uses a smaller speech model for now, so expect a few more fixes."
    Lang.KANNADA -> "Kannada talk. Uses a smaller speech model for now, so expect a few more fixes."
    Lang.HINDI -> "Hindi talk: vlogs, films and speeches."
    Lang.ENGLISH -> "English talk. You can add a translation in Tamil, Telugu, Kannada, Malayalam or Hindi."
  }

  /** "Tamil · தமிழ்", or just the name when both are the same. */
  fun name(l: Lang): String = if (l == Lang.ENGLISH) l.label else "${l.label} · ${l.nativeLabel}"
}
