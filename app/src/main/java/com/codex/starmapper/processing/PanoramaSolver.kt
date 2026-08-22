package com.codex.starmapper.processing

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.tan

/** Ergebnis eines Panorama-/Fisheye-Solves inkl. Debug-Daten für die Sichtprüfung. */
data class PanoramaSolveResult(
    val wcs: PanoramaWcsSolution,
    val debug: PanoramaDebug,
    val log: String,
)

data class PanoramaDebug(
    val solvedPatches: Int,
    val patchCentersImage: List<Offset>,
    val residualArcmin: Float,
    val fieldOfViewWidthDeg: Float,
)

class PanoramaSolveException(message: String) : IllegalStateException(message)

/**
 * Löst Fisheye-Bilder über das Patch-Verfahren (Phase 1: ein zentraler Patch genügt).
 * Ein kleiner Ausschnitt um die optische Achse wird gnomonisch entzerrt (runde Sterne),
 * mit einem normalen Solver gelöst, daraus globale Drehung + Brennweite f kalibriert.
 * Der eigentliche Patch-Solve wird als [patchSolver] injiziert (ASTAP oder Nova).
 */
class PanoramaSolver {

    fun interface PatchSolver {
        /** Löst ein flaches Mini-Bild (~[fovDeg]° Bildhöhe), null = nicht gelöst. */
        suspend fun solve(mini: Bitmap, fovDeg: Float): WcsSolution?
    }

    suspend fun solveFisheye(
        bitmap: Bitmap,
        patchSolver: PatchSolver,
        assumedWidthFovsDeg: List<Double> = DEFAULT_ASSUMED_WIDTH_FOVS,
        onStatus: suspend (String) -> Unit,
    ): PanoramaSolveResult = withContext(Dispatchers.Default) {
        val cx = bitmap.width / 2.0
        val cy = bitmap.height / 2.0

        // ============================================================================
        // Plan B (bevorzugt): roher zentraler Crop, als flaches Einzelbild gelöst.
        // Der Zentralbereich eines Fisheye ist nahezu rektilinear -> löst als TAN+SIP
        // (genau der Weg, der am Gerät zuverlässig löst). Aus der ECHTEN WCS leiten wir
        // f (zentraler Pixelmaßstab) + globale Orientierung ab – ohne Reprojektion und
        // ohne einen Maßstab raten zu müssen. Schlägt das fehl, folgt der Reproject-Sweep.
        // ============================================================================
        val cropFraction = CENTER_CROP_FRACTION
        val cropW = (bitmap.width * cropFraction).roundToInt().coerceAtLeast(64)
        val cropH = (bitmap.height * cropFraction).roundToInt().coerceAtLeast(64)
        val cropX = (bitmap.width - cropW) / 2
        val cropY = (bitmap.height - cropH) / 2

        // Aus der echten Crop-WCS f + Drehung ableiten. f = zentraler Pixelmaßstab,
        // Korrespondenzen (Crop-Pixel -> equatorial) gegen die Fisheye-Kamerarichtung gefittet.
        fun calibrateFromCrop(cropWcs: WcsSolution): PanoramaSolveResult? {
            val ccx = cropW / 2.0
            val ccy = cropH / 2.0
            // WICHTIG: WcsSolution.imageToSky ignoriert SIP. Über den ganzen Crop (±~22°, große
            // Fisheye-SIP) verfälscht das die Stützpunkte massiv (Bootstrap-Restfehler >200').
            // Daher NUR im zentralen, fast verzeichnungsfreien Bereich kalibrieren (SIP≈0):
            //  - f aus kleinem delta (lokaler Maßstab am Zentrum),
            //  - Korrespondenzen im zentralen ~25%-Fenster.
            val delta = (cropW.coerceAtMost(cropH) * 0.06).coerceAtLeast(8.0)
            val skyC = cropWcs.imageToSky(ccx, ccy, cropH)
            val skyR = cropWcs.imageToSky(ccx + delta, ccy, cropH)
            val angle = angleBetween(
                raDecToVector(skyC.raDegrees.toDouble(), skyC.decDegrees.toDouble()),
                raDecToVector(skyR.raDegrees.toDouble(), skyR.decDegrees.toDouble()),
            )
            if (angle < 1e-9) return null
            val f = delta / angle
            if (!f.isFinite() || f <= 1.0) return null
            val fovWidthDeg = (bitmap.width / f * 180.0 / PI).toFloat()
            if (!fovWidthDeg.isFinite() || fovWidthDeg < 15f || fovWidthDeg > 250f) return null

            // Parität ist a priori unbekannt -> beide probieren. Ein Spiegel-Mismatch zwischen
            // Kamera-Frame und WCS-Himmel kann von der reinen (Eigen-)Rotation NICHT korrigiert
            // werden und liefert genau so ein großes Rest-Plateau. Kleineres Residuum gewinnt.
            data class ParityFit(val rotResidualArcmin: Float, val result: PanoramaSolveResult)
            fun fitParity(flipY: Boolean): ParityFit? {
                val projection = FisheyeProjection(cx, cy, f, flipY = flipY)
                val correspondences = mutableListOf<RotationFit.Correspondence>()
                val grid = 6
                val regionFrac = 0.30 // zentrales Fenster: geringe SIP-Verzeichnung
                for (gx in 0 until grid) for (gy in 0 until grid) {
                    val u = ccx + ((gx + 0.5) / grid - 0.5) * cropW * regionFrac
                    val v = ccy + ((gy + 0.5) / grid - 0.5) * cropH * regionFrac
                    val camDir = projection.pixelToDirection(cropX + u, cropY + v) ?: continue
                    val sky = cropWcs.imageToSky(u, v, cropH)
                    correspondences += RotationFit.Correspondence(
                        equatorial = raDecToVector(sky.raDegrees.toDouble(), sky.decDegrees.toDouble()),
                        pano = camDir,
                    )
                }
                // solveRobust (Huber-IRLS) statt solve(): hier gibt es -- anders als in
                // FisheyeRefiner.kt -- KEINEN nachfolgenden LM-Fit, der einzelne Ausreisser unter
                // den 36 Gitterpunkten kompensieren wuerde; rot wird direkt als finale Rotation
                // verwendet. rmsDegrees ersetzt die bisherige manuelle Restfehler-Schleife (macht
                // dieselbe Rechnung, nur ueber die robust gewichtete statt die rohe Rotation).
                val fit = RotationFit.solveRobust(correspondences) ?: return null
                val rot = fit.rotation
                val residualArcmin = (fit.rmsDegrees * 60.0).toFloat()
                return ParityFit(
                    residualArcmin,
                    PanoramaSolveResult(
                        wcs = PanoramaWcsSolution(projection, rot),
                        debug = PanoramaDebug(
                            solvedPatches = 1,
                            patchCentersImage = listOf(Offset(cx.toFloat(), cy.toFloat())),
                            residualArcmin = residualArcmin,
                            fieldOfViewWidthDeg = fovWidthDeg,
                        ),
                        log = "",
                    ),
                )
            }

            val normal = fitParity(false)
            val flipped = fitParity(true)
            val best = listOfNotNull(normal, flipped).minByOrNull { it.rotResidualArcmin } ?: return null
            val rn = normal?.rotResidualArcmin ?: Float.NaN
            val rf = flipped?.rotResidualArcmin ?: Float.NaN
            return best.result.copy(
                log = "fisheye (center-crop) solved: f=${f.roundToInt()} px/rad, " +
                    "fovWidth=${fovWidthDeg.roundToInt()}°, residual=${"%.1f".format(best.rotResidualArcmin)}' " +
                    "parity(normal=${"%.1f".format(rn)}',flip=${"%.1f".format(rf)}')",
            )
        }

        val crop = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
        var bestCrop: PanoramaSolveResult? = null
        try {
            for (spanDeg in assumedWidthFovsDeg) {
                onStatus("Zentrum lösen (Center-Crop, Bildfeld ~${spanDeg.roundToInt()}°) ...")
                // Crop deckt cropFraction der Bildbreite ab -> entsprechend kleineres Bildfeld.
                val cropFovDeg = (spanDeg * cropFraction).toFloat()
                val wcs = patchSolver.solve(crop, cropFovDeg) ?: continue
                val result = calibrateFromCrop(wcs) ?: continue
                if (bestCrop == null || result.debug.residualArcmin < bestCrop.debug.residualArcmin) {
                    bestCrop = result
                }
                if (result.debug.residualArcmin < 20f) break
            }
        } finally {
            crop.recycle()
        }
        if (bestCrop != null) return@withContext bestCrop
        onStatus("Center-Crop nicht lösbar – Reprojektion versuchen ...")

        // ---- Fallback: Reprojektions-Sweep (Phase-1-Verfahren). ----
        val sourcePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val miniSize = MINI_SIZE
        val miniHalfFov = (PATCH_FOV_DEGREES / 2.0) * PI / 180.0
        val fMini = (miniSize / 2.0) / tan(miniHalfFov)

        // Kamerarichtung eines Mini-Pixels (gnomonisch um die +Z-Achse zentriert).
        // Unabhängig von f0 – f0 bestimmt nur, WO im Quellbild gesampelt wird.
        fun miniToCamDir(mx: Double, my: Double): Vec3 {
            val tx = (mx - miniSize / 2.0) / fMini
            val ty = (my - miniSize / 2.0) / fMini
            return Vec3(tx, ty, 1.0).normalized()
        }

        // Aus einem gelösten Mini-Patch (f0 + miniWcs) die volle zentrale Lösung berechnen:
        // f kalibrieren (Bildmitte exakt, Randstrahl liefert Maßstab), Korrespondenzen mit
        // kalibriertem f bilden, globale Drehung fitten, zentralen Restfehler messen.
        // Liefert null bei unplausibler/degenerierter Lösung.
        fun calibrate(f0: Double, miniWcs: WcsSolution): PanoramaSolveResult? {
            val projection0 = FisheyeProjection(cx, cy, f0)
            val centerSky = miniWcs.imageToSky(miniSize / 2.0, miniSize / 2.0, miniSize)
            val edgeSky = miniWcs.imageToSky((miniSize - 1).toDouble(), miniSize / 2.0, miniSize)
            val edgeDir = miniToCamDir((miniSize - 1).toDouble(), miniSize / 2.0)
            val thetaEdge = acos(edgeDir.z.coerceIn(-1.0, 1.0))
            val deltaReal = angleBetween(
                raDecToVector(centerSky.raDegrees.toDouble(), centerSky.decDegrees.toDouble()),
                raDecToVector(edgeSky.raDegrees.toDouble(), edgeSky.decDegrees.toDouble()),
            )
            val f = if (deltaReal > 1e-6) f0 * (thetaEdge / deltaReal) else f0
            if (!f.isFinite() || f <= 1.0) return null
            val projection = FisheyeProjection(cx, cy, f)

            val correspondences = mutableListOf<RotationFit.Correspondence>()
            val grid = 5
            for (gx in 0 until grid) for (gy in 0 until grid) {
                val mx = (gx + 0.5) * miniSize / grid
                val my = (gy + 0.5) * miniSize / grid
                val sampledPano = projection0.directionToPixel(miniToCamDir(mx, my)) ?: continue
                val camTrue = projection.pixelToDirection(
                    sampledPano.x.toDouble(), sampledPano.y.toDouble(),
                ) ?: continue
                val sky = miniWcs.imageToSky(mx, my, miniSize)
                correspondences += RotationFit.Correspondence(
                    equatorial = raDecToVector(sky.raDegrees.toDouble(), sky.decDegrees.toDouble()),
                    pano = camTrue,
                )
            }
            // solveRobust (Huber-IRLS) statt solve(): s. Kommentar in fitParity() oben -- auch hier
            // kein nachfolgender LM-Fit, rot wird direkt verwendet.
            val fit = RotationFit.solveRobust(correspondences) ?: return null
            val rot = fit.rotation
            val residualArcmin = (fit.rmsDegrees * 60.0).toFloat()
            val fovWidthDeg = (bitmap.width / f * 180.0 / PI).toFloat()
            if (!fovWidthDeg.isFinite() || fovWidthDeg < 15f || fovWidthDeg > 250f) return null

            return PanoramaSolveResult(
                wcs = PanoramaWcsSolution(projection, rot),
                debug = PanoramaDebug(
                    solvedPatches = 1,
                    patchCentersImage = listOf(Offset(cx.toFloat(), cy.toFloat())),
                    residualArcmin = residualArcmin,
                    fieldOfViewWidthDeg = fovWidthDeg,
                ),
                log = "fisheye solved: f=${f.roundToInt()} px/rad, fovWidth=${fovWidthDeg.roundToInt()}°, " +
                    "residual=${"%.1f".format(residualArcmin)}'",
            )
        }

        // Skala-Sweep: das echte Bildfeld ist unbekannt. Jeden angenommenen Wert entzerren,
        // lösen, kalibrieren – und die Lösung mit dem KLEINSTEN zentralen Restfehler nehmen.
        // (Der erste lösbare Kandidat kann eine Falsch-/Müll-Lösung sein; Min-Restfehler
        // verwirft sie.) So braucht der Nutzer keine FOV-Eingabe.
        var best: PanoramaSolveResult? = null
        for (spanDeg in assumedWidthFovsDeg) {
            onStatus("Zentrum lösen (Bildfeld ~${spanDeg.roundToInt()}°) ...")
            val candidate = bitmap.width / (spanDeg * PI / 180.0)
            val projection = FisheyeProjection(cx, cy, candidate)
            val mini = buildMiniBitmap(
                sourcePixels, bitmap.width, bitmap.height, miniSize, projection, ::miniToCamDir,
            )
            val wcs = patchSolver.solve(mini, PATCH_FOV_DEGREES)
            mini.recycle()
            if (wcs == null) continue
            val result = calibrate(candidate, wcs) ?: continue
            if (best == null || result.debug.residualArcmin < best.debug.residualArcmin) {
                best = result
            }
            // Sehr gute Zentral-Lösung -> nicht weiter probieren (spart ASTAP-Läufe).
            if (result.debug.residualArcmin < 30f) break
        }
        best ?: throw PanoramaSolveException(
            "Die Bildmitte ließ sich bei keinem angenommenen Bildfeld lösen. Ist es wirklich " +
                "ein (Ultra-)Weitwinkel-/Fisheye-Bild? Normale Objektive im Modus \"Einzelbild\" lösen.",
        )
    }

    private fun buildMiniBitmap(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        miniSize: Int,
        projection: FisheyeProjection,
        miniToCamDir: (Double, Double) -> Vec3,
    ): Bitmap {
        val out = IntArray(miniSize * miniSize)
        for (my in 0 until miniSize) {
            for (mx in 0 until miniSize) {
                val dir = miniToCamDir(mx + 0.5, my + 0.5)
                val p = projection.directionToPixel(dir)
                out[my * miniSize + mx] = if (p == null) {
                    0xFF000000.toInt()
                } else {
                    sampleBilinear(src, srcW, srcH, p.x, p.y)
                }
            }
        }
        return Bitmap.createBitmap(out, miniSize, miniSize, Bitmap.Config.ARGB_8888)
    }

    private fun sampleBilinear(src: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        if (x < 0f || y < 0f || x > w - 1f || y > h - 1f) return 0xFF000000.toInt()
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(w - 1); val y1 = (y0 + 1).coerceAtMost(h - 1)
        val fx = x - x0; val fy = y - y0
        val c00 = src[y0 * w + x0]; val c10 = src[y0 * w + x1]
        val c01 = src[y1 * w + x0]; val c11 = src[y1 * w + x1]
        fun lerpCh(shift: Int): Int {
            val a = ((c00 ushr shift) and 0xFF) * (1 - fx) + ((c10 ushr shift) and 0xFF) * fx
            val b = ((c01 ushr shift) and 0xFF) * (1 - fx) + ((c11 ushr shift) and 0xFF) * fx
            return (a * (1 - fy) + b * fy).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (lerpCh(16) shl 16) or (lerpCh(8) shl 8) or lerpCh(0)
    }

    private fun angleBetween(a: Vec3, b: Vec3): Double =
        acos(a.normalized().dot(b.normalized()).coerceIn(-1.0, 1.0))

    companion object {
        /**
         * Bildhöhe des entzerrten Mini-Patches in Grad. Größer = mehr Sterne im Patch
         * (kleinere Patches scheiterten am Lösen, vgl. funktionierender manueller Center-Crop).
         * 40° gnomonisch ist noch wenig verzerrt (±20°), die Solver/SIP fangen den Rest ab.
         */
        const val PATCH_FOV_DEGREES = 40f
        private const val MINI_SIZE = 1200

        /**
         * Anteil der Bildkante, der für den Fisheye-Center-Crop-Solve verwendet wird (zentrales
         * Fenster). Geteilt mit dem Export („gelösten Ausschnitt exportieren"), damit die
         * Gegenprobe exakt denselben Ausschnitt zeigt, der gelöst wurde.
         */
        const val CENTER_CROP_FRACTION = 0.5

        /**
         * Angenommene Gesamt-Bildfelder (Breite, Grad) für den Skala-Sweep des Zentralpatches.
         * Deckt von Ultra-Fisheye bis normalem Weitwinkel ab; jeder Kandidat fängt dank des
         * ASTAP-internen ±-Sweeps einen Faktor ~1,5 ab -> lückenlose Abdeckung.
         */
        val DEFAULT_ASSUMED_WIDTH_FOVS = listOf(180.0, 120.0, 80.0, 55.0)
    }
}
