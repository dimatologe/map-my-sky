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
}
