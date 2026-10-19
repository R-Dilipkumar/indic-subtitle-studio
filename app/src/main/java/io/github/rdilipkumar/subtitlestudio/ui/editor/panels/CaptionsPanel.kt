package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.core.model.TranslationState
import io.github.rdilipkumar.subtitlestudio.ui.common.Badge
import io.github.rdilipkumar.subtitlestudio.ui.common.SubtitleModeSwitch
import io.github.rdilipkumar.subtitlestudio.ui.common.formatTime
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel
import io.github.rdilipkumar.subtitlestudio.ui.editor.Tool
import io.github.rdilipkumar.subtitlestudio.ui.editor.cueMarkColor
import io.github.rdilipkumar.subtitlestudio.ui.editor.rememberEditedFilePicker
import io.github.rdilipkumar.subtitlestudio.ui.editor.rememberSrtImporter
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

/** Every caption as a list (like a script). The one playing now is highlighted. */
@Composable
fun CaptionsPanel(ui: EditorUi, vm: EditorViewModel, positionMs: Long, onSeek: (Long) -> Unit, onRedo: () -> Unit) {
  val cues = ui.doc.cues
  val listState = rememberLazyListState()
  val playingIndex = cues.indexOfFirst { positionMs in it.startMs until it.endMs }
  var adding by remember { mutableStateOf(false) }
  var replacing by remember { mutableStateOf(false) }
  var confirmRedo by remember { mutableStateOf(false) }
  val loadEdited = rememberEditedFilePicker(vm)
  val importSrt = rememberSrtImporter(vm)
  var confirmImport by remember { mutableStateOf(false) }
  LaunchedEffect(playingIndex) {
    if (playingIndex >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == playingIndex + 1 }) listState.animateScrollToItem(playingIndex)
  }

  LazyColumn(state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (ui.language.isIndic) {
      item {
        SubtitleModeSwitch(
          ui.doc.tracks.mode,
          vm::setSubtitleMode,
          Modifier.padding(bottom = 6.dp),
          soundsExample = cues.firstOrNull { it.phonetic.isNotBlank() }?.phonetic ?: "vanakkam nanbargale",
          meaningExample = cues.firstOrNull { it.translation.isNotBlank() }?.translation ?: "Hello friends",
        )
      }
    }
    item {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallAction("Add here", Icons.Rounded.Add) { adding = true }
        SmallAction("Find & replace", Icons.Rounded.FindReplace) { replacing = true }
        SmallAction("Import .srt", Icons.Rounded.FileUpload) { if (cues.isEmpty()) importSrt() else confirmImport = true }
        if (cues.isNotEmpty()) SmallAction("Load edited file", Icons.Rounded.UploadFile, loadEdited)
        SmallAction(if (cues.isEmpty()) "Auto subtitles" else "Redo with AI", Icons.Rounded.AutoAwesome) {
          if (cues.isEmpty()) onRedo() else confirmRedo = true
        }
        val stale = cues.count { it.translationState == TranslationState.STALE }
        if (stale > 0) SmallAction("Re-translate $stale", Icons.Rounded.AutoAwesome) { vm.translate(null) }
      }
    }
    if (cues.isEmpty()) {
      item {
        Text(
          "No captions yet. Move the playhead and tap \"Add here\", tap \"Import .srt\" to bring in a subtitle file, or let the app make them (\"Auto subtitles\").",
          color = Studio.muted,
          fontSize = 13.sp,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
    itemsIndexed(cues, key = { _, c -> c.id }) { i, c ->
      val selected = c.id == ui.selectedId
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(if (i == playingIndex) Studio.surface3 else Studio.surface2)
          .border(1.5.dp, if (selected) Studio.accent else Studio.surface2, RoundedCornerShape(12.dp))
          .clickable {
            vm.select(c.id)
            onSeek(c.startMs)
          }
          .padding(10.dp),
      ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(cueMarkColor(c)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}  ·  ${formatTime(c.startMs)} – ${formatTime(c.endMs)}", color = Studio.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (c.lowConfidence && !c.edited) Badge("Check", Studio.markCheck)
            if (c.translationState == TranslationState.STALE) {
              Spacer(Modifier.width(4.dp))
              Badge("Old translation", Studio.markStale)
            }
          }
          Text(c.text, color = Studio.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
          if (c.phonetic != c.text && ui.doc.tracks.showPhonetic) Text(c.phonetic, color = Studio.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (c.translation.isNotBlank()) Text(c.translation, color = Studio.markAdded, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { vm.select(c.id); vm.showTool(Tool.TEXT); onSeek(c.startMs) }) { Text("Edit") }
      }
    }
  }

  if (confirmImport) {
    AlertDialog(
      onDismissRequest = { confirmImport = false },
      title = { Text("Import an .srt file?") },
      text = { Text("Its captions replace the ones here. The project's look stays. Undo brings your captions back.") },
      confirmButton = { TextButton(onClick = { confirmImport = false; importSrt() }) { Text("Choose file") } },
      dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
    )
  }
  if (confirmRedo) {
    AlertDialog(
      onDismissRequest = { confirmRedo = false },
      title = { Text("Make the captions again?") },
      text = { Text("You can pick another language, size or look. The new captions replace these ones, including your edits.") },
      confirmButton = { TextButton(onClick = { confirmRedo = false; onRedo() }) { Text("Continue") } },
      dismissButton = { TextButton(onClick = { confirmRedo = false }) { Text("Keep mine") } },
    )
  }
  if (adding) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { adding = false },
      title = { Text("New caption at ${formatTime(positionMs)}") },
      text = { OutlinedTextField(text, { text = it }, placeholder = { Text("Type what is said") }) },
      confirmButton = { TextButton(onClick = { adding = false; vm.insertAt(positionMs, text) }, enabled = text.isNotBlank()) { Text("Add") } },
      dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
    )
  }
  if (replacing) {
    var find by remember { mutableStateOf("") }
    var with by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { replacing = false },
      title = { Text("Find and replace") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(find, { find = it }, label = { Text("Find") }, singleLine = true)
          OutlinedTextField(with, { with = it }, label = { Text("Replace with") }, singleLine = true)
          Text("Fixes the same mistake everywhere, e.g. a name the AI heard wrong.", color = Studio.muted, fontSize = 12.sp)
        }
      },
      confirmButton = { TextButton(onClick = { replacing = false; vm.replaceAll(find, with) }, enabled = find.isNotBlank()) { Text("Replace all") } },
      dismissButton = { TextButton(onClick = { replacing = false }) { Text("Cancel") } },
    )
  }
}

@Composable
internal fun SmallAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
  Row(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(Studio.surface3)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = Studio.accent, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(text, fontSize = 12.sp, color = Studio.text)
  }
}
