package com.codex.starmapper.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

object AppDiagnostics {
    private const val MAX_EVENT_FILE_BYTES = 256 * 1024L
    private const val MAX_EXPORTED_EVENT_LINES = 300
    private var installed = false
    private lateinit var applicationContext: Context

    @Synchronized
    fun install(context: Context) {
        applicationContext = context.applicationContext
        if (installed) return
        installed = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                crashFile().writeText(
                    buildString {
                        appendLine("timestamp=${Instant.now()}")
                        appendLine("thread=${thread.name}")
                        appendLine(error.stackTraceToString())
                    },
                    Charsets.UTF_8,
                )
                record("uncaught_exception type=${error.javaClass.name} message=${safe(error.message)}")
            }
            previousHandler?.uncaughtException(thread, error)
        }
        record("diagnostics_installed")
    }

    @Synchronized
    fun record(event: String) {
        if (!::applicationContext.isInitialized) return
        val file = eventFile()
        file.parentFile?.mkdirs()
        if (file.length() > MAX_EVENT_FILE_BYTES) {
            val retained = file.readLines().takeLast(150)
            file.writeText(retained.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        }
        file.appendText(
            "${Instant.now()} | ${Thread.currentThread().name} | ${safe(event)}\n",
            Charsets.UTF_8,
        )
    }

    /**
     * Liest per [ApplicationExitInfo] (API 30+) aus, WARUM zuletzt Prozesse gestorben sind — inkl.
     * nativer Abstürze (die kein Java-Handler fängt). Landet im Diagnose-Export -> wir sehen die
     * echte Absturz-Ursache OHNE Logcat: nativer SIGSEGV vs. Speichermangel vs. anderes, und in
     * welchem Prozess/welcher .so. Genau das Signal, das die aktuelle Crash-Ursache entscheidet.
     */
    fun recordExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            for (info in infos) {
                record(
                    "proc_exit reason=${exitReasonName(info.reason)} status=${info.status} " +
                        "process=${info.processName} importance=${info.importance} " +
                        "desc=${info.description} at=${info.timestamp}",
                )
                if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    // Tombstone kann Text ODER Protobuf sein -> druckbare Teilstrings extrahieren und
                    // die relevanten (Signal, .so-Namen, astrometry-Funktionen) herausfiltern.
                    val blob = runCatching {
                        info.traceInputStream?.use { it.readBytes().toString(Charsets.US_ASCII) }
                    }.getOrNull().orEmpty()
                    if (blob.isNotEmpty()) {
                        val relevant = Regex("[\\x20-\\x7e]{4,}").findAll(blob)
                            .map { it.value }
                            .filter { s ->
                                s.contains("libastrometry") || s.contains("astrometry") ||
                                    s.contains("SIGSEGV") || s.contains("SIGABRT") ||
                                    s.contains("signal") || s.contains(".so") || s.contains("abort")
                            }
                            .distinct()
                            .take(40)
                            .joinToString(" | ")
                        if (relevant.isNotBlank()) record("proc_exit_native_trace $relevant")
                    }
                }
            }
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH_JVM"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESS_RESOURCE"
        else -> "reason#$reason"
    }

    fun recentEvents(): List<String> {
        if (!::applicationContext.isInitialized) return emptyList()
        return runCatching { eventFile().readLines().takeLast(MAX_EXPORTED_EVENT_LINES) }
            .getOrDefault(emptyList())
    }

    fun previousCrash(): String? {
        if (!::applicationContext.isInitialized) return null
        return runCatching { crashFile().takeIf { it.isFile }?.readText(Charsets.UTF_8) }
            .getOrNull()
    }

    private fun eventFile(): File =
        File(applicationContext.filesDir, "diagnostics/recent-events.log")

    private fun crashFile(): File =
        File(applicationContext.filesDir, "diagnostics/last-crash.txt").also {
            it.parentFile?.mkdirs()
        }

    private fun safe(value: String?): String =
        value.orEmpty().replace('\n', ' ').replace('\r', ' ').take(1_000)
}
