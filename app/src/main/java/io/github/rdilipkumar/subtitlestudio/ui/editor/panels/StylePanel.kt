package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import io.github.rdilipkumar.subtitlestudio.ui.common.ColourControls
import io.github.rdilipkumar.subtitlestudio.ui.common.FontControls
import io.github.rdilipkumar.subtitlestudio.ui.common.OutlineControls
import io.github.rdilipkumar.subtitlestudio.ui.common.Pill
import io.github.rdilipkumar.subtitlestudio.ui.common.PillRow
import io.github.rdilipkumar.subtitlestudio.ui.common.SectionTitle
import io.github.rdilipkumar.subtitlestudio.ui.common.SizeControls
import io.github.rdilipkumar.subtitlestudio.ui.common.StyleChange
import io.github.rdilipkumar.subtitlestudio.ui.common.SwitchRow
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel
import io.github.rdilipkumar.subtitlestudio.ui.editor.Scope
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

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

@Composable
fun StylePanel(ui: EditorUi, vm: EditorViewModel) {
  val st = vm.editingStyle()
  val change: StyleChange = { label, key, style -> vm.applyStyle(style, label, key) }

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

    FontControls(st, ui.language.script, change, vm::toast)
    SizeControls(st, change)
    ColourControls(st, change)
    OutlineControls(st, change)
    Spacer(Modifier.padding(8.dp))
  }
}
