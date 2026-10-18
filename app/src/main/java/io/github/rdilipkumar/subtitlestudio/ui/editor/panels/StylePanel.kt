package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.core.model.BoxStyle
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.data.CustomFont
import io.github.rdilipkumar.subtitlestudio.render.FontRegistry
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import io.github.rdilipkumar.subtitlestudio.ui.common.ColorSwatches
import io.github.rdilipkumar.subtitlestudio.ui.common.LabeledSlider
import io.github.rdilipkumar.subtitlestudio.ui.common.Pill
import io.github.rdilipkumar.subtitlestudio.ui.common.PillRow
import io.github.rdilipkumar.subtitlestudio.ui.common.SectionTitle
import io.github.rdilipkumar.subtitlestudio.ui.common.SwitchRow
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel
import io.github.rdilipkumar.subtitlestudio.ui.editor.Scope
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A font button with its name written in that font. */
@Composable
private fun FontPill(f: FontRegistry.FontChoice, selected: Boolean, onClick: () -> Unit) {
  val family = remember(f.id) { FontFamily(App.graph.fonts.typeface(f.id, Script.LATIN, bold = true)) }
  val accent = Studio.accent
  Box(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(if (selected) accent.copy(alpha = 0.18f) else Studio.surface2)
      .border(1.dp, if (selected) accent else Studio.outline, RoundedCornerShape(50))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 6.dp),
  ) {
    Text(f.label, fontFamily = family, fontSize = 16.sp, color = if (selected) accent else Studio.text, maxLines = 1)
  }
}

/** Choose "all captions" or "this caption" for look and position changes. */
@Composable
internal fun ScopeRow(ui: EditorUi, vm: EditorViewModel) {
  val sel = ui.selected
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
    Text("Change", color = Studio.muted, fontSize = 12.sp)
    Pill("All captions", ui.scope == Scope.ALL, { vm.setScope(Scope.ALL) })
    Pill("This caption", ui.scope == Scope.THIS, { vm.setScope(Scope.THIS) }, enabled = sel != null)
    if (sel != null && (sel.style != null || sel.transform != null)) {
      TextButton(onClick = { vm.resetCueLook(sel.id) }) { Text("Reset this one") }
    }
  }
}

/** Tells the font list about added or removed fonts right away (not after the settings save). */
private fun syncFonts() {
  val g = App.graph
  g.fonts.setCustomFonts(g.settings.value.customFonts.map { it.name to File(g.fontsDir, it.file) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StylePanel(ui: EditorUi, vm: EditorViewModel) {
  val st = vm.editingStyle()
  fun set(label: String, key: String? = null, f: (CaptionStyle) -> CaptionStyle) = vm.applyStyle(f(st), label, key)
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
      if (name == null) vm.toast("That file is not a font the phone can read") else {
        settings.update { it.copy(customFonts = it.customFonts + CustomFont("custom:${name.second}", name.first, name.second)) }
        syncFonts()
        set("Font: ${name.first}") { it.copy(fontId = "custom:${name.second}") }
        vm.toast("Added font \"${name.first}\"")
      }
    }
  }
  var colorTarget by remember { mutableStateOf(0) }

  Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
    ScopeRow(ui, vm)

    SectionTitle("Ready-made looks")
    PillRow(StylePresets.all, StylePresets.byId(st.presetId), { it.label }, { vm.applyPreset(it.id) })

    SectionTitle("Lines on screen", hint = "Use the arrows to change the order. The top line is the biggest.")
    val tracks = ui.doc.tracks
    for (kind in tracks.order) {
      val label = when (kind) {
        TrackKind.NATIVE -> "Original words"
        TrackKind.PHONETIC -> "English letters"
        TrackKind.TRANSLATION -> "Translation"
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          SwitchRow(label, tracks.isVisible(kind), { vm.setTracks(tracks.with(kind, it)) })
        }
        IconButton(onClick = { vm.moveTrack(kind, up = true) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Up") }
        IconButton(onClick = { vm.moveTrack(kind, up = false) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Down") }
      }
    }
    if (ui.language.isIndic) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Casual letters", ui.doc.phoneticMode == PhoneticMode.CASUAL, { vm.setPhoneticMode(PhoneticMode.CASUAL) })
        Pill("Exact letters", ui.doc.phoneticMode == PhoneticMode.EXACT, { vm.setPhoneticMode(PhoneticMode.EXACT) })
      }
    }

    SectionTitle("Font", hint = "Each name is shown in its own font. Add any .ttf or .otf from your phone.")
    val customFonts by settings.state.collectAsState()
    val fontChoices = remember(customFonts.customFonts) { App.graph.fonts.all }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for (f in fontChoices) FontPill(f, f.id == st.fontId) { set("Font: ${f.label}") { it.copy(fontId = f.id) } }
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

    SectionTitle("Size")
    LabeledSlider("Text size", st.textSize, 0.022f..0.11f, { v -> set("Text size", "size") { it.copy(textSize = v) } }, "${(st.textSize * 1000).toInt()}")
    LabeledSlider("Second and third lines", st.secondaryScale, 0.45f..1f, { v -> set("Line sizes", "size2") { it.copy(secondaryScale = v) } }, "${(st.secondaryScale * 100).toInt()}%")
    LabeledSlider("Line width", st.maxWidth, 0.4f..0.98f, { v -> set("Line width", "width") { it.copy(maxWidth = v) } }, "${(st.maxWidth * 100).toInt()}%")

    SectionTitle("Colours")
    val targets = listOf("Words", "English letters", "Translation", "Highlight", "Outline", "Box")
    PillRow(targets.indices.toList(), colorTarget, { targets[it] }, { colorTarget = it })
    val current = when (colorTarget) {
      0 -> st.textColor
      1 -> st.phoneticColor
      2 -> st.translationColor
      3 -> st.highlightColor
      4 -> st.strokeColor
      else -> st.boxColor
    }
    Row(Modifier.padding(vertical = 8.dp)) {
      ColorSwatches(StylePresets.swatches, current) { c ->
        set("Colour: ${targets[colorTarget]}") {
          when (colorTarget) {
            0 -> it.copy(textColor = c)
            1 -> it.copy(phoneticColor = c)
            2 -> it.copy(translationColor = c)
            3 -> it.copy(highlightColor = c)
            4 -> it.copy(strokeColor = c)
            else -> it.copy(boxColor = c, box = if (it.box == BoxStyle.NONE) BoxStyle.BLOCK else it.box)
          }
        }
      }
    }

    SectionTitle("Outline and background")
    LabeledSlider("Outline", st.strokeWidth, 0f..0.4f, { v -> set("Outline", "stroke") { it.copy(strokeWidth = v) } }, if (st.strokeWidth < 0.01f) "Off" else "${(st.strokeWidth * 100).toInt()}")
    SwitchRow("Shadow", st.shadow, { v -> set("Shadow") { it.copy(shadow = v) } })
    PillRow(BoxStyle.entries, st.box, {
      when (it) {
        BoxStyle.NONE -> "No box"
        BoxStyle.LINES -> "Box per line"
        BoxStyle.BLOCK -> "One box"
      }
    }, { b -> set("Background box") { it.copy(box = b) } })
    if (st.box != BoxStyle.NONE) {
      LabeledSlider("Box corners", st.boxRadius, 0f..0.6f, { v -> set("Box corners", "radius") { it.copy(boxRadius = v) } }, "${(st.boxRadius * 100).toInt()}")
    }
    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
  }
}
