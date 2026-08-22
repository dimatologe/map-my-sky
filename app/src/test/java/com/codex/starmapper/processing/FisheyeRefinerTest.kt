package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class FisheyeRefinerTest {

    private fun rotZ(a: Double) = Mat3(cos(a), -sin(a), 0.0, sin(a), cos(a), 0.0, 0.0, 0.0, 1.0)
    private fun rotX(a: Double) = Mat3(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))

    private fun camDir(thetaDeg: Double, phiDeg: Double): Vec3 {
        val t = thetaDeg * PI / 180.0
        val p = phiDeg * PI / 180.0
        return Vec3(sin(t) * cos(p), sin(t) * sin(p), cos(t))
    }

    @Test
    fun recoversDistortionAndOrientationFromSyntheticStars() {
        // Wahre Kamera: Bildmitte 2000/3000, f=1200, deutliche Verzeichnung k1=0.08.
        val cxT = 2000.0; val cyT = 3000.0; val fT = 1200.0; val k1T = 0.08; val k2T = -0.005
        val rotTrue = rotZ(0.35) * rotX(0.17) // eq -> cam
        val trueProj = FisheyeProjection(cxT, cyT, fT, k1T, k2T)

        // Katalogsterne über das Feld verteilt; detektierte Pixel = wahre Projektion.
        val catalog = mutableListOf<Vec3>()
        val detected = mutableListOf<Offset>()
        for (thetaDeg in listOf(5.0, 15.0, 25.0, 35.0, 45.0, 55.0, 65.0)) {
            for (phiStep in 0 until 8) {
                val cam = camDir(thetaDeg, phiStep * 45.0)
                val eq = rotTrue.transpose() * cam // Rᵀ·cam, sodass rotTrue·eq = cam
                val pix = trueProj.directionToPixel(rotTrue * eq) ?: continue
                catalog += eq
                detected += pix
            }
        }
        assertTrue("genug synthetische Sterne", catalog.size >= 50)

        // Startlösung wie Phase 1: Mitte korrekt, f 5% daneben, keine Verzeichnung,
        // Orientierung leicht verdreht.
        val initial = PanoramaWcsSolution(
            projection = FisheyeProjection(cxT, cyT, fT * 1.05, 0.0, 0.0),
            rotEquToPano = rotZ(0.36) * rotX(0.18),
        )

        val result = FisheyeRefiner.refine(
            initial = initial,
            catalog = catalog,
            detected = detected,
            imageWidth = 4000,
            imageHeight = 6000,
        )

        assertTrue("Verfeinerung hat verbessert", result.improved)
        assertTrue("genug Matches: ${result.matchedStars}", result.matchedStars >= 50)
        assertTrue("kleiner Restfehler: ${result.residualArcmin}'", result.residualArcmin < 10f)

        // End-to-End: ein frischer Stern muss nahe der wahren Position landen.
        val testCam = camDir(40.0, 100.0)
        val testEq = rotTrue.transpose() * testCam
        val truePix = trueProj.directionToPixel(rotTrue * testEq)!!
        val refinedPix = result.solution.projection.directionToPixel(result.solution.rotEquToPano * testEq)!!
        val dist = hypot((refinedPix.x - truePix.x).toDouble(), (refinedPix.y - truePix.y).toDouble())
        assertTrue("Testsstern nahe Wahrheit: $dist px", dist < 12.0)
    }

    @Test
    fun calibratePanoramaRecoversStereographic() {
        // Wahre stereografische Kamera mit Punkten bis 110° (jenseits eines equidistanten Fisheye).
        val cxT = 3000.0; val cyT = 2000.0; val fT = 1400.0
        val rotTrue = rotZ(0.4) * rotX(0.25) // eq -> pano
        val trueProj = StereographicProjection(cxT, cyT, fT)
        val refs = mutableListOf<Pair<Offset, Vec3>>()
        for (thetaDeg in listOf(10.0, 30.0, 50.0, 70.0, 90.0, 110.0)) {
            for (phiStep in 0 until 8) {
                val cam = camDir(thetaDeg, phiStep * 45.0)
                val eq = rotTrue.transpose() * cam
                val pix = trueProj.directionToPixel(rotTrue * eq) ?: continue
                refs += pix to eq
            }
        }
        assertTrue("genug Referenzen: ${refs.size}", refs.size >= 30)

        val cal = FisheyeRefiner.calibratePanorama(
            refs, imageWidth = 6000, imageHeight = 4000,
            allowed = setOf(PanoProjectionKind.Stereographic),
        )
        assertNotNull("Kalibrierung vorhanden", cal)
        assertEquals(PanoProjectionKind.Stereographic, cal!!.kind)
        assertTrue("kleiner RMS: ${cal.rms}", cal.rms < 1.0)

        // Auto (alle Modelle) muss Stereographic per kleinstem RMS bevorzugen.
        val auto = FisheyeRefiner.calibratePanorama(
            refs, imageWidth = 6000, imageHeight = 4000,
            allowed = setOf(
                PanoProjectionKind.Fisheye, PanoProjectionKind.Stereographic,
                PanoProjectionKind.Equirectangular, PanoProjectionKind.Cylindrical,
                PanoProjectionKind.Mercator,
            ),
        )
        assertNotNull(auto)
        assertEquals(PanoProjectionKind.Stereographic, auto!!.kind)
    }

    @Test
    fun calibratePanoramaPrefersFisheyeForModerateFovLensDistortion() {
        // Echtes Fisheye-Objektiv, moderates FOV (theta bis 60°) -- KEIN 360-Grad-Panorama, keine
        // Kachel-Extremwerte wie beim theta=110°-Fall oben. Verzeichnung (k1=0,08, k2=-0,005)
        // identisch zu recoversDistortionAndOrientationFromSyntheticStars -- dort bereits als
        // "deutliche, aber realistische" Verzeichnung erprobt. Regressionsschutz für den neuen
        // BIC-Strafterm (s. FisheyeRefiner.complexityPenalizedScore): eine Konstante, stark genug
        // für das Wide-Panorama-Überanpassungsproblem, darf den mit Abstand häufigsten Anwendungsfall
        // (ein echtes Fisheye-Foto auf wenigen Kacheln) NICHT kaputt machen -- bisher gab es dafür
        // gar keinen Test.
        val cxT = 2000.0; val cyT = 1500.0; val fT = 1000.0; val k1T = 0.08; val k2T = -0.005
        val rotTrue = rotZ(0.2) * rotX(0.15)
        val trueProj = FisheyeProjection(cxT, cyT, fT, k1T, k2T)
        val refs = mutableListOf<Pair<Offset, Vec3>>()
        for (thetaDeg in listOf(15.0, 30.0, 45.0, 60.0)) {
            for (phiStep in 0 until 8) {
                val cam = camDir(thetaDeg, phiStep * 45.0)
                val eq = rotTrue.transpose() * cam
                val pix = trueProj.directionToPixel(rotTrue * eq) ?: continue
                refs += pix to eq
            }
        }
        assertTrue("genug Referenzen: ${refs.size}", refs.size >= 30)

        val fisheyeOnly = FisheyeRefiner.calibratePanorama(
            refs, imageWidth = 4000, imageHeight = 3000, allowed = setOf(PanoProjectionKind.Fisheye),
        )
        assertNotNull(fisheyeOnly)
        assertTrue("kleiner RMS bei passendem Modell: ${fisheyeOnly!!.rms}", fisheyeOnly.rms < 1.0)

        val auto = FisheyeRefiner.calibratePanorama(
            refs, imageWidth = 4000, imageHeight = 3000,
            allowed = setOf(
                PanoProjectionKind.Fisheye, PanoProjectionKind.Stereographic,
                PanoProjectionKind.Rectilinear, PanoProjectionKind.Equirectangular,
                PanoProjectionKind.Cylindrical, PanoProjectionKind.Mercator,
            ),
        )
        assertNotNull(auto)
        assertEquals(
            "echtes Fisheye-Objektiv (moderates FOV, wenige Anker) darf durch den BIC-Strafterm " +
                "nicht an ein Modell mit weniger Parametern verlieren",
            PanoProjectionKind.Fisheye,
            auto!!.kind,
        )
    }

    @Test
    fun returnsUnchangedWhenTooFewStars() {
        val initial = PanoramaWcsSolution(FisheyeProjection(100.0, 100.0, 200.0), Mat3.IDENTITY)
        val result = FisheyeRefiner.refine(initial, emptyList(), emptyList(), 200, 200)
        assertTrue(!result.improved)
    }

    @Test
    fun tileVoteWeightsNormalizesPerTileNotPerPoint() {
        // Zwei Kacheln mit 9 und 25 Punkten (normal 3x3 vs. De-Warp 5x5): beide sollen in Summe
        // gleich viel Gewicht beitragen (1.0 je Kachel), nicht proportional zur Punktzahl.
        val weights = FisheyeRefiner.tileVoteWeights(listOf(9, 25))
        assertEquals(34, weights.size)
        assertEquals(1.0, weights.take(9).sum(), 1e-9)
        assertEquals(1.0, weights.drop(9).sum(), 1e-9)
        weights.take(9).forEach { assertEquals(1.0 / 9, it, 1e-12) }
        weights.drop(9).forEach { assertEquals(1.0 / 25, it, 1e-12) }

        // Untergrenze: eine Kachel mit nur 3 Punkten (z.B. stark rand-beschnittene De-Warp-Kachel)
        // bekommt NICHT volles Gewicht 1/3 pro Punkt, sondern anteilig weniger (Floor bei 9).
        val floored = FisheyeRefiner.tileVoteWeights(listOf(3), floor = 9)
        assertEquals(3, floored.size)
        floored.forEach { assertEquals(1.0 / 9, it, 1e-12) }
    }

    @Test
    fun tileVoteWeightsMultipliesReliabilityIntoPerPointWeight() {
        // Zwei gleich große Kacheln (9 Punkte), aber Kachel 2 nur halb so vertrauenswürdig (s.
        // TileConsistency.tileReliabilityWeights) -> ihr Anteil am gemeinsamen Fit muss sich exakt
        // halbieren, unabhängig von der (hier gleichen) Rasterdichte.
        val weights = FisheyeRefiner.tileVoteWeights(listOf(9, 9), reliability = listOf(1.0, 0.5))
        assertEquals(18, weights.size)
        weights.take(9).forEach { assertEquals(1.0 / 9, it, 1e-12) }
        weights.drop(9).forEach { assertEquals(0.5 / 9, it, 1e-12) }
    }

    @Test
    fun calibratePanoramaWeightsNullEqualsUniformWeights() {
        // weights=null (heutiges Verhalten aller bestehenden Aufrufer) muss wertgleich zu
        // uniformen Gewichten 1.0 sein -> reine Zusatzfunktion, keine Verhaltensänderung.
        val cxT = 3000.0; val cyT = 2000.0; val fT = 1400.0
        val rotTrue = rotZ(0.4) * rotX(0.25)
        val trueProj = StereographicProjection(cxT, cyT, fT)
        val refs = mutableListOf<Pair<Offset, Vec3>>()
        for (thetaDeg in listOf(10.0, 30.0, 50.0, 70.0)) {
            for (phiStep in 0 until 8) {
                val cam = camDir(thetaDeg, phiStep * 45.0)
                val eq = rotTrue.transpose() * cam
                val pix = trueProj.directionToPixel(rotTrue * eq) ?: continue
                refs += pix to eq
            }
        }
        val allowed = setOf(PanoProjectionKind.Stereographic)
        val withoutWeights = FisheyeRefiner.calibratePanorama(refs, 6000, 4000, allowed)
        val withUniformWeights = FisheyeRefiner.calibratePanorama(
            refs, 6000, 4000, allowed, weights = List(refs.size) { 1.0 },
        )
        assertNotNull(withoutWeights)
        assertNotNull(withUniformWeights)
        assertEquals(withoutWeights!!.rms, withUniformWeights!!.rms, 1e-6)
    }

    @Test
    fun tileVoteWeightsBalanceUnequalTileSizes() {
        // Zwei "Kacheln" aus demselben Kamera-Modell, aber mit LEICHT unterschiedlicher wahrer
        // Rotation (simuliert zwei unabhängig gelöste Kacheln, die nicht perfekt übereinstimmen):
        // Kachel A mit vielen Punkten (rotA), Kachel B mit wenigen Punkten (rotB, ~2.9° Versatz).
        // Ohne Gewichtung dominiert A den gemeinsamen Fit rein durch Punktzahl; mit
        // tileVoteWeights soll sich der Fit spürbar mehr in Richtung B bewegen.
        val cx = 2000.0; val cy = 1500.0; val f = 1000.0
        val proj = FisheyeProjection(cx, cy, f, 0.0, 0.0)
        val rotA = rotZ(0.0)
        val rotB = rotZ(0.05)

        fun tilePoints(rot: Mat3, count: Int): List<Pair<Offset, Vec3>> =
            (0 until count).mapNotNull { i ->
                val cam = camDir(30.0, i * (360.0 / count))
                val eq = rot.transpose() * cam
                val pix = proj.directionToPixel(cam) ?: return@mapNotNull null
                pix to eq
            }

        val tileA = tilePoints(rotA, 40)
        val tileB = tilePoints(rotB, 9)
        val allRefs = tileA + tileB
        val weights = FisheyeRefiner.tileVoteWeights(listOf(tileA.size, tileB.size))
        val allowed = setOf(PanoProjectionKind.Fisheye)

        val unweighted = FisheyeRefiner.calibratePanorama(allRefs, 4000, 3000, allowed)!!
        val weighted = FisheyeRefiner.calibratePanorama(allRefs, 4000, 3000, allowed, weights)!!

        fun medianErrorForTile(fit: PanoramaWcsSolution, tile: List<Pair<Offset, Vec3>>): Double {
            val errors = tile.map { (pix, eq) ->
                val p = fit.projection.directionToPixel(fit.rotEquToPano * eq)!!
                hypot((p.x - pix.x).toDouble(), (p.y - pix.y).toDouble())
            }.sorted()
            return errors[errors.size / 2]
        }

        val bErrUnweighted = medianErrorForTile(unweighted.solution, tileB)
        val bErrWeighted = medianErrorForTile(weighted.solution, tileB)
        assertTrue(
            "gewichteter Fit soll Kachel B (wenige Punkte) näherkommen: " +
                "unweighted=$bErrUnweighted weighted=$bErrWeighted",
            bErrWeighted < bErrUnweighted,
        )
    }

    @Test
    fun crossMatchSolutionWorksForNonFisheyeProjection() {
        // Beweis der Generalisierung: seed ist Equirectangular (zylindrische Familie, KEINE
        // Pol-Klammer wie bei Cylindrical/Mercator, daher risikoärmster Vertreter für einen
        // synthetischen Test) statt Fisheye -- crossMatch (das alte, private Original) war hart
        // an FisheyeProjection gebunden; crossMatchSolution muss für JEDES Modell funktionieren.
        val cx = 2000.0; val cy = 1500.0
        val proj = CylindricalProjection(cx, cy, fx = 800.0, fy = 800.0, kind = PanoProjectionKind.Equirectangular)
        val rot = rotZ(0.2) * rotX(0.1)
        val seed = PanoramaWcsSolution(proj, rot)

        val catalog = mutableListOf<Vec3>()
        val detected = mutableListOf<Offset>()
        for (thetaDeg in listOf(30.0, 50.0, 70.0, 90.0, 110.0)) {
            for (phiStep in 0 until 6) {
                val cam = camDir(thetaDeg, phiStep * 60.0)
                val eq = rot.transpose() * cam
                val pix = proj.directionToPixel(rot * eq) ?: continue
                catalog += eq
                detected += pix
            }
        }
        assertTrue("genug synthetische Sterne: ${catalog.size}", catalog.size >= 15)

        // Distraktoren weit weg von jeder echten Projektion (Bildecken) -- dürfen keine Treffer erzeugen.
        val decoys = listOf(Offset(20f, 20f), Offset(3950f, 2950f), Offset(20f, 2950f))
        val matches = FisheyeRefiner.crossMatchSolution(seed, catalog, detected + decoys, tol = 50.0)

        assertEquals("alle echten Treffer gefunden, keine Distraktoren", detected.size, matches.size)
        matches.forEach { (_, pix) -> assertTrue("kein Distraktor unter den Treffern", pix in detected) }
    }

    @Test
    fun reprojectionRmsMatchesInjectedResidual() {
        val proj = FisheyeProjection(1000.0, 800.0, 900.0)
        val rot = Mat3.IDENTITY
        val fit = PanoramaWcsSolution(proj, rot)

        val dirs = listOf(camDir(10.0, 0.0), camDir(10.0, 90.0), camDir(10.0, 180.0), camDir(10.0, 270.0))
        val refs = dirs.map { dir ->
            val truePix = proj.directionToPixel(rot * dir)!!
            // Bekannter, injizierter Versatz von exakt 3px in x -> RMS muss exakt 3.0 sein.
            Offset(truePix.x + 3f, truePix.y) to dir
        }

        val rms = FisheyeRefiner.reprojectionRms(fit, refs)
        assertEquals(3.0, rms, 1e-6)
    }

    @Test
    fun globalCrossMatchImprovesFarFieldAccuracyOverClusteredTileAnchorsAlone() {
        // Realer Bug-Fall nachgebaut (s. predictHint-Fix + solveAllTiles-Nachschärfung): wenige,
        // eng geklumpte "Kachel"-Anker (schmaler Theta-Bereich, wie mehrere Kacheln im selben
        // Bildbereich) können die Verzeichnung (k1,k2) nicht bestimmen -- bei kleinem Theta ist
        // ihr Effekt (θ+k1·θ³+k2·θ⁵) gegenüber θ selbst vernachlässigbar, die LM-Optimierung hat
        // dafür kaum Gradientenkraft. Erst Ganzbild-Cross-Match-Punkte über einen weiten
        // Theta-Bereich zwingen k1,k2 auf ihre wahren Werte -- das erklärt den in echten Tests
        // gemessenen 30-40px-RMS für Sternbilder außerhalb der Kacheln.
        val cxT = 2000.0; val cyT = 1500.0; val fT = 1000.0; val k1T = 0.15; val k2T = -0.03
        val rotTrue = rotZ(0.1) * rotX(0.05)
        val trueProj = FisheyeProjection(cxT, cyT, fT, k1T, k2T)

        fun pointsAt(thetaDegs: List<Double>): List<Pair<Offset, Vec3>> =
            thetaDegs.flatMap { thetaDeg ->
                (0 until 8).mapNotNull { phiStep ->
                    val cam = camDir(thetaDeg, phiStep * 45.0)
                    val eq = rotTrue.transpose() * cam
                    val pix = trueProj.directionToPixel(rotTrue * eq) ?: return@mapNotNull null
                    pix to eq
                }
            }

        // "Kachel"-Anker: nur ein schmaler Theta-Bereich (5-10°), wie mehrere Kacheln im selben
        // Bildbereich (genau wie im echten Bug: 4 Vorkacheln, alle im unteren Bildbereich).
        val tileAnchors = pointsAt(listOf(5.0, 7.0, 10.0))
        // "Ganzbild"-Punkte: über einen weiten Theta-Bereich verteilt (Ganzbild-Sternerkennung).
        val wholeImagePoints = pointsAt(listOf(5.0, 15.0, 30.0, 45.0, 60.0))
        val allowed = setOf(PanoProjectionKind.Fisheye)

        val tileOnlyFit = FisheyeRefiner.calibratePanorama(tileAnchors, 4000, 3000, allowed)!!
        val combinedFit = FisheyeRefiner.calibratePanorama(tileAnchors + wholeImagePoints, 4000, 3000, allowed)!!

        // Testpunkt weit außerhalb des Kachel-Bereichs (theta=55°, von keiner "Kachel" abgedeckt).
        val testCam = camDir(55.0, 100.0)
        val testEq = rotTrue.transpose() * testCam
        val truePix = trueProj.directionToPixel(rotTrue * testEq)!!

        fun errorAt(fit: PanoramaWcsSolution): Double {
            val p = fit.projection.directionToPixel(fit.rotEquToPano * testEq) ?: return Double.MAX_VALUE
            return hypot((p.x - truePix.x).toDouble(), (p.y - truePix.y).toDouble())
        }

        val tileOnlyError = errorAt(tileOnlyFit.solution)
        val combinedError = errorAt(combinedFit.solution)
        assertTrue(
            "Ganzbild-Punkte müssen die Fern-Genauigkeit deutlich verbessern: " +
                "nurKacheln=$tileOnlyError kombiniert=$combinedError",
            combinedError < tileOnlyError,
        )
    }

    @Test
    fun isNearAnyAnchorAcceptsWithinMarginOfNearestAnchor() {
        // Zwei Anker auf entgegengesetzten Seiten (theta=30, phi=0/180 -> 60 Grad auseinander).
        val anchors = listOf(camDir(30.0, 0.0), camDir(30.0, 180.0))
        val marginRad = Math.toRadians(10.0)
        // Nur 2 Grad vom ERSTEN Anker entfernt (vom zweiten ~58 Grad) -> muss reichen.
        val near = camDir(28.0, 0.0)
        assertTrue(isNearAnyAnchor(near, anchors, marginRad))
    }

    @Test
    fun isNearAnyAnchorRejectsBeyondMarginOfEveryAnchor() {
        val anchors = listOf(camDir(30.0, 0.0), camDir(30.0, 180.0))
        val marginRad = Math.toRadians(10.0)
        // 90 Grad von der +Z-Achse, seitlich versetzt -> 90 Grad von BEIDEN Ankern entfernt.
        val far = camDir(90.0, 90.0)
        assertTrue(!isNearAnyAnchor(far, anchors, marginRad))
    }

    @Test
    fun isNearAnyAnchorFailsOpenWithoutAnchors() {
        // Kein Anker (z.B. noch kein Solve) -> Prüfung greift nicht, alles bleibt sichtbar.
        assertTrue(isNearAnyAnchor(camDir(45.0, 10.0), emptyList(), Math.toRadians(20.0)))
    }

    @Test
    fun isNearAnyAnchorHandlesWideIrregularCoverageBetterThanASingleCentroidRadius() {
        // Realer Gerätebug (0.22.11/153): ein 2D-Anker-"Fleck" (weiter RA- UND Dec-Bereich, wie bei
        // einem großen Fisheye-Mosaik) hat einen Schwerpunkt-Radius, der bis in die am weitesten
        // entfernte ECKE der Abdeckung reicht -- ein Punkt, der von JEDEM einzelnen Anker weit weg
        // ist, aber zufällig nah genug am (viel zu großzügigen) Schwerpunkt-Radius liegt, rutschte
        // frueher durch. isNearAnyAnchor prueft gegen JEDEN Anker einzeln und faellt darauf nicht rein.
        val anchors = listOf(-40.0, -20.0, 0.0, 20.0, 40.0).flatMap { lon ->
            listOf(40.0, -20.0).map { lat -> camDir(90.0 - lat, lon) }
        }
        val marginRad = Math.toRadians(20.0)
        // Deutlich jenseits der südlichsten Anker-Reihe (lat=-20) -> darf nicht akzeptiert werden,
        // obwohl es (siehe Kommentar oben) innerhalb von Schwerpunkt+Streuung+20° gelegen hätte.
        val farBeyondCoverage = camDir(90.0 - (-50.0), 0.0)
        assertTrue(!isNearAnyAnchor(farBeyondCoverage, anchors, marginRad))
    }

    @Test
    fun equidistantSeedFromPreservesForwardDirectionForAzimuthalBaseFit() {
        // Regressionsschutz: für ein bereits azimutales baseFit (Fisheye) war die Blickrichtungs-
        // Bestimmung schon vorher richtig ("+Z im Pano-Frame" = Blickrichtung stimmt für diese
        // Familie) -- equidistantSeedFrom darf sie durch die neue, verallgemeinerte Herleitung
        // nicht verändern.
        val rotTrue = rotZ(0.3) * rotX(0.2) // eq -> pano
        val baseFit = PanoramaWcsSolution(FisheyeProjection(2000.0, 1500.0, 1000.0, k1 = 0.05), rotTrue)
        val trueForwardEq = rotTrue.transpose() * Vec3(0.0, 0.0, 1.0)

        val seed = FisheyeRefiner.equidistantSeedFrom(baseFit, imageWidth = 4000, imageHeight = 3000)
        val seedLocal = seed.projection.pixelToDirection(2000.0, 1500.0)!!
        val seedForwardEq = seed.rotEquToPano.transpose() * seedLocal

        val angleDeg = Math.toDegrees(acos(trueForwardEq.dot(seedForwardEq).coerceIn(-1.0, 1.0)))
        assertTrue("Blickrichtung erhalten: $angleDeg Grad Abweichung", angleDeg < 0.01)
    }

    @Test
    fun equidistantSeedFromRecoversCorrectDirectionForCylindricalBaseFit() {
        // Der eigentliche Referenzstern-Rotationsbug (Polaris/Alioth/Alkaid landeten auf falschen
        // Positionen bzw. im Vordergrund, s. Plan): für ein zylindrisches Gewinnermodell (hier
        // Mercator) liegt die Bildmitte auf der LOKALEN +X-Achse, NICHT +Z -- die alte Logik nahm
        // "+Z im Pano-Frame = Blickrichtung" universell an. Ohne den Fix würde
        // directionToPixel(Vec3(0,0,1)) hier sofort an der Pol-Klammer scheitern (phi=90° >
        // MAX_PHI=85°) und auf die geometrische Bildmitte MIT der unveränderten (falschen) Rotation
        // zurückfallen. Dieser Test beweist, dass equidistantSeedFrom jetzt die tatsächliche RA/Dec
        // der Bildmitte trifft, unabhängig von der Projektionsfamilie des baseFit.
        val proj = CylindricalProjection(2000.0, 1000.0, fx = 500.0, fy = 500.0, kind = PanoProjectionKind.Mercator)
        val rotTrue = rotZ(0.6) * rotX(0.4) // eq -> pano, klar abseits jeder Pol-/Achsen-Ausrichtung
        val baseFit = PanoramaWcsSolution(proj, rotTrue)
        val trueForwardEq = (rotTrue.transpose() * proj.pixelToDirection(2000.0, 1000.0)!!).normalized()

        val seed = FisheyeRefiner.equidistantSeedFrom(baseFit, imageWidth = 4000, imageHeight = 2000)
        val seedLocal = seed.projection.pixelToDirection(2000.0, 1000.0)!!
        val seedForwardEq = (seed.rotEquToPano.transpose() * seedLocal).normalized()

        val angleDeg = Math.toDegrees(acos(trueForwardEq.dot(seedForwardEq).coerceIn(-1.0, 1.0)))
        assertTrue(
            "Bildmitte nach equidistantSeedFrom muss auf die tatsächliche RA/Dec zeigen: " +
                "$angleDeg Grad Abweichung",
            angleDeg < 0.01,
        )
    }

    @Test
    fun refineRejectsNonMonotonicFisheyeFitAndKeepsOriginal() {
        // Spinnennetz-Kollaps (s. Plan): FisheyeProjection.radiusForTheta ist für k1=-0.23 (derselbe
        // Wert wie in WcsSolutionTest/AstapOverlayMapperTest) ab theta~69° nicht mehr monoton
        // (Nulldurchgang von 1+3*k1*theta² bei theta=acos-frei sqrt(1/(3*0.23))≈69°). Ohne Teil C
        // würde refine() dieses Modell als "verbessert" übernehmen, sobald es die synthetischen
        // Referenzen gut erklärt -- der Monotonie-Check (0..179°, weit über die Referenzpunkte
        // hinaus) muss das jetzt verhindern und stattdessen den unveränderten Ausgangszustand
        // zurückgeben. Feste Parität (aus initial übernommen) -- anders als calibrateFromReferences
        // keine zweite, ggf. verwirrende Paritäts-Alternative.
        val cxT = 2000.0; val cyT = 1500.0; val fT = 1000.0; val k1T = -0.23
        val rotTrue = rotZ(0.1) * rotX(0.05)
        val trueProj = FisheyeProjection(cxT, cyT, fT, k1T)

        val catalog = mutableListOf<Vec3>()
        val detected = mutableListOf<Offset>()
        for (thetaDeg in listOf(5.0, 15.0, 25.0, 35.0, 45.0, 55.0, 65.0)) {
            for (phiStep in 0 until 8) {
                val cam = camDir(thetaDeg, phiStep * 45.0)
                val eq = rotTrue.transpose() * cam
                val pix = trueProj.directionToPixel(rotTrue * eq) ?: continue
                catalog += eq
                detected += pix
            }
        }
        assertTrue("genug synthetische Sterne", catalog.size >= 30)

        val initial = PanoramaWcsSolution(
            projection = FisheyeProjection(cxT, cyT, fT * 1.05, 0.0, 0.0),
            rotEquToPano = rotZ(0.11) * rotX(0.06),
        )

        val result = FisheyeRefiner.refine(
            initial = initial,
            catalog = catalog,
            detected = detected,
            imageWidth = 4000,
            imageHeight = 3000,
        )

        assertTrue("nicht-monotoner Fit darf NICHT als Verbesserung übernommen werden", !result.improved)
        assertEquals("Ausgangslösung bleibt unverändert", initial, result.solution)
    }

    @Test
    fun calibrateFromReferencesRejectsNonMonotonicFisheyeFit() {
        // Dieselbe Nicht-Monotonie wie oben, aber über den anderen Teil-C-Aufrufpfad
        // (calibrateFromReferences/fitFixed, genutzt von der manuellen Referenzstern-Kalibrierung):
        // muss null liefern statt eines kollabierenden Modells.
        val truth = FisheyeProjection(cx = 2000.0, cy = 1500.0, f = 1000.0, k1 = -0.23)
        val rotTrue = rotZ(0.1) * rotX(0.05)
        val refs = mutableListOf<Pair<Offset, Vec3>>()
        // Bewusst asymmetrische Phi-Werte (kein 45°-Ring): eine 8-fach symmetrische Punktwolke wäre
        // unter Phi -> -Phi (= die andere Parität) auf sich selbst abbildbar und könnte der falschen
        // Parität eine zufällig ebenso gute (Spiegel-)Alternativlösung erlauben.
        val phiDegs = listOf(10.0, 63.0, 130.0, 210.0, 290.0)
        for (thetaDeg in listOf(5.0, 15.0, 25.0, 35.0, 45.0, 55.0, 65.0)) {
            for (phiDeg in phiDegs) {
                val cam = camDir(thetaDeg, phiDeg)
                val eq = rotTrue.transpose() * cam
                val pix = truth.directionToPixel(rotTrue * eq) ?: continue
                refs += pix to eq
            }
        }
        assertTrue("genug synthetische Referenzen: ${refs.size}", refs.size >= 30)

        val solution = FisheyeRefiner.calibrateFromReferences(refs, imageWidth = 4000, imageHeight = 3000)
        assertNull("nicht-monotoner Fit muss verworfen werden statt ein kollabierendes Modell zu liefern", solution)
    }

    // Platzhalter-WCS für TileWcs.wcs -- globalizeTileCorrRefs fasst dieses Feld nie an, nur
    // tileOffsetX/Y, daher genügt irgendein gültiger WcsSolutionLike-Wert (Muster aus TileDeWarp.kt).
    private fun placeholderWcs(): WcsSolutionLike =
        PanoramaWcsSolution(FisheyeProjection(0.0, 0.0, 1000.0, 0.0, 0.0), Mat3.IDENTITY)

    @Test
    fun globalizeTileCorrRefsAppliesTileOffset() {
        val tileA = 1L to TileWcs(placeholderWcs(), tileOffsetX = 100, tileOffsetY = 200, tileWidth = 400, tileHeight = 300)
        val tileB = 2L to TileWcs(placeholderWcs(), tileOffsetX = 500, tileOffsetY = 50, tileWidth = 400, tileHeight = 300)
        val dirA1 = Vec3(1.0, 0.0, 0.0)
        val dirA2 = Vec3(0.0, 1.0, 0.0)
        val dirB1 = Vec3(0.0, 0.0, 1.0)
        val corrRefsById = mapOf(
            1L to listOf(Offset(10f, 20f) to dirA1, Offset(30f, 40f) to dirA2),
            2L to listOf(Offset(5f, 6f) to dirB1),
            // Kachel-ID, die NICHT in idToTileWcs vorkommt (z.B. durch Outlier-Filter ausgeschlossen
            // oder inzwischen gelöscht) -- muss stillschweigend ignoriert werden.
            99L to listOf(Offset(1f, 1f) to Vec3(1.0, 1.0, 1.0)),
        )

        val result = FisheyeRefiner.globalizeTileCorrRefs(listOf(tileA, tileB), corrRefsById)

        assertEquals(3, result.size)
        assertEquals(Offset(110f, 220f), result[0].first)
        assertEquals(dirA1, result[0].second)
        assertEquals(Offset(130f, 240f), result[1].first)
        assertEquals(dirA2, result[1].second)
        assertEquals(Offset(505f, 56f), result[2].first)
        assertEquals(dirB1, result[2].second)
    }

    @Test
    fun globalizeTileCorrRefsReturnsEmptyWithoutCorrData() {
        // Regressionsfall: ASTAP-/Nova-/De-Warp-gelöste Kacheln haben keine tileCorrRefsById-Einträge.
        val tileA = 1L to TileWcs(placeholderWcs(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 100, tileHeight = 100)
        assertTrue(FisheyeRefiner.globalizeTileCorrRefs(listOf(tileA), emptyMap()).isEmpty())
    }

    @Test
    fun globalizeTileCorrRefsCapsAndEvenlySamplesLargeTiles() {
        // Gerätebeleg 2026-07-27: 30-31 Kacheln / ~5500 Punkte -> 66-71s Refit-Zeit trotz bereits
        // behobener estimateFocal-Redundanz. Default-Obergrenze 80/Kachel (angehoben von 40, s.
        // Gerätebeleg 2026-07-29 zu verworfenen echten Treffern), gleichmäßig verteilt statt der
        // ersten 80 (vermeidet Bias durch die Erkennungsreihenfolge von astrometry.net).
        val tile = 1L to TileWcs(placeholderWcs(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 100, tileHeight = 100)
        val refs = (0 until 200).map { i -> Offset(i.toFloat(), 0f) to Vec3(i.toDouble(), 0.0, 0.0) }
        val corrRefsById = mapOf(1L to refs)

        val result = FisheyeRefiner.globalizeTileCorrRefs(listOf(tile), corrRefsById)

        assertEquals(80, result.size)
        // Erster Eintrag am Original-Anfang, letzter nahe dem Original-Ende -- Beleg für gleichmäßige
        // Verteilung statt simples Abschneiden (das würde x=0..79 statt bis nahe 199 liefern).
        assertEquals(0f, result.first().first.x, 1e-6f)
        assertTrue("letzter Eintrag muss nahe dem Original-Ende liegen, nicht bei Index 79", result.last().first.x > 180f)
    }

    @Test
    fun corrRefGroupSizesAndWeightsAlignsByTileIdNotPosition() {
        // idToTileWcs (nur Kacheln mit erfolgreich abgeleiteter eigener WCS) ist hier bewusst eine
        // andere Teilmenge/Reihenfolge als tileIds/reliability (die JEDE nicht-Ausreißer-Kachel
        // enthalten) -- genau der Fallstrick, den eine rein positionale Zuordnung übersehen würde:
        // Kachel 2 fehlt in idToTileWcs komplett, Kachel 3 kommt vor Kachel 1.
        val tile3 = 3L to TileWcs(placeholderWcs(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 100, tileHeight = 100)
        val tile1 = 1L to TileWcs(placeholderWcs(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 100, tileHeight = 100)
        val corrRefsById = mapOf(
            1L to listOf(Offset(0f, 0f) to Vec3(1.0, 0.0, 0.0)),
            2L to listOf(Offset(0f, 0f) to Vec3(0.0, 1.0, 0.0), Offset(1f, 1f) to Vec3(0.0, 1.0, 0.0)),
            3L to (0 until 5).map { i -> Offset(i.toFloat(), 0f) to Vec3(0.0, 0.0, 1.0) },
        )
        val tileIds = listOf(1L, 2L, 3L)
        val reliability = listOf(0.4, 0.9, 0.7)

        val (sizes, weights) = FisheyeRefiner.corrRefGroupSizesAndWeights(
            listOf(tile3, tile1), corrRefsById, tileIds, reliability,
        )

        // Reihenfolge folgt idToTileWcs (tile3, dann tile1), NICHT tileIds -- Kachel 2 (nur in
        // tileIds, nicht in idToTileWcs) taucht gar nicht erst auf.
        assertEquals(listOf(5, 1), sizes)
        assertEquals(listOf(0.7, 0.4), weights)
    }

    @Test
    fun corrRefGroupSizesAndWeightsDefaultsMissingReliabilityToFullTrust() {
        // Kachel-ID in idToTileWcs, aber nicht in tileIds (z.B. weil sie in TileConsistency.
        // tileReliabilityWeights aus einem anderen Grund fehlt) -> volles Vertrauen (1.0), dieselbe
        // Konvention wie FisheyeRefiner.tileVoteWeights.
        val tile = 7L to TileWcs(placeholderWcs(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 100, tileHeight = 100)
        val corrRefsById = mapOf(7L to listOf(Offset(0f, 0f) to Vec3(1.0, 0.0, 0.0)))

        val (sizes, weights) = FisheyeRefiner.corrRefGroupSizesAndWeights(
            listOf(tile), corrRefsById, tileIds = listOf(1L, 2L), reliability = listOf(0.3, 0.6),
        )

        assertEquals(listOf(1), sizes)
        assertEquals(listOf(1.0), weights)
    }

    @Test
    fun corrRefGroupSizesAndWeightsSumMatchesGlobalizeTileCorrRefsSize() {
        // Kern-Invariante, die fitMesh als Vorbedingung braucht (references.size == groupSizes.sum()):
        // für dieselben Eingaben (inkl. gleichem maxPerTile) muss die Summe der Gruppengrößen exakt
        // der Länge der von globalizeTileCorrRefs gebauten flachen Punktliste entsprechen.
        val tileA = 1L to TileWcs(placeholderWcs(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 100, tileHeight = 100)
        val tileB = 2L to TileWcs(placeholderWcs(), tileOffsetX = 500, tileOffsetY = 0, tileWidth = 100, tileHeight = 100)
        val corrRefsById = mapOf(
            1L to (0 until 150).map { i -> Offset(i.toFloat(), 0f) to Vec3(1.0, 0.0, 0.0) }, // > maxPerTile
            2L to listOf(Offset(0f, 0f) to Vec3(0.0, 1.0, 0.0)),
        )
        val idToTileWcs = listOf(tileA, tileB)

        val flat = FisheyeRefiner.globalizeTileCorrRefs(idToTileWcs, corrRefsById, maxPerTile = 80)
        val (sizes, _) = FisheyeRefiner.corrRefGroupSizesAndWeights(
            idToTileWcs, corrRefsById, tileIds = listOf(1L, 2L), reliability = listOf(1.0, 1.0), maxPerTile = 80,
        )

        assertEquals(flat.size, sizes.sum())
        assertEquals(listOf(80, 1), sizes)
    }

    // --- fitMesh ---------------------------------------------------------------

    // 7 räumlich getrennte "Kachel"-Zentren (Theta 15-45°, Phi über den vollen Kreis verteilt) --
    // ANDERS als ein Ring aus vielen Phi-Phasen bei GLEICHEM Theta: ein Ring würde beim Declustering
    // in CorrectedProjection (Kachel -> EIN Mittelwert-Repräsentativpunkt) auf einen physikalisch
    // bedeutungslosen Punkt nahe der Bildmitte kollabieren (Gegenrichtungen heben sich auf). Echte
    // Kacheln sind räumlich KOMPAKT (s. tileAnchorPairs: 3x3-Raster INNERHALB einer kleinen
    // Kachel-Bounding-Box), das bildet dieser Aufbau nach.
    private val meshTestTileCenters = listOf(
        15.0 to 0.0, 20.0 to 51.4, 25.0 to 102.8, 30.0 to 154.3,
        35.0 to 205.7, 40.0 to 257.1, 45.0 to 308.6,
    )

    // Enger 3x3-Cluster (±0.1°) um jedes Zentrum -- eng genug, dass die Korrektur INNERHALB einer
    // Kachel praktisch konstant ist (Voraussetzung für die "In-Sample fast exakt"-Prüfung unten),
    // aber mit echter (wenn auch kleiner) räumlicher Ausdehnung wie ein reales Kachel-Raster.
    private fun meshTestTileGroups(rotTrue: Mat3, truth: PanoramaProjection): List<List<Pair<Offset, Vec3>>> =
        meshTestTileCenters.map { (centerTheta, centerPhi) ->
            listOf(-0.1, 0.0, 0.1).flatMap { dTheta ->
                listOf(-0.1, 0.0, 0.1).mapNotNull { dPhi ->
                    val cam = camDir(centerTheta + dTheta, centerPhi + dPhi)
                    val eq = rotTrue.transpose() * cam
                    val pix = truth.directionToPixel(rotTrue * eq) ?: return@mapNotNull null
                    pix to eq
                }
            }
        }

    @Test
    fun fitMeshImprovesOnBaselineBetweenTileGroups() {
        val rotTrue = rotZ(0.3) * rotX(0.2)
        val trueProj = StereographicProjection(cx = 2000.0, cy = 1500.0, f = 1000.0)
        val groups = meshTestTileGroups(rotTrue, trueProj)
        val refs = groups.flatten()
        val groupSizes = groups.map { it.size }
        assertTrue("genug Referenzen: ${refs.size}", refs.size >= 40)

        // Baseline mit leicht falscher Brennweite (5%, Rotation exakt) -- realistisch: der starre
        // Gewinner trifft die Ausrichtung meist gut, aber nicht jeden Formel-Parameter exakt. Das
        // gibt der Korrektur etwas Echtes zu tun (wachsender radialer Fehler), ohne Rotation/Kind
        // mit hineinzumischen.
        val baseline = PanoramaWcsSolution(StereographicProjection(cx = 2000.0, cy = 1500.0, f = 950.0), rotTrue)

        val calibration = FisheyeRefiner.fitMesh(refs, groupSizes, baseline)
        assertNotNull("Mesh-Kalibrierung vorhanden", calibration)
        assertEquals(PanoProjectionKind.Mesh, calibration!!.kind)
        assertTrue(
            "kreuzvalidierter Restfehler bleibt in einem plausiblen Rahmen -- CorrectedProjection " +
                "fällt außerhalb ihrer Abdeckung graceful auf die Baseline zurück, nie auf einen " +
                "Strafwert (s. Kommentar an fitMesh): ${calibration.rms}",
            calibration.rms < 100.0,
        )

        // Frischer Punkt ZWISCHEN zwei Kachel-Zentren (20°/51,4° und 25°/102,8°), nicht exakt an
        // einem Trainingspunkt: die Korrektur muss die unkorrigierte Baseline dort deutlich
        // verbessern.
        val testCam = camDir(22.5, 77.0)
        val testEq = rotTrue.transpose() * testCam
        val truePix = trueProj.directionToPixel(rotTrue * testEq)!!
        val baselinePix = baseline.projection.directionToPixel(rotTrue * testEq)!!
        val baselineError = hypot((baselinePix.x - truePix.x).toDouble(), (baselinePix.y - truePix.y).toDouble())
        val meshPix = calibration.solution.projection.directionToPixel(calibration.solution.rotEquToPano * testEq)
        assertNotNull(meshPix)
        val correctedError = hypot((meshPix!!.x - truePix.x).toDouble(), (meshPix.y - truePix.y).toDouble())
        assertTrue(
            "Korrektur muss die unkorrigierte Baseline deutlich verbessern: baseline=$baselineError " +
                "korrigiert=$correctedError",
            correctedError < baselineError * 0.5,
        )
    }

    @Test
    fun fitMeshReturnsNullWithOnlyOneTileGroup() {
        // Nur EINE Kachel-Gruppe -> jede Leave-One-Tile-Out-Runde hätte null Trainingspunkte übrig;
        // ohne Kreuzvalidierungs-Grundlage liefert fitMesh bewusst null statt eines unbelegten RMS.
        val proj = StereographicProjection(cx = 1000.0, cy = 1000.0, f = 900.0)
        val refs = (0 until 8).map { step ->
            val dir = camDir(20.0, step * 45.0)
            proj.directionToPixel(dir)!! to dir
        }
        val baseline = PanoramaWcsSolution(proj, Mat3.IDENTITY)
        assertNull(FisheyeRefiner.fitMesh(refs, listOf(refs.size), baseline))
    }

    @Test
    fun fitMeshCrossValidatedRmsIsHonestNotTheNearZeroInSampleFit() {
        // Kernpunkt der Kreuzvalidierung (s. Kommentar an FisheyeRefiner.fitMesh): eine Korrektur
        // trifft ihre EIGENEN Trainingspunkte (nahezu) exakt, weil jede Kachel in meshTestTileGroups
        // bewusst eng geclustert ist (±0,1°) -- die Korrektur ist innerhalb einer so kleinen Kachel
        // praktisch konstant, jeder Rohpunkt bekommt also im Declustering fast exakt seine eigene
        // Korrektur zurück (s. CorrectedProjection), unabhängig davon, wie ungenau die Baseline
        // selbst ist. Ein In-Sample-Restfehler wäre für die Modellwahl gegen die 6 übrigen
        // (In-Sample gemessenen) Kinds also bedeutungslos. Dieser Test beweist, dass calibration.rms
        // tatsächlich die Leave-One-Tile-Out-Zahl ist -- spürbar über dem In-Sample-Restfehler
        // derselben vollen Korrektur.
        val rotTrue = rotZ(0.3) * rotX(0.2)
        val trueProj = StereographicProjection(cx = 2000.0, cy = 1500.0, f = 1000.0)
        val groups = meshTestTileGroups(rotTrue, trueProj)
        val refs = groups.flatten()
        val groupSizes = groups.map { it.size }
        val baseline = PanoramaWcsSolution(StereographicProjection(cx = 2000.0, cy = 1500.0, f = 950.0), rotTrue)

        val calibration = FisheyeRefiner.fitMesh(refs, groupSizes, baseline)
        assertNotNull(calibration)

        val inSampleRms = FisheyeRefiner.reprojectionRms(calibration!!.solution, refs)
        assertTrue("In-Sample-Restfehler der vollen Korrektur ist praktisch exakt: $inSampleRms", inSampleRms < 0.5)
        assertTrue(
            "kreuzvalidierter RMS muss deutlich über dem In-Sample-Wert liegen (ehrliche Schätzung, " +
                "kein Selbstbetrug): cv=${calibration.rms} inSample=$inSampleRms",
            calibration.rms > inSampleRms * 5.0,
        )
    }

    @Test
    fun fitMeshGroupWeightsNullEqualsUniformWeights() {
        // groupWeights=null (heutiges Verhalten aller bestehenden Aufrufer) muss wertgleich zu
        // uniformen Gewichten 1.0 sein -- reine Zusatzfunktion, keine Verhaltensänderung (gleiche
        // Prüfidee wie calibratePanoramaWeightsNullEqualsUniformWeights oben).
        val rotTrue = rotZ(0.3) * rotX(0.2)
        val trueProj = StereographicProjection(cx = 2000.0, cy = 1500.0, f = 1000.0)
        val groups = meshTestTileGroups(rotTrue, trueProj)
        val refs = groups.flatten()
        val groupSizes = groups.map { it.size }
        val baseline = PanoramaWcsSolution(StereographicProjection(cx = 2000.0, cy = 1500.0, f = 950.0), rotTrue)

        val withoutWeights = FisheyeRefiner.fitMesh(refs, groupSizes, baseline)
        val withUniformWeights = FisheyeRefiner.fitMesh(
            refs, groupSizes, baseline, groupWeights = List(groupSizes.size) { 1.0 },
        )

        assertNotNull(withoutWeights)
        assertNotNull(withUniformWeights)
        assertEquals(withoutWeights!!.rms, withUniformWeights!!.rms, 1e-6)
    }

    @Test
    fun fitMeshDownweightedGroupHasLessInfluenceNearby() {
        val rotTrue = rotZ(0.3) * rotX(0.2)
        val trueProj = StereographicProjection(cx = 2000.0, cy = 1500.0, f = 1000.0)
        val groups = meshTestTileGroups(rotTrue, trueProj).toMutableList()
        // Eine Kachel (Zentrum 25°/102,8°) systematisch um 40px verfälscht -- simuliert eine schwach
        // bestimmte/unzuverlässige Kachel-Lösung (Diagnose-Dump-Befund: wenige Sternpaarungen -> stark
        // erhöhte eigene RMS), nicht zufälliges Rauschen. Alle Punkte der Gruppe gleich verschoben,
        // damit die Kachel INTERN weiter konsistent bleibt (reine Verzerrung, kein Streuen).
        val corruptedIndex = 2
        groups[corruptedIndex] = groups[corruptedIndex].map { (pix, dir) -> Offset(pix.x + 40f, pix.y) to dir }
        val refs = groups.flatten()
        val groupSizes = groups.map { it.size }
        val baseline = PanoramaWcsSolution(StereographicProjection(cx = 2000.0, cy = 1500.0, f = 950.0), rotTrue)

        val uniform = FisheyeRefiner.fitMesh(refs, groupSizes, baseline)
        val downweighted = FisheyeRefiner.fitMesh(
            refs, groupSizes, baseline,
            groupWeights = groupSizes.indices.map { if (it == corruptedIndex) 0.05 else 1.0 },
        )
        assertNotNull(uniform)
        assertNotNull(downweighted)

        // Testpunkt NAHE (nicht exakt auf) dem Zentrum der verfälschten Kachel -- dort dominiert unter
        // Gleichgewichtung deren (verfälschte) Korrektur die Vorhersage am stärksten.
        val (centerTheta, centerPhi) = meshTestTileCenters[corruptedIndex]
        val testCam = camDir(centerTheta + 0.05, centerPhi + 0.05)
        val testEq = rotTrue.transpose() * testCam
        val truePix = trueProj.directionToPixel(rotTrue * testEq)!!

        fun errorFor(calibration: FisheyeRefiner.PanoCalibration): Double {
            val pix = calibration.solution.projection.directionToPixel(calibration.solution.rotEquToPano * testEq)!!
            return hypot((pix.x - truePix.x).toDouble(), (pix.y - truePix.y).toDouble())
        }
        val uniformError = errorFor(uniform!!)
        val downweightedError = errorFor(downweighted!!)

        // Bewusst nur die RICHTUNG geprüft (nicht ein konkretes Verhältnis) -- am Gerät/durch echte
        // Zahlen später nachschärfbar, aber diese Grundaussage (Herunterwichten einer verfälschten
        // Kachel bringt eine nahe Vorhersage näher an die Wahrheit statt weiter weg) muss immer gelten.
        assertTrue(
            "heruntergewichtete Korrektur muss näher an der Wahrheit liegen als die uniforme: " +
                "uniform=$uniformError downweighted=$downweightedError",
            downweightedError < uniformError,
        )
    }
}
