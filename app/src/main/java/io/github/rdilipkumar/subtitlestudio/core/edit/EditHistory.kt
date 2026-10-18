package io.github.rdilipkumar.subtitlestudio.core.edit

import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc

/**
 * The edit timeline behind undo, redo and the History panel. Each entry is a full snapshot
 * (docs are small and immutable, so this is cheap) with a short label of what changed.
 *
 * Rapid repeats of the same edit (dragging a slider, moving a caption with a finger) share a
 * [key] and fold into one entry, so the history reads like a list of real decisions.
 */
class EditHistory(initial: SubtitleDoc, private val limit: Int = 300) {

  data class Entry(val label: String, val doc: SubtitleDoc, val timeMillis: Long, val key: String?)

  private val entries = ArrayList<Entry>().apply { add(Entry("Opened", initial, System.currentTimeMillis(), null)) }

  var index: Int = 0
    private set

  val current: SubtitleDoc get() = entries[index].doc
  val canUndo: Boolean get() = index > 0
  val canRedo: Boolean get() = index < entries.lastIndex
  val all: List<Entry> get() = entries.toList()

  /** Records a new state. Anything that was undone is dropped (like any editor). */
  fun push(label: String, doc: SubtitleDoc, key: String? = null, now: Long = System.currentTimeMillis()) {
    if (doc == current) return
    while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
    val top = entries[index]
    if (key != null && top.key == key && index > 0 && now - top.timeMillis < MERGE_WINDOW_MS) {
      entries[index] = top.copy(doc = doc, timeMillis = now)
      return
    }
    entries.add(Entry(label, doc, now, key))
    index = entries.lastIndex
    if (entries.size > limit) {
      entries.removeAt(1) // keep "Opened" so the user can always go back to the start
      index--
    }
  }

  /** Ends a run of folded edits: the next edit gets its own entry even with the same key. */
  fun seal() {
    entries[index] = entries[index].copy(key = null)
  }

  fun undo(): SubtitleDoc? = if (canUndo) entries[--index].doc else null
  fun redo(): SubtitleDoc? = if (canRedo) entries[++index].doc else null

  /** Goes to any point in the history; later entries stay available for redo. */
  fun jumpTo(i: Int): SubtitleDoc? {
    if (i !in entries.indices) return null
    index = i
    return entries[i].doc
  }

  companion object {
    const val MERGE_WINDOW_MS = 2_000L
  }
}
