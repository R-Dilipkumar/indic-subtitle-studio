package io.github.rdilipkumar.subtitlestudio.ui.packs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.data.ModelCatalog
import io.github.rdilipkumar.subtitlestudio.data.ModelPack
import io.github.rdilipkumar.subtitlestudio.data.PackKind
import io.github.rdilipkumar.subtitlestudio.data.PackState
import io.github.rdilipkumar.subtitlestudio.ui.common.Badge
import io.github.rdilipkumar.subtitlestudio.ui.common.formatBytes
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import kotlinx.coroutines.launch

@Composable
fun PacksScreen(onBack: () -> Unit) {
  val models = App.graph.models
  val states by models.states.collectAsState()
  val scope = rememberCoroutineScope()
  var importFor by remember { mutableStateOf<ModelPack?>(null) }
  var info by remember { mutableStateOf<String?>(null) }
  var confirmDelete by remember { mutableStateOf<ModelPack?>(null) }
  val used = remember(states) { models.bytesOnDisk() }

  val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
    val pack = importFor ?: return@rememberLauncherForActivityResult
    importFor = null
    if (uris.isEmpty()) return@rememberLauncherForActivityResult
    scope.launch {
      val found = models.importFiles(pack, uris)
      info = if (models.isReady(pack)) "${pack.title} is ready." else
        "Found $found of ${pack.files.size} files. Needed: ${pack.files.joinToString { it.name }}"
    }
  }

  Column(Modifier.fillMaxSize().background(Studio.bg).statusBarsPadding().navigationBarsPadding()) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
      Column(Modifier.weight(1f)) {
        Text("Language packs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Download once on Wi-Fi. After that everything works with no internet. Using ${formatBytes(used)}.", color = Studio.muted, fontSize = 12.sp)
      }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item { Text("Speech to text", color = Studio.muted, fontSize = 13.sp) }
      items(ModelCatalog.all.filter { it.kind == PackKind.SPEECH }, key = { it.id }) { p ->
        PackCard(p, states[p.id] ?: PackState.Missing, onDownload = { models.download(p) }, onCancel = { models.cancel(p) }, onDelete = { confirmDelete = p }, onImport = { importFor = p; pickFiles.launch(arrayOf("*/*")) })
      }
      item { Text("Translation", color = Studio.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
      items(ModelCatalog.all.filter { it.kind == PackKind.TRANSLATE }, key = { it.id }) { p ->
        PackCard(p, states[p.id] ?: PackState.Missing, onDownload = { models.download(p) }, onCancel = { models.cancel(p) }, onDelete = { confirmDelete = p }, onImport = { importFor = p; pickFiles.launch(arrayOf("*/*")) })
      }
      item {
        Text(
          "No internet at all? Copy a pack's files (or a .zip of them) onto the phone, then use \"Import files\". " +
            "English letters (phonetic) need no pack: they are built into the app.",
          color = Studio.muted,
          fontSize = 12.sp,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }

  info?.let {
    AlertDialog(onDismissRequest = { info = null }, confirmButton = { TextButton(onClick = { info = null }) { Text("OK") } }, text = { Text(it) })
  }
  confirmDelete?.let { p ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      confirmButton = { TextButton(onClick = { models.delete(p); confirmDelete = null }) { Text("Remove", color = Studio.danger) } },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
      title = { Text("Remove ${p.title}?") },
      text = { Text("Frees ${formatBytes(p.totalBytes)}. Your projects stay; you would need to download it again to make new subtitles in this language.") },
    )
  }
}

@Composable
private fun PackCard(
  p: ModelPack,
  state: PackState,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onImport: () -> Unit,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Studio.surface)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(if (p.kind == PackKind.SPEECH) Icons.Rounded.RecordVoiceOver else Icons.Rounded.Translate, null, tint = Studio.accent)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(p.title, color = Studio.text, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.width(8.dp))
          Badge(p.quality, if (p.quality == "Good") Studio.green else Studio.markCheck)
        }
        Text(p.detail, color = Studio.muted, fontSize = 12.sp)
        Text(formatBytes(p.totalBytes), color = Studio.muted, fontSize = 12.sp)
        p.licenseNote?.let { Text(it, color = Studio.markCheck, fontSize = 12.sp) }
      }
      if (state == PackState.Ready) Icon(Icons.Rounded.CheckCircle, "Ready", tint = Studio.green, modifier = Modifier.size(26.dp))
    }
    when (state) {
      is PackState.Downloading -> {
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth(), color = Studio.accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${formatBytes(state.doneBytes)} of ${formatBytes(state.totalBytes)}", color = Studio.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
          TextButton(onClick = onCancel) { Text("Pause") }
        }
      }
      PackState.Ready -> Row {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDelete) { Text("Remove", color = Studio.muted) }
      }
      else -> {
        if (state is PackState.Failed) Text(state.message, color = Studio.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Row {
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onImport) { Text("Import files") }
          TextButton(onClick = onDownload) { Text(if (state is PackState.Failed) "Try again" else "Download") }
        }
      }
    }
  }
}
