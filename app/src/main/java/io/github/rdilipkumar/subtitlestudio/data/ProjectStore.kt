package io.github.rdilipkumar.subtitlestudio.data

import io.github.rdilipkumar.subtitlestudio.core.model.ProjectFile
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

/**
 * The sandbox: each project is a private folder holding a copy of the user's video and the
 * subtitle file. The user's original video is never touched; exports are new files.
 *
 *   projects/<id>/source.mp4    copy of the imported video
 *   projects/<id>/project.json  captions, look and edit state
 *   projects/<id>/thumb.jpg     list thumbnail
 *   projects/<id>/wave.bin      audio loudness for the timeline (100 values per second)
 */
class ProjectStore(private val root: File, private val json: Json) {

  private val _list = MutableStateFlow<List<ProjectMeta>>(emptyList())
  val list: StateFlow<List<ProjectMeta>> = _list
  private val mutex = Mutex()

  fun dir(id: String): File = File(root, id)
  fun videoFile(meta: ProjectMeta): File = File(dir(meta.id), meta.videoFile)
  fun thumbFile(id: String): File = File(dir(id), "thumb.jpg")
  fun waveFile(id: String): File = File(dir(id), "wave.bin")
  private fun projectFile(id: String): File = File(dir(id), "project.json")

  fun newId(): String = "p" + System.currentTimeMillis().toString(36) + Random.nextInt(1000, 9999)

  suspend fun refresh() = withContext(Dispatchers.IO) {
    val metas = root.listFiles()?.mapNotNull { d -> readFile(File(d, "project.json"))?.meta } ?: emptyList()
    _list.value = metas.sortedByDescending { it.updatedAt }
  }

  suspend fun load(id: String): ProjectFile? = withContext(Dispatchers.IO) { readFile(projectFile(id)) }

  /** Saves atomically (write a temp file, then swap), so a crash never leaves a broken project. */
  suspend fun save(file: ProjectFile, touch: Boolean = true) = withContext(Dispatchers.IO) {
    mutex.withLock {
      val pf = if (touch) file.copy(meta = file.meta.copy(updatedAt = System.currentTimeMillis())) else file
      val d = dir(pf.meta.id).apply { mkdirs() }
      val tmp = File(d, "project.json.tmp")
      tmp.writeText(json.encodeToString(ProjectFile.serializer(), pf))
      val target = projectFile(pf.meta.id)
      if (!tmp.renameTo(target)) {
        target.delete()
        tmp.renameTo(target)
      }
      _list.value = (listOf(pf.meta) + _list.value.filterNot { it.id == pf.meta.id }).sortedByDescending { it.updatedAt }
    }
  }

  suspend fun delete(id: String) = withContext(Dispatchers.IO) {
    mutex.withLock {
      dir(id).deleteRecursively()
      _list.value = _list.value.filterNot { it.id == id }
    }
  }

  private fun readFile(f: File): ProjectFile? = try {
    if (f.exists()) json.decodeFromString(ProjectFile.serializer(), f.readText()) else null
  } catch (e: Exception) {
    null
  }
}
