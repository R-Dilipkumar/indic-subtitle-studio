package io.github.rdilipkumar.subtitlestudio.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class VideoInfo(
  val durationMs: Long,
  /** As shown on screen (rotation applied). */
  val width: Int,
  val height: Int,
  val hasAudio: Boolean,
)

/** Copies a picked video into a project's sandbox folder and reads its basic facts. */
object VideoImporter {

  fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }

  private fun sizeOf(context: Context, uri: Uri): Long =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
      if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
    } ?: -1L

  suspend fun copy(context: Context, uri: Uri, dest: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
    val total = sizeOf(context, uri)
    val tmp = File(dest.path + ".part")
    context.contentResolver.openInputStream(uri)!!.use { input ->
      FileOutputStream(tmp).use { out ->
        val buf = ByteArray(512 * 1024)
        var done = 0L
        while (true) {
          coroutineContext.ensureActive()
          val n = input.read(buf)
          if (n < 0) break
          out.write(buf, 0, n)
          done += n
          if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }
      }
    }
    if (!tmp.renameTo(dest)) throw java.io.IOException("Could not save the video copy")
  }

  fun info(file: File): VideoInfo {
    val r = MediaMetadataRetriever()
    try {
      r.setDataSource(file.path)
      val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
      val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
      val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
      val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
      val swap = rot == 90 || rot == 270
      return VideoInfo(dur, if (swap) h else w, if (swap) w else h, audio)
    } finally {
      r.release()
    }
  }

  /** Saves a small picture of the video for the project list. */
  fun saveThumbnail(video: File, dest: File, durationMs: Long) {
    val r = MediaMetadataRetriever()
    try {
      r.setDataSource(video.path)
      val atUs = (minOf(1_000L, durationMs / 3) * 1000L).coerceAtLeast(0)
      val frame = r.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 640) ?: return
      FileOutputStream(dest).use { frame.compress(Bitmap.CompressFormat.JPEG, 82, it) }
      frame.recycle()
    } catch (_: Exception) {
    } finally {
      r.release()
    }
  }

  /** A full-size frame, used to preview caption looks on the setup screen. */
  fun frameAt(video: File, timeMs: Long, maxSide: Int = 720): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
      r.setDataSource(video.path)
      r.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
    } catch (_: Exception) {
      null
    } finally {
      r.release()
    }
  }
}
