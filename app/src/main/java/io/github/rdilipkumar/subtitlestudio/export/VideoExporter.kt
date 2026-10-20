package io.github.rdilipkumar.subtitlestudio.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import io.github.rdilipkumar.subtitlestudio.core.format.SubtitleFormats
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.render.CaptionRenderer
import io.github.rdilipkumar.subtitlestudio.render.FontRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Burns captions into a new MP4 with Media3 Transformer, on the phone's video hardware. Each
 * frame's captions are drawn by the same [CaptionRenderer] as the preview, through a
 * [CanvasOverlay], so the result matches what the user saw. The sandbox copy is read only.
 */
@OptIn(UnstableApi::class)
class VideoExporter(private val context: Context, private val fonts: FontRegistry) {

  private class CaptionsOverlay(private val doc: SubtitleDoc, fonts: FontRegistry) : CanvasOverlay(true) {
    private val renderer = CaptionRenderer(fonts)
    private var blank = true

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
      val timeMs = presentationTimeUs / 1000
      // Between captions, leave the already-clear frame untouched: Media3 only re-uploads the
      // overlay to the GPU when its bitmap changed, which saves a full-frame copy per frame.
      if (blank && doc.cueAt(timeMs) == null) return
      canvas.drawColor(0, PorterDuff.Mode.CLEAR)
      blank = renderer.draw(canvas, canvas.width, canvas.height, doc, timeMs) == null
    }
  }

  /** Exports to a temp file and returns it. [onProgress] gets 0..1 on the main thread. */
  suspend fun burnIn(source: File, doc: SubtitleDoc, out: File, onProgress: (Float) -> Unit): File =
    withContext(Dispatchers.Main) {
      out.parentFile?.mkdirs()
      out.delete()
      val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
        .setEffects(Effects(listOf(), listOf(OverlayEffect(ImmutableList.of<TextureOverlay>(CaptionsOverlay(doc, fonts))))))
        .build()
      suspendCancellableCoroutine { cont ->
        var poll: Job? = null
        val transformer = Transformer.Builder(context)
          .setVideoMimeType(MimeTypes.VIDEO_H264)
          .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
              poll?.cancel()
              if (cont.isActive) cont.resume(out)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
              poll?.cancel()
              if (cont.isActive) cont.resumeWithException(exportException)
            }
          })
          .build()
        transformer.start(item, out.path)
        val holder = ProgressHolder()
        poll = launch {
          while (isActive) {
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
            delay(250)
          }
        }
        cont.invokeOnCancellation {
          poll?.cancel()
          // Transformer must be stopped on the thread that started it.
          Handler(Looper.getMainLooper()).post {
            transformer.cancel()
            out.delete()
          }
        }
      }.also { onProgress(1f) }
    }

  /** Copies a finished file into the phone's Movies folder (visible in the gallery). */
  suspend fun saveToGallery(file: File, displayName: String): Uri = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
      put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
      put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
      put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Offline Reel Captions")
      put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not save to the gallery")
    try {
      resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
      values.clear()
      values.put(MediaStore.Video.Media.IS_PENDING, 0)
      resolver.update(uri, values, null, null)
    } catch (e: Throwable) {
      // Phone full or cancelled: do not leave a half-written video in the gallery.
      runCatching { resolver.delete(uri, null, null) }
      throw e
    }
    uri
  }

  /** Writes a subtitle file into Downloads/Offline Reel Captions. */
  suspend fun saveSubtitleFile(text: String, displayName: String, mime: String): Uri = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME, displayName)
      put(MediaStore.Downloads.MIME_TYPE, mime)
      put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Offline Reel Captions")
      put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not save the file")
    try {
      resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
      values.clear()
      values.put(MediaStore.Downloads.IS_PENDING, 0)
      resolver.update(uri, values, null, null)
    } catch (e: Throwable) {
      runCatching { resolver.delete(uri, null, null) }
      throw e
    }
    uri
  }

  enum class FileType(val ext: String, val mime: String, val label: String) {
    SRT("srt", "application/x-subrip", "SRT (works everywhere)"),
    VTT("vtt", "text/vtt", "WebVTT (web players)"),
    ASS("ass", "text/x-ssa", "ASS (keeps colours and position)"),
  }

  fun subtitleText(type: FileType, doc: SubtitleDoc, tracks: List<TrackKind>, width: Int, height: Int, script: Script): String = when (type) {
    FileType.SRT -> SubtitleFormats.toSrt(doc, tracks)
    FileType.VTT -> SubtitleFormats.toVtt(doc, tracks)
    FileType.ASS -> SubtitleFormats.toAss(doc, tracks, width, height, fonts.familyName(doc.style.fontId, script))
  }
}
