package io.github.rdilipkumar.subtitlestudio.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
 * [timeMs] picks the moment inside the caption (for the highlighted word).
 */
@Composable
fun CaptionPreview(
  frame: Bitmap?,
  videoW: Int,
  videoH: Int,
  doc: SubtitleDoc,
  cue: Cue,
  timeMs: Long,
  modifier: Modifier = Modifier,
) {
  val renderer = remember { CaptionRenderer(App.graph.fonts) }
  val image = remember(frame) { frame?.asImageBitmap() }
  val w = if (videoW > 0) videoW else 1080
  val h = if (videoH > 0) videoH else 1920
  Canvas(
    modifier
      .aspectRatio(w.toFloat() / h)
      .clip(RoundedCornerShape(14.dp))
      .background(Color(0xFF2A2F3A)),
  ) {
    if (image != null) {
      drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    } else {
      drawRect(Color(0xFF3B4252), topLeft = Offset.Zero, size = size)
    }
    drawIntoCanvas { c ->
      val nc = c.nativeCanvas
      nc.save()
      nc.scale(size.width / w, size.height / h)
      renderer.drawCue(nc, w, h, doc, cue, timeMs)
      nc.restore()
    }
  }
}
