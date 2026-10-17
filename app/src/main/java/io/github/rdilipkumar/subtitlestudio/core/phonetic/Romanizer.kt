package io.github.rdilipkumar.subtitlestudio.core.phonetic

import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import java.text.Normalizer

/**
 * Writes Indian-script text in English letters by how it sounds ("phonetic subtitles").
 *
 * Works fully offline with no model: Indian scripts are syllable-based and their Unicode blocks
 * share one layout (the same offset is the same letter in every script), so one parser reads
 * all of them and small per-language rules decide the sound.
 *
 * CASUAL mode follows how people type in chats, with the sound changes that the letters do not
 * show. In Tamil one letter can sound different by position: க is "k" at the start
 * ("kaadhal") but "g" between vowels ("magan") and after a nasal ("thangam"). Hindi drops the
 * silent "a" ("kamal", not "kamala"). Malayalam adds the short "u" at word ends ("kaattu").
 *
 * EXACT mode gives ISO 15919 letters with accent marks and no sound rules, so every letter can
 * be read back.
 *
 * Latin text, digits and punctuation pass through unchanged, so mixed Tanglish works.
 */
object Romanizer {

  /** Romanize a whole line. [dictionary] maps native words to the user's preferred spelling. */
  fun romanize(
    text: String,
    mode: PhoneticMode = PhoneticMode.CASUAL,
    dictionary: Map<String, String> = emptyMap(),
  ): String {
    val s = Normalizer.normalize(text, Normalizer.Form.NFC)
    val out = StringBuilder(s.length + 8)
    var i = 0
    while (i < s.length) {
      val cp = s.codePointAt(i)
      val script = scriptOf(cp)
      if (script == null || script == Script.LATIN || cp == 0x0964 || cp == 0x0965) {
        out.appendCodePoint(mapLoose(cp))
        i += Character.charCount(cp)
        continue
      }
      val block = blockStart(script)
      val off = cp - block
      if (off in 0x66..0x6F) { // native digits
        out.append('0' + (off - 0x66))
        i += Character.charCount(cp)
        continue
      }
      // Collect one word of this script (letters, signs and joiners).
      var j = i
      while (j < s.length) {
        val c = s.codePointAt(j)
        val joiner = c == ZWJ || c == ZWNJ
        if (!joiner && (scriptOf(c) != script || isDigitOrDanda(c, block))) break
        j += Character.charCount(c)
      }
      if (j == i) { // never stall on a character we cannot read
        out.appendCodePoint(cp)
        i += Character.charCount(cp)
        continue
      }
      val word = s.substring(i, j)
      val fixed = dictionary[word]
      out.append(fixed ?: romanizeRun(word, script, mode))
      i = j
    }
    return out.toString()
  }

  /** Which Indian script a code point belongs to, LATIN for basic letters, or null. */
  fun scriptOf(cp: Int): Script? = when (cp) {
    in 0x0900..0x097F -> Script.DEVANAGARI
    in 0x0B80..0x0BFF -> Script.TAMIL
    in 0x0C00..0x0C7F -> Script.TELUGU
    in 0x0C80..0x0CFF -> Script.KANNADA
    in 0x0D00..0x0D7F -> Script.MALAYALAM
    in 0x41..0x5A, in 0x61..0x7A -> Script.LATIN
    else -> null
  }

  /** The script most of the letters in [text] are written in, or LATIN. */
  fun dominantScript(text: String): Script {
    val counts = IntArray(Script.entries.size)
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      scriptOf(cp)?.let { counts[it.ordinal]++ }
      i += Character.charCount(cp)
    }
    val best = counts.indices.maxByOrNull { counts[it] } ?: return Script.LATIN
    return if (counts[best] == 0) Script.LATIN else Script.entries[best]
  }

  fun blockStart(script: Script): Int = when (script) {
    Script.DEVANAGARI -> 0x0900
    Script.TAMIL -> 0x0B80
    Script.TELUGU -> 0x0C00
    Script.KANNADA -> 0x0C80
    Script.MALAYALAM -> 0x0D00
    Script.LATIN -> 0
  }

  // ---------------------------------------------------------------------------------------
  // Parsing one word into letters

  private const val ZWJ = 0x200D
  private const val ZWNJ = 0x200C
  private const val INHERENT = -1
  private const val DEAD = -2

  private enum class Kind { CONS, VOWEL, ANUSVARA, VISARGA, CANDRA, AYTHAM, CHILLU, OTHER }

  private class Tok(val kind: Kind, val off: Int) {
    /** For CONS: INHERENT, DEAD (virama), or the vowel sign's offset. */
    var vowel: Int = INHERENT
    var nukta = false
    /** Malayalam short "u" written as u-sign + virama. */
    var halfU = false
    /** Virama followed by a zero-width joiner (old-style Malayalam chillu). */
    var joined = false
    /** Replaces the consonant's letters (special clusters). */
    var consOverride: String? = null
    /** Replaces the vowel's letters (special clusters). */
    var vowelOverride: String? = null
  }

  private fun isDigitOrDanda(cp: Int, block: Int): Boolean =
    (cp - block) in 0x66..0x6F || cp == 0x0964 || cp == 0x0965

  private fun mapLoose(cp: Int): Int = when (cp) {
    0x0964, 0x0965 -> '.'.code // danda, used by several scripts
    else -> cp
  }

  private fun parse(word: String, script: Script): List<Tok> {
    val block = blockStart(script)
    val toks = ArrayList<Tok>(word.length)
    var i = 0
    while (i < word.length) {
      val cp = word.codePointAt(i)
      i += Character.charCount(cp)
      if (cp == ZWNJ) continue
      val last = toks.lastOrNull()
      if (cp == ZWJ) {
        if (last != null && last.kind == Kind.CONS && last.vowel == DEAD) last.joined = true
        continue
      }
      val off = cp - block
      when {
        off == 0x3C -> if (last?.kind == Kind.CONS) last.nukta = true
        off == 0x4D -> if (last?.kind == Kind.CONS) {
          if (last.vowel >= 0) last.halfU = true else last.vowel = DEAD
        }
        isVowelSign(off, script) -> {
          if (last?.kind == Kind.CONS && last.vowel != DEAD) {
            last.vowel = combineSign(last.vowel, off)
          } else if (last?.kind == Kind.VOWEL) {
            // Length mark after an independent vowel (rare): keep the vowel as is.
          } else {
            toks.add(Tok(Kind.VOWEL, signToIndependent(off)))
          }
        }
        off == 0x02 -> toks.add(Tok(Kind.ANUSVARA, off))
        off == 0x03 -> toks.add(Tok(if (script == Script.TAMIL) Kind.AYTHAM else Kind.VISARGA, off))
        off == 0x01 -> toks.add(Tok(Kind.CANDRA, off))
        script == Script.MALAYALAM && (off in 0x7A..0x7F || off in 0x54..0x56) ->
          toks.add(Tok(Kind.CHILLU, off))
        script == Script.MALAYALAM && off == 0x4E -> // dot reph: a dead "r"
          toks.add(Tok(Kind.CONS, 0x30).also { it.vowel = DEAD })
        isConsonant(off, script) -> toks.add(Tok(Kind.CONS, off))
        isIndependentVowel(off) -> toks.add(Tok(Kind.VOWEL, off))
        off == 0x50 && script == Script.DEVANAGARI -> toks.add(Tok(Kind.OTHER, off)) // om
        else -> Unit // accents and rare marks are dropped
      }
    }
    return toks
  }

  private fun isConsonant(off: Int, script: Script): Boolean = when {
    off in 0x15..0x39 -> true
    script == Script.MALAYALAM && off == 0x3A -> true
    script == Script.DEVANAGARI && off in 0x58..0x5F -> true
    script == Script.TELUGU && off in 0x58..0x5A -> true
    script == Script.KANNADA && off == 0x5E -> true
    else -> false
  }

  private fun isIndependentVowel(off: Int): Boolean = off in 0x04..0x14 || off == 0x60 || off == 0x61

  private fun isVowelSign(off: Int, script: Script): Boolean = when {
    off in 0x3E..0x4C -> true
    off == 0x57 && (script == Script.TAMIL || script == Script.MALAYALAM) -> true
    off == 0x55 || off == 0x56 -> script == Script.TELUGU || script == Script.KANNADA
    off == 0x62 || off == 0x63 -> true
    off == 0x3A || off == 0x3B -> script == Script.DEVANAGARI // rare Devanagari signs
    else -> false
  }

  /** Two-part vowel signs that NFC may leave apart. */
  private fun combineSign(current: Int, sign: Int): Int = when {
    current == INHERENT -> sign
    current == 0x46 && sign == 0x3E -> 0x4A // e + aa = o
    current == 0x47 && sign == 0x3E -> 0x4B // ee + aa = oo
    current == 0x46 && (sign == 0x57 || sign == 0x4C) -> 0x4C // e + au mark = au
    current == 0x46 && sign == 0x55 -> 0x47 // Kannada e + length = ee
    current == 0x46 && sign == 0x56 -> 0x48 // e + ai mark = ai
    current == 0x3F && sign == 0x55 -> 0x40 // Kannada i + length = ii
    current == 0x46 && sign == 0x42 -> 0x4A // Kannada e + uu = o
    current == 0x4A && sign == 0x55 -> 0x4B // Kannada o + length = oo
    else -> current
  }

  private fun signToIndependent(sign: Int): Int = when (sign) {
    0x3E -> 0x06; 0x3F -> 0x07; 0x40 -> 0x08; 0x41 -> 0x09; 0x42 -> 0x0A
    0x43 -> 0x0B; 0x46 -> 0x0E; 0x47 -> 0x0F; 0x48 -> 0x10; 0x4A -> 0x12
    0x4B -> 0x13; 0x4C, 0x57 -> 0x14
    else -> 0x05
  }

  // ---------------------------------------------------------------------------------------
  // Letter tables

  private val NASALS_TAMIL = setOf(0x19, 0x1E, 0x23, 0x28, 0x29, 0x2E)
  private val LIQUIDS = setOf(0x2F, 0x30, 0x32, 0x33, 0x34, 0x35)
  private val LABIALS = 0x2A..0x2E

  private fun consCasual(off: Int, script: Script, nukta: Boolean): String {
    // A nukta dot marks borrowed sounds (Hindi/Urdu, and English loans in Kannada).
    if (nukta) return when (off) {
      0x15 -> "q"; 0x16 -> "kh"; 0x17 -> "g"; 0x1C -> "z"; 0x21 -> "d"; 0x22 -> "dh"
      0x2B -> "f"; 0x2F -> "y"; 0x28 -> "n"; 0x30 -> "r"; 0x33 -> "l"
      else -> consCasual(off, script, false)
    }
    if (script == Script.DEVANAGARI) {
      when (off) {
        0x24 -> return "t"; 0x1E -> return "ny"
        0x58 -> return "q"; 0x59 -> return "kh"; 0x5A -> return "g"; 0x5B -> return "z"
        0x5C -> return "d"; 0x5D -> return "dh"; 0x5E -> return "f"; 0x5F -> return "y"
      }
    }
    if (script == Script.TELUGU) when (off) {
      0x58 -> return "ch"; 0x59 -> return "j"; 0x5A -> return "r"
    }
    if (script == Script.KANNADA && off == 0x5E) return "zh"
    return when (off) {
      0x15 -> "k"; 0x16 -> "kh"; 0x17 -> "g"; 0x18 -> "gh"; 0x19 -> "ng"
      0x1A -> "ch"; 0x1B -> "chh"; 0x1C -> "j"; 0x1D -> "jh"; 0x1E -> "nj"
      0x1F -> "t"; 0x20 -> "th"; 0x21 -> "d"; 0x22 -> "dh"; 0x23 -> "n"
      0x24 -> "th"; 0x25 -> "th"; 0x26 -> "d"; 0x27 -> "dh"; 0x28 -> "n"; 0x29 -> "n"
      0x2A -> "p"; 0x2B -> "ph"; 0x2C -> "b"; 0x2D -> "bh"; 0x2E -> "m"
      0x2F -> "y"; 0x30 -> "r"; 0x31 -> "r"; 0x32 -> "l"; 0x33 -> "l"; 0x34 -> "zh"
      0x35 -> "v"; 0x36 -> "sh"; 0x37 -> "sh"; 0x38 -> "s"; 0x39 -> "h"; 0x3A -> "t"
      else -> ""
    }
  }

  private fun consExact(off: Int, script: Script, nukta: Boolean): String {
    if (script == Script.DEVANAGARI) {
      if (nukta) return when (off) {
        0x15 -> "q"; 0x16 -> "ḵh"; 0x17 -> "ġ"; 0x1C -> "z"; 0x21 -> "ṛ"; 0x22 -> "ṛh"
        0x2B -> "f"; 0x2F -> "ẏ"; 0x28 -> "ṉ"; 0x30 -> "ṟ"; 0x33 -> "ḻ"
        else -> consExact(off, script, false)
      }
      when (off) {
        0x58 -> return "q"; 0x59 -> return "ḵh"; 0x5A -> return "ġ"; 0x5B -> return "z"
        0x5C -> return "ṛ"; 0x5D -> return "ṛh"; 0x5E -> return "f"; 0x5F -> return "ẏ"
      }
    }
    if (script == Script.TELUGU) when (off) {
      0x58 -> return "ĉ"; 0x59 -> return "ĵ"; 0x5A -> return "ṟ"
    }
    if (script == Script.KANNADA && off == 0x5E) return "ḻ"
    return when (off) {
      0x15 -> "k"; 0x16 -> "kh"; 0x17 -> "g"; 0x18 -> "gh"; 0x19 -> "ṅ"
      0x1A -> "c"; 0x1B -> "ch"; 0x1C -> "j"; 0x1D -> "jh"; 0x1E -> "ñ"
      0x1F -> "ṭ"; 0x20 -> "ṭh"; 0x21 -> "ḍ"; 0x22 -> "ḍh"; 0x23 -> "ṇ"
      0x24 -> "t"; 0x25 -> "th"; 0x26 -> "d"; 0x27 -> "dh"; 0x28 -> "n"; 0x29 -> "ṉ"
      0x2A -> "p"; 0x2B -> "ph"; 0x2C -> "b"; 0x2D -> "bh"; 0x2E -> "m"
      0x2F -> "y"; 0x30 -> "r"; 0x31 -> "ṟ"; 0x32 -> "l"; 0x33 -> "ḷ"; 0x34 -> "ḻ"
      0x35 -> "v"; 0x36 -> "ś"; 0x37 -> "ṣ"; 0x38 -> "s"; 0x39 -> "h"; 0x3A -> "ṯ"
      else -> ""
    }
  }

  /** Vowel letters by independent-vowel offset (signs are mapped to these first). */
  private fun vowelCasual(off: Int, script: Script): String = when (off) {
    0x04 -> "a"; 0x05 -> "a"; 0x06 -> "aa"; 0x07 -> "i"; 0x08 -> "ee"; 0x09 -> "u"; 0x0A -> "oo"
    0x0B -> if (script == Script.DEVANAGARI) "ri" else "ru"
    0x0C -> "lu"; 0x0D -> "e"; 0x0E -> "e"; 0x0F -> "e"; 0x10 -> "ai"
    0x11 -> "o"; 0x12 -> "o"; 0x13 -> "o"; 0x14 -> "au"; 0x60 -> "roo"; 0x61 -> "loo"
    else -> ""
  }

  private fun vowelExact(off: Int, script: Script): String {
    val dravidian = script != Script.DEVANAGARI
    return when (off) {
      0x04 -> "a"; 0x05 -> "a"; 0x06 -> "ā"; 0x07 -> "i"; 0x08 -> "ī"; 0x09 -> "u"; 0x0A -> "ū"
      0x0B -> "r̥"; 0x0C -> "l̥"; 0x0D -> "ê"; 0x0E -> "e"; 0x0F -> if (dravidian) "ē" else "e"
      0x10 -> "ai"; 0x11 -> "ô"; 0x12 -> "o"; 0x13 -> if (dravidian) "ō" else "o"; 0x14 -> "au"
      0x60 -> "r̥̄"; 0x61 -> "l̥̄"
      else -> ""
    }
  }

  private fun signVowelOffset(sign: Int): Int = when (sign) {
    0x3E -> 0x06; 0x3F -> 0x07; 0x40 -> 0x08; 0x41 -> 0x09; 0x42 -> 0x0A
    0x43 -> 0x0B; 0x44 -> 0x60; 0x45 -> 0x0D; 0x46 -> 0x0E; 0x47 -> 0x0F; 0x48 -> 0x10
    0x49 -> 0x11; 0x4A -> 0x12; 0x4B -> 0x13; 0x4C, 0x57 -> 0x14; 0x62 -> 0x0C; 0x63 -> 0x61
    0x55 -> 0x08; 0x56 -> 0x10
    else -> 0x05
  }

  private fun chillu(off: Int, exact: Boolean): String = when (off) {
    0x7A -> if (exact) "ṇ" else "n"
    0x7B -> "n"
    0x7C -> "r"
    0x7D -> "l"
    0x7E -> if (exact) "ḷ" else "l"
    0x7F -> "k"
    0x54 -> "m"
    0x55 -> "y"
    0x56 -> if (exact) "ḻ" else "zh"
    else -> ""
  }

  // ---------------------------------------------------------------------------------------
  // Rendering

  private fun romanizeRun(word: String, script: Script, mode: PhoneticMode): String {
    val toks = parse(word, script)
    if (toks.isEmpty()) return ""
    val exact = mode == PhoneticMode.EXACT
    if (!exact) {
      applySpecialClusters(toks, script)
      if (script == Script.DEVANAGARI) deleteSchwas(toks)
    }
    val out = StringBuilder(word.length * 2)
    for (i in toks.indices) {
      val t = toks[i]
      val prev = toks.getOrNull(i - 1)
      val next = toks.getOrNull(i + 1)
      when (t.kind) {
        Kind.VOWEL -> out.append(if (exact) vowelExact(t.off, script) else vowelCasual(t.off, script))
        Kind.CONS -> {
          val c = t.consOverride ?: when {
            exact -> consExact(t.off, script, t.nukta)
            script == Script.TAMIL -> tamilCons(t, prev, next)
            script == Script.MALAYALAM -> malayalamCons(t, prev, next)
            else -> consCasual(t.off, script, t.nukta)
          }
          out.append(c)
          out.append(vowelAfter(t, next, script, exact))
        }
        Kind.ANUSVARA -> out.append(
          when {
            exact -> "ṁ"
            next?.kind == Kind.CONS && next.off in LABIALS -> "m"
            next?.kind == Kind.CONS -> "n"
            script == Script.DEVANAGARI -> "n"
            else -> "m"
          },
        )
        Kind.VISARGA -> out.append(if (exact) "ḥ" else "h")
        Kind.CANDRA -> out.append(if (exact) "m̐" else "n")
        Kind.AYTHAM -> out.append(
          when {
            exact -> "ḵ"
            next?.kind == Kind.CONS && next.off == 0x2A -> "" // ஃப = f, the ப carries it
            else -> "h"
          },
        )
        Kind.CHILLU -> out.append(chillu(t.off, exact))
        Kind.OTHER -> out.append(if (t.off == 0x50) "om" else "")
      }
    }
    return out.toString()
  }

  private fun vowelAfter(t: Tok, next: Tok?, script: Script, exact: Boolean): String {
    t.vowelOverride?.let { return it }
    return when {
      t.vowel == DEAD -> when {
        // Malayalam: a word-final virama is the short "u" sound.
        script == Script.MALAYALAM && next == null && !t.joined -> if (exact) "ŭ" else "u"
        else -> ""
      }
      t.vowel == INHERENT -> "a"
      else -> {
        val v = signVowelOffset(t.vowel)
        val base = if (exact) vowelExact(v, script) else vowelCasual(v, script)
        if (t.halfU) (if (exact) "ŭ" else "u") else base
      }
    }
  }

  /** Clusters whose sound is not the sum of their letters. */
  private fun applySpecialClusters(toks: List<Tok>, script: Script) {
    for (i in 0 until toks.size - 1) {
      val a = toks[i]
      val b = toks[i + 1]
      if (a.kind != Kind.CONS || b.kind != Kind.CONS || a.vowel != DEAD) continue
      // jña: "gn" in the south ("gnaanam"), "gy" in Hindi ("gyaan")
      if (a.off == 0x1C && b.off == 0x1E) {
        a.consOverride = "g"
        b.consOverride = if (script == Script.DEVANAGARI) "y" else "n"
      }
      // Tamil ஸ்ரீ = "sri"
      if (script == Script.TAMIL && a.off == 0x38 && b.off == 0x30 && b.vowel == 0x40) {
        b.vowelOverride = "i"
      }
    }
    // Tamil ஃப = "f"
    if (script == Script.TAMIL) {
      for (i in 0 until toks.size - 1) {
        if (toks[i].kind == Kind.AYTHAM && toks[i + 1].kind == Kind.CONS && toks[i + 1].off == 0x2A) {
          toks[i + 1].consOverride = "f"
        }
      }
    }
  }

  /**
   * Hindi does not say every written "a": "कमल" is "kamal", "अपना" is "apnaa". The usual rule:
   * drop the last one, then going right to left drop an "a" that sits between two sounded
   * syllables.
   */
  private fun deleteSchwas(toks: List<Tok>) {
    val syll = toks.indices.filter { toks[it].kind == Kind.CONS || toks[it].kind == Kind.VOWEL }
    if (syll.size < 2) return
    fun hasVowel(t: Tok) = t.kind == Kind.VOWEL || (t.kind == Kind.CONS && t.vowel != DEAD)
    val lastIdx = toks.lastIndex
    val last = toks[lastIdx]
    if (last.kind == Kind.CONS && last.vowel == INHERENT) last.vowel = DEAD
    for (k in syll.size - 2 downTo 1) {
      val t = toks[syll[k]]
      if (t.kind != Kind.CONS || t.vowel != INHERENT) continue
      val before = toks[syll[k] - 1]
      val after = toks.getOrNull(syll[k] + 1) ?: continue
      // Only between two sounded syllables. Since we walk right to left, a just-dropped "a"
      // makes the next one keep its "a", so two are never dropped in a row.
      if (!hasVowel(before)) continue
      if (after.kind != Kind.CONS || !hasVowel(after)) continue
      t.vowel = DEAD
    }
  }

  private fun tamilCons(t: Tok, prev: Tok?, next: Tok?): String {
    val off = t.off
    val geminateFirst = t.vowel == DEAD && next?.kind == Kind.CONS && next.off == off
    val geminateSecond = prev?.kind == Kind.CONS && prev.vowel == DEAD && prev.off == off
    val afterNasal = (prev?.kind == Kind.CONS && prev.vowel == DEAD && prev.off in NASALS_TAMIL) ||
      prev?.kind == Kind.ANUSVARA
    val afterLiquid = prev?.kind == Kind.CONS && prev.vowel == DEAD && prev.off in LIQUIDS
    val initial = prev == null
    val betweenVowels = prev != null && (prev.kind == Kind.VOWEL || (prev.kind == Kind.CONS && prev.vowel != DEAD))
    // A stop that closes a syllable (virama, not doubled) stays hard: காட்சி = "kaatchi".
    val closed = t.vowel == DEAD && !geminateFirst
    val soft = !closed && (afterNasal || afterLiquid || betweenVowels)
    return when (off) {
      0x15 -> when { // க
        geminateFirst || geminateSecond -> "k"
        soft -> "g"
        else -> "k"
      }
      0x1A -> when { // ச
        geminateFirst -> ""
        geminateSecond -> "ch"
        closed -> "ch"
        afterNasal -> "j"
        initial || soft -> "s"
        else -> "ch"
      }
      0x1F -> when { // ட
        geminateFirst || geminateSecond -> "t"
        soft -> "d"
        else -> "t"
      }
      0x24 -> when { // த
        geminateFirst -> "t"
        geminateSecond -> "th"
        soft -> "dh"
        else -> "th"
      }
      0x2A -> when { // ப
        geminateFirst || geminateSecond -> "p"
        soft -> "b"
        else -> "p"
      }
      0x31 -> when { // ற
        geminateFirst -> "t"
        geminateSecond -> "r"
        afterNasal -> "dr"
        else -> "r"
      }
      0x19 -> if (t.vowel == DEAD && next?.kind == Kind.CONS && next.off == 0x15) "n" else "ng" // ங
      0x1E -> when { // ஞ
        t.vowel == DEAD && next?.kind == Kind.CONS && next.off == 0x1A -> "n"
        initial -> "gn"
        else -> "nj"
      }
      else -> consCasual(off, Script.TAMIL, t.nukta)
    }
  }

  private fun malayalamCons(t: Tok, prev: Tok?, next: Tok?): String {
    val off = t.off
    val geminateFirst = t.vowel == DEAD && next?.kind == Kind.CONS && next.off == off
    val geminateSecond = prev?.kind == Kind.CONS && prev.vowel == DEAD && prev.off == off
    val afterN = (prev?.kind == Kind.CONS && prev.vowel == DEAD && prev.off == 0x28) ||
      (prev?.kind == Kind.CHILLU && prev.off == 0x7B)
    return when (off) {
      0x31 -> when { // റ: റ്റ = "tt", ന്റ = "nt"
        geminateFirst || geminateSecond || afterN -> "t"
        else -> "r"
      }
      0x1A -> if (geminateFirst) "" else "ch" // ച്ച = "ch"
      0x24 -> if (geminateFirst) "t" else "th" // ത്ത = "tth"
      0x19 -> when { // ങ്ങ = "ng" (ningal), ങ്ക = "nk"
        geminateSecond -> "g"
        t.vowel == DEAD && next?.kind == Kind.CONS && (next.off == 0x19 || next.off == 0x15) -> "n"
        else -> "ng"
      }
      0x1E -> when { // ഞ്ഞ = "nj" (manju)
        geminateSecond -> "j"
        t.vowel == DEAD && next?.kind == Kind.CONS && (next.off == 0x1E || next.off == 0x1A) -> "n"
        else -> "nj"
      }
      else -> consCasual(off, Script.MALAYALAM, t.nukta)
    }
  }
}
