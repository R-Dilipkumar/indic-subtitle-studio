package io.github.rdilipkumar.subtitlestudio.ui.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectMeta
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectStatus
import io.github.rdilipkumar.subtitlestudio.data.PackState
import io.github.rdilipkumar.subtitlestudio.pipeline.JobState
import io.github.rdilipkumar.subtitlestudio.ui.common.Badge
import io.github.rdilipkumar.subtitlestudio.ui.common.formatTime
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
  vm: HomeViewModel,
  onOpen: (ProjectMeta) -> Unit,
  onCreated: (String) -> Unit,
  onOpenPacks: () -> Unit,
  onOpenRunning: (String) -> Unit,
) {
  val projects by vm.projects.collectAsState()
  val jobs by vm.jobs.collectAsState()
  val importing by vm.importing.collectAsState()
  val error by vm.error.collectAsState()
  val packs by vm.packStates.collectAsState()
  val readyPacks = packs.values.count { it == PackState.Ready }

  val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) vm.import(uri, onCreated)
  }
  fun pickVideo() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))

  Box(Modifier.fillMaxSize().background(Studio.bg)) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(160.dp),
      contentPadding = PaddingValues(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.statusBarsPadding(),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Offline Reel Captions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Studio.text)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WifiOff, null, tint = Studio.green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Everything runs on your phone", color = Studio.muted, fontSize = 13.sp)
              }
            }
            TextButton(onClick = onOpenPacks) {
              Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (readyPacks == 0) "Language packs" else "Packs ($readyPacks)")
            }
          }
          Spacer(Modifier.height(16.dp))
          NewProjectCard(importing = importing, onClick = ::pickVideo)
          if (projects.isNotEmpty()) {
            Text("Your projects", color = Studio.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp, bottom = 2.dp))
          }
        }
      }
      items(projects, key = { it.id }) { meta ->
        ProjectCard(
          meta = meta,
          job = jobs[meta.id],
          thumbFile = vm.thumb(meta.id),
          onClick = { if (jobs[meta.id] is JobState.Running) onOpenRunning(meta.id) else onOpen(meta) },
          onRename = { vm.rename(meta, it) },
          onDelete = { vm.delete(meta) },
        )
      }
    }
  }

  error?.let { msg ->
    AlertDialog(
      onDismissRequest = vm::clearError,
      confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
      title = { Text("Could not open the video") },
      text = { Text(msg) },
    )
  }
}

@Composable
private fun NewProjectCard(importing: Float?, onClick: () -> Unit) {
  Surface(
    onClick = { if (importing == null) onClick() },
    shape = RoundedCornerShape(20.dp),
    color = Color.Transparent,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      Modifier
        .background(Brush.linearGradient(listOf(Color(0xFF3A2E07), Color(0xFF1B2A22))))
        .padding(20.dp),
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Studio.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = Studio.onAccent)
          }
          Spacer(Modifier.width(14.dp))
          Column {
            Text(if (importing == null) "New project" else "Bringing your video in…", color = Studio.text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text("Pick a video. It is copied into a safe sandbox; your original stays untouched.", color = Studio.muted, fontSize = 13.sp)
          }
        }
        if (importing != null) {
          Spacer(Modifier.height(14.dp))
          LinearProgressIndicator(progress = { importing }, modifier = Modifier.fillMaxWidth(), color = Studio.accent)
        } else {
          Spacer(Modifier.height(12.dp))
          Text(
            "Auto subtitles · English letters (phonetic) · Translation\nTamil · Tanglish · Telugu · Kannada · Malayalam · Hindi · English",
            color = Studio.text.copy(alpha = 0.8f),
            fontSize = 12.sp,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
  meta: ProjectMeta,
  job: JobState?,
  thumbFile: java.io.File,
  onClick: () -> Unit,
  onRename: (String) -> Unit,
  onDelete: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  val thumb by produceState<ImageBitmap?>(null, thumbFile, meta.updatedAt) {
    value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(thumbFile.path)?.asImageBitmap() }
  }
  Column(
    Modifier
      .clip(RoundedCornerShape(16.dp))
      .background(Studio.surface)
      .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
  ) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Studio.surface2)) {
      thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        ?: Icon(Icons.Rounded.Movie, null, tint = Studio.muted, modifier = Modifier.align(Alignment.Center))
      Box(
        Modifier
          .align(Alignment.BottomEnd)
          .padding(6.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(Color.Black.copy(alpha = 0.6f))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      ) { Text(formatTime(meta.durationMs, tenths = false), color = Color.White, fontSize = 11.sp) }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
      }
    }
    Column(Modifier.padding(10.dp)) {
      Text(meta.name, color = Studio.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(4.dp))
      when {
        job is JobState.Running -> Badge("Working · ${(job.progress * 100).toInt()}%", Studio.accent)
        job is JobState.Failed -> Badge("Needs attention", Studio.danger)
        meta.status == ProjectStatus.READY -> Badge(meta.language?.label ?: "Ready", Studio.green)
        else -> Badge("Not started", Studio.muted)
      }
    }
  }
  if (renaming) {
    var text by remember { mutableStateOf(meta.name) }
    AlertDialog(
      onDismissRequest = { renaming = false },
      confirmButton = { TextButton(onClick = { renaming = false; onRename(text) }) { Text("Save") } },
      dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
      title = { Text("Rename project") },
      text = { OutlinedTextField(text, { text = it }, singleLine = true) },
    )
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Studio.danger) } },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
      title = { Text("Delete this project?") },
      text = { Text("This removes the sandbox copy and its subtitles from the app. Your original video and anything you exported stay where they are.") },
    )
  }
}
