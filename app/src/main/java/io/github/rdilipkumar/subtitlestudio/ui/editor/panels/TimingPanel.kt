package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.ui.common.PillRow
import io.github.rdilipkumar.subtitlestudio.ui.common.SectionTitle
import io.github.rdilipkumar.subtitlestudio.ui.common.formatTime
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

@Composable
fun TimingPanel(ui: EditorUi, vm: EditorViewModel, positionMs: Long) {
  val cue = ui.selected
  var regroupTo by remember { mutableStateOf<CaptionLength?>(null) }
  Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
    if (cue != null) {
      SectionTitle("This caption", hint = "Or drag its edges on the timeline")
      NudgeRow("Starts", formatTime(cue.startMs), onMinus = { vm.setTiming(cue.id, cue.startMs - 100, cue.endMs) }, onPlus = { vm.setTiming(cue.id, cue.startMs + 100, cue.endMs) }, onHere = { vm.setTiming(cue.id, positionMs, cue.endMs) })
      NudgeRow("Ends", formatTime(cue.endMs), onMinus = { vm.setTiming(cue.id, cue.startMs, cue.endMs - 100) }, onPlus = { vm.setTiming(cue.id, cue.startMs, cue.endMs + 100) }, onHere = { vm.setTiming(cue.id, cue.startMs, positionMs) })
      SectionTitle("Words", hint = "Fine-tune when each word lights up")
      for (w in cue.words) {
        NudgeRow(
          w.text,
          formatTime(w.startMs),
          onMinus = { vm.setWordTiming(cue.id, w.id, w.startMs - 50, w.endMs) },
          onPlus = { vm.setWordTiming(cue.id, w.id, w.startMs + 50, w.endMs) },
          onHere = { vm.setWordTiming(cue.id, w.id, positionMs, w.endMs) },
          small = true,
        )
      }
    } else {
      Text("Select a caption to change its timing, or fix all captions at once below.", color = Studio.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    }

    SectionTitle("All captions early or late?", hint = "Shifts every caption together")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for ((label, d) in listOf("−0.5 s" to -500L, "−0.1 s" to -100L, "+0.1 s" to 100L, "+0.5 s" to 500L)) {
        SmallAction(label, Icons.Rounded.Schedule) { vm.shiftAll(d) }
      }
    }

    SectionTitle("Caption size", hint = "Rebuilds all captions with the same words")
    PillRow(CaptionLength.entries, CaptionLength.PUNCHY, { it.label }, { regroupTo = it })
    Spacer(Modifier.padding(8.dp))
  }

  regroupTo?.let { len ->
    AlertDialog(
      onDismissRequest = { regroupTo = null },
      title = { Text("Rebuild as \"${len.label}\"?") },
      text = { Text("Every word and your word fixes are kept. Per-caption looks and translations are cleared (you can translate again). Undo brings everything back.") },
      confirmButton = { TextButton(onClick = { vm.regroup(len); regroupTo = null }) { Text("Rebuild") } },
      dismissButton = { TextButton(onClick = { regroupTo = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun NudgeRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, onHere: () -> Unit, small: Boolean = false) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
    Text(label, color = Studio.text, fontSize = if (small) 13.sp else 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
    Text(value, color = Studio.accent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Spacer(Modifier.width(8.dp))
    NudgeButton("−", onMinus)
    Spacer(Modifier.width(6.dp))
    NudgeButton("+", onPlus)
    Spacer(Modifier.width(6.dp))
    NudgeButton("At playhead", onHere)
  }
}

@Composable
private fun NudgeButton(text: String, onClick: () -> Unit) {
  Text(
    text,
    color = Studio.text,
    fontSize = 13.sp,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(Studio.surface3)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
  )
}
