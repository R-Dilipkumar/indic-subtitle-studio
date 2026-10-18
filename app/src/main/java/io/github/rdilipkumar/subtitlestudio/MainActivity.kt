package io.github.rdilipkumar.subtitlestudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.rdilipkumar.subtitlestudio.ui.AppRoot
import io.github.rdilipkumar.subtitlestudio.ui.theme.StudioTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  /** A video shared into the app from the gallery, waiting to become a project. */
  private val sharedVideo = mutableStateOf<Uri?>(null)

  private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
  private var askedNotifications = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Always light icons: the app is dark whatever the phone's theme.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    if (savedInstanceState == null) sharedVideo.value = videoFrom(intent)
    askForNotificationsOnFirstJob()
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

  /**
   * Android 13+ hides the "working" notification of long jobs unless the user allows it, so ask
   * the first time a job starts (when the reason is clear). Jobs run either way.
   */
  private fun askForNotificationsOnFirstJob() {
    if (Build.VERSION.SDK_INT < 33) return
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        App.graph.work.active.collect { jobs ->
          val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
          if (jobs.isNotEmpty() && !granted && !askedNotifications) {
            askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
      }
    }
  }

  private fun videoFrom(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
  }
}
