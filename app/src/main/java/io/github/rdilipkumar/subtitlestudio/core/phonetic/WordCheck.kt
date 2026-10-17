package io.github.rdilipkumar.subtitlestudio.core.phonetic

import io.github.rdilipkumar.subtitlestudio.core.model.Script

/**
 * Spots words the speech model probably got wrong, from spelling rules alone: the model
 * sometimes drops the first letter of a phrase (எங்கிட்ட → ங்கிட்ட), which leaves a word
 * starting in a way no word can. The editor marks these "Please check".
 */
object WordCheck {

  // Tamil letters that never begin a word: ங ண ழ ள ற ன
  private val TAMIL_NEVER_FIRST = setOf(0x0B99, 0x0BA3, 0x0BB4, 0x0BB3, 0x0BB1, 0x0BA9)

  fun looksBroken(word: String): Boolean {
    val w = word.trimStart('"', '\'', '(', '[', '“', '‘')
    if (w.isEmpty()) return false
    val first = w.codePointAt(0)
    val script = Romanizer.scriptOf(first) ?: return false
    if (script == Script.LATIN) return false
    val block = Romanizer.blockStart(script)
    // A word cannot start with a vowel sign, virama or other joining mark.
    val type = Character.getType(first)
    if (type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()) return true
    if (script == Script.TAMIL && first in TAMIL_NEVER_FIRST) return true
    // A vowel sign and a virama on the same letter is not a real spelling.
    var prevOff = -1
    var i = 0
    while (i < w.length) {
      val cp = w.codePointAt(i)
      val off = cp - block
      val isSign = off in 0x3E..0x4C && off != 0x4D
      val isVirama = off == 0x4D
      if (prevOff >= 0) {
        val prevSign = prevOff in 0x3E..0x4C && prevOff != 0x4D
        if ((prevSign && isVirama) || (prevOff == 0x4D && isSign)) return true
      }
      prevOff = if (cp - block in 0..0x7F) off else -1
      i += Character.charCount(cp)
    }
    return false
  }
}
