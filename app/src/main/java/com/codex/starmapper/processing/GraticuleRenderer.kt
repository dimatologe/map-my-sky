package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.SkyPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Eine Polylinie (Meridian oder Parallele) in BILD-Koordinaten. */
data class GraticuleLine(val points: List<Offset>)

/** Eine Gradzahl-Beschriftung in BILD-Koordinaten. */
data class GraticuleLabel(val text: String, val pos: Offset)

/** Fertig berechnetes Koordinatennetz (Editor + Export zeichnen exakt dasselbe -> WYSIWYG). */
data class GraticuleGeometry(
    val lines: List<GraticuleLine>,
    val labels: List<GraticuleLabel>,
)

/**
 * Berechnet aus einer WCS-Lösung ein RA/Dec-Gradnetz als Polylinien + Gradzahlen, alles in
 * BILD-Koordinaten. Bewusst ohne Compose-/Android-Abhängigkeit, damit Editor (Live-Vorschau)
 * und [ExportRenderer] (1:1) dieselbe Geometrie zeichnen.
 *
 * Meridiane (konstante RA) und Parallelen (konstante Dec) werden fein abgetastet und über
 * [WcsSolutionLike.skyToImage] projiziert. Sprünge über die 360°-Naht / hinter die Kamera
 * (Fisheye) brechen die Polylinie (Divergenz-Cull, modellunabhängig wie bei den Sternbildlinien).
 *
 * Beschriftung: RA in Stunden (z.B. "6h", "6h30m"), Dec in Grad (z.B. "+45°", "-10°").
 */
object GraticuleRenderer {

    // Dec-Schritt-Leiter (Grad) und RA-Schritt-Leiter (Grad, jeweils "stundenfreundlich":
    // 3.75°=0.25h, 7.5°=0.5h, 15°=1h, 30°=2h, 45°=3h, 90°=6h).
    private val DEC_STEPS = floatArrayOf(0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f)
    private val RA_STEPS_DEG = floatArrayOf(3.75f, 7.5f, 15f, 30f, 45f, 90f)
    private const val TARGET_LINES = 9f
    private const val FRONT_HEMI_COS_LIMIT = -0.0175 // cos(91°): Fisheye-Rückhemisphäre verwerfen

    fun compute(
        wcs: WcsSolutionLike,
        imageWidth: Int,
        imageHeight: Int,
        densityFactor: Float,
    ): GraticuleGeometry {
        if (imageWidth <= 0 || imageHeight <= 0) return GraticuleGeometry(emptyList(), emptyList())

        val longEdge = max(imageWidth, imageHeight).toFloat()
        val density = densityFactor.coerceIn(0.3f, 3f)

        // Fisheye (radial): Sterne hinter dem ~180°-Feld verwerfen, sonst projiziert ein gefaltetes
        // Modell sie fälschlich in die Bildmitte. Nur beim radialen Modell sinnvoll.
        val fisheyeRot = (wcs as? PanoramaWcsSolution)
            ?.takeIf { it.projection is FisheyeProjection }
            ?.rotEquToPano

        fun project(raDeg: Double, decDeg: Double): Offset? {
            if (fisheyeRot != null) {
                if ((fisheyeRot * raDecToVector(raDeg, decDeg)).z < FRONT_HEMI_COS_LIMIT) return null
            }
            return wcs.skyToImage(SkyPoint(raDeg.toFloat(), decDeg.toFloat()), imageHeight)
        }

        // --- Maßstab + sichtbarer Himmelsbereich schätzen ---
        val center = centerSky(wcs, imageWidth, imageHeight)
        val pxPerDeg = center?.let { pxPerDegAt(wcs, it, imageHeight) } ?: 0f

        val centerRa: Double
        val centerDec: Double
        val fovDeg: Double
        if (center != null && pxPerDeg > 0f) {
            centerRa = center.raDegrees.toDouble()
            centerDec = center.decDegrees.toDouble()
            fovDeg = (longEdge / pxPerDeg).toDouble()
        } else {
            // Fallback (z.B. nicht invertierbares Modell): ganzer Himmel, grobe Schritte.
            centerRa = 0.0
            centerDec = 0.0
            fovDeg = 360.0
        }

        // Schrittweiten wählen (densityFactor>1 -> feiner -> kleinerer Schritt).
        val idealDec = (fovDeg / TARGET_LINES / density).toFloat()
        val decStep = nearest(DEC_STEPS, idealDec)
        val cosCenter = max(cos(centerDec * PI / 180.0), 0.2)
        val idealRa = (decStep / cosCenter).toFloat()
        val raStepDeg = nearest(RA_STEPS_DEG, idealRa)

        // Iterationsbereich begrenzen (sonst bei engem FOV Millionen Linien).
        val margin = 1.2
        val decMin = (centerDec - fovDeg * margin).coerceAtLeast(-89.5)
        val decMax = (centerDec + fovDeg * margin).coerceAtMost(89.5)
        val raHalf = min(fovDeg * margin / cosCenter, 185.0)
        val raMin = centerRa - raHalf
        val raMax = centerRa + raHalf

        val sampleDeg = (min(decStep, raStepDeg) / 3f).coerceIn(0.25f, 2f).toDouble()
        val divergenceSlack = longEdge * 0.02f
        // Für den Divergenz-/Naht-Bruch: im Fallback (px/Grad nicht bestimmbar) eine grobe Skala aus
        // der Bildgröße, damit der Bruch nicht ausgeschaltet ist (sonst Geisterlinien über die Naht).
        val pxPerDegEff = if (pxPerDeg > 0f) pxPerDeg else longEdge / 360f

        val lines = ArrayList<GraticuleLine>()
        val labels = ArrayList<GraticuleLabel>()

        // --- Parallelen (konstante Dec) ---
        run {
            val firstK = ceil(decMin / decStep).toInt()
            val lastK = floor(decMax / decStep).toInt()
            for (k in firstK..lastK) {
                val dec = (k * decStep).toDouble()
                if (dec <= -89.5 || dec >= 89.5) continue
                val raSamples = sampleCount(raMax - raMin, sampleDeg)
                val expectedStepPx = (sampleDeg * cos(dec * PI / 180.0)).toFloat() * pxPerDegEff
                val projected = ArrayList<Offset?>(raSamples + 1)
                for (i in 0..raSamples) {
                    val ra = raMin + (raMax - raMin) * i / raSamples
                    projected += project(((ra % 360.0) + 360.0) % 360.0, dec)
                }
                addLine(projected, expectedStepPx, divergenceSlack, imageWidth, imageHeight, lines)
                pickLabel(projected, imageWidth, imageHeight, horizontalEdges = false)
                    ?.let { labels += GraticuleLabel(formatDec(dec), clampLabelPos(it, imageWidth, imageHeight)) }
            }
        }

        // --- Meridiane (konstante RA) ---
        run {
            val firstK = ceil(raMin / raStepDeg).toInt()
            val lastK = floor(raMax / raStepDeg).toInt()
            val expectedStepPx = sampleDeg.toFloat() * pxPerDegEff
            for (k in firstK..lastK) {
                val raRaw = k * raStepDeg.toDouble()
                val ra = ((raRaw % 360.0) + 360.0) % 360.0
                val decSamples = sampleCount(decMax - decMin, sampleDeg)
                val projected = ArrayList<Offset?>(decSamples + 1)
                for (i in 0..decSamples) {
                    val dec = decMin + (decMax - decMin) * i / decSamples
                    projected += project(ra, dec)
                }
                addLine(projected, expectedStepPx, divergenceSlack, imageWidth, imageHeight, lines)
                pickLabel(projected, imageWidth, imageHeight, horizontalEdges = true)
                    ?.let { labels += GraticuleLabel(formatRaHours(ra), clampLabelPos(it, imageWidth, imageHeight)) }
            }
        }

        // Pol-Überlagerung vermeiden: an den Polen laufen alle Meridiane zusammen -> die RA-Zahlen
        // stapeln sich auf engstem Raum. Labels mit zu geringem Abstand zu einem bereits behaltenen
        // verwerfen (generisch, fängt auch sonstige Überlappungen ab).
        val minLabelSep = min(imageWidth, imageHeight) * 0.03f
        val dedupedLabels = ArrayList<GraticuleLabel>(labels.size)
        for (l in labels) {
            val tooClose = dedupedLabels.any {
                hypot((it.pos.x - l.pos.x).toDouble(), (it.pos.y - l.pos.y).toDouble()) < minLabelSep
            }
            if (!tooClose) dedupedLabels += l
        }
        return GraticuleGeometry(lines, dedupedLabels)
    }

    /** Zerlegt eine projizierte (evtl. lückenhafte/divergente) Punktfolge in saubere Polylinien. */
    private fun addLine(
        projected: List<Offset?>,
        expectedStepPx: Float,
        slack: Float,
        width: Int,
        height: Int,
        out: MutableList<GraticuleLine>,
    ) {
        var current = ArrayList<Offset>()
        var prev: Offset? = null
        fun flush() {
            if (current.size >= 2 && touchesImage(current, width, height)) {
                out += GraticuleLine(current)
            }
            current = ArrayList()
        }
        for (p in projected) {
            if (p == null || !p.x.isFinite() || !p.y.isFinite()) {
                flush(); prev = null; continue
            }
            val last = prev
            if (last != null && expectedStepPx > 0f) {
                val jump = hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()).toFloat()
                if (jump > 4f * expectedStepPx + slack) {
                    flush()
                }
            }
            current.add(p)
            prev = p
        }
        flush()
    }

    private fun touchesImage(points: List<Offset>, width: Int, height: Int): Boolean {
        val pad = max(width, height) * 0.05f
        return points.any { it.x in -pad..(width + pad) && it.y in -pad..(height + pad) }
    }

    /**
     * Label-Anker: der sichtbare Punkt der Linie, der seinem Rahmen-Rand am nächsten ist.
     * RA/Meridiane -> obere ODER untere Kante (horizontalEdges=true), Dec/Parallelen -> linke ODER
     * rechte Kante. So sitzen die Stunden konsistent oben/unten und die Grad links/rechts am Rahmen
     * (Sternkarten-Optik), statt je nach Linienverlauf am „höchsten/linkesten" Punkt zu streuen.
     */
    private fun pickLabel(
        projected: List<Offset?>,
        width: Int,
        height: Int,
        horizontalEdges: Boolean,
    ): Offset? {
        var best: Offset? = null
        var bestDist = Float.MAX_VALUE
        for (p in projected) {
            if (p == null) continue
            if (p.x < 0f || p.x > width || p.y < 0f || p.y > height) continue
            val dist = if (horizontalEdges) minOf(p.y, height - p.y) else minOf(p.x, width - p.x)
            if (dist < bestDist) {
                bestDist = dist
                best = p
            }
        }
        return best
    }

    /**
     * Hält den Label-Anker so weit im Bild, dass der links-ausgerichtete Text vollständig sichtbar
     * bleibt (auch wenn die Projektion/Linie teilweise außerhalb liegt). Rechts mehr Rand, weil der
     * Text nach rechts wächst; oben/unten Platz für die Texthöhe. Bildkoordinaten -> gilt für Editor
     * UND Export gleichermaßen.
     */
    private fun clampLabelPos(p: Offset, width: Int, height: Int): Offset {
        val minDim = min(width, height)
        val leftPad = minDim * 0.012f
        val rightPad = minDim * 0.075f
        val vPad = minDim * 0.03f
        return Offset(
            p.x.coerceIn(leftPad, width - rightPad),
            p.y.coerceIn(vPad, height - vPad),
        )
    }

    /** Pixel pro Grad in der Nähe von [sky] (2-Punkt-Sampling, wie AstapOverlayMapper). */
    private fun pxPerDegAt(wcs: WcsSolutionLike, sky: SkyPoint, imageHeight: Int): Float {
        val p0 = wcs.skyToImage(sky, imageHeight) ?: return 0f
        val step = if (sky.decDegrees < 89f) 0.2f else -0.2f
        val p1 = wcs.skyToImage(SkyPoint(sky.raDegrees, sky.decDegrees + step), imageHeight) ?: return 0f
        return hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat() / 0.2f
    }

    /** RA/Dec in der Bildmitte (für Schrittwahl + sichtbaren Bereich). null = nicht bestimmbar. */
    private fun centerSky(wcs: WcsSolutionLike, width: Int, height: Int): SkyPoint? = when (wcs) {
        is WcsSolution -> wcs.imageToSky(width / 2.0, height / 2.0, height)
        is PanoramaWcsSolution -> {
            val dir = wcs.projection.pixelToDirection(width / 2.0, height / 2.0)
            if (dir == null) {
                null
            } else {
                val (ra, dec) = vectorToRaDec(wcs.rotEquToPano.transpose() * dir)
                SkyPoint(ra.toFloat(), dec.toFloat())
            }
        }
        is MosaicWcsSolution -> {
            val tile = wcs.tiles.firstOrNull()
            if (tile == null) null else centerSky(tile.wcs, tile.tileWidth, tile.tileHeight)
        }
    }

    private fun sampleCount(spanDeg: Double, sampleDeg: Double): Int =
        (abs(spanDeg) / sampleDeg).roundToInt().coerceIn(2, 1000)

    private fun nearest(ladder: FloatArray, value: Float): Float =
        ladder.minByOrNull { abs(it - value) } ?: ladder.last()

    private fun formatDec(decDeg: Double): String {
        val rounded = (decDeg * 10.0).roundToInt() / 10.0
        val sign = if (rounded < 0) "-" else "+"
        val abs = abs(rounded)
        val body = if (abs % 1.0 == 0.0) abs.toInt().toString() else trimDecimal(abs)
        return "$sign$body°"
    }

    private fun formatRaHours(raDeg: Double): String {
        var totalMin = (raDeg / 15.0 * 60.0).roundToInt()
        totalMin = ((totalMin % 1440) + 1440) % 1440 // 0..1439 Minuten = 24h
        val h = totalMin / 60
        val m = totalMin % 60
        return if (m == 0) "${h}h" else "${h}h${m}m"
    }

    private fun trimDecimal(v: Double): String {
        val s = (Math.round(v * 10.0) / 10.0).toString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}
