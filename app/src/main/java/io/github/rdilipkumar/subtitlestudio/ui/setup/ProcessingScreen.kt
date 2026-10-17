package io.github.rdilipkumar.subtitlestudio.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.pipeline.JobState
import io.github.rdilipkumar.subtitlestudio.pipeline.Step
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

/** Shows the automatic job step by step, with the words appearing as they are heard. */
@Composable
fun ProcessingScreen(projectId: String, onDone: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit) {
  val g = App.graph
  val states by g.subtitler.states.collectAsState()
  val state = states[projectId]

  // Keep the screen awake while the phone works.
  val view = LocalView.current
  DisposableEffect(Unit) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
  LaunchedEffect(state) {
    if (state == JobState.Done) {
      g.subtitler.clear(projectId)
      onDone()
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(Studio.bg)
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(20.dp),
  ) {
    Text("Making your subtitles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("All on this phone — no internet needed. You can leave this screen; it keeps going.", color = Studio.muted, fontSize = 13.sp)
    Spacer(Modifier.height(20.dp))

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
      when (state) {
        is JobState.Running -> {
          val current = state.steps.indexOf(state.step)
          for ((i, step) in state.steps.withIndex()) {
            StepRow(step, done = i < current, active = i == current, progress = if (i == current) state.progress else 0f, detail = if (i == current) state.detail else "")
          }
          state.etaSeconds?.takeIf { it > 0 }?.let {
            Text("About ${if (it > 90) "${it / 60} min" else "$it s"} left", color = Studio.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
          }
          if (state.heard.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Studio.surface)
                .padding(14.dp),
            ) {
              Text("Heard so far", color = Studio.muted, fontSize = 12.sp)
              for (line in state.heard) Text(line, color = Studio.text, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }
          }
        }
        is JobState.Failed -> {
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Studio.danger)
            Spacer(Modifier.width(10.dp))
            Text(state.message, color = Studio.text)
          }
        }
        else -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      if (state is JobState.Failed) {
        OutlinedButton(onClick = { g.subtitler.clear(projectId); onBack() }, modifier = Modifier.weight(1f)) { Text("Home") }
        Button(
          onClick = { g.subtitler.clear(projectId); onRetry() },
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(containerColor = Studio.accent, contentColor = Studio.onAccent),
        ) { Text("Change options") }
      } else {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Run in background") }
        OutlinedButton(onClick = { g.subtitler.cancel(projectId); onRetry() }, modifier = Modifier.weight(1f)) { Text("Stop", color = Studio.danger) }
      }
    }
  }
}

@Composable
private fun StepRow(step: Step, done: Boolean, active: Boolean, progress: Float, detail: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    when {
      done -> Icon(Icons.Rounded.CheckCircle, null, tint = Studio.green, modifier = Modifier.size(24.dp))
      active -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Studio.accent)
      else -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = Studio.outline, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(step.label, color = if (done || active) Studio.text else Studio.muted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
      if (active) {
        if (detail.isNotEmpty()) Text(detail, color = Studio.muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Studio.accent)
      }
    }
  }
}
