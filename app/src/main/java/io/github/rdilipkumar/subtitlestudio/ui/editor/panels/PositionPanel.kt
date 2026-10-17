package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.core.model.SafeZone
import io.github.rdilipkumar.subtitlestudio.core.model.Transform
import io.github.rdilipkumar.subtitlestudio.ui.common.LabeledSlider
import io.github.rdilipkumar.subtitlestudio.ui.common.Pill
import io.github.rdilipkumar.subtitlestudio.ui.common.PillRow
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

    SectionTitle("Quick spots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for ((label, y) in listOf("Top" to 0.18f, "Middle" to 0.5f, "Lower third" to 0.68f, "Bottom" to 0.86f)) {
        Pill(label, kotlin.math.abs(t.y - y) < 0.02f && t.x == 0.5f, { vm.applyTransform(t.copy(x = 0.5f, y = y), key = null) })
      }
    }

    SectionTitle("Safe zone", hint = "Shows where Reels, Shorts or TikTok put their buttons, so captions stay readable")
    PillRow(SafeZone.entries, ui.meta?.safeZone ?: SafeZone.NONE, { it.label }, { vm.setSafeZone(it) })

    SectionTitle("Size and angle")
    LabeledSlider("Size", t.scale, 0.3f..3f, { vm.applyTransform(t.copy(scale = it), key = "scale") }, "${(t.scale * 100).toInt()}%")
    LabeledSlider("Angle", t.rotation, -45f..45f, { vm.applyTransform(t.copy(rotation = it), key = "rotate") }, "${t.rotation.toInt()}°")
    TextButton(onClick = { vm.applyTransform(Transform(y = t.y), key = null) }) { Text("Reset size, angle and centre") }
    Spacer(Modifier.padding(8.dp))
  }
}
