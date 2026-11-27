package dev.shugyo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.shugyo.ui.BuildRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps Shugyō working while a study set is being built, even if you switch apps or lock the phone.
 * Android cuts off the internet for apps in the background; a service with a notification keeps it.
 */
class BuildService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null
    private var watching = false
    private var lastUpdate = 0L
    private var lastText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels(this)
        val state = BuildRunner.state.value
        try {
            ServiceCompat.startForeground(
                this, ID_PROGRESS, progress(state.status, state.fraction),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (wake == null) {
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shugyo:build").apply {
                    setReferenceCounted(false)
                    acquire(45 * 60_000L)
                }
        }
        if (!watching) {
            watching = true
            scope.launch {
                BuildRunner.state.collect { s ->
                    if (s.running) update(s.status, s.fraction) else finish(s)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun update(status: String, fraction: Float) {
        val now = SystemClock.elapsedRealtime()
        if (status == lastText && now - lastUpdate < 2000) return
        lastText = status
        lastUpdate = now
        notify(this, ID_PROGRESS, progress(status, fraction))
    }

    private fun finish(s: BuildRunner.State) {
        if (!App.visible) {
            when {
                s.doneDeck.isNotEmpty() ->
                    notify(this, ID_DONE, simple(this, "✨ Your study set is ready", "Tap to start learning."))
                s.error.isNotEmpty() ->
                    notify(this, ID_DONE, simple(this, "Couldn't finish your study set", s.error.lineSequence().first()))
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun progress(status: String, fraction: Float) =
        NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle("Building your study set")
            .setContentText(status.ifBlank { "Getting ready…" })
            .setProgress(100, (fraction * 100).toInt().coerceIn(0, 100), fraction <= 0.01f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(this))
            .build()

    override fun onDestroy() {
        wake?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_PROGRESS = "build"
        private const val CHANNEL_DONE = "done"
        private const val ID_PROGRESS = 1
        private const val ID_DONE = 2

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, BuildService::class.java))
            } catch (e: Exception) {
                // Not allowed right now: the build still runs while the app is open.
            }
        }

        private fun channels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Building study sets", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Study set ready", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun simple(context: Context, title: String, text: String) =
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_launcher_mono)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build()

        private fun notify(context: Context, id: Int, n: android.app.Notification) {
            try {
                NotificationManagerCompat.from(context).notify(id, n)
            } catch (e: SecurityException) {
                // notifications are off for Shugyō
            }
        }
    }
}
