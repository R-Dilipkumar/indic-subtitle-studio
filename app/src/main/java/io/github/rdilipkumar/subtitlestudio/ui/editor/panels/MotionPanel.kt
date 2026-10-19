package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rdilipkumar.subtitlestudio.ui.common.MotionControls
import io.github.rdilipkumar.subtitlestudio.ui.common.Pill
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel

/** How captions and words appear, stand out and disappear. */
@Composable
fun MotionPanel(ui: EditorUi, vm: EditorViewModel, positionMs: Long, onPlayFrom: (Long) -> Unit) {
  val st = vm.editingStyle()
  Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
    ScopeRow(ui, vm)
    // The caption to try effects on: the selected one, else the one at (or next after) the playhead.
    val tryCue = ui.selected ?: ui.doc.cueAt(positionMs) ?: ui.doc.cues.firstOrNull { it.startMs > positionMs }
    if (tryCue != null) {
      Pill("Play this caption", false, { onPlayFrom((tryCue.startMs - 400).coerceAtLeast(0)) }, Modifier.padding(top = 4.dp), icon = Icons.Rounded.PlayArrow)
    }
    MotionControls(st) { label, key, style -> vm.applyStyle(style, label, key) }
    Spacer(Modifier.height(8.dp))
  }
}
