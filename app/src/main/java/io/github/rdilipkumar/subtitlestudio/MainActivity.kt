package io.github.rdilipkumar.subtitlestudio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.rdilipkumar.subtitlestudio.ui.AppRoot
import io.github.rdilipkumar.subtitlestudio.ui.theme.StudioTheme

class MainActivity : ComponentActivity() {

  /** A video shared into the app from the gallery, waiting to become a project. */
  private val sharedVideo = mutableStateOf<Uri?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Always light icons: the app is dark whatever the phone's theme.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    if (savedInstanceState == null) sharedVideo.value = videoFrom(intent)
    setContent {
      StudioTheme {
        AppRoot(sharedVideo = sharedVideo.value, onSharedVideoTaken = { sharedVideo.value = null })
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    videoFrom(intent)?.let { sharedVideo.value = it }
  }

  private fun videoFrom(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
  }
}
