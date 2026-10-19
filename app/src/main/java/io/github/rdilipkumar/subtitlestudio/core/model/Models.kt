package io.github.rdilipkumar.subtitlestudio.core.model

import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import kotlinx.serialization.Serializable

/**
 * Plain data for one subtitle project. Everything here is pure Kotlin (no Android) so the
 * editing rules can be unit-tested on the JVM.
 *
 * Times are milliseconds from the start of the video.
 */

@Serializable
enum class Script { TAMIL, TELUGU, KANNADA, MALAYALAM, DEVANAGARI, LATIN }

@Serializable
enum class Lang(
  val code: String,
  val label: String,
  val nativeLabel: String,
  val script: Script,
  /** FLORES-200 code used by the IndicTrans2 translator. */
  val floresCode: String,
) {
  TAMIL("ta", "Tamil", "தமிழ்", Script.TAMIL, "tam_Taml"),
  /**
   * Tamil talk full of English words. Captions keep English words in English and write the
   * Tamil in English letters: "Hi makkale vibe pannalaamaa".
   */
  TANGLISH("ta", "Tanglish", "Tamil + English", Script.TAMIL, "tam_Taml"),
  TELUGU("te", "Telugu", "తెలుగు", Script.TELUGU, "tel_Telu"),
  KANNADA("kn", "Kannada", "ಕನ್ನಡ", Script.KANNADA, "kan_Knda"),
  MALAYALAM("ml", "Malayalam", "മലയാളം", Script.MALAYALAM, "mal_Mlym"),
  HINDI("hi", "Hindi", "हिन्दी", Script.DEVANAGARI, "hin_Deva"),
  ENGLISH("en", "English", "English", Script.LATIN, "eng_Latn");

  val isIndic: Boolean get() = this != ENGLISH

  /** Can be picked as a language to translate into. */
  val isTranslationTarget: Boolean get() = this != TANGLISH
}

/** How the "English letters" line is written. */
@Serializable
enum class PhoneticMode {
  /** How people type in chats: "vanakkam nanbargale". */
  CASUAL,
  /** Precise ISO 15919 letters with accent marks: "vaṇakkam naṇparkaḷē". */
  EXACT,
}

@Serializable
enum class Origin { AI, USER, IMPORTED }

@Serializable
enum class TranslationState {
  /** No translation made yet. */
  NONE,
  /** Made by the offline translator and still matches the words. */
  AUTO,
  /** Typed or fixed by the user. */
  USER,
  /** The words changed after it was made, so it may no longer match. */
  STALE,
}

@Serializable
data class Word(
  val id: String,
  /** The word as spoken, in its own script (or Latin for English). */
  val text: String,
  /** The word in English letters, by sound. */
  val phonetic: String,
  val startMs: Long,
  val endMs: Long,
  /** 0..1. Low values are flagged in the editor so the user knows where to check. */
  val confidence: Float = 1f,
  /** The user typed the phonetic spelling themselves, so it is never regenerated. */
  val phoneticByUser: Boolean = false,
  /** The user changed this word's text. */
  val edited: Boolean = false,
) {
  val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1)
}

@Serializable
data class Cue(
  val id: String,
  val startMs: Long,
  val endMs: Long,
  val words: List<Word>,
  val translation: String = "",
  val translationState: TranslationState = TranslationState.NONE,
  val origin: Origin = Origin.AI,
  /** Any change made by the user (text, timing, look or position). */
  val edited: Boolean = false,
  /** This caption's own look, or null to follow the project look. */
  val style: CaptionStyle? = null,
  /** This caption's own position, or null to follow the project position. */
  val transform: Transform? = null,
) {
  val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1)
  val text: String get() = words.joinToString(" ") { it.text }
  val phonetic: String get() = words.joinToString(" ") { it.phonetic }
  val lowConfidence: Boolean get() = words.any { it.confidence < LOW_CONFIDENCE }

  fun contains(timeMs: Long): Boolean = timeMs in startMs until endMs

  companion object {
    const val LOW_CONFIDENCE = 0.6f
  }
}

/** Where the caption block sits on the video. x/y are its centre, 0..1 of the frame. */
@Serializable
data class Transform(
  val x: Float = 0.5f,
  val y: Float = 0.72f,
  val scale: Float = 1f,
  val rotation: Float = 0f,
)

@Serializable
enum class TrackKind { NATIVE, PHONETIC, TRANSLATION }

@Serializable
data class TrackSettings(
  val showNative: Boolean = true,
  val showPhonetic: Boolean = true,
  val showTranslation: Boolean = false,
  /** Top-to-bottom order. The first visible line is drawn largest. */
  val order: List<TrackKind> = listOf(TrackKind.NATIVE, TrackKind.PHONETIC, TrackKind.TRANSLATION),
  /**
   * How much of the translation shows at once, on its own timing. Null keeps each caption's own
   * piece, so the translation changes together with the captions.
   */
  val translationSize: CaptionLength? = null,
) {
  fun isVisible(kind: TrackKind): Boolean = when (kind) {
    TrackKind.NATIVE -> showNative
    TrackKind.PHONETIC -> showPhonetic
    TrackKind.TRANSLATION -> showTranslation
  }

  fun visibleInOrder(): List<TrackKind> = order.filter { isVisible(it) }

  fun with(kind: TrackKind, visible: Boolean): TrackSettings = when (kind) {
    TrackKind.NATIVE -> copy(showNative = visible)
    TrackKind.PHONETIC -> copy(showPhonetic = visible)
    TrackKind.TRANSLATION -> copy(showTranslation = visible)
  }

  /** Which of the two subtitle modes is on, or null for a custom mix. */
  val mode: SubtitleMode?
    get() = when {
      showPhonetic && !showTranslation -> SubtitleMode.PHONETIC
      showTranslation && !showPhonetic -> SubtitleMode.TRANSLATION
      else -> null
    }

  /** Switches the subtitle mode; its line becomes the main (largest) one. The original-words line is left as it is. */
  fun withMode(m: SubtitleMode): TrackSettings {
    val main = if (m == SubtitleMode.PHONETIC) TrackKind.PHONETIC else TrackKind.TRANSLATION
    return copy(
      showPhonetic = m == SubtitleMode.PHONETIC,
      showTranslation = m == SubtitleMode.TRANSLATION,
      order = listOf(main) + order.filter { it != main },
    )
  }
}

/**
 * What the subtitles say. PHONETIC ("phonetic bypass") writes the sounds in English letters
 * with no translation: Hindi "क्या बात है" shows as "kya baat hai", and English words keep their
 * English spelling. TRANSLATION shows the meaning in another language.
 */
@Serializable
enum class SubtitleMode { PHONETIC, TRANSLATION }

@Serializable
enum class BoxStyle { NONE, LINES, BLOCK }

@Serializable
enum class HighlightMode {
  /** All words look the same. */
  NONE,
  /** The word being spoken changes colour. */
  COLOR,
  /** Colour sweeps through each word as it is spoken (karaoke). */
  SWEEP,
  /** The word being spoken grows a little and changes colour. */
  POP,
  /** A coloured box sits behind the word being spoken. */
  BOX,
}

@Serializable
enum class RevealMode {
  /** The whole caption shows at once. */
  ALL,
  /** Words appear one by one as they are spoken. */
  WORD_BY_WORD,
  /** Letters type out as each word is spoken. */
  LETTER_BY_LETTER,
}

/**
 * How a caption (or a word) comes in or goes out. Slides are named by the direction of travel:
 * SLIDE_UP comes in from below and leaves upwards. WIPE uncovers from the left.
 */
@Serializable
enum class Anim { NONE, FADE, POP, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, ZOOM, BOUNCE, DROP, WIPE }

/**
 * The look of captions. Sizes are fractions so they look the same on any video size:
 * [textSize] is a fraction of the video height; stroke and box sizes are fractions of the
 * text size.
 */
@Serializable
data class CaptionStyle(
  val presetId: String = "pop",
  val fontId: String = FONT_DEFAULT,
  val textSize: Float = 0.050f,
  /** Size of the second and third lines compared to the first. */
  val secondaryScale: Float = 0.72f,
  val textColor: Int = WHITE,
  val phoneticColor: Int = 0xFFFFE066.toInt(),
  val translationColor: Int = 0xFFB3E5FC.toInt(),
  val highlightColor: Int = 0xFF2BFF88.toInt(),
  val strokeColor: Int = BLACK,
  val strokeWidth: Float = 0.14f,
  val shadow: Boolean = true,
  val box: BoxStyle = BoxStyle.NONE,
  val boxColor: Int = 0xB3000000.toInt(),
  val boxRadius: Float = 0.25f,
  val bold: Boolean = true,
  val uppercaseLatin: Boolean = false,
  val highlight: HighlightMode = HighlightMode.POP,
  val reveal: RevealMode = RevealMode.ALL,
  val entrance: Anim = Anim.POP,
  val exit: Anim = Anim.FADE,
  /** How long the caption takes to come in. */
  val animMs: Int = 180,
  /** How long the caption takes to go out. */
  val exitMs: Int = 180,
  /** How each word comes in when words appear one by one. */
  val wordAnim: Anim = Anim.POP,
  /** Widest a line may get before wrapping, as a fraction of the video width. */
  val maxWidth: Float = 0.86f,
  val lineSpacing: Float = 0.18f,
) {
  companion object {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val FONT_DEFAULT = "system"
  }
}

/** Phone app screen areas covered by buttons and captions, so subtitles can avoid them. */
@Serializable
enum class SafeZone(
  val label: String,
  val top: Float,
  val bottom: Float,
  val left: Float,
  val right: Float,
) {
  NONE("Off", 0f, 0f, 0f, 0f),
  REELS("Instagram Reels", 0.12f, 0.24f, 0.05f, 0.16f),
  SHORTS("YouTube Shorts", 0.11f, 0.22f, 0.05f, 0.15f),
  TIKTOK("TikTok", 0.13f, 0.26f, 0.05f, 0.18f),
}

@Serializable
data class SubtitleDoc(
  val cues: List<Cue> = emptyList(),
  val style: CaptionStyle = CaptionStyle(),
  val transform: Transform = Transform(),
  val tracks: TrackSettings = TrackSettings(),
  val phoneticMode: PhoneticMode = PhoneticMode.CASUAL,
) {
  fun cue(id: String): Cue? = cues.firstOrNull { it.id == id }
  fun indexOf(id: String): Int = cues.indexOfFirst { it.id == id }
  fun styleOf(cue: Cue): CaptionStyle = cue.style ?: style
  fun transformOf(cue: Cue): Transform = cue.transform ?: transform

  /** The caption on screen at [timeMs], if any. Cues are kept sorted and never overlap. */
  fun cueAt(timeMs: Long): Cue? {
    var lo = 0
    var hi = cues.lastIndex
    while (lo <= hi) {
      val mid = (lo + hi) ushr 1
      val c = cues[mid]
      when {
        timeMs < c.startMs -> hi = mid - 1
        timeMs >= c.endMs -> lo = mid + 1
        else -> return c
      }
    }
    return null
  }
}

@Serializable
enum class ProjectStatus { NEW, READY }

@Serializable
data class ProjectMeta(
  val id: String,
  val name: String,
  val createdAt: Long,
  val updatedAt: Long,
  val videoFile: String = "source.mp4",
  val durationMs: Long = 0,
  /** Size as shown on screen (rotation already applied). */
  val width: Int = 0,
  val height: Int = 0,
  val language: Lang? = null,
  val translateTo: Lang? = null,
  val status: ProjectStatus = ProjectStatus.NEW,
  val safeZone: SafeZone = SafeZone.NONE,
)

@Serializable
data class ProjectFile(
  val meta: ProjectMeta,
  val doc: SubtitleDoc = SubtitleDoc(),
  /** The captions exactly as the AI made them, for "compare with original" and reset. */
  val aiDoc: SubtitleDoc? = null,
)
