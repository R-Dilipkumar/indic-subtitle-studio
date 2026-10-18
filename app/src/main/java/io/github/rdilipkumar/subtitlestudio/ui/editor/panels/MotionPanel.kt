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
import io.github.rdilipkumar.subtitlestudio.core.model.Anim
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.HighlightMode
import io.github.rdilipkumar.subtitlestudio.core.model.RevealMode
import io.github.rdilipkumar.subtitlestudio.ui.common.LabeledSlider
import io.github.rdilipkumar.subtitlestudio.ui.common.Pill
import io.github.rdilipkumar.subtitlestudio.ui.common.PillFlow
import io.github.rdilipkumar.subtitlestudio.ui.common.SectionTitle
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel

/** How captions and words appear, stand out and disappear. */
@Composable
fun MotionPanel(ui: EditorUi, vm: EditorViewModel, positionMs: Long, onPlayFrom: (Long) -> Unit) {
  val st = vm.editingStyle()
  fun set(label: String, key: String? = null, f: (CaptionStyle) -> CaptionStyle) = vm.applyStyle(f(st), label, key)
  Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
    ScopeRow(ui, vm)
    // The caption to try effects on: the selected one, else the one at (or next after) the playhead.
    val tryCue = ui.selected ?: ui.doc.cueAt(positionMs) ?: ui.doc.cues.firstOrNull { it.startMs > positionMs }
    if (tryCue != null) {
      Pill("Play this caption", false, { onPlayFrom((tryCue.startMs - 400).coerceAtLeast(0)) }, Modifier.padding(top = 4.dp), icon = Icons.Rounded.PlayArrow)
    }

    SectionTitle("Words appear")
    PillFlow(RevealMode.entries, st.reveal, {
      when (it) {
        RevealMode.ALL -> "All at once"
        RevealMode.WORD_BY_WORD -> "One by one"
        RevealMode.LETTER_BY_LETTER -> "Typing (letter by letter)"
      }
    }, { r -> set("Words appear") { it.copy(reveal = r) } })
    if (st.reveal == RevealMode.WORD_BY_WORD) {
      SectionTitle("Each word comes in")
      PillFlow(Anim.entries, st.wordAnim, ::animLabel, { a -> set("Each word: ${animLabel(a)}") { it.copy(wordAnim = a) } })
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
    }, { h -> set("Highlight") { it.copy(highlight = h) } })

    SectionTitle("Caption comes in")
    PillFlow(Anim.entries, st.entrance, ::animLabel, { a -> set("Comes in: ${animLabel(a)}") { it.copy(entrance = a) } })
    LabeledSlider("Coming in takes", st.animMs.toFloat(), 0f..1000f, { v -> set("Coming in speed", "anim-in") { it.copy(animMs = v.toInt()) } }, "${st.animMs} ms")

    SectionTitle("Caption goes out")
    PillFlow(Anim.entries, st.exit, ::animLabel, { a -> set("Goes out: ${animLabel(a)}") { it.copy(exit = a) } })
    LabeledSlider("Going out takes", st.exitMs.toFloat(), 0f..1000f, { v -> set("Going out speed", "anim-out") { it.copy(exitMs = v.toInt()) } }, "${st.exitMs} ms")
    Spacer(Modifier.height(8.dp))
  }
}

private fun animLabel(a: Anim) = when (a) {
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
