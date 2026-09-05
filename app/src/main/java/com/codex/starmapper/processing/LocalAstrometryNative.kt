package com.codex.starmapper.processing

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import net.astrometry.JNI

/**
 * Lädt die native astrometry.net-Bibliothek (libastrometry.so) EINMALIG und meldet, ob der
 * lokale Solver auf diesem Gerät nutzbar ist.
 *
 * Bewusst tolerant: fehlt die .so (noch nicht eingebaut) oder ist die Android-Version zu alt,
 * bleibt [available] = false und die App fällt sauber auf den Online-Solver zurück — die App
 * kompiliert und läuft also auch OHNE die native Bibliothek.
 *
 * Die native Lib nutzt glob()/globfree(), erst ab Android 9 (API 28) vorhanden -> darunter wird
 * gar nicht erst geladen.
 *
 * WICHTIG (Unit 6, 2026-09-02): [available]/[tryLoad] lädt die .so tatsächlich (`System.
 * loadLibrary`) -- das darf NUR in dem Prozess passieren, der [solveField] auch wirklich aufruft
 * (der isolierte `:solver`-Prozess, s. LocalSolveService.kt). Für eine reine "ist der lokale
 * Solver auf diesem Gerät grundsätzlich nutzbar"-Anzeige/Vorprüfung im HAUPTPROZESS (Solver-Wahl-
 * UI, LocalAstrometrySolver.isAvailable()/solve()s Vorab-Check) [isSupported] verwenden -- prüft
 * nur, ob die .so-Datei existiert, OHNE sie zu laden. Vorher wurde [available] dafür mitgenutzt,
 * wodurch die native Lib bereits beim ersten Anzeigen der Solver-Einstellungen (lange vor jedem
 * tatsächlichen Solve) unnötig in den Hauptprozess geladen wurde, obwohl dort nie [solveField]
 * aufgerufen wird -- ein strukturell unsauberer Zustand, den ein realer nativer Crash-Fund
 * (SIGSEGV des Hauptprozesses kurz nach einem Solve-Abbruch, Untersuchung s. Memory) als einzige
 * konkrete Auffälligkeit im Hauptprozess offenlegte. Kein Kotlin/Java-seitiger Use-after-free
 * fand sich (ausführlich untersucht); dieser Fix entfernt die eine strukturelle Anomalie, die
 * dabei auffiel, unabhängig davon, ob sie der exakte Auslöser war -- der Hauptprozess lädt die
 * native Lib danach gar nicht mehr, ausser er würde sie tatsächlich brauchen (tut er nicht).
 */
object LocalAstrometryNative {

    private const val TAG = "LocalAstrometry"
    private const val LIB_FILE_NAME = "libastrometry.so"

    /** Android-Version reicht für die native Lib (API 28+)? */
    val supportedApi: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * Ist der lokale Solver auf diesem Gerät grundsätzlich nutzbar (API-Version + .so tatsächlich
     * im APK gebündelt)? Prüft NUR, ob die Datei existiert -- lädt NICHTS, kann daher gefahrlos
     * im Hauptprozess für reine UI-/Vorab-Zwecke aufgerufen werden.
     */
    fun isSupported(context: Context): Boolean =
        supportedApi && runCatching {
            val dir = context.applicationInfo.nativeLibraryDir ?: return@runCatching false
            File(dir, LIB_FILE_NAME).exists()
        }.getOrDefault(false)

    /** true, wenn die native Bibliothek geladen werden konnte (einmalig ausgewertet). */
    val available: Boolean by lazy { supportedApi && tryLoad() }

    private fun tryLoad(): Boolean = try {
        System.loadLibrary("astrometry")
        Log.i(TAG, "libastrometry.so geladen — lokaler Solver verfügbar.")
        true
    } catch (t: Throwable) {
        // Erwartbar, solange die .so nicht eingebaut ist: kein Absturz, nur Online statt Lokal.
        Log.w(TAG, "libastrometry.so nicht ladbar (${t.message}) — lokaler Solver deaktiviert.")
        false
    }

    /**
     * Ruft die native solveField-Funktion auf.
     * @return 0 bei Erfolg; die volle WCS-Lösung liegt in der per "--wcs" angegebenen Datei.
     *
     * WICHTIG: zuerst [available] auswerten. Das löst das (lazy) `System.loadLibrary` GENAU IN DEM
     * Prozess aus, der hier die native Methode aufruft. Der Solve läuft in einem eigenen
     * `:solver`-Prozess (LocalSolveService); in diesem frischen Prozess wird [available] sonst
     * NIE angefasst -> die Lib wäre nie geladen -> `JNI.solveField` (static native) würfe
     * UnsatisfiedLinkError. Deshalb hier zentral erzwingen (gilt für jeden Prozess/Aufrufer).
     */
    fun solveField(args: Array<String>, results: DoubleArray): Int {
        check(available) { "libastrometry.so in diesem Prozess nicht geladen (Android < 9 oder .so fehlt)." }
        return JNI.solveField(args, results)
    }
}
