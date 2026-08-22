package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.ConstellationPattern
import com.codex.starmapper.domain.DeepSkyObject
import com.codex.starmapper.domain.Hemisphere
import com.codex.starmapper.domain.OverlayLineStyle
import com.codex.starmapper.domain.SkyPoint
import com.codex.starmapper.domain.StarNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import kotlin.math.hypot

class AstapOverlayMapperTest {
    @Test
    fun visibleCatalogEdgesBecomeEditableAnchorOverrides() {
        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", -1f, 0f, 23.999333f, 0f),
                StarNode("B", 1f, 0f, 0.000667f, 0f),
            ),
            edges = listOf(0 to 1),
        )
        val solution = WcsSolution(
            crPix1 = 501.0,
            crPix2 = 400.0,
            crVal1Degrees = 0.0,
            crVal2Degrees = 0.0,
            cd11 = 0.01,
            cd12 = 0.0,
            cd21 = 0.0,
            cd22 = 0.01,
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = solution,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertEquals(1, overlays.size)
        assertEquals(2, overlays.single().anchorOverrides.size)
        assertTrue(overlays.single().showName)
    }

    private fun nearOriginSolution() = WcsSolution(
        crPix1 = 501.0,
        crPix2 = 400.0,
        crVal1Degrees = 0.0,
        crVal2Degrees = 0.0,
        cd11 = 0.01,
        cd12 = 0.0,
        cd21 = 0.0,
        cd22 = 0.01,
    )

    @Test
    fun evaluateConstellationAccuracyMeasuresRealPixelErrorPerConstellation() {
        // A und B bewusst weit auseinander (nicht wie im seam-crossing-Test oben nur 0.02 Grad
        // getrennt) -- sonst wuerde der einzige "matches"-Eintrag fuer A faelschlich auch bei B
        // innerhalb der Toleranz landen und den erwarteten matchedStars=1 verfaelschen.
        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", -1f, 0f, 1f, 10f),
                StarNode("B", 1f, 0f, 3f, -10f),
            ),
            edges = listOf(0 to 1),
        )
        val solution = nearOriginSolution()
        val imageHeight = 800
        val starA = pattern.stars[0]
        val predictedA = solution.skyToImage(SkyPoint(starA.raHours * 15f, starA.decDegrees), imageHeight)!!
        val dirA = raDecToVector(starA.raHours.toDouble() * 15.0, starA.decDegrees.toDouble())
        val matches = listOf(dirA to Offset(predictedA.x + 4f, predictedA.y))

        val accuracy = AstapOverlayMapper.evaluateConstellationAccuracy(listOf(pattern), matches, solution, imageHeight)

        assertEquals(1, accuracy.size)
        val (matchedPattern, acc) = accuracy.single()
        assertEquals(pattern.id, matchedPattern.id)
        assertEquals(1, acc.matchedStars)
        assertEquals(4.0, acc.rmsErrorPx, 1e-6)
    }

    @Test
    fun evaluateConstellationAccuracyIgnoresConstellationsWithoutRealMatch() {
        val nearPattern = ConstellationPattern(
            id = "Near",
            name = "Near",
            germanName = "Near",
            hemisphere = Hemisphere.Both,
            stars = listOf(StarNode("A", -1f, 0f, 1f, 10f)),
            edges = emptyList(),
        )
        val farPattern = ConstellationPattern(
            id = "Far",
            name = "Far",
            germanName = "Far",
            hemisphere = Hemisphere.Both,
            stars = listOf(StarNode("C", 0f, 0f, 12f, 45f)),
            edges = emptyList(),
        )
        val solution = nearOriginSolution()
        val imageHeight = 800
        val starA = nearPattern.stars[0]
        val predictedA = solution.skyToImage(SkyPoint(starA.raHours * 15f, starA.decDegrees), imageHeight)!!
        val dirA = raDecToVector(starA.raHours.toDouble() * 15.0, starA.decDegrees.toDouble())
        val matches = listOf(dirA to predictedA)

        val accuracy = AstapOverlayMapper.evaluateConstellationAccuracy(
            listOf(nearPattern, farPattern),
            matches,
            solution,
            imageHeight,
        )

        assertEquals(1, accuracy.size)
        assertEquals("Near", accuracy.single().first.id)
    }

    @Test
    fun evaluateConstellationAccuracyRespectsAngularTolerance() {
        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(StarNode("A", -1f, 0f, 1f, 10f)),
            edges = emptyList(),
        )
        val solution = nearOriginSolution()
        val imageHeight = 800
        val starA = pattern.stars[0]
        // Richtung 1 Grad neben dem Muster-Stern -- weit ausserhalb der Standard-Toleranz (0.05 Grad).
        val farDir = raDecToVector(starA.raHours.toDouble() * 15.0 + 1.0, starA.decDegrees.toDouble())
        val matches = listOf(farDir to Offset(100f, 100f))

        val accuracy = AstapOverlayMapper.evaluateConstellationAccuracy(listOf(pattern), matches, solution, imageHeight)

        assertTrue(accuracy.isEmpty())
    }

    @Ignore(
        "Kachel-Mauer (isClaimedByAnyTile/isNearAnyAnchor) für Sterne auf Nutzerwunsch zurückgebaut " +
            "(2026-08-17, analog zu createConstellationOverlaysCullsStarBeyondAnchoredFov): ein knapp " +
            "außerhalb der Anker-/Kachel-Marge liegender Stern (hier M31-analog) wurde komplett " +
            "verworfen, obwohl Bildrand-Prüfung + Maske ihn sonst plausibel gezeichnet hätten. Der " +
            "hier getestete Fall (kollabierende Zylinder-Projektion ohne Längengrad-Grenze) bleibt ein " +
            "reales Restrisiko, ist aber durch die Bildrand-Prüfung direkt danach nicht abgedeckt --" +
            "am Gerät beobachten, ob ein gezielter Ersatzschutz nötig wird.",
    )
    @Test
    fun createStarOverlaysCullsStarBeyondAnchoredFov() {
        // Reproduziert den ursprünglich gefundenen Bug: eine zylindrische Projektion (hier
        // Equirectangular) hat KEINE Längengrad-Grenze -- jede Himmelsrichtung bekommt einen
        // gültigen, ggf. sogar im Bild liegenden Pixel, egal wie weit sie von der tatsächlich
        // fotografierten/verankerten Region entfernt ist.
        val solution = PanoramaWcsSolution(
            CylindricalProjection(cx = 500.0, cy = 400.0, fx = 100.0, fy = 100.0, kind = PanoProjectionKind.Equirectangular),
            Mat3.IDENTITY,
        )
        val imageWidth = 1000
        val imageHeight = 800

        // Vorab-Check: der weit entfernte Stern (160 Grad RA-Abstand) bekommt fälschlich einen
        // gültigen, im Bild liegenden Pixel -- keine Nullprüfung würde ihn also je ausschließen.
        val farPixel = solution.skyToImage(SkyPoint(170f, 0f), imageHeight)
        assertNotNull(farPixel)
        assertTrue(farPixel!!.x in 0f..imageWidth.toFloat())

        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("Near", 0f, 0f, 10f / 15f, 0f),
                StarNode("Far", 0f, 0f, 170f / 15f, 0f),
            ),
            edges = emptyList(),
        )

        val overlays = AstapOverlayMapper.createStarOverlays(
            catalogStars = emptyList(),
            constellationPatterns = listOf(pattern),
            solution = solution,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            includeNamed = false,
            includeConstellation = true,
            allToMagnitude = null,
        )

        assertEquals(1, overlays.size)
        assertEquals("Near", overlays.single().text)
    }

    @Ignore(
        "Kachel-Mauer (isClaimedByAnyTile/isNearAnyAnchor) für DSOs auf Nutzerwunsch zurückgebaut " +
            "(2026-08-17, s. createStarOverlaysCullsStarBeyondAnchoredFov): ein reales Objekt (M31) " +
            "blieb trotz jeder Helligkeits-/Schwellwert-Einstellung unsichtbar, weil es knapp " +
            "außerhalb der Anker-/Kachel-Marge lag -- Bildrand-Prüfung + Maske bleiben die " +
            "Schutzschicht, decken aber genau den hier konstruierten Kollaps-Fall nicht ab.",
    )
    @Test
    fun createDeepSkyOverlaysCullsObjectBeyondAnchoredFov() {
        val solution = PanoramaWcsSolution(
            CylindricalProjection(cx = 500.0, cy = 400.0, fx = 100.0, fy = 100.0, kind = PanoProjectionKind.Equirectangular),
            Mat3.IDENTITY,
        )
        val imageWidth = 1000
        val imageHeight = 800
        val nearObject = DeepSkyObject("N1", "Near", "oc", SkyPoint(10f, 0f), magnitude = 5f, dimensions = "10")
        val farObject = DeepSkyObject("F1", "Far", "oc", SkyPoint(170f, 0f), magnitude = 5f, dimensions = "10")

        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(nearObject, farObject),
            solution = solution,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            categories = setOf(DeepSkyCategory.Cluster),
        )

        assertEquals(1, overlays.size)
    }

    @Test
    fun createDeepSkyOverlaysPlacesNestedSmallObjectLabelNearbyInsteadOfOutsideContainingCircle() {
        // Nutzerbefund (2026-08-20, Screenshot Schwan/Füchschen): ein kleines Objekt INNERHALB eines
        // sehr großen wich zwingend über dessen Rand hinaus aus (lange Führungslinie quer durchs
        // Bild), weil der umschließende Kreis wie jedes fremde Objekt als Hindernis behandelt wurde --
        // auch für die eigene, winzige Beschriftung direkt daneben, obwohl dort reichlich freier
        // Innenraum war.
        val solution = nearOriginSolution()
        val imageWidth = 1000
        val imageHeight = 800
        // Groß: 300' (5°) Durchmesser -> ca. 250px Radius bei ca. 100px/Grad (cd11=cd22=0.01).
        val bigObject = DeepSkyObject(
            "Big", "Big", "oc", SkyPoint(0f, 0f),
            magnitude = 5f, dimensions = "300", majorAxisArcmin = 300f,
        )
        // Klein, ca. 100px vom Zentrum des großen entfernt -- deutlich innerhalb von dessen ~250px
        // Radius, aber winzig genug (6' -> ~5px Radius), um dort reichlich Platz zu haben.
        val smallObject = DeepSkyObject(
            "Small", "Small", "oc", SkyPoint(1f, 0f),
            magnitude = 8f, dimensions = "6", majorAxisArcmin = 6f,
        )

        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(bigObject, smallObject),
            solution = solution,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            categories = setOf(DeepSkyCategory.Cluster),
            showNames = true,
            fullLabelPlacement = true,
        )

        assertEquals(2, overlays.size)
        val smallOverlay = overlays.single { it.text == "Small" }
        assertTrue(smallOverlay.showName)
        // Vorher (Bug): die Suche scheiterte an jeder Distanz innerhalb des großen Kreises (als
        // Hindernis behandelt) und wich bis knapp hinter dessen ~250px Radius aus. Jetzt: kleine
        // Distanz nahe am Objekt, da der umschließende Kreis für die eigene Beschriftung kein
        // Hindernis mehr ist.
        assertTrue(
            "labelLeaderPx sollte klein sein (nahe am Objekt), war aber ${smallOverlay.labelLeaderPx}",
            smallOverlay.labelLeaderPx < 60f,
        )
    }

    @Ignore(
        "Kachel-Mauer (isClaimedByAnyTile/isNearAnyAnchor) für Sternbilder auf Nutzerwunsch " +
            "zurückgebaut (2026-07-28): riss zuvor zusammenhängende Sternbilder an der 20°/Kachel-" +
            "Grenze ab (\"abgehakt\"). Diese kollabierende Fisheye-Kante (kurze, erfundene Linie " +
            "nahe der Bildmitte statt einer zu langen) wird von keiner verbleibenden Geometrie-" +
            "Prüfung (Divergenz-Cull, Fix 2) erkannt, da alle nur zu LANGE Kanten verwerfen. Nutzer-" +
            "Entscheidung: erst am Gerät beobachten, ob das reale Restrisiko ist, bevor ein gezielter " +
            "Ersatzschutz (z.B. Monotonie-Check analog isMonotonicFisheyeRadius) umgesetzt wird.",
    )
    @Test
    fun createConstellationOverlaysCullsStarBeyondAnchoredFov() {
        // Reproduziert den "Spinnennetz"-Bug: ein Fisheye-Verzeichnungspolynom (k1), das bei großem
        // Theta (120 Grad, weit außerhalb der echten Kachel-Abdeckung) auf einen kleinen, gültigen
        // (nicht null) Bildradius zurückfällt -- der betroffene Stern landet fälschlich nahe der
        // Bildmitte, statt (wie bei einem sauberen Modell) korrekt ausgeschlossen zu werden.
        val cx = 500.0
        val cy = 400.0
        val projection = FisheyeProjection(cx, cy, f = 1000.0, k1 = -0.23)
        val tileSolution = PanoramaWcsSolution(projection, Mat3.IDENTITY)

        val nearDir = raDecToVector(0.0, 85.0) // theta = 90-85 = 5 Grad
        val farDir = raDecToVector(0.0, -30.0) // theta = 90-(-30) = 120 Grad

        // Vorab-Check: das gewählte k1 reproduziert wirklich den Kollaps.
        val farPixel = projection.directionToPixel(farDir)
        assertNotNull("weit entfernter Stern muss (fälschlich) einen gültigen Pixel bekommen", farPixel)
        val distFromCenter = hypot((farPixel!!.x - cx).toDouble(), (farPixel.y - cy).toDouble())
        assertTrue(
            "Kollaps-Vorbedingung verletzt: Pixel liegt nicht nahe der Mitte ($distFromCenter px)",
            distFromCenter < 100.0,
        )

        // Solution als Mosaik verpackt, wie im echten Kachel-Betrieb -- genau das macht den alten,
        // rein Fisheye-spezifischen panoRot/frontHemi-Schutz wirkungslos (MosaicWcsSolution ist
        // keine PanoramaWcsSolution).
        val mosaic = MosaicWcsSolution(
            tiles = listOf(TileWcs(tileSolution, tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)),
            fallback = tileSolution,
        )

        val nearRaDec = vectorToRaDec(nearDir)
        val farRaDec = vectorToRaDec(farDir)
        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("Near", 0f, 0f, (nearRaDec.first / 15.0).toFloat(), nearRaDec.second.toFloat()),
                StarNode("Far", 0f, 0f, (farRaDec.first / 15.0).toFloat(), farRaDec.second.toFloat()),
            ),
            edges = listOf(0 to 1),
        )
        // Anker nur um den NAHEN Bereich (wie echte Kacheln, die genau diesen einen Stern lösen).
        val anchorDirs = listOf(4.0, 5.0, 6.0).map { raDecToVector(0.0, 90.0 - it) }

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        // Die einzige Kante verbindet Near<->Far -- Far liegt weit außerhalb der Anker-Streuung und
        // muss trotz seines (fälschlich gültigen) Pixels verworfen werden; die Konstellation bleibt leer.
        assertTrue("Kante zum weit entfernten, kollabierten Stern darf nicht gezeichnet werden", overlays.isEmpty())
    }

    @Ignore(
        "Kachel-Mauer für Sternbilder auf Nutzerwunsch zurückgebaut (2026-07-28, s. Kommentar bei " +
            "createConstellationOverlaysCullsStarBeyondAnchoredFov). Dieser Test prüfte genau die " +
            "jetzt bewusst entfernte Einschränkung: glatte Extrapolation weit über die Kachel-Anker " +
            "hinaus (kein Kollaps, nur unverankertes Gebiet) soll ab jetzt wieder gezeichnet werden.",
    )
    @Test
    fun createConstellationOverlaysRejectsStarFarFromEveryAnchorDespiteCentroidProximity() {
        // Realer Gerätebug (0.22.11/153): ein Fisheye-Mosaik mit Ankern über einen weiten RA/Dec-
        // Bereich (hier: 5 Längen x 2 Breiten, wie ein Foto vom Zenit bis knapp über den Horizont)
        // hat einen Schwerpunkt-Radius, der bis in die am weitesten entfernte ECKE der Abdeckung
        // reicht -- ein Stern, der von JEDEM einzelnen Anker weit entfernt ist (hier: 30 Grad südlich
        // der südlichsten Anker-Reihe), lag dadurch fälschlich trotzdem noch "im Schwerpunkt-Radius"
        // (61 von erlaubten 70 Grad, s. Verifikationsrechnung im Commit). Genau das ließ im echten
        // Gerätetest tief-südliche Sternbilder (Kiel des Schiffs, Segel, Netz u.a.) über dem
        // Vordergrund erscheinen. isNearAnyAnchor (Nächster-Anker statt Schwerpunkt) muss das
        // ausschließen, obwohl BEIDE Sterne einen gültigen, im Bild liegenden Pixel bekommen.
        val solution = PanoramaWcsSolution(
            CylindricalProjection(cx = 500.0, cy = 400.0, fx = 100.0, fy = 100.0, kind = PanoProjectionKind.Equirectangular),
            Mat3.IDENTITY,
        )
        val anchorDirs = listOf(-40.0, -20.0, 0.0, 20.0, 40.0).flatMap { ra ->
            listOf(40.0, -20.0).map { dec -> raDecToVector(ra, dec) }
        }
        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("Near", 0f, 0f, 0f, -15f),
                StarNode("Far", 0f, 0f, 0f, -50f),
            ),
            edges = listOf(0 to 1),
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = solution,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertTrue(
            "Stern 30 Grad jenseits der Anker-Abdeckung darf nicht gezeichnet werden, auch wenn er " +
                "nah genug am (zu großzügigen) Schwerpunkt-Radius läge",
            overlays.isEmpty(),
        )
    }

    private fun narrowTileSolution(crVal1Degrees: Double) = WcsSolution(
        crPix1 = 501.0,
        crPix2 = 400.0,
        crVal1Degrees = crVal1Degrees,
        crVal2Degrees = 0.0,
        cd11 = 0.01,
        cd12 = 0.0,
        cd21 = 0.0,
        cd22 = 0.01,
    )

    @Test
    fun createConstellationOverlaysDrawsStarClaimedByTileEvenWithDegenerateFallback() {
        // "Virtuelle Mauer": eine echte Kachel (enge Gnomonik/TAN-Lösung) beansprucht diese beiden
        // Sterne über IHR EIGENES Rechteck -> müssen gezeichnet werden, OBWOHL die anchorDirs
        // absichtlich weit weg zeigen (der Winkel-Rückfall allein würde sie ablehnen) und der
        // Fallback eine andere, unabhängige Lösung ist.
        val tile = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val fallback = PanoramaWcsSolution(FisheyeProjection(cx = 100.0, cy = 100.0, f = 50.0), Mat3.IDENTITY)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile), fallback = fallback)
        val anchorDirs = listOf(raDecToVector(170.0, 0.0))
        val fovMarginRad = Math.toRadians(20.0)

        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", 0f, 0f, 1f / 15f, 0f),
                StarNode("B", 0f, 0f, 2f / 15f, 0f),
            ),
            edges = listOf(0 to 1),
        )

        // Vorab-Check: OHNE die Kachel-Mauer würde der Winkel-Rückfall allein beide Sterne ablehnen.
        assertTrue(
            "Vorbedingung verletzt: anchorDirs müssen zu weit weg zeigen, damit isNearAnyAnchor allein ablehnt",
            !isNearAnyAnchor(raDecToVector(1.0, 0.0), anchorDirs, fovMarginRad) &&
                !isNearAnyAnchor(raDecToVector(2.0, 0.0), anchorDirs, fovMarginRad),
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertEquals(1, overlays.size)
    }

    @Test
    fun createConstellationOverlaysDrawsGapStarViaAnchorFallbackWhenNoTileClaimsIt() {
        // Zwei echte Kacheln (RA=0 und RA=20) mit einer Lücke dazwischen (RA~9-11). Kein Stern dort
        // wird von einer Kachel beansprucht -- seit dem Rückbau der Kachel-Mauer (2026-07-28) gibt es
        // dafür keinen separaten Winkel-Rückfall mehr: der Stern wird ohnehin gezeichnet, solange der
        // glatte Fallback (hier: eine echte, unauffällige WcsSolution) ihm einen plausiblen Pixel gibt.
        val tile1 = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tile2 = TileWcs(narrowTileSolution(20.0), tileOffsetX = 2000, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val fallback = narrowTileSolution(10.0) // deckt die Lücke glatt ab (echte WcsSolution reicht).
        val mosaic = MosaicWcsSolution(tiles = listOf(tile1, tile2), fallback = fallback)

        // Vorab-Check: die Lücken-Sterne dürfen von KEINER der beiden Kacheln beansprucht werden.
        assertTrue(
            "Vorbedingung verletzt: Lücken-Sterne dürfen von keiner Kachel beansprucht werden",
            !mosaic.isClaimedByAnyTile(SkyPoint(9f, 0f)) && !mosaic.isClaimedByAnyTile(SkyPoint(11f, 0f)),
        )

        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", 0f, 0f, 9f / 15f, 0f),
                StarNode("B", 0f, 0f, 11f / 15f, 0f),
            ),
            edges = listOf(0 to 1),
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 3500,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertEquals(1, overlays.size)
    }

    @Test
    fun createConstellationOverlaysExcludesStarFarFromRealTilesAndAnchors() {
        // Eine ECHTE (nicht entartete) Kachel, ein Stern 170+ Grad entfernt -> die zugrunde liegende
        // Gnomonik-Projektion divergiert/liefert bereits selbst null (s. WcsSolution.skyToImage,
        // denominator<=1e-10) -> weiterhin ausgeschlossen, unabhängig vom (seit 2026-07-28 für
        // Sternbilder entfernten) Anker-Abstandsgate.
        val tile = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))

        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", 0f, 0f, 170f / 15f, 0f),
                StarNode("B", 0f, 0f, 175f / 15f, 0f),
            ),
            edges = listOf(0 to 1),
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertTrue(overlays.isEmpty())
    }

    @Test
    fun createConstellationOverlaysDrawsCurvedEdgesWhenMosaicFallbackIsNonlinear() {
        // Bug B (2026-07-27): die alte Bedingung `solution is PanoramaWcsSolution` prüfte nur das
        // ÄUSSERE MosaicWcsSolution-Objekt und war deshalb in der Praxis IMMER false, selbst wenn der
        // Lücken-Fallback (hier: Mercator) selbst gekrümmt gerendert werden müsste. Sonst identisch zu
        // createConstellationOverlaysDrawsGapStarViaAnchorFallbackWhenNoTileClaimsIt, nur mit einem
        // echten nichtlinearen Fallback statt einer weiteren TAN-WcsSolution.
        val tile1 = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tile2 = TileWcs(narrowTileSolution(20.0), tileOffsetX = 2000, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val fallback = PanoramaWcsSolution(
            CylindricalProjection(cx = 1750.0, cy = 400.0, fx = 100.0, fy = 100.0, kind = PanoProjectionKind.Mercator),
            Mat3.IDENTITY,
        )
        val mosaic = MosaicWcsSolution(tiles = listOf(tile1, tile2), fallback = fallback)

        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", 0f, 0f, 9f / 15f, 0f),
                StarNode("B", 0f, 0f, 11f / 15f, 0f),
            ),
            edges = listOf(0 to 1),
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 3500,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertEquals(1, overlays.size)
        val polylines = overlays.single().edgePolylines
        assertNotNull(
            "Kante durch einen nichtlinearen Fallback muss als Großkreis-Polylinie gezeichnet werden",
            polylines,
        )
        assertTrue(
            "Polylinie muss echtes Großkreis-Sampling sein, keine gerade 2-Punkt-Linie",
            polylines!!.single().size > 2,
        )
    }

    @Test
    fun createConstellationOverlaysKeepsStraightEdgesForCropDirectMosaicWithoutFallback() {
        // Regressionsschutz für Fix 1 (Bug B): der reine Einzelkachel-Crop-Direct-Fall (TAN, kein
        // Fallback) muss weiterhin gerade Linien zeichnen -- edgePolylines bleibt null.
        val tile = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))

        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", 0f, 0f, 1f / 15f, 0f),
                StarNode("B", 0f, 0f, 2f / 15f, 0f),
            ),
            edges = listOf(0 to 1),
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertEquals(1, overlays.size)
        assertEquals(null, overlays.single().edgePolylines)
    }

    @Test
    fun createConstellationOverlaysRejectsIncoherentPatternAcrossFarApartTiles() {
        // Großer-Bär-Klasse Fehler (2026-07-27): jede Kante für sich besteht ihre EIGENE Prüfung
        // (kurz, lokal plausibel: 1 Grad Winkelabstand, 100px Abstand, beides weit innerhalb aller
        // bestehenden Toleranzen), aber die Vereinigung der Kanten ist insgesamt verstreut/inkohärent
        // -- zwei Kacheln, deren WAHRE Himmelsposition nur 11 Grad auseinanderliegt (100px/Grad lokale
        // Skala würde also ~1100px Gesamt-Abstand erwarten lassen), aber im Bild 8100px auseinander-
        // gezogen sind (z.B. durch eine fehlerhafte Mosaik-Verankerung). Ohne Fix 2 würde dieses Muster
        // trotzdem gezeichnet, da keine bestehende Prüfung die Gesamt-Ausdehnung über mehrere Kanten
        // hinweg betrachtet (nur einzelne Kanten).
        val tile1 = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tile2 = TileWcs(narrowTileSolution(10.0), tileOffsetX = 8000, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile1, tile2))

        val pattern = ConstellationPattern(
            id = "Tst",
            name = "Test",
            germanName = "Test",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A1", 0f, 0f, 0f, 0f),
                StarNode("A2", 0f, 0f, 1f / 15f, 0f),
                StarNode("B1", 0f, 0f, 10f / 15f, 0f),
                StarNode("B2", 0f, 0f, 11f / 15f, 0f),
            ),
            // Keine Brücken-Kante zwischen den beiden Kacheln -- genau das lässt jede Pro-Kante-Prüfung
            // isoliert bestehen.
            edges = listOf(0 to 1, 2 to 3),
        )

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 9100,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
        )

        assertTrue(
            "Insgesamt verstreutes Muster (jede Kante einzeln plausibel) muss durch die neue " +
                "Gesamt-Kohärenz-Prüfung verworfen werden",
            overlays.isEmpty(),
        )
    }

    @Test
    fun createConstellationOverlaysRecordsFullCompletenessForFullyVisiblePattern() {
        val tile = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))
        val pattern = ConstellationPattern(
            id = "Full",
            name = "Full",
            germanName = "Full",
            hemisphere = Hemisphere.North,
            stars = listOf(
                StarNode("A", 0f, 0f, 0f, 0f),
                StarNode("B", 0f, 0f, 1f / 15f, 0f),
            ),
            edges = listOf(0 to 1),
        )
        val completeness = mutableListOf<ConstellationCompleteness>()

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
            completenessOut = completeness,
        )

        assertEquals(1, overlays.size)
        assertEquals(1, completeness.size)
        val c = completeness.single()
        assertEquals("Full", c.id)
        assertEquals(Hemisphere.North, c.hemisphere)
        assertEquals(2, c.totalStars)
        assertEquals(1, c.totalEdges)
        assertEquals(2, c.survivingStars)
        assertEquals(1, c.survivingEdges)
        assertTrue(c.anyStarInFov)
    }

    @Test
    fun createConstellationOverlaysRecordsZeroCompletenessForPatternOutsideFov() {
        // Südhalbkugel-Sternbild bei einer Nordhimmel-Aufnahme: korrekt NICHT gezeichnet, aber die
        // Vollständigkeits-Erfassung muss das als "nicht im Bild" (anyStarInFov=false) klassifizieren,
        // nicht als "im Bild, aber leer" -- genau die Unterscheidung, die das vorherige
        // tallyConstellationVisibility nicht pro Sternbild liefern konnte. Bestätigt außerdem, dass die
        // Erfassung selbst nicht nordhalbkugel-spezifisch ist (hemisphere=South wird korrekt erfasst).
        val tile = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))
        val pattern = ConstellationPattern(
            id = "Cru",
            name = "Crux",
            germanName = "Kreuz des Südens",
            hemisphere = Hemisphere.South,
            stars = listOf(
                StarNode("A", 0f, 0f, 170f / 15f, 0f),
                StarNode("B", 0f, 0f, 175f / 15f, 0f),
            ),
            edges = listOf(0 to 1),
        )
        val completeness = mutableListOf<ConstellationCompleteness>()

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
            completenessOut = completeness,
        )

        assertTrue(overlays.isEmpty())
        assertEquals(1, completeness.size)
        val c = completeness.single()
        assertEquals("Cru", c.id)
        assertEquals(Hemisphere.South, c.hemisphere)
        assertEquals(0, c.survivingEdges)
        assertTrue("Muster außerhalb jedes Ankers muss als anyStarInFov=false erfasst werden", !c.anyStarInFov)
    }

    @Test
    fun createConstellationOverlaysRecordsPartialCompletenessWhenOneEdgeIsCulled() {
        val tile = TileWcs(narrowTileSolution(0.0), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))
        val pattern = ConstellationPattern(
            id = "Part",
            name = "Part",
            germanName = "Part",
            hemisphere = Hemisphere.Both,
            stars = listOf(
                StarNode("A", 0f, 0f, 0f, 0f),
                StarNode("B", 0f, 0f, 1f / 15f, 0f),
                StarNode("C", 0f, 0f, 170f / 15f, 0f),
            ),
            edges = listOf(0 to 1, 1 to 2),
        )
        val completeness = mutableListOf<ConstellationCompleteness>()

        val overlays = AstapOverlayMapper.createConstellationOverlays(
            catalog = listOf(pattern),
            solution = mosaic,
            imageWidth = 1000,
            imageHeight = 800,
            colorArgb = 0xFFFFFFFF,
            strokeWidth = 3f,
            anchorRadiusRatio = 0.045f,
            lineStyle = OverlayLineStyle.Solid,
            opacity = 1f,
            showNames = true,
            nameTextSize = 30f,
            completenessOut = completeness,
        )

        assertEquals(1, overlays.size)
        val c = completeness.single()
        assertEquals(3, c.totalStars)
        assertEquals(2, c.totalEdges)
        assertEquals(2, c.survivingStars)
        assertEquals(1, c.survivingEdges)
        assertTrue(c.anyStarInFov)
    }
}
