package io.github.rdilipkumar.subtitlestudio.data

import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CustomFont(val id: String, val name: String, val file: String)

@Serializable
data class AppSettings(
  /**
   * The user's own English-letters spellings, native word -> spelling ("Teach the app").
   * Used for every new caption from then on.
   */
  val dictionary: Map<String, String> = emptyMap(),
  val lastLanguage: Lang = Lang.TAMIL,
  val lastTranslateTo: Lang? = null,
  val lastLength: CaptionLength = CaptionLength.PUNCHY,
  val lastPreset: String = "pop",
  val showNative: Boolean = true,
  val showPhonetic: Boolean = true,
  val showTranslation: Boolean = false,
  val phoneticMode: PhoneticMode = PhoneticMode.CASUAL,
  val customFonts: List<CustomFont> = emptyList(),
)

class SettingsStore(private val file: File, private val json: Json, private val scope: CoroutineScope) {

  private val _state = MutableStateFlow(load())
  val state: StateFlow<AppSettings> = _state

  val value: AppSettings get() = _state.value

  fun update(change: (AppSettings) -> AppSettings) {
    val next = change(_state.value)
    if (next == _state.value) return
    _state.value = next
    scope.launch(Dispatchers.IO) {
      runCatching {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(AppSettings.serializer(), next))
        if (!tmp.renameTo(file)) {
          file.delete()
          tmp.renameTo(file)
        }
      }
    }
  }

  fun rememberSpelling(nativeWord: String, spelling: String) = update {
    val key = nativeWord.trim().trimEnd('.', ',', '!', '?', '।')
    if (key.isEmpty()) it else it.copy(dictionary = it.dictionary + (key to spelling.trim()))
  }

  fun forgetSpelling(nativeWord: String) = update { it.copy(dictionary = it.dictionary - nativeWord) }

  private fun load(): AppSettings = try {
    if (file.exists()) json.decodeFromString(AppSettings.serializer(), file.readText()) else AppSettings()
  } catch (e: Exception) {
    AppSettings()
  }
}
