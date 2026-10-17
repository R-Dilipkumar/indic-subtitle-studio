package io.github.rdilipkumar.subtitlestudio.render

import android.content.Context
import android.graphics.Typeface
import io.github.rdilipkumar.subtitlestudio.core.model.Script
import java.io.File

/**
 * Caption fonts. A "font" here is a family covering every script: Baloo and Anek have a
 * matching design for Tamil, Telugu, Kannada and Malayalam (plus English letters), so the
 * native line and the English-letters line look like one style.
 *
 * All bundled fonts are from Google Fonts (SIL Open Font License, see assets/fonts/OFL.txt).
 */
class FontRegistry(private val context: Context) {

  data class FontChoice(
    val id: String,
    val label: String,
    /** Font file for each script. Scripts not listed use the phone's own font. */
    val scripts: Map<Script, String> = emptyMap(),
    /** Font file for English letters, or null for the phone's own font. */
    val latin: String? = null,
    val weight: Int = 800,
    val custom: File? = null,
  )

  val builtIn: List<FontChoice> = listOf(
    FontChoice("system", "Clean"),
    FontChoice(
      "baloo", "Baloo",
      scripts = mapOf(
        Script.TAMIL to "fonts/baloo-tamil.ttf",
        Script.TELUGU to "fonts/baloo-telugu.ttf",
        Script.KANNADA to "fonts/baloo-kannada.ttf",
        Script.MALAYALAM to "fonts/baloo-malayalam.ttf",
        Script.DEVANAGARI to "fonts/baloo-devanagari.ttf",
      ),
      latin = "fonts/baloo-devanagari.ttf",
    ),
    FontChoice(
      "anek", "Anek",
      scripts = mapOf(
        Script.TAMIL to "fonts/anek-tamil.ttf",
        Script.TELUGU to "fonts/anek-telugu.ttf",
        Script.KANNADA to "fonts/anek-kannada.ttf",
        Script.MALAYALAM to "fonts/anek-malayalam.ttf",
      ),
      latin = "fonts/anek-tamil.ttf",
    ),
    FontChoice("poppins", "Poppins", latin = "fonts/poppins-extrabold.ttf"),
    FontChoice("montserrat", "Montserrat", latin = "fonts/montserrat.ttf"),
    FontChoice("bebas", "Bebas", latin = "fonts/bebas-neue.ttf", weight = 400),
  )

  @Volatile
  private var customs: List<FontChoice> = emptyList()

  fun setCustomFonts(list: List<Pair<String, File>>) {
    customs = list.map { (name, file) -> FontChoice("custom:${file.name}", name, custom = file) }
  }

  val all: List<FontChoice> get() = builtIn + customs

  fun choice(id: String): FontChoice = all.firstOrNull { it.id == id } ?: builtIn.first()

  /** Font name written into ASS subtitle files. */
  fun familyName(id: String, script: Script): String = when (choice(id).id) {
    "baloo" -> when (script) {
      Script.TAMIL -> "Baloo Thambi 2"
      Script.TELUGU -> "Baloo Tammudu 2"
      Script.KANNADA -> "Baloo Tamma 2"
      Script.MALAYALAM -> "Baloo Chettan 2"
      else -> "Baloo 2"
    }
    "anek" -> when (script) {
      Script.TELUGU -> "Anek Telugu"
      Script.KANNADA -> "Anek Kannada"
      Script.MALAYALAM -> "Anek Malayalam"
      else -> "Anek Tamil"
    }
    "poppins" -> "Poppins"
    "montserrat" -> "Montserrat"
    "bebas" -> "Bebas Neue"
    else -> when (script) {
      Script.TAMIL -> "Noto Sans Tamil"
      Script.TELUGU -> "Noto Sans Telugu"
      Script.KANNADA -> "Noto Sans Kannada"
      Script.MALAYALAM -> "Noto Sans Malayalam"
      Script.DEVANAGARI -> "Noto Sans Devanagari"
      Script.LATIN -> "Roboto"
    }
  }

  private val cache = HashMap<String, Typeface>()

  /** The typeface for text in [script] (LATIN for the English-letters and translation lines). */
  fun typeface(fontId: String, script: Script, bold: Boolean): Typeface {
    val key = "$fontId|$script|$bold"
    synchronized(cache) { cache[key]?.let { return it } }
    val c = choice(fontId)
    val weight = if (bold) c.weight else 400
    val tf = try {
      when {
        c.custom != null -> Typeface.createFromFile(c.custom)
        else -> {
          val path = if (script == Script.LATIN) c.latin else c.scripts[script]
          if (path != null) {
            Typeface.Builder(context.assets, path).setFontVariationSettings("'wght' $weight").build()
          } else {
            Typeface.create(Typeface.DEFAULT, if (bold) 700 else 400, false)
          }
        }
      }
    } catch (e: Exception) {
      null
    } ?: Typeface.create(Typeface.DEFAULT, if (bold) 700 else 400, false)
    synchronized(cache) { cache[key] = tf }
    return tf
  }
}
