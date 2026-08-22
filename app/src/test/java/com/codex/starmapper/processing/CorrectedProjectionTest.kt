package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CorrectedProjectionTest {

    private fun camDir(thetaDeg: Double, phiDeg: Double): Vec3 {
        val t = thetaDeg * PI / 180.0
        val p = phiDeg * PI / 180.0
        return Vec3(sin(t) * cos(p), sin(t) * sin(p), cos(t))
    }

    // Simple, voll kontrollierte Baseline (kein Import einer der "echten" Projektionsklassen nötig):
    // einfache Gnomonik, liefert bewusst null für z<=0.05 (hinter der Kamera) -- Grundlage für den
    // Null-Weiterreichungs-Test unten.
    private fun simpleGnomonicBaseline(): PanoramaProjection = object : PanoramaProjection {
        override fun directionToPixel(dir: Vec3): Offset? {
            val d = dir.normalized()
            if (d.z <= 0.05) return null
            return Offset((d.x / d.z * 1000.0).toFloat(), (d.y / d.z * 1000.0).toFloat())
        }

        override fun pixelToDirection(px: Double, py: Double): Vec3? =
            Vec3(px / 1000.0, py / 1000.0, 1.0).normalized()
    }

    @Test
    fun nodeCountReflectsDeclusteredGroupsNotRawPointCount() {
        val baseline = simpleGnomonicBaseline()
        val controlPoints = (0 until 12).map { i ->
            val dir = camDir(5.0 + i * 0.1, i * 30.0)
            baseline.directionToPixel(dir)!! to dir
        }
        // 12 Rohpunkte, aber nur 3 Kachel-Gruppen (5+4+3) -> nach dem Declustering genau 3 Knoten,
        // nicht 12 (s. Klassenkommentar an CorrectedProjection: eng geklumpte Rohpunkte EINER Kachel
        // dürfen eine dünn besetzte Nachbarkachel nicht allein durch Punktzahl dominieren).
        val corrected = CorrectedProjection(baseline, controlPoints, groupSizes = listOf(5, 4, 3))
        assertEquals(3, corrected.nodeCount)
    }

    @Test
    fun appliesIsolatedNodesOwnCorrectionAtItsCenter() {
        val baseline = simpleGnomonicBaseline()
        // 4 eng benachbarte Einzel-Kacheln (schmaler Ring nah der Achse, theta=5°) bestimmen einen
        // KLEINEN mittleren Nächster-Nachbar-Abstand und damit einen kleinen Stütz-Radius. Eine
        // fünfte, weit abgesetzte Kachel (theta=60°) liegt um ein Vielfaches jenseits dieses
        // Radius -- ihre eigene Korrektur darf an ihrem eigenen Zentrum also nicht von den vier
        // nahen Kacheln verwässert werden.
        fun soloTile(thetaDeg: Double, phiDeg: Double, dx: Float, dy: Float): Pair<Offset, Vec3> {
            val dir = camDir(thetaDeg, phiDeg)
            val basePixel = baseline.directionToPixel(dir)!!
            return Offset(basePixel.x + dx, basePixel.y + dy) to dir
        }
        val near = listOf(0.0, 90.0, 180.0, 270.0).map { phi -> soloTile(5.0, phi, 3f, -2f) }
        val isolated = soloTile(60.0, 45.0, 40f, -25f)
        val controlPoints = near + isolated
        val groupSizes = List(near.size) { 1 } + 1

        val corrected = CorrectedProjection(baseline, controlPoints, groupSizes)
        assertEquals(5, corrected.nodeCount)

        val isolatedDir = camDir(60.0, 45.0)
        val basePixel = baseline.directionToPixel(isolatedDir)!!
        val actual = corrected.directionToPixel(isolatedDir)
        assertNotNull(actual)
        assertEquals(basePixel.x + 40f, actual!!.x, 1.0f)
        assertEquals(basePixel.y - 25f, actual.y, 1.0f)
    }

    @Test
    fun fallsBackToBaselineFarFromAnyNode() {
        val baseline = simpleGnomonicBaseline()
        // Kontrollpunkte nur in einem engen Ring nahe der Achse (theta=5-10°) -> kleiner
        // Stütz-Radius (s. Klassenkommentar). Eine Abfrage bei theta=60° liegt um ein Vielfaches
        // jenseits davon -- muss auf die UNVERÄNDERTE Baseline-Vorhersage zurückfallen (kein
        // null, keine erfundene Korrektur).
        val controlPoints = listOf(5.0, 7.0, 10.0).flatMap { thetaDeg ->
            (0 until 6).map { step ->
                val dir = camDir(thetaDeg, step * 60.0)
                val basePixel = baseline.directionToPixel(dir)!!
                Offset(basePixel.x + 20f, basePixel.y + 20f) to dir
            }
        }
        val corrected = CorrectedProjection(baseline, controlPoints)

        val farDir = camDir(60.0, 30.0)
        val expected = baseline.directionToPixel(farDir)!!
        val actual = corrected.directionToPixel(farDir)
        assertNotNull("fällt auf Baseline zurück statt null", actual)
        assertEquals(expected.x, actual!!.x, 0.5f)
        assertEquals(expected.y, actual.y, 0.5f)
    }

    @Test
    fun propagatesBaselineNull() {
        val baseline = simpleGnomonicBaseline()
        val controlPoints = listOf(5.0, 7.0, 10.0).flatMap { thetaDeg ->
            (0 until 6).map { step ->
                val dir = camDir(thetaDeg, step * 60.0)
                baseline.directionToPixel(dir)!! to dir
            }
        }
        val corrected = CorrectedProjection(baseline, controlPoints)

        // Hinter der Kamera (z<=0.05, s. simpleGnomonicBaseline) -- die Baseline liefert dort schon
        // null; CorrectedProjection darf das NICHT durch eine erfundene Korrektur verschleiern.
        assertNull(corrected.directionToPixel(Vec3(0.0, 0.0, -1.0)))
    }

    @Test
    fun ignoresControlPointsWhereBaselineItselfFails() {
        val baseline = simpleGnomonicBaseline()
        // Einer von fünf Kontrollpunkten liegt hinter der Kamera (Baseline liefert dort null) --
        // muss beim Declustering übersprungen werden, statt den Aufbau der ganzen Kachel-Gruppe
        // (oder gar der ganzen Korrektur) zu verhindern.
        val validDirs = listOf(camDir(5.0, 0.0), camDir(5.0, 90.0), camDir(5.0, 180.0), camDir(5.0, 270.0))
        val controlPoints = validDirs.map { dir -> baseline.directionToPixel(dir)!! to dir } +
            (Offset(0f, 0f) to Vec3(0.0, 0.0, -1.0))
        val corrected = CorrectedProjection(baseline, controlPoints, groupSizes = listOf(controlPoints.size))
        assertTrue("eine Gruppe bleibt trotz eines ungültigen Punkts übrig", corrected.nodeCount == 1)
    }

    @Test
    fun lowerReliabilityNodeContributesProportionallyLessAtEqualDistance() {
        val baseline = simpleGnomonicBaseline()
        // Zwei Einzelpunkt-Knoten je 100px auseinander (Basis-Pixel (0,0) bzw. (100,0)), Anfrage exakt
        // in der Mitte (50,0) -> beide GEOMETRISCH gleich weit entfernt, gleiches kompaktes Gewicht vor
        // der Zuverlässigkeit. Gegensätzliche Korrekturen (+10/-10) machen den Zuverlässigkeits-Anteil
        // direkt sichtbar: ohne Gewichtung würden sie sich exakt aufheben (Ergebnis 50,0).
        val dirA = Vec3(0.0, 0.0, 1.0) // Basis-Pixel (0,0)
        val dirB = Vec3(0.1, 0.0, 1.0) // Basis-Pixel (100,0)
        val dirQuery = Vec3(0.05, 0.0, 1.0) // Basis-Pixel (50,0)
        val reliability = 0.5
        val corrected = CorrectedProjection(
            baseline,
            controlPoints = listOf(Offset(10f, 0f) to dirA, Offset(90f, 0f) to dirB),
            groupSizes = listOf(1, 1),
            groupWeights = listOf(1.0, reliability),
        )

        val result = corrected.directionToPixel(dirQuery)

        assertNotNull(result)
        // Exakte, von Hand hergeleitete Formel bei gleichem geometrischem Gewicht beider Knoten:
        // blendedDx = 10*(1-r)/(1+r) -- s. Testkommentar/Plan-Herleitung. Gesamtgewicht liegt hier
        // über 1.0 (auf 1.0 gedeckelte Konfidenz), die Formel gilt daher direkt ohne weitere Skalierung.
        val expectedDx = 10.0 * (1.0 - reliability) / (1.0 + reliability)
        assertEquals(50f + expectedDx.toFloat(), result!!.x, 0.05f)
        assertEquals(0f, result.y, 0.05f)
    }

    @Test
    fun nearZeroReliabilityNodeBehavesAsIfAbsent() {
        val baseline = simpleGnomonicBaseline()
        // Knoten C liegt GENAU auf der Anfrage (Distanz 0 -> maximales geometrisches Gewicht) und würde
        // ohne die Nahe-Null-Zuverlässigkeit die Korrektur dominieren (dx=50). Knoten D liegt daneben
        // (dx=20) mit normalem Vertrauen -- das Ergebnis muss trotz C's günstigerer Position praktisch
        // nur von D bestimmt werden.
        val dirC = Vec3(0.0, 0.0, 1.0) // Basis-Pixel (0,0), = Anfragepunkt
        val dirD = Vec3(0.01, 0.0, 1.0) // Basis-Pixel (10,0)
        val corrected = CorrectedProjection(
            baseline,
            controlPoints = listOf(Offset(50f, 0f) to dirC, Offset(30f, 0f) to dirD),
            groupSizes = listOf(1, 1),
            groupWeights = listOf(0.0001, 1.0),
        )

        val result = corrected.directionToPixel(dirC)

        assertNotNull(result)
        // Von Hand hergeleitet (radiusSq = 9*10²=900, C-Gewicht ~0 -> kürzt sich aus der gewichteten
        // Mittelung heraus): blendedDx = dx_D = 20 exakt, Konfidenz = D's alleiniges (nicht gedeckeltes)
        // Gewicht bei Distanz 10 zum Zentrum -> x = 20 * (1 - 100/900)² = 20 * 0,790123... ≈ 15,80.
        assertEquals(15.8025f, result!!.x, 0.05f)
        assertEquals(0f, result.y, 0.05f)
    }
}
