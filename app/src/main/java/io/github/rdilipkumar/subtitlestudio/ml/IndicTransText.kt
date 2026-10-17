package io.github.rdilipkumar.subtitlestudio.ml

import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import java.text.Normalizer

/**
 * Text clean-up around the IndicTrans2 translator, following AI4Bharat's IndicProcessor:
 * the model reads and writes every Indian language in Devanagari letters (one shared
 * alphabet), so text is moved into Devanagari before and back into its own script after.
 * Because Indian scripts share one Unicode layout, that move is a fixed letter offset.
 */
object IndicTransText {

  private const val DEVANAGARI = 0x0900
  private val PUNCT = Regex("""([!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~।॥])""")

  /** Prepares a sentence for the translator: "<src> <tgt> tokens…". */
  fun prepare(text: String, from: Lang, to: Lang): String {
    var s = normalizePunct(Normalizer.normalize(text, Normalizer.Form.NFC))
    s = s.replace("​", "").replace("‌", "").replace("‍", "")
    s = if (from == Lang.ENGLISH) tokenizeEnglish(s) else toDevanagari(splitPunct(expandChillus(s)), from.script)
    return "${from.floresCode} ${to.floresCode} ${s.trim()}"
  }

  /** Turns the translator's output back into normal text in [to]'s script. */
  fun finish(decoded: String, to: Lang): String {
    val s = decoded.trim()
    if (to == Lang.ENGLISH) return detokenizeEnglish(s)
    return joinPunct(fromDevanagari(s, to.script))
  }

  private fun normalizePunct(s: String): String = s
    .replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
    .replace('–', '-').replace('—', '-').replace("…", "...")
    .replace('|', '।')
    .replace(Regex("\\s+"), " ")
    .trim()

  private fun splitPunct(s: String): String =
    s.replace(PUNCT, " $1 ").replace(Regex("\\s+"), " ").trim()

  /** Malayalam chillu letters become consonant + virama, as the translator expects. */
  private fun expandChillus(s: String): String = s
    .replace("ൺ", "ണ്").replace("ൻ", "ന്").replace("ർ", "ര്")
    .replace("ൽ", "ല്").replace("ൾ", "ള്").replace("ൿ", "ക്")

  fun toDevanagari(s: String, script: Script): String {
    if (script == Script.DEVANAGARI || script == Script.LATIN) return s
    val base = Romanizer.blockStart(script)
    val sb = StringBuilder(s.length)
    for (ch in s) {
      val off = ch.code - base
      if (off in 0x00..0x6F && ch != '।' && ch != '॥') sb.append((DEVANAGARI + off).toChar()) else sb.append(ch)
    }
    return sb.toString()
  }

  fun fromDevanagari(s: String, script: Script): String {
    if (script == Script.DEVANAGARI || script == Script.LATIN) return s
    val base = Romanizer.blockStart(script)
    val sb = StringBuilder(s.length)
    for (ch in s) {
      var off = ch.code - DEVANAGARI
      if (off in 0x00..0x6F && ch != '।' && ch != '॥') {
        if (script == Script.TAMIL) off = tamilOffset(off)
        sb.append((base + off).toChar())
      } else {
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  /** Tamil has no voiced or aspirated stops: those letters map to the plain one (kha → ka). */
  private fun tamilOffset(o: Int): Int {
    var off = o
    if (off in 0x15..0x28 && off != 0x1C && (off - 0x15) % 5 != 0 && (off - 0x15) % 5 != 4) {
      off = 0x15 + 5 * ((off - 0x15) / 5)
    }
    if (off == 0x2B || off == 0x2C || off == 0x2D) off = 0x2A
    if (off == 0x36) off = 0x37
    return off
  }

  /** Close to the Moses tokenizer the English side was trained with. */
  private fun tokenizeEnglish(s: String): String {
    var t = " $s "
    t = t.replace(Regex("""([^\w\s'.,\-])"""), " $1 ")
    t = t.replace(Regex("""([^\d]),"""), "$1 , ").replace(Regex(""",([^\d])"""), " , $1")
    t = t.replace(Regex("""(\w)'(\w)"""), "$1 '$2")
    t = t.replace(Regex("""\.(\s*)$"""), " .$1")
    t = t.replace(Regex("""\.\s+"""), " . ")
    return t.replace(Regex("\\s+"), " ").trim()
  }

  private fun detokenizeEnglish(s: String): String {
    var t = s
    t = t.replace(Regex("""\s+([.,!?;:%)\]}])"""), "$1")
    t = t.replace(Regex("""([(\[{$])\s+"""), "$1")
    t = t.replace(Regex("""\s+'(s|re|ve|ll|d|m|t)\b"""), "'$1")
    t = t.replace(Regex("""\s+n't\b"""), "n't")
    t = t.replace(Regex("""" ([^"]*?) """"), "\"$1\"")
    return t.replace(Regex("\\s+"), " ").trim()
  }

  private fun joinPunct(s: String): String =
    s.replace(Regex("""\s+([.,!?;:।॥%)\]}])"""), "$1").replace(Regex("""([(\[{])\s+"""), "$1")
      .replace(Regex("\\s+"), " ").trim()
}
