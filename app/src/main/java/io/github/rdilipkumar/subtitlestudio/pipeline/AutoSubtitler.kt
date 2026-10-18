package io.github.rdilipkumar.subtitlestudio.pipeline

import android.content.Context
import android.util.Log
import io.github.rdilipkumar.subtitlestudio.core.edit.DocOps
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.ProjectStatus
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleDoc
import io.github.rdilipkumar.subtitlestudio.core.model.TrackSettings
import io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.core.timing.CueBuilder
import io.github.rdilipkumar.subtitlestudio.core.timing.IdGen
import io.github.rdilipkumar.subtitlestudio.core.timing.TimedWord
import io.github.rdilipkumar.subtitlestudio.data.ModelCatalog
import io.github.rdilipkumar.subtitlestudio.data.ModelStore
import io.github.rdilipkumar.subtitlestudio.data.ProjectStore
import io.github.rdilipkumar.subtitlestudio.data.SettingsStore
import io.github.rdilipkumar.subtitlestudio.data.SpeechEngineKind
import io.github.rdilipkumar.subtitlestudio.media.AudioDecoder
import io.github.rdilipkumar.subtitlestudio.ml.SpeechTranscriber
import io.github.rdilipkumar.subtitlestudio.ml.ThedalEngine
import io.github.rdilipkumar.subtitlestudio.ml.TranslationService
import io.github.rdilipkumar.subtitlestudio.ml.WhisperEngine
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import io.github.rdilipkumar.subtitlestudio.work.BackgroundWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** What the user chose on the setup screen. */
data class GenerateOptions(
  val language: Lang,
  val translateTo: Lang?,
  val length: CaptionLength,
  val presetId: String,
  val tracks: TrackSettings,
  val phoneticMode: PhoneticMode,
)

enum class Step(val label: String) {
  AUDIO("Reading the sound"),
  SPEECH("Finding where people speak"),
  WORDS("Writing down the words"),
  PHONETIC("Writing them in English letters"),
  TRANSLATE("Translating"),
  SAVE("Saving"),
}

sealed interface JobState {
  data class Running(
    val step: Step,
    val progress: Float,
    val detail: String = "",
    /** The last few lines heard, shown live while the job runs. */
    val heard: List<String> = emptyList(),
    val etaSeconds: Int? = null,
    val steps: List<Step> = Step.entries,
  ) : JobState
  data object Done : JobState
  data class Failed(val message: String) : JobState
}

val UuidIds = IdGen { UUID.randomUUID().toString().substring(0, 13) }

/**
 * Runs the whole automatic subtitle job for a project, fully on the phone, and keeps going if
 * the user leaves the screen. Results land in the project file (also kept as the "AI
 * original" for compare and reset).
 */
class AutoSubtitler(
  private val context: Context,
  private val scope: CoroutineScope,
  private val projects: ProjectStore,
  private val models: ModelStore,
  private val settings: SettingsStore,
  private val translation: TranslationService,
  private val work: BackgroundWork,
) {
  private val _states = MutableStateFlow<Map<String, JobState>>(emptyMap())
  val states: StateFlow<Map<String, JobState>> = _states
  private val jobs = HashMap<String, Job>()

  fun isRunning(projectId: String): Boolean = jobs[projectId]?.isActive == true

  fun start(projectId: String, options: GenerateOptions) {
    if (isRunning(projectId)) return
    report(projectId, JobState.Running(Step.AUDIO, 0f, steps = stepsFor(options)))
    work.begin("subtitles-$projectId", "Making subtitles")
    jobs[projectId] = scope.launch(Dispatchers.Default) {
      try {
        run(projectId, options)
        report(projectId, JobState.Done)
      } catch (e: kotlinx.coroutines.CancellationException) {
        _states.update { it - projectId }
        throw e
      } catch (e: AudioDecoder.NoAudioException) {
        report(projectId, JobState.Failed("This video has no sound, so there is nothing to subtitle. You can still add captions by hand."))
      } catch (e: TranslationService.MissingPackException) {
        report(projectId, JobState.Failed(e.message ?: "Translation pack missing"))
      } catch (e: Throwable) {
        Log.e(TAG, "Subtitle job failed", e)
        report(projectId, JobState.Failed("Something went wrong: ${e.message ?: e.javaClass.simpleName}"))
      } finally {
        work.end("subtitles-$projectId")
      }
    }
  }

  fun cancel(projectId: String) {
    jobs.remove(projectId)?.cancel()
    _states.update { it - projectId }
  }

  fun clear(projectId: String) = _states.update { it - projectId }

  private companion object {
    const val TAG = "AutoSubtitler"
  }

  private fun stepsFor(o: GenerateOptions): List<Step> =
    Step.entries.filter { (it != Step.TRANSLATE || o.translateTo != null) && (it != Step.PHONETIC || o.language.isIndic) }

  private fun report(id: String, s: JobState) = _states.update { it + (id to s) }

  private suspend fun run(projectId: String, o: GenerateOptions) {
    val steps = stepsFor(o)
    fun running(step: Step, p: Float, detail: String = "", heard: List<String> = emptyList(), eta: Int? = null) =
      report(projectId, JobState.Running(step, p, detail, heard, eta, steps))

    val file = projects.load(projectId) ?: error("Project not found")
    val video = projects.videoFile(file.meta)
    val pack = ModelCatalog.speech[o.language] ?: error("No speech model for ${o.language.label}")
    if (!models.isReady(pack)) error("Download the ${pack.title} pack first")

    // 1. Sound
    val samples = AudioDecoder.decode(video) { running(Step.AUDIO, it) }
    withContext(Dispatchers.IO) { projects.waveFile(projectId).writeBytes(AudioDecoder.peaks(samples)) }

    // 2–3. Speech and words
    val dictionary = settings.value.dictionary
    val phonetic = { s: String -> if (o.language.isIndic) Romanizer.romanize(s, o.phoneticMode, dictionary) else s }
    val words = ArrayList<TimedWord>()
    val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    val engine = when (pack.engine) {
      SpeechEngineKind.WHISPER -> WhisperEngine(models.dir(pack), pack.encoderFile, pack.decoderFile, pack.tokensFile, pack.whisperLanguage, threads)
      SpeechEngineKind.THEDAL -> ThedalEngine(models.dir(pack), threads)
    }
    SpeechTranscriber(context.assets, engine).use { tr ->
      running(Step.SPEECH, 0f)
      val pieces = tr.findPieces(samples)
      running(Step.SPEECH, 1f, "${pieces.size} parts with speech")
      val heard = ArrayList<String>()
      val started = System.currentTimeMillis()
      for ((i, piece) in pieces.withIndex()) {
        val r = tr.transcribe(samples, piece)
        Log.i(TAG, "Part ${i + 1}/${pieces.size} (${pack.engine}): ${r.text.take(80)}")
        words.addAll(r.words)
        if (r.text.isNotBlank()) {
          heard.add(r.text)
          if (heard.size > 4) heard.removeAt(0)
        }
        val elapsed = (System.currentTimeMillis() - started) / 1000f
        val eta = if (i >= 0) ((elapsed / (i + 1)) * (pieces.size - i - 1)).toInt() else null
        running(Step.WORDS, (i + 1f) / pieces.size, "Part ${i + 1} of ${pieces.size}", heard.toList(), eta)
      }
    }

    // 4. Captions with English letters
    if (o.language.isIndic) running(Step.PHONETIC, 0.5f)
    val preset = StylePresets.byId(o.presetId)
    var doc = SubtitleDoc(
      cues = CueBuilder.build(words, o.length, phonetic, UuidIds),
      style = preset.style,
      transform = preset.transform,
      tracks = o.tracks,
      phoneticMode = o.phoneticMode,
    )

    // 5. Translation
    var translateTo = o.translateTo
    if (translateTo != null && doc.cues.isNotEmpty()) {
      if (translation.isAvailable(o.language, translateTo)) {
        val map = translation.translate(doc.cues, o.language, translateTo) { d, t ->
          running(Step.TRANSLATE, d.toFloat() / t, "Sentence $d of $t")
        }
        doc = DocOps.setAutoTranslations(doc, map)
      } else {
        translateTo = null
        var tracks = doc.tracks.copy(showTranslation = false)
        // Translation was the only line: show the words instead of nothing.
        if (tracks.visibleInOrder().isEmpty()) tracks = if (o.language.isIndic) tracks.copy(showPhonetic = true) else tracks.copy(showNative = true)
        doc = doc.copy(tracks = tracks)
      }
    }

    // 6. Save
    running(Step.SAVE, 1f)
    val latest = projects.load(projectId) ?: file
    projects.save(
      latest.copy(
        meta = latest.meta.copy(language = o.language, translateTo = translateTo, status = ProjectStatus.READY),
        doc = doc,
        aiDoc = doc,
      ),
    )
    settings.update {
      it.copy(
        lastLanguage = o.language,
        lastTranslateTo = o.translateTo,
        lastLength = o.length,
        lastPreset = o.presetId,
        showNative = o.tracks.showNative,
        showPhonetic = o.tracks.showPhonetic,
        showTranslation = o.tracks.showTranslation,
        phoneticMode = o.phoneticMode,
        mainLine = o.tracks.visibleInOrder().firstOrNull(),
      )
    }
  }

  /** Makes the timeline sound picture for projects that were started by hand. */
  suspend fun ensureWaveform(projectId: String, video: File) {
    val wave = projects.waveFile(projectId)
    if (wave.exists()) return
    runCatching {
      val samples = AudioDecoder.decode(video)
      withContext(Dispatchers.IO) { wave.writeBytes(AudioDecoder.peaks(samples)) }
    }
  }
}
