package com.codex.starmapper.processing

import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WcsSolutionTest {
    @Test
    fun centerSkyCoordinateMapsToReferencePixel() {
        val solution = basicSolution()

        val point = solution.skyToImage(SkyPoint(0f, 0f), imageHeight = 800)

        assertNotNull(point)
        assertEquals(500f, point!!.x, 0.001f)
        assertEquals(400f, point.y, 0.001f)
    }

    @Test
    fun rightAscensionAndDeclinationRespectRasterOrientation() {
        val solution = basicSolution()

        val east = solution.skyToImage(SkyPoint(0.01f, 0f), imageHeight = 800)
        val north = solution.skyToImage(SkyPoint(0f, 0.01f), imageHeight = 800)

        assertNotNull(east)
        assertNotNull(north)
        assertEquals(501f, east!!.x, 0.01f)
        assertEquals(400f, east.y, 0.01f)
        assertEquals(500f, north!!.x, 0.01f)
        assertEquals(399f, north.y, 0.01f)
    }

    @Test
    fun isClaimedByAnyTileTrueWhenTileOwnWcsPlacesPointInsideItsRectangle() {
        val tile = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))

        assertTrue(mosaic.isClaimedByAnyTile(SkyPoint(0f, 0f)))
    }

    @Test
    fun isClaimedByAnyTileFalseWhenPointFarOutsideEveryTilesOwnRectangle() {
        val tile = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))

        // 170 Grad entfernt -> Gnomonik-Nenner <= 0, skyToImage liefert null (kein Zurückfalten).
        assertFalse(mosaic.isClaimedByAnyTile(SkyPoint(170f, 0f)))
    }

    @Test
    fun isClaimedByAnyTileIgnoresPanoramaTypedTileEvenIfItsOwnProjectionFoldsBack() {
        // Reproduziert den "Spinnennetz"-Kollaps (wie AstapOverlayMapperTest): ein Fisheye-
        // Verzeichnungspolynom (k1), das bei großem Theta auf einen kleinen, gültigen (nicht null)
        // Bildradius zurückfällt -- die Kachel-eigene WCS ist hier SELBST ein PanoramaWcsSolution,
        // darf also der Mauer-Prüfung NICHT vertraut werden dürfen.
        val projection = FisheyeProjection(cx = 500.0, cy = 400.0, f = 1000.0, k1 = -0.23)
        val tileSolution = PanoramaWcsSolution(projection, Mat3.IDENTITY)
        val farDir = raDecToVector(0.0, -30.0) // theta = 90-(-30) = 120 Grad
        val farPixel = projection.directionToPixel(farDir)
        assertNotNull("Kollaps-Vorbedingung: weit entfernter Punkt bekommt trotzdem einen Pixel", farPixel)

        val tile = TileWcs(tileSolution, tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))

        assertFalse(mosaic.isClaimedByAnyTile(SkyPoint(0f, -30f)))
    }

    @Test
    fun isClaimedByAnyTileHonorsMarginFractionAtTileEdge() {
        // Kachel deckt (per basicSolution + tileWidth=1000) ~RA 0-5 Grad ab. RA=5.5 Grad -> Pixel-X
        // ~1051.7 (knapp jenseits von 1000, aber innerhalb der 10%-Marge bis 1100) -> beansprucht.
        // RA=8 Grad -> Pixel-X ~1305.2 (deutlich jenseits der Marge) -> nicht beansprucht.
        val tile = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))

        assertTrue(mosaic.isClaimedByAnyTile(SkyPoint(5.5f, 0f)))
        assertFalse(mosaic.isClaimedByAnyTile(SkyPoint(8f, 0f)))
    }

    @Test
    fun skyToImageAveragesTwoIdenticalOverlappingTilesToTheSameResult() {
        // (a) Zwei exakt übereinstimmende überlappende Kacheln -> Ergebnis identisch zu beiden.
        val tileA = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tileB = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tileA, tileB))

        val point = mosaic.skyToImage(SkyPoint(0f, 0f), imageHeight = 800)

        assertNotNull(point)
        assertEquals(500f, point!!.x, 0.01f)
        assertEquals(400f, point.y, 0.01f)
    }

    @Test
    fun skyToImageBlendsBetweenTwoOverlappingTilesInsteadOfSnappingToOne() {
        // (b) Künstlicher 10px-Offset zwischen zwei überlappenden Kacheln -> Ergebnis liegt dazwischen,
        // nicht hart auf A (500) oder B (510).
        val tileA = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val tileBSolution = basicSolution().copy(crPix1 = 511.0) // simuliert abweichenden Restfehler
        val tileB = TileWcs(tileBSolution, tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tileA, tileB))

        val point = mosaic.skyToImage(SkyPoint(0f, 0f), imageHeight = 800)

        assertNotNull(point)
        assertTrue("darf nicht hart auf Kachel A einrasten", point!!.x > 500f)
        assertTrue("darf nicht hart auf Kachel B einrasten", point.x < 510f)
        assertEquals(505f, point.x, 0.01f) // (500+510)/2 -- alter Code hätte exakt 500 geliefert (distSq=0)
        assertEquals(400f, point.y, 0.01f)
    }

    @Test
    fun skyToImageMatchesSingleTileWcsExactlyWhenOnlyOneTileClaimsThePoint() {
        // (c1) Regressionsschutz: EINE Kachel, Punkt tief innerhalb -> unverändert wie vor dem Fix.
        val tile = TileWcs(basicSolution(), tileOffsetX = 200, tileOffsetY = 100, tileWidth = 1000, tileHeight = 800)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile))

        val point = mosaic.skyToImage(SkyPoint(0f, 0f), imageHeight = 800)

        assertNotNull(point)
        assertEquals(700f, point!!.x, 0.001f) // 500 (Kachel-lokal) + 200 (tileOffsetX)
        assertEquals(500f, point.y, 0.001f)   // 400 (Kachel-lokal) + 100 (tileOffsetY)
    }

    @Test
    fun skyToImageStillBlendsToFallbackNearTileEdgeWhenOnlyOneTileIsInvolved() {
        // (c2) Regressionsschutz für den BEREITS gerätegetesteten Einzelkachel-Blend (Kepheus/Eidechse,
        // 2026-07-29): EINE Kachel fährt in ihre eigene Randmarge, KEINE zweite Kachel beteiligt.
        val tileWcsSolution = basicSolution()
        val tile = TileWcs(tileWcsSolution, tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val fallbackSolution = basicSolution().copy(crPix1 = 1501.0) // klar unterscheidbarer Fallback
        val mosaic = MosaicWcsSolution(tiles = listOf(tile), fallback = fallbackSolution)
        // RA=5.5 Grad -> Pixel-X ~1051.7, s. isClaimedByAnyTileHonorsMarginFractionAtTileEdge oben:
        // knapp jenseits von 1000, aber innerhalb der 10%-Marge bis 1100.
        val point = SkyPoint(5.5f, 0f)

        val tileLocal = tileWcsSolution.skyToImage(point, imageHeight = 800)!!
        val fallbackPoint = fallbackSolution.skyToImage(point, imageHeight = 800)!!
        val marginPx = 1000 * 0.10f // dupliziert bewusst den privaten BLEND_MARGIN_FRACTION-Wert (0.10f),
        // exakt wie der bestehende Test isClaimedByAnyTileHonorsMarginFractionAtTileEdge es bereits tut.
        val expectedTrust = (1f - (tileLocal.x - 1000f) / marginPx).coerceIn(0f, 1f)
        assertTrue("Vorbedingung: Punkt muss strikt in der Blend-Marge liegen", expectedTrust in 0.01f..0.99f)

        val result = mosaic.skyToImage(point, imageHeight = 800)

        assertNotNull(result)
        assertEquals(tileLocal.x * expectedTrust + fallbackPoint.x * (1f - expectedTrust), result!!.x, 0.01f)
        assertEquals(tileLocal.y * expectedTrust + fallbackPoint.y * (1f - expectedTrust), result.y, 0.01f)
    }

    @Test
    fun skyToImageReturnsPureFallbackWhenNoTileClaimsThePoint() {
        // (d) Reiner Fallback-Bereich (keine Kachel beansprucht den Punkt) -> unverändert.
        val tile = TileWcs(basicSolution(), tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800)
        val fallbackSolution = basicSolution().copy(crPix1 = 1501.0)
        val mosaic = MosaicWcsSolution(tiles = listOf(tile), fallback = fallbackSolution)

        // 170 Grad entfernt -> Gnomonik-Nenner <= 0, tile.wcs.skyToImage liefert null -> candidates leer.
        val point = mosaic.skyToImage(SkyPoint(170f, 0f), imageHeight = 800)
        val expected = fallbackSolution.skyToImage(SkyPoint(170f, 0f), imageHeight = 800)

        assertNotNull("Vorbedingung: Fallback muss hier selbst einen Pixel liefern", expected)
        assertEquals(expected!!.x, point!!.x, 0.001f)
        assertEquals(expected.y, point.y, 0.001f)
    }

    @Test
    fun skyToImageAveragesAcrossFourSimultaneouslyOverlappingTilesAtAGridCorner() {
        // (e) Zusatztest: 3+/4 Kacheln gleichzeitig trust=1.0 (wie an einer 2x2-Rasterecke) ->
        // Formel braucht keinen Sonderfall, mittelt einfach über alle 4.
        val tiles = listOf(500, 510, 520, 530).map { localX ->
            TileWcs(
                basicSolution().copy(crPix1 = localX + 1.0),
                tileOffsetX = 0, tileOffsetY = 0, tileWidth = 1000, tileHeight = 800,
            )
        }
        val mosaic = MosaicWcsSolution(tiles = tiles)

        val point = mosaic.skyToImage(SkyPoint(0f, 0f), imageHeight = 800)

        assertNotNull(point)
        assertEquals(515f, point!!.x, 0.01f) // (500+510+520+530)/4
        assertEquals(400f, point.y, 0.01f)
    }

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
}
