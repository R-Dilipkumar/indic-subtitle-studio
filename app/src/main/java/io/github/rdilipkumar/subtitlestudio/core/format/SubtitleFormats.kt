package io.github.rdilipkumar.subtitlestudio.core.format

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.HighlightMode
import io.github.rdilipkumar.subtitlestudio.core.model.BoxStyle
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.core.timing.TranslationSplit
import io.github.rdilipkumar.subtitlestudio.core.timing.TranslationTrack
import io.github.rdilipkumar.subtitlestudio.core.timing.WordTiming
import java.util.Locale
import kotlin.math.roundToInt

/** Subtitle files: SRT and WebVTT (plain, work everywhere) and ASS (keeps colours and position). */
object SubtitleFormats {

  /** The text lines of one caption for the chosen [tracks], top to bottom. */
  fun linesOf(cue: Cue, tracks: List<TrackKind>, uppercaseLatin: Boolean = false, translation: String = cue.translation): List<String> =
    linesWithKinds(cue, tracks, uppercaseLatin, translation).map { it.second }

  /** Like [linesOf], keeping which track each line came from. */
  fun linesWithKinds(cue: Cue, tracks: List<TrackKind>, uppercaseLatin: Boolean = false, translation: String = cue.translation): List<Pair<TrackKind, String>> {
    val lines = ArrayList<Pair<TrackKind, String>>(3)
    for (t in tracks) {
      val line = when (t) {
        TrackKind.NATIVE -> cue.text
        TrackKind.PHONETIC -> cue.phonetic
        TrackKind.TRANSLATION -> translation
      }.trim()
      // English videos: the English-letters line equals the words, so do not repeat it.
      if (line.isEmpty() || lines.any { it.second.equals(line, ignoreCase = true) }) continue
      lines.add(t to if (uppercaseLatin && t != TrackKind.NATIVE) line.uppercase(Locale.ROOT) else line)
    }
    return lines
  }

  /**
   * The translation a file shows with each caption. With its own size on screen the translation
   * may change inside a caption, which files cannot do, so a caption carries every piece said
   * while it shows.
   */
  fun translationFor(doc: SubtitleDoc): (Cue) -> String {
    val size = doc.tracks.translationSize ?: return { it.translation }
    val chunks = TranslationTrack.build(doc.cues, size)
    return { TranslationTrack.textDuring(chunks, it) }
  }

  fun toSrt(doc: SubtitleDoc, tracks: List<TrackKind>): String = buildString {
    val translation = translationFor(doc)
    var n = 1
    for (c in doc.cues) {
      val lines = linesOf(c, tracks, translation = translation(c))
      if (lines.isEmpty()) continue
      append(n++).append('\n')
      append(srtTime(c.startMs)).append(" --> ").append(srtTime(c.endMs)).append('\n')
      lines.forEach { append(it).append('\n') }
      append('\n')
    }
  }

  fun toVtt(doc: SubtitleDoc, tracks: List<TrackKind>): String = buildString {
    val translation = translationFor(doc)
    append("WEBVTT\n\n")
    for (c in doc.cues) {
      val lines = linesOf(c, tracks, translation = translation(c))
      if (lines.isEmpty()) continue
      append(vttTime(c.startMs)).append(" --> ").append(vttTime(c.endMs)).append('\n')
      lines.forEach { append(it.replace("-->", "->")).append('\n') }
      append('\n')
    }
  }

  /**
   * Advanced SubStation Alpha. Keeps font, colours, outline, box, each caption's position and
   * rotation, and word-by-word karaoke timing, so other editors and players show it styled.
   */
  fun toAss(doc: SubtitleDoc, tracks: List<TrackKind>, videoWidth: Int, videoHeight: Int, fontName: String): String {
    val w = if (videoWidth > 0) videoWidth else 1080
    val h = if (videoHeight > 0) videoHeight else 1920
    val st = doc.style
    val size = (st.textSize * h).roundToInt().coerceAtLeast(8)
    // With a box (BorderStyle 3) ASS paints the box in the outline colour, sized by Outline.
    val boxed = st.box != BoxStyle.NONE
    val outline = if (boxed) size * 0.25f else (st.strokeWidth * size * 0.5f).coerceAtLeast(0f)
    val shadow = if (st.shadow && !boxed) (size * 0.06f) else 0f
    val borderStyle = if (boxed) 3 else 1
    val outlineColor = if (boxed) st.boxColor else st.strokeColor
    val back = if (boxed) st.boxColor else 0x80000000.toInt()
    return buildString {
      append("[Script Info]\nScriptType: v4.00+\nPlayResX: $w\nPlayResY: $h\nWrapStyle: 0\nScaledBorderAndShadow: yes\n\n")
      append("[V4+ Styles]\n")
      append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, ")
      append("Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
      append("Style: Main,").append(fontName).append(',').append(size).append(',')
      append(assColor(st.textColor)).append(',').append(assColor(st.textColor)).append(',')
      append(assColor(outlineColor)).append(',').append(assColor(back)).append(',')
      append(if (st.bold) -1 else 0).append(",0,0,0,100,100,0,0,").append(borderStyle).append(',')
      append(fmt(outline)).append(',').append(fmt(shadow)).append(",5,20,20,20,1\n\n")
      append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
      val translationOf = translationFor(doc)
      for (c in doc.cues) {
        val translation = translationOf(c)
        val lines = linesWithKinds(c, tracks, doc.styleOf(c).uppercaseLatin, translation)
        if (lines.isEmpty()) continue
        val cs = doc.styleOf(c)
        val tr = doc.transformOf(c)
        val sb = StringBuilder()
        sb.append("{\\pos(").append((tr.x * w).roundToInt()).append(',').append((tr.y * h).roundToInt()).append(')')
        if (tr.rotation != 0f) sb.append("\\frz").append(fmt(-tr.rotation))
        if (tr.scale != 1f) {
          val sc = (tr.scale * 100).roundToInt()
          sb.append("\\fscx").append(sc).append("\\fscy").append(sc)
        }
        if (cs != st) sb.append("\\fs").append((cs.textSize * h).roundToInt())
        sb.append('}')
        var first = true
        for ((li, kl) in lines.withIndex()) {
          val (kind, line) = kl
          if (li > 0) sb.append("\\N")
          val color = when (kind) {
            TrackKind.PHONETIC -> cs.phoneticColor
            TrackKind.TRANSLATION -> cs.translationColor
            else -> cs.textColor
          }
          val lineSize = if (first) (cs.textSize * h) else (cs.textSize * cs.secondaryScale * h)
          sb.append("{\\fs").append(lineSize.roundToInt())
          val karaoke = first && cs.highlight != HighlightMode.NONE
          if (karaoke) {
            // \k: each word starts in SecondaryColour and turns PrimaryColour when spoken.
            sb.append("\\1c").append(assColor(cs.highlightColor, bgr = true)).append("\\2c").append(assColor(color, bgr = true)).append('}')
            val timed: List<Triple<String, Long, Long>> = when (kind) {
              TrackKind.NATIVE -> c.words.map { Triple(it.text, it.startMs, it.endMs) }
              TrackKind.PHONETIC -> c.words.map { Triple(it.phonetic, it.startMs, it.endMs) }
              TrackKind.TRANSLATION -> {
                // Translated words are spread over the time the caption is spoken.
                val words = TranslationSplit.words(line)
                WordTiming.spreadWords(words, TranslationSplit.speechStart(c), TranslationSplit.speechEnd(c))
                  .mapIndexed { k, (s, e) -> Triple(words[k], s, e) }
              }
            }
            var t = c.startMs
            for ((wi, word) in timed.withIndex()) {
              val (text, start, end) = word
              val lead = ((start - t).coerceAtLeast(0) / 10).toInt()
              if (lead > 0) sb.append("{\\k").append(lead).append('}')
              val dur = ((end - start).coerceAtLeast(10) / 10).toInt()
              sb.append("{\\k").append(dur).append('}').append(escape(text))
              if (wi < timed.lastIndex) sb.append(' ')
              t = end
            }
          } else {
            sb.append("\\1c").append(assColor(color, bgr = true)).append('}').append(escape(line))
          }
          first = false
        }
        append("Dialogue: 0,").append(assTime(c.startMs)).append(',').append(assTime(c.endMs))
        append(",Main,,0,0,0,,").append(sb).append('\n')
      }
    }
  }

  data class ParsedCue(val startMs: Long, val endMs: Long, val lines: List<String>)

  /** Reads SRT or WebVTT text. Formatting tags are removed. */
  fun parse(text: String): List<ParsedCue> {
    val out = ArrayList<ParsedCue>()
    val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
    val arrow = Regex("""(\d{1,2}:)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}:)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
    for (b in blocks) {
      val lines = b.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
      val ti = lines.indexOfFirst { arrow.containsMatchIn(it) }
      if (ti < 0) continue
      val m = arrow.find(lines[ti]) ?: continue
      val g = m.groupValues
      val start = toMs(g[1], g[2], g[3], g[4])
      val end = toMs(g[5], g[6], g[7], g[8])
      val body = lines.drop(ti + 1).map { it.replace(Regex("<[^>]+>"), "").replace(Regex("\\{[^}]*\\}"), "").trim() }
        .filter { it.isNotEmpty() }
      if (body.isNotEmpty() && end > start) out.add(ParsedCue(start, end, body))
    }
    return out.sortedBy { it.startMs }
  }

  private fun toMs(h: String, m: String, s: String, frac: String): Long {
    val hours = h.removeSuffix(":").toLongOrNull() ?: 0L
    val f = frac.padEnd(3, '0').take(3).toLong()
    return ((hours * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + f
  }

  fun srtTime(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, t % 1000)
  }

  fun vttTime(ms: Long): String = srtTime(ms).replace(',', '.')

  private fun assTime(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, (t % 1000) / 10)
  }

  /** ARGB to ASS colour. Style lines use &HAABBGGRR; inline \c tags use &HBBGGRR&. */
  private fun assColor(argb: Int, bgr: Boolean = false): String {
    val a = 255 - ((argb ushr 24) and 0xFF)
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return if (bgr) String.format(Locale.ROOT, "&H%02X%02X%02X&", b, g, r)
    else String.format(Locale.ROOT, "&H%02X%02X%02X%02X", a, b, g, r)
  }

  private fun fmt(f: Float): String = String.format(Locale.ROOT, "%.1f", f)

  private fun escape(s: String): String = s.replace("{", "(").replace("}", ")").replace("\n", " ")
}
