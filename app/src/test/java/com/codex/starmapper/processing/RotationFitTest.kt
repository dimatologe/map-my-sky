package com.codex.starmapper.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

class RotationFitTest {

    private fun rotZ(a: Double) = Mat3(cos(a), -sin(a), 0.0, sin(a), cos(a), 0.0, 0.0, 0.0, 1.0)
    private fun rotX(a: Double) = Mat3(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))

    private fun camDir(thetaDeg: Double, phiDeg: Double): Vec3 {
        val t = thetaDeg * PI / 180.0
        val p = phiDeg * PI / 180.0
        return Vec3(sin(t) * cos(p), sin(t) * sin(p), cos(t))
    }

    /** Rotationswinkel zwischen zwei Rotationsmatrizen in Grad (gleiche Formel wie RotationFit intern). */
    private fun rotationErrorDegrees(a: Mat3, b: Mat3): Double {
        val r = a.transpose() * b
        val trace = r.m00 + r.m11 + r.m22
        val cosAngle = ((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosAngle))
    }

    /** 20 gleichmäßig über die Kugel verteilte Testrichtungen (analog FisheyeRefinerTest.camDir-Raster). */
    private fun sphereDirections(): List<Vec3> {
        val out = mutableListOf<Vec3>()
        for (thetaDeg in listOf(10.0, 30.0, 50.0, 70.0)) {
            for (phiStep in 0 until 5) {
                out += camDir(thetaDeg, phiStep * 72.0)
            }
        }
        return out
    }

    @Test
    fun solveRobustMatchesTrueRotationOnCleanData() {
        val rotTrue = rotZ(0.4) * rotX(0.2)
        val correspondences = sphereDirections().map { eq -> RotationFit.Correspondence(eq, rotTrue * eq) }

        val result = RotationFit.solveRobust(correspondences)

        assertTrue("solveRobust muss bei sauberen Daten ein Ergebnis liefern", result != null)
        assertEquals(0.0, rotationErrorDegrees(rotTrue, result!!.rotation), 0.01)
        assertEquals(0.0, result.rmsDegrees, 1e-6)
        assertEquals(0, result.lowWeightCount)
    }

    @Test
    fun solveRobustToleratesSingleOutlierBetterThanPlainSolve() {
        val rotTrue = rotZ(0.4) * rotX(0.2)
        val good = sphereDirections().map { eq -> RotationFit.Correspondence(eq, rotTrue * eq) }
        // Ein einzelner grob falscher Match (z.B. ein automatisch fehlgematchter Stern unter vielen
        // guten Kachel-Ankern) -- 25 Grad neben der wahren Rotation, analog zum historisch
        // verifizierten Fall (2026-07 Session: 15 Grad Fehllösung unter 12 guten Gruppen).
        val outlierEq = camDir(40.0, 36.0)
        val outlierPano = (rotZ(25.0 * PI / 180.0) * rotTrue) * outlierEq
        val withOutlier = good + RotationFit.Correspondence(outlierEq, outlierPano)

        val plain = RotationFit.solve(withOutlier)
        val robust = RotationFit.solveRobust(withOutlier)

        assertTrue("solve() liefert ein Ergebnis", plain != null)
        assertTrue("solveRobust() liefert ein Ergebnis", robust != null)
        val plainError = rotationErrorDegrees(rotTrue, plain!!)
        val robustError = rotationErrorDegrees(rotTrue, robust!!.rotation)
        assertTrue(
            "solveRobust (${"%.2f".format(robustError)} deg) muss deutlich naeher an der wahren " +
                "Rotation liegen als das einfache solve() (${"%.2f".format(plainError)} deg)",
            robustError < plainError * 0.5,
        )
        assertTrue("Restfehler von solveRobust muss klein bleiben", robustError < 1.0)
        assertTrue("der Ausreisser muss als Low-Weight erkannt werden", robust.lowWeightCount >= 1)
    }

    @Test
    fun solveRobustReturnsNullWithFewerThanTwoCorrespondences() {
        val single = listOf(RotationFit.Correspondence(Vec3(1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)))
        assertNull(RotationFit.solveRobust(single))
        assertNull(RotationFit.solveRobust(emptyList()))
    }

    @Test
    fun huberWeightMatchesSolveRobustsInlineFormula() {
        val sigma = 2.0
        val k = 1.345
        // Innerhalb der Schwelle (k*sigma = 2.69) -> volles Gewicht.
        assertEquals(1.0, RotationFit.huberWeight(2.0, sigma), 1e-9)
        assertEquals(1.0, RotationFit.huberWeight(k * sigma, sigma), 1e-9) // exakt an der Schwelle
        // Jenseits der Schwelle -> k*sigma/residual (dieselbe Formel wie solveRobusts Inline-Ternary).
        assertEquals((k * sigma) / 10.0, RotationFit.huberWeight(10.0, sigma), 1e-9)
        // Abweichender Sonderfall zu solveRobust (dort: Gewicht bleibt UNVERÄNDERT, hier: 1.0 --
        // s. Funktionskommentar an huberWeight): sigma nahe 0 bedeutet hier "alle Werte praktisch
        // identisch" -> volles Vertrauen für jeden Wert, unabhängig vom Residuum.
        assertEquals(1.0, RotationFit.huberWeight(1000.0, 0.0), 1e-9)
    }
}
