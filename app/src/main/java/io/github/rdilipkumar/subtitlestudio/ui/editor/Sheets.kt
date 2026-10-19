package io.github.rdilipkumar.subtitlestudio.ui.editor

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleMode
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.ui.common.Pill
import io.github.rdilipkumar.subtitlestudio.export.VideoExporter
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

/** Every change so far, newest first. Tap one to go back to that moment (later ones stay for redo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(ui: EditorUi, vm: EditorViewModel, onClose: () -> Unit) {
  ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Studio.surface) {
    Column(Modifier.padding(horizontal = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          Text("Tap any step to go back to it. Nothing is lost: later steps stay until you make a new change.", color = Studio.muted, fontSize = 12.sp)
        }
      }
      if (ui.aiDoc != null) {
        TextButton(onClick = { vm.resetToAi(); onClose() }) {
          Icon(Icons.Rounded.RestartAlt, null)
          Spacer(Modifier.width(6.dp))
          Text("Back to what the AI made")
        }
      }
      val entries = ui.history
      LazyColumn(Modifier.heightIn(max = 460.dp)) {
        itemsIndexed(entries.asReversed()) { revIndex, e ->
          val index = entries.lastIndex - revIndex
          val current = index == ui.historyIndex
          val future = index > ui.historyIndex
          Row(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable { vm.jumpTo(index) }
              .padding(vertical = 10.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (current) Studio.accent else if (future) Studio.surface3 else Studio.markEdited)
                .border(1.dp, Studio.outline, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(e.label, color = if (future) Studio.muted else Studio.text, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
              Text(
                "${DateUtils.getRelativeTimeSpanString(e.timeMillis, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS)} · ${e.doc.cues.size} captions" + if (future) " · undone" else "",
                color = Studio.muted,
                fontSize = 11.sp,
              )
            }
            if (current) Text("Now", color = Studio.accent, fontSize = 12.sp)
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

/** Opens the phone's file picker for an edited .srt / .vtt and loads it into the project. */
@Composable
fun rememberEditedFilePicker(vm: EditorViewModel): () -> Unit {
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.loadEdited(uri) }
  return { launcher.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "text/*", "application/octet-stream", "*/*")) }
}

/** Opens the phone's file picker for an .srt / .vtt whose captions replace the project's. */
@Composable
fun rememberSrtImporter(vm: EditorViewModel): () -> Unit {
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSrt(uri) }
  return { launcher.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "text/*", "application/octet-stream", "*/*")) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(ui: EditorUi, vm: EditorViewModel, onClose: () -> Unit, onOpen: (Uri, String) -> Unit, onShare: (Uri, String) -> Unit) {
  ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Studio.surface) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Export", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      Text("Makes new files. The sandbox copy and your original video are not changed.", color = Studio.muted, fontSize = 12.sp)

      when (val ex = ui.export) {
        is ExportState.Running -> {
          Text(ex.label, color = Studio.text)
          LinearProgressIndicator(progress = { ex.progress }, modifier = Modifier.fillMaxWidth(), color = Studio.accent)
          Text("${(ex.progress * 100).toInt()}% · uses the phone's video hardware, all offline", color = Studio.muted, fontSize = 12.sp)
          TextButton(onClick = vm::cancelExport) { Text("Stop", color = Studio.danger) }
        }
        is ExportState.Done -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Studio.green)
            Spacer(Modifier.width(8.dp))
            Text(ex.label, color = Studio.text)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onShare(ex.uri, ex.mime) }, colors = ButtonDefaults.buttonColors(containerColor = Studio.accent, contentColor = Studio.onAccent)) { Text("Share") }
            OutlinedButton(onClick = { onOpen(ex.uri, ex.mime) }) { Text("Open") }
            TextButton(onClick = vm::clearExport) { Text("Export more") }
          }
        }
        is ExportState.Failed -> {
          Text(ex.message, color = Studio.danger)
          TextButton(onClick = vm::clearExport) { Text("Back") }
        }
        ExportState.Idle -> {
          ExportCard(
            icon = Icons.Rounded.Movie,
            title = "Video with captions",
            detail = "MP4 with the captions burned in, exactly as in the preview. Ready for Reels, Shorts and WhatsApp.",
          ) {
            Button(onClick = vm::exportVideo, colors = ButtonDefaults.buttonColors(containerColor = Studio.accent, contentColor = Studio.onAccent)) { Text("Make video") }
          }
          var native by remember { mutableStateOf(ui.doc.tracks.showNative) }
          var phonetic by remember { mutableStateOf(ui.doc.tracks.showPhonetic) }
          var translation by remember { mutableStateOf(ui.doc.tracks.showTranslation) }
          ExportCard(
            icon = Icons.Rounded.Subtitles,
            title = "Subtitle file",
            detail = "For YouTube, editors (Premiere, CapCut, DaVinci) and players. Pick the lines to include:",
          ) {
            Column {
              CheckLine("Original words", native) { native = it }
              if (ui.language.isIndic) CheckLine("English letters", phonetic) { phonetic = it }
              CheckLine("Translation", translation) { translation = it }
              val tracks = ui.doc.tracks.order.filter {
                when (it) {
                  TrackKind.NATIVE -> native
                  TrackKind.PHONETIC -> phonetic && ui.language.isIndic
                  TrackKind.TRANSLATION -> translation
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in VideoExporter.FileType.entries) {
                  OutlinedButton(onClick = { vm.exportFile(type, tracks) }, enabled = tracks.isNotEmpty()) { Text(".${type.ext}") }
                }
              }
              Text("ASS keeps colours, position and karaoke timing.", color = Studio.muted, fontSize = 11.sp)
            }
          }
          var editLine by remember { mutableStateOf(if (ui.doc.tracks.mode == SubtitleMode.PHONETIC && ui.language.isIndic) TrackKind.PHONETIC else TrackKind.NATIVE) }
          val loadEdited = rememberEditedFilePicker(vm)
          ExportCard(
            icon = Icons.Rounded.EditNote,
            title = "Edit the words outside the app",
            detail = "Get the captions as a text file (.srt), fix the words in any app (notes, a computer, an AI chat), then load the file back. Your look, effects and positions stay.",
          ) {
            Column {
              if (ui.language.isIndic) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Pill("Original words", editLine == TrackKind.NATIVE, { editLine = TrackKind.NATIVE })
                  Pill("English letters", editLine == TrackKind.PHONETIC, { editLine = TrackKind.PHONETIC })
                }
                Spacer(Modifier.height(8.dp))
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.exportForEditing(editLine) }) { Text("1. Get .srt to edit") }
                OutlinedButton(onClick = loadEdited) { Text("2. Load edited file") }
              }
              Text("Change only the words; keep the numbers and times as they are.", color = Studio.muted, fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ExportCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, actions: @Composable () -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Studio.surface2)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, null, tint = Studio.accent)
      Spacer(Modifier.width(10.dp))
      Text(title, fontWeight = FontWeight.SemiBold)
    }
    Text(detail, color = Studio.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    actions()
  }
}

@Composable
private fun CheckLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
    Checkbox(checked, onChange)
    Text(label, fontSize = 14.sp)
  }
}
