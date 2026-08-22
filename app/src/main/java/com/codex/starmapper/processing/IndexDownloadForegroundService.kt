package com.codex.starmapper.processing

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

/**
 * Duenner Foreground-Service (Typ dataSync), gleiches Muster wie [com.codex.starmapper.solve.SolveForegroundService]:
 * haelt den Prozess waehrend eines Index-Katalog-Downloads auf Vordergrund-Prioritaet, damit Android
 * das Netz bei gesperrtem Bildschirm nicht kappt.
 *
 * Geraetebeleg (2026-08-18): [AstrometryIndexDownloadManager] lief bisher in einem reinen
 * Hintergrund-Coroutine-Scope OHNE WakeLock/Foreground-Service -- Sperrt der Nutzer das Handy waehrend
 * eines der grossen Pakete (295-950 MB), kappt Android (Doze) irgendwann die Verbindung
 * ("Software caused connection abort"). Der bereits vorhandene, bewaehrte Solve-Mechanismus deckt nur
 * den eigentlichen Solve ab, nicht diese Downloads.
 *
 * Reine Lebenszeit-Huelle: die eigentliche Download-Logik/der Fortschritt bleiben unveraendert in
 * [AstrometryIndexDownloadManager] (dort auch im Popup sichtbar) -- hier nur Start/Stop, gesteuert von
 * "laeuft mindestens ein Paket-Download".
 */
class IndexDownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        acquireWakeLock()
        startForegroundCompat(buildNotification())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "starmapper:index_download").apply {
            setReferenceCounted(false)
            // Grosse Pakete (bis ~950 MB) auf langsamer Verbindung -- grosszuegiges Timeout als
            // Sicherheitsnetz gegen ein dauerhaft haengendes Lock, kein realistisches Download-Limit.
            acquire(3 * 60 * 60 * 1000L)
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

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_constellation)
            .setContentTitle(getString(R.string.notif_index_download_title))
            .setContentText(getString(R.string.notif_index_download_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_index_download_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_index_download_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "index_download_v1"
        private const val NOTIFICATION_ID = 4720

        fun start(context: Context) {
            val intent = Intent(context, IndexDownloadForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IndexDownloadForegroundService::class.java))
        }
    }
}
