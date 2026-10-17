package io.github.rdilipkumar.subtitlestudio.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** A dark, video-editor look: the video stays the brightest thing on screen. */
object Studio {
  val bg = Color(0xFF0E0F13)
  val surface = Color(0xFF16181E)
  val surface2 = Color(0xFF1E2129)
  val surface3 = Color(0xFF272B35)
  val outline = Color(0xFF323744)
  val text = Color(0xFFF2F3F5)
  val muted = Color(0xFF9AA0AA)
  val accent = Color(0xFFFFC83D)
  val onAccent = Color(0xFF1A1300)
  val green = Color(0xFF2BFF88)

  // Edit marks, used on the timeline, the caption list and the legend.
  val markAi = Color(0xFF465063)
  val markEdited = Color(0xFF8B5CF6)
  val markCheck = Color(0xFFFF9F1C)
  val markAdded = Color(0xFF2DD4BF)
  val markStale = Color(0xFFFACC15)
  val danger = Color(0xFFEF4444)
}

private val scheme = darkColorScheme(
  primary = Studio.accent,
  onPrimary = Studio.onAccent,
  primaryContainer = Color(0xFF3A2E07),
  onPrimaryContainer = Studio.accent,
  secondary = Studio.green,
  onSecondary = Color(0xFF00210F),
  background = Studio.bg,
  onBackground = Studio.text,
  surface = Studio.surface,
  onSurface = Studio.text,
  surfaceVariant = Studio.surface2,
  onSurfaceVariant = Studio.muted,
  surfaceContainer = Studio.surface,
  surfaceContainerHigh = Studio.surface2,
  surfaceContainerHighest = Studio.surface3,
  surfaceContainerLow = Studio.surface,
  surfaceContainerLowest = Studio.bg,
  outline = Studio.outline,
  outlineVariant = Studio.outline,
  error = Studio.danger,
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = scheme) {
    // Screens draw on plain dark boxes, not Surfaces, so set the default text/icon colour here.
    CompositionLocalProvider(LocalContentColor provides Studio.text, content = content)
  }
}
