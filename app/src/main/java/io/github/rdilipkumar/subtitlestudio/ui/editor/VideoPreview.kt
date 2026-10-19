package io.github.rdilipkumar.subtitlestudio.ui.editor

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.core.model.SafeZone
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.Transform
import io.github.rdilipkumar.subtitlestudio.render.CaptionRenderer
import io.github.rdilipkumar.subtitlestudio.ui.common.CaptionDrag
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import kotlin.math.min

/**
 * The video with its captions drawn on top by the shared renderer (same as export).
 * Tap a caption to select it, drag to move, pinch to resize, twist to rotate. Guides appear
 * when a caption snaps to the centre, and the safe zone shows where app buttons will cover.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
  player: ExoPlayer,
  videoW: Int,
  videoH: Int,
  doc: SubtitleDoc,
  positionMs: Long,
  playing: Boolean,
  selectedId: String?,
  safeZone: SafeZone,
  editingTransform: Transform,
  onSelect: (String?) -> Unit,
  onDoubleTap: (String) -> Unit,
  onTransform: (Transform, done: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val renderer = remember { CaptionRenderer(App.graph.fonts) }
  val w = if (videoW > 0) videoW else 1080
  val h = if (videoH > 0) videoH else 1920
  // Where the caption was last drawn. A plain holder, not state: it is written while drawing.
  val placedRef = remember { PlacedRef() }
  var guideX by remember { mutableStateOf(false) }
  var guideY by remember { mutableStateOf(false) }
  var dragging by remember { mutableStateOf(false) }
  val latestTransform by rememberUpdatedState(editingTransform)
  val latestSelected by rememberUpdatedState(selectedId)
  val latestDoc by rememberUpdatedState(doc)
  val latestPos by rememberUpdatedState(positionMs)
  val latestPlaying by rememberUpdatedState(playing)

  BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
    val density = LocalDensity.current
    val maxWpx = constraints.maxWidth.toFloat()
    val maxHpx = constraints.maxHeight.toFloat()
    val k = min(maxWpx / w, maxHpx / h)
    val boxW = with(density) { (w * k).toDp() }
    val boxH = with(density) { (h * k).toDp() }

    Box(Modifier.size(boxW, boxH)) {
      AndroidView(
        factory = { ctx ->
          PlayerView(ctx).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            this.player = player
          }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
      )

      Canvas(
        Modifier
          .fillMaxSize()
          .pointerInput(w, h) {
            val scaleK = size.width / w.toFloat()
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val vx = down.position.x / scaleK
              val vy = down.position.y / scaleK
              val slop = 24f / scaleK
              val p = placedRef.value
              val onCaption = p != null && p.hit(vx, vy, slop)
              if (onCaption && p!!.cueId != latestSelected) onSelect(p.cueId)
              var moved = false
              var start = latestTransform
              var cur = start
              // What was last sent: the screen's copy may not have caught up when the finger lifts.
              var sent = start
              var totalPan = Offset.Zero
              val startTime = down.uptimeMillis
              while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.any { it.pressed }
                if (!pressed) break
                if (!onCaption) continue
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                val rot = event.calculateRotation()
                totalPan += pan
                if (!moved && (totalPan.getDistance() > viewConfiguration.touchSlop || zoom != 1f || rot != 0f)) {
                  moved = true
                  dragging = true
                  start = latestTransform
                  cur = start
                }
                if (moved) {
                  // Snaps to the centre lines and to level.
                  val step = CaptionDrag.step(cur, pan, zoom, rot, size.width.toFloat(), size.height.toFloat())
                  cur = step.raw
                  guideX = step.guideX
                  guideY = step.guideY
                  sent = step.shown
                  onTransform(sent, false)
                  event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                }
              }
              if (moved) {
                onTransform(sent, true)
              } else {
                val up = System.currentTimeMillis()
                if (!onCaption) onSelect(null)
                else if (p != null) {
                  if (lastTapId == p.cueId && up - lastTapAt < 350) onDoubleTap(p.cueId)
                  lastTapId = p.cueId
                  lastTapAt = up
                }
              }
              dragging = false
              guideX = false
              guideY = false
            }
          },
      ) {
        val s = size.width / w
        // Safe zone: shade where the app's buttons and captions will sit.
        if (safeZone != SafeZone.NONE) {
          val shade = Color(0x55FF3D3D)
          drawRect(shade, Offset.Zero, Size(size.width, size.height * safeZone.top))
          drawRect(shade, Offset(0f, size.height * (1 - safeZone.bottom)), Size(size.width, size.height * safeZone.bottom))
          drawRect(shade, Offset(size.width * (1 - safeZone.right), size.height * safeZone.top), Size(size.width * safeZone.right, size.height * (1 - safeZone.top - safeZone.bottom)))
        }
        // Captions.
        drawIntoCanvas { c ->
          val nc = c.nativeCanvas
          nc.save()
          nc.scale(s, s)
          val pinned = latestSelected?.let { id -> latestDoc.cue(id) }?.takeIf { !it.contains(latestPos) }
          placedRef.value = renderer.draw(nc, w, h, latestDoc, latestPos, pinned, settled = !latestPlaying)
          nc.restore()
        }
        // Selection box.
        val p = placedRef.value
        if (p != null && p.cueId == latestSelected) {
          val cx = p.cx * s
          val cy = p.cy * s
          val hw = p.halfW * p.scale * s + 12f
          val hh = p.halfH * p.scale * s + 8f
          rotate(p.rotation, Offset(cx, cy)) {
            drawRect(
              Studio.accent,
              topLeft = Offset(cx - hw, cy - hh),
              size = Size(hw * 2, hh * 2),
              style = Stroke(width = 2.5f, pathEffect = if (dragging) null else PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
            for (corner in listOf(Offset(cx - hw, cy - hh), Offset(cx + hw, cy - hh), Offset(cx - hw, cy + hh), Offset(cx + hw, cy + hh))) {
              drawCircle(Studio.accent, 7f, corner)
            }
          }
        }
        if (guideX) drawLine(Studio.green, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
        if (guideY) drawLine(Studio.green, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2f)
      }
    }
  }
}

private class PlacedRef {
  var value: CaptionRenderer.Placed? = null
}

// Double-tap tracking shared by the gesture loop (one preview on screen at a time).
private var lastTapId: String? = null
private var lastTapAt = 0L
