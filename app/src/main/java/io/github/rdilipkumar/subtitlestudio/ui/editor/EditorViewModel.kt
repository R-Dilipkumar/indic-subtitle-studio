package io.github.rdilipkumar.subtitlestudio.ui.editor

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.core.edit.DocOps
import io.github.rdilipkumar.subtitlestudio.core.edit.EditHistory
import io.github.rdilipkumar.subtitlestudio.core.format.SubtitleFormats
import io.github.rdilipkumar.subtitlestudio.core.model.CaptionStyle
import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectFile
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectMeta
import io.github.rdilipkumar.subtitlestudio.core.model.SafeZone
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleMode
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.core.model.TrackSettings
import io.github.rdilipkumar.subtitlestudio.core.model.Transform
import io.github.rdilipkumar.subtitlestudio.core.model.TranslationState
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.export.VideoExporter
import io.github.rdilipkumar.subtitlestudio.pipeline.UuidIds
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tool(val label: String) {
  CAPTIONS("Captions"),
  TEXT("Text"),
  STYLE("Style"),
  MOTION("Motion"),
  TIMING("Timing"),
  POSITION("Position"),
}

/** Whether a look or position change goes to every caption or only the selected one. */
enum class Scope { ALL, THIS }

sealed interface ExportState {
  data object Idle : ExportState
  data class Running(val label: String, val progress: Float) : ExportState
  data class Done(val label: String, val uri: Uri, val mime: String) : ExportState
  data class Failed(val message: String) : ExportState
}

data class EditorUi(
  val loaded: Boolean = false,
  val meta: ProjectMeta? = null,
  val doc: SubtitleDoc = SubtitleDoc(),
  val aiDoc: SubtitleDoc? = null,
  val selectedId: String? = null,
  val tool: Tool? = Tool.CAPTIONS,
  val scope: Scope = Scope.ALL,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
  val history: List<EditHistory.Entry> = emptyList(),
  val historyIndex: Int = 0,
  val comparing: Boolean = false,
  val toast: String? = null,
  val busy: String? = null,
  val export: ExportState = ExportState.Idle,
  val waveform: ByteArray? = null,
  val dictionary: Map<String, String> = emptyMap(),
) {
  val selected: Cue? get() = selectedId?.let { doc.cue(it) }
  val language: Lang get() = meta?.language ?: Lang.TAMIL
}

/**
 * The editor's single source of truth. Every change is a pure [DocOps] step recorded in
 * [EditHistory] (undo, redo, jump back) and saved to the sandbox a moment later.
 */
@OptIn(UnstableApi::class)
class EditorViewModel(app: Application) : AndroidViewModel(app) {
  private val g = App.graph
  private val _ui = MutableStateFlow(EditorUi())
  val ui: StateFlow<EditorUi> = _ui

  private var history: EditHistory? = null
  private var file: ProjectFile? = null
  private var saveJob: Job? = null
  private var projectId: String? = null

  val player: ExoPlayer = ExoPlayer.Builder(app).build()

  fun bind(id: String) {
    if (projectId == id) return
    projectId = id
    viewModelScope.launch {
      val loaded = g.projects.load(id) ?: return@launch
      // Older projects copied a sentence's translation onto each of its captions: share it out.
      val pf = loaded.copy(
        doc = DocOps.shareRepeatedTranslations(loaded.doc),
        aiDoc = loaded.aiDoc?.let { DocOps.shareRepeatedTranslations(it) },
      )
      file = pf
      history = EditHistory(pf.doc)
      val video = g.projects.videoFile(pf.meta)
      player.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
      player.prepare()
      _ui.value = EditorUi(
        loaded = true,
        meta = pf.meta,
        doc = pf.doc,
        aiDoc = pf.aiDoc,
        history = history!!.all,
        tool = if (pf.doc.cues.isEmpty()) Tool.CAPTIONS else Tool.CAPTIONS,
        dictionary = g.settings.value.dictionary,
      )
      launch {
        g.subtitler.ensureWaveform(id, video)
        val wave = withContext(Dispatchers.IO) { g.projects.waveFile(id).takeIf { it.exists() }?.readBytes() }
        _ui.update { it.copy(waveform = wave) }
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // History

  private fun commit(label: String, next: SubtitleDoc, key: String? = null) {
    val h = history ?: return
    if (next == h.current) return
    h.push(label, next, key)
    _ui.update {
      it.copy(
        doc = next,
        canUndo = h.canUndo,
        canRedo = h.canRedo,
        history = h.all,
        historyIndex = h.index,
        selectedId = it.selectedId?.takeIf { id -> next.cue(id) != null },
      )
    }
    scheduleSave()
  }

  private fun restore(doc: SubtitleDoc?) {
    val h = history ?: return
    doc ?: return
    _ui.update {
      it.copy(
        doc = doc,
        canUndo = h.canUndo,
        canRedo = h.canRedo,
        history = h.all,
        historyIndex = h.index,
        selectedId = it.selectedId?.takeIf { id -> doc.cue(id) != null },
      )
    }
    scheduleSave()
  }

  fun undo() = restore(history?.undo())
  fun redo() = restore(history?.redo())
  fun jumpTo(index: Int) = restore(history?.jumpTo(index))

  fun resetToAi() {
    val ai = _ui.value.aiDoc ?: return
    commit("Back to the AI original", ai)
    toast("Back to the AI original. Undo brings your edits back.")
  }

  private fun scheduleSave() {
    saveJob?.cancel()
    saveJob = viewModelScope.launch {
      delay(700)
      saveNow()
    }
  }

  private suspend fun saveNow() {
    val pf = file ?: return
    val doc = _ui.value.doc
    val meta = _ui.value.meta ?: pf.meta
    val next = pf.copy(meta = meta, doc = doc)
    file = next
    g.projects.save(next)
  }

  /** Saves right away (leaving the editor). Runs on the app scope so it finishes even then. */
  fun flush() {
    if (saveJob?.isActive == true) {
      saveJob?.cancel()
      g.scope.launch(NonCancellable) { saveNow() }
    }
  }

  // ---------------------------------------------------------------------------------------
  // Selection and panels

  fun select(id: String?) = _ui.update { it.copy(selectedId = id, scope = if (id == null) Scope.ALL else it.scope) }
  fun openTool(t: Tool?) = _ui.update { it.copy(tool = if (it.tool == t) null else t) }
  fun showTool(t: Tool) = _ui.update { it.copy(tool = t) }
  fun setScope(s: Scope) = _ui.update { it.copy(scope = s) }
  fun setComparing(on: Boolean) = _ui.update { it.copy(comparing = on && it.aiDoc != null) }
  fun toast(msg: String?) = _ui.update { it.copy(toast = msg) }
  fun clearExport() = _ui.update { it.copy(export = ExportState.Idle) }

  fun setSafeZone(z: SafeZone) {
    val m = _ui.value.meta ?: return
    _ui.update { it.copy(meta = m.copy(safeZone = z)) }
    scheduleSave()
  }

  // ---------------------------------------------------------------------------------------
  // Text

  private fun phonetic(): DocOps.Phonetic {
    val lang = _ui.value.language
    val mode = _ui.value.doc.phoneticMode
    val dict = g.settings.value.dictionary
    return DocOps.Phonetic { if (lang.isIndic) Romanizer.romanize(it, mode, dict) else it }
  }

  fun editWord(cueId: String, wordId: String, text: String) =
    commit("Changed a word", DocOps.editWord(_ui.value.doc, cueId, wordId, text, phonetic(), UuidIds))

  fun editCueText(cueId: String, text: String) =
    commit("Changed caption words", DocOps.editCueText(_ui.value.doc, cueId, text, phonetic(), UuidIds), key = "text-$cueId")

  /** The user's spelling for one word; with [remember], used for that word from now on. */
  fun editWordPhonetic(cueId: String, wordId: String, spelling: String, remember: Boolean) {
    val doc = _ui.value.doc
    val word = doc.cue(cueId)?.words?.firstOrNull { it.id == wordId } ?: return
    var next = DocOps.editWordPhonetic(doc, cueId, wordId, spelling)
    if (remember) {
      g.settings.rememberSpelling(word.text, spelling)
      _ui.update { it.copy(dictionary = g.settings.value.dictionary) }
      next = DocOps.regeneratePhonetics(next, phonetic())
      toast("\"${word.text}\" will be spelled \"$spelling\" from now on")
    }
    commit("Changed English letters", next, key = "ph-$wordId")
  }

  fun editTranslation(cueId: String, text: String) =
    commit("Changed translation", DocOps.editTranslation(_ui.value.doc, cueId, text), key = "tr-$cueId")

  fun replaceAll(find: String, replace: String) {
    val (next, n) = DocOps.replaceAll(_ui.value.doc, find, replace, phonetic())
    if (n == 0) toast("\"$find\" not found") else {
      commit("Replaced \"$find\" ($n)", next)
      toast("Replaced $n")
    }
  }

  fun setPhoneticMode(mode: PhoneticMode) {
    val doc = _ui.value.doc.copy(phoneticMode = mode)
    val lang = _ui.value.language
    val dict = g.settings.value.dictionary
    commit(
      if (mode == PhoneticMode.CASUAL) "English letters: casual" else "English letters: exact",
      DocOps.regeneratePhonetics(doc) { if (lang.isIndic) Romanizer.romanize(it, mode, dict) else it },
    )
  }

  // ---------------------------------------------------------------------------------------
  // Structure

  fun splitAfter(cueId: String, wordIndex: Int) = commit("Split a caption", DocOps.splitCue(_ui.value.doc, cueId, wordIndex, UuidIds))

  fun splitAt(timeMs: Long) {
    val cue = _ui.value.selected ?: _ui.value.doc.cueAt(timeMs) ?: return toast("Put the playhead on a caption to split it")
    if (cue.words.size < 2) return toast("A one-word caption cannot be split")
    commit("Split a caption", DocOps.splitCueAt(_ui.value.doc, cue.id, timeMs, UuidIds))
  }

  fun mergeNext(cueId: String) = commit("Joined two captions", DocOps.mergeWithNext(_ui.value.doc, cueId))

  fun mergePrev(cueId: String) {
    val doc = _ui.value.doc
    val i = doc.indexOf(cueId)
    if (i > 0) commit("Joined two captions", DocOps.mergeWithNext(doc, doc.cues[i - 1].id)).also { select(doc.cues[i - 1].id) }
  }

  fun delete(cueId: String) {
    commit("Deleted a caption", DocOps.deleteCue(_ui.value.doc, cueId))
    select(null)
  }

  fun insertAt(timeMs: Long, text: String) {
    val before = _ui.value.doc
    val next = DocOps.insertCue(before, timeMs, text, phonetic(), UuidIds, _ui.value.meta?.durationMs ?: 0)
    if (next == before) return toast("No free space here. Move the playhead to a gap between captions.")
    commit("Added a caption", next)
    val oldIds = before.cues.mapTo(HashSet()) { it.id }
    next.cues.firstOrNull { it.id !in oldIds }?.let { select(it.id) }
  }

  fun regroup(length: CaptionLength) {
    commit("Captions: ${length.label}", DocOps.regroup(_ui.value.doc, length, UuidIds))
    select(null)
  }

  /** How much of the translation shows at once (null: with each caption). */
  fun setTranslationSize(size: CaptionLength?) {
    val doc = _ui.value.doc
    commit("Translation size: ${size?.label ?: "with the captions"}", doc.copy(tracks = doc.tracks.copy(translationSize = size)))
  }

  // ---------------------------------------------------------------------------------------
  // Timing

  private val duration get() = _ui.value.meta?.durationMs ?: 0L

  fun setTiming(cueId: String, start: Long, end: Long, key: String? = null) =
    commit("Changed caption timing", DocOps.setCueTiming(_ui.value.doc, cueId, start, end, duration), key ?: "time-$cueId")

  fun move(cueId: String, deltaMs: Long, key: String? = null) =
    commit("Moved a caption in time", DocOps.moveCue(_ui.value.doc, cueId, deltaMs, duration), key ?: "move-$cueId")

  /** Moves a caption so it starts at [startMs], measured against the latest doc (not the screen's copy). */
  fun moveTo(cueId: String, startMs: Long, key: String? = null) {
    val cue = _ui.value.doc.cue(cueId) ?: return
    move(cueId, startMs - cue.startMs, key)
  }

  /** A finger drag finished: the next drag starts a new undo step. */
  fun endGesture() {
    history?.seal()
  }

  fun shiftAll(deltaMs: Long) = commit("Shifted all captions", DocOps.shiftAll(_ui.value.doc, deltaMs, duration), key = "shift-all")

  fun setWordTiming(cueId: String, wordId: String, start: Long, end: Long) =
    commit("Changed word timing", DocOps.setWordTiming(_ui.value.doc, cueId, wordId, start, end), key = "wt-$wordId")

  // ---------------------------------------------------------------------------------------
  // Look and position

  /** The look being edited: the selected caption's own look when editing "this caption". */
  fun editingStyle(): CaptionStyle {
    val u = _ui.value
    val c = u.selected
    return if (u.scope == Scope.THIS && c != null) u.doc.styleOf(c) else u.doc.style
  }

  fun editingTransform(): Transform {
    val u = _ui.value
    val c = u.selected
    return if (c != null && (u.scope == Scope.THIS || c.transform != null)) u.doc.transformOf(c) else u.doc.transform
  }

  fun applyStyle(style: CaptionStyle, label: String = "Changed the look", key: String? = null) {
    val u = _ui.value
    val target = if (u.scope == Scope.THIS) u.selectedId else null
    commit(if (target != null) "$label (one caption)" else label, DocOps.applyStyle(u.doc, style, target), key?.let { "$it-${target ?: "all"}" })
  }

  /** Applies a ready-made look, keeping the user's text size and where they put the captions. */
  fun applyPreset(id: String) {
    val p = StylePresets.byId(id)
    val u = _ui.value
    val target = if (u.scope == Scope.THIS) u.selectedId else null
    commit("Look: ${p.label}", DocOps.applyStyle(u.doc, p.style.copy(textSize = editingStyle().textSize), target))
  }

  /** Moves captions: the selected one if it has its own place (or "this caption" is chosen), else all. */
  fun applyTransform(t: Transform, key: String? = "place") {
    val u = _ui.value
    val c = u.selected
    val target = if (c != null && (u.scope == Scope.THIS || c.transform != null)) c.id else null
    commit(if (target != null) "Moved one caption" else "Moved captions", DocOps.applyTransform(u.doc, t, target), key?.let { "$it-${target ?: "all"}" })
  }

  fun resetCueLook(cueId: String) {
    val doc = _ui.value.doc
    commit("Reset a caption's look", doc.copy(cues = doc.cues.map { if (it.id == cueId) it.copy(style = null, transform = null) else it }))
  }

  fun setTracks(t: TrackSettings) = commit("Changed lines on screen", _ui.value.doc.copy(tracks = t))

  /** Phonetic (the sounds in English letters) or translation (the meaning), switchable any time. */
  fun setSubtitleMode(m: SubtitleMode) {
    val doc = _ui.value.doc
    commit(if (m == SubtitleMode.PHONETIC) "Mode: phonetic" else "Mode: translation", doc.copy(tracks = doc.tracks.withMode(m)))
    if (m == SubtitleMode.TRANSLATION && doc.cues.any { it.translationState == TranslationState.NONE || it.translationState == TranslationState.STALE }) {
      if (canTranslate()) translate(null) else toast("Download the translation pack to see the meaning (Language packs)")
    }
  }

  fun moveTrack(kind: TrackKind, up: Boolean) {
    val tracks = _ui.value.doc.tracks
    val order = tracks.order.toMutableList()
    val i = order.indexOf(kind)
    val j = if (up) i - 1 else i + 1
    if (i < 0 || j !in order.indices) return
    order[i] = order[j].also { order[j] = order[i] }
    setTracks(tracks.copy(order = order))
  }

  // ---------------------------------------------------------------------------------------
  // Translation

  fun translationTarget(): Lang = _ui.value.meta?.translateTo ?: if (_ui.value.language == Lang.ENGLISH) Lang.TAMIL else Lang.ENGLISH

  fun setTranslationTarget(l: Lang) {
    val m = _ui.value.meta ?: return
    _ui.update { it.copy(meta = m.copy(translateTo = l)) }
    scheduleSave()
  }

  fun canTranslate(): Boolean = g.translation.isAvailable(_ui.value.language, translationTarget())

  /** Translates the given captions (or, with null, the ones that are missing or out of date). */
  fun translate(cueIds: Set<String>? = null) {
    val u = _ui.value
    val from = u.language
    val to = translationTarget()
    if (!canTranslate()) return toast("Download the translation pack first (Language packs)")
    val cues = u.doc.cues.filter { c ->
      if (cueIds != null) c.id in cueIds else c.translationState == TranslationState.NONE || c.translationState == TranslationState.STALE
    }
    if (cues.isEmpty()) return toast("All translations are up to date")
    _ui.update { it.copy(busy = "Translating…") }
    g.work.begin("translate-${projectId}", "Translating captions")
    viewModelScope.launch {
      try {
        val map = g.translation.translate(cues, from, to, groupSentences = cueIds == null) { d, t ->
          _ui.update { it.copy(busy = "Translating $d of $t…") }
        }
        val next = DocOps.setAutoTranslations(_ui.value.doc, map)
        commit(if (cues.size == 1) "Translated a caption" else "Translated ${cues.size} captions", next.copy(tracks = next.tracks.copy(showTranslation = true)))
      } catch (e: Exception) {
        android.util.Log.e("EditorViewModel", "Translation failed", e)
        toast("Translation failed: ${e.message}")
      } finally {
        _ui.update { it.copy(busy = null) }
        g.work.end("translate-${projectId}")
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // Export

  private var exportJob: Job? = null

  fun exportVideo() {
    val pf = file ?: return
    val meta = _ui.value.meta ?: return
    val doc = _ui.value.doc
    if (doc.cues.isEmpty()) return toast("Add some captions first")
    player.pause()
    g.work.begin("export-${meta.id}", "Making your video")
    exportJob = viewModelScope.launch {
      _ui.update { it.copy(export = ExportState.Running("Making your video", 0f)) }
      val tmp = File(getApplication<Application>().cacheDir, "exports/${meta.id}.mp4")
      try {
        g.exporter.burnIn(g.projects.videoFile(pf.meta), doc, tmp) { p ->
          _ui.update { it.copy(export = ExportState.Running("Making your video", p)) }
        }
        val name = safeName(meta.name) + "_subtitled_" + System.currentTimeMillis() % 100000 + ".mp4"
        val uri = g.exporter.saveToGallery(tmp, name)
        _ui.update { it.copy(export = ExportState.Done("Saved to Movies/Offline Reel Captions", uri, "video/mp4")) }
      } catch (e: kotlinx.coroutines.CancellationException) {
        _ui.update { it.copy(export = ExportState.Idle) }
      } catch (e: Exception) {
        android.util.Log.e("EditorViewModel", "Video export failed", e)
        _ui.update { it.copy(export = ExportState.Failed(e.message ?: "Export failed")) }
      } finally {
        tmp.delete()
        g.work.end("export-${meta.id}")
      }
    }
  }

  fun cancelExport() {
    exportJob?.cancel()
    _ui.update { it.copy(export = ExportState.Idle) }
  }

  fun exportFile(type: VideoExporter.FileType, tracks: List<TrackKind>, nameEnding: String = "") {
    val meta = _ui.value.meta ?: return
    val doc = _ui.value.doc
    if (doc.cues.isEmpty()) return toast("Add some captions first")
    viewModelScope.launch {
      try {
        val script = if (_ui.value.language.isIndic) _ui.value.language.script else io.github.rdilipkumar.subtitlestudio.core.model.Script.LATIN
        val text = g.exporter.subtitleText(type, doc, tracks, meta.width, meta.height, script)
        val uri = g.exporter.saveSubtitleFile(text, safeName(meta.name) + nameEnding + "." + type.ext, type.mime)
        _ui.update { it.copy(export = ExportState.Done("Saved to Downloads/Offline Reel Captions", uri, type.mime)) }
      } catch (e: Exception) {
        _ui.update { it.copy(export = ExportState.Failed(e.message ?: "Could not save the file")) }
      }
    }
  }

  /** One line per caption as a plain .srt, to fix in any app and load back with [loadEdited]. */
  fun exportForEditing(line: TrackKind) = exportFile(VideoExporter.FileType.SRT, listOf(line), nameEnding = "_edit")

  /** Replaces the captions with an .srt or .vtt the user edited elsewhere. One undo brings the old ones back. */
  fun loadEdited(uri: Uri) {
    val app = getApplication<Application>()
    viewModelScope.launch {
      val text = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
      }
      val parsed = text?.let { SubtitleFormats.parse(it.removePrefix("\uFEFF")) }.orEmpty()
      if (parsed.isEmpty()) return@launch toast("No captions found in that file. It should be an .srt or .vtt subtitle file.")
      val lang = _ui.value.language
      // Edited in English letters (Tanglish and the like): keep them exactly as typed.
      val phoneticEdit = lang.isIndic && Romanizer.dominantScript(parsed.joinToString(" ") { it.lines.joinToString(" ") }) == io.github.rdilipkumar.subtitlestudio.core.model.Script.LATIN
      val next = DocOps.loadEdited(_ui.value.doc, parsed, phonetic(), UuidIds, phoneticEdit)
      commit("Loaded your edited file (${next.cues.size} captions)", next)
      select(null)
      toast("Loaded ${next.cues.size} captions from your file. Undo brings the old ones back.")
    }
  }

  /**
   * Brings in captions from an .srt or .vtt file, replacing the ones here. The first line of
   * each is the words; a second line becomes the translation when the translation line is on.
   * One undo brings the old captions back.
   */
  fun importSrt(uri: Uri) {
    val app = getApplication<Application>()
    viewModelScope.launch {
      val text = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
      }
      val parsed = text?.let { SubtitleFormats.parse(it.removePrefix("\uFEFF")) }.orEmpty()
      if (parsed.isEmpty()) return@launch toast("No captions found in that file. It should be an .srt or .vtt subtitle file.")
      val doc = _ui.value.doc
      val next = DocOps.importCaptions(doc, parsed, phonetic(), UuidIds, withTranslation = doc.tracks.showTranslation)
      commit("Imported ${next.cues.size} captions", next)
      select(null)
      toast(if (doc.cues.isEmpty()) "Imported ${next.cues.size} captions." else "Imported ${next.cues.size} captions. Undo brings the old ones back.")
    }
  }

  private fun safeName(s: String) = s.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().ifEmpty { "subtitles" }.take(40)

  override fun onCleared() {
    flush()
    player.release()
    super.onCleared()
  }
}
