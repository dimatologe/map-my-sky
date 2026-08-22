package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Reine (Android-freie, testbare) Geometrie für „echte" Striche: Mittellinie verdichten,
 * leicht „menschlich" verwackeln und in ein Pinsel-Polygon mit Auslauf (Taper) verwandeln.
 *
 * Einheiten-agnostisch: dieselbe Funktion arbeitet im Editor (Bildschirm-px) und im Export
 * (Bild-px). Solange Amplitude/Segmentlänge proportional zur Strichbreite sind und der Seed
 * gleich bleibt, ist die Editor-Vorschau eine uniform skalierte Kopie des Exports (WYSIWYG).
 */
object StrokeGeometry {

    /** Fügt Zwischenpunkte ein, sodass kein Segment länger als [maxSegment] ist. */
    fun densify(points: List<Offset>, maxSegment: Float): List<Offset> {
        if (points.size < 2 || maxSegment <= 0f) return points
        val out = ArrayList<Offset>(points.size * 2)
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val dist = (b - a).getDistance()
            val steps = max(1, ceil(dist / maxSegment).toInt())
            for (s in 0 until steps) out += a + (b - a) * (s.toFloat() / steps)
        }
        out += points.last()
        return out
    }

    /**
     * Verschiebt die Innenpunkte deterministisch um bis zu [amplitude] – ein konstantes (per
     * [seed] reproduzierbares) Zittern, das den Strich von Hand gezogen wirken lässt. Endpunkte
     * bleiben fest (Sternbildlinien treffen weiter exakt die Sterne).
     */
    fun wobble(points: List<Offset>, amplitude: Float, seed: Long): List<Offset> {
        if (points.size < 3 || amplitude <= 0f) return points
        return points.mapIndexed { index, point ->
            if (index == 0 || index == points.size - 1) {
                point
            } else {
                point + Offset(
                    hashUnit(seed, index * 2) * amplitude,
                    hashUnit(seed, index * 2 + 1) * amplitude,
                )
            }
        }
    }

    /**
     * Baut aus einer Mittellinie das gefüllte Pinsel-Polygon: dünn an den Enden, dick in der
     * Mitte (sin-Profil). [endFraction] hält die Enden auf einem Mindestanteil der Breite, damit
     * an Sternbild-Knoten (wo mehrere Linien zusammenlaufen) nichts ausfranst.
     */
    fun taperedOutline(
        centerline: List<Offset>,
        baseWidth: Float,
        endFraction: Float = 0.28f,
        power: Float = 0.62f,
    ): List<Offset> {
        val n = centerline.size
        if (n < 2 || baseWidth <= 0f) return emptyList()
        val cumulative = FloatArray(n)
        for (i in 1 until n) cumulative[i] = cumulative[i - 1] + (centerline[i] - centerline[i - 1]).getDistance()
        val total = cumulative[n - 1].coerceAtLeast(1e-3f)

        fun tangent(i: Int): Offset {
            val before = if (i > 0) centerline[i] - centerline[i - 1] else Offset.Zero
            val after = if (i < n - 1) centerline[i + 1] - centerline[i] else Offset.Zero
            val sum = before + after
            val len = sum.getDistance()
            return if (len < 1e-4f) Offset(1f, 0f) else sum / len
        }

        fun halfWidth(t: Float): Float {
            val s = sin(PI.toFloat() * t).coerceIn(0f, 1f)
            val profile = endFraction + (1f - endFraction) * s.pow(power)
            return baseWidth * 0.5f * profile
        }

        val left = ArrayList<Offset>(n)
        val right = ArrayList<Offset>(n)
        for (i in 0 until n) {
            val t = cumulative[i] / total
            val tan = tangent(i)
            val normal = Offset(-tan.y, tan.x)
            val hw = halfWidth(t)
            left += centerline[i] + normal * hw
            right += centerline[i] - normal * hw
        }
        // Umriss = linke Seite vorwärts, rechte Seite rückwärts -> geschlossenes Polygon.
        val outline = ArrayList<Offset>(n * 2)
        outline += left
        for (i in right.indices.reversed()) outline += right[i]
        return outline
    }

    /** Deterministische Pseudo-Zufallszahl in [-1, 1] aus [seed] und [index]. */
    fun hashUnit(seed: Long, index: Int): Float {
        var h = (seed.toInt() * 73856093) xor (index * 19349663) xor 0x5bd1e995
        h = h xor (h ushr 13)
        h *= 0x5bd1e995
        h = h xor (h ushr 15)
        return (h and 0xFFFF) / 32767.5f - 1f
    }
}
