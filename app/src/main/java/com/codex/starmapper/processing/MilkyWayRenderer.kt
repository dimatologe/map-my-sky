package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.MilkyWayLayer
import kotlin.math.hypot
import kotlin.math.max

/** Eine geschlossene Umriss-Fläche (Bild-Koordinaten) einer Dichtestufe. */
data class MilkyWayRing(val points: List<Offset>)

data class MilkyWayLevelGeometry(val level: Int, val rings: List<MilkyWayRing>)

/** Fertig berechnete Milchstraßen-Geometrie (Editor + Export zeichnen exakt dasselbe -> WYSIWYG). */
data class MilkyWayGeometry(val levels: List<MilkyWayLevelGeometry>)

/**
 * Projiziert die Milchstraßen-Dichtekonturen (RA/Dec-Ringe je Dichtestufe, siehe
 * [com.codex.starmapper.data.MilkyWayAssetLoader]) über die gelöste WCS ins Bild. Bewusst ohne
 * Compose-/Android-Abhängigkeit (wie [GraticuleRenderer]), damit Editor und Export exakt dieselbe
 * Geometrie zeichnen.
 *
 * Ringe, bei denen die Projektion irgendwo abbricht (außerhalb des Modells, z.B. Fisheye-
 * Rückhemisphäre) oder über eine unplausibel große Distanz springt (Naht-/Divergenz-Sprung), werden
 * verworfen statt eine falsch quer über das Bild gezogene Fläche zu zeichnen — derselbe Cull-Ansatz
 * wie bei den Sternbildlinien/dem Koordinatennetz.
 */
object MilkyWayRenderer {
    fun compute(
        layers: List<MilkyWayLayer>,
        wcs: WcsSolutionLike,
        imageWidth: Int,
        imageHeight: Int,
    ): MilkyWayGeometry {
        if (imageWidth <= 0 || imageHeight <= 0 || layers.isEmpty()) {
            return MilkyWayGeometry(emptyList())
        }
        val longEdge = max(imageWidth, imageHeight).toFloat()
        // Großzügig: Milchstraßen-Konturen sind großflächig, echte Nachbarpunkte im Ring liegen
        // i.d.R. viel näher zusammen als ein Naht-/Rückhemisphären-Sprung.
        val jumpLimit = longEdge * 0.35f
        val levels = layers.map { layer ->
            val rings = layer.polygons.flatMap { polygon ->
                polygon.rings.mapNotNull { ring -> projectRing(ring, wcs, imageHeight, jumpLimit) }
            }
            MilkyWayLevelGeometry(layer.level, rings)
        }
        return MilkyWayGeometry(levels)
    }

    private fun projectRing(
        ring: List<com.codex.starmapper.domain.SkyPoint>,
        wcs: WcsSolutionLike,
        imageHeight: Int,
        jumpLimit: Float,
    ): MilkyWayRing? {
        val projected = ArrayList<Offset>(ring.size)
        var prev: Offset? = null
        for (sky in ring) {
            val p = wcs.skyToImage(sky, imageHeight) ?: return null
            if (!p.x.isFinite() || !p.y.isFinite()) return null
            val last = prev
            if (last != null) {
                val jump = hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()).toFloat()
                if (jump > jumpLimit) return null
            }
            projected += p
            prev = p
        }
        return if (projected.size < 3) null else MilkyWayRing(projected)
    }
}
