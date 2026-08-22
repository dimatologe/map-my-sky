package com.codex.starmapper.processing

import android.os.Build
import android.util.Log
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
 */
object LocalAstrometryNative {

    private const val TAG = "LocalAstrometry"

    /** Android-Version reicht für die native Lib (API 28+)? */
    val supportedApi: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

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
