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
 * The display fonts (Poppins and after) have English letters only, so each comes with a
 * look-alike for Indian letters: picking Bangers also changes the Tamil line (to Coiny).
 *
 * All bundled fonts are from Google Fonts (SIL Open Font License or Apache 2.0, see
 * assets/fonts/NOTICE.txt).
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
    /** Weight for the [scripts] files (a display font's partner has its own weight range). */
    val scriptWeight: Int = weight,
    val custom: File? = null,
  )

  private companion object {
    val BALOO = mapOf(
      Script.TAMIL to "fonts/baloo-tamil.ttf",
      Script.TELUGU to "fonts/baloo-telugu.ttf",
      Script.KANNADA to "fonts/baloo-kannada.ttf",
      Script.MALAYALAM to "fonts/baloo-malayalam.ttf",
      Script.DEVANAGARI to "fonts/baloo-devanagari.ttf",
    )
    val ANEK = mapOf(
      Script.TAMIL to "fonts/anek-tamil.ttf",
      Script.TELUGU to "fonts/anek-telugu.ttf",
      Script.KANNADA to "fonts/anek-kannada.ttf",
      Script.MALAYALAM to "fonts/anek-malayalam.ttf",
    )
    const val CATAMARAN = "fonts/catamaran.ttf"
    const val COINY = "fonts/coiny.ttf"
    const val KAVIVANAR = "fonts/kavivanar.ttf"
    const val ARIMA = "fonts/arima.ttf"

    /** Font names written into ASS subtitle files, by font file. */
    val FAMILY = mapOf(
      "fonts/baloo-tamil.ttf" to "Baloo Thambi 2",
      "fonts/baloo-telugu.ttf" to "Baloo Tammudu 2",
      "fonts/baloo-kannada.ttf" to "Baloo Tamma 2",
      "fonts/baloo-malayalam.ttf" to "Baloo Chettan 2",
      "fonts/baloo-devanagari.ttf" to "Baloo 2",
      "fonts/anek-tamil.ttf" to "Anek Tamil",
      "fonts/anek-telugu.ttf" to "Anek Telugu",
      "fonts/anek-kannada.ttf" to "Anek Kannada",
      "fonts/anek-malayalam.ttf" to "Anek Malayalam",
      "fonts/poppins-extrabold.ttf" to "Poppins",
      "fonts/montserrat.ttf" to "Montserrat",
      "fonts/bebas-neue.ttf" to "Bebas Neue",
      "fonts/anton.ttf" to "Anton",
      "fonts/bangers.ttf" to "Bangers",
      "fonts/luckiest-guy.ttf" to "Luckiest Guy",
      "fonts/lilita-one.ttf" to "Lilita One",
      "fonts/archivo-black.ttf" to "Archivo Black",
      "fonts/permanent-marker.ttf" to "Permanent Marker",
      CATAMARAN to "Catamaran",
      COINY to "Coiny",
      KAVIVANAR to "Kavivanar",
      ARIMA to "Arima",
    )

    /** Baloo for every Indian script, with [tamil] for Tamil. */
    fun withTamil(tamil: String, base: Map<Script, String> = BALOO) = base + (Script.TAMIL to tamil)
  }

  val builtIn: List<FontChoice> = listOf(
    FontChoice("system", "Clean"),
    FontChoice("baloo", "Baloo", scripts = BALOO, latin = "fonts/baloo-devanagari.ttf"),
    FontChoice("anek", "Anek", scripts = ANEK, latin = "fonts/anek-tamil.ttf"),
    // Tamil-first fonts, with English letters in the same design.
    FontChoice("catamaran", "Catamaran", scripts = withTamil(CATAMARAN, ANEK), latin = CATAMARAN, weight = 800),
    FontChoice("coiny", "Coiny", scripts = withTamil(COINY), latin = COINY, weight = 400, scriptWeight = 800),
    FontChoice("arima", "Arima", scripts = withTamil(ARIMA) + (Script.MALAYALAM to ARIMA), latin = ARIMA, weight = 700),
    FontChoice("kavivanar", "Kavivanar", scripts = withTamil(KAVIVANAR), latin = KAVIVANAR, weight = 400, scriptWeight = 800),
    // English-only display fonts, each with a matching look for Indian letters.
    FontChoice("poppins", "Poppins", scripts = withTamil(CATAMARAN), latin = "fonts/poppins-extrabold.ttf"),
    FontChoice("montserrat", "Montserrat", scripts = withTamil(CATAMARAN), latin = "fonts/montserrat.ttf"),
    FontChoice("bebas", "Bebas", scripts = withTamil(CATAMARAN), latin = "fonts/bebas-neue.ttf", weight = 400, scriptWeight = 900),
    FontChoice("anton", "Anton", scripts = withTamil(CATAMARAN), latin = "fonts/anton.ttf", weight = 400, scriptWeight = 900),
    FontChoice("bangers", "Bangers", scripts = withTamil(COINY), latin = "fonts/bangers.ttf", weight = 400, scriptWeight = 800),
    FontChoice("luckiest", "Luckiest Guy", scripts = withTamil(COINY), latin = "fonts/luckiest-guy.ttf", weight = 400, scriptWeight = 800),
    FontChoice("lilita", "Lilita One", scripts = BALOO, latin = "fonts/lilita-one.ttf", weight = 400, scriptWeight = 800),
    FontChoice("archivo", "Archivo Black", scripts = withTamil(CATAMARAN), latin = "fonts/archivo-black.ttf", weight = 400, scriptWeight = 900),
    FontChoice("marker", "Marker", scripts = withTamil(KAVIVANAR), latin = "fonts/permanent-marker.ttf", weight = 400, scriptWeight = 800),
  )

  @Volatile
  private var customs: List<FontChoice> = emptyList()

  fun setCustomFonts(list: List<Pair<String, File>>) {
    customs = list.map { (name, file) -> FontChoice("custom:${file.name}", name, custom = file) }
  }

  val all: List<FontChoice> get() = builtIn + customs

  fun choice(id: String): FontChoice = all.firstOrNull { it.id == id } ?: builtIn.first()

  /** Font name written into ASS subtitle files. */
  fun familyName(id: String, script: Script): String {
    val c = choice(id)
    val path = if (c.custom != null) null else if (script == Script.LATIN) c.latin else c.scripts[script]
    return path?.let { FAMILY[it] } ?: when (script) {
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
    val weight = if (!bold) 400 else if (script == Script.LATIN) c.weight else c.scriptWeight
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
