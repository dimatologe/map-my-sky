package com.codex.starmapper.diagnostics

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowInsets
import androidx.core.content.FileProvider
import com.codex.starmapper.BuildConfig
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.DetectedStar
import java.io.File
import java.time.Instant
import java.util.Locale

data class AppDiagnosticSnapshot(
    val imageWidth: Int?,
    val imageHeight: Int?,
    val imageConfig: String?,
    val imageMimeType: String?,
    val selectedTool: String,
    val activePanel: String?,
    val selectedOverlayId: Long?,
    val overlays: List<AnnotationOverlay>,
    val detectedStars: List<DetectedStar>,
    val showDetectedStars: Boolean,
    val showConstellationAnchors: Boolean,
    val starDetectionSensitivity: Float,
    val selectedConstellationId: String,
    val constellationCatalogCount: Int,
    val skyCatalogStarCount: Int,
    val referenceCatalogStarCount: Int,
    val deepSkyObjectCount: Int,
    val milkyWayLayerCount: Int,
    val d3Settings: Map<String, Any>,
)

object AppDiagnosticExporter {
    fun createShareUri(
        context: Context,
        snapshot: AppDiagnosticSnapshot,
    ): Uri {
        AppDiagnostics.record("app_diagnostic_export_requested")
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "sternbild_mapper_app_diagnose_${System.currentTimeMillis()}.txt")
        file.writeText(buildReport(context, snapshot), Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    internal fun buildReport(context: Context, snapshot: AppDiagnosticSnapshot): String = buildString {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val runtime = Runtime.getRuntime()
        val activity = context as? Activity
        val displayMetrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val navigationInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.decorView?.rootWindowInsets
                ?.getInsets(WindowInsets.Type.navigationBars())
        } else {
            null
        }
        val statusInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.decorView?.rootWindowInsets
                ?.getInsets(WindowInsets.Type.statusBars())
        } else {
            null
        }
        val internalStorage = StatFs(context.filesDir.absolutePath)
        val externalStorage = runCatching {
            StatFs(Environment.getExternalStorageDirectory().absolutePath)
        }.getOrNull()

        appendLine("Sternbild Mapper - full app diagnostic report")
        appendLine("Generated: ${Instant.now()}")
        appendLine("Privacy: No original image, thumbnail, file name or image path is included.")
        appendLine()
        appendLine("[APP]")
        appendLine("package=${context.packageName}")
        appendLine("versionName=${packageInfo?.versionName ?: "unknown"}")
        appendLine("versionCode=${versionCode(packageInfo)}")
        appendLine("buildId=${BuildConfig.BUILD_ID}")
        appendLine("locale=${Locale.getDefault().toLanguageTag()}")
        appendLine("nightMode=${configuration.uiMode}")
        appendLine()
        appendLine("[DEVICE]")
        appendLine("manufacturer=${Build.MANUFACTURER}")
        appendLine("brand=${Build.BRAND}")
        appendLine("model=${Build.MODEL}")
        appendLine("device=${Build.DEVICE}")
        appendLine("hardware=${Build.HARDWARE}")
        appendLine("androidSdk=${Build.VERSION.SDK_INT}")
        appendLine("androidRelease=${Build.VERSION.RELEASE}")
        appendLine("supportedAbis=${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("screenPixels=${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
        appendLine("screenDp=${configuration.screenWidthDp}x${configuration.screenHeightDp}")
        appendLine("density=${format(displayMetrics.density)}")
        appendLine("densityDpi=${displayMetrics.densityDpi}")
        appendLine("fontScale=${format(configuration.fontScale)}")
        appendLine("orientation=${configuration.orientation}")
        appendLine("navigationInsetsPx=${navigationInsets?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "unavailable"}")
        appendLine("statusInsetsPx=${statusInsets?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "unavailable"}")
        appendLine()
        appendLine("[RUNTIME]")
        appendLine("memoryMaxBytes=${runtime.maxMemory()}")
        appendLine("memoryTotalBytes=${runtime.totalMemory()}")
        appendLine("memoryFreeBytes=${runtime.freeMemory()}")
        appendLine("processors=${runtime.availableProcessors()}")
        appendLine("internalStorageAvailableBytes=${internalStorage.availableBytes}")
        appendLine("internalStorageTotalBytes=${internalStorage.totalBytes}")
        appendLine("externalStorageAvailableBytes=${externalStorage?.availableBytes ?: -1}")
        appendLine()
        appendLine("[IMAGE]")
        appendLine("loaded=${snapshot.imageWidth != null}")
        appendLine("dimensions=${snapshot.imageWidth ?: "none"}x${snapshot.imageHeight ?: "none"}")
        appendLine("bitmapConfig=${snapshot.imageConfig ?: "none"}")
        appendLine("mimeType=${snapshot.imageMimeType ?: "unknown"}")
        appendLine()
        appendLine("[EDITOR STATE]")
        appendLine("selectedTool=${snapshot.selectedTool}")
        appendLine("activePanel=${snapshot.activePanel ?: "none"}")
        appendLine("selectedOverlayId=${snapshot.selectedOverlayId ?: "none"}")
        appendLine("showDetectedStars=${snapshot.showDetectedStars}")
        appendLine("showConstellationAnchors=${snapshot.showConstellationAnchors}")
        appendLine("starDetectionSensitivity=${format(snapshot.starDetectionSensitivity)}")
        appendLine("selectedConstellationId=${snapshot.selectedConstellationId}")
        appendLine("overlayCount=${snapshot.overlays.size}")
        snapshot.overlays.forEachIndexed { index, overlay ->
            appendLine(
                "overlay[$index]=" +
                    "id:${overlay.id}," +
                    "kind:${overlay.kind}," +
                    "center:${format(overlay.center.x)}x${format(overlay.center.y)}," +
                    "size:${format(overlay.size.width)}x${format(overlay.size.height)}," +
                    "rotation:${format(overlay.rotationDegrees)}," +
                    "style:${overlay.lineStyle}," +
                    "stroke:${format(overlay.strokeWidth)}," +
                    "opacity:${format(overlay.opacity)}," +
                    "anchors:${overlay.anchorOverrides.size}," +
                    "constellation:${overlay.constellation?.id ?: "none"}," +
                    "textLength:${overlay.text.length}",
            )
        }
        appendLine("detectedStarCount=${snapshot.detectedStars.size}")
        if (snapshot.detectedStars.isNotEmpty()) {
            appendLine("detectedStarRadiusRange=${format(snapshot.detectedStars.minOf { it.radius })}..${format(snapshot.detectedStars.maxOf { it.radius })}")
            appendLine("detectedStarScoreRange=${format(snapshot.detectedStars.minOf { it.score })}..${format(snapshot.detectedStars.maxOf { it.score })}")
        }
        appendLine()
        appendLine("[CATALOGS]")
        appendLine("constellations=${snapshot.constellationCatalogCount}")
        appendLine("sphereStars=${snapshot.skyCatalogStarCount}")
        appendLine("referenceStars=${snapshot.referenceCatalogStarCount}")
        appendLine("deepSkyObjects=${snapshot.deepSkyObjectCount}")
        appendLine("milkyWayLayers=${snapshot.milkyWayLayerCount}")
        snapshot.d3Settings.toSortedMap().forEach { (key, value) ->
            appendLine("d3.$key=$value")
        }
        appendLine()
        appendLine("[PREFERENCES]")
        val preferences = context.getSharedPreferences("sternbild_mapper", Context.MODE_PRIVATE)
        preferences.all.toSortedMap().forEach { (key, value) ->
            // Zugangsdaten gehören nicht in teilbare Diagnoseberichte.
            if (key in secretPreferenceKeys) {
                appendLine("$key=${if (value?.toString().isNullOrBlank()) "missing" else "set"}")
            } else {
                appendLine("$key=${sanitizePreference(value)}")
            }
        }
        appendLine()
        appendLine("[APP FILES]")
        appendFileInventory(context.filesDir, context.filesDir, maxDepth = 4)
        appendLine()
        appendLine("[RECENT EVENTS]")
        val events = AppDiagnostics.recentEvents()
        if (events.isEmpty()) appendLine("none") else events.forEach(::appendLine)
        appendLine()
        appendLine("[LAST UNCAUGHT EXCEPTION]")
        appendLine(AppDiagnostics.previousCrash() ?: "none")
    }

    private fun StringBuilder.appendFileInventory(root: File, directory: File, maxDepth: Int) {
        val depth = directory.relativeTo(root).invariantSeparatorsPath
            .split('/')
            .count { it.isNotBlank() }
        if (depth > maxDepth) return
        directory.listFiles().orEmpty()
            .filterNot { it.name == "last-crash.txt" || it.name == "recent-events.log" }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
            .forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (file.isDirectory) {
                    appendLine("dir=$relative")
                    appendFileInventory(root, file, maxDepth)
                } else {
                    appendLine("file=$relative;bytes=${file.length()}")
                }
            }
    }

    private val secretPreferenceKeys = setOf("nova_api_key")

    private fun sanitizePreference(value: Any?): String = when (value) {
        is Set<*> -> value.joinToString(prefix = "[", postfix = "]")
        else -> value.toString().replace('\n', ' ').take(500)
    }

    private fun versionCode(packageInfo: android.content.pm.PackageInfo?): Long {
        if (packageInfo == null) return -1
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun format(value: Number): String =
        String.format(Locale.US, "%.4f", value.toDouble())
}
