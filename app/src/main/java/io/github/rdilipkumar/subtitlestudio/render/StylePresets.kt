package io.github.rdilipkumar.subtitlestudio.render

import io.github.rdilipkumar.subtitlestudio.core.model.Anim
import io.github.rdilipkumar.subtitlestudio.core.model.BoxStyle
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.HighlightMode
import io.github.rdilipkumar.subtitlestudio.core.model.RevealMode
import io.github.rdilipkumar.subtitlestudio.core.model.Transform

/** Ready-made caption looks. Picking one replaces the look; every part can be changed after. */
object StylePresets {

  data class Preset(val id: String, val label: String, val style: CaptionStyle, val transform: Transform = Transform())

  val all: List<Preset> = listOf(
    Preset(
      "pop", "Pop",
      CaptionStyle(presetId = "pop", fontId = "baloo", textSize = 0.052f, highlight = HighlightMode.POP, highlightColor = 0xFF2BFF88.toInt(), entrance = Anim.POP),
    ),
    Preset(
      "karaoke", "Karaoke",
      CaptionStyle(presetId = "karaoke", fontId = "anek", textSize = 0.048f, highlight = HighlightMode.SWEEP, highlightColor = 0xFFFFD60A.toInt(), entrance = Anim.FADE, phoneticColor = 0xFFFFFFFF.toInt()),
    ),
    Preset(
      "boxed", "Boxed",
      CaptionStyle(presetId = "boxed", fontId = "system", textSize = 0.044f, strokeWidth = 0f, shadow = false, box = BoxStyle.BLOCK, boxColor = 0xCC111111.toInt(), highlight = HighlightMode.COLOR, highlightColor = 0xFF5EEAD4.toInt(), entrance = Anim.SLIDE_UP),
    ),
    Preset(
      "bold", "Big & bold",
      CaptionStyle(presetId = "bold", fontId = "poppins", textSize = 0.060f, textColor = 0xFFFFE600.toInt(), phoneticColor = 0xFFFFFFFF.toInt(), strokeWidth = 0.22f, uppercaseLatin = true, highlight = HighlightMode.POP, highlightColor = 0xFFFF3D7F.toInt(), entrance = Anim.BOUNCE),
      Transform(y = 0.62f),
    ),
    Preset(
      "reveal", "Word by word",
      CaptionStyle(presetId = "reveal", fontId = "baloo", textSize = 0.054f, reveal = RevealMode.WORD_BY_WORD, highlight = HighlightMode.COLOR, highlightColor = 0xFF7DD3FC.toInt(), entrance = Anim.NONE),
    ),
    Preset(
      "highlight", "Highlight box",
      CaptionStyle(presetId = "highlight", fontId = "montserrat", textSize = 0.048f, highlight = HighlightMode.BOX, highlightColor = 0xFF7C3AED.toInt(), strokeWidth = 0.08f, entrance = Anim.POP),
    ),
    Preset(
      "classic", "Classic film",
      CaptionStyle(presetId = "classic", fontId = "system", textSize = 0.036f, bold = false, strokeWidth = 0.10f, highlight = HighlightMode.NONE, entrance = Anim.NONE, exit = Anim.NONE, phoneticColor = 0xFFE5E7EB.toInt(), maxWidth = 0.9f),
      Transform(y = 0.88f),
    ),
    Preset(
      "minimal", "Minimal",
      CaptionStyle(presetId = "minimal", fontId = "anek", textSize = 0.042f, strokeWidth = 0f, shadow = true, highlight = HighlightMode.NONE, entrance = Anim.FADE, phoneticColor = 0xFFD1D5DB.toInt()),
    ),
  )

  fun byId(id: String): Preset = all.firstOrNull { it.id == id } ?: all.first()

  /** Colours offered in the colour pickers. */
  val swatches: List<Int> = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFE600, 0xFFFFD60A, 0xFFFF9F1C, 0xFFFF3D7F, 0xFFEF4444,
    0xFF2BFF88, 0xFF22C55E, 0xFF5EEAD4, 0xFF7DD3FC, 0xFF3B82F6, 0xFF7C3AED, 0xFFE879F9,
    0xFFB3E5FC, 0xFFFFE066, 0xFFD1D5DB, 0xFF6B7280, 0xCC111111, 0x99000000,
  ).map { it.toInt() }
}
