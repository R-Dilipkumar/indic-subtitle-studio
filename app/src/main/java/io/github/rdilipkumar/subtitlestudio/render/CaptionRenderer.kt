package io.github.rdilipkumar.subtitlestudio.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.github.rdilipkumar.subtitlestudio.core.model.Anim
import io.github.rdilipkumar.subtitlestudio.core.model.BoxStyle
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.HighlightMode
import io.github.rdilipkumar.subtitlestudio.core.model.RevealMode
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Draws captions onto an Android [Canvas] in video pixels. The editor preview and the video
 * export both call this, so the exported video looks exactly like the preview.
 *
 * Not thread-safe (it reuses paints): give each thread its own renderer.
 */
class CaptionRenderer(private val fonts: FontRegistry) {

  companion object {
    /** How long one word takes to come in when words appear one by one. */
    private const val WORD_IN_MS = 200f
  }

  /** Where a caption was drawn, in video pixels, for tapping and the selection box. */
  data class Placed(
    val cueId: String,
    val cx: Float,
    val cy: Float,
    val halfW: Float,
    val halfH: Float,
    val scale: Float,
    val rotation: Float,
  ) {
    fun hit(px: Float, py: Float, slop: Float): Boolean {
      val dx = px - cx
      val dy = py - cy
      val r = Math.toRadians(-rotation.toDouble())
      val lx = (dx * cos(r) - dy * sin(r)).toFloat() / scale
      val ly = (dx * sin(r) + dy * cos(r)).toFloat() / scale
      return abs(lx) <= halfW + slop && abs(ly) <= halfH + slop
    }
  }

  private class WordBox(val text: String, val wordIndex: Int, val width: Float) {
    var x = 0f
    var baseline = 0f
  }

  private class Line(
    val kind: TrackKind,
    val size: Float,
    val typeface: Typeface,
    val words: List<WordBox>,
    val rowRects: List<RectF>,
    val ascent: Float,
    val descent: Float,
    val space: Float,
  )

  private class Layout(val lines: List<Line>, val halfW: Float, val halfH: Float, val baseSize: Float)

  private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
  private val stroke = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    style = Paint.Style.STROKE
    strokeJoin = Paint.Join.ROUND
    strokeCap = Paint.Cap.ROUND
  }
  private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val tmpRect = RectF()

  // Small cache: the same caption is drawn for many frames in a row.
  private val cache = object : LinkedHashMap<Any, Layout>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Layout>?): Boolean = size > 24
  }

  /**
   * Draws the caption on screen at [timeMs]. With [pinned], draws that caption fully shown
   * (used while editing a caption the playhead is not on). Returns where it was drawn.
   */
  fun draw(canvas: Canvas, w: Int, h: Int, doc: SubtitleDoc, timeMs: Long, pinned: Cue? = null, settled: Boolean = false): Placed? {
    val cue = pinned ?: doc.cueAt(timeMs) ?: return null
    val static = pinned != null && !pinned.contains(timeMs)
    return drawCue(canvas, w, h, doc, cue, if (static) -1 else timeMs, settled)
  }

  /**
   * [timeMs] < 0 draws the caption fully shown with no animation or highlight. [settled] skips
   * the coming-in and going-out motion (the editor uses it while paused, so a caption is never
   * invisible at its first frame).
   */
  fun drawCue(canvas: Canvas, w: Int, h: Int, doc: SubtitleDoc, cue: Cue, timeMs: Long, settled: Boolean = false): Placed? {
    val style = doc.styleOf(cue)
    val tr = doc.transformOf(cue)
    val layout = layoutFor(doc, cue, style, w, h) ?: return null
    val static = timeMs < 0
    val t = if (static) cue.startMs + style.animMs else timeMs

    // Entrance and exit motion.
    val m = Motion()
    if (!static && !settled) {
      if (style.animMs > 0) comeIn(m, style.entrance, ((t - cue.startMs).toFloat() / style.animMs).coerceIn(0f, 1f), layout.baseSize, layout.halfW)
      if (style.exitMs > 0) goOut(m, style.exit, 1f - ((cue.endMs - t).toFloat() / style.exitMs).coerceIn(0f, 1f), layout.baseSize, layout.halfW)
    }
    if (m.alpha <= 0.003f || m.clipTo <= m.clipFrom) return placed(cue, tr.x * w, tr.y * h, layout, tr.scale, tr.rotation)

    val cx = tr.x * w
    val cy = tr.y * h
    val totalScale = tr.scale * m.scale
    canvas.save()
    canvas.translate(cx + m.dx, cy + m.dy)
    if (tr.rotation != 0f) canvas.rotate(tr.rotation)
    canvas.scale(totalScale, totalScale)
    if (m.clipFrom > 0f || m.clipTo < 1f) {
      // Wipe: only part of the caption block (with room for outline and pop) is uncovered.
      val edge = layout.halfW + layout.baseSize
      canvas.clipRect(-edge + 2 * edge * m.clipFrom, -layout.halfH * 2f - layout.baseSize, -edge + 2 * edge * m.clipTo, layout.halfH * 2f + layout.baseSize)
    }
    val layered = m.alpha < 0.999f
    if (layered) {
      tmpRect.set(-layout.halfW * 1.5f, -layout.halfH * 1.6f, layout.halfW * 1.5f, layout.halfH * 1.6f)
      canvas.saveLayerAlpha(tmpRect, (m.alpha * 255).toInt())
    }

    // Boxes behind the text.
    if (style.box != BoxStyle.NONE) {
      boxPaint.color = style.boxColor
      val padX = layout.baseSize * 0.35f
      val padY = layout.baseSize * 0.16f
      val radius = layout.baseSize * style.boxRadius
      if (style.box == BoxStyle.BLOCK) {
        tmpRect.set(-layout.halfW - padX, -layout.halfH - padY, layout.halfW + padX, layout.halfH + padY)
        canvas.drawRoundRect(tmpRect, radius, radius, boxPaint)
      } else {
        for (line in layout.lines) for (r in line.rowRects) {
          tmpRect.set(r.left - padX, r.top - padY * 0.5f, r.right + padX, r.bottom + padY * 0.5f)
          canvas.drawRoundRect(tmpRect, radius, radius, boxPaint)
        }
      }
    }

    // Which word is being spoken.
    var active = -1
    if (!static) {
      for (i in cue.words.indices) if (cue.words[i].startMs <= t) active = i
    }

    val byLetter = style.reveal == RevealMode.LETTER_BY_LETTER && !static
    val oneByOne = (style.reveal == RevealMode.WORD_BY_WORD || byLetter) && !static
    for (line in layout.lines) {
      val base = when (line.kind) {
        TrackKind.NATIVE -> style.textColor
        TrackKind.PHONETIC -> style.phoneticColor
        TrackKind.TRANSLATION -> style.translationColor
      }
      val wordAligned = line.kind != TrackKind.TRANSLATION
      fill.typeface = line.typeface
      fill.textSize = line.size
      stroke.typeface = line.typeface
      stroke.textSize = line.size
      stroke.strokeWidth = style.strokeWidth * line.size
      stroke.color = style.strokeColor
      for (wb in line.words) {
        val wi = wb.wordIndex
        if (wordAligned && oneByOne && wi > active) continue
        val isActive = wordAligned && wi == active && !static
        val word = if (wi >= 0) cue.words.getOrNull(wi) else null
        // Typing: the spoken word shows only the letters said so far.
        val shown = if (byLetter && isActive && word != null) typedPart(wb.text, t, word.startMs, word.endMs) else null
        if (shown != null && shown.isEmpty()) continue
        val drawn = if (shown != null) WordBox(shown, wi, wb.width).also { it.x = wb.x; it.baseline = wb.baseline } else wb
        // Each word's own entrance, when words appear one by one.
        val wordStart = word?.startMs ?: cue.startMs
        val wp = if (wordAligned && style.reveal == RevealMode.WORD_BY_WORD && !static && !settled && style.wordAnim != Anim.NONE) {
          ((t - wordStart).toFloat() / WORD_IN_MS).coerceIn(0f, 1f)
        } else {
          1f
        }
        val wordSave = if (wp < 1f) beginWordMotion(canvas, style.wordAnim, wp, wb, line) else 0
        if (wordSave < 0) continue
        when (style.highlight) {
          HighlightMode.NONE -> drawWord(canvas, drawn, base, style, line)
          HighlightMode.COLOR -> drawWord(canvas, drawn, if (isActive) style.highlightColor else base, style, line)
          HighlightMode.POP -> {
            if (isActive) {
              // Grow the spoken word, but never so much that it covers the gap to its neighbours.
              val pop = minOf(1.14f, 1f + 1.1f * line.space / drawn.width.coerceAtLeast(1f))
              canvas.save()
              canvas.scale(pop, pop, drawn.x + drawn.width / 2, drawn.baseline - line.size * 0.35f)
              drawWord(canvas, drawn, style.highlightColor, style, line)
              canvas.restore()
            } else {
              drawWord(canvas, drawn, base, style, line)
            }
          }
          HighlightMode.BOX -> {
            if (isActive) {
              boxPaint.color = style.highlightColor
              val pad = line.size * 0.14f
              tmpRect.set(drawn.x - pad, drawn.baseline + line.ascent - pad * 0.3f, drawn.x + drawn.width + pad, drawn.baseline + line.descent + pad * 0.3f)
              canvas.drawRoundRect(tmpRect, line.size * 0.22f, line.size * 0.22f, boxPaint)
            }
            drawWord(canvas, drawn, base, style, line)
          }
          HighlightMode.SWEEP -> {
            if (!wordAligned || word == null || static) {
              drawWord(canvas, drawn, base, style, line)
            } else {
              val p = ((t - word.startMs).toFloat() / max(1L, word.endMs - word.startMs)).coerceIn(0f, 1f)
              when {
                p >= 1f -> drawWord(canvas, drawn, style.highlightColor, style, line)
                p <= 0f -> drawWord(canvas, drawn, base, style, line)
                else -> {
                  drawWord(canvas, drawn, base, style, line)
                  canvas.save()
                  canvas.clipRect(drawn.x - line.size, drawn.baseline + line.ascent * 1.3f, drawn.x + drawn.width * p, drawn.baseline + line.descent * 1.5f)
                  drawWord(canvas, drawn, style.highlightColor, style, line, strokeAgain = false)
                  canvas.restore()
                }
              }
            }
          }
        }
        if (wordSave > 0) canvas.restoreToCount(wordSave)
      }
    }

    if (layered) canvas.restore()
    canvas.restore()
    return placed(cue, cx, cy, layout, tr.scale, tr.rotation)
  }

  /** Where the caption is in its coming-in or going-out motion. */
  private class Motion {
    var alpha = 1f
    var scale = 1f
    var dx = 0f
    var dy = 0f
    /** Wipe: the uncovered part of the caption, from left (0) to right (1). */
    var clipFrom = 0f
    var clipTo = 1f
  }

  /** [e] runs 0 to 1 while the caption comes in. */
  private fun comeIn(m: Motion, a: Anim, e: Float, size: Float, halfW: Float) {
    val rest = 1f - easeOutCubic(e)
    when (a) {
      Anim.NONE -> Unit
      Anim.FADE -> m.alpha *= e
      Anim.POP -> { m.scale *= 0.55f + 0.45f * easeOutBack(e); m.alpha *= min(1f, e * 2.5f) }
      Anim.SLIDE_UP -> { m.dy += rest * size * 0.9f; m.alpha *= e }
      Anim.SLIDE_DOWN -> { m.dy -= rest * size * 0.9f; m.alpha *= e }
      Anim.SLIDE_LEFT -> { m.dx += rest * (halfW + size); m.alpha *= e }
      Anim.SLIDE_RIGHT -> { m.dx -= rest * (halfW + size); m.alpha *= e }
      Anim.ZOOM -> { m.scale *= 1f + 0.7f * rest; m.alpha *= e }
      Anim.BOUNCE -> { m.scale *= bounce(e); m.alpha *= min(1f, e * 3f) }
      Anim.DROP -> { m.dy -= (1f - easeOutBounce(e)) * size * 2.2f; m.alpha *= min(1f, e * 4f) }
      Anim.WIPE -> m.clipTo = min(m.clipTo, easeOutCubic(e))
    }
  }

  /** [q] runs 0 to 1 while the caption goes out. */
  private fun goOut(m: Motion, a: Anim, q: Float, size: Float, halfW: Float) {
    val left = 1f - q
    val gone = q * q * q
    when (a) {
      Anim.NONE -> Unit
      Anim.FADE -> m.alpha *= left
      Anim.POP, Anim.BOUNCE -> { m.scale *= 0.8f + 0.2f * left; m.alpha *= left }
      Anim.SLIDE_UP -> { m.dy -= gone * size * 0.9f; m.alpha *= left }
      Anim.SLIDE_DOWN -> { m.dy += gone * size * 0.9f; m.alpha *= left }
      Anim.SLIDE_LEFT -> { m.dx -= gone * (halfW + size); m.alpha *= left }
      Anim.SLIDE_RIGHT -> { m.dx += gone * (halfW + size); m.alpha *= left }
      Anim.ZOOM -> { m.scale *= 1f + 0.5f * gone; m.alpha *= left }
      Anim.DROP -> { m.dy += q * q * size * 2.5f; m.alpha *= left }
      Anim.WIPE -> m.clipFrom = max(m.clipFrom, gone)
    }
  }

  /**
   * Starts one word's coming-in motion ([p] 0 to 1) around the word's middle. Returns the
   * count to restore the canvas to after drawing the word, or -1 when nothing shows yet.
   */
  private fun beginWordMotion(canvas: Canvas, a: Anim, p: Float, wb: WordBox, line: Line): Int {
    val m = Motion()
    comeIn(m, a, p, line.size * 0.7f, wb.width / 2)
    if (m.alpha <= 0.003f || m.clipTo <= 0f) return -1
    val cx = wb.x + wb.width / 2
    val cy = wb.baseline - line.size * 0.35f
    val count = canvas.save()
    canvas.translate(m.dx, m.dy)
    if (m.scale != 1f) canvas.scale(m.scale, m.scale, cx, cy)
    if (m.clipTo < 1f) {
      canvas.clipRect(wb.x - line.size, wb.baseline + line.ascent * 1.4f, wb.x - line.size + (wb.width + line.size * 2) * m.clipTo, wb.baseline + line.descent * 1.6f)
    }
    if (m.alpha < 0.999f) {
      tmpRect.set(wb.x - line.size, wb.baseline + line.ascent * 1.5f, wb.x + wb.width + line.size, wb.baseline + line.descent * 1.7f)
      canvas.saveLayerAlpha(tmpRect, (m.alpha * 255).toInt())
    }
    return count
  }

  private val letters = BreakIterator.getCharacterInstance()

  /** The letters of [text] said by [timeMs], cut between whole letters (never mid-syllable). */
  private fun typedPart(text: String, timeMs: Long, startMs: Long, endMs: Long): String {
    val p = ((timeMs - startMs).toFloat() / max(1L, endMs - startMs)).coerceIn(0f, 1f)
    if (p >= 1f) return text
    letters.setText(text)
    var count = 0
    while (letters.next() != BreakIterator.DONE) count++
    val want = kotlin.math.ceil(p * count).toInt()
    if (want >= count) return text
    letters.first()
    var end = 0
    repeat(want) { end = letters.next() }
    return text.substring(0, end)
  }

  private fun placed(cue: Cue, cx: Float, cy: Float, l: Layout, scale: Float, rotation: Float) =
    Placed(cue.id, cx, cy, l.halfW, l.halfH, scale, rotation)

  private fun drawWord(canvas: Canvas, wb: WordBox, color: Int, style: CaptionStyle, line: Line, strokeAgain: Boolean = true) {
    val shadowOn = style.shadow
    if (strokeAgain && style.strokeWidth > 0f) {
      if (shadowOn) stroke.setShadowLayer(line.size * 0.08f, 0f, line.size * 0.05f, 0x99000000.toInt()) else stroke.clearShadowLayer()
      canvas.drawText(wb.text, wb.x, wb.baseline, stroke)
    }
    fill.color = color
    if (shadowOn && style.strokeWidth <= 0f && strokeAgain) {
      fill.setShadowLayer(line.size * 0.10f, 0f, line.size * 0.05f, 0xAA000000.toInt())
    } else {
      fill.clearShadowLayer()
    }
    canvas.drawText(wb.text, wb.x, wb.baseline, fill)
  }

  // ---------------------------------------------------------------------------------------
  // Layout

  private fun layoutFor(doc: SubtitleDoc, cue: Cue, style: CaptionStyle, w: Int, h: Int): Layout? {
    val key = listOf(System.identityHashCode(cue), cue.id, style, doc.tracks, w, h)
    cache[key]?.let { return it }
    val l = buildLayout(doc, cue, style, w, h) ?: return null
    cache[key] = l
    return l
  }

  private fun buildLayout(doc: SubtitleDoc, cue: Cue, style: CaptionStyle, w: Int, h: Int): Layout? {
    val baseSize = style.textSize * h
    val maxWidth = style.maxWidth * w
    val nativeScript = Romanizer.dominantScript(cue.text)
    val kinds = ArrayList<TrackKind>(3)
    for (k in doc.tracks.visibleInOrder()) {
      val text = when (k) {
        TrackKind.NATIVE -> cue.text
        TrackKind.PHONETIC -> cue.phonetic
        TrackKind.TRANSLATION -> cue.translation
      }
      if (text.isBlank()) continue
      // English videos: the English-letters line would repeat the words.
      if (k == TrackKind.PHONETIC && nativeScript == Script.LATIN && doc.tracks.showNative) continue
      kinds.add(k)
    }
    if (kinds.isEmpty()) return null

    val lines = ArrayList<Line>(kinds.size)
    var y = 0f
    var maxRow = 0f
    for ((i, kind) in kinds.withIndex()) {
      var size = if (i == 0) baseSize else baseSize * style.secondaryScale
      val script = if (kind == TrackKind.NATIVE) nativeScript else Script.LATIN
      val tf = fonts.typeface(style.fontId, script, style.bold)
      fill.typeface = tf
      fill.textSize = size
      val tokens: List<Pair<String, Int>> = when (kind) {
        TrackKind.NATIVE -> cue.words.mapIndexed { wi, wd -> latinCase(wd.text, style) to wi }
        TrackKind.PHONETIC -> cue.words.mapIndexed { wi, wd -> latinCase(wd.phonetic, style) to wi }
        TrackKind.TRANSLATION -> cue.translation.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { latinCase(it, style) to -1 }
      }.filter { it.first.isNotEmpty() }
      // A single word wider than the line would run off the video: shrink this line to fit.
      val widest = tokens.maxOfOrNull { fill.measureText(it.first) } ?: 0f
      if (widest > maxWidth) {
        size *= maxWidth / widest
        fill.textSize = size
      }
      val fm = fill.fontMetrics
      // Some fonts have a very thin space, and the "pop" highlight grows the spoken word, so
      // keep a clear gap between words.
      val popRoom = if (style.highlight == HighlightMode.POP && kind != TrackKind.TRANSLATION) 0.10f else 0f
      val space = max(fill.measureText(" "), size * (0.28f + popRoom))
      val boxes = tokens.map { (text, wi) -> WordBox(text, wi, fill.measureText(text)) }

      // Wrap into rows, then avoid a lonely last word.
      val rows = ArrayList<MutableList<WordBox>>()
      var cur = ArrayList<WordBox>()
      var curW = 0f
      for (b in boxes) {
        val add = if (cur.isEmpty()) b.width else curW + space + b.width
        if (cur.isNotEmpty() && add > maxWidth) {
          rows.add(cur)
          cur = ArrayList()
          curW = 0f
        }
        curW = if (cur.isEmpty()) b.width else curW + space + b.width
        cur.add(b)
      }
      if (cur.isNotEmpty()) rows.add(cur)
      if (rows.size >= 2 && rows.last().size == 1 && rows[rows.size - 2].size >= 3) {
        val moved = rows[rows.size - 2].removeAt(rows[rows.size - 2].lastIndex)
        rows.last().add(0, moved)
      }

      val rowH = (fm.descent - fm.ascent) * 0.92f
      if (i > 0) y += baseSize * style.lineSpacing
      val rects = ArrayList<RectF>(rows.size)
      for (row in rows) {
        val rowW = row.sumOf { it.width.toDouble() }.toFloat() + space * (row.size - 1)
        maxRow = max(maxRow, rowW)
        var x = -rowW / 2
        val baseline = y - fm.ascent * 0.92f
        for (b in row) {
          b.x = x
          b.baseline = baseline
          x += b.width + space
        }
        rects.add(RectF(-rowW / 2, y, rowW / 2, y + rowH))
        y += rowH
      }
      lines.add(Line(kind, size, tf, boxes, rects, fm.ascent * 0.92f, fm.descent * 0.92f, space))
    }
    // Centre the block vertically on the caption position.
    val half = y / 2
    for (line in lines) {
      for (b in line.words) b.baseline -= half
      for (r in line.rowRects) r.offset(0f, -half)
    }
    return Layout(lines, maxRow / 2, half, baseSize)
  }

  private fun latinCase(s: String, style: CaptionStyle): String =
    if (style.uppercaseLatin && Romanizer.dominantScript(s) == Script.LATIN) s.uppercase(Locale.ROOT) else s

  private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
  }

  private fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)

  private fun easeOutBounce(x: Float): Float {
    val n = 7.5625f
    val d = 2.75f
    return when {
      x < 1f / d -> n * x * x
      x < 2f / d -> (x - 1.5f / d).let { n * it * it + 0.75f }
      x < 2.5f / d -> (x - 2.25f / d).let { n * it * it + 0.9375f }
      else -> (x - 2.625f / d).let { n * it * it + 0.984375f }
    }
  }

  private fun bounce(x: Float): Float =
    if (x < 0.6f) (x / 0.6f) * 1.15f else 1.15f - ((x - 0.6f) / 0.4f) * 0.15f + 0.04f * sin((x - 0.6f) / 0.4f * PI.toFloat())
}
