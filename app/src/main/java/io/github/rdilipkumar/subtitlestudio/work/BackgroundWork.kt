package io.github.rdilipkumar.subtitlestudio.work

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.rdilipkumar.subtitlestudio.App
import io.github.rdilipkumar.subtitlestudio.MainActivity
import io.github.rdilipkumar.subtitlestudio.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Long jobs (making subtitles, exporting, translating, downloading packs) must keep going when
 * the user switches to another app. Android pauses or kills a backgrounded app unless it shows a
 * "working" notification through a foreground service, so [WorkService] runs while any job does.
 */
class BackgroundWork(private val app: Application) {

  private val _active = MutableStateFlow<Map<String, String>>(emptyMap())

  /** Running jobs: key to what the notification says. */
  val active: StateFlow<Map<String, String>> = _active

  fun begin(key: String, label: String) {
    _active.update { it + (key to label) }
    try {
      ContextCompat.startForegroundService(app, Intent(app, WorkService::class.java))
    } catch (e: Exception) {
      // Android refuses when the app is already in the background. The job still runs; it just
      // may pause if the user leaves the app.
      Log.w(TAG, "Could not start the background service", e)
    }
  }

  fun end(key: String) = _active.update { it - key }

  companion object {
    private const val TAG = "BackgroundWork"
  }
}

/** Shows the "working" notification while [BackgroundWork] has jobs, then stops itself. */
class WorkService : Service() {

  private val scope = MainScope()
  private var watching = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val work = App.graph.work
    makeChannel()
    // Android requires this right away, even if the job has already finished.
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(work.active.value), serviceType())
    if (!watching) {
      watching = true
      scope.launch {
        work.active.collect { jobs ->
          if (jobs.isEmpty()) {
            ServiceCompat.stopForeground(this@WorkService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
          } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(jobs))
          }
        }
      }
    }
    return START_NOT_STICKY
  }

  /** Android 15+ ends a foreground job after many hours; stop quietly instead of crashing. */
  override fun onTimeout(startId: Int, fgsType: Int) {
    stopSelf()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun serviceType(): Int =
    if (Build.VERSION.SDK_INT >= 35) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }

  private fun makeChannel() {
    val nm = getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(CHANNEL) == null) {
      nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background work", NotificationManager.IMPORTANCE_LOW))
    }
  }

  private fun notification(jobs: Map<String, String>) =
    NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(R.drawable.ic_stat_work)
      .setContentTitle(jobs.values.lastOrNull() ?: "Working…")
      .setContentText(if (jobs.size > 1) "${jobs.size} jobs running on this phone" else "Keeps going if you switch apps")
      .setProgress(0, 0, true)
      .setOngoing(true)
      .setSilent(true)
      .setContentIntent(
        PendingIntent.getActivity(
          this,
          0,
          // Same as tapping the app icon: brings the open app back as it was.
          Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
      )
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()

  companion object {
    private const val CHANNEL = "work"
    private const val NOTIFICATION_ID = 1
  }
}
