package io.github.rdilipkumar.subtitlestudio.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectFile
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectMeta
import io.github.rdilipkumar.subtitlestudio.media.VideoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {
  private val g = App.graph

  val projects = g.projects.list
  val jobs = g.subtitler.states
  val packStates = g.models.states

  /** Copy progress 0..1 while a video is being brought into the sandbox, else null. */
  private val _importing = MutableStateFlow<Float?>(null)
  val importing: StateFlow<Float?> = _importing
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error

  fun clearError() {
    _error.value = null
  }

  /** Copies the video into a new project folder (the sandbox) and reads its facts. */
  fun import(uri: Uri, onCreated: (String) -> Unit) {
    if (_importing.value != null) return
    _importing.value = 0f
    viewModelScope.launch {
      val id = g.projects.newId()
      try {
        val ctx = getApplication<Application>()
        // The photo picker hides the real file name (it gives "21.mp4"): ignore number names.
        val name = VideoImporter.displayName(ctx, uri)?.takeIf { n -> n.substringBeforeLast('.').any { !it.isDigit() } }
        val ext = VideoImporter.displayName(ctx, uri)?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..4 } ?: "mp4"
        val dir = g.projects.dir(id).apply { mkdirs() }
        val video = java.io.File(dir, "source.$ext")
        VideoImporter.copy(ctx, uri, video) { _importing.value = it }
        val info = withContext(Dispatchers.IO) { VideoImporter.info(video) }
        if (info.durationMs <= 0 || info.width <= 0) {
          g.projects.delete(id)
          _error.value = "This file does not look like a video the phone can play."
          return@launch
        }
        withContext(Dispatchers.IO) { VideoImporter.saveThumbnail(video, g.projects.thumbFile(id), info.durationMs) }
        val now = System.currentTimeMillis()
        val title = name?.substringBeforeLast('.')?.take(40)?.ifBlank { null } ?: "Video ${java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(now)}"
        g.projects.save(
          ProjectFile(
            ProjectMeta(
              id = id,
              name = title,
              createdAt = now,
              updatedAt = now,
              videoFile = video.name,
              durationMs = info.durationMs,
              width = info.width,
              height = info.height,
            ),
          ),
        )
        onCreated(id)
      } catch (e: Exception) {
        g.projects.delete(id)
        _error.value = "Could not bring the video in: ${e.message ?: e.javaClass.simpleName}"
      } finally {
        _importing.value = null
      }
    }
  }

  fun rename(meta: ProjectMeta, name: String) = viewModelScope.launch {
    val pf = g.projects.load(meta.id) ?: return@launch
    g.projects.save(pf.copy(meta = pf.meta.copy(name = name.trim().ifEmpty { meta.name })))
  }

  fun delete(meta: ProjectMeta) = viewModelScope.launch {
    g.subtitler.cancel(meta.id)
    g.projects.delete(meta.id)
  }

  fun thumb(id: String) = g.projects.thumbFile(id)
}
