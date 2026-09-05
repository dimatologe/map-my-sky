package com.codex.starmapper.processing

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.diagnostics.AppDiagnostics
import androidx.compose.ui.geometry.Size
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.CatalogStar
import com.codex.starmapper.domain.ConstellationPattern
import com.codex.starmapper.domain.DeepSkyObject
import com.codex.starmapper.domain.DsoShape
import com.codex.starmapper.domain.DsoColorGroup
import com.codex.starmapper.domain.DsoSizeOverride
import com.codex.starmapper.domain.Hemisphere
import com.codex.starmapper.domain.OverlayFont
import com.codex.starmapper.domain.OverlayKind
import com.codex.starmapper.domain.OverlayLineStyle
import com.codex.starmapper.domain.SkyPoint
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class DeepSkyCategory(val colorArgb: Long) {
    Galaxy(0xFFB7C8FF),
    Nebula(0xFF7BE7D6),
    Cluster(0xFFFFD77E),
    Other(0xFFD7E3FF),
    ;

    companion object {
        // Typcodes aus dem d3-celestial-Katalog (dsos.20.json), kleingeschrieben.
        fun fromType(type: String): DeepSkyCategory = when (type) {
            "g", "s", "s0", "sd", "i", "e", "gg" -> Galaxy
            "en", "bn", "sfr", "rn", "pn", "snr", "dn" -> Nebula
            "oc", "gc" -> Cluster
            else -> Other
        }
    }
}

data class ConstellationAccuracy(val matchedStars: Int, val rmsErrorPx: Double)

/**
 * Vollständigkeits-Momentaufnahme EINES Katalog-Sternbilds aus einem [AstapOverlayMapper.createConstellationOverlays]-
 * Durchlauf — für ALLE 88 Katalog-Muster erfasst (nicht nur die tatsächlich gezeichneten), damit ein
 * Audit systematisch prüfen kann, ob/wie vollständig jedes einzelne Muster gezeichnet wurde (nicht nur
 * die paar, die visuell auffallen). [survivingStars]/[survivingEdges] sind 0, wenn das Muster komplett
 * verworfen wurde (kein sichtbarer Stern ODER Gesamt-Kohärenz-Cull) — [anyStarInFov] unterscheidet dabei
 * "korrekt nicht im Bild" (z.B. Südhalbkugel-Sternbild bei einer Nordhimmel-Aufnahme) von "im Bild, aber
 * nichts überlebt" (echtes Vollständigkeits-Problem).
 */
data class ConstellationCompleteness(
    val id: String,
    val hemisphere: Hemisphere,
    val totalStars: Int,
    val totalEdges: Int,
    val survivingStars: Int,
    val survivingEdges: Int,
    val anyStarInFov: Boolean,
)

object AstapOverlayMapper {

    // Zweites, von der Skalen-Schätzung unabhängiges Sicherheitsnetz im Divergenz-/Naht-Cull unten:
    // eine Kante, deren gezeichnete Länge schon diesen Anteil der Bilddiagonale übersteigt, ist bei
    // JEDER real fotografierten Sternbild-Ausdehnung unplausibel und wird verworfen, auch wenn die
    // (selbst nur eine Näherung bleibende) lokale Skalen-Schätzung sie durchließe. Startwert
    // UNVERIFIZIERT gegen echte Gerätefälle (z.B. den Herkules-Spinnennetz-Fall, 2026-07-24) --
    // vor endgültigem Festschreiben mit mehreren echten Weitwinkel-/Panorama-Aufnahmen prüfen.
    private const val MAX_EDGE_DIAGONAL_FRACTION = 0.6f

    // Analog zu MAX_EDGE_DIAGONAL_FRACTION, aber fuer die GESAMTE Ausdehnung eines Musters ueber alle
    // ueberlebenden Kanten: selbst wenn jede Kante einzeln lokal plausibel bleibt, kann die Vereinigung
    // an verschiedenen Stellen verankerter Kanten insgesamt verstreut/inkohaerent sein (Grosser-Baer-
    // Befund, 2026-07-27). Schwellen unverifiziert gegen echte Geraetefaelle, wie schon
    // MAX_EDGE_DIAGONAL_FRACTION -- ggf. nach dem naechsten Geraetetest nachjustieren.
    private const val MAX_PATTERN_DIAGONAL_FRACTION = 1.5f
    private const val PATTERN_DIAGONAL_TOLERANCE = 3.5f
    private const val PATTERN_DIAGONAL_SLACK_FRACTION = 0.05f

    // Grosskreis-Zwischenpunkte einer gekruemmten Kante (greatCircleImagePolyline) sind -- anders
    // als deren Endpunkte -- NICHT einzeln durch Maske/Divergenz geprueft: faellt ein Zwischenpunkt
    // in eine Luecke ohne Kachel-Anspruch, kann der Fallback dort weit daneben extrapolieren, obwohl
    // BEIDE echten Endpunkte laengst als plausibel bestaetigt sind. Gleiche Toleranz-Groessenordnung
    // wie der bestehende Pro-Kante-Divergenz-Cull.
    private const val CURVE_DEVIATION_TOLERANCE = 3.5f

    fun createConstellationOverlays(
        catalog: List<ConstellationPattern>,
        solution: WcsSolutionLike,
        imageWidth: Int,
        imageHeight: Int,
        // Vordergrund-Maske (SolveMask, 1/SCALE-Auflösung) — Sternbild-Kanten mit einem Endpunkt auf
        // übermaltem Vordergrund werden verworfen, unabhängig davon, ob sie sonst gültig lösen.
        foregroundMask: Bitmap? = null,
        colorArgb: Long,
        strokeWidth: Float,
        anchorRadiusRatio: Float,
        lineStyle: OverlayLineStyle,
        opacity: Float,
        showNames: Boolean,
        nameTextSize: Float,
        font: OverlayFont = OverlayFont.SansSerif,
        // Optionale Vollständigkeits-Erfassung fürs Audit (s. ConstellationCompleteness) — für JEDES
        // Katalog-Muster genau ein Eintrag, unabhängig davon, ob es am Ende gezeichnet wird. Default
        // null -> keine Verhaltens-/Performance-Änderung für Aufrufer, die das nicht brauchen.
        completenessOut: MutableList<ConstellationCompleteness>? = null,
        // `true` NUR, wenn dieses Bild aus dem expliziten 360°-/Tiny-Sky-Workflow stammt (s.
        // seamAwareProjection unten). Default `false` = Golden-Verhalten: keine Naht-Sonderbehandlung.
        panoramaWorkflowActive: Boolean = false,
    ): List<AnnotationOverlay> {
        val padding = max(imageWidth, imageHeight) * 0.08f
        // Fisheye/Panorama: gerade Pixel-Linien zwischen weit entfernten Ankern laufen quer durchs
        // Bild ("Spinnennetz"). Daher (a) Kanten zu Sternen weit AUSSERHALB des Bildes verwerfen
        // und (b) die verbleibenden Kanten als gekrümmte Großkreis-Polylinien zeichnen. Bei
        // normalen Einzelbildern/Mosaiken bleibt alles wie bisher (gerade Linien, kein Regress).
        // Fisheye-Projektion (radial) ODER Crop-Direct (Einzel-Kachel-Mosaik aus dem astrometry.net-
        // Crop-Solve) -> Kanten zu weit entfernten Sternen verwerfen (sonst Querlinien / unzuverlässige
        // TAN-Extrapolation außerhalb des gelösten Bereichs).
        val mosaic = solution as? MosaicWcsSolution
        // Gekruemmte Grosskreis-Darstellung aktivieren, wenn IRGENDEIN Teil der tatsaechlich genutzten
        // Loesung nichtlinear ist: entweder `solution` selbst (Einzelbild-Fisheye/Panorama ohne Mosaik)
        // oder der Luecken-Fallback eines Mosaiks (Mercator/Zylindrisch/Fisheye/Stereografisch -- der in
        // der Praxis haeufigste Fall). greatCircleImagePolyline() reicht dieselbe `solution`-Referenz pro
        // Zwischenpunkt weiter -- MosaicWcsSolution.skyToImage() waehlt darin jeden Grosskreis-Stuetzpunkt
        // selbst wieder passend (echte Kachel-TAN ODER Fallback), auch bei gemischten Kanten. Der reine
        // Einzelkachel-Crop-Direct-Fall (kein Fallback) bleibt unveraendert gerade (mosaic.fallback ist
        // dort per Konstruktion null, s. cropTile zwei Zeilen weiter).
        val fisheyeProjection = solution is PanoramaWcsSolution || mosaic?.fallback is PanoramaWcsSolution
        // Naht-Aufteilung beim Zeichnen: BEIDE Bedingungen muessen erfuellt sein.
        //
        // (1) Die Projektion ist horizontal periodisch (`horizontalPeriodPx() != null`) -- ohne Periode
        //     gibt es keine Naht, an der ueberhaupt geteilt werden koennte.
        // (2) [panoramaWorkflowActive]: das Bild stammt aus dem EXPLIZITEN 360°-/Tiny-Sky-Workflow.
        //
        // Bedingung (2) ist neu (Nutzer-Vorgabe 2026-09-04). Vorher genuegte (1) allein -- das war zu
        // breit: ein normal importiertes, Mercator-GESTITCHTES Foto ist mathematisch ebenfalls periodisch,
        // aber kein 360°-Panorama-Workflow. Auf so einem Bild verschob `splitPolylineAtSeam` Kantenstuecke
        // ausserhalb [0, imageWidth] um ±imageWidth zurueck INS Bild und zeigte dadurch Sternbilder(teile),
        // die dort nicht hingehoeren (Gerätebefund Diagnose 45: 69/69 Overlays seamAware=true auf einem
        // 6392x4101-Foto). Die Golden-APK behandelt exakt dasselbe Bild mit Mercator korrekt (Diagnose 47)
        // -- sie kannte splitPolylineAtSeam gar nicht.
        //
        // Mercator selbst wird dadurch NICHT entwertet: nur die Naht-Sonderbehandlung entfaellt, die
        // Projektion und ihre Auswahl bleiben unveraendert. Der echte 360°-Fall behaelt sie vollstaendig.
        // GraticuleRenderers eigenes `seamAware` ist davon UNBERUEHRT (eingefrorener Gradnetz-Umbau).
        val periodicProjectionSource = (solution as? PanoramaWcsSolution)
            ?: (mosaic?.fallback as? PanoramaWcsSolution)
        val periodicProjection = periodicProjectionSource?.projection?.horizontalPeriodPx() != null
        val seamAwareProjection = panoramaWorkflowActive && periodicProjection
        // "Genau 1 Kachel" ist nur dann der alte Crop-Direct-Fall (ganzes Bild als EINE Kachel gelöst,
        // Cull auf nur diese Kachel sinnvoll), wenn KEIN Fallback existiert. Mit Fallback (seit 0.16.0:
        // Einzelbildlösung deckt den Rest ab) deckt eine einzelne Verfeinerungs-Kachel NUR einen kleinen
        // Ausschnitt ab -> sonst würden Sternbilder außerhalb dieser Kachel fälschlich verworfen, obwohl
        // der Fallback sie dort korrekt zeichnen könnte (Einzelbild + Kachel sollen sich ergänzen).
        val cropTile = mosaic?.tiles?.singleOrNull()?.takeIf { mosaic.fallback == null }
        // Kanten zu weit entfernten Ankern IMMER verwerfen (auch bei normalem Einzelbild/Mosaik):
        // sonst laufen Linien quer durchs Schwarze, und Sternbilder ~80-90° neben dem Feld (z.B.
        // Volans/Vela) projizieren via TAN auf Millionen-Pixel ("endlose" Linien). Der erlaubte
        // Bereich ist PRO BILD relativ zur jeweiligen Lösung (keine feste Sternbild-Sperrliste) –
        // eine echte Südhimmel-Aufnahme um Volans/Vela zeigt diese normal.
        val cullFarEdges = true
        val regionMinX: Float
        val regionMinY: Float
        val regionMaxX: Float
        val regionMaxY: Float
        if (cropTile != null) {
            // Nur der gelöste Ausschnitt (+10% Rand) – draußen ist die TAN+SIP-Lösung extrapoliert.
            val mx = cropTile.tileWidth * 0.10f
            val my = cropTile.tileHeight * 0.10f
            regionMinX = cropTile.tileOffsetX - mx
            regionMinY = cropTile.tileOffsetY - my
            regionMaxX = cropTile.tileOffsetX + cropTile.tileWidth + mx
            regionMaxY = cropTile.tileOffsetY + cropTile.tileHeight + my
        } else {
            // ~50% Überstand über den Bildrand ausdrücklich erlaubt (teils angeschnittene
            // Sternbilder bleiben sichtbar); alles Weitere/Divergente (Millionen-Pixel) fällt weg.
            regionMinX = -imageWidth * 0.5f
            regionMinY = -imageHeight * 0.5f
            regionMaxX = imageWidth * 1.5f
            regionMaxY = imageHeight * 1.5f
        }
        fun nearFrame(p: Offset): Boolean =
            p.x in regionMinX..regionMaxX && p.y in regionMinY..regionMaxY
        // Bewusst KEIN separates Anker-/Kachel-Abstandsgate mehr (bis 0.22.26: isClaimedByAnyTile +
        // isNearAnyAnchor, 20°-Marge): Sterne knapp außerhalb dieser Marge wurden per Kante komplett
        // verworfen, auch wenn der glatte Fallback dort eine plausible Position lieferte -- das riss
        // sonst zusammenhängende Sternbilder an der Marge ab ("abgehakt", Nutzer-Befund 2026-07-28).
        // Divergenz-/Naht-Cull unten + die Gesamt-Kohärenz-Prüfung weiter unten bleiben die alleinige
        // Schutzschicht gegen zu lange/verstreute Kanten; sie kennen keine Anker-Distanz, sondern
        // beurteilen die tatsächlich GEZEICHNETE Geometrie.
        return catalog.mapIndexedNotNull { catalogIndex, pattern ->
            // Referenzpunkt für die nahtstellen-sichere Umrechnung ALLER Sternpositionen dieses
            // Musters: die (grobe, sphärische) Muster-Mitte, EINMAL über den Hauptzweig projiziert.
            // Jeder Einzelstern wird unten relativ zu DIESER EINEN gemeinsamen Referenz entfaltet
            // (nicht von Stern zu Stern verkettet -- die Katalog-Reihenfolge in pattern.stars ist
            // keine räumliche Nachbarschaft), damit je ZWEI beliebige Sterne des Musters (nicht nur
            // Listen-Nachbarn, also auch die beiden Enden einer beliebigen Kante) auf demselben Ast
            // landen. Für jedes reale IAU-Sternbild (Winkelausdehnung immer klar unter 180°, selbst
            // Hydra nur ~100°) ist "nächster Ast zur Muster-Mitte" für JEDEN Einzelstern eindeutig
            // richtig. Schlägt schon die Referenz-Projektion fehl, bleibt reference=null -> identisches
            // Verhalten wie vor diesem Fix.
            val patternCenterDir = pattern.stars.fold(Vec3(0.0, 0.0, 0.0)) { acc, star ->
                val v = raDecToVector(star.raHours * 15.0, star.decDegrees.toDouble())
                Vec3(acc.x + v.x, acc.y + v.y, acc.z + v.z)
            }.normalized()
            val (patternCenterRa, patternCenterDec) = vectorToRaDec(patternCenterDir)
            val patternCenterSky = com.codex.starmapper.domain.SkyPoint(patternCenterRa.toFloat(), patternCenterDec.toFloat())
            val patternReference = solution.skyToImage(point = patternCenterSky, imageHeight = imageHeight)
            val projected = pattern.stars.map { star ->
                solution.skyToImage(
                    point = com.codex.starmapper.domain.SkyPoint(
                        raDegrees = star.raHours * 15f,
                        decDegrees = star.decDegrees,
                    ),
                    imageHeight = imageHeight,
                    reference = patternReference,
                )
            }
            val visibleEdges = pattern.edges.filter { (startIndex, endIndex) ->
                val start = projected.getOrNull(startIndex) ?: return@filter false
                val end = projected.getOrNull(endIndex) ?: return@filter false
                // Vordergrund-Maske: ein Endpunkt auf übermaltem Vordergrund verwirft die Kante,
                // unabhängig davon, ob sie sonst gültig lösen würde.
                if (foregroundMask != null &&
                    (SolveMask.isMasked(foregroundMask, start) || SolveMask.isMasked(foregroundMask, end))
                ) {
                    return@filter false
                }
                // Anti-Spinnennetz: nur Kanten behalten, deren BEIDE Enden im gültigen Bereich liegen
                // (Sterne weit außerhalb / hinter der Kamera erzeugen die Querlinien).
                if (cullFarEdges && !(nearFrame(start) && nearFrame(end))) return@filter false
                // Divergenz-/Naht-Cull (modellunabhängig): erwartete Pixel-Länge = Winkelabstand der
                // beiden Sterne × lokale Skala (px/Grad). Ist die GEZEICHNETE Länge viel größer, ist
                // die Projektion dort falsch extrapoliert / die Kante läuft über die 360°-Naht ->
                // verwerfen. Fängt Querlinien unabhängig vom Projektionsmodell.
                val sa = pattern.stars[startIndex]
                val sb = pattern.stars[endIndex]
                val angSepDeg = Math.toDegrees(
                    acos(
                        raDecToVector(sa.raHours * 15.0, sa.decDegrees.toDouble())
                            .dot(raDecToVector(sb.raHours * 15.0, sb.decDegrees.toDouble()))
                            .coerceIn(-1.0, 1.0),
                    ),
                ).toFloat()
                val actual = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat()
                // Absolute Obergrenze zuerst (billig, unabhängig von jeder Skalen-Schätzung unten).
                val imageDiagonal = hypot(imageWidth.toDouble(), imageHeight.toDouble()).toFloat()
                if (actual > MAX_EDGE_DIAGONAL_FRACTION * imageDiagonal) return@filter false
                val skyA = com.codex.starmapper.domain.SkyPoint(sa.raHours * 15f, sa.decDegrees)
                val skyB = com.codex.starmapper.domain.SkyPoint(sb.raHours * 15f, sb.decDegrees)
                val pxPerDeg = estimatePixelsPerDegree(solution, skyA, start, skyB, end, imageHeight)
                if (pxPerDeg > 0f) {
                    val expected = angSepDeg * pxPerDeg
                    val slack = max(imageWidth, imageHeight) * 0.02f
                    if (actual > 3.5f * expected + slack) return@filter false
                }
                segmentIntersectsImage(start, end, imageWidth, imageHeight, padding)
            }
            // Aufzeichnung fürs Vollständigkeits-Audit (s. ConstellationCompleteness) — an GENAU den
            // drei Ausstiegspunkten unten, dieselbe Cull-Logik wie die eigentliche Zeichenentscheidung,
            // keine zweite Quelle der Wahrheit.
            fun recordCompleteness(survivingStars: Int, survivingEdges: Int) {
                completenessOut?.add(
                    ConstellationCompleteness(
                        id = pattern.id,
                        hemisphere = pattern.hemisphere,
                        totalStars = pattern.stars.size,
                        totalEdges = pattern.edges.size,
                        survivingStars = survivingStars,
                        survivingEdges = survivingEdges,
                        // Kein separates Anker-/Kachel-Sichtbarkeitsfeld mehr (s. Kommentar bei
                        // visibleEdges oben) -> "nahe genug am fotografierten Bereich" ersetzt sinngemäß
                        // den entfernten Anker-Abstands-Begriff, ohne neue Prüf-Logik einzuführen.
                        anyStarInFov = projected.any { it != null && nearFrame(it) },
                    ),
                )
            }
            if (visibleEdges.isEmpty()) {
                recordCompleteness(0, 0)
                return@mapIndexedNotNull null
            }

            val usedOriginalIndices = visibleEdges
                .flatMap { listOf(it.first, it.second) }
                .distinct()
            val indexMap = usedOriginalIndices.withIndex().associate { it.value to it.index }
            val points = usedOriginalIndices.map { projected[it] ?: return@mapIndexedNotNull null }
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }

            // Gesamt-Kohärenz-Cull: selbst wenn jede Kante für sich lokal plausibel bleibt (Prüfungen
            // oben), kann die Vereinigung mehrerer, an verschiedenen (z.B. weit auseinanderliegenden
            // Kachel-/Fallback-)Stellen verankerter Kanten insgesamt verstreut/inkohärent sein — ein
            // glatt-aber-global-falsch extrapoliertes Fallback-Modell bleibt lokal selbstkonsistent,
            // auch wenn es global falsch ist (Großer-Bär-Befund, 2026-07-27: Bounding-Box breiter als
            // das ganze Bild, obwohl jede einzelne Kante ihre eigene Prüfung bestand). Analog zur
            // Pro-Kante-Prüfung oben (absolute Obergrenze zuerst, dann Winkel-basierte Erwartung), nur
            // auf die GESAMTE Ausdehnung angewandt statt auf eine einzelne Kante.
            val actualDiagonal = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat()
            val imageDiagonalTotal = hypot(imageWidth.toDouble(), imageHeight.toDouble()).toFloat()
            var coherent = actualDiagonal <= MAX_PATTERN_DIAGONAL_FRACTION * imageDiagonalTotal
            if (coherent) {
                val usedDirs = usedOriginalIndices.map { idx ->
                    idx to raDecToVector(
                        pattern.stars[idx].raHours * 15.0,
                        pattern.stars[idx].decDegrees.toDouble(),
                    )
                }
                val (maxAngSepDeg, farA, farB) = angularDiameter(usedDirs)
                val projectedByIndex = usedOriginalIndices.zip(points).toMap()
                val pA = projectedByIndex[farA]
                val pB = projectedByIndex[farB]
                if (pA != null && pB != null && farA != farB) {
                    val sa = pattern.stars[farA]
                    val sb = pattern.stars[farB]
                    // Bevorzugt die tatsächlich gesampelte Pfadlänge (korrekt bei nichtlinearen
                    // Projektionen über größere Distanzen, s. sampledPathLength); nur wenn das
                    // Sampling scheitert (z.B. Pfad läuft komplett durch eine Pol-Ausschlusszone)
                    // auf die alte lineare Skalenschätzung von EINEM Punktepaar zurückfallen.
                    val expectedDiagonal = sampledPathLength(
                        sa.raHours, sa.decDegrees, sb.raHours, sb.decDegrees, solution, imageHeight,
                        imageWidth, pA, pB,
                    ) ?: estimatePixelsPerDegree(
                        solution, SkyPoint(sa.raHours * 15f, sa.decDegrees), pA,
                        SkyPoint(sb.raHours * 15f, sb.decDegrees), pB, imageHeight,
                    ).takeIf { it > 0f }?.let { maxAngSepDeg * it }
                    if (expectedDiagonal != null) {
                        val slack = max(imageWidth, imageHeight) * PATTERN_DIAGONAL_SLACK_FRACTION
                        coherent = actualDiagonal <= PATTERN_DIAGONAL_TOLERANCE * expectedDiagonal + slack
                    }
                }
            }
            if (!coherent) {
                recordCompleteness(0, 0)
                return@mapIndexedNotNull null
            }
            recordCompleteness(usedOriginalIndices.size, visibleEdges.size)

            // Gekrümmte Kanten nur bei echter Fisheye-Projektion (radial). Crop-Direct ist TAN ->
            // gerade Linien sind dort korrekt.
            val edgePolylines = if (fisheyeProjection) {
                visibleEdges.map { (startIndex, endIndex) ->
                    val start = checkNotNull(projected[startIndex])
                    val end = checkNotNull(projected[endIndex])
                    val a = pattern.stars[startIndex]
                    val b = pattern.stars[endIndex]
                    val curved = greatCircleImagePolyline(
                        a.raHours, a.decDegrees, b.raHours, b.decDegrees, solution, imageHeight, reference = start,
                    )
                    if (isCurveTrustworthy(curved, start, end, imageWidth, imageHeight)) {
                        curved
                    } else {
                        listOf(start, end)
                    }
                }
            } else {
                null
            }

            val center = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
            val clippedPattern = pattern.copy(
                stars = usedOriginalIndices.map { pattern.stars[it] },
                edges = visibleEdges.map { edge ->
                    checkNotNull(indexMap[edge.first]) to checkNotNull(indexMap[edge.second])
                },
                mythStrokes = emptyList(),
            )
            AnnotationOverlay(
                id = -(catalogIndex + 1L),
                kind = OverlayKind.Constellation,
                center = center,
                size = Size(
                    width = (maxX - minX).coerceAtLeast(24f),
                    height = (maxY - minY).coerceAtLeast(24f),
                ),
                constellation = clippedPattern,
                anchorOverrides = points.mapIndexed { index, point -> index to point }.toMap(),
                edgePolylines = edgePolylines,
                seamAware = seamAwareProjection,
                colorArgb = colorArgb,
                strokeWidth = strokeWidth,
                anchorRadiusRatio = anchorRadiusRatio,
                lineStyle = lineStyle,
                opacity = opacity,
                showName = showNames,
                nameTextSize = nameTextSize,
                font = font,
                layer = AnnotationLayer.Constellation,
            )
        }
    }

    /**
     * Größte paarweise Winkel-Trennung (Grad) unter den gegebenen (Original-Index, Richtungsvektor)-
     * Paaren -- rein sphärisch, unabhängig von jeder Projektion. Grundlage für den Gesamt-Kohärenz-
     * Cull in createConstellationOverlays. Liefert zusätzlich die zwei beteiligten Original-Indizes
     * zurück (für die lokale Skalen-Sonde via estimatePixelsPerDegree).
     */
    private fun angularDiameter(dirs: List<Pair<Int, Vec3>>): Triple<Float, Int, Int> {
        var bestSep = 0f
        var bestA = dirs.firstOrNull()?.first ?: -1
        var bestB = bestA
        for (i in dirs.indices) {
            for (j in i + 1 until dirs.size) {
                val sep = Math.toDegrees(
                    acos(dirs[i].second.dot(dirs[j].second).coerceIn(-1.0, 1.0)),
                ).toFloat()
                if (sep > bestSep) {
                    bestSep = sep
                    bestA = dirs[i].first
                    bestB = dirs[j].first
                }
            }
        }
        return Triple(bestSep, bestA, bestB)
    }

    /**
     * Projiziert Deep-Sky-Objekte ins gelöste Bild und erzeugt typisierte Marker-Overlays:
     * Galaxien/Haufen/Sonstiges als Ellipse, Nebel als Rechteck, jeweils mit Namenslabel.
     * Die Overlay-IDs sind Platzhalter; der Aufrufer vergibt echte IDs beim Einfügen.
     */
    fun createDeepSkyOverlays(
        objects: List<DeepSkyObject>,
        solution: WcsSolutionLike,
        imageWidth: Int,
        imageHeight: Int,
        // Vordergrund-Maske (SolveMask, 1/SCALE-Auflösung) — Objekte auf übermaltem Vordergrund
        // werden verworfen, unabhängig davon, ob sie sonst gültig lösen.
        foregroundMask: Bitmap? = null,
        categories: Set<DeepSkyCategory>,
        // Helligkeits-BEREICH je Katalog (Unter-/Obergrenze). Default konservativ.
        catalogMagRange: (DeepSkyCatalogGroup) -> ClosedFloatingPointRange<Float> = { 0f..11f },
        catalogs: Set<DeepSkyCatalogGroup>? = null,
        strokeWidth: Float = 2.5f,
        // Deckkraft je Katalog (analog catalogMagRange) -- entweder EIN gemeinsamer Wert für alle
        // (Aufrufer liefert dann für jede Gruppe denselben Wert zurück) oder ein eigener je Katalog.
        opacityFor: (DeepSkyCatalogGroup) -> Float = { 0.9f },
        nameTextSize: Float = 30f,
        // TEMPORÄR deaktiviert (Nutzertest): faktisch unbegrenzt, damit die Bildmenge zu 100% über
        // den Helligkeits-Regler (Unter-/Obergrenze pro Katalog) gesteuert wird, statt zusätzlich
        // von diesem Deckel überstimmt zu werden. Ursprünglich 400 (s. Kommentar bei maxPerCatalog
        // für den Hintergrund) -- bei Bedarf einfach zurückstellen.
        maxObjects: Int = Int.MAX_VALUE,
        // TEMPORÄR deaktiviert (Nutzertest, s. maxObjects). Ursprünglich 200 als Deckel je Katalog
        // gegen Flutung durch einen einzelnen Katalog (unabhängig vom Helligkeits-Regler) -- bei
        // Bedarf einfach zurückstellen.
        maxPerCatalog: Int = Int.MAX_VALUE,
        // Schriftart der Objektnamen (UI-Auswahl). Default = SansSerif (Altverhalten).
        font: OverlayFont = OverlayFont.SansSerif,
        // Namen anzeigen? (UI-Schalter). Bei true werden überlappende Namen automatisch entstapelt.
        showNames: Boolean = true,
        // Vorberechnete Katalogzugehörigkeit (einmalig beim Laden bestimmt, s. StarMapperApp) --
        // vermeidet pro Aufruf tausendfaches Regex-Matching (DeepSkyCatalogGroup.of()) über den
        // gesamten Bestand. Fehlt ein Eintrag (z.B. in Tests ohne Cache), wird live nachgerechnet --
        // funktional identisch, nur langsamer.
        catalogGroups: Map<DeepSkyObject, DeepSkyCatalogGroup> = emptyMap(),
        // Per Objekt-Suche manuell ausgewählte Objekte (Objekt-`id`): übergehen Kategorie-, Katalog-
        // UND Helligkeits-Filter sowie die Kappungs-Deckel unten komplett -- der Nutzer hat sie
        // bewusst gesucht und ausgewählt, sie sollen unabhängig von den übrigen Reglern erscheinen.
        pinnedIds: Set<String> = emptySet(),
        // Manuell verändertes Größe/Rotation je Objekt (Objekt-`id`) -- übersteuert die aus Katalog/
        // Form-Fakten berechnete Größe, damit eine Nutzer-Bearbeitung eine Neusynchronisierung dieser
        // Ebene (Filter-/Reglerwechsel) übersteht statt verworfen zu werden.
        sizeOverrides: Map<String, DsoSizeOverride> = emptyMap(),
        // Globale, nutzerdefinierte Standardfarbe je DSO-Typ-Gruppe (Katalog bearbeiten -> Farben,
        // Nutzerwunsch 2026-08-20) -- überschreibt die eingebaute Farbe aus dsoStyleForType, wenn für
        // die jeweilige Gruppe gesetzt. Anders als sizeOverrides (pro Objekt-`id`) hier pro TYP-GRUPPE.
        dsoColorOverrides: Map<DsoColorGroup, Long> = emptyMap(),
        // App-eigene Form-Fakten (normalisierte Bezeichnung -> DsoShape): große Achse (Bogenmin), optional
        // kleine Achse + Positionswinkel. Mit min+pa -> orientierte Ellipse; nur maj -> Kreis in korrigierter
        // Größe; ohne Eintrag -> Kreis aus Katalog-`dim` (Altverhalten). Gemeinfreie Messfakten.
        shapes: Map<String, DsoShape> = emptyMap(),
        // Mindest-Renderdurchmesser als ANTEIL der kürzeren Bildseite (nicht Bild-px absolut!) --
        // Objekte darunter werden verworfen. Bewusst relativ statt absolut: eine feste Pixelzahl
        // würde je nach Sensorauflösung UND Sichtfeld/Brennweite (beides bereits über pixelsPerDegree
        // in majPxArr/radii eingerechnet) unterschiedlich aggressiv wirken -- ein Anteil der Bildseite
        // verhält sich dagegen unabhängig davon konsistent (spiegelt den technischen Boden aus
        // arcminToPx, ebenfalls ein Bildseiten-Anteil). 0 = aus (Altverhalten, nur der technische
        // Boden aus arcminToPx greift). Gepinnte Objekte sind ausgenommen.
        minRenderSizeFraction: Float = 0f,
        // Bereits belegte Namens-Bounding-Boxen anderer Ebenen (z.B. Sternbildnamen, Bild-px, je
        // [l, t, r, b]) -- werden wie bereits platzierte DSO-Namen als Hindernis behandelt, damit DSO-
        // Namen bekannten fremden Namen ausweichen, statt sie zu überlagern.
        externalObstacleBoxes: List<FloatArray> = emptyList(),
        // true = volle Namensplatzierung (Rotation + wachsende Führungslinie um Umrisse) — teuer, nur beim
        // Loslassen. false = günstige Basis-Platzierung (Live-Slider, kein Ruckeln).
        fullLabelPlacement: Boolean = true,
        // Sprache des Anzeigenamens (BCP-47-Kürzel, s. AppLocale.resolvedLanguageTag) -- steuert nur
        // [DeepSkyObject.properDisplayName], analog zu createStarOverlays' `lang`-Parameter.
        lang: String = "en",
        // Punkt 6 (Nutzer-Vorgabe 2026-08-30): koppelt Beschriftungs- an Objekt-Deckkraft, s.
        // AnnotationOverlay.nameOpacityLinked-KDoc. Default false = bisheriges Verhalten unverändert.
        nameOpacityLinked: Boolean = false,
        // Best Known V1 (Nutzer-Vorgabe 2026-09-01, s. BestKnownCatalog-KDoc): true = NUR Objekte
        // zeigen, die BestKnownCatalog.match() erfolgreich auf eines der 250 kuratierten Ziele abbildet
        // (gepinnte Objekte bleiben wie bei allen übrigen Filtern ausgenommen), UND deren Anzeigename
        // (sofern in der Tabelle vorhanden) durch den Best-Known-Namen ersetzen. Default false =
        // bisheriges Verhalten (kompletter Katalog, Katalogname) unverändert.
        bestKnownEnabled: Boolean = false,
        // WCS-basierter Mindestgrößen-Filter (Abschnitt I-P der Best-Known-Vorgabe): `null` = "Alle
        // Größen" (Filter AUS, Altverhalten -- nur der bereits bestehende minRenderSizeFraction/
        // arcminToPx-Boden greift weiterhin). Nicht-null = Prozentanteil der kürzeren Bildseite, gegen
        // die natürliche projizierte Größe (VOR sizeOverrides, VOR arcminToPx-Fallback) geprüft --
        // bewusst eine ANDERE, frühere Prüfung als minRenderSizeFraction (die bleibt unverändert
        // bestehen, wirkt aber danach auf die ggf. schon override-/fallback-beeinflusste Größe, s.
        // dortigen Parameter-Kommentar). Objekte OHNE bekannte natürliche Winkelausdehnung werden bei
        // aktivem Filter verworfen (sizeKnown=false darf NIE die minRenderSizeFraction-Ersatzgröße als
        // Beweis für "groß genug" verwenden, s. Abschnitt M) -- außer sie sind gepinnt.
        minNaturalSizePercent: Float? = null,
        // Performance-Fix 2026-09-02: einmalig beim Katalog-Laden vorberechnete Best-Known-Zuordnung
        // (Objekt -> Treffer oder null), analog zu catalogGroups oben -- vermeidet, dass BEI JEDER
        // Neuberechnung (jeder Slider-Commit im Objekte-Menü) erneut bis zu ~365.000 normalisierte
        // Vergleiche über den kompletten Katalog laufen. `null` (Default, z.B. in Tests ohne Cache) fällt
        // auf die direkte, weiterhin korrekte BestKnownCatalog.match()-Berechnung pro Objekt zurück --
        // funktional identisch, nur langsamer.
        bestKnownMatches: Map<DeepSkyObject, BestKnownEntry?>? = null,
    ): List<AnnotationOverlay> {
        if (pinnedIds.isEmpty() && (categories.isEmpty() || catalogs?.isEmpty() == true)) return emptyList()
        val groupOf: (DeepSkyObject) -> DeepSkyCatalogGroup = { catalogGroups[it] ?: DeepSkyCatalogGroup.of(it) }
        val bestKnownOf: (DeepSkyObject) -> BestKnownEntry? = { bestKnownMatches?.get(it) ?: BestKnownCatalog.match(it) }
        // Diagnose-Zähler (Abschnitt W) -- unabhängig von Sichtbarkeits-/Bildfeld-Filtern: wie viele der
        // 250 Best-Known-Ziele lassen sich im GELADENEN Katalog überhaupt auflösen (unabhängig von
        // Kategorie/Katalog/Helligkeit/Bildausschnitt dieses konkreten Fotos). Performance-Fix: liest den
        // ggf. bereits vorberechneten bestKnownMatches-Cache statt einer ZWEITEN vollen match()-Passage
        // über den Katalog (vorher redundant zum Filter direkt darunter).
        // Zusätzlich zur bereits vorhandenen OBJEKT-Zählung: welche BestKnownEntry-ZEILEN wurden im
        // Katalog überhaupt gefunden (Nutzer-Vorgabe 2026-09-04)? Wird im SELBEN Durchlauf mitgesammelt
        // -- kein zweiter Scan über die ~91k Objekte (der Best-Known-Abgleich war bereits einmal ein
        // Performance-Thema, s. normalizeId-Kommentar oben).
        val bestKnownMatchedEntries = HashSet<BestKnownEntry>()
        val bestKnownMatchedInCatalog = if (bestKnownEnabled) {
            objects.count { o -> bestKnownOf(o)?.also { bestKnownMatchedEntries += it } != null }
        } else {
            0
        }
        var naturalSizeKnownCount = 0
        var naturalSizeRejectedCount = 0
        val projected = objects.asSequence()
            .filter { it.id in pinnedIds || DeepSkyCategory.fromType(it.type) in categories }
            .filter { it.id in pinnedIds || catalogs == null || groupOf(it) in catalogs }
            // Best Known V1 (Abschnitt H): schaltet KEINE anderen Filter ab -- läuft als zusätzlicher,
            // unabhängiger Filter in derselben Kette. Reine Identitätsprüfung, keine Positionsheuristik
            // (BestKnownCatalog.match(), s. dortiger KDoc).
            .filter { it.id in pinnedIds || !bestKnownEnabled || bestKnownOf(it) != null }
            .filter { deepSky ->
                // Gepinnt: keine Helligkeitsprüfung. Sonst Bereich je Katalog; Objekte ohne
                // Helligkeit passieren immer (über Größe gezeichnet, Running-Man-Altverhalten).
                deepSky.id in pinnedIds ||
                    deepSky.magnitude == null ||
                    deepSky.magnitude in catalogMagRange(groupOf(deepSky))
            }
            .mapNotNull { deepSky ->
                // Bewusst KEIN separates Anker-/Kachel-Abstandsgate mehr (bis 0.23.x: isClaimedByAnyTile +
                // isNearAnyAnchor, 20°-Marge) -- analog zur bereits für Sternbilder entfernten "virtuellen
                // Mauer" (s. createConstellationOverlays): ein Objekt knapp außerhalb dieser Marge wurde
                // komplett verworfen, auch wenn Bildrand-Prüfung + Maske direkt darunter eine plausible,
                // im Bild liegende Position lieferten (Nutzerbefund 2026-08-17: M31 blieb trotz jeder
                // Helligkeits-/Schwellwert-Einstellung unsichtbar, weil es außerhalb dieser Marge lag).
                // Bildrand-Prüfung + Vordergrundmaske direkt darunter bleiben die Schutzschicht.
                val point = solution.skyToImage(deepSky.point, imageHeight) ?: return@mapNotNull null
                if (point.x < 0f || point.x > imageWidth || point.y < 0f || point.y > imageHeight) {
                    return@mapNotNull null
                }
                if (foregroundMask != null && SolveMask.isMasked(foregroundMask, point)) {
                    return@mapNotNull null
                }
                deepSky to point
            }
            // Hellste zuerst; unbekannte Helligkeit ans Ende, aber nicht verwerfen.
            .sortedBy { (deepSky, _) -> deepSky.magnitude ?: UNKNOWN_MAGNITUDE_SORT }
            .toList()

        // Deckel je Katalog gegen Flutung, dann Gesamtdeckel (Reihenfolge = hellste zuerst). Gepinnte
        // Objekte sind von beiden Deckeln ausgenommen (immer aufgenommen, s. Parameter-Kommentar).
        val perCatalogCount = HashMap<DeepSkyCatalogGroup, Int>()
        var candidates = ArrayList<Pair<DeepSkyObject, Offset>>(min(projected.size, maxObjects))
        for (entry in projected) {
            if (entry.first.id in pinnedIds) {
                candidates += entry
                continue
            }
            if (candidates.size >= maxObjects) continue
            val group = groupOf(entry.first)
            val n = perCatalogCount[group] ?: 0
            if (n >= maxPerCatalog) continue
            perCatalogCount[group] = n + 1
            candidates += entry
        }

        // Form je Kandidat vorab: Ellipse (maj×min + PA aus Form-Fakten) oder Kreis (nur maj bzw. Katalog-`dim`).
        // `radii` = UMSCHLIESSENDER Radius (max(majPx,minPx)/2) für Callout/Hindernis-Prüfung + "Name außerhalb".
        var majPxArr = FloatArray(candidates.size)
        var minPxArr = FloatArray(candidates.size)
        var rotArr = FloatArray(candidates.size)
        var radii = FloatArray(candidates.size)
        // sizeKnown = ein echtes Winkelmaß (Form-Fakten ODER Katalog-`dim`) konnte tatsächlich projiziert
        // werden -- unterscheidet eine ECHTE natürliche Größe vom arcminToPx(null,...)-Anzeige-Platzhalter
        // (Abschnitt M: "keine geratenen Größen", der Platzhalter darf NIE als Beweis für "groß genug"
        // gelten). Nur für den neuen minNaturalSizePercent-Filter unten gebraucht.
        val sizeKnownArr = BooleanArray(candidates.size)
        for (i in candidates.indices) {
            val ds = candidates[i].first
            val shape = shapes[normalizeDesignation(ds.name)] ?: shapes[normalizeDesignation(ds.id)]
            val majArcmin = shape?.majArcmin ?: ds.majorAxisArcmin
            val ellipse = majArcmin?.takeIf { it > 0f }?.let { maj ->
                val minArcmin = shape?.minArcmin?.takeIf { shape.posAngleDeg != null }
                projectedEllipseAxes(
                    point = candidates[i].second,
                    sky = ds.point,
                    majArcmin = maj,
                    minArcmin = minArcmin ?: maj,
                    paDeg = if (minArcmin != null) shape?.posAngleDeg ?: 0f else 0f,
                    solution = solution,
                    imageHeight = imageHeight,
                )
            }
            if (ellipse != null) {
                majPxArr[i] = ellipse.first
                minPxArr[i] = ellipse.second
                rotArr[i] = ellipse.third
                sizeKnownArr[i] = true
            } else {
                // Kein Winkelmaß bekannt (weder Form-Fakten noch Katalog-`dim`) ODER Nord-/Ost-
                // Abtastung fehlgeschlagen (z.B. Projektions-Polstelle) -> fester Anzeige-Kreis, wie
                // der bisherige arcminToPx-Fallback (dessen Formel unabhängig von pixelsPerDegree ist).
                val fallback = arcminToPx(null, 0f, imageWidth, imageHeight)
                majPxArr[i] = fallback
                minPxArr[i] = fallback
                rotArr[i] = 0f
                sizeKnownArr[i] = false
            }
            radii[i] = max(majPxArr[i], minPxArr[i]) / 2f
        }

        // WCS-basierter Mindestgrößen-Filter (Abschnitt I-P): NATÜRLICHE projizierte Größe (majPxArr an
        // dieser Stelle, VOR sizeOverrides/minRenderSizeFraction unten) gegen einen Prozentanteil der
        // kürzeren Bildseite geprüft -- reines Filterkriterium, beeinflusst die spätere Render-Größe
        // selbst nicht (die bleibt Sache von sizeOverrides/minRenderSizeFraction danach, unverändert).
        if (minNaturalSizePercent != null && minNaturalSizePercent > 0f) {
            val thresholdPx = minNaturalSizePercent / 100f * min(imageWidth, imageHeight)
            naturalSizeKnownCount = sizeKnownArr.count { it }
            val keepIdx = candidates.indices.filter { i ->
                candidates[i].first.id in pinnedIds || (sizeKnownArr[i] && majPxArr[i] >= thresholdPx)
            }
            naturalSizeRejectedCount = candidates.size - keepIdx.size
            if (keepIdx.size != candidates.size) {
                val filteredCandidates = ArrayList<Pair<DeepSkyObject, Offset>>(keepIdx.size)
                val filteredMajPx = FloatArray(keepIdx.size)
                val filteredMinPx = FloatArray(keepIdx.size)
                val filteredRot = FloatArray(keepIdx.size)
                val filteredRadii = FloatArray(keepIdx.size)
                keepIdx.forEachIndexed { newI, oldI ->
                    filteredCandidates += candidates[oldI]
                    filteredMajPx[newI] = majPxArr[oldI]
                    filteredMinPx[newI] = minPxArr[oldI]
                    filteredRot[newI] = rotArr[oldI]
                    filteredRadii[newI] = radii[oldI]
                }
                candidates = filteredCandidates
                majPxArr = filteredMajPx
                minPxArr = filteredMinPx
                rotArr = filteredRot
                radii = filteredRadii
            }
        }

        // Nutzer-Override (manuell verändertes Overlay) übersteuert die Katalog-/Form-Fakten-Größe --
        // sonst würde jede Neusynchronisierung (Filter-/Reglerwechsel, s. syncDeepSkyLayer) eine manuelle
        // Größen-/Rotationsänderung wieder verwerfen (s. Parameter-Kommentar sizeOverrides).
        for (i in candidates.indices) {
            val override = sizeOverrides[candidates[i].first.id] ?: continue
            majPxArr[i] = override.size.width
            minPxArr[i] = override.size.height
            rotArr[i] = override.rotationDegrees
            radii[i] = max(majPxArr[i], minPxArr[i]) / 2f
        }

        // Zu klein, um im Bild als Form erkennbar zu sein (kaum von einem Punkt/Stern unterscheidbar)
        // -- individuell je Foto, da radii bereits aus DIESEM Bild-Maßstab stammen. Gepinnte Objekte
        // sind ausgenommen (wie bei allen anderen Filtern/Deckeln oben).
        if (minRenderSizeFraction > 0f) {
            val minRenderSizePx = minRenderSizeFraction * min(imageWidth, imageHeight)
            val keepIdx = candidates.indices.filter { i ->
                candidates[i].first.id in pinnedIds || radii[i] * 2f >= minRenderSizePx
            }
            if (keepIdx.size != candidates.size) {
                val filteredCandidates = ArrayList<Pair<DeepSkyObject, Offset>>(keepIdx.size)
                val filteredMajPx = FloatArray(keepIdx.size)
                val filteredMinPx = FloatArray(keepIdx.size)
                val filteredRot = FloatArray(keepIdx.size)
                val filteredRadii = FloatArray(keepIdx.size)
                keepIdx.forEachIndexed { newI, oldI ->
                    filteredCandidates += candidates[oldI]
                    filteredMajPx[newI] = majPxArr[oldI]
                    filteredMinPx[newI] = minPxArr[oldI]
                    filteredRot[newI] = rotArr[oldI]
                    filteredRadii[newI] = radii[oldI]
                }
                candidates = filteredCandidates
                majPxArr = filteredMajPx
                minPxArr = filteredMinPx
                rotArr = filteredRot
                radii = filteredRadii
            }
        }
        // Alle sichtbaren Marker als Hindernis-Kreise (Linie + Name sollen sie nicht kreuzen).
        val obsX = FloatArray(candidates.size) { candidates[it].second.x }
        val obsY = FloatArray(candidates.size) { candidates[it].second.y }
        var maxObsR = 0f
        for (rr0 in radii) if (rr0 > maxObsR) maxObsR = rr0

        // Callout-Namen RUNDUM platzieren: pro Objekt die Richtung mit dem KÜRZESTEN kollisionsfreien Weg
        // wählen. Führungslinie + Name dürfen keine fremden Objektkreise/Labels kreuzen (nur der eigene,
        // enthaltende Kreis darf zum Herauswachsen durchquert werden). Live-Drag: günstige Basis-Platzierung.
        val candAngles = floatArrayOf(0f, -45f, 45f, -90f, 90f, 180f, -135f, 135f)
        // Echte gemessene Textbreite statt der früheren Zeichenzahl-Schätzung (label.length*0.55f) --
        // letztere konnte je nach Name (schmale/breite Zeichen) spürbar daneben liegen und eine an der
        // Suche "kollisionsfrei" geprüfte Position beim tatsächlichen Zeichnen doch wieder überlappen
        // lassen (Nutzerbefund 2026-08-30). EIN Paint für alle Objekte -- Font/Fett/Größe sind für den
        // ganzen Aufruf konstant (DSO-Namen sind immer fett, s. ExportRenderer.drawMarkerName/
        // SphericalOverlayRenderer, deren nameBold für layer!=null immer true ist).
        val measurePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font.toTypeface(bold = true)
            textSize = (nameTextSize * font.sizeScale()).coerceIn(14f, 120f)
        }
        // Mit fremden Namens-Boxen (z.B. Sternbildnamen) vorbelegt, s. externalObstacleBoxes-Kommentar.
        val placedBoxes = ArrayList<FloatArray>(externalObstacleBoxes.size).apply { addAll(externalObstacleBoxes) } // je [l, t, r, b] in Bild-px
        val result = candidates.mapIndexed { index, (deepSky, point) ->
            // Umschließender Durchmesser (große Achse) für die Muster-Sichtbarkeitsprüfung; die eigentliche
            // Form (Kreis oder orientierte Ellipse) steckt in majPxArr/minPxArr/rotArr.
            val diameterPx = radii[index] * 2f
            val style = dsoStyleForType(deepSky.type, dsoColorOverrides)
            // Linienstil je Typ; zu kleine/kurze Form (kein sichtbares Muster) -> Vollstrich.
            val renderStroke = (strokeWidth * 2f).coerceAtLeast(0.3f) // spiegelt OverlayGeometry.strokeWidth
            val dashPeriod = renderStroke * 6.4f                     // on(*4) + off(*2.4)
            val effectiveStyle = if (style.lineStyle != OverlayLineStyle.Solid &&
                diameterPx * 3.14159f < 6f * dashPeriod
            ) OverlayLineStyle.Solid else style.lineStyle
            // Best Known V1 (Abschnitt D/T): ändert AUSSCHLIESSLICH diesen lokalen Anzeigenamen -- kein
            // Feld an [deepSky] selbst wird überschrieben. Fällt zurück auf den bereits bestehenden
            // Populärname-Mechanismus (z.B. "Andromeda Galaxy" statt "M 31" aus dso_names.json), wenn
            // Best Known aus ist ODER dieses konkrete Objekt keinen eigenen Best-Known-Namen hat.
            val bestKnownName = if (bestKnownEnabled) {
                bestKnownOf(deepSky)?.let { BestKnownCatalog.resolveDisplayName(it, lang) }
            } else {
                null
            }
            val label = bestKnownName ?: deepSky.properDisplayName(lang)

            var labelAngle = 0f
            var labelLeaderPx = 0f
            var showThisName = false
            val dsoOverride = sizeOverrides[deepSky.id]
            if (showNames && label.isNotBlank()) {
                val effSize = (nameTextSize * font.sizeScale()).coerceIn(14f, 120f)
                val r = radii[index]
                val baseLeader = max(effSize * 0.5f, r * 0.15f)
                val gap = OverlayGeometry.MARKER_NAME_GAP
                if (dsoOverride != null) {
                    // Manuell verschobene Beschriftung übersteht Neusynchronisierung (Nutzerbefund
                    // 2026-08-27) -- keine erneute automatische Platzierungssuche, Position bleibt exakt
                    // wie vom Nutzer gesetzt. Trotzdem als Hindernis eintragen (s. placedBoxes unten),
                    // damit andere, noch automatisch zu platzierende Namen ihr ausweichen.
                    showThisName = true
                    labelAngle = dsoOverride.labelAngleDeg
                    labelLeaderPx = dsoOverride.labelLeaderPx
                    val textW = measurePaint.measureText(label)
                    val textH = effSize * 1.1f
                    val th = Math.toRadians(labelAngle.toDouble())
                    val dx = cos(th).toFloat(); val dy = sin(th).toFloat()
                    // Echter, RICHTUNGSABHÄNGIGER Formrand (Ellipse inkl. Rotation) statt der
                    // umschließenden Kreisnäherung radii[index] -- exakt dieselbe Formel wie
                    // OverlayGeometry.markerLabelLayout beim tatsächlichen Zeichnen (boundaryRadiusFor
                    // ist deren gemeinsamer Kern). Ohne das verifizierte die Hindernis-Box hier eine
                    // ANDERE Stelle als die später wirklich gezeichnete (Nutzerbefund 2026-08-30: Labels
                    // lagen teils im eigenen Objekt/schnitten dessen Kontur).
                    val ownBoundary = OverlayGeometry.boundaryRadiusFor(majPxArr[index], minPxArr[index], rotArr[index], false, dx, dy)
                    val dist = ownBoundary + labelLeaderPx + gap
                    val ax = point.x + dist * dx
                    val ay = point.y + dist * dy
                    val l = when {
                        dx > 0.35f -> ax
                        dx < -0.35f -> ax - textW
                        else -> ax - textW / 2f
                    }
                    placedBoxes += floatArrayOf(l, ay - textH / 2f, l + textW, ay + textH / 2f)
                } else if (!fullLabelPlacement) {
                    // Live-Drag: günstige Basis-Platzierung (kein O(n^2), keine Kollisionsprüfung) -> kein Ruckeln.
                    showThisName = true
                    labelAngle = 0f
                    labelLeaderPx = baseLeader
                } else {
                    val textW = measurePaint.measureText(label)
                    val textH = effSize * 1.1f
                    val step = max(textH, 8f)
                    // r (umschließender Radius) bleibt hier NUR als großzügige, sichere Obergrenze für den
                    // Such-Deckel -- die eigentliche Platzierung unten nutzt pro Kandidatenwinkel den
                    // echten, richtungsabhängigen Formrand (s. Kommentar im dsoOverride-Zweig oben).
                    val maxDist = r + baseLeader + gap + maxObsR + textW + textH
                    var bestAngle = 0f
                    var bestDist = Float.MAX_VALUE
                    for (angle in candAngles) {
                        val th = Math.toRadians(angle.toDouble())
                        val dx = cos(th).toFloat()
                        val dy = sin(th).toFloat()
                        val ownBoundary = OverlayGeometry.boundaryRadiusFor(majPxArr[index], minPxArr[index], rotArr[index], false, dx, dy)
                        val baseDist = ownBoundary + baseLeader + gap
                        var dist = baseDist
                        while (dist <= maxDist) {
                            val ax = point.x + dist * dx
                            val ay = point.y + dist * dy
                            val l = when {
                                dx > 0.35f -> ax
                                dx < -0.35f -> ax - textW
                                else -> ax - textW / 2f
                            }
                            val rBox = l + textW
                            val tBox = ay - textH / 2f
                            val bBox = ay + textH / 2f
                            val edgeX = point.x + ownBoundary * dx
                            val edgeY = point.y + ownBoundary * dy
                            val lineEndX = point.x + (dist - gap) * dx
                            val lineEndY = point.y + (dist - gap) * dy
                            // Namens-Box muss VOLLSTÄNDIG im Bild liegen -- ohne das fand die Suche bei
                            // Objekten nahe dem Bildrand (kaum andere Objekte als Hindernis) anstandslos
                            // eine "kollisionsfreie", aber tatsächlich unsichtbare Stelle außerhalb des
                            // Bildes (Nutzerbefund 2026-08-27).
                            var blocked = l < 0f || rBox > imageWidth || tBox < 0f || bBox > imageHeight
                            var j = 0
                            while (!blocked && j < candidates.size) {
                                if (j != index) {
                                    val cxj = obsX[j]; val cyj = obsY[j]; val rj = radii[j]
                                    // Liegt UNSER Objekt selbst innerhalb von Kreis j (z.B. kleines Objekt in
                                    // einem sehr großen)? Dann darf dessen Beschriftung den umschließenden
                                    // Kreis j für freien Innenraum nutzen -- weder als Box- noch als
                                    // Linien-Hindernis -- statt zwingend über dessen Rand hinauszuwandern.
                                    val dcx = cxj - point.x; val dcy = cyj - point.y
                                    val inside = dcx * dcx + dcy * dcy < rj * rj
                                    // Name muss außerhalb JEDES fremden Kreises liegen (außer wir liegen selbst darin).
                                    if (!inside && rectCircleHit(l, tBox, rBox, bBox, cxj, cyj, rj)) { blocked = true; break }
                                    // Linie darf fremde Kreise nicht kreuzen (außer wir liegen selbst darin).
                                    if (!inside && segmentCircleHit(edgeX, edgeY, lineEndX, lineEndY, cxj, cyj, rj)) {
                                        blocked = true; break
                                    }
                                }
                                j++
                            }
                            if (!blocked && placedBoxes.none { it[0] < rBox && l < it[2] && it[1] < bBox && tBox < it[3] }) {
                                break // kleinster freier dist für diesen Winkel
                            }
                            dist += step
                        }
                        if (dist <= maxDist && dist < bestDist) {
                            bestDist = dist
                            bestAngle = angle
                        }
                    }
                    if (bestDist <= maxDist) {
                        val th = Math.toRadians(bestAngle.toDouble())
                        val dx = cos(th).toFloat()
                        val dy = sin(th).toFloat()
                        val ax = point.x + bestDist * dx
                        val ay = point.y + bestDist * dy
                        val l = when {
                            dx > 0.35f -> ax
                            dx < -0.35f -> ax - textW
                            else -> ax - textW / 2f
                        }
                        placedBoxes += floatArrayOf(l, ay - textH / 2f, l + textW, ay + textH / 2f)
                        labelAngle = bestAngle
                        // Konsistent mit dem echten Formrand in GENAU dieser Richtung (identisch zu dem,
                        // was oben im Such-Loop für denselben bestAngle bereits berechnet wurde) --
                        // dieser Wert (nicht mehr r) ist es, den OverlayGeometry.markerLabelLayout beim
                        // Zeichnen als Ausgangspunkt für die Führungslinie nimmt.
                        val finalBoundary = OverlayGeometry.boundaryRadiusFor(majPxArr[index], minPxArr[index], rotArr[index], false, dx, dy)
                        labelLeaderPx = bestDist - finalBoundary - gap
                        showThisName = true
                    }
                }
            }
            AnnotationOverlay(
                id = -(DEEP_SKY_ID_OFFSET + index),
                kind = OverlayKind.Ellipse,
                center = point,
                // Große Achse entlang X, kleine entlang Y; rotationDegrees dreht in Bildlage (PA). Kreis: maj==min, rot=0.
                size = Size(majPxArr[index], minPxArr[index]),
                rotationDegrees = rotArr[index],
                text = label,
                colorArgb = style.colorArgb,
                strokeWidth = strokeWidth,
                lineStyle = effectiveStyle,
                opacity = opacityFor(groupOf(deepSky)),
                showName = showThisName,
                nameTextSize = nameTextSize,
                // Name behaelt die Marker-Farbe (Altverhalten vor nameColorArgb/0.24.x) -- der neue,
                // separat waehlbare Namensfarben-Regler ist nur fuer nutzerplatzierte Formen gedacht.
                nameColorArgb = style.colorArgb,
                labelAngleDeg = labelAngle,
                labelLeaderPx = labelLeaderPx,
                font = font,
                nameOpacityLinked = nameOpacityLinked,
                layer = AnnotationLayer.DeepSky,
                sourceId = deepSky.id,
            )
        }
        // Best Known V1 (Abschnitt W) -- rein additive, kompakte Diagnosezeile. sizeFilterMode=all,
        // wenn minNaturalSizePercent null/0 ist (Abschnitt N: "Alle Größen" = Filter komplett aus) --
        // dann bleiben naturalSizeKnown/naturalSizeRejected bei 0 (Filter lief nie, keine Ablehnung).
        val sizeFilterActive = minNaturalSizePercent != null && minNaturalSizePercent > 0f
        val sizeFilterModeText = if (sizeFilterActive) "percent" else "all"
        val minObjectSizePercentText = minNaturalSizePercent?.let { "%.1f".format(it) } ?: "n/a"
        val minObjectSizePxText = if (sizeFilterActive) {
            "%.1f".format(minNaturalSizePercent!! / 100f * min(imageWidth, imageHeight))
        } else {
            "n/a"
        }
        if (bestKnownEnabled) {
            val unmatched = BestKnownCatalog.entries.filter { it !in bestKnownMatchedEntries }
            AppDiagnostics.record(
                "bestknown_catalog_audit entries=${BestKnownCatalog.entries.size} " +
                    "matchedEntries=${bestKnownMatchedEntries.size} " +
                    "unmatchedEntries=${unmatched.size} " +
                    "unmatched=${
                        if (unmatched.isEmpty()) "none" else unmatched.joinToString("|") { it.preferredCanonicalId }
                    }",
            )
        }
        AppDiagnostics.record(
            "deepsky_best_known bestKnownEnabled=$bestKnownEnabled " +
                "bestKnownMatchedInCatalog=$bestKnownMatchedInCatalog " +
                "sizeFilterMode=$sizeFilterModeText minObjectSizePercent=$minObjectSizePercentText " +
                "minObjectSizePx=$minObjectSizePxText " +
                "naturalSizeKnown=$naturalSizeKnownCount naturalSizeRejected=$naturalSizeRejectedCount " +
                "visibleCount=${result.size}",
        )
        return result
    }

    /**
     * Projiziert Katalogsterne ins gelöste Bild. Drei unabhängige Quellen, dedupliziert:
     * - [includeNamed]: Sterne mit Eigennamen (Rigel, Wega ...), beschriftet.
     * - [includeConstellation]: Sterne, die zu Sternbildlinien gehören.
     * - [allToMagnitude]: alle Katalogsterne bis zu dieser Grenzgröße (null = aus).
     */
    fun createStarOverlays(
        catalogStars: List<CatalogStar>,
        constellationPatterns: List<ConstellationPattern>,
        solution: WcsSolutionLike,
        imageWidth: Int,
        imageHeight: Int,
        // Vordergrund-Maske (SolveMask, 1/SCALE-Auflösung) — Sterne auf übermaltem Vordergrund
        // werden verworfen, unabhängig davon, ob sie sonst gültig lösen.
        foregroundMask: Bitmap? = null,
        includeNamed: Boolean,
        includeConstellation: Boolean,
        allToMagnitude: Float?,
        strokeWidth: Float = 2f,
        nameTextSize: Float = 26f,
        // Cap hoch genug für gute Abdeckung bei hoher Magnitude-Schwelle, aber gedeckelt für
        // Performance. Sortierung ist nach Helligkeit -> die hellsten Sterne bleiben IMMER erhalten,
        // werden also nie vom Cap weggeschnitten (nur die schwächsten fallen am Limit weg).
        maxStars: Int = 600,
        // Deckkraft der Sternnamen/-punkte (UI-Regler). Wird in den Overlay-Alpha gebacken, damit
        // das Zeichnen unverändert performant bleibt (keine Render-Zeit-Sonderlogik nötig).
        nameOpacity: Float = 0.95f,
        // Globale Stern-Beschriftung (0.11.0): Farbe + Schriftart der Sternnamen/-punkte.
        nameColorArgb: Long = STAR_MARKER_COLOR,
        font: OverlayFont = OverlayFont.SansSerif,
        // Punkte auf den Sternen zeichnen? false = nur Namen (UI-Toggle).
        showDots: Boolean = true,
        // Sprache der Sternnamen (BCP-47-Kürzel, s. AppLocale.resolvedLanguageTag).
        lang: String = "en",
    ): List<AnnotationOverlay> {
        if (!includeNamed && !includeConstellation && allToMagnitude == null) return emptyList()
        val minSide = min(imageWidth, imageHeight)

        data class StarPick(
            val point: SkyPoint,
            val magnitude: Float?,
            val label: String,
            val isConstellation: Boolean,
        )

        val picks = mutableListOf<StarPick>()
        if (includeNamed) {
            catalogStars.asSequence()
                .filter { it.properName.isNotBlank() }
                .forEach { picks += StarPick(it.point, it.magnitude, it.properDisplayName(lang), false) }
        }
        if (allToMagnitude != null) {
            catalogStars.asSequence()
                .filter { it.magnitude <= allToMagnitude }
                .forEach {
                    // Nutzerwunsch: JEDER Stern hier bekommt eine Beschriftung, nicht nur die mit
                    // echtem Eigennamen — displayName() fällt für unbenannte Sterne auf ihre
                    // Katalogbezeichnung zurück (Bayer/Flamsteed/Variable/Designation/HIP, siehe
                    // StarCatalogAssetLoader.starDisplayLabel). Nur Sterne ganz ohne jeden Katalog-
                    // Eintrag (kein starnames.json-Treffer) bleiben unbeschriftete Punkte.
                    picks += StarPick(it.point, it.magnitude, it.displayName(lang), false)
                }
        }
        if (includeConstellation) {
            constellationPatterns.asSequence()
                .flatMap { it.stars.asSequence() }
                .forEach {
                    picks += StarPick(
                        SkyPoint(it.raHours * 15f, it.decDegrees),
                        null,
                        // Sternbildsterne sind ringlos -> der Name IST ihre Darstellung; daher
                        // immer mit Namen (sonst werden sie ohne "benannte Sterne" unsichtbar).
                        it.name,
                        true,
                    )
                }
        }

        // Räumliche Dedup über die PROJIZIERTEN Pixel: derselbe Stern hat je nach Datenquelle
        // (Katalog vs. Sternbild-Pattern) leicht abweichende RA/Dec -> Pixel-Nähe statt
        // Koord-Schlüssel. Beim Verschmelzen gewinnt ein Label; isConstellation = ODER
        // (ein Sternbildstern wird ringlos gezeichnet, nur der Ankerring repräsentiert ihn).
        class Merged(val point: Offset, var magnitude: Float?, var label: String, var isConstellation: Boolean)
        val mergeRadius = (minSide * 0.006f).coerceAtLeast(6f)
        val cell = mergeRadius.toDouble()
        val grid = HashMap<Long, MutableList<Int>>()
        val merged = ArrayList<Merged>()
        fun cellKey(gx: Int, gy: Int): Long = (gx.toLong() shl 32) xor (gy.toLong() and 0xffffffffL)
        for (pick in picks) {
            // Bewusst KEIN separates Anker-/Kachel-Abstandsgate mehr -- analog zu createDeepSkyOverlays
            // und der bereits für Sternbilder entfernten "virtuellen Mauer" (s. createConstellationOverlays).
            // Bildrand-Prüfung + Vordergrundmaske direkt darunter bleiben die Schutzschicht.
            val point = solution.skyToImage(pick.point, imageHeight) ?: continue
            if (point.x < 0f || point.x > imageWidth || point.y < 0f || point.y > imageHeight) continue
            if (foregroundMask != null && SolveMask.isMasked(foregroundMask, point)) continue
            val gx = floor(point.x / cell).toInt()
            val gy = floor(point.y / cell).toInt()
            var hitIdx = -1
            for (dgx in -1..1) {
                for (dgy in -1..1) {
                    grid[cellKey(gx + dgx, gy + dgy)]?.forEach { idx ->
                        val m = merged[idx]
                        val dx = m.point.x - point.x
                        val dy = m.point.y - point.y
                        if (hitIdx < 0 && dx * dx + dy * dy < mergeRadius * mergeRadius) hitIdx = idx
                    }
                }
            }
            if (hitIdx >= 0) {
                val m = merged[hitIdx]
                if (m.label.isBlank() && pick.label.isNotBlank()) m.label = pick.label
                if (m.magnitude == null) m.magnitude = pick.magnitude
                m.isConstellation = m.isConstellation || pick.isConstellation
            } else {
                merged += Merged(point, pick.magnitude, pick.label, pick.isConstellation)
                grid.getOrPut(cellKey(gx, gy)) { mutableListOf() }.add(merged.lastIndex)
            }
        }

        // Beim Kappen (maxStars) benannte/Sternbild-Sterne bevorzugen: ihnen fehlt oft eine
        // Katalog-Magnitude (null) -> sonst würden sie hinter "alle Sterne bis Magnitude"
        // wegsortiert und verschwinden, sobald man zusätzlich "alle Sterne" einschaltet.
        val ordered = merged.sortedBy {
            it.magnitude ?: if (it.isConstellation || it.label.isNotBlank()) 2.5f else UNKNOWN_MAGNITUDE_SORT
        }.take(maxStars)

        return ordered.mapIndexed { index, m ->
            val brightness = m.magnitude?.let { ((6.5f - it) / 8f).coerceIn(0.18f, 1f) } ?: 0.6f
            val radius = (minSide * 0.004f * (0.6f + brightness)).coerceAtLeast(minSide * 0.003f)
            AnnotationOverlay(
                id = -(STAR_ID_OFFSET + index),
                kind = OverlayKind.Ellipse,
                center = m.point,
                size = Size(radius * 2f, radius * 2f),
                text = m.label,
                colorArgb = nameColorArgb,
                strokeWidth = strokeWidth,
                lineStyle = OverlayLineStyle.Solid,
                opacity = nameOpacity.coerceIn(0.05f, 1f),
                showName = m.label.isNotBlank(),
                nameTextSize = nameTextSize,
                font = font,
                markerDot = showDots,
                layer = AnnotationLayer.Star,
                // ALLE Sterne sind ringlos (Nutzerwunsch 0.9.7): Darstellung = kleiner gefüllter Punkt
                // (im Renderer/Export für die Star-Ebene gezeichnet) + optionaler Name. Kein Kreis.
                markerRing = false,
            )
        }
    }

    /**
     * Echte, pro Sternbild gemessene Platzierungsgenauigkeit gegen tatsächlich erkannte, bereits
     * cross-gematchte Sterne (z.B. aus FisheyeRefiner.crossMatchSolution) — statt einer einzigen
     * aggregierten RMS-Zahl übers ganze Bild. Ein Sternbild taucht im Ergebnis nur auf, wenn
     * mindestens einer seiner Muster-Sterne unter [matches] einen winkelnah (s. [matchToleranceDeg])
     * übereinstimmenden echten Treffer hat — für alle anderen gibt es (noch) keine unabhängige
     * Messung. Winkel-Nähe statt Namensvergleich, weil [matches] aus dem hellen Referenzkatalog
     * stammt und [constellations] aus einer komplett anderen, für die Linienzeichnung kuratierten
     * Datenquelle (d3-celestial) — derselbe physische Stern kann dort unterschiedlich heißen.
     */
    fun evaluateConstellationAccuracy(
        constellations: List<ConstellationPattern>,
        matches: List<Pair<Vec3, Offset>>,
        solution: WcsSolutionLike,
        imageHeight: Int,
        matchToleranceDeg: Double = 0.05,
    ): List<Pair<ConstellationPattern, ConstellationAccuracy>> {
        if (matches.isEmpty()) return emptyList()
        val toleranceCos = cos(Math.toRadians(matchToleranceDeg))
        val result = ArrayList<Pair<ConstellationPattern, ConstellationAccuracy>>()
        for (pattern in constellations) {
            val errors = ArrayList<Double>()
            for (star in pattern.stars) {
                val starDir = raDecToVector(star.raHours.toDouble() * 15.0, star.decDegrees.toDouble())
                val best = matches.maxByOrNull { (dir, _) -> dir.dot(starDir) } ?: continue
                if (best.first.dot(starDir) < toleranceCos) continue
                val predicted = solution.skyToImage(
                    SkyPoint(star.raHours * 15f, star.decDegrees),
                    imageHeight,
                ) ?: continue
                errors += hypot((predicted.x - best.second.x).toDouble(), (predicted.y - best.second.y).toDouble())
            }
            if (errors.isNotEmpty()) {
                result += pattern to ConstellationAccuracy(errors.size, sqrt(errors.sumOf { it * it } / errors.size))
            }
        }
        return result
    }

    /**
     * Lokale Bildskala (Pixel pro Grad) zwischen zwei Himmelspunkten, für den Divergenz-/Naht-Cull
     * oben. Sondiert einen kleinen Schritt ENTLANG der tatsächlichen Verbindungsrichtung (nicht nur
     * eine feste Deklinations-Achse wie zuvor) an BEIDEN Endpunkten und liefert das KONSERVATIVERE
     * (kleinere) Ergebnis. Eine einzige, nur-am-Start und nur-in-Dec gemessene, linear über den
     * vollen Winkelabstand hochgerechnete Schätzung kann in Zielrichtung (oder am anderen Ende)
     * völlig anders (viel stärker verzerrt) ausfallen, als die Startsonde vermuten lässt -- genau
     * das ließ eine echte Spinnennetz-Kante bei einem 34-Kacheln-Panorama durchrutschen
     * (Herkules-Fall, Nutzer-Diagnose 2026-07-24).
     */
    private fun estimatePixelsPerDegree(
        solution: WcsSolutionLike,
        skyA: SkyPoint,
        projectedA: Offset,
        skyB: SkyPoint,
        projectedB: Offset,
        imageHeight: Int,
    ): Float {
        fun probe(from: SkyPoint, to: SkyPoint, projectedFrom: Offset): Float? {
            var dRa = (to.raDegrees - from.raDegrees).toDouble()
            if (dRa > 180.0) dRa -= 360.0
            if (dRa < -180.0) dRa += 360.0
            val dDec = (to.decDegrees - from.decDegrees).toDouble()
            val len = hypot(dRa, dDec)
            if (len < 1e-9) return null
            val step = min(0.2, len * 0.5)
            // reference=projectedFrom: der Sondierungs-Punkt liegt nur `step` Grad (<=0,2°) von `from`
            // entfernt -- ohne dieselbe Entfaltungs-Referenz wie `projectedFrom` könnte er auf den
            // JEWEILS ANDEREN Ast der 360°-Naht fallen und eine riesige Schein-Distanz liefern, die
            // genau den Kanten-Cull korrumpiert, der sich unten auf dieses Ergebnis verlässt.
            val shifted = solution.skyToImage(
                SkyPoint(
                    (from.raDegrees + (dRa / len * step)).toFloat(),
                    (from.decDegrees + (dDec / len * step)).toFloat(),
                ),
                imageHeight,
                reference = projectedFrom,
            ) ?: return null
            val distance = hypot(
                (shifted.x - projectedFrom.x).toDouble(),
                (shifted.y - projectedFrom.y).toDouble(),
            ).toFloat()
            return distance / step.toFloat()
        }
        val fromA = probe(skyA, skyB, projectedA)
        val fromB = probe(skyB, skyA, projectedB)
        return when {
            fromA != null && fromB != null -> min(fromA, fromB)
            fromA != null -> fromA
            fromB != null -> fromB
            else -> 0f
        }
    }

    /**
     * Bildet eine Katalog-Ellipse (großer/kleiner Halbmesser aus [majArcmin]/[minArcmin], Positionswinkel
     * [paDeg] Grad Nord->Ost) an [sky] in eine Bild-Ellipse um: (Durchmesser groß px, Durchmesser klein
     * px, Rotationswinkel Grad). ISOTROPE lokale Skala (geometrisches Mittel der beiden Jacobi-Eigenwerte
     * an [point], `sqrt(|det J|)`) + reiner Rotationsanteil der Jacobi-Matrix -- das Katalog-Seitenver-
     * hältnis `majArcmin:minArcmin` bleibt dadurch IMMER exakt erhalten, unabhängig von der lokalen
     * (An-)Isotropie der Projektion an diesem Bildpunkt (Nutzer-Vorgabe 2026-08-27: "wieder normal ohne
     * Verzerrung" -- ersetzt die zuvor hier verwendete volle 2x2-SVD, die eine reale Projektionsanisotropie
     * [z.B. Equirectangular fernab des Himmelsäquators] noch zusätzlich in die gezeichnete Form mischte,
     * s. [[project_rendering_seam_ellipse_fix]]). null bei fehlgeschlagener Nord-/Ost-Abtastung (z.B.
     * Projektions-Polstelle) -- Aufrufer fällt dann auf den festen Anzeige-Kreis zurück.
     *
     * Herleitung: Jacobi-Spalten (jNx,jNy)/(jEx,jEy) = Bild-px PRO GRAD wahrer Nord-/Ost-Distanz an
     * [point]. Für ein reales 2x2 J=[[a,c],[b,d]] (Spalten (a,b),(c,d)) liefert `atan2(b-c, a+d)` den
     * Rotationswinkel der Polarzerlegung J=R*M (R = reine Rotation, M = symmetrischer Streckanteil) --
     * OHNE Matrix-Invertierung/-Wurzel, per Hand an 3 Fällen verifiziert (reine Rotation φ -> φ; reine
     * anisotrope Diagonalskalierung -> 0; Skalierung gefolgt von Rotation R(φ)*diag(sx,sy) -> φ,
     * unabhängig von sx,sy). Mit `isoScale = sqrt(|det J|)` liegt `A_iso = isoScale*R(θ_J+PA)*diag(aDeg,
     * bDeg)` bereits in Polarform vor -- Halbachsen/Rotation folgen direkt ohne weitere SVD.
     */
    /** Lokale 2x2-Jacobi-Matrix (Bild-px PRO GRAD wahrer Winkeldistanz) der Sky->Bild-Abbildung
     *  [solution] bei [point]/[sky], via Epsilon-Abtastung in Nord-/Ost-Richtung (Betrag, nicht nur
     *  Richtung wie im früheren paToImageAngle -- die Schrittweite wird herausdividiert). Grundlage für
     *  [projectedEllipseAxes] (DSO-Ellipsenform). Rückgabe [jNx,jNy,jEx,jEy], oder null, wenn nicht
     *  abbildbar (z.B. Antipode). */
    private fun localSkyToImageJacobian(
        point: Offset,
        sky: SkyPoint,
        solution: WcsSolutionLike,
        imageHeight: Int,
    ): FloatArray? {
        val delta = 0.1f
        val decStep = if (sky.decDegrees < 89f) delta else -delta
        // WICHTIG (Nutzerbefund 2026-08-24, "blaue Linien"/Bild-Naht-Ausreißer): [point] als Referenz
        // durchreichen, exakt wie createConstellationOverlays es für patternReference bereits tut --
        // ohne das kann der Epsilon-Tastpunkt bei einem Objekt nahe der 360°-Bild-Naht (RA nahe 0°/360°)
        // auf die GEGENÜBERLIEGENDE Bildseite springen (atan2-Hauptzweig), was hier eine riesige, freie
        // Scheindistanz statt einer kleinen lokalen Ableitung ergibt -- Diagnose zeigte dadurch
        // entstandene DSO-Ellipsen mit bis zu 4404px Breite bei 1px Höhe, alle nahe x=0.
        val north = solution.skyToImage(SkyPoint(sky.raDegrees, sky.decDegrees + decStep), imageHeight, point) ?: return null
        val cosDec = cos(Math.toRadians(sky.decDegrees.toDouble())).let { if (abs(it) < 0.02) 0.02 else it }
        val east = solution.skyToImage(
            SkyPoint(sky.raDegrees + (delta / cosDec).toFloat(), sky.decDegrees),
            imageHeight,
            point,
        ) ?: return null
        val sign = if (decStep < 0f) -1f else 1f
        return floatArrayOf(
            (north.x - point.x) * sign / delta,
            (north.y - point.y) * sign / delta,
            (east.x - point.x) / delta,
            (east.y - point.y) / delta,
        )
    }

    private fun projectedEllipseAxes(
        point: Offset,
        sky: SkyPoint,
        majArcmin: Float,
        minArcmin: Float,
        paDeg: Float,
        solution: WcsSolutionLike,
        imageHeight: Int,
    ): Triple<Float, Float, Float>? {
        val jac = localSkyToImageJacobian(point, sky, solution, imageHeight) ?: return null
        val jNx = jac[0]; val jNy = jac[1]; val jEx = jac[2]; val jEy = jac[3]

        // majArcmin/minArcmin sind volle Achsen (Katalog-Konvention, wie arcminToPx) -> /120 statt /60
        // für den HALBEN Wert (Grad).
        val aDeg = majArcmin / 120.0
        val bDeg = minArcmin / 120.0
        // Isotrope Skala (Bild-px pro Grad) = geometrisches Mittel der Jacobi-Eigenwerte, UNABHÄNGIG
        // von deren tatsächlichem Verhältnis -- das Katalog-Seitenverhältnis aDeg:bDeg bleibt dadurch
        // immer exakt erhalten, s. Funktions-KDoc.
        val isoScale = sqrt(abs(jNx.toDouble() * jEy.toDouble() - jNy.toDouble() * jEx.toDouble()))
        if (!isoScale.isFinite() || isoScale <= 0.0) return null
        // Reiner Rotationsanteil von J (Polarzerlegung J=R*M, R-Winkel per Standardformel für ein
        // 2x2 J=[[a,c],[b,d]] mit Spalten (jNx,jNy)=(a,b) und (jEx,jEy)=(c,d)) + Katalog-Positionswinkel.
        val rotationRad = atan2((jNy - jEx).toDouble(), (jNx + jEy).toDouble()) + Math.toRadians(paDeg.toDouble())
        val majorPx = (2.0 * isoScale * aDeg).toFloat()
        val minorPx = (2.0 * isoScale * bDeg).toFloat()
        return Triple(majorPx, minorPx, Math.toDegrees(rotationRad).toFloat())
    }

    /**
     * Winkelgröße (Bogenmin) -> Kreisdurchmesser in Bild-px, an den Himmelsmaßstab gekoppelt.
     * floor = kleiner absoluter Sicht-/Antipp-Wert (NICHT mehr 1.2%·Kante, das war bei Weitfeld ~43'
     * und blies alle kleinen Objekte auf identische Blöcke auf), cap = kurze Bildkante. Ohne Größe
     * -> kleiner fester Kreis (~0.006·minDim).
     */
    private fun arcminToPx(arcmin: Float?, pixelsPerDegree: Float, imageWidth: Int, imageHeight: Int): Float {
        val minDim = min(imageWidth, imageHeight).toFloat()
        val px = if (arcmin != null && arcmin > 0f && pixelsPerDegree > 0f) {
            arcmin / 60f * pixelsPerDegree
        } else {
            minDim * 0.006f
        }
        val floor = max(8f, minDim * 0.004f)
        return px.coerceIn(floor, minDim)
    }

    // Farbschema je Objektart (Nutzerwunsch; tunebar). Cyan/Hellgrün/Hellgrün(Kugelhaufen)/Gelb/Gold.
    private const val GALAXY_CYAN = 0xFF3FDDF5L
    // Exakt der frühere App-Palette-Ton "Hellgrün" (0xFF76FF03) -- Nutzerwunsch 2026-08-17, nach
    // Live-Vergleich am Gerät explizit dieser Ton statt eines selbst gewählten Grüns. Bewusst ein
    // ANDERER Hex-Wert als GLOBULAR_LIGHTGREEN
    // (0xFFA6F05A, pastelliger/weniger gesättigt) -- beide bleiben zwei eigenständige Konstanten,
    // damit sie im Code klar unterscheidbar bleiben, auch wenn beide farblich in derselben
    // Gelbgrün-Familie liegen (Kugelsternhaufen können auf demselben Foto vorkommen).
    private const val NEBULA_BRIGHTGREEN = 0xFF76FF03L
    private const val GLOBULAR_LIGHTGREEN = 0xFFA6F05AL
    // Exakt der frühere App-Palette-Ton "Gelb" (0xFFFFD28A) -- Nutzerwunsch 2026-08-18, ersetzt das
    // vorherige Pink für offene Sternhaufen.
    private const val OPEN_YELLOW = 0xFFFFD28AL
    private const val OTHER_GOLD = 0xFFFFD77EL

    private data class DsoStyle(val colorArgb: Long, val lineStyle: OverlayLineStyle)

    /**
     * Farbe + Linienstil je DSO-Typ (die Form ist Kreis oder orientierte Ellipse, siehe Form-Fakten): Galaxien =
     * Cyan·gepunktet, Nebel (inkl. PN/Dunkel/SNR) = Hellgrün·durchgezogen, Kugelsternhaufen (gc) =
     * pastelliges Hellgrün·gestrichelt, offene Sternhaufen (oc) = Gelb·gestrichelt, Sonstige = Gold·durchgezogen.
     * Bei zu kleinem Kreis wird der Stil zur Sichtbarkeit auf Solid gesetzt (siehe createDeepSkyOverlays).
     * [colorOverrides] (Katalog bearbeiten -> Farben, Nutzerwunsch 2026-08-20) überschreibt die
     * eingebaute Farbe je Gruppe, wenn gesetzt -- der Linienstil bleibt immer fest je Gruppe.
     */
    private fun dsoStyleForType(type: String, colorOverrides: Map<DsoColorGroup, Long> = emptyMap()): DsoStyle {
        val group = DsoColorGroup.of(type)
        val (defaultColor, lineStyle) = when (group) {
            DsoColorGroup.Galaxy -> GALAXY_CYAN to OverlayLineStyle.Dotted
            DsoColorGroup.Globular -> GLOBULAR_LIGHTGREEN to OverlayLineStyle.Dashed
            DsoColorGroup.OpenCluster -> OPEN_YELLOW to OverlayLineStyle.Dashed
            DsoColorGroup.Nebula -> NEBULA_BRIGHTGREEN to OverlayLineStyle.Solid
            DsoColorGroup.Other -> OTHER_GOLD to OverlayLineStyle.Solid
        }
        return DsoStyle(colorOverrides[group] ?: defaultColor, lineStyle)
    }

    /** Eingebauter Standardton je Gruppe (ohne Nutzer-Override) -- Single Source of Truth fürs
     *  "Farben"-Menü (Erstwert je Regler), ohne die 5 privaten Konstanten oben zu duplizieren. */
    fun builtInColorFor(group: DsoColorGroup): Long = when (group) {
        DsoColorGroup.Galaxy -> GALAXY_CYAN
        DsoColorGroup.Globular -> GLOBULAR_LIGHTGREEN
        DsoColorGroup.OpenCluster -> OPEN_YELLOW
        DsoColorGroup.Nebula -> NEBULA_BRIGHTGREEN
        DsoColorGroup.Other -> OTHER_GOLD
    }

    /** Schneidet das Segment (x0,y0)-(x1,y1) den Kreis (cx,cy,radius)? (kürzeste Distanz < radius). */
    private fun segmentCircleHit(x0: Float, y0: Float, x1: Float, y1: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val vx = x1 - x0
        val vy = y1 - y0
        val vv = vx * vx + vy * vy
        val t = if (vv <= 0f) 0f else (((cx - x0) * vx + (cy - y0) * vy) / vv).coerceIn(0f, 1f)
        val px = x0 + t * vx
        val py = y0 + t * vy
        val dx = cx - px
        val dy = cy - py
        return dx * dx + dy * dy < radius * radius
    }

    /** Überlappt das Rechteck (left,top,right,bottom) den Kreis (cx,cy,radius)? */
    private fun rectCircleHit(left: Float, top: Float, right: Float, bottom: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val nx = cx.coerceIn(left, right)
        val ny = cy.coerceIn(top, bottom)
        val dx = cx - nx
        val dy = cy - ny
        return dx * dx + dy * dy < radius * radius
    }

    /** Bezeichnungs-Normalisierung für den Form-Fakten-Lookup gegen [shapes] (dso_shapes.json-Schlüssel). */
    private fun normalizeDesignation(s: String): String =
        s.trim().uppercase().replace(Regex("[\\s\\-_]"), "")

    private const val DEEP_SKY_ID_OFFSET = 100_000L
    private const val STAR_ID_OFFSET = 200_000L
    private const val STAR_MARKER_COLOR = 0xFFEAF2FF
    private const val UNKNOWN_MAGNITUDE_SORT = 998f

    /**
     * Großkreis zwischen zwei Sternen (RA/Dec) in eine ins Bild projizierte Polylinie unterteilen.
     * Auf einem Fisheye folgt die Linie so der Wölbung, statt als Gerade quer durchs Bild zu
     * schneiden. Punkte, die nicht projizierbar sind, werden übersprungen.
     */
    private fun greatCircleImagePolyline(
        raHoursA: Float,
        decDegreesA: Float,
        raHoursB: Float,
        decDegreesB: Float,
        solution: WcsSolutionLike,
        imageHeight: Int,
        // Startreferenz für die nahtstellen-sichere Entfaltung -- i.d.R. der bereits fixierte
        // (unwrapped) Startpunkt des Aufrufers, damit der erste Kurvenpunkt (t=0, exakt Stern A)
        // garantiert auf demselben Ast landet wie das außerhalb schon berechnete `start`. Jeder
        // weitere Punkt referenziert danach den JEWEILS VORHERIGEN Kurvenpunkt (anders als beim
        // Muster-Sterne-Loop oben: die Samples HIER sind ein räumlich geordneter Weg A->B, Verketten
        // ist hier also korrekt und am einfachsten).
        reference: Offset? = null,
    ): List<Offset> {
        val va = raDecToVector(raHoursA * 15.0, decDegreesA.toDouble())
        val vb = raDecToVector(raHoursB * 15.0, decDegreesB.toDouble())
        val steps = 14
        val out = ArrayList<Offset>(steps + 1)
        var ref = reference
        for (i in 0..steps) {
            val v = slerp(va, vb, i.toDouble() / steps)
            val (ra, dec) = vectorToRaDec(v)
            val p = solution.skyToImage(
                com.codex.starmapper.domain.SkyPoint(ra.toFloat(), dec.toFloat()),
                imageHeight,
                ref,
            ) ?: continue
            out += p
            ref = p
        }
        return out
    }

    /**
     * Tatsächliche projizierte Pfadlänge zwischen zwei Sternen, gemessen durch Sampling entlang des
     * Großkreises (wiederverwendet [greatCircleImagePolyline]) statt eine einzige lineare
     * Skalenschätzung über die gesamte Distanz hochzurechnen. Notwendig, weil bei nichtlinearen
     * Projektionen (Mercator/Zylindrisch: Y wächst logarithmisch mit der Deklination; Equirectangular:
     * X ohne cos(Dec)-Korrektur nahe am Pol) "Pixel pro Grad" über größere Winkeldistanzen NICHT
     * konstant ist -- eine an einem Punktepaar geschätzte Skala, linear auf die gesamte Spanne
     * hochgerechnet, unter- oder überschätzt die tatsächliche Ausdehnung systematisch (Großer-Bär/
     * Kleiner-Bär-Befund, 2026-07-31: Skala am flacheren Ende des Musters geschätzt und aufs steilere
     * Ende hochgerechnet, ergibt bei Mercator eine zu kleine "erwartete" Diagonale).
     *
     * [isCurveTrustworthy] sichert GENAU wie beim Kurven-Zeichnen (s. dort) gegen einen Sonderfall ab,
     * der sonst diese Messung selbst verfälschen würde: läuft der Großkreis-Pfad an einer Mosaik-
     * Kachelgrenze vorbei, kann ein Zwischenpunkt in eine ganz andere Kachel "springen" -- die
     * gesampelte Pfadlänge würde dann NICHT die tatsächliche Projektionskrümmung messen, sondern
     * diesen Sprung selbst, und ironischerweise GENAU das Muster als "kohärent" durchwinken, das
     * dieser Check eigentlich verwerfen soll (Großer-Bär-Testfall: zwei weit auseinanderliegende
     * TAN-Kacheln ohne Fallback). null (Aufrufer fällt auf die alte lineare Schätzung zurück), wenn
     * die Kurve nicht vertrauenswürdig ist oder weniger als 2 Zwischenpunkte projizierbar sind (z.B.
     * Pfad läuft komplett durch eine Pol-Ausschlusszone).
     */
    private fun sampledPathLength(
        raHoursA: Float,
        decDegreesA: Float,
        raHoursB: Float,
        decDegreesB: Float,
        solution: WcsSolutionLike,
        imageHeight: Int,
        imageWidth: Int,
        start: Offset,
        end: Offset,
    ): Float? {
        val poly = greatCircleImagePolyline(raHoursA, decDegreesA, raHoursB, decDegreesB, solution, imageHeight, reference = start)
        if (!isCurveTrustworthy(poly, start, end, imageWidth, imageHeight)) return null
        var length = 0f
        for (i in 1 until poly.size) {
            length += hypot((poly[i].x - poly[i - 1].x).toDouble(), (poly[i].y - poly[i - 1].y).toDouble()).toFloat()
        }
        return length
    }

    /**
     * Prüft, ob eine per [greatCircleImagePolyline] gesampelte Kurve vertrauenswürdig genug ist, um
     * sie statt der geraden Verbindung zwischen [start]/[end] zu zeichnen. Die Kurven-Zwischenpunkte
     * sind (anders als [start]/[end] selbst) durch keine Masken-/Divergenz-Prüfung abgesichert --
     * fällt ein Zwischenpunkt in eine Lücke ohne Kachel-Anspruch, kann der Fallback
     * dort weit danebenliegen, auch wenn beide echten Endpunkte bereits als plausibel bestätigt
     * sind (Nutzerbeleg 2026-07-27: Delphin über fast die volle Bildbreite verzerrt, obwohl dessen
     * eigene 5 Sterne allesamt korrekt/kompakt sitzen). Bei Misstrauen soll der Aufrufer auf die
     * gerade Verbindung zurückfallen statt die Kante zu verwerfen -- [start]/[end] sind bereits
     * geprüft und damit immer eine sichere Darstellung.
     */
    private fun isCurveTrustworthy(
        curve: List<Offset>,
        start: Offset,
        end: Offset,
        imageWidth: Int,
        imageHeight: Int,
    ): Boolean {
        if (curve.size < 2) return false
        val minX = curve.minOf { it.x }
        val maxX = curve.maxOf { it.x }
        val minY = curve.minOf { it.y }
        val maxY = curve.maxOf { it.y }
        val curveDiagonal = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat()
        val imageDiagonal = hypot(imageWidth.toDouble(), imageHeight.toDouble()).toFloat()
        if (curveDiagonal > MAX_EDGE_DIAGONAL_FRACTION * imageDiagonal) return false
        val straightDistance = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat()
        val slack = max(imageWidth, imageHeight) * 0.02f
        return curveDiagonal <= CURVE_DEVIATION_TOLERANCE * straightDistance + slack
    }

    /** Sphärische lineare Interpolation zweier Einheitsvektoren. */
    private fun slerp(a: Vec3, b: Vec3, t: Double): Vec3 {
        val an = a.normalized()
        val bn = b.normalized()
        val dot = an.dot(bn).coerceIn(-1.0, 1.0)
        val theta = acos(dot)
        if (theta < 1e-6) return an
        val s = sin(theta)
        val w1 = sin((1.0 - t) * theta) / s
        val w2 = sin(t * theta) / s
        return Vec3(
            an.x * w1 + bn.x * w2,
            an.y * w1 + bn.y * w2,
            an.z * w1 + bn.z * w2,
        ).normalized()
    }

    private fun segmentIntersectsImage(
        start: Offset,
        end: Offset,
        width: Int,
        height: Int,
        padding: Float,
    ): Boolean {
        val left = -padding
        val top = -padding
        val right = width + padding
        val bottom = height + padding
        if (start.x in left..right && start.y in top..bottom) return true
        if (end.x in left..right && end.y in top..bottom) return true

        val dx = end.x - start.x
        val dy = end.y - start.y
        var lower = 0f
        var upper = 1f
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(start.x - left, right - start.x, start.y - top, bottom - start.y)
        for (index in p.indices) {
            if (p[index] == 0f && q[index] < 0f) return false
            if (p[index] == 0f) continue
            val ratio = q[index] / p[index]
            if (p[index] < 0f) lower = max(lower, ratio) else upper = min(upper, ratio)
            if (lower > upper) return false
        }
        return true
    }
}
