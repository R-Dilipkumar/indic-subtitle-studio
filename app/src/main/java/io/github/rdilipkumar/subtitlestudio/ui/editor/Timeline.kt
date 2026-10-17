package io.github.rdilipkumar.subtitlestudio.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Origin
import io.github.rdilipkumar.subtitlestudio.core.model.TranslationState
import io.github.rdilipkumar.subtitlestudio.ui.common.formatTime
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import kotlin.math.abs
import kotlin.math.roundToLong

/** The colour that tells the user what happened to a caption. */
fun cueMarkColor(c: Cue): Color = when {
  c.lowConfidence && !c.edited -> Studio.markCheck
  c.origin == Origin.USER -> Studio.markAdded
  c.edited -> Studio.markEdited
  else -> Studio.markAi
}

/**
 * Scrolling timeline with the playhead fixed in the middle (like phone video editors):
 * drag to scrub, pinch to zoom, tap a caption to select it. On the selected caption, drag an
 * edge to change when it starts or ends, or drag its middle to move it.
 */
@Composable
fun Timeline(
  cues: List<Cue>,
  durationMs: Long,
  positionMs: Long,
  selectedId: String?,
  waveform: ByteArray?,
  onSeek: (Long, done: Boolean) -> Unit,
  onSelect: (String?) -> Unit,
  onRetime: (String, Long, Long) -> Unit,
  onMove: (String, Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  var pxPerMs by remember { mutableFloatStateOf(with(density) { 90.dp.toPx() } / 1000f) }
  val latestCues by rememberUpdatedState(cues)
  val latestPos by rememberUpdatedState(positionMs)
  val latestSel by rememberUpdatedState(selectedId)
  val labelPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      typeface = Typeface.DEFAULT
    }
  }
  val rulerPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Studio.muted.toArgb() }
  }
  val edgePx = with(density) { 16.dp.toPx() }
  val laneTop = with(density) { 46.dp.toPx() }
  val laneH = with(density) { 46.dp.toPx() }

  Canvas(
    modifier
      .fillMaxWidth()
      .height(104.dp)
      .background(Studio.surface)
      .pointerInput(durationMs) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val center = size.width / 2f
          val posAtDown = latestPos
          val timeAt = { x: Float -> posAtDown + ((x - center) / pxPerMs).roundToLong() }
          val tDown = timeAt(down.position.x)
          val sel = latestSel?.let { id -> latestCues.firstOrNull { it.id == id } }
          val inLane = down.position.y in (laneTop - 8f)..(laneTop + laneH + 8f)
          // What this drag does.
          val mode = when {
            sel != null && inLane && abs(down.position.x - (center + (sel.startMs - posAtDown) * pxPerMs)) < edgePx -> 1 // start edge
            sel != null && inLane && abs(down.position.x - (center + (sel.endMs - posAtDown) * pxPerMs)) < edgePx -> 2 // end edge
            sel != null && inLane && tDown in sel.startMs..sel.endMs -> 3 // move
            else -> 0 // scroll
          }
          var moved = false
          var lastX = down.position.x
          var totalDx = 0f
          var zooming = false
          while (true) {
            val event = awaitPointerEvent()
            val active = event.changes.filter { it.pressed }
            if (active.isEmpty()) break
            if (active.size >= 2) {
              zooming = true
              pxPerMs = (pxPerMs * event.calculateZoom()).coerceIn(0.01f, 2.5f)
              event.changes.forEach { it.consume() }
              continue
            }
            if (zooming) continue
            val x = active.first().position.x
            val dx = x - lastX
            lastX = x
            totalDx += dx
            if (!moved && abs(totalDx) > viewConfiguration.touchSlop) moved = true
            if (!moved) continue
            event.changes.forEach { it.consume() }
            val dMs = (totalDx / pxPerMs).roundToLong()
            when (mode) {
              1 -> onRetime(sel!!.id, sel.startMs + dMs, sel.endMs)
              2 -> onRetime(sel!!.id, sel.startMs, sel.endMs + dMs)
              3 -> onMove(sel!!.id, sel.startMs + dMs - (latestCues.firstOrNull { it.id == sel.id }?.startMs ?: sel.startMs))
              else -> onSeek((posAtDown - dMs).coerceIn(0, durationMs), false)
            }
          }
          if (!moved && !zooming) {
            val hit = if (inLane) latestCues.firstOrNull { tDown in it.startMs until it.endMs } else null
            onSelect(hit?.id)
            if (hit != null && hit.id != sel?.id) onSeek(hit.startMs, true)
          } else if (mode == 0 && moved) {
            onSeek(latestPos, true)
          }
        }
      },
  ) {
    val center = size.width / 2f
    val tLeft = positionMs - (center / pxPerMs).toLong()
    val tRight = positionMs + (center / pxPerMs).toLong()
    fun x(t: Long) = center + (t - positionMs) * pxPerMs

    // Outside the video.
    if (tLeft < 0) drawRect(Studio.bg, Offset.Zero, Size(x(0), size.height))
    if (tRight > durationMs) drawRect(Studio.bg, Offset(x(durationMs), 0f), Size(size.width - x(durationMs), size.height))

    // Ruler.
    val stepMs = when {
      pxPerMs > 0.4f -> 500L
      pxPerMs > 0.08f -> 1000L
      pxPerMs > 0.03f -> 5000L
      else -> 15000L
    }
    labelPaint.textSize = 10.dp.toPx()
    rulerPaint.textSize = 10.dp.toPx()
    var t = (tLeft / stepMs).coerceAtLeast(0) * stepMs
    while (t <= tRight && t <= durationMs) {
      val xx = x(t)
      val major = t % (stepMs * 2) == 0L
      drawLine(Studio.outline, Offset(xx, 0f), Offset(xx, if (major) 10.dp.toPx() else 6.dp.toPx()), strokeWidth = 1.5f)
      if (major) drawIntoCanvas { it.nativeCanvas.drawText(formatTime(t, tenths = stepMs < 1000), xx + 3f, 12.dp.toPx(), rulerPaint) }
      t += stepMs
    }

    // Sound.
    if (waveform != null && waveform.isNotEmpty()) {
      val top = 16.dp.toPx()
      val hgt = 26.dp.toPx()
      val step = 3f
      var xx = 0f
      while (xx < size.width) {
        val tm = tLeft + (xx / pxPerMs).toLong()
        val i = (tm / 10).toInt()
        if (i in waveform.indices) {
          val v = (waveform[i].toInt() and 0xFF) / 255f
          val bh = (v * hgt).coerceAtLeast(1f)
          drawLine(Studio.muted.copy(alpha = 0.45f), Offset(xx, top + hgt - bh), Offset(xx, top + hgt), strokeWidth = 2f)
        }
        xx += step
      }
    }

    // Captions.
    val radius = CornerRadius(8.dp.toPx())
    labelPaint.textSize = 11.dp.toPx()
    for (c in cues) {
      if (c.endMs < tLeft || c.startMs > tRight) continue
      val l = x(c.startMs)
      val r = x(c.endMs)
      val col = cueMarkColor(c)
      val selected = c.id == selectedId
      drawRoundRect(col.copy(alpha = if (selected) 0.95f else 0.75f), Offset(l, laneTop), Size((r - l).coerceAtLeast(2f), laneH), radius)
      if (selected) {
        drawRoundRect(Studio.accent, Offset(l, laneTop), Size((r - l).coerceAtLeast(2f), laneH), radius, style = Stroke(2.5.dp.toPx()))
        // Edge handles.
        drawRoundRect(Studio.accent, Offset(l - 3.dp.toPx(), laneTop + laneH * 0.25f), Size(6.dp.toPx(), laneH * 0.5f), CornerRadius(3.dp.toPx()))
        drawRoundRect(Studio.accent, Offset(r - 3.dp.toPx(), laneTop + laneH * 0.25f), Size(6.dp.toPx(), laneH * 0.5f), CornerRadius(3.dp.toPx()))
      }
      // Word boundaries, when zoomed in enough to read them.
      if (pxPerMs > 0.15f) for (w in c.words.drop(1)) {
        val wx = x(w.startMs)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(wx, laneTop + 6f), Offset(wx, laneTop + laneH - 6f), strokeWidth = 1f)
      }
      if (c.translationState == TranslationState.STALE) drawCircle(Studio.markStale, 4.dp.toPx(), Offset(r - 7.dp.toPx(), laneTop + 7.dp.toPx()))
      if (c.lowConfidence && !c.edited) drawCircle(Studio.markCheck, 4.dp.toPx(), Offset(l + 7.dp.toPx(), laneTop + 7.dp.toPx()))
      if (r - l > 24f) {
        clipRect(l + 4f, laneTop, r - 4f, laneTop + laneH) {
          drawIntoCanvas {
            it.nativeCanvas.drawText(c.text, l + 7.dp.toPx(), laneTop + laneH * 0.45f, labelPaint)
            labelPaint.alpha = 170
            it.nativeCanvas.drawText(c.phonetic, l + 7.dp.toPx(), laneTop + laneH * 0.82f, labelPaint)
            labelPaint.alpha = 255
          }
        }
      }
    }

    // Playhead.
    drawLine(Studio.accent, Offset(center, 0f), Offset(center, size.height), strokeWidth = 2.dp.toPx())
    drawCircle(Studio.accent, 5.dp.toPx(), Offset(center, 4.dp.toPx()))
  }
}
