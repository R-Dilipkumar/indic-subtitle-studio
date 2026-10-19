package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.core.model.SafeZone
import io.github.rdilipkumar.subtitlestudio.ui.common.PillRow
import io.github.rdilipkumar.subtitlestudio.ui.common.PlaceControls
import io.github.rdilipkumar.subtitlestudio.ui.common.SectionTitle
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

@Composable
fun PositionPanel(ui: EditorUi, vm: EditorViewModel) {
  val t = vm.editingTransform()
  Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
    ScopeRow(ui, vm)
    Text("Tip: drag the caption on the video. Pinch to resize, twist to rotate.", color = Studio.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))

    PlaceControls(t) { _, key, place -> vm.applyTransform(place, key = key) }

    SectionTitle("Safe zone", hint = "Shows where Reels, Shorts or TikTok put their buttons, so captions stay readable")
    PillRow(SafeZone.entries, ui.meta?.safeZone ?: SafeZone.NONE, { it.label }, { vm.setSafeZone(it) })
    Spacer(Modifier.padding(8.dp))
  }
}
