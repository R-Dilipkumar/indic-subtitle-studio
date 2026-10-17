package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rdilipkumar.subtitlestudio.core.model.Anim
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.HighlightMode
import io.github.rdilipkumar.subtitlestudio.core.model.RevealMode
import io.github.rdilipkumar.subtitlestudio.ui.common.LabeledSlider
import io.github.rdilipkumar.subtitlestudio.ui.common.PillRow
import io.github.rdilipkumar.subtitlestudio.ui.common.SectionTitle
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel

@Composable
fun MotionPanel(ui: EditorUi, vm: EditorViewModel) {
  val st = vm.editingStyle()
  fun set(label: String, key: String? = null, f: (CaptionStyle) -> CaptionStyle) = vm.applyStyle(f(st), label, key)
  Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
    ScopeRow(ui, vm)

    SectionTitle("Word being spoken", hint = "How the current word stands out")
    PillRow(HighlightMode.entries, st.highlight, {
      when (it) {
        HighlightMode.NONE -> "Nothing"
        HighlightMode.COLOR -> "Colour"
        HighlightMode.SWEEP -> "Karaoke sweep"
        HighlightMode.POP -> "Pop"
        HighlightMode.BOX -> "Box"
      }
    }, { h -> set("Highlight") { it.copy(highlight = h) } })

    SectionTitle("Words appear")
    PillRow(RevealMode.entries, st.reveal, { if (it == RevealMode.ALL) "All at once" else "One by one" }, { r -> set("Words appear") { it.copy(reveal = r) } })

    SectionTitle("Caption comes in")
    PillRow(Anim.entries, st.entrance, ::animLabel, { a -> set("Comes in: ${animLabel(a)}") { it.copy(entrance = a) } })
    SectionTitle("Caption goes out")
    PillRow(Anim.entries, st.exit, ::animLabel, { a -> set("Goes out: ${animLabel(a)}") { it.copy(exit = a) } })
    LabeledSlider("Speed", st.animMs.toFloat(), 0f..600f, { v -> set("Animation speed", "anim") { it.copy(animMs = v.toInt()) } }, "${st.animMs} ms")
    Spacer(Modifier.height(8.dp))
  }
}

private fun animLabel(a: Anim) = when (a) {
  Anim.NONE -> "None"
  Anim.FADE -> "Fade"
  Anim.POP -> "Pop"
  Anim.SLIDE_UP -> "Slide"
  Anim.BOUNCE -> "Bounce"
}
