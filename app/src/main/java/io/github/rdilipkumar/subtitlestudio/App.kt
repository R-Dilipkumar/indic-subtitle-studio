package io.github.rdilipkumar.subtitlestudio

import android.app.Application
import io.github.rdilipkumar.subtitlestudio.data.ModelStore
import io.github.rdilipkumar.subtitlestudio.data.ProjectStore
import io.github.rdilipkumar.subtitlestudio.data.SettingsStore
import io.github.rdilipkumar.subtitlestudio.export.VideoExporter
import io.github.rdilipkumar.subtitlestudio.ml.TranslationService
import io.github.rdilipkumar.subtitlestudio.pipeline.AutoSubtitler
import io.github.rdilipkumar.subtitlestudio.render.FontRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/** Everything the screens share, created once for the app. */
class AppGraph(val app: Application) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  val projects = ProjectStore(File(app.filesDir, "projects"), json)
  val settings = SettingsStore(File(app.filesDir, "settings.json"), json, scope)
  val models = ModelStore(app, scope)
  val fonts = FontRegistry(app)
  val translation = TranslationService(models)
  val subtitler = AutoSubtitler(app, scope, projects, models, settings, translation)
  val exporter = VideoExporter(app, fonts)
  val fontsDir: File = File(app.filesDir, "fonts").apply { mkdirs() }

  init {
    scope.launch { projects.refresh() }
    scope.launch {
      settings.state.map { it.customFonts }.distinctUntilChanged().collect { list ->
        fonts.setCustomFonts(list.map { it.name to File(fontsDir, it.file) })
      }
    }
  }
}

class App : Application() {
  override fun onCreate() {
    super.onCreate()
    graph = AppGraph(this)
  }

  companion object {
    lateinit var graph: AppGraph
      private set
  }
}
