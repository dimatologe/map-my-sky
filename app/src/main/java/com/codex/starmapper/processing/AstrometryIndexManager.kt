package com.codex.starmapper.processing

import android.content.Context
import android.util.Log
import com.codex.starmapper.BuildConfig
import java.io.File

/**
 * Bereitet die astrometry.net-Index-Dateien + die backend.cfg auf dem Gerät auf.
 *
 * Die Indizes liegen als Assets (assets/astrometry/index-*.fits) in der App. astrometry.net muss
 * sie aber als ECHTE Dateien lesen (nicht aus dem komprimierten APK) -> beim ersten Bedarf einmal
 * je App-Version nach filesDir kopieren und die backend.cfg mit dem Pfad darauf schreiben.
 */
object AstrometryIndexManager {

    private const val TAG = "LocalAstrometry"
    private const val ASSET_DIR = "astrometry"

    private fun baseDir(context: Context) = File(context.filesDir, "astrometry")

    /** Zielordner aller Index-Dateien (gebündelte UND heruntergeladene). backend.cfg deckt ihn per
     *  add_path + autoindex ab -> hier abgelegte index-*.fits werden automatisch mitgenutzt. */
    fun indexDir(context: Context): File = File(baseDir(context), "index")

    /** Arbeits-/Temp-Ordner für Solve-Ausgaben (.wcs, .axy usw.). */
    fun tempDir(context: Context): File = File(baseDir(context), "tmp").apply { mkdirs() }

    /** Namen der gebündelten Index-Assets (index-*.fits), sortiert. */
    private fun assetIndexNames(context: Context): List<String> = try {
        context.assets.list(ASSET_DIR)
            ?.filter { it.startsWith("index-") && it.endsWith(".fits") }
            ?.sorted()
            ?: emptyList()
    } catch (t: Throwable) {
        Log.w(TAG, "Assets/$ASSET_DIR nicht lesbar: ${t.message}")
        emptyList()
    }

    /** Sind überhaupt Index-Dateien gebündelt? (für die Verfügbarkeits-Anzeige) */
    fun hasBundledIndexes(context: Context): Boolean = assetIndexNames(context).isNotEmpty()

    /**
     * Kopiert (einmalig je App-Version) die Index-Assets nach filesDir und schreibt backend.cfg.
     * @return backend.cfg-Datei oder null, wenn keine Index-Dateien gebündelt sind / Kopie scheitert.
     */
    fun ensurePrepared(context: Context): File? {
        val names = assetIndexNames(context)
        if (names.isEmpty()) {
            Log.w(TAG, "Keine Index-Dateien in assets/$ASSET_DIR gebündelt.")
            return null
        }
        val indexDir = indexDir(context).apply { mkdirs() }
        val marker = File(baseDir(context), ".ready")
        val want = "v${BuildConfig.VERSION_CODE}\n" + names.joinToString("\n")
        val upToDate = marker.exists() &&
            marker.readText() == want &&
            names.all { File(indexDir, it).exists() }
        if (!upToDate) {
            for (name in names) {
                try {
                    context.assets.open("$ASSET_DIR/$name").use { input ->
                        File(indexDir, name).outputStream().use { input.copyTo(it) }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Index $name kopieren fehlgeschlagen: ${t.message}")
                    return null
                }
            }
            marker.writeText(want)
            Log.i(TAG, "Index-Dateien aufbereitet: ${names.size} Stück in ${indexDir.absolutePath}")
        }
        // backend.cfg: solve-field/astrometry-engine erwartet add_path auf das Index-Verzeichnis.
        // BEWUSST OHNE "inparallel": paralleles Index-Matching (pthreads) in der nativen Engine ist
        // der wahrscheinlichste native Absturz (Crash NACH der Sternextraktion, siehe 0.13.0-Log).
        // Sequentiell = stabil (etwas langsamer, aber es stürzt nicht ab).
        val cfg = File(baseDir(context), "backend.cfg")
        cfg.writeText(
            "cpulimit 300\n" +
                "add_path ${indexDir.absolutePath}\n" +
                "autoindex\n",
        )
        tempDir(context)
        return cfg
    }
}
