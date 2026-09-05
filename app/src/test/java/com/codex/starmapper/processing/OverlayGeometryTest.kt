package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.DrawLayer
import com.codex.starmapper.domain.OverlayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die WYSIWYG-Invarianten: alle Overlay-Maße sind in Bild-Pixeln definiert und
 * hängen NICHT von der Sternbild-Bounding-Box oder einer Bildschirm-/Zoom-Größe ab.
 * Editor (= Wert * viewport.scale) und Export (= Wert) bleiben dadurch deckungsgleich.
 */
class OverlayGeometryTest {

    private fun constellation(
        size: Size,
        anchorRadiusRatio: Float = 0.045f,
        strokeWidth: Float = 3f,
        nameTextSize: Float = 30f,
    ) = AnnotationOverlay(
        id = 1L,
        kind = OverlayKind.Constellation,
        center = Offset(500f, 400f),
        size = size,
        anchorRadiusRatio = anchorRadiusRatio,
        strokeWidth = strokeWidth,
        nameTextSize = nameTextSize,
    )

    @Test
    fun anchorRadiusIndependentOfBoundingBox() {
        val small = constellation(Size(200f, 150f))
        val large = constellation(Size(3000f, 2400f))
        val imageMinDim = 4000f

        val rSmall = OverlayGeometry.constellationAnchorRadius(small, imageMinDim)
        val rLarge = OverlayGeometry.constellationAnchorRadius(large, imageMinDim)

        assertEquals(rSmall, rLarge, 1e-4f)
        // Konstanter Bruchteil der kürzeren Bildkante.
        assertEquals(imageMinDim * 0.006f, rSmall, 1e-4f)
    }

    @Test
    fun anchorRadiusScalesWithImageNotWithBoundingBox() {
        val overlay = constellation(Size(800f, 600f))
        val r2000 = OverlayGeometry.constellationAnchorRadius(overlay, 2000f)
        val r4000 = OverlayGeometry.constellationAnchorRadius(overlay, 4000f)
        assertEquals(2f, r4000 / r2000, 1e-4f)
    }

    @Test
    fun anchorSliderScalesRadiusWithinBounds() {
        val tiny = constellation(Size(800f, 600f), anchorRadiusRatio = 0.0f)
        val huge = constellation(Size(800f, 600f), anchorRadiusRatio = 1.0f)
        val base = constellation(Size(800f, 600f), anchorRadiusRatio = 0.045f)
        val imageMinDim = 3000f

        // Faktor ist auf [0.4, 1.8] geklemmt.
        assertEquals(
            imageMinDim * 0.006f * 0.4f,
            OverlayGeometry.constellationAnchorRadius(tiny, imageMinDim),
            1e-3f,
        )
        assertEquals(
            imageMinDim * 0.006f * 1.8f,
            OverlayGeometry.constellationAnchorRadius(huge, imageMinDim),
            1e-3f,
        )
        assertEquals(
            imageMinDim * 0.006f,
            OverlayGeometry.constellationAnchorRadius(base, imageMinDim),
            1e-3f,
        )
    }

    @Test
    fun trimGapLargerWhenAnchorsVisible() {
        val anchorRadius = 24f
        val stroke = OverlayGeometry.strokeWidth(3f)
        val withAnchors = OverlayGeometry.constellationLineTrimGap(anchorRadius, stroke, true)
        val withoutAnchors = OverlayGeometry.constellationLineTrimGap(anchorRadius, stroke, false)
        assertTrue(withAnchors > withoutAnchors)
        // Anker sichtbar: Lücke liegt außerhalb des Rings.
        assertTrue(withAnchors > anchorRadius)
    }

    @Test
    fun strokeWidthHasImageSpaceFloor() {
        assertEquals(2f, OverlayGeometry.strokeWidth(0f), 1e-4f)
        assertEquals(8f, OverlayGeometry.strokeWidth(4f), 1e-4f)
    }

    @Test
    fun textSizesClampInImageSpace() {
        assertEquals(14f, OverlayGeometry.constellationNameTextSize(constellation(Size(10f, 10f), nameTextSize = 1f)), 1e-4f)
        assertEquals(96f, OverlayGeometry.constellationNameTextSize(constellation(Size(10f, 10f), nameTextSize = 500f)), 1e-4f)
    }

    @Test
    fun groupByDrawLayerMergesDeepSkyAndManualOverlaysIntoObjects() {
        val constellationOverlay = AnnotationOverlay(
            id = 1L, kind = OverlayKind.Constellation, center = Offset.Zero, size = Size(1f, 1f),
            layer = AnnotationLayer.Constellation,
        )
        val deepSkyOverlay = AnnotationOverlay(
            id = 2L, kind = OverlayKind.Ellipse, center = Offset.Zero, size = Size(1f, 1f),
            layer = AnnotationLayer.DeepSky,
        )
        val manualTextOverlay = AnnotationOverlay(
            id = 3L, kind = OverlayKind.Text, center = Offset.Zero, size = Size(1f, 1f),
            layer = null,
        )
        val starOverlay = AnnotationOverlay(
            id = 4L, kind = OverlayKind.Ellipse, center = Offset.Zero, size = Size(1f, 1f),
            layer = AnnotationLayer.Star,
        )

        val grouped = OverlayGeometry.groupByDrawLayer(
            listOf(constellationOverlay, deepSkyOverlay, manualTextOverlay, starOverlay),
        )

        assertEquals(listOf(constellationOverlay), grouped[DrawLayer.Constellation])
        // DeepSky UND die nutzerplatzierte Textform landen BEIDE im Objects-Bucket, in stabiler
        // Original-Reihenfolge -- Nutzerwunsch, dass eigene Zeichnungen zur Schicht "Objekte" zählen.
        assertEquals(listOf(deepSkyOverlay, manualTextOverlay), grouped[DrawLayer.Objects])
        assertEquals(listOf(starOverlay), grouped[DrawLayer.Star])
        assertTrue(DrawLayer.MilkyWay !in grouped)
        assertTrue(DrawLayer.Graticule !in grouped)
    }

    @Test
    fun splitPolylineAtSeamPassesThroughWhenNoCrossing() {
        val points = listOf(Offset(10f, 20f), Offset(500f, 400f), Offset(990f, 100f))
        val result = OverlayGeometry.splitPolylineAtSeam(points, imageWidth = 1000)
        assertEquals(listOf(points), result)
    }

    @Test
    fun splitPolylineAtSeamSplitsAtExactCrossing() {
        // Entfaltete Kante (990,100)->(1010,110) bei imageWidth=1000: Schnittpunkt exakt bei x=1000,
        // t=0.5 -> y=105. Erstes Teilstück (990,100)-(1000,105) braucht keine Verschiebung (avgX=995,
        // Vielfaches 0). Zweites Teilstück (1000,105)-(1010,110) hat avgX=1005 -> Vielfaches 1000 ->
        // wird um 1000 zurückverschoben zu (0,105)-(10,110). Ergebnis: zwei kurze Teilstücke, je an
        // ihrer Bildkante endend -- "verbindet ohne Lücke", statt einer langen Querlinie oder gar
        // nichts.
        val result = OverlayGeometry.splitPolylineAtSeam(
            listOf(Offset(990f, 100f), Offset(1010f, 110f)),
            imageWidth = 1000,
        )
        assertEquals(2, result.size)
        assertEquals(Offset(990f, 100f), result[0][0])
        assertEquals(1000f, result[0][1].x, 0.01f)
        assertEquals(105f, result[0][1].y, 0.01f)
        assertEquals(0f, result[1][0].x, 0.01f)
        assertEquals(105f, result[1][0].y, 0.01f)
        assertEquals(Offset(10f, 110f), result[1][1])
    }

    @Test
    fun splitPolylineAtSeamHandlesDegenerateInputsWithoutCrashing() {
        assertEquals(emptyList<List<Offset>>(), OverlayGeometry.splitPolylineAtSeam(emptyList(), imageWidth = 1000))
        val single = listOf(Offset(5f, 5f))
        assertEquals(listOf(single), OverlayGeometry.splitPolylineAtSeam(single, imageWidth = 1000))
    }

    @Test
    fun clampAxisRatioPassesThroughWhenWithinBound() {
        val (a, b) = OverlayGeometry.clampAxisRatio(10f, 5f, 20f)
        assertEquals(10f, a, 1e-4f)
        assertEquals(5f, b, 1e-4f)
    }

    @Test
    fun clampAxisRatioPreservesGeometricMeanWhenClamping() {
        // sigma1=800, sigma2=2 -> Verhältnis 400:1, weit über dem Deckel 20:1. Erwartung von Hand:
        // gm = sqrt(800*2) = sqrt(1600) = 40; factor = sqrt(20). a' = 40*sqrt(20) ≈ 178.8854,
        // b' = 40/sqrt(20) ≈ 8.9443 -- Verhältnis a'/b' = 20 exakt, geometrisches Mittel sqrt(a'*b')
        // bleibt exakt 40 (unverändert gegenüber dem Original-sqrt(800*2)).
        val (a, b) = OverlayGeometry.clampAxisRatio(800f, 2f, 20f)
        assertEquals(178.8854f, a, 0.01f)
        assertEquals(8.9443f, b, 0.01f)
        assertEquals(20f, a / b, 0.001f)
        assertEquals(40.0, Math.sqrt((a * b).toDouble()), 0.01)
    }

    @Test
    fun clampAxisRatioMatchesDiagnosedNadirSliverCase() {
        // Reale Werte aus der Diagnose 2026-08-24 (overlay id:424, vor dem Naht-Fix):
        // 4404,395 x 1,0308px -- ein 4276:1-Ausreißer. Nach dem Deckel (20:1) muss die Fläche
        // ungefähr erhalten bleiben (Größenordnung, kein Sub-Pixel-Strich mehr) und das Verhältnis
        // exakt 20:1 sein.
        val (a, b) = OverlayGeometry.clampAxisRatio(4404.395f, 1.0308f, 20f)
        assertEquals(20f, a / b, 0.001f)
        assertTrue(a < 400f) // deutlich kleiner als die ursprünglichen 4404px
        assertTrue(b > 10f) // deutlich sichtbar statt Sub-Pixel
    }

    @Test
    fun clampAxisRatioLeavesNonPositiveInputsUnchanged() {
        val (a, b) = OverlayGeometry.clampAxisRatio(0f, 5f, 20f)
        assertEquals(0f, a, 1e-4f)
        assertEquals(5f, b, 1e-4f)
    }

}
