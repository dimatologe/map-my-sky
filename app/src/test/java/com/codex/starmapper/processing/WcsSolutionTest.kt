package com.codex.starmapper.processing

import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [WcsSolutionLike.imageToSkyApprox] -- Rundreise-Tests (skyToImage -> imageToSkyApprox -> ungefähr
 * derselbe SkyPoint) statt Zahlen von Hand nachzurechnen, da eine echte Gnomonik-/Panorama-Umkehrung
 * von Hand fehleranfällig wäre -- die Rundreise-Eigenschaft ist der eigentlich relevante Vertrag für
 * den verbleibenden Nutzungszweck (grobe Sichtfeld-Bounding-Box für die Tycho-2-Katalogabfrage,
 * `raDecBoundingBox`/`sampleSkyPoints`, keine Präzisionsplatzierung).
 */
class WcsSolutionTest {

    @Test
    fun imageToSkyApproxRoundTripsForTanSolution() {
        val wcs = WcsSolution(
            crPix1 = 500.0,
            crPix2 = 400.0,
            crVal1Degrees = 100.0,
            crVal2Degrees = 20.0,
            cd11 = 0.01,
            cd12 = 0.0,
            cd21 = 0.0,
            cd22 = 0.01,
            flipY = false,
        )
        val original = SkyPoint(101.5f, 21.2f)
        val pixel = wcs.skyToImage(original, imageHeight = 800)
        requireNotNull(pixel)
        val recovered = wcs.imageToSkyApprox(pixel.x.toDouble(), pixel.y.toDouble(), imageHeight = 800)
        requireNotNull(recovered)
        assertEquals(original.raDegrees, recovered.raDegrees, 0.01f)
        assertEquals(original.decDegrees, recovered.decDegrees, 0.01f)
    }

    @Test
    fun imageToSkyApproxRoundTripsForPanoramaSolution() {
        val wcs = PanoramaWcsSolution(
            CylindricalProjection(cx = 500.0, cy = 400.0, fx = 100.0, fy = 100.0, kind = PanoProjectionKind.Equirectangular),
            Mat3.IDENTITY,
        )
        val original = SkyPoint(15f, -30f)
        val pixel = wcs.skyToImage(original, imageHeight = 800)
        requireNotNull(pixel)
        val recovered = wcs.imageToSkyApprox(pixel.x.toDouble(), pixel.y.toDouble(), imageHeight = 800)
        requireNotNull(recovered)
        assertEquals(original.raDegrees, recovered.raDegrees, 0.01f)
        assertEquals(original.decDegrees, recovered.decDegrees, 0.01f)
    }

    @Test
    fun imageToSkyApproxReturnsNullForMosaicSolution() {
        val wcs = MosaicWcsSolution(tiles = emptyList())
        assertNull(wcs.imageToSkyApprox(100.0, 100.0, imageHeight = 800))
    }
}
