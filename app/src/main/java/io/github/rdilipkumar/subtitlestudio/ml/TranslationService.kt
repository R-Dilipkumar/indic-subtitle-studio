package io.github.rdilipkumar.subtitlestudio.ml

import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.timing.TranslationSplit
import io.github.rdilipkumar.subtitlestudio.data.ModelCatalog
import io.github.rdilipkumar.subtitlestudio.data.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Translates captions offline. Short captions ("2–3 words") make poor sentences on their own,
 * so neighbouring captions that belong to one sentence are translated together, and the
 * sentence's translation is then shared out between them by speaking time
 * ([TranslationSplit]). The translation line can show more or less than one piece at a time
 * (its own size, see TranslationTrack).
 */
class TranslationService(private val models: ModelStore) {

  class MissingPackException(val packTitles: List<String>) :
    IllegalStateException("Download the translation pack first: ${packTitles.joinToString()}")

  fun isAvailable(from: Lang, to: Lang): Boolean =
    ModelCatalog.translationPath(from, to).all { models.isReady(it) }

  suspend fun translate(
    cues: List<Cue>,
    from: Lang,
    to: Lang,
    groupSentences: Boolean = true,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
  ): Map<String, String> = withContext(Dispatchers.Default) {
    val path = ModelCatalog.translationPath(from, to)
    if (path.isEmpty()) return@withContext cues.associate { it.id to it.text }
    val missing = path.filterNot { models.isReady(it) }
    if (missing.isNotEmpty()) throw MissingPackException(missing.map { it.title })

    val groups = if (groupSentences) sentences(cues) else cues.map { listOf(it) }
    var texts = groups.map { g -> g.joinToString(" ") { it.text } }
    var lang = from
    val steps = path.size * texts.size
    var done = 0
    for (pack in path) {
      val target = if (pack == ModelCatalog.toEnglish) Lang.ENGLISH else to
      IndicTranslator(models.dir(pack)).use { tr ->
        texts = texts.map { t ->
          coroutineContext.ensureActive()
          val r = runCatching { tr.translate(t, lang, target) }.getOrDefault("")
          onProgress(++done, steps)
          r
        }
      }
      lang = target
    }
    val out = HashMap<String, String>()
    for ((g, t) in groups.zip(texts)) out.putAll(share(g, t))
    out
  }

  /**
   * Joins captions into sentences: until a full stop, a pause in the speech (measured between
   * the words, not the captions, which linger), or about 16 words.
   */
  private fun sentences(cues: List<Cue>): List<List<Cue>> {
    val out = ArrayList<List<Cue>>()
    var cur = ArrayList<Cue>()
    var words = 0
    for ((i, c) in cues.withIndex()) {
      cur.add(c)
      words += c.words.size
      val next = cues.getOrNull(i + 1)
      val ends = c.text.trimEnd().lastOrNull()?.let { it in ".!?।" } == true
      val lastWordEnd = c.words.lastOrNull()?.endMs ?: c.endMs
      val nextWordStart = next?.words?.firstOrNull()?.startMs ?: next?.startMs
      val pause = nextWordStart == null || nextWordStart - lastWordEnd >= 450
      if (ends || pause || words >= 16) {
        out.add(cur)
        cur = ArrayList()
        words = 0
      }
    }
    if (cur.isNotEmpty()) out.add(cur)
    return out
  }

  /** Each caption of the sentence carries its share of the meaning. */
  private fun share(group: List<Cue>, text: String): Map<String, String> =
    group.map { it.id }.zip(TranslationSplit.share(text, group)).toMap()
}
