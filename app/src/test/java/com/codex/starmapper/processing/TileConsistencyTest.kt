package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TileConsistencyTest {

    private fun rotZ(a: Double) = Mat3(cos(a), -sin(a), 0.0, sin(a), cos(a), 0.0, 0.0, 0.0, 1.0)
    private fun rotX(a: Double) = Mat3(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))

    private fun camDir(thetaDeg: Double, phiDeg: Double): Vec3 {
        val t = thetaDeg * PI / 180.0
        val p = phiDeg * PI / 180.0
        return Vec3(sin(t) * cos(p), sin(t) * sin(p), cos(t))
    }

    private val imgW = 6000
    private val imgH = 4000
    private val rotTrue = rotZ(0.4) * rotX(0.25) // eq -> pano
    private val trueProj = StereographicProjection(3000.0, 2000.0, 1400.0)
    private val allowed = setOf(PanoProjectionKind.Stereographic)

    /** Kachel: 3x3-Anker um (thetaDeg, phiDeg), konsistent mit der wahren Projektion + Rauschen. */
    private fun goodTile(
        id: Long,
        thetaDeg: Double,
        phiDeg: Double,
        noisePx: Float = 0f,
        random: Random = Random(42),
    ): Pair<Long, List<Pair<Offset, Vec3>>> {
        val anchors = mutableListOf<Pair<Offset, Vec3>>()
        for (dt in listOf(-4.0, 0.0, 4.0)) for (dp in listOf(-6.0, 0.0, 6.0)) {
            val cam = camDir(thetaDeg + dt, phiDeg + dp)
            val eq = rotTrue.transpose() * cam
            val pix = trueProj.directionToPixel(rotTrue * eq) ?: continue
            val noisy = Offset(
                pix.x + (random.nextFloat() - 0.5f) * 2f * noisePx,
                pix.y + (random.nextFloat() - 0.5f) * 2f * noisePx,
            )
            anchors += noisy to eq
        }
        return id to anchors
    }

    /** Falsch gelöste Kachel: Pixel wie eine echte Kachel, aber Himmel um [offsetDeg] versetzt. */
    private fun badTile(id: Long, thetaDeg: Double, phiDeg: Double, offsetDeg: Double): Pair<Long, List<Pair<Offset, Vec3>>> {
        val anchors = mutableListOf<Pair<Offset, Vec3>>()
        for (dt in listOf(-4.0, 0.0, 4.0)) for (dp in listOf(-6.0, 0.0, 6.0)) {
            val cam = camDir(thetaDeg + dt, phiDeg + dp)
            val pix = trueProj.directionToPixel(cam) ?: continue
            // Anker behauptet einen um offsetDeg verschobenen Himmel -> widerspricht allen anderen.
            val wrongCam = camDir(thetaDeg + dt + offsetDeg, phiDeg + dp)
            anchors += pix to (rotTrue.transpose() * wrongCam)
        }
        return id to anchors
    }

    @Test
    fun flagsTheFalselySolvedTile() {
        val tiles = listOf(
            goodTile(1, 15.0, 0.0),
            goodTile(2, 30.0, 90.0),
            goodTile(3, 45.0, 180.0),
            goodTile(4, 30.0, 270.0),
            goodTile(5, 60.0, 45.0),
            badTile(6, 50.0, 135.0, offsetDeg = 40.0),
        )
        val outliers = TileConsistency.flagOutliers(tiles, imgW, imgH, allowed)
        assertEquals("genau eine Kachel geflaggt", 1, outliers.size)
        assertEquals(6L, outliers.first().tileId)
        assertTrue("großer Fehler: ${outliers.first().medianErrorPx}", outliers.first().medianErrorPx > 200.0)
    }

    @Test
    fun consistentTilesWithNoiseAreNotFlagged() {
        // ~50 px Rauschen simuliert Modell-Restfehler am Rand -> darf NICHT flaggen.
        val random = Random(7)
        val tiles = (1L..5L).mapIndexed { i, id ->
            goodTile(id, 15.0 + i * 12.0, i * 72.0, noisePx = 50f, random = random)
        }
        val outliers = TileConsistency.flagOutliers(tiles, imgW, imgH, allowed)
        assertTrue("keine Flags, aber: $outliers", outliers.isEmpty())
    }

    @Test
    fun tooFewTilesNeverFlag() {
        val tiles = listOf(
            goodTile(1, 20.0, 0.0),
            badTile(2, 40.0, 90.0, offsetDeg = 40.0),
        )
        val outliers = TileConsistency.flagOutliers(tiles, imgW, imgH, allowed)
        assertTrue("unter MIN_TILES keine Entscheidung", outliers.isEmpty())
    }

    /** Wie [goodTile], aber mit deutlich dichterem Raster (mehr Anker) -- simuliert eine
     *  De-Warp-Kachel (bis zu 25 Anker) neben normalen 3x3-Kacheln (9 Anker). */
    private fun denseGoodTile(id: Long, thetaDeg: Double, phiDeg: Double): Pair<Long, List<Pair<Offset, Vec3>>> {
        val anchors = mutableListOf<Pair<Offset, Vec3>>()
        for (dt in listOf(-8.0, -4.0, 0.0, 4.0, 8.0)) for (dp in listOf(-12.0, -6.0, 0.0, 6.0, 12.0)) {
            val cam = camDir(thetaDeg + dt, phiDeg + dp)
            val eq = rotTrue.transpose() * cam
            val pix = trueProj.directionToPixel(rotTrue * eq) ?: continue
            anchors += pix to eq
        }
        return id to anchors
    }

    @Test
    fun flagsBadTileEvenWhenAGoodTileHasManyMoreAnchors() {
        // Eine gute Kachel liefert absichtlich viel mehr Anker (bis 25) als die übrigen (9) --
        // der interne Referenz-Fit in flagOutliers ist jetzt kachel-gewichtet (s.
        // FisheyeRefiner.tileVoteWeights), damit die ankerreiche Kachel den gemeinsamen Fit nicht
        // unverhältnismäßig dominiert. Regressionstest: die schlecht gelöste Kachel muss trotz der
        // ungleichen Ankerzahlen weiterhin zuverlässig erkannt werden.
        val tiles = listOf(
            denseGoodTile(1, 15.0, 0.0),
            goodTile(2, 30.0, 90.0),
            goodTile(3, 45.0, 180.0),
            goodTile(4, 30.0, 270.0),
            goodTile(5, 60.0, 45.0),
            badTile(6, 50.0, 135.0, offsetDeg = 40.0),
        )
        val outliers = TileConsistency.flagOutliers(tiles, imgW, imgH, allowed)
        assertEquals("genau eine Kachel geflaggt", 1, outliers.size)
        assertEquals(6L, outliers.first().tileId)
    }

    // Einfache lineare Platten-Lösung (wie WcsSolutionTest.basicSolution) -- eigener, unabhängiger
    // Fixture-Satz hier, da overlapDisagreement direkt auf TileWcs/WcsSolutionLike arbeitet, nicht auf
    // den obigen (Vec3, PanoramaProjection)-Fixtures der flagOutliers-Tests.
    private fun basicSolution() = WcsSolution(
        crPix1 = 501.0,
        crPix2 = 400.0,
        crVal1Degrees = 0.0,
        crVal2Degrees = 0.0,
        cd11 = 0.01,
        cd12 = 0.0,
        cd21 = 0.0,
        cd22 = 0.01,
    )

    /** Ein paar Himmelspunkte nahe des Tangentialpunkts (0,0) -> lineare Näherung der Plattenlösung
     *  bleibt praktisch exakt, unabhängig von Projektionskrümmung fernab der Bildmitte. */
    private val nearCenterSkyPoints = listOf(
        SkyPoint(0f, 0f), SkyPoint(0.005f, 0f), SkyPoint(0f, 0.005f), SkyPoint(-0.005f, -0.005f),
    )

    private fun corrRefsFrom(wcs: WcsSolutionLike, tileHeight: Int, skyPoints: List<SkyPoint>): List<Pair<Offset, Vec3>> =
        skyPoints.mapNotNull { sp ->
            val local = wcs.skyToImage(sp, tileHeight) ?: return@mapNotNull null
            local to raDecToVector(sp.raDegrees.toDouble(), sp.decDegrees.toDouble())
        }

    @Test
    fun overlapDisagreementIsNearZeroForConsistentOverlappingTiles() {
        val tileA = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tileB = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val refsA = corrRefsFrom(tileA.wcs, tileA.tileHeight, nearCenterSkyPoints)

        val result = TileConsistency.overlapDisagreement(
            listOf(1L to tileA, 2L to tileB), mapOf(1L to refsA),
        )

        assertEquals(nearCenterSkyPoints.size, result[1L]?.matchCount)
        assertEquals(0.0, result.getValue(1L).rmsPx!!, 0.01)
    }

    @Test
    fun overlapDisagreementMatchesInjectedOffsetBetweenOverlappingTiles() {
        val tileA = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        // 10px-Versatz (gleiche Technik wie WcsSolutionTest.skyToImageBlendsBetweenTwoOverlappingTiles...).
        val tileB = TileWcs(basicSolution().copy(crPix1 = 511.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val refsA = corrRefsFrom(tileA.wcs, tileA.tileHeight, nearCenterSkyPoints)

        val result = TileConsistency.overlapDisagreement(
            listOf(1L to tileA, 2L to tileB), mapOf(1L to refsA),
        )

        // Symmetrisch: BEIDE Kacheln bekommen denselben Widerspruchswert zugerechnet (s. Kommentar an
        // overlapDisagreement) -- unabhängig davon, dass nur Kachel A eigene .corr-Treffer eingebracht hat.
        assertEquals(10.0, result.getValue(1L).rmsPx!!, 0.5)
        assertEquals(10.0, result.getValue(2L).rmsPx!!, 0.5)
    }

    @Test
    fun overlapDisagreementIgnoresTilesThatDoNotSpatiallyOverlap() {
        val tileA = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        // Weit entfernt platziert (weit jenseits der 10%-Randtoleranz) -> keine Überlappung.
        val tileB = TileWcs(basicSolution(), tileOffsetX = 5000, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val refsA = corrRefsFrom(tileA.wcs, tileA.tileHeight, nearCenterSkyPoints)

        val result = TileConsistency.overlapDisagreement(
            listOf(1L to tileA, 2L to tileB), mapOf(1L to refsA),
        )

        assertTrue("keine Überlappung -> leeres Ergebnis, aber war: $result", result.isEmpty())
    }

    @Test
    fun overlapDisagreementReturnsNullRmsBelowMinMatchesButKeepsCount() {
        val tileA = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tileB = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        // Nur 1 Treffer -- unter MIN_OVERLAP_MATCHES (3).
        val refsA = corrRefsFrom(tileA.wcs, tileA.tileHeight, nearCenterSkyPoints.take(1))

        val result = TileConsistency.overlapDisagreement(
            listOf(1L to tileA, 2L to tileB), mapOf(1L to refsA),
        )

        assertEquals(1, result[1L]?.matchCount)
        assertNull(result.getValue(1L).rmsPx)
    }

    @Test
    fun overlapDisagreementLetsCorrFreeTileRespondButNotProbe() {
        val tileA = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tileB = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val refsA = corrRefsFrom(tileA.wcs, tileA.tileHeight, nearCenterSkyPoints)
        // corrRefsById hat GAR KEINEN Eintrag für Kachel B (z.B. ASTAP/Nova/De-Warp ohne eigene .corr) --
        // B darf trotzdem als ANTWORTENDE Kachel im Ergebnis auftauchen (nur nicht als Sondierende).
        val result = TileConsistency.overlapDisagreement(
            listOf(1L to tileA, 2L to tileB), mapOf(1L to refsA),
        )

        assertTrue("Kachel B muss trotz fehlender eigener .corr-Daten bewertet werden", result.containsKey(2L))
    }

    @Test
    fun tileReliabilityWeightsAllOneWithoutAnySignal() {
        val weights = TileConsistency.tileReliabilityWeights(listOf(1L, 2L, 3L), emptyMap(), emptyMap())
        assertEquals(listOf(1.0, 1.0, 1.0), weights)
    }

    @Test
    fun tileReliabilityWeightsDownweightsRelativeOutlier() {
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        // Vier unauffällige Kacheln (0.5-1.3px) + ein klarer Ausreißer (25px).
        val own = mapOf(
            1L to TileConsistency.TileOwnAccuracy(0.5, 300),
            2L to TileConsistency.TileOwnAccuracy(0.8, 250),
            3L to TileConsistency.TileOwnAccuracy(1.3, 200),
            4L to TileConsistency.TileOwnAccuracy(0.9, 280),
            5L to TileConsistency.TileOwnAccuracy(25.0, 8),
        )
        val weights = TileConsistency.tileReliabilityWeights(ids, own, emptyMap())

        // Bewusst relativ statt exakt geprüft: die Huber-Gewichte der unauffälligen Kacheln hängen
        // selbst leicht von deren eigener natürlicher Streuung ab (Einzeldurchlauf, s. Klassenkommentar
        // an tileReliabilityWeights) -- entscheidend ist der KLARE Abstand zum echten Ausreißer.
        val outlierWeight = weights[4]
        val othersMax = weights.subList(0, 4).max()
        assertTrue(
            "Ausreißer ($outlierWeight) muss klar unter den übrigen Kacheln (max $othersMax) liegen",
            outlierWeight < othersMax * 0.5,
        )
        assertTrue("Ausreißer bleibt klar heruntergewichtet: $outlierWeight", outlierWeight < 0.3)
    }

    @Test
    fun tileReliabilityWeightsNeverGoesBelowFloor() {
        val ids = listOf(1L, 2L, 3L, 4L)
        val own = mapOf(
            1L to TileConsistency.TileOwnAccuracy(0.5, 300),
            2L to TileConsistency.TileOwnAccuracy(0.6, 280),
            3L to TileConsistency.TileOwnAccuracy(0.7, 250),
            4L to TileConsistency.TileOwnAccuracy(500.0, 6), // extremer Ausreißer
        )
        val weights = TileConsistency.tileReliabilityWeights(ids, own, emptyMap())

        assertEquals(0.1, weights[3], 1e-9)
    }

    @Test
    fun tileReliabilityWeightsTreatsMissingSignalAsFullTrust() {
        val ids = listOf(1L, 2L, 3L, 4L)
        // Kachel 4 hat WEDER own- noch overlap-Eintrag -- bleibt 1.0, unabhängig davon, wie sehr die
        // ÜBRIGEN Kacheln streuen (fehlende Daten sind kein Beleg für eine schlechte Kachel).
        val own = mapOf(
            1L to TileConsistency.TileOwnAccuracy(0.5, 300),
            2L to TileConsistency.TileOwnAccuracy(15.0, 8),
            3L to TileConsistency.TileOwnAccuracy(0.6, 280),
        )
        val weights = TileConsistency.tileReliabilityWeights(ids, own, emptyMap())

        assertEquals(1.0, weights[3], 1e-9)
    }

    @Test
    fun tileReliabilityWeightsCombinesOwnAndOverlapByWorstCase() {
        val ids = listOf(1L, 2L, 3L, 4L)
        // Kachel 4: exzellente EIGENE Genauigkeit, aber starker Überlappungs-Widerspruch -- muss trotz
        // des guten own-Werts heruntergewichtet werden (Idee 3 darf nicht von Idee 2 maskiert werden).
        val own = mapOf(
            1L to TileConsistency.TileOwnAccuracy(0.5, 300),
            2L to TileConsistency.TileOwnAccuracy(0.6, 280),
            3L to TileConsistency.TileOwnAccuracy(0.7, 250),
            4L to TileConsistency.TileOwnAccuracy(0.3, 320),
        )
        val overlap = mapOf(4L to TileConsistency.OverlapAccuracy(30.0, 20))
        val weights = TileConsistency.tileReliabilityWeights(ids, own, overlap)

        assertTrue("trotz gutem own-Wert heruntergewichtet: ${weights[3]}", weights[3] < 0.5)
    }
}
