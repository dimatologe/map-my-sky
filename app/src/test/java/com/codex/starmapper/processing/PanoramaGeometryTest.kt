package com.codex.starmapper.processing

import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PanoramaGeometryTest {

    // --- FisheyeProjection ---------------------------------------------------

    @Test
    fun fisheyeCenterMapsToOpticalAxis() {
        val projection = FisheyeProjection(cx = 1500.0, cy = 1500.0, f = 1000.0)
        val dir = projection.pixelToDirection(1500.0, 1500.0)
        assertNotNull(dir)
        assertEquals(0.0, dir!!.x, 1e-9)
        assertEquals(0.0, dir.y, 1e-9)
        assertEquals(1.0, dir.z, 1e-9)
    }

    @Test
    fun fisheyePixelRoundTrip() {
        val projection = FisheyeProjection(cx = 1500.0, cy = 1500.0, f = 1000.0)
        val samples = listOf(
            1600.0 to 1500.0,
            1500.0 to 1650.0,
            1750.0 to 1300.0,
            1200.0 to 1800.0,
        )
        for ((px, py) in samples) {
            val dir = projection.pixelToDirection(px, py)
            assertNotNull("dir for ($px,$py)", dir)
            val back = projection.directionToPixel(dir!!)
            assertNotNull("pixel for ($px,$py)", back)
            assertEquals("x for ($px,$py)", px.toFloat(), back!!.x, 0.01f)
            assertEquals("y for ($px,$py)", py.toFloat(), back.y, 0.01f)
        }
    }

    @Test
    fun fisheyeRadiusGrowsLinearlyWithAngle() {
        // equidistant: r = f * theta. Ein 30deg-Strahl liegt bei f*theta Pixeln.
        val f = 900.0
        val projection = FisheyeProjection(cx = 0.0, cy = 0.0, f = f)
        val theta = 30.0 * PI / 180.0
        val dir = Vec3(sin(theta), 0.0, cos(theta))
        val pixel = projection.directionToPixel(dir)
        assertNotNull(pixel)
        assertEquals((f * theta).toFloat(), hypot(pixel!!.x.toDouble(), pixel.y.toDouble()).toFloat(), 0.05f)
    }

    @Test
    fun fisheyeRejectsAntipode() {
        val projection = FisheyeProjection(cx = 0.0, cy = 0.0, f = 900.0)
        assertNull(projection.directionToPixel(Vec3(0.0, 0.0, -1.0)))
    }

    // --- StereographicProjection ---------------------------------------------

    @Test
    fun stereographicCenterMapsToOpticalAxis() {
        val projection = StereographicProjection(cx = 1500.0, cy = 1500.0, f = 1000.0)
        val dir = projection.pixelToDirection(1500.0, 1500.0)
        assertNotNull(dir)
        assertEquals(0.0, dir!!.x, 1e-9)
        assertEquals(0.0, dir.y, 1e-9)
        assertEquals(1.0, dir.z, 1e-9)
    }

    @Test
    fun stereographicPixelRoundTrip() {
        val projection = StereographicProjection(cx = 1500.0, cy = 1500.0, f = 1000.0)
        val samples = listOf(
            1600.0 to 1500.0,
            1500.0 to 1650.0,
            1750.0 to 1300.0,
            1200.0 to 1800.0,
            2700.0 to 2700.0, // weit außen (>90°) – das Stereografische darf das abbilden
        )
        for ((px, py) in samples) {
            val dir = projection.pixelToDirection(px, py)
            assertNotNull("dir for ($px,$py)", dir)
            val back = projection.directionToPixel(dir!!)
            assertNotNull("pixel for ($px,$py)", back)
            assertEquals("x for ($px,$py)", px.toFloat(), back!!.x, 0.02f)
            assertEquals("y for ($px,$py)", py.toFloat(), back.y, 0.02f)
        }
    }

    @Test
    fun stereographicRadiusMatchesFormula() {
        // r = 2 f tan(theta/2); auch jenseits 90° gültig (anders als equidistantes Fisheye).
        val f = 900.0
        val projection = StereographicProjection(cx = 0.0, cy = 0.0, f = f)
        val theta = 100.0 * PI / 180.0
        val dir = Vec3(sin(theta), 0.0, cos(theta))
        val pixel = projection.directionToPixel(dir)
        assertNotNull(pixel)
        val expected = 2.0 * f * kotlin.math.tan(theta / 2.0)
        assertEquals(expected.toFloat(), hypot(pixel!!.x.toDouble(), pixel.y.toDouble()).toFloat(), 0.05f)
    }

    @Test
    fun stereographicRejectsAntipode() {
        val projection = StereographicProjection(cx = 0.0, cy = 0.0, f = 900.0)
        assertNull(projection.directionToPixel(Vec3(0.0, 0.0, -1.0)))
    }

    // --- raDec helpers -------------------------------------------------------

    @Test
    fun raDecVectorRoundTrip() {
        val cases = listOf(
            0.0 to 0.0,
            45.0 to 30.0,
            123.4 to -56.7,
            359.9 to 12.3,
        )
        for ((ra, dec) in cases) {
            val (ra2, dec2) = vectorToRaDec(raDecToVector(ra, dec))
            assertEquals("ra $ra", ra, ra2, 1e-6)
            assertEquals("dec $dec", dec, dec2, 1e-6)
        }
    }

    // --- RotationFit (Wahba/Davenport) ---------------------------------------

    @Test
    fun rotationFitRecoversKnownRotation() {
        // Bekannte Drehung um die Z-Achse um 37deg.
        val a = 37.0 * PI / 180.0
        val truth = Mat3(
            cos(a), -sin(a), 0.0,
            sin(a), cos(a), 0.0,
            0.0, 0.0, 1.0,
        )
        val reference = listOf(
            raDecToVector(10.0, 20.0),
            raDecToVector(80.0, -15.0),
            raDecToVector(200.0, 45.0),
            raDecToVector(300.0, 5.0),
        )
        val correspondences = reference.map {
            RotationFit.Correspondence(equatorial = it, pano = truth * it)
        }
        val solved = RotationFit.solve(correspondences)
        assertNotNull(solved)

        val test = raDecToVector(135.0, -33.0)
        val expected = truth * test
        val actual = solved!! * test
        assertEquals(expected.x, actual.x, 1e-6)
        assertEquals(expected.y, actual.y, 1e-6)
        assertEquals(expected.z, actual.z, 1e-6)
    }

    @Test
    fun rotationFitNeedsTwoPairs() {
        assertNull(RotationFit.solve(listOf(RotationFit.Correspondence(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0)))))
    }

    // --- PanoramaWcsSolution -------------------------------------------------

    @Test
    fun panoramaSolutionMapsPoleToCenterUnderIdentity() {
        val projection = FisheyeProjection(cx = 500.0, cy = 500.0, f = 300.0)
        val solution = PanoramaWcsSolution(projection, Mat3.IDENTITY)
        // Dec = +90deg -> äquatorialer Vektor (0,0,1) -> optische Achse -> Bildmitte.
        val pixel = solution.skyToImage(SkyPoint(123f, 90f), imageHeight = 1000)
        assertNotNull(pixel)
        assertEquals(500f, pixel!!.x, 0.01f)
        assertEquals(500f, pixel.y, 0.01f)
    }

    @Test
    fun panoramaSolutionPlacesOffAxisPointOnExpectedRadius() {
        val f = 300.0
        val projection = FisheyeProjection(cx = 500.0, cy = 500.0, f = f)
        val solution = PanoramaWcsSolution(projection, Mat3.IDENTITY)
        // Dec = 80deg, RA = 0 -> 10deg von der Achse entlang +x.
        val pixel = solution.skyToImage(SkyPoint(0f, 80f), imageHeight = 1000)
        assertNotNull(pixel)
        val r = hypot((pixel!!.x - 500f).toDouble(), (pixel.y - 500f).toDouble())
        assertEquals(f * (10.0 * PI / 180.0), r, 0.1)
    }

    // --- WcsSolution.imageToSky (inverse) ------------------------------------

    @Test
    fun wcsImageToSkyIsInverseOfSkyToImage() {
        val solution = WcsSolution(
            crPix1 = 500.0,
            crPix2 = 400.0,
            crVal1Degrees = 10.0,
            crVal2Degrees = 20.0,
            cd11 = 0.001,
            cd12 = 0.0,
            cd21 = 0.0,
            cd22 = 0.001,
        )
        val height = 800
        val samples = listOf(520.0 to 410.0, 480.0 to 360.0, 600.0 to 500.0)
        for ((px, py) in samples) {
            val sky = solution.imageToSky(px, py, height)
            val back = solution.skyToImage(sky, height)
            assertNotNull(back)
            assertEquals("x ($px,$py)", px.toFloat(), back!!.x, 0.05f)
            assertEquals("y ($px,$py)", py.toFloat(), back.y, 0.05f)
        }
    }

    // --- SolveController phase bridge ----------------------------------------

    @Test
    fun solveControllerCancelInvokesHandlerOnce() {
        var cancels = 0
        com.codex.starmapper.solve.SolveController.begin("t", "x") { cancels++ }
        assertTrue(com.codex.starmapper.solve.SolveController.status.value.active)
        com.codex.starmapper.solve.SolveController.requestCancel()
        com.codex.starmapper.solve.SolveController.finish()
        // Nach finish() ist kein Handler mehr registriert -> kein weiterer Abbruch.
        com.codex.starmapper.solve.SolveController.requestCancel()
        assertEquals(1, cancels)
        assertTrue(!com.codex.starmapper.solve.SolveController.status.value.active)
    }
}
