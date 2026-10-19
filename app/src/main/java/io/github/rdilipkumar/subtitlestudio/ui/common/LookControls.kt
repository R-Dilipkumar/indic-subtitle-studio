package io.github.rdilipkumar.subtitlestudio.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.core.model.Anim
import io.github.rdilipkumar.subtitlestudio.core.model.BoxStyle
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.HighlightMode
import io.github.rdilipkumar.subtitlestudio.core.model.RevealMode
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import io.github.rdilipkumar.subtitlestudio.core.model.Transform
import io.github.rdilipkumar.subtitlestudio.data.CustomFont
import io.github.rdilipkumar.subtitlestudio.render.FontRegistry
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * The look controls, shared by the editor and the setup screen. Each takes the current value and
 * reports changes as (label for undo, key that joins a slider drag into one step, new value).
 */
typealias StyleChange = (label: String, key: String?, style: CaptionStyle) -> Unit
typealias PlaceChange = (label: String, key: String?, transform: Transform) -> Unit

/** The letter "a" of [script], to show how a font writes it. */
private fun sampleLetter(script: Script): String? = when (script) {
  Script.TAMIL -> "அ"
  Script.TELUGU -> "అ"
  Script.KANNADA -> "ಅ"
  Script.MALAYALAM -> "അ"
  Script.DEVANAGARI -> "अ"
  Script.LATIN -> null
}

/** A font button with its name written in that font, plus a letter of the video's own script. */
@Composable
private fun FontPill(f: FontRegistry.FontChoice, script: Script, selected: Boolean, onClick: () -> Unit) {
  val fonts = App.graph.fonts
  val family = remember(f.id) { FontFamily(fonts.typeface(f.id, Script.LATIN, bold = true)) }
  val letter = sampleLetter(script)
  val scriptFamily = remember(f.id, script) { FontFamily(fonts.typeface(f.id, script, bold = true)) }
  val accent = Studio.accent
  Box(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(if (selected) accent.copy(alpha = 0.18f) else Studio.surface2)
      .border(1.dp, if (selected) accent else Studio.outline, RoundedCornerShape(50))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(f.label, fontFamily = family, fontSize = 16.sp, color = if (selected) accent else Studio.text, maxLines = 1)
      if (letter != null) {
        Spacer(Modifier.width(6.dp))
        Text(letter, fontFamily = scriptFamily, fontSize = 16.sp, color = if (selected) accent else Studio.muted, maxLines = 1)
      }
    }
  }
}

/** Tells the font list about added or removed fonts right away (not after the settings save). */
private fun syncFonts() {
  val g = App.graph
  g.fonts.setCustomFonts(g.settings.value.customFonts.map { it.name to File(g.fontsDir, it.file) })
}

/** Font list (each name in its own font), add/remove the user's own font, bold and capitals. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FontControls(st: CaptionStyle, script: Script, onChange: StyleChange, onMessage: (String) -> Unit) {
  fun set(label: String, f: (CaptionStyle) -> CaptionStyle) = onChange(label, null, f(st))
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val settings = App.graph.settings
  val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val name = withContext(Dispatchers.IO) {
        val display = io.github.rdilipkumar.subtitlestudio.media.VideoImporter.displayName(context, uri) ?: "font.ttf"
        val file = File(App.graph.fontsDir, "f${System.currentTimeMillis()}_" + display.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        context.contentResolver.openInputStream(uri)?.use { i -> file.outputStream().use { i.copyTo(it) } }
        val ok = runCatching { android.graphics.Typeface.createFromFile(file) }.getOrNull()?.let { it != android.graphics.Typeface.DEFAULT } == true
        if (!ok) {
          file.delete()
          null
        } else {
          display.substringBeforeLast('.') to file.name
        }
      }
      if (name == null) onMessage("That file is not a font the phone can read") else {
        settings.update { it.copy(customFonts = it.customFonts + CustomFont("custom:${name.second}", name.first, name.second)) }
        syncFonts()
        set("Font: ${name.first}") { it.copy(fontId = "custom:${name.second}") }
        onMessage("Added font \"${name.first}\"")
      }
    }
  }

  val hint = if (sampleLetter(script) != null) {
    "Each name is shown in its own font. The letter next to it is how your video's language looks in that font."
  } else {
    "Each name is shown in its own font. Add any .ttf or .otf from your phone."
  }
  SectionTitle("Font", hint = hint)
  val custom by settings.state.collectAsState()
  val fontChoices = remember(custom.customFonts) { App.graph.fonts.all }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (f in fontChoices) FontPill(f, script, f.id == st.fontId) { set("Font: ${f.label}") { it.copy(fontId = f.id) } }
  }
  Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Pill("Add your own font", false, { pickFont.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) }, icon = Icons.Rounded.Add)
    val current = fontChoices.firstOrNull { it.id == st.fontId }
    if (current?.custom != null) {
      Pill("Remove this font", false, {
        settings.update { it.copy(customFonts = it.customFonts.filterNot { c -> c.id == current.id }) }
        syncFonts()
        current.custom.delete()
        set("Font: ${fontChoices.first().label}") { it.copy(fontId = CaptionStyle.FONT_DEFAULT) }
      }, icon = Icons.Rounded.Delete)
    }
  }
  SwitchRow("Bold", st.bold, { v -> set("Bold") { it.copy(bold = v) } })
  SwitchRow("CAPITAL English letters", st.uppercaseLatin, { v -> set("Capitals") { it.copy(uppercaseLatin = v) } })
}

/** Text size, the size of the lines under the first, and how wide a line may get. */
@Composable
fun SizeControls(st: CaptionStyle, onChange: StyleChange) {
  SectionTitle("Size")
  LabeledSlider("Text size", st.textSize, 0.022f..0.11f, { v -> onChange("Text size", "size", st.copy(textSize = v)) }, "${(st.textSize * 1000).toInt()}")
  LabeledSlider("Second and third lines", st.secondaryScale, 0.45f..1f, { v -> onChange("Line sizes", "size2", st.copy(secondaryScale = v)) }, "${(st.secondaryScale * 100).toInt()}%")
  LabeledSlider("Line width", st.maxWidth, 0.4f..0.98f, { v -> onChange("Line width", "width", st.copy(maxWidth = v)) }, "${(st.maxWidth * 100).toInt()}%")
}

/** Colour of each line, the spoken word, the outline and the box. */
@Composable
fun ColourControls(st: CaptionStyle, onChange: StyleChange) {
  var target by rememberSaveable { mutableStateOf(0) }
  SectionTitle("Colours")
  val targets = listOf("Words", "English letters", "Translation", "Highlight", "Outline", "Box")
  PillRow(targets.indices.toList(), target, { targets[it] }, { target = it })
  val current = when (target) {
    0 -> st.textColor
    1 -> st.phoneticColor
    2 -> st.translationColor
    3 -> st.highlightColor
    4 -> st.strokeColor
    else -> st.boxColor
  }
  Row(Modifier.padding(vertical = 8.dp)) {
    ColorSwatches(StylePresets.swatches, current) { c ->
      onChange(
        "Colour: ${targets[target]}",
        null,
        when (target) {
          0 -> st.copy(textColor = c)
          1 -> st.copy(phoneticColor = c)
          2 -> st.copy(translationColor = c)
          3 -> st.copy(highlightColor = c)
          4 -> st.copy(strokeColor = c)
          else -> st.copy(boxColor = c, box = if (st.box == BoxStyle.NONE) BoxStyle.BLOCK else st.box)
        },
      )
    }
  }
}

/** Outline, shadow and the box behind the text. */
@Composable
fun OutlineControls(st: CaptionStyle, onChange: StyleChange) {
  SectionTitle("Outline and background")
  LabeledSlider("Outline", st.strokeWidth, 0f..0.4f, { v -> onChange("Outline", "stroke", st.copy(strokeWidth = v)) }, if (st.strokeWidth < 0.01f) "Off" else "${(st.strokeWidth * 100).toInt()}")
  SwitchRow("Shadow", st.shadow, { v -> onChange("Shadow", null, st.copy(shadow = v)) })
  PillRow(BoxStyle.entries, st.box, {
    when (it) {
      BoxStyle.NONE -> "No box"
      BoxStyle.LINES -> "Box per line"
      BoxStyle.BLOCK -> "One box"
    }
  }, { b -> onChange("Background box", null, st.copy(box = b)) })
  if (st.box != BoxStyle.NONE) {
    LabeledSlider("Box corners", st.boxRadius, 0f..0.6f, { v -> onChange("Box corners", "radius", st.copy(boxRadius = v)) }, "${(st.boxRadius * 100).toInt()}")
  }
}

/** How captions and words appear, stand out and disappear. Works on every line. */
@Composable
fun MotionControls(st: CaptionStyle, onChange: StyleChange) {
  SectionTitle("Words appear")
  PillFlow(RevealMode.entries, st.reveal, {
    when (it) {
      RevealMode.ALL -> "All at once"
      RevealMode.WORD_BY_WORD -> "One by one"
      RevealMode.LETTER_BY_LETTER -> "Typing (letter by letter)"
    }
  }, { r -> onChange("Words appear", null, st.copy(reveal = r)) })
  if (st.reveal == RevealMode.WORD_BY_WORD) {
    SectionTitle("Each word comes in")
    PillFlow(Anim.entries, st.wordAnim, ::animLabel, { a -> onChange("Each word: ${animLabel(a)}", null, st.copy(wordAnim = a)) })
  }

  SectionTitle("Word being spoken", hint = "How the current word stands out")
  PillFlow(HighlightMode.entries, st.highlight, {
    when (it) {
      HighlightMode.NONE -> "Nothing"
      HighlightMode.COLOR -> "Colour"
      HighlightMode.SWEEP -> "Karaoke sweep"
      HighlightMode.POP -> "Pop"
      HighlightMode.BOX -> "Box"
    }
  }, { h -> onChange("Highlight", null, st.copy(highlight = h)) })

  SectionTitle("Caption comes in")
  PillFlow(Anim.entries, st.entrance, ::animLabel, { a -> onChange("Comes in: ${animLabel(a)}", null, st.copy(entrance = a)) })
  LabeledSlider("Coming in takes", st.animMs.toFloat(), 0f..1000f, { v -> onChange("Coming in speed", "anim-in", st.copy(animMs = v.toInt())) }, "${st.animMs} ms")

  SectionTitle("Caption goes out")
  PillFlow(Anim.entries, st.exit, ::animLabel, { a -> onChange("Goes out: ${animLabel(a)}", null, st.copy(exit = a)) })
  LabeledSlider("Going out takes", st.exitMs.toFloat(), 0f..1000f, { v -> onChange("Going out speed", "anim-out", st.copy(exitMs = v.toInt())) }, "${st.exitMs} ms")
}

fun animLabel(a: Anim) = when (a) {
  Anim.NONE -> "None"
  Anim.FADE -> "Fade"
  Anim.POP -> "Pop"
  Anim.SLIDE_UP -> "Slide up"
  Anim.SLIDE_DOWN -> "Slide down"
  Anim.SLIDE_LEFT -> "Slide left"
  Anim.SLIDE_RIGHT -> "Slide right"
  Anim.ZOOM -> "Zoom"
  Anim.BOUNCE -> "Bounce"
  Anim.DROP -> "Drop"
  Anim.WIPE -> "Wipe"
}

/** Where the caption block sits: quick spots, size and angle. */
@Composable
fun PlaceControls(t: Transform, onChange: PlaceChange) {
  SectionTitle("Quick spots")
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    for ((label, y) in listOf("Top" to 0.18f, "Middle" to 0.5f, "Lower third" to 0.68f, "Bottom" to 0.86f)) {
      Pill(label, abs(t.y - y) < 0.02f && t.x == 0.5f, { onChange("Moved captions", null, t.copy(x = 0.5f, y = y)) })
    }
  }
  SectionTitle("Size and angle")
  LabeledSlider("Size", t.scale, 0.3f..3f, { onChange("Resized captions", "scale", t.copy(scale = it)) }, "${(t.scale * 100).toInt()}%")
  LabeledSlider("Angle", t.rotation, -45f..45f, { onChange("Turned captions", "rotate", t.copy(rotation = it)) }, "${t.rotation.toInt()}°")
  TextButton(onClick = { onChange("Reset size and angle", null, Transform(y = t.y)) }) { Text("Reset size, angle and centre") }
}

/**
 * One step of moving a caption with fingers: [pan] in pixels of a view [width] x [height],
 * [zoom] and [rotation] (degrees) since the last step. Snaps to the centre lines and to level.
 */
object CaptionDrag {
  /** [raw] follows the fingers exactly; [shown] is snapped, and is what to apply. */
  data class Step(val raw: Transform, val shown: Transform, val guideX: Boolean, val guideY: Boolean)

  fun step(cur: Transform, pan: Offset, zoom: Float, rotation: Float, width: Float, height: Float): Step {
    val raw = cur.copy(
      x = (cur.x + pan.x / width).coerceIn(0.02f, 0.98f),
      y = (cur.y + pan.y / height).coerceIn(0.02f, 0.98f),
      scale = (cur.scale * zoom).coerceIn(0.3f, 4f),
      rotation = cur.rotation + rotation,
    )
    val gx = abs(raw.x - 0.5f) < 0.015f
    val gy = abs(raw.y - 0.5f) < 0.012f
    val shown = raw.copy(
      x = if (gx) 0.5f else raw.x,
      y = if (gy) 0.5f else raw.y,
      rotation = if (abs(raw.rotation) < 3f) 0f else raw.rotation,
    )
    return Step(raw, shown, gx, gy)
  }
}
