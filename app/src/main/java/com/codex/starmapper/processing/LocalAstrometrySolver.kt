package com.codex.starmapper.processing

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.diagnostics.AppDiagnostics
import com.codex.starmapper.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Fehler beim lokalen (on-device) Lösen mit der nativen astrometry.net-Engine. */
class LocalSolveException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Ergebnis eines lokalen Solves: die WCS-Lösung (Pixel<->Himmel) plus die echten, von astrometry.net
 * selbst verifizierten Sterntreffer dieses Solves aus der `.corr`-Datei (Bildposition -> wahre
 * Himmelsrichtung, in genau dieser Reihenfolge -- passend zu FisheyeRefiner.reprojectionRmsWcs()).
 * [corrRefs] ist leer, wenn keine `.corr`-Datei geschrieben wurde, sie leer/kaputt ist, oder ihr
 * Spaltenschema nicht zu den erwarteten Spalten passt -- NIE ein Grund, [wcs] selbst scheitern zu
 * lassen (s. solve() unten).
 */
data class LocalSolveResult(
    val wcs: WcsSolution,
    val corrRefs: List<Pair<Offset, Vec3>> = emptyList(),
)

/**
 * Lokaler, offline-fähiger Solver: nutzt die native libastrometry.so (astrometry.net) direkt
 * auf dem Gerät – KEIN Server, keine Warteschlange, kein Netz.
 *
 * Ablauf: Kachel-Bitmap -> minimales 8-bit-Graustufen-FITS im Cache -> [JNI.solveField]
 * (solve-field) mit gebündelten Weitfeld-Indizes -> die geschriebene `.wcs`-Datei mit dem
 * vorhandenen [WcsSolutionParser] auslesen -> [WcsSolution].
 *
 * Koordinaten: Das FITS wird in Bild-Zeilenreihenfolge (oberste Zeile zuerst) geschrieben; damit
 * verhält sich die Lösung wie der Nova/JPEG-Weg -> `flipY = false`, native Auflösung (kein
 * Rescale). So passt die WCS 1:1 in dieselbe Pipeline wie Nova.
 */
class LocalAstrometrySolver(private val context: Context) {

    /**
     * Ist der lokale Solver auf diesem Gerät grundsätzlich einsatzbereit? Läuft im Hauptprozess (UI-
     * Anzeige) -- nutzt bewusst [LocalAstrometryNative.isSupported] (reine Existenzprüfung), NICHT
     * [LocalAstrometryNative.available] (würde die native Lib unnötig in den Hauptprozess laden, s.
     * KDoc dort).
     */
    fun isAvailable(): Boolean =
        LocalAstrometryNative.isSupported(context) && AstrometryIndexManager.hasBundledIndexes(context)

    /**
     * Löst [bitmap] lokal. Optionaler Positions-Hinweis (RA/Dec/Radius in Grad) -> `--ra/--dec/--radius`.
     * @return die WCS-Lösung (Pixel<->Himmel) auf den Pixelkoordinaten von [bitmap]) plus die echten
     *   Solve-Treffer aus `.corr` (s. [LocalSolveResult]).
     * @throws LocalSolveException wenn die Engine/Indizes fehlen oder kein Solve gelingt.
     */
    suspend fun solve(
        bitmap: Bitmap,
        centerRaDeg: Double? = null,
        centerDecDeg: Double? = null,
        radiusDeg: Double? = null,
        onStatus: suspend (String) -> Unit,
    ): LocalSolveResult = withContext(Dispatchers.IO) {
        // Reine Vorprüfung (Existenz, kein Laden, s. LocalAstrometryNative-KDoc) -- solve() läuft im
        // Hauptprozess, ruft JNI.solveField() selbst NIE auf (das passiert isoliert in LocalSolveService/
        // dem `:solver`-Prozess, s. LocalSolveService.start() unten); hier reicht die Vorabprüfung.
        if (!LocalAstrometryNative.isSupported(context)) {
            throw LocalSolveException(context.getString(R.string.local_error_lib_unavailable))
        }
        val backendCfg = AstrometryIndexManager.ensurePrepared(context)
            ?: throw LocalSolveException(context.getString(R.string.local_error_no_index_files))
        val tmp = AstrometryIndexManager.tempDir(context)
        // Reste früherer (evtl. abgestürzter) Solves aufräumen -> Temp-Ordner wächst nicht unbegrenzt
        // (dort lagen sonst mehrere 26-MB-FITS + viele tmp.*-Dateien pro Crash).
        runCatching { tmp.listFiles()?.forEach { it.delete() } }

        onStatus(context.getString(R.string.local_status_preparing_fits))
        val fits = File(tmp, "solve_${System.nanoTime()}.fits")
        val wcsOut = File(tmp, "${fits.nameWithoutExtension}.wcs")
        // Enthält astrometry.nets eigene, bereits verifizierte Sterntreffer dieses Solves
        // (s. FitsBinaryTable-Kommentar) -- wird unten zu LocalSolveResult.corrRefs ausgewertet.
        val corrOut = File(tmp, "${fits.nameWithoutExtension}.corr")
        try {
            writeGrayscaleFits(bitmap, fits)
            coroutineContext.ensureActive()

            // Positions-Hinweis NUR mit GUELTIGEN Werten durchreichen (endlich + im Wertebereich),
            // sonst blind. So bleibt die bewährte Methode erhalten, ohne der nativen Engine Müll
            // (NaN / Ausreisser aus einem groben Modell) zu geben, das sie zum Absturz bringen könnte.
            // RA wird auf [0,360) normiert statt bei z.B. -49.6° (== 310.4°) verworfen zu werden:
            // manche Katalog-/Modellwerte liefern RA im Bereich [-180,180) (2026-07-21 per
            // local_solve_hint_rejected-Log an einem "Position bekannt, dann blind"-Fall bewiesen —
            // Dec traf exakt den gesuchten Stern, nur RA war um -360° verschoben). Dec/Radius bleiben
            // hart geprüft (kein Wrap-Konzept, ein Ausreisser dort ist echt ungültig).
            val hint: Triple<Double, Double, Double>? =
                if (centerRaDeg != null && centerDecDeg != null && radiusDeg != null &&
                    centerRaDeg.isFinite() && centerDecDeg.isFinite() && radiusDeg.isFinite() &&
                    centerDecDeg in -90.0..90.0 && radiusDeg > 0.0 && radiusDeg <= 90.0
                ) {
                    val normalizedRa = ((centerRaDeg % 360.0) + 360.0) % 360.0
                    Triple(normalizedRa, centerDecDeg, radiusDeg)
                } else {
                    null
                }
            // Rein diagnostisch (2026-07-21): Nutzer-Beobachtung "Position bekannt, dann aber blind" —
            // der Aufrufer (solveAllTiles) hatte einen nicht-null Hinweis (rawHint!=null geloggt), aber
            // hier kam trotzdem hint=false an. Zeigt die rohen Eingabewerte, um zu klären, welcher der
            // drei Werte die Validierung oben nicht besteht (null erwartet -> "null" statt Zahl).
            if (hint == null && (centerRaDeg != null || centerDecDeg != null || radiusDeg != null)) {
                AppDiagnostics.record(
                    "local_solve_hint_rejected ra=${centerRaDeg ?: "null"} dec=${centerDecDeg ?: "null"} " +
                        "radius=${radiusDeg ?: "null"}",
                )
            }

            // Downsampling nach Bildgröße: astrometry extrahiert sonst aus einem großen Einzelbild
            // (z. B. 6024 px) ZEHNTAUSENDE Sterne und sucht blind über alle Index-Skalen -> Timeout
            // (Geräte-Beleg: 6024x4024 blind = 320 s CODE_TIMEOUT, .axy ~291 kB). `--downsample` ist
            // astrometry.nets eigene Empfehlung dafür. Zielgröße ~2000 px lange Kante -> deutlich
            // weniger Sterne, schnelle Lösung. Kleine Kacheln (<=~2000 px) bleiben bei 1 (unverändert,
            // keine Regression) — die lösen ohnehin schon schnell.
            val longEdge = maxOf(bitmap.width, bitmap.height)
            val downsample = Math.round(longEdge / 2000f).coerceIn(1, 4)

            val args = buildList {
                add("--no-plots")
                add("--overwrite")
                add("--fits-image")
                // Sternerkennung (image2xy) = astrometry.nets EIGENE, funktioniert. ABER der
                // Nachbearbeitungsschritt "removelines" schrieb auf dem Gerät 0-Byte-Dateien und
                // riss danach den Prozess nativ mit. Diesen (und uniformize) überspringen -> die
                // extrahierte Sternliste geht direkt in den Solve. KEINE eigene Sternerkennung.
                add("--no-remove-lines")
                add("--uniformize"); add("0")
                if (downsample > 1) { add("--downsample"); add(downsample.toString()) }
                add("--temp-dir"); add(tmp.absolutePath)
                add("--dir"); add(tmp.absolutePath)
                add("--backend-config"); add(backendCfg.absolutePath)
                add("--wcs"); add(wcsOut.absolutePath)
                add("--new-fits"); add("none")
                add("--corr"); add(corrOut.absolutePath)
                add("--rdls"); add("none")
                add("--index-xyls"); add("none")
                // Positions-Hinweis: nur nahe der aus Nachbarkacheln geschätzten Stelle suchen ->
                // schneller/robuster. (Unschuldig am früheren Crash — der lag am 2. Aufruf im selben
                // Prozess, jetzt via Hilfsprozess gelöst.) Locale.US = Punkt-Dezimal (Gerät ist de-DE).
                if (hint != null && ENABLE_POSITION_HINT) {
                    add("--ra"); add(String.format(Locale.US, "%.6f", hint.first))
                    add("--dec"); add(String.format(Locale.US, "%.6f", hint.second))
                    add("--radius"); add(String.format(Locale.US, "%.4f", hint.third))
                }
                add(fits.absolutePath)
            }.toTypedArray()

            onStatus(
                if (hint != null && ENABLE_POSITION_HINT) {
                    context.getString(R.string.local_status_solving_hinted)
                } else {
                    context.getString(R.string.local_status_solving_blind)
                },
            )
            // VOR dem nativen Aufruf ins App-Log schreiben (appendText ist synchron -> überlebt einen
            // nativen Crash). Zeigt im Diagnose-Export exakt, was übergeben wurde. Fehlt danach
            // "local_solve_done", ist der Prozess im nativen solveField gestorben -> genau lokalisierbar.
            AppDiagnostics.record(
                "local_solve hint=${hint != null && ENABLE_POSITION_HINT} args=${args.joinToString(" ")}",
            )
            Log.i(TAG, "solveField args: ${args.joinToString(" ")}")

            // Nicht direkt aufrufen, sondern im EIGENEN Hilfsprozess (LocalSolveService) — astrometry.net
            // ist nicht wiederverwendbar (2. Aufruf im selben Prozess -> exit(255)). Frischer Prozess je
            // Solve = jeder Aufruf ist ein „erster" (kein exit) + ein etwaiges exit()/Crash killt NUR den
            // Hilfsprozess. Ergebnis dateibasiert (robust gegen Prozess-Tod).
            val done = File(tmp, "${fits.nameWithoutExtension}.done")
            runCatching { done.delete() }
            awaitSolverProcessGone() // vorigen Hilfsprozess sicher tot -> garantiert frischer Start
            LocalSolveService.start(context, args, done.absolutePath)
            val code = awaitSolveResult(done)
            coroutineContext.ensureActive()
            AppDiagnostics.record("local_solve_done code=$code exists=${wcsOut.exists()}")

            if (code != 0 || !wcsOut.exists() || wcsOut.length() <= 0L) {
                val why = if (!wcsOut.exists() || wcsOut.length() <= 0L) {
                    context.getString(R.string.local_error_no_wcs_written, code)
                } else {
                    context.getString(R.string.local_error_code_only, code)
                }
                throw LocalSolveException(context.getString(R.string.local_error_solve_failed, why))
            }
            onStatus(context.getString(R.string.local_status_solved_reading_wcs))
            // Liest die .corr-Datei aus (echte, von astrometry.net selbst verifizierte Sterntreffer
            // dieses Solves) -- komplett in runCatching gekapselt: darf den Solve-ERFOLG (wcs unten)
            // unter KEINEN Umständen zum Scheitern bringen. Jeder Fehlerfall (Datei fehlt/kaputt/
            // Schema passt nicht) -> corrRefs bleibt leer, niemals eine Exception nach außen.
            var corrRefs: List<Pair<Offset, Vec3>> = emptyList()
            runCatching {
                if (corrOut.exists() && corrOut.length() > 0L) {
                    val corrBytes = corrOut.readBytes()
                    val ext = FitsBinaryTable.readFirstExtensionHeader(corrBytes)
                    if (ext != null) {
                        val cols = FitsBinaryTable.columns(ext).joinToString(",") { (name, form) -> "$name:$form" }
                        AppDiagnostics.record(
                            "local_solve_corr_schema rows=${ext.cards["NAXIS2"] ?: "?"} cols=$cols",
                        )
                        val fields = FitsBinaryTable.readColumns(
                            corrBytes, ext, listOf("field_x", "field_y", "index_ra", "index_dec"),
                        )
                        if (fields != null) {
                            val fx = fields.getValue("field_x")
                            val fy = fields.getValue("field_y")
                            val iRa = fields.getValue("index_ra")
                            val iDec = fields.getValue("index_dec")
                            // FITS-Konvention ist 1-basiert (Pixel (1,1) = Mitte des ersten Pixels) --
                            // field_x/field_y stammen aus astrometry.nets EIGENER Sternerkennung auf
                            // DERSELBEN FITS-Datei wie die WCS-Lösung, folgen also derselben
                            // Konvention wie CRPIX1/CRPIX2. Dieselbe -1.0-Korrektur wie in
                            // WcsSolution.skyToImage()/imageToSky() (dort ausführlich begründet) auf
                            // die 0-basierte Offset-Konvention des restlichen Codes (u.a.
                            // StarDetector-Blobs) umrechnen.
                            corrRefs = fx.indices.map { i ->
                                Offset(fx[i].toFloat() - 1f, fy[i].toFloat() - 1f) to raDecToVector(iRa[i], iDec[i])
                            }
                            AppDiagnostics.record("local_solve_corr_rows parsed=${corrRefs.size}")
                        } else {
                            AppDiagnostics.record(
                                "local_solve_corr_rows Schema/Zeilenbreite passt nicht zu den erwarteten Spalten",
                            )
                        }
                    } else {
                        AppDiagnostics.record("local_solve_corr_schema keine Extension gefunden")
                    }
                } else {
                    AppDiagnostics.record("local_solve_corr_schema keine .corr-Datei geschrieben")
                }
            }.onFailure { e ->
                AppDiagnostics.record("local_solve_corr_schema Fehler: ${e.message}")
            }
            val cards = WcsSolutionParser.parseCards(wcsOut.readBytes())
            // Wie Nova/JPEG: Rasterreihenfolge von oben -> flipY = false; native Auflösung, kein Rescale.
            val wcs = WcsSolutionParser.fromCards(cards = cards, requirePlateSolved = false, flipY = false)
            LocalSolveResult(wcs = wcs, corrRefs = corrRefs)
        } catch (c: CancellationException) {
            killSolverProcess() // Abbruch (z. B. Nutzer) -> laufenden Hilfsprozess mitnehmen
            throw c
        } catch (e: LocalSolveException) {
            throw e
        } catch (t: Throwable) {
            throw LocalSolveException(context.getString(R.string.local_error_solve_aborted, t.message.toString()), t)
        } finally {
            runCatching { fits.delete() }
            runCatching { wcsOut.delete() }
            runCatching { corrOut.delete() }
            runCatching { File(tmp, "${fits.nameWithoutExtension}.done").delete() }
        }
    }

    // --- Hilfsprozess-Koordination (dateibasiert, robust gegen exit()/Prozess-Tod) ----------------

    /** Wartet (kurz), bis KEIN :solver-Prozess mehr läuft -> der nächste Solve startet frisch. */
    private suspend fun awaitSolverProcessGone(timeoutMs: Long = 6_000) {
        val start = System.currentTimeMillis()
        while (isSolverProcessRunning() && System.currentTimeMillis() - start < timeoutMs) {
            coroutineContext.ensureActive()
            delay(100)
        }
    }

    /**
     * Wartet auf das Ergebnis des Hilfsprozesses: entweder die `done`-Datei erscheint (solveField kam
     * sauber zurück) ODER der :solver-Prozess verschwindet ohne `done` (astrometry hat exit() gerufen
     * bzw. der Prozess kam nie hoch) -> Solve gescheitert, aber die App lebt.
     * @return solveField-Code (0 = Erfolg), sonst negativ.
     */
    private suspend fun awaitSolveResult(done: File, timeoutMs: Long = 320_000): Int {
        val start = System.currentTimeMillis()
        var everSeen = false
        while (System.currentTimeMillis() - start < timeoutMs) {
            coroutineContext.ensureActive()
            if (done.exists()) {
                val text = runCatching { done.readText() }.getOrDefault("")
                return Regex("code=(-?\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            }
            val running = isSolverProcessRunning()
            if (running) everSeen = true
            if (everSeen && !running) {
                delay(200) // kleine Nachfrist, falls `done` gerade geschrieben wird
                if (done.exists()) continue
                return EXIT_KILLED // Prozess war da, jetzt weg, kein Ergebnis -> exit()/Crash im Solver
            }
            // Hilfsprozess kam nie hoch (startService gescheitert o. ae.) -> nicht ewig warten.
            if (!everSeen && System.currentTimeMillis() - start > 15_000) return EXIT_KILLED
            delay(150)
        }
        return CODE_TIMEOUT
    }

    private fun isSolverProcessRunning(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val target = LocalSolveService.processName(context)
        return runCatching { am.runningAppProcesses }.getOrNull()?.any { it.processName == target } == true
    }

    // Bewusst NICHT mehr per ActivityManager-PID-Suche + Process.killProcess() von AUSSEN (Gerätebeleg
    // 2026-08-18: das führte zuverlässig zu einem nativen Absturz des HAUPTPROZESSES, SIGABRT, jedes
    // Mal beim Abbrechen eines laufenden lokalen Solves) -- stattdessen ein Signal an den :solver-
    // Prozess, sich SELBST zu beenden (s. Kommentar an LocalSolveService.EXTRA_CANCEL). Ein Prozess,
    // der sich selbst tötet, ist im Gegensatz dazu niemals riskant.
    private fun killSolverProcess() {
        LocalSolveService.cancel(context)
    }

    // --- Minimaler FITS-Writer: 8-bit Graustufen, oberste Bildzeile zuerst -----------------------

    private fun writeGrayscaleFits(bitmap: Bitmap, out: File) {
        val w = bitmap.width
        val h = bitmap.height
        require(w > 0 && h > 0) { context.getString(R.string.local_error_empty_image) }
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h) // top-first, row-major
        BufferedOutputStream(FileOutputStream(out)).use { os ->
            // Header (2880-Byte-Blöcke, 80-Zeichen-Karten).
            val header = StringBuilder()
            header.append(card("SIMPLE", "T"))
            header.append(card("BITPIX", "8"))
            header.append(card("NAXIS", "2"))
            header.append(card("NAXIS1", w.toString()))
            header.append(card("NAXIS2", h.toString()))
            header.append("END".padEnd(80))
            val headerLen = header.length
            val headerPadded = headerLen + ((2880 - headerLen % 2880) % 2880)
            os.write(header.toString().toByteArray(Charsets.US_ASCII))
            repeat(headerPadded - headerLen) { os.write(' '.code) }

            // Datenblock: Luma je Pixel, oberste Zeile zuerst.
            val row = ByteArray(w)
            var dataBytes = 0L
            for (y in 0 until h) {
                val base = y * w
                for (x in 0 until w) {
                    val p = pixels[base + x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    row[x] = ((r * 77 + g * 150 + b * 29) shr 8).toByte() // BT.601-Luma
                }
                os.write(row)
                dataBytes += w
            }
            // Daten auf 2880 auffüllen.
            val pad = ((2880 - (dataBytes % 2880)) % 2880).toInt()
            repeat(pad) { os.write(0) }
        }
    }

    /** Eine FITS-Karte: Schlüssel (8) + "= " + Wert rechtsbündig (20), auf 80 aufgefüllt. */
    private fun card(keyword: String, value: String): String {
        val body = keyword.padEnd(8) + "= " + value.padStart(20)
        return (if (body.length > 80) body.substring(0, 80) else body).padEnd(80)
    }

    companion object {
        private const val TAG = "LocalAstrometry"

        // Positions-Hinweis (--ra/--dec/--radius): WIEDER AN. Er war UNSCHULDIG — der früher
        // vermutete Crash lag NICHT am Hinweis, sondern am 2. solveField-Aufruf im selben Prozess
        // (astrometry.net nicht wiederverwendbar -> exit(255), Beleg ApplicationExitInfo EXIT_SELF).
        // Das ist jetzt durch die Prozess-Isolation (LocalSolveService) behoben.
        private const val ENABLE_POSITION_HINT = true

        // Rückgabe-Codes des Hilfsprozess-Wartens (negativ = kein sauberer solveField-Return).
        private const val EXIT_KILLED = -255 // :solver-Prozess weg ohne Ergebnis (astrometry exit()/nie gestartet)
        private const val CODE_TIMEOUT = -998
    }
}
