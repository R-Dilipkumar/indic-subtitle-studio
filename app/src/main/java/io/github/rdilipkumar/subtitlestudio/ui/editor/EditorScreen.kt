package io.github.rdilipkumar.subtitlestudio.ui.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import io.github.rdilipkumar.subtitlestudio.ui.common.LegendDot
import io.github.rdilipkumar.subtitlestudio.ui.common.formatTime
import io.github.rdilipkumar.subtitlestudio.ui.editor.panels.CaptionsPanel
import io.github.rdilipkumar.subtitlestudio.ui.editor.panels.MotionPanel
import io.github.rdilipkumar.subtitlestudio.ui.editor.panels.PositionPanel
import io.github.rdilipkumar.subtitlestudio.ui.editor.panels.StylePanel
import io.github.rdilipkumar.subtitlestudio.ui.editor.panels.TextPanel
import io.github.rdilipkumar.subtitlestudio.ui.editor.panels.TimingPanel
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import kotlinx.coroutines.delay

/**
 * The sandbox editor. Top to bottom: the video with live captions, play controls, the
 * timeline, the tool tabs and the open tool. Every change is undoable and listed in History.
 */
@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit, onRedo: () -> Unit) {
  val vm: EditorViewModel = viewModel()
  LaunchedEffect(projectId) { vm.bind(projectId) }
  val ui by vm.ui.collectAsState()
  val player = vm.player
  val context = LocalContext.current

  var positionMs by remember { mutableLongStateOf(0L) }
  var playing by remember { mutableStateOf(false) }
  var scrubbing by remember { mutableStateOf(false) }
  var showHistory by remember { mutableStateOf(false) }
  var showExport by remember { mutableStateOf(false) }

  // Follow the player smoothly, once per screen frame, while it plays. When paused nothing
  // moves on its own, so there is no frame loop; seeks are caught by the listener below.
  LaunchedEffect(player, playing) {
    if (!scrubbing) positionMs = player.currentPosition
    while (playing) {
      withFrameNanos { }
      if (!scrubbing) positionMs = player.currentPosition
    }
  }
  DisposableEffect(player) {
    val l = object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        playing = isPlaying
      }

      override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (!scrubbing) positionMs = newPosition.positionMs
      }
    }
    player.addListener(l)
    onDispose {
      player.removeListener(l)
      player.pause()
      vm.flush()
    }
  }
  BackHandler(enabled = ui.selectedId != null || showHistory || showExport) {
    when {
      showExport -> showExport = false
      showHistory -> showHistory = false
      else -> vm.select(null)
    }
  }
  LaunchedEffect(ui.toast) {
    if (ui.toast != null) {
      delay(2600)
      vm.toast(null)
    }
  }

  val meta = ui.meta
  val doc = if (ui.comparing) ui.aiDoc ?: ui.doc else ui.doc
  val duration = meta?.durationMs ?: 0L

  Box(Modifier.fillMaxSize().background(Studio.bg)) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
      // Top bar
      Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.flush(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
          Text(meta?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
          Text("Sandbox · original untouched · ${ui.doc.cues.size} captions", color = Studio.muted, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = vm::undo, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
        IconButton(onClick = vm::redo, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
        IconButton(onClick = { showHistory = true }) { Icon(Icons.Rounded.History, "History") }
        IconButton(onClick = { player.pause(); showExport = true }) { Icon(Icons.Rounded.IosShare, "Export", tint = Studio.accent) }
      }

      // Video
      Box(Modifier.weight(1f).fillMaxWidth()) {
        if (meta != null) {
          VideoPreview(
            player = player,
            videoW = meta.width,
            videoH = meta.height,
            doc = doc,
            positionMs = positionMs,
            playing = playing,
            selectedId = if (ui.comparing) null else ui.selectedId,
            safeZone = meta.safeZone,
            editingTransform = vm.editingTransform(),
            onSelect = { id ->
              vm.select(id)
              if (id != null && ui.tool == null) vm.showTool(Tool.TEXT)
            },
            onDoubleTap = { id -> vm.select(id); vm.showTool(Tool.TEXT) },
            onTransform = { t, done ->
              if (!ui.comparing) {
                vm.applyTransform(t, key = "place")
                if (done) vm.endGesture()
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
        // Hold to compare with what the AI made.
        if (ui.aiDoc != null) {
          Box(
            Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .clip(RoundedCornerShape(50))
              .background(if (ui.comparing) Studio.accent else Studio.surface.copy(alpha = 0.85f))
              .pointerInput(Unit) {
                awaitPointerEventScope {
                  while (true) {
                    val e = awaitPointerEvent()
                    vm.setComparing(e.changes.any { it.pressed })
                  }
                }
              }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Compare, null, tint = if (ui.comparing) Studio.onAccent else Studio.text, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (ui.comparing) "AI original" else "Hold: compare", fontSize = 11.sp, color = if (ui.comparing) Studio.onAccent else Studio.text)
            }
          }
        }
        if (ui.busy != null) {
          Row(
            Modifier
              .align(Alignment.TopStart)
              .padding(8.dp)
              .clip(RoundedCornerShape(50))
              .background(Studio.surface.copy(alpha = 0.9f))
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Studio.accent)
            Spacer(Modifier.width(6.dp))
            Text(ui.busy!!, fontSize = 11.sp)
          }
        }
      }

      // Play controls
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${formatTime(positionMs)} / ${formatTime(duration, tenths = false)}", color = Studio.muted, fontSize = 12.sp, modifier = Modifier.width(110.dp))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {
          val prev = ui.doc.cues.lastOrNull { it.startMs < positionMs - 300 }
          player.seekTo(prev?.startMs ?: 0L)
          prev?.let { vm.select(it.id) }
        }) { Icon(Icons.Rounded.SkipPrevious, "Previous caption") }
        Box(
          Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(50))
            .background(Studio.accent)
            .clickable { if (playing) player.pause() else { if (positionMs >= duration - 50) player.seekTo(0); player.play() } },
          contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", tint = Studio.onAccent) }
        IconButton(onClick = {
          val next = ui.doc.cues.firstOrNull { it.startMs > positionMs + 50 }
          if (next != null) {
            player.seekTo(next.startMs)
            vm.select(next.id)
          }
        }) { Icon(Icons.Rounded.SkipNext, "Next caption") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.splitAt(positionMs) }) { Icon(Icons.Rounded.ContentCut, "Split at playhead") }
      }

      // Timeline + legend
      Timeline(
        cues = doc.cues,
        durationMs = duration,
        positionMs = positionMs,
        selectedId = ui.selectedId,
        waveform = ui.waveform,
        onSeek = { t, done ->
          scrubbing = !done
          positionMs = t
          player.seekTo(t)
          if (playing) player.pause()
        },
        onSelect = { id ->
          vm.select(id)
          if (id != null && ui.tool == null) vm.showTool(Tool.TEXT)
        },
        onRetime = { id, s, e -> vm.setTiming(id, s, e, key = "edge-$id") },
        onMoveTo = { id, start -> vm.moveTo(id, start, key = "drag-$id") },
        onDragEnd = vm::endGesture,
      )
      Row(
        Modifier.fillMaxWidth().background(Studio.surface).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        LegendDot(Studio.markAi, "AI made")
        LegendDot(Studio.markEdited, "You edited")
        LegendDot(Studio.markAdded, "You added")
        LegendDot(Studio.markCheck, "Please check")
        LegendDot(Studio.markStale, "Translation old")
      }

      // Tool tabs
      Row(Modifier.fillMaxWidth().background(Studio.bg).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToolTab(Tool.CAPTIONS, Icons.Rounded.FormatListBulleted, ui.tool, vm::openTool)
        ToolTab(Tool.TEXT, Icons.Rounded.TextFields, ui.tool, vm::openTool)
        ToolTab(Tool.STYLE, Icons.Rounded.Palette, ui.tool, vm::openTool)
        ToolTab(Tool.MOTION, Icons.Rounded.Animation, ui.tool, vm::openTool)
        ToolTab(Tool.TIMING, Icons.Rounded.Timer, ui.tool, vm::openTool)
        ToolTab(Tool.POSITION, Icons.Rounded.OpenWith, ui.tool, vm::openTool)
      }

      // Open tool
      if (ui.tool != null && ui.loaded) {
        Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).background(Studio.surface)) {
          when (ui.tool) {
            Tool.CAPTIONS -> CaptionsPanel(ui, vm, positionMs, onSeek = { player.seekTo(it) }, onRedo = onRedo)
            Tool.TEXT -> TextPanel(ui, vm, positionMs, onSeek = { player.seekTo(it) })
            Tool.STYLE -> StylePanel(ui, vm)
            Tool.MOTION -> MotionPanel(ui, vm, positionMs, onPlayFrom = { player.seekTo(it); player.play() })
            Tool.TIMING -> TimingPanel(ui, vm, positionMs)
            Tool.POSITION -> PositionPanel(ui, vm)
            null -> Unit
          }
        }
      }
    }

    ui.toast?.let {
      Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding()) { Text(it) }
    }
  }

  if (showHistory) HistorySheet(ui, vm, onClose = { showHistory = false })
  if (showExport) {
    ExportSheet(ui, vm, onClose = { showExport = false; vm.clearExport() }, onOpen = { uri, mime ->
      val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      runCatching { context.startActivity(i) }
    }, onShare = { uri, mime ->
      val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      runCatching { context.startActivity(Intent.createChooser(i, "Share")) }
    })
  }
}

@Composable
private fun ToolTab(tool: Tool, icon: ImageVector, current: Tool?, onClick: (Tool) -> Unit) {
  val on = current == tool
  Column(
    Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable { onClick(tool) }
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, tool.label, tint = if (on) Studio.accent else Studio.muted, modifier = Modifier.size(22.dp))
    Text(tool.label, fontSize = 10.sp, color = if (on) Studio.accent else Studio.muted, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(2.dp))
  }
}
