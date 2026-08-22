package com.codex.starmapper.processing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

/**
 * Führt EINEN nativen astrometry.net-`solveField`-Aufruf in einem EIGENEN Prozess aus
 * (`android:process=":solver"`, siehe Manifest) und beendet den Prozess danach.
 *
 * Warum: astrometry.net ist ein Einmal-Programm und NICHT wiederverwendbar — der zweite Aufruf
 * im selben Prozess läuft in einen Fehler und ruft intern `exit(255)` (Geräte-Beleg:
 * ApplicationExitInfo REASON_EXIT_SELF, status 255), was sonst die GANZE App beenden würde.
 * Ein frischer Prozess je Solve gibt jedem Aufruf frischen nativen Zustand (= immer ein „erster"
 * Aufruf) UND isoliert einen etwaigen `exit()`/Crash auf diesen Hilfsprozess — die App bleibt offen.
 *
 * Kommunikation bewusst dateibasiert (robust gegen Prozess-Tod): der Hauptprozess übergibt die
 * fertigen `solveField`-Argumente + einen `done`-Pfad; hier wird gelöst, das Ergebnis in die
 * `done`-Datei geschrieben und der Prozess beendet. Fällt der Prozess vorher per `exit()` weg,
 * fehlt die `done`-Datei -> der Hauptprozess erkennt das am verschwundenen `:solver`-Prozess.
 *
 * Abbrechen (Nutzer drückt "Abbrechen" während ein Solve läuft) läuft bewusst über [EXTRA_CANCEL]
 * statt darüber, dass der Hauptprozess diesen Prozess von AUSSEN per `ActivityManager`-PID-Suche +
 * `Process.killProcess()` tötet (Gerätebeleg 2026-08-18: das führte zuverlässig zu einem nativen
 * Absturz DES HAUPTPROZESSES selbst, SIGABRT, jedes Mal beim Abbrechen eines laufenden lokalen
 * Solves). Ein Prozess, der SICH SELBST beendet (wie im Erfolgsfall unten), ist dagegen niemals
 * riskant. Der native `solveField`-Aufruf blockiert zwar auf seinem eigenen Hintergrund-Thread
 * (`thread(name="local-solve")` unten), der Haupt-Thread DIESES Prozesses bleibt aber die ganze
 * Zeit frei -> ein zweiter `startService()`-Aufruf mit [EXTRA_CANCEL] erreicht ihn sofort, auch
 * während der Solve noch läuft, und er tötet sich dann selbst.
 */
class LocalSolveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_CANCEL, false) == true) {
            // Selbstmord statt von außen getötet zu werden (s. Klassenkommentar) -- killt auch den
            // noch laufenden Solve-Hintergrund-Thread mit, da der ganze Prozess verschwindet.
            Process.killProcess(Process.myPid())
            return START_NOT_STICKY
        }
        val args = intent?.getStringArrayExtra(EXTRA_ARGS)
        val donePath = intent?.getStringExtra(EXTRA_DONE)
        if (args == null || donePath == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        thread(name = "local-solve") {
            var code = -1
            runCatching {
                val results = DoubleArray(2) { Double.NaN }
                code = LocalAstrometryNative.solveField(args, results)
                Log.i(TAG, "solveField returned $code")
            }.onFailure {
                Log.w(TAG, "solveField threw: ${it.message}")
                code = -2
            }
            // Ergebnis hinterlegen. Existiert NUR, wenn solveField sauber zurückkehrte — bei
            // exit(255) ist der Prozess vorher tot und diese Datei fehlt (vom Hauptprozess erkannt).
            runCatching { File(donePath).writeText("code=$code\n") }
            stopSelf()
            // Prozess sicher beenden -> der nächste Solve startet garantiert frisch.
            Process.killProcess(Process.myPid())
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "LocalAstrometry"
        const val EXTRA_ARGS = "args"
        const val EXTRA_DONE = "done"
        const val EXTRA_CANCEL = "cancel"

        /** Name des Hilfsprozesses (siehe `android:process` im Manifest). */
        fun processName(context: Context): String = "${context.packageName}:solver"

        fun start(context: Context, args: Array<String>, donePath: String) {
            val intent = Intent(context, LocalSolveService::class.java)
                .putExtra(EXTRA_ARGS, args)
                .putExtra(EXTRA_DONE, donePath)
            context.startService(intent)
        }

        /**
         * Abbrechen eines laufenden Solves (s. Klassenkommentar zu [EXTRA_CANCEL]) -- läuft ins
         * LEERE (kein Fehler), falls der Prozess inzwischen schon selbst beendet ist: Android
         * startet dann einen frischen `:solver`-Prozess NUR für dieses Signal, der sich sofort
         * selbst beendet, ohne je einen Solve begonnen zu haben. Etwas verschwenderisch, aber
         * harmlos -- kein Vergleich zum vorherigen externen Kill-Versuch.
         */
        fun cancel(context: Context) {
            val intent = Intent(context, LocalSolveService::class.java).putExtra(EXTRA_CANCEL, true)
            context.startService(intent)
        }
    }
}
