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
import com.codex.starmapper.domain.OverlayKind
import com.codex.starmapper.processing.GraticuleGeometry
import com.codex.starmapper.processing.MilkyWayGeometry
import com.codex.starmapper.processing.OverlayGeometry
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

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
    // Fertig berechnete Render-Geometrie (Bild-px) der beiden NICHT-Overlay-Zeichen-Pässe (Koordinaten-
    // netz/Milchstraße) -- null, wenn der jeweilige Pass gerade ausgeschaltet ist oder ohne Lösung keine
    // Geometrie existiert. Ermöglicht, ALLE Zeichen-Funktionen der App (nicht nur Overlays) pixelgenau
    // allein aus der Diagnose-Textdatei nachzurechnen, ohne Screenshot (Nutzerwunsch 2026-08-27).
    val graticule: GraticuleGeometry?,
    val milkyWay: MilkyWayGeometry?,
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
            val tsg = overlay.tinySkyGeometry
            val freehandSegments = OverlayGeometry.denormalizedFreehandPoints(overlay)
            appendLine(
                "overlay[$index]=" +
                    "id:${overlay.id}," +
                    "kind:${overlay.kind}," +
                    "layer:${overlay.layer ?: "none"}," +
                    "center:${format(overlay.center.x)}x${format(overlay.center.y)}," +
                    "size:${format(overlay.size.width)}x${format(overlay.size.height)}," +
                    "rotation:${format(overlay.rotationDegrees)}," +
                    "showName:${overlay.showName}," +
                    "labelAngleDeg:${format(overlay.labelAngleDeg)}," +
                    "labelLeaderPx:${format(overlay.labelLeaderPx)}," +
                    "style:${overlay.lineStyle}," +
                    "stroke:${format(overlay.strokeWidth)}," +
                    "opacity:${format(overlay.opacity)}," +
                    "anchors:${overlay.anchorOverrides.size}," +
                    // GOLDEN-RUECKBAU 2026-09-03: muss fuer ein normales (nicht 360-Grad-)Foto IMMER
                    // false sein -- true wuerde bedeuten, dass die Naht-Aufteilung beim Zeichnen
                    // wieder aktiv ist (s. AnnotationOverlay.seamAware).
                    "seamAware:${overlay.seamAware}," +
                    // Tatsächliche Anker-Pixelpositionen (nicht nur die Anzahl oben) -- macht die vom
                    // Nutzer per Hand ausgerichtete Sternbildform selbst pixelgenau nachrechenbar, ohne
                    // Screenshot (Nutzerwunsch 2026-08-27: "alle Formen... pixelgenau auswertbar").
                    "anchorPts:${
                        if (overlay.anchorOverrides.isEmpty()) {
                            "none"
                        } else {
                            overlay.anchorOverrides.entries.sortedBy { it.key }
                                .joinToString(";") { (idx, p) -> "$idx-${format(p.x)}x${format(p.y)}" }
                        }
                    }," +
                    // Tatsächliche Freihand-Konturpunkte (Bild-px, denormalisiert -- s.
                    // OverlayGeometry.denormalizedFreehandPoints) -- Segmente mit "|" getrennt, Punkte
                    // je Segment mit ";" -- nur für kind=Freehand gesetzt.
                    "freehandPts:${
                        if (freehandSegments.isEmpty()) {
                            "none"
                        } else {
                            freehandSegments.joinToString("|") { seg ->
                                seg.joinToString(";") { p -> "${format(p.x)}x${format(p.y)}" }
                            }
                        }
                    }," +
                    "constellation:${overlay.constellation?.id ?: "none"}," +
                    "textLength:${overlay.text.length}," +
                    "lineCount:${if (overlay.text.isEmpty()) 0 else overlay.text.count { it == '\n' } + 1}," +
                    // Tiny-Sky-eigene Geometrie (s. AnnotationOverlay.tinySkyGeometry) -- weicht von
                    // center/size/rotation/labelAngleDeg/labelLeaderPx oben ab, SOBALD das Overlay in
                    // Tiny Sky entstanden/bearbeitet wurde; "none" heisst, es gibt nur die eine (native)
                    // Geometrie oben. Ermöglicht, Beschriftungs-/Formpositionen in BEIDEN Modi (2:1 UND
                    // Tiny Sky) allein aus dieser Textdatei nachzurechnen, ohne Screenshot.
                    "tsg:${
                        if (tsg == null) {
                            "none"
                        } else {
                            "center=${format(tsg.center.x)}x${format(tsg.center.y)};" +
                                "size=${format(tsg.size.width)}x${format(tsg.size.height)};" +
                                "rotation=${format(tsg.rotationDegrees)};" +
                                "labelAngleDeg=${format(tsg.labelAngleDeg)};" +
                                "labelLeaderPx=${format(tsg.labelLeaderPx)}"
                        }
                    }",
            )
        }
        appendLine("detectedStarCount=${snapshot.detectedStars.size}")
        if (snapshot.detectedStars.isNotEmpty()) {
            appendLine("detectedStarRadiusRange=${format(snapshot.detectedStars.minOf { it.radius })}..${format(snapshot.detectedStars.maxOf { it.radius })}")
            appendLine("detectedStarScoreRange=${format(snapshot.detectedStars.minOf { it.score })}..${format(snapshot.detectedStars.maxOf { it.score })}")
        }
        appendLine()
        appendLine("[GRATICULE]")
        appendLine("enabled=${snapshot.graticule != null}")
        val graticuleLines = snapshot.graticule?.lines.orEmpty()
        val graticuleLabels = snapshot.graticule?.labels.orEmpty()
        appendLine("lineCount=${graticuleLines.size}")
        graticuleLines.forEachIndexed { index, line ->
            appendLine(
                "line[$index]=points:${line.points.size};pts:${
                    line.points.joinToString(";") { p -> "${format(p.x)}x${format(p.y)}" }
                }",
            )
        }
        appendLine("labelCount=${graticuleLabels.size}")
        graticuleLabels.forEachIndexed { index, label ->
            appendLine("label[$index]=text:${label.text};pos:${format(label.pos.x)}x${format(label.pos.y)}")
        }
        snapshot.graticule?.debug?.let { d ->
            appendLine(
                "debug=centerRa:${format(d.centerRa)};centerDec:${format(d.centerDec)};" +
                    "centerOnlyPxPerDeg:${format(d.centerOnlyPxPerDeg)};minPxPerDeg:${format(d.minPxPerDeg)};" +
                    "fovDeg:${format(d.fovDeg)};raHalf:${format(d.raHalf)};decMin:${format(d.decMin)};" +
                    "decMax:${format(d.decMax)};decStep:${format(d.decStep)};raStepDeg:${format(d.raStepDeg)};" +
                    "raSweepOverlapDeg:${d.raSweepOverlapDeg?.let { format(it) } ?: "none"}",
            )
            appendLine("parallelNullStats=${d.parallelNullStats.joinToString(",")}")
            appendLine(
                "solutionType=wcs:${d.wcsTypeName};projection:${d.projectionTypeName ?: "none"};" +
                    "hasResidualCorrection:${d.hasResidualCorrection};mosaicTileCount:${d.mosaicTileCount ?: "n/a"};" +
                    "mosaicHasFallback:${d.mosaicHasFallback ?: "n/a"};" +
                    "horizontalPeriodPx:${d.horizontalPeriodPx?.let { format(it) } ?: "none"}",
            )
            appendLine("parallelAuditStats=${d.parallelAuditStats.joinToString(",")}")
            appendLine("meridianAuditStats=${d.meridianAuditStats.joinToString(",")}")
            d.segmentDiagnostics.forEachIndexed { index, seg -> appendLine("segment[$index]=$seg") }
            appendLine("microGapFindings=${d.microGapFindings.joinToString(",")}")
            appendLine("seamCrossingCount=${d.seamCrossingCount}")
            appendLine("maxSeamAngleDeg=${d.maxSeamAngleDeg?.let { format(it) } ?: "none"}")
            d.seamCrossingChecks.forEachIndexed { index, c -> appendLine("seamCrossing[$index]=$c") }
            appendLine("worstCurveQualityFinding=${d.worstCurveQualityFinding ?: "none"}")
            d.curveQualityStats.forEachIndexed { index, c -> appendLine("curveQuality[$index]=$c") }
            appendLine("overlapFindingCount=${d.overlapFindings.size}")
            d.overlapFindings.forEachIndexed { index, o -> appendLine("overlap[$index]=$o") }
            appendLine("worstMidpointDeviationFinding=${d.worstMidpointDeviationFinding ?: "none"}")
            d.midpointDeviationStats.forEachIndexed { index, m -> appendLine("midpointDeviation[$index]=$m") }
        }
        appendLine()
        appendLine("[GRATICULE_SEAM_CONTROL_GROUP]")
        // Kontrollgruppe (Nutzer-Vorgabe 2026-08-28, Abschnitt 4): derselbe C0+C1-Test wie oben, aber auf
        // Sternbild-Kanten -- unsere nachweislich funktionierende Referenz. Rein additiv/lesend: nutzt nur
        // bereits vorhandene `overlay.edgePolylines` (die auch beim normalen Zeichnen/Export verwendet
        // werden), rührt weder AstapOverlayMapper.kt noch den Zeichen-/Export-Code an. Nur Sternbild-
        // Overlays mit gekrümmten Kanten (edgePolylines != null, s. StarMapperApp.kt-Kommentar dort) haben
        // überhaupt potenzielle Nahtkreuzungen -- gerade Zwei-Punkt-Kanten (normale Einzelbilder/Mosaike)
        // werden nie an der Naht aufgetrennt und sind hier folgerichtig nicht enthalten.
        data class TaggedCheck(val constellationId: String, val edgeIndex: Int, val check: OverlayGeometry.SeamCrossingCheck)
        val constellationSeamChecks = mutableListOf<TaggedCheck>()
        val diagImageWidth = snapshot.imageWidth ?: 0
        // Dieselbe gefittete Modellperiode wie beim Gradnetz (identische WCS-Lösung in diesem Snapshot) --
        // damit branchByModelPeriod auch hier vergleichbar ist, s. Nutzer-Vorgabe 2026-08-28 Abschnitt 3.
        val diagModelPeriodPx = snapshot.graticule?.debug?.horizontalPeriodPx
        // Nutzer-Vorgabe 2026-08-28 (Folgeauftrag, explizite Framing-Anweisung): `crossingCount=0` darf
        // NIEMALS als "kein Problem gefunden" gelesen werden -- es bedeutet nur, dass entweder (a) diese
        // Aufnahme keine gekrümmten Sternbild-Kanten hat (kein Fisheye/Panorama-Overlay platziert) oder
        // (b) zwar welche existieren, aber zufällig keine davon exakt eine `k*imageWidth`-Kreuzung
        // überstreicht -- BEIDES sind Daten-Verfügbarkeitsfragen dieser einen Aufnahme, KEIN Beleg, dass
        // die Gradnetz-Nahtbefunde oben unbedenklich wären. Diese beiden Zwischenzahlen machen die
        // Unterscheidung direkt aus dem Text ablesbar, ohne dass das beim Lesen extra hergeleitet werden muss.
        var constellationOverlayCount = 0
        var curvedEdgeConstellationCount = 0
        if (diagImageWidth > 0) {
            snapshot.overlays.forEach { overlay ->
                if (overlay.kind != OverlayKind.Constellation) return@forEach
                constellationOverlayCount++
                val edgePolylines = overlay.edgePolylines ?: return@forEach
                curvedEdgeConstellationCount++
                val constellationId = overlay.constellation?.id ?: "?"
                edgePolylines.forEachIndexed { edgeIndex, poly ->
                    OverlayGeometry.analyzeSeamCrossings(poly, diagImageWidth, diagModelPeriodPx).forEach { c ->
                        constellationSeamChecks += TaggedCheck(constellationId, edgeIndex, c)
                    }
                }
            }
        }
        appendLine("constellationOverlayCount=$constellationOverlayCount;curvedEdgeConstellationCount=$curvedEdgeConstellationCount")
        appendLine("crossingCount=${constellationSeamChecks.size}")
        val maxConstellationAngle = constellationSeamChecks
            .mapNotNull { it.check.angleDifferenceDeg.takeIf { a -> a.isFinite() } }
            .maxOfOrNull { abs(it) }
        appendLine("maxAngleDeg=${maxConstellationAngle?.let { format(it) } ?: "none"}")
        constellationSeamChecks.forEachIndexed { index, t ->
            val c = t.check
            appendLine(
                "crossing[$index]=constellation:${t.constellationId};edgeIdx:${t.edgeIndex};" +
                    "seamY:${format(c.seamY)};" +
                    "angleDiffDeg:${if (c.angleDifferenceDeg.isFinite()) format(c.angleDifferenceDeg) else "n/a"};" +
                    "angleDiffNearDeg:${if (c.angleDifferenceDegNear.isFinite()) format(c.angleDifferenceDegNear) else "n/a"};" +
                    "leftLen:${format(c.leftSegmentLength)};rightLen:${format(c.rightSegmentLength)};" +
                    "leftWinPts:${c.leftWindowPoints};rightWinPts:${c.rightWindowPoints};" +
                    "xBefore:${format(c.unwrappedXBefore)};yBefore:${format(c.unwrappedYBefore)};" +
                    "xAfter:${format(c.unwrappedXAfter)};yAfter:${format(c.unwrappedYAfter)};" +
                    "modelPeriodPx:${c.modelPeriodPx?.let { format(it) } ?: "n/a"};" +
                    "branchByWidth:${c.branchByImageWidthBefore}->${c.branchByImageWidthAfter};" +
                    "branchByModelPeriod:${c.branchByModelPeriodBefore ?: "n/a"}->${c.branchByModelPeriodAfter ?: "n/a"}",
            )
        }
        appendLine()
        appendLine("[MILKYWAY]")
        appendLine("enabled=${snapshot.milkyWay != null}")
        val milkyWayRings = snapshot.milkyWay?.levels.orEmpty().flatMap { level -> level.rings.map { level.level to it } }
        appendLine("ringCount=${milkyWayRings.size}")
        milkyWayRings.forEachIndexed { index, (level, ring) ->
            appendLine(
                "ring[$index]=level:$level;points:${ring.points.size};pts:${
                    ring.points.joinToString(";") { p -> "${format(p.x)}x${format(p.y)}" }
                }",
            )
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
