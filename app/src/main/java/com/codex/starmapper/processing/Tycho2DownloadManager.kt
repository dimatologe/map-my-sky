package com.codex.starmapper.processing

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Prozessweiter Download+Verarbeitungs-Manager für den optionalen Tycho-2-Tiefkatalog -- läuft in
 * einem eigenen, App-lebenslangen Scope (analog [AstrometryIndexDownloadManager]), damit ein
 * laufender Download/Konvertierung nicht abbricht, wenn die Einstellungen-Ansicht geschlossen wird.
 * EIN zusammenhängender Vorgang mit zwei sichtbaren Phasen: erst [downloadProgress] (0..1 über alle
 * 20 Teile), dann nach vollständigem Download [convertProgress] (0..1 über die 20 Teile beim
 * Einlesen in die SQLite-Datenbank) -- die UI liest beide getrennt für unterschiedliche Beschriftung
 * ("Wird heruntergeladen…" / "Wird verarbeitet…").
 */
object Tycho2DownloadManager {
    val downloadProgress = mutableStateOf<Float?>(null)
    val convertProgress = mutableStateOf<Float?>(null)
    val downloadError = mutableStateOf<String?>(null)
    val convertError = mutableStateOf<String?>(null)
    // Bumpt nach jedem abgeschlossenen Durchlauf/Löschen -> UI prüft den Installiert-Status neu.
    val completedVersion = mutableIntStateOf(0)

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val isBusy: Boolean get() = job != null

    /** Startet Download+Konvertierung (idempotent: läuft bereits einer, passiert nichts). */
    fun start(context: Context) {
        if (job != null) return
        val appContext = context.applicationContext
        downloadError.value = null
        convertError.value = null
        downloadProgress.value = 0f
        // WakeLock/Foreground-Service wie bei den Index-Katalog-Downloads (derselbe Service, generisch
        // gehalten) -- schützt den ~160-MB-Download bei gesperrtem Bildschirm.
        IndexDownloadForegroundService.start(appContext)
        job = scope.launch {
            try {
                Tycho2Downloader(appContext).download { fr -> downloadProgress.value = fr }
                downloadProgress.value = null
                convertProgress.value = 0f
                Tycho2Converter(appContext).convert { fr -> convertProgress.value = fr }
                // Rohe .gz-Teile werden nicht mehr gebraucht, sobald die Datenbank steht.
                Tycho2Downloader(appContext).deleteRaw()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                if (convertProgress.value != null) {
                    convertError.value = e.message ?: "Processing failed."
                } else {
                    downloadError.value = e.message ?: "Download failed."
                }
            } finally {
                downloadProgress.value = null
                convertProgress.value = null
                job = null
                completedVersion.intValue++
                IndexDownloadForegroundService.stop(appContext)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Löscht Datenbank + etwaige rohe Reste (nur wenn gerade nichts läuft). */
    fun remove(context: Context) {
        if (job != null) return
        val appContext = context.applicationContext
        scope.launch {
            Tycho2Store.delete(appContext)
            completedVersion.intValue++
        }
    }
}
