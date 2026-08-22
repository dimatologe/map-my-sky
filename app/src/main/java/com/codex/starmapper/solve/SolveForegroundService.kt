package com.codex.starmapper.solve

import android.app.Notification
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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.codex.starmapper.MainActivity
import com.codex.starmapper.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dünner Foreground-Service (Typ dataSync): hält den Prozess während eines Solves auf
 * Vordergrund-Priorität, damit Android Netz/CPU nicht kappt, und spiegelt den Fortschritt
 * aus [SolveController] in eine Notification mit Abbrechen-Aktion. Die Rechenarbeit selbst
 * läuft in der Compose-Scope, nicht hier.
 */
class SolveForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null
    // Hält die CPU während des Solves wach: ein Foreground-Service allein verhindert NICHT, dass der
    // Prozessor bei ausgeschaltetem/gesperrtem Bildschirm schlafen geht (Doze) -> ohne WakeLock kann
    // ein laufender Solve am Sperrbildschirm stocken. Timeout als Sicherheitsnetz gegen hängende Locks.
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            SolveController.requestCancel()
            stopSelfSafely()
            return START_NOT_STICKY
        }

        ensureChannel()
        acquireWakeLock()
        val status = SolveController.status.value
        startForegroundCompat(buildNotification(status.title, status.text))

        if (collectorJob == null) {
            // Startwert aus dem synchron gelesenen status oben (nicht hart true/false) -- der
            // Collector bekommt beim allerersten Abonnieren sofort den aktuellen Wert erneut
            // zugestellt (StateFlow-Semantik); ohne diesen Startwert wuerde das faelschlich als
            // "gerade beendet" durchgehen, wenn active zufaellig schon wieder false ist.
            var wasActive = status.active
            collectorJob = scope.launch {
                SolveController.status.collectLatest { current ->
                    if (!current.active) {
                        // Nur bei ECHTEM Uebergang aktiv->inaktiv UND echtem Solve-Erfolg: eigene
                        // "fertig"-Notification (Icon + Ton, sperrbildschirmtauglich) -- sonst
                        // verschwindet beim Solve-Ende nur das stumme Lauf-Icon ohne jeden Ersatz
                        // (Nutzerbefund 2026-07-31: kein Logo/keine Meldung mehr bei gesperrtem/
                        // abgedunkeltem Bildschirm, weil der bisherige "fertig"-Hinweis nur ein
                        // roher Ringtone.play()-Aufruf aus einer UI-lifecycle-gebundenen
                        // LaunchedEffect war, keine echte, vom System zuverlaessig zugestellte
                        // Notification).
                        if (wasActive && current.success) {
                            notificationManager().notify(COMPLETION_NOTIFICATION_ID, buildCompletionNotification())
                        }
                        wasActive = false
                        stopSelfSafely()
                        return@collectLatest
                    }
                    wasActive = true
                    notificationManager().notify(
                        NOTIFICATION_ID,
                        buildNotification(current.title, current.text),
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectorJob = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "starmapper:solve").apply {
            setReferenceCounted(false)
            // 30-min-Timeout: ein Solve dauert höchstens Minuten; verhindert ein dauerhaft hängendes Lock.
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun mainActivityContentIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(title: String, text: String): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SolveForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val defaultTitle = getString(R.string.notif_solve_title)
        val defaultText = getString(R.string.notif_solve_text)
        val cancelLabel = getString(R.string.notif_cancel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_constellation)
            .setContentTitle(title.ifBlank { defaultTitle })
            .setContentText(text.ifBlank { defaultText })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(mainActivityContentIntent(0))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, cancelLabel, cancelIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // Eigene, vom stummen Lauf-Kanal unabhaengige Notification bei echtem Solve-Erfolg (s.
    // Aufrufstelle in onStartCommand): eigene ID, eigener Kanal mit Ton + IMPORTANCE_DEFAULT statt
    // des rohen Ringtone.play()-Aufrufs, der bisher an die Compose-UI gebunden war und bei
    // gesperrtem/abgedunkeltem Bildschirm unzuverlaessig sein konnte.
    private fun buildCompletionNotification(): Notification {
        val title = getString(R.string.notif_complete_title)
        val text = getString(R.string.notif_complete_text)
        return NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_constellation)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(mainActivityContentIntent(2))
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_LOW: lautlos, KEIN Heads-up-Pop-up; erscheint in Statusleiste
            // + Benachrichtigungsleiste (runterswipen) und auf dem Sperrbildschirm.
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_running_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_running_desc)
                setShowBadge(false)
                // Inhalt auf dem Sperrbildschirm zeigen (sofern systemseitig erlaubt).
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager().createNotificationChannel(channel)
            // IMPORTANCE_DEFAULT (nicht LOW): dieser Kanal soll -- anders als der stumme Lauf-Kanal
            // oben -- einen Ton abspielen, aber kein aufdringliches Heads-up-Banner (das waere HIGH).
            val completionChannel = NotificationChannel(
                COMPLETION_CHANNEL_ID,
                getString(R.string.notif_complete_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notif_channel_complete_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager().createNotificationChannel(completionChannel)
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        // v2: neue ID, damit die explizite Sperrbildschirm-/LOW-Konfiguration greift
        // (eine bereits angelegte Kanal-Config kann die App nicht mehr ändern).
        private const val CHANNEL_ID = "solve_progress_v2"
        private const val NOTIFICATION_ID = 4711
        // Eigener Kanal + eigene ID fuer die "fertig"-Notification: eigenstaendig von NOTIFICATION_ID,
        // damit ServiceCompat.STOP_FOREGROUND_REMOVE (das nur NOTIFICATION_ID entfernt) sie nicht
        // gleich wieder mit loescht. Nicht private: MainActivity.onStart() raeumt sie beim Oeffnen der
        // App auch dann weg, wenn der Nutzer nicht direkt auf die Notification, sondern auf das
        // App-Icon tippt (setAutoCancel greift nur beim direkten Antippen der Notification selbst).
        private const val COMPLETION_CHANNEL_ID = "solve_complete_v1"
        const val COMPLETION_NOTIFICATION_ID = 4712
        const val ACTION_CANCEL = "com.codex.starmapper.solve.ACTION_CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, SolveForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SolveForegroundService::class.java))
        }
    }
}
