package io.github.rdilipkumar.subtitlestudio.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.render.CaptionRenderer

/**
 * A still frame with a caption drawn by the real renderer: a small, exact preview of a look.
 * [timeMs] picks the moment inside the caption (for the highlighted word). With [closeUp] the
 * frame fills the given width and is cropped around the caption, so the text stays readable
 * in a short box; the caller then sets the box size.
 */
@Composable
fun CaptionPreview(
  frame: Bitmap?,
  videoW: Int,
  videoH: Int,
  doc: SubtitleDoc,
  cue: Cue?,
  timeMs: Long,
  modifier: Modifier = Modifier,
  closeUp: Boolean = false,
) {
  val renderer = remember { CaptionRenderer(App.graph.fonts) }
  val image = remember(frame) { frame?.asImageBitmap() }
  val w = if (videoW > 0) videoW else 1080
  val h = if (videoH > 0) videoH else 1920
  Canvas(
    (if (closeUp) modifier else modifier.aspectRatio(w.toFloat() / h))
      .clip(RoundedCornerShape(14.dp))
      .background(Color(0xFF2A2F3A)),
  ) {
    val scale = size.width / w
    val frameH = h * scale
    // Close up: keep the caption's centre in the middle of the box, without showing past the frame.
    val dy = when {
      !closeUp -> 0f
      frameH <= size.height -> (size.height - frameH) / 2f
      else -> {
        val focus = (cue?.let { doc.transformOf(it).y } ?: doc.transform.y) * frameH
        (size.height / 2f - focus).coerceIn(size.height - frameH, 0f)
      }
    }
    if (image != null) {
      drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(0, dy.toInt()),
        dstSize = IntSize(size.width.toInt(), frameH.toInt()),
      )
    } else {
      drawRect(Color(0xFF3B4252), topLeft = Offset(0f, dy), size = Size(size.width, frameH))
    }
    if (cue != null) {
      drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        nc.translate(0f, dy)
        nc.scale(scale, scale)
        renderer.drawCue(nc, w, h, doc, cue, timeMs)
        nc.restore()
      }
    }
  }
}

/** [CaptionPreview] that plays [doc]'s captions over and over, so motion and timing show. */
@Composable
fun PlayingCaptionPreview(
  frame: Bitmap?,
  videoW: Int,
  videoH: Int,
  doc: SubtitleDoc,
  loopMs: Long,
  modifier: Modifier = Modifier,
  closeUp: Boolean = false,
) {
  var t by remember { mutableLongStateOf(0L) }
  LaunchedEffect(loopMs) {
    val start = withFrameMillis { it }
    while (true) withFrameMillis { now -> t = (now - start) % loopMs.coerceAtLeast(1) }
  }
  val cue = doc.cues.firstOrNull { t >= it.startMs && t < it.endMs }
  CaptionPreview(frame, videoW, videoH, doc, cue, t, modifier, closeUp)
}
