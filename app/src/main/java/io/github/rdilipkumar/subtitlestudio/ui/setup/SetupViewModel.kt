package io.github.rdilipkumar.subtitlestudio.ui.setup

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.core.format.SubtitleFormats
import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.Origin
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectMeta
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectStatus
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TrackSettings
import io.github.rdilipkumar.subtitlestudio.core.model.TranslationState
import io.github.rdilipkumar.subtitlestudio.core.model.Word
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.core.timing.CueBuilder
import io.github.rdilipkumar.subtitlestudio.core.timing.WordTiming
import io.github.rdilipkumar.subtitlestudio.data.ModelCatalog
import io.github.rdilipkumar.subtitlestudio.data.ModelPack
import io.github.rdilipkumar.subtitlestudio.media.VideoImporter
import io.github.rdilipkumar.subtitlestudio.pipeline.GenerateOptions
import io.github.rdilipkumar.subtitlestudio.pipeline.UuidIds
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupChoices(
  val language: Lang,
  val translateTo: Lang?,
  val length: CaptionLength,
  val presetId: String,
  val showNative: Boolean,
  val showPhonetic: Boolean,
  val showTranslation: Boolean,
  val phoneticMode: PhoneticMode,
)

class SetupViewModel(app: Application) : AndroidViewModel(app) {
  private val g = App.graph
  lateinit var projectId: String
    private set

  private val _meta = MutableStateFlow<ProjectMeta?>(null)
  val meta: StateFlow<ProjectMeta?> = _meta
  private val _frame = MutableStateFlow<Bitmap?>(null)
  val frame: StateFlow<Bitmap?> = _frame
  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message

  val packStates = g.models.states

  private val _choices = MutableStateFlow(
    g.settings.value.let { s ->
      SetupChoices(
        language = s.lastLanguage,
        translateTo = s.lastTranslateTo?.takeIf { it != s.lastLanguage } ?: if (s.lastLanguage == Lang.ENGLISH) Lang.TAMIL else Lang.ENGLISH,
        length = s.lastLength,
        presetId = s.lastPreset,
        showNative = s.showNative,
        showPhonetic = s.showPhonetic,
        showTranslation = s.showTranslation,
        phoneticMode = s.phoneticMode,
      )
    },
  )
  val choices: StateFlow<SetupChoices> = _choices

  fun bind(id: String) {
    if (::projectId.isInitialized && projectId == id) return
    projectId = id
    viewModelScope.launch {
      val pf = g.projects.load(id) ?: return@launch
      _meta.value = pf.meta
      pf.meta.language?.let { l -> _choices.update { it.copy(language = l) } }
      _frame.value = withContext(Dispatchers.IO) {
        VideoImporter.frameAt(g.projects.videoFile(pf.meta), (pf.meta.durationMs / 3).coerceAtMost(4000))
      }
    }
  }

  fun change(f: (SetupChoices) -> SetupChoices) = _choices.update { c ->
    val n = f(c)
    // Translating into the spoken language makes no sense; pick a sensible other one.
    if (n.translateTo == n.language) n.copy(translateTo = if (n.language == Lang.ENGLISH) Lang.TAMIL else Lang.ENGLISH) else n
  }

  fun speechPack(): ModelPack = ModelCatalog.speech.getValue(_choices.value.language)
  fun translationPacks(): List<ModelPack> {
    val c = _choices.value
    return c.translateTo?.let { ModelCatalog.translationPath(c.language, it) } ?: emptyList()
  }

  fun download(pack: ModelPack) = g.models.download(pack)
  fun clearMessage() {
    _message.value = null
  }

  fun options(): GenerateOptions {
    val c = _choices.value
    val translate = c.showTranslation && c.translateTo != null
    return GenerateOptions(
      language = c.language,
      translateTo = if (translate) c.translateTo else null,
      length = c.length,
      presetId = c.presetId,
      tracks = TrackSettings(
        showNative = c.showNative,
        showPhonetic = c.showPhonetic && c.language.isIndic,
        showTranslation = translate,
      ),
      phoneticMode = c.phoneticMode,
    )
  }

  fun start(onStarted: () -> Unit) {
    g.subtitler.start(projectId, options())
    onStarted()
  }

  /** "I'll write the captions myself": opens the editor with the chosen look and no captions. */
  fun startBlank(onDone: () -> Unit) = viewModelScope.launch {
    val pf = g.projects.load(projectId) ?: return@launch
    val o = options()
    val preset = StylePresets.byId(o.presetId)
    val doc = SubtitleDoc(style = preset.style, transform = preset.transform, tracks = o.tracks, phoneticMode = o.phoneticMode)
    g.projects.save(pf.copy(meta = pf.meta.copy(language = o.language, status = ProjectStatus.READY), doc = doc, aiDoc = null))
    onDone()
  }

  /**
   * Brings in an existing SRT/VTT file. The first line of each caption is taken as the words;
   * English letters (and later translation) are added from there.
   */
  fun importSubtitles(uri: Uri, onDone: () -> Unit) = viewModelScope.launch {
    val pf = g.projects.load(projectId) ?: return@launch
    val text = withContext(Dispatchers.IO) {
      getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }
    val parsed = text?.let { SubtitleFormats.parse(it.removePrefix("﻿")) }.orEmpty()
    if (parsed.isEmpty()) {
      _message.value = "No captions found in that file. It should be an .srt or .vtt subtitle file."
      return@launch
    }
    val o = options()
    val dict = g.settings.value.dictionary
    val phon = { s: String -> if (o.language.isIndic) Romanizer.romanize(s, o.phoneticMode, dict) else s }
    val cues = parsed.map { p ->
      val parts = p.lines.first().split(Regex("\\s+")).filter { it.isNotEmpty() }
      val times = WordTiming.spreadWords(parts, p.startMs, p.endMs)
      Cue(
        id = UuidIds.next(),
        startMs = p.startMs,
        endMs = p.endMs,
        words = parts.mapIndexed { i, w -> Word(UuidIds.next(), w, phon(w), times[i].first, times[i].second) },
        translation = p.lines.getOrNull(1)?.takeIf { o.tracks.showTranslation } ?: "",
        translationState = if (p.lines.size > 1 && o.tracks.showTranslation) TranslationState.USER else TranslationState.NONE,
        origin = Origin.IMPORTED,
      )
    }
    val preset = StylePresets.byId(o.presetId)
    val doc = SubtitleDoc(CueBuilder.settleTimes(cues), preset.style, preset.transform, o.tracks, o.phoneticMode)
    g.projects.save(pf.copy(meta = pf.meta.copy(language = o.language, status = ProjectStatus.READY), doc = doc, aiDoc = doc))
    onDone()
  }

  /** A made-up caption in the chosen language, to preview looks. */
  fun sampleDoc(): Pair<SubtitleDoc, Cue> {
    val c = _choices.value
    val preset = StylePresets.byId(c.presetId)
    val (text, translation) = SAMPLES.getValue(c.language)
    val shownTranslation = if (c.translateTo == Lang.ENGLISH || c.language == Lang.ENGLISH) translation else SAMPLES[c.translateTo]?.first ?: translation
    val parts = text.split(' ')
    val cue = Cue(
      id = "sample",
      startMs = 0,
      endMs = 2000,
      words = parts.mapIndexed { i, w ->
        Word("s$i", w, if (c.language.isIndic) Romanizer.romanize(w, c.phoneticMode) else w, i * 500L, i * 500L + 480)
      },
      translation = if (c.language == Lang.ENGLISH) SAMPLES[c.translateTo ?: Lang.TAMIL]!!.first else shownTranslation,
    )
    val doc = SubtitleDoc(
      cues = listOf(cue),
      style = preset.style,
      transform = preset.transform,
      tracks = TrackSettings(c.showNative, c.showPhonetic && c.language.isIndic, c.showTranslation && c.translateTo != null),
      phoneticMode = c.phoneticMode,
    )
    return doc to cue
  }

  companion object {
    val SAMPLES: Map<Lang, Pair<String, String>> = mapOf(
      Lang.TAMIL to ("வணக்கம் நண்பர்களே" to "Hello friends"),
      Lang.TELUGU to ("నమస్కారం స్నేహితులారా" to "Hello friends"),
      Lang.KANNADA to ("ನಮಸ್ಕಾರ ಸ್ನೇಹಿತರೇ" to "Hello friends"),
      Lang.MALAYALAM to ("നമസ്കാരം സുഹൃത്തുക്കളേ" to "Hello friends"),
      Lang.HINDI to ("नमस्ते दोस्तों" to "Hello friends"),
      Lang.ENGLISH to ("Hello friends" to "Hello friends"),
    )
  }
}
