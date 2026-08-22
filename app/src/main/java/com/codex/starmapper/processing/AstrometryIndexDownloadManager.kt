package com.codex.starmapper.processing

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Prozessweiter Download-Manager für die Index-Pakete. Läuft in einem eigenen, App-lebenslangen
 * Scope – ein laufender Download bricht daher NICHT ab, wenn der Index-Dialog geschlossen wird
 * (früher: [androidx.compose.runtime.rememberCoroutineScope] starb mit der Dialog-Composition).
 * Die UI liest nur [progress]/[errors]/[completedVersion] und ruft [start]/[cancel]/[remove] auf.
 */
object AstrometryIndexDownloadManager {
    // pkg.id -> Fortschritt 0..1 (Eintrag vorhanden = läuft gerade).
    val progress = mutableStateMapOf<String, Float>()
    // pkg.id -> letzte Fehlermeldung.
    val errors = mutableStateMapOf<String, String>()
    // Bumpt nach jedem abgeschlossenen Download/Löschen -> UI prüft den Installiert-Status neu.
    val completedVersion = mutableIntStateOf(0)

    private val jobs = mutableMapOf<String, Job>()
    // Main.immediate: State-Updates laufen konsistent auf dem UI-Thread; download() wechselt intern
    // selbst auf Dispatchers.IO.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun isDownloading(pkg: AstrometryIndexPackage): Boolean = progress.containsKey(pkg.id)

    /** Startet den Download (idempotent: läuft bereits einer für das Paket, passiert nichts). */
    fun start(context: Context, pkg: AstrometryIndexPackage) {
        if (jobs.containsKey(pkg.id)) return
        val appContext = context.applicationContext
        val downloader = AstrometryIndexDownloader(appContext)
        errors.remove(pkg.id)
        progress[pkg.id] = 0f
        // Erster aktiver Download insgesamt (nicht nur dieses Paket) -> WakeLock/Foreground-Service
        // starten, damit Android das Netz bei gesperrtem Bildschirm nicht kappt (Geraetebeleg:
        // "Software caused connection abort" bei den grossen 295-950-MB-Paketen + Sperrbildschirm).
        if (jobs.isEmpty()) IndexDownloadForegroundService.start(appContext)
        val job = scope.launch {
            try {
                downloader.download(pkg) { fr -> progress[pkg.id] = fr }
            } catch (c: CancellationException) {
                // Abbruch: bereits geladene Dateien bleiben liegen, später fortsetzbar.
                throw c
            } catch (e: Throwable) {
                errors[pkg.id] = e.message ?: "Download fehlgeschlagen."
            } finally {
                progress.remove(pkg.id)
                jobs.remove(pkg.id)
                completedVersion.intValue++
                // Letzter aktiver Download vorbei -> Service wieder freigeben.
                if (jobs.isEmpty()) IndexDownloadForegroundService.stop(appContext)
            }
        }
        jobs[pkg.id] = job
    }

    fun cancel(pkg: AstrometryIndexPackage) {
        jobs[pkg.id]?.cancel()
    }

    /** Löscht die Dateien des Pakets (nur wenn gerade kein Download läuft). */
    fun remove(context: Context, pkg: AstrometryIndexPackage) {
        if (jobs.containsKey(pkg.id)) return
        val downloader = AstrometryIndexDownloader(context.applicationContext)
        scope.launch {
            downloader.remove(pkg)
            completedVersion.intValue++
        }
    }
}
