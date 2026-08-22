package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGeometryTest {

    @Test
    fun densifyRespectsMaxSegment() {
        val points = listOf(Offset(0f, 0f), Offset(100f, 0f))
        val dense = StrokeGeometry.densify(points, 10f)
        // Endpunkte bleiben erhalten, kein Segment länger als maxSegment.
        assertEquals(Offset(0f, 0f), dense.first())
        assertEquals(Offset(100f, 0f), dense.last())
        dense.zipWithNext().forEach { (a, b) ->
            assertTrue((b - a).getDistance() <= 10f + 1e-3f)
        }
    }

    @Test
    fun wobbleKeepsEndpointsAndIsDeterministic() {
        val points = StrokeGeometry.densify(listOf(Offset(0f, 0f), Offset(100f, 0f)), 10f)
        val a = StrokeGeometry.wobble(points, 4f, seed = 42L)
        val b = StrokeGeometry.wobble(points, 4f, seed = 42L)
        assertEquals(points.first(), a.first())
        assertEquals(points.last(), a.last())
        // Gleicher Seed -> exakt gleiches Ergebnis (WYSIWYG-invariant).
        assertEquals(a, b)
        // Innenpunkte um maximal die Amplitude (in x und y) verschoben.
        for (i in 1 until points.size - 1) {
            assertTrue(kotlin.math.abs(a[i].x - points[i].x) <= 4f + 1e-3f)
            assertTrue(kotlin.math.abs(a[i].y - points[i].y) <= 4f + 1e-3f)
        }
    }

    @Test
    fun taperedOutlineIsThinAtEndsAndThickInMiddle() {
        val n = 11
        val center = (0 until n).map { Offset(it * 10f, 0f) }
        val baseWidth = 20f
        val outline = StrokeGeometry.taperedOutline(center, baseWidth, endFraction = 0.28f)
        // Umriss = linke Seite (n) + rechte Seite rückwärts (n).
        assertEquals(2 * n, outline.size)

        fun crossWidth(i: Int): Float = (outline[i] - outline[2 * n - 1 - i]).getDistance()

        val endWidth = crossWidth(0)
        val midWidth = crossWidth(n / 2)
        // Enden ~ baseWidth * endFraction, Mitte ~ baseWidth.
        assertEquals(baseWidth * 0.28f, endWidth, 1.5f)
        assertEquals(baseWidth, midWidth, 1.5f)
        assertTrue(midWidth > endWidth * 2f)
    }

    @Test
    fun hashUnitIsBoundedAndDeterministic() {
        for (i in 0 until 50) {
            val v = StrokeGeometry.hashUnit(7L, i)
            assertTrue(v in -1f..1f)
            assertEquals(v, StrokeGeometry.hashUnit(7L, i), 0f)
        }
    }
}
