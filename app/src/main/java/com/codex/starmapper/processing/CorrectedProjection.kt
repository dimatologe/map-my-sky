package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.min

/**
 * [PanoramaProjection] als glattes KORREKTUR-Feld über einem bereits gefitteten [baseline]-Modell
 * -- KEIN eigenständiger Formel-Ersatz. Für jeden Kontrollpunkt ist die Korrektur die Differenz
 * zwischen der tatsächlichen Position und [baseline]s eigener Vorhersage (typischerweise klein,
 * wenige bis einige Dutzend Pixel). Eine Anfrage bekommt [baseline]s Vorhersage PLUS eine gewichtete
 * Mittelung der Korrekturen NAHER Kontrollpunkte; weit von jedem Kontrollpunkt entfernt sinkt das
 * Gesamtgewicht gegen 0 -> das Ergebnis nähert sich von selbst der reinen [baseline]-Vorhersage,
 * statt zu extrapolieren oder abrupt umzuschalten. Damit ist diese Klasse strukturell (fast) nie
 * schlechter als [baseline] allein.
 *
 * Vorgängerversion (s. Git-Historie): ein Delaunay-Dreiecksnetz + baryzentrische Interpolation
 * DIREKT über den Kontrollpunkten, im gnomonischen (u,v)-Tangentialraum gesucht. Das lieferte dank
 * des Baseline-Rückfalls keine Katastrophen-Fehler mehr, aber die räumliche ZUORDNUNG (welche
 * Korrektur wo gilt) erbte weiterhin die Verzerrung dieses Raums bei weiten Sichtfeldern
 * (Gerätebeleg 2026-08-16: Mesh blieb dadurch immer ~2% hinter dem besten starren Modell zurück,
 * nie ein echter Gewinn). Diese Fassung sucht/gewichtet stattdessen im PIXEL-Raum -- der ist immer
 * flach und unverzerrt, unabhängig vom Sichtfeld der Aufnahme (etabliertes Muster: geodätische
 * Distortion-Grids/NADCON/NTv2, Shepards Methode/Inverse-Distance-Weighting, s. Recherche
 * 2026-08-16).
 *
 * [controlPoints] werden zuerst NACH KACHEL gruppiert ([groupSizes], 1:1 zu [FisheyeRefiner.fitMesh]s
 * gleichnamigem Parameter) und zu je EINEM Repräsentativpunkt zusammengefasst ("Declustering", s.
 * PixInsight-Recherche: die eng geklumpten Rohpunkte EINER Kachel würden eine dünn besetzte
 * Nachbarkachel sonst allein durch Punktzahl überproportional dominieren -- das Prinzip trifft
 * jede Methode, die aus Rohpunkten statt aus deren KACHEL-URSPRUNG gewichtet). Punkte, an denen
 * [baseline] selbst keine Vorhersage liefert, fallen aus dieser Kachel-Mittelung heraus (nicht aus
 * der ganzen Kachel, s. init).
 *
 * [groupWeights] (optional, 1:1 zu [groupSizes]): Vertrauens-Gewicht je Kachel-Gruppe (s.
 * TileConsistency.tileReliabilityWeights) -- multipliziert zusätzlich in [blendedCorrection]s
 * Umgebungs-Gewicht hinein, sodass eine unsichere Kachel bei der Mittelung naher Korrekturen
 * proportional weniger zählt, unabhängig von ihrem rein geometrischen Abstand. Default 1.0 überall
 * = bisheriges, ungewichtetes Verhalten.
 */
class CorrectedProjection(
    private val baseline: PanoramaProjection,
    controlPoints: List<Pair<Offset, Vec3>>,
    groupSizes: List<Int> = List(controlPoints.size) { 1 },
    groupWeights: List<Double> = List(groupSizes.size) { 1.0 },
) : PanoramaProjection {

    private data class ClusterNode(
        val anchorX: Double,
        val anchorY: Double,
        val dx: Double,
        val dy: Double,
        val reliability: Double,
    )

    private val nodes: List<ClusterNode>
    private val radiusSq: Double

    init {
        val built = ArrayList<ClusterNode>(groupSizes.size)
        var start = 0
        for (groupIndex in groupSizes.indices) {
            val size = groupSizes[groupIndex]
            var sumAx = 0.0
            var sumAy = 0.0
            var sumDx = 0.0
            var sumDy = 0.0
            var count = 0
            for (i in start until (start + size).coerceAtMost(controlPoints.size)) {
                val (actualPixel, dir) = controlPoints[i]
                val basePixel = baseline.directionToPixel(dir) ?: continue
                sumAx += basePixel.x
                sumAy += basePixel.y
                sumDx += actualPixel.x - basePixel.x
                sumDy += actualPixel.y - basePixel.y
                count++
            }
            if (count > 0) {
                built += ClusterNode(
                    sumAx / count, sumAy / count, sumDx / count, sumDy / count,
                    reliability = groupWeights.getOrElse(groupIndex) { 1.0 },
                )
            }
            start += size
        }
        nodes = built
        radiusSq = supportRadiusSq(nodes)
    }

    /** Anzahl Kachel-Repräsentativpunkte nach dem Declustering (0, wenn [baseline] nirgends griff). */
    val nodeCount: Int get() = nodes.size

    override fun directionToPixel(dir: Vec3): Offset? {
        val basePixel = baseline.directionToPixel(dir) ?: return null
        val (dx, dy, confidence) = blendedCorrection(basePixel.x.toDouble(), basePixel.y.toDouble())
        if (confidence <= 0.0) return basePixel
        val x = basePixel.x + (dx * confidence).toFloat()
        val y = basePixel.y + (dy * confidence).toFloat()
        if (!x.isFinite() || !y.isFinite()) return basePixel
        return Offset(x, y)
    }

    override fun pixelToDirection(px: Double, py: Double): Vec3? {
        // Eine Fixpunkt-Iteration genügt (Korrekturen sind klein, s. Klassenkommentar): die
        // Korrektur AN DER ZIEL-PIXEL-POSITION abgreifen, vom Ziel abziehen, dann mit dem
        // korrigierten Pixel über die Baseline invertieren. Kein Newton-artiges Nachiterieren nötig.
        val (dx, dy, confidence) = blendedCorrection(px, py)
        val refined = baseline.pixelToDirection(px - dx * confidence, py - dy * confidence)
        return refined ?: baseline.pixelToDirection(px, py)
    }

    /**
     * Gewichtetes Mittel der Korrekturen naher [nodes] an (qx,qy), plus ein KONFIDENZWERT
     * (0..1 = Gesamtgewicht, gedeckelt): entfernt sich (qx,qy) von jedem Knoten, sinkt die
     * Konfidenz gegen 0 -- der Aufrufer skaliert die zurückgegebene Korrektur damit selbst
     * (statt hier hart auf 0 zu springen), s. Klassenkommentar.
     */
    private fun blendedCorrection(qx: Double, qy: Double): Triple<Double, Double, Double> {
        if (nodes.isEmpty()) return Triple(0.0, 0.0, 0.0)
        var sumW = 0.0
        var sumWDx = 0.0
        var sumWDy = 0.0
        for (n in nodes) {
            val ddx = qx - n.anchorX
            val ddy = qy - n.anchorY
            val distSq = ddx * ddx + ddy * ddy
            val w = compactWeight(distSq, radiusSq) * n.reliability
            if (w <= 0.0) continue
            sumW += w
            sumWDx += w * n.dx
            sumWDy += w * n.dy
        }
        if (sumW <= MIN_WEIGHT_SUM) return Triple(0.0, 0.0, 0.0)
        return Triple(sumWDx / sumW, sumWDy / sumW, min(1.0, sumW))
    }

    private companion object {
        // Wie viele mittlere Nächster-Nachbar-Abstände (s. supportRadiusSq) ein Knoten noch spürbar
        // mitgewichtet -- 3x sorgt dafür, dass die LÜCKE zwischen zwei benachbarten Kacheln (bei der
        // Hälfte des Nachbarabstands) klar innerhalb BEIDER Radien liegt, während 2-3 Kacheln weiter
        // entfernte Punkte praktisch keinen Einfluss mehr haben. Reine Erfahrungsentscheidung, am
        // Gerät nachjustierbar.
        const val RADIUS_MULTIPLIER = 3.0

        // Bei nur einem einzigen Knoten (kein Nachbar, um einen typischen Abstand zu schätzen):
        // dessen Korrektur gilt dann näherungsweise ÜBERALL (kompaktes Gewicht bleibt nahe 1 im
        // gesamten realistischen Bildbereich) -- in der Praxis kaum erreichbar, da fitMesh() vorher
        // schon mindestens 2 Kachel-Gruppen für die Kreuzvalidierung verlangt; nur zur Absicherung
        // des Einzelfalls, falls diese Klasse direkt (ohne fitMesh) mit wenigen Gruppen genutzt wird.
        const val SINGLE_NODE_RADIUS_SQ = 1.0e14

        const val MIN_WEIGHT_SUM = 1e-9

        fun compactWeight(distSq: Double, radiusSq: Double): Double {
            if (distSq >= radiusSq) return 0.0
            val t = 1.0 - distSq / radiusSq
            return t * t
        }

        fun supportRadiusSq(nodes: List<ClusterNode>): Double {
            if (nodes.size < 2) return SINGLE_NODE_RADIUS_SQ
            val nearest = nodes.map { n ->
                nodes.filter { it !== n }.minOf { o -> hypot(n.anchorX - o.anchorX, n.anchorY - o.anchorY) }
            }.sorted()
            val median = nearest[nearest.size / 2]
            val radius = median * RADIUS_MULTIPLIER
            return radius * radius
        }
    }
}
