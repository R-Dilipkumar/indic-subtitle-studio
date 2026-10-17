package io.github.rdilipkumar.subtitlestudio.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

sealed interface PackState {
  data object Missing : PackState
  data class Downloading(val doneBytes: Long, val totalBytes: Long) : PackState {
    val fraction: Float get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
  }
  data object Ready : PackState
  data class Failed(val message: String) : PackState
}

/**
 * Keeps language packs on the phone. Downloads resume where they stopped (a ".part" file plus
 * an HTTP Range request), and finished files are checked against their exact size.
 * Packs can also be imported from files the user copied to the phone, for zero internet.
 */
class ModelStore(private val context: Context, private val scope: CoroutineScope) {

  val root: File = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }

  private val _states = MutableStateFlow(ModelCatalog.all.associate { it.id to stateOnDisk(it) })
  val states: StateFlow<Map<String, PackState>> = _states
  private val jobs = HashMap<String, Job>()

  fun dir(pack: ModelPack): File = File(root, pack.id)
  fun file(pack: ModelPack, name: String): File = File(dir(pack), name)

  fun isReady(pack: ModelPack): Boolean = _states.value[pack.id] == PackState.Ready

  fun bytesOnDisk(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

  private fun stateOnDisk(pack: ModelPack): PackState =
    if (pack.files.all { File(File(root, pack.id), it.name).let { f -> f.isFile && f.length() > 0 } }) PackState.Ready
    else PackState.Missing

  private fun set(pack: ModelPack, s: PackState) = _states.update { it + (pack.id to s) }

  fun download(pack: ModelPack) {
    if (jobs[pack.id]?.isActive == true || isReady(pack)) return
    set(pack, PackState.Downloading(0, pack.totalBytes))
    jobs[pack.id] = scope.launch(Dispatchers.IO) {
      val total = pack.totalBytes
      try {
        dir(pack).mkdirs()
        var doneBefore = 0L
        for (pf in pack.files) {
          val dest = file(pack, pf.name)
          if (dest.isFile && dest.length() == pf.bytes) {
            doneBefore += pf.bytes
            continue
          }
          var lastUi = 0L
          downloadFile(pf, dest) { got ->
            val now = System.currentTimeMillis()
            if (now - lastUi > 250) {
              lastUi = now
              set(pack, PackState.Downloading(doneBefore + got, total))
            }
          }
          doneBefore += pf.bytes
        }
        set(pack, stateOnDisk(pack))
      } catch (e: kotlinx.coroutines.CancellationException) {
        set(pack, PackState.Missing)
        throw e
      } catch (e: Exception) {
        set(pack, PackState.Failed(e.message ?: "Download failed"))
      }
    }
  }

  fun cancel(pack: ModelPack) {
    jobs.remove(pack.id)?.cancel()
    set(pack, stateOnDisk(pack))
  }

  fun delete(pack: ModelPack) {
    cancel(pack)
    dir(pack).deleteRecursively()
    set(pack, PackState.Missing)
  }

  private suspend fun downloadFile(pf: PackFile, dest: File, onBytes: (Long) -> Unit) {
    val part = File(dest.path + ".part")
    var attempt = 0
    while (true) {
      try {
        fetchInto(pf, part, onBytes)
        break
      } catch (e: IOException) {
        // Flaky mobile data: retry a few times, keeping what we already have.
        if (++attempt >= 4) throw e
        kotlinx.coroutines.delay(1500L * attempt)
      }
    }
    if (pf.bytes > 0 && part.length() != pf.bytes) {
      part.delete()
      throw IOException("${pf.name} came out the wrong size; please try again")
    }
    if (!part.renameTo(dest)) throw IOException("Could not save ${pf.name}")
  }

  private suspend fun fetchInto(pf: PackFile, part: File, onBytes: (Long) -> Unit) {
    var have = if (part.exists()) part.length() else 0L
    if (pf.bytes > 0 && have == pf.bytes) return
    if (pf.bytes in 1 until have) {
      part.delete()
      have = 0
    }
    val conn = (URL(pf.url).openConnection() as HttpURLConnection).apply {
      instanceFollowRedirects = true
      connectTimeout = 20_000
      readTimeout = 30_000
      if (have > 0) setRequestProperty("Range", "bytes=$have-")
    }
    try {
      val code = conn.responseCode
      if (code == 200 && have > 0) {
        have = 0 // the server sent the whole file again
      } else if (code !in 200..299) {
        throw IOException("Server said $code for ${pf.name}")
      }
      conn.inputStream.use { input ->
        FileOutputStream(part, have > 0).use { out ->
          val buf = ByteArray(256 * 1024)
          var got = have
          while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            got += n
            onBytes(got)
          }
        }
      }
    } finally {
      conn.disconnect()
    }
  }

  /**
   * Imports pack files the user picked (the files themselves, or a .zip holding them).
   * Returns how many of the pack's files were found.
   */
  suspend fun importFiles(pack: ModelPack, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
    val wanted = pack.files.associateBy { it.name }
    val found = HashSet<String>()
    dir(pack).mkdirs()
    for (uri in uris) {
      val name = displayName(uri) ?: continue
      if (name.endsWith(".zip", ignoreCase = true)) {
        context.contentResolver.openInputStream(uri)?.use { raw ->
          ZipInputStream(raw.buffered()).use { zip ->
            while (true) {
              val e = zip.nextEntry ?: break
              val base = e.name.substringAfterLast('/')
              if (!e.isDirectory && base in wanted) {
                val tmp = File(dir(pack), "$base.part")
                FileOutputStream(tmp).use { zip.copyTo(it) }
                if (tmp.renameTo(file(pack, base))) found.add(base)
              }
            }
          }
        }
      } else if (name in wanted) {
        val tmp = File(dir(pack), "$name.part")
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(tmp).use { input.copyTo(it) } }
        if (tmp.renameTo(file(pack, name))) found.add(name)
      }
    }
    set(pack, stateOnDisk(pack))
    found.size
  }

  private fun displayName(uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')
}
