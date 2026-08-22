package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Sichert den robusten Vollbild-Fit aus manuellen Referenzen: aus Bildpixel↔Katalogrichtung-Paaren
 * eines bekannten Fisheye-Modells (mit radialer Verzeichnung + Rotation) muss
 * [FisheyeRefiner.calibrateFromReferences] eine Lösung zurückgewinnen, die die Referenzen genau
 * reprojiziert.
 */
class ManualFisheyeFitTest {

    @Test
    fun recoversKnownDistortionFromReferences() {
        val width = 1853
        val height = 2780
        val truth = FisheyeProjection(cx = 926.5, cy = 1390.0, f = 1300.0, k1 = 0.05)
        // Wahre Rotation (äquatorial -> Kamera): 90° um die x-Achse (gültige Rotation, det=+1).
        val rot = Mat3(
            1.0, 0.0, 0.0,
            0.0, 0.0, -1.0,
            0.0, 1.0, 0.0,
        )
        val rotInverse = rot.transpose()

        // Über das Bild verteilte Referenzpixel (Mitte, Kanten, Ecken).
        val pixels = listOf(
            Offset(926f, 1390f), Offset(300f, 400f), Offset(1500f, 400f),
            Offset(300f, 2300f), Offset(1500f, 2300f), Offset(926f, 200f),
            Offset(926f, 2500f), Offset(200f, 1390f), Offset(1650f, 1390f),
            Offset(700f, 900f), Offset(1200f, 1900f), Offset(450f, 1800f),
        )
        // Referenz = (Pixel, äquatoriale Richtung), sodass rot * dir = Kamerarichtung des Pixels.
        val refs = pixels.mapNotNull { p ->
            val cam = truth.pixelToDirection(p.x.toDouble(), p.y.toDouble()) ?: return@mapNotNull null
            p to (rotInverse * cam)
        }
        assertTrue("zu wenige Referenzen erzeugt", refs.size >= 8)

        val solution = FisheyeRefiner.calibrateFromReferences(refs, width, height)
        assertNotNull("Fit lieferte null", solution)
        solution!!

        var sumSq = 0.0
        for ((pixel, dir) in refs) {
            val projected = solution.projection.directionToPixel(solution.rotEquToPano * dir)
            assertNotNull("Reprojektion null", projected)
            val d = hypot((projected!!.x - pixel.x).toDouble(), (projected.y - pixel.y).toDouble())
            sumSq += d * d
        }
        val rms = sqrt(sumSq / refs.size)
        assertTrue("Reprojektions-RMS zu groß: $rms px", rms < 6.0)
    }
}
