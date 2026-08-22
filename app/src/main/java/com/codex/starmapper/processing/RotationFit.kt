package com.codex.starmapper.processing

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Löst das Wahba-Problem: finde die Rotation R, die Referenzvektoren (äquatorial, RA/Dec)
 * möglichst gut auf Beobachtungsvektoren (Bild-Kugelsystem) abbildet — R·eq ≈ pano.
 * Verfahren: Davenport-q-Methode (Eigenvektor der 4×4-K-Matrix zum größten Eigenwert),
 * gelöst mit zyklischem Jacobi-Eigensolver. Keine externe Mathe-Bibliothek.
 */
object RotationFit {

    data class Correspondence(val equatorial: Vec3, val pano: Vec3, val weight: Double = 1.0)

    /** Liefert R mit R·equatorial ≈ pano, oder null wenn < 2 brauchbare Paare. */
    fun solve(correspondences: List<Correspondence>): Mat3? {
        val pairs = correspondences.filter { it.weight > 0.0 }
        if (pairs.size < 2) return null

        // Attitude-Profilmatrix B = Σ w · pano · equatorialᵀ
        var b00 = 0.0; var b01 = 0.0; var b02 = 0.0
        var b10 = 0.0; var b11 = 0.0; var b12 = 0.0
        var b20 = 0.0; var b21 = 0.0; var b22 = 0.0
        for (c in pairs) {
            val o = c.pano.normalized()
            val r = c.equatorial.normalized()
            val w = c.weight
            b00 += w * o.x * r.x; b01 += w * o.x * r.y; b02 += w * o.x * r.z
            b10 += w * o.y * r.x; b11 += w * o.y * r.y; b12 += w * o.y * r.z
            b20 += w * o.z * r.x; b21 += w * o.z * r.y; b22 += w * o.z * r.z
        }
        val sigma = b00 + b11 + b22
        // z = [B23-B32, B31-B13, B12-B21] (1-indexiert)
        val z0 = b12 - b21
        val z1 = b20 - b02
        val z2 = b01 - b10
        // S = B + Bᵀ
        val s00 = 2 * b00; val s11 = 2 * b11; val s22 = 2 * b22
        val s01 = b01 + b10; val s02 = b02 + b20; val s12 = b12 + b21

        // K = [[S - σI, z],[zᵀ, σ]]  (symmetrisch, 4×4)
        val k = Array(4) { DoubleArray(4) }
        k[0][0] = s00 - sigma; k[0][1] = s01; k[0][2] = s02; k[0][3] = z0
        k[1][0] = s01; k[1][1] = s11 - sigma; k[1][2] = s12; k[1][3] = z1
        k[2][0] = s02; k[2][1] = s12; k[2][2] = s22 - sigma; k[2][3] = z2
        k[3][0] = z0; k[3][1] = z1; k[3][2] = z2; k[3][3] = sigma

        val (values, vectors) = jacobiEigen(k)
        // Eigenvektor zum größten Eigenwert = optimales Quaternion (Vektor 0..2, Skalar 3)
        var maxIdx = 0
        for (i in 1 until 4) if (values[i] > values[maxIdx]) maxIdx = i
        val qx = vectors[0][maxIdx]
        val qy = vectors[1][maxIdx]
        val qz = vectors[2][maxIdx]
        val qw = vectors[3][maxIdx]
        val n = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
        if (n < 1e-12) return null
        // Historisch bereits einmal gefunden + behoben (2026-07-19, Commit 1d30a5f), beim Rueckbau
        // der Tiny-Sky-Architektur (2026-07-20) versehentlich mit zurueckgerollt, obwohl der Fund
        // selbst unabhaengig war: das Davenport-q-Quaternion beschreibt in der Raumfahrt-Attitude-
        // Konvention eine PASSIVE Drehung (Referenz- in Beobachtungs-KOORDINATEN); quaternionToMatrix()
        // setzt dagegen die aktive Hamilton-Konvention um. Ohne Transponieren liefert solve() exakt R^T
        // statt R (Fehlwinkel = 2x wahrer Rotationswinkel). Numerisch erneut bestaetigt (Scratchpad
        // dieser Sitzung, RotationFitTest.solveRobustMatchesTrueRotationOnCleanData): ohne dieses
        // Transponieren weicht selbst das rauschfreie synthetische Testergebnis um ueber 50 Grad von
        // der wahren Rotation ab. Bislang unbemerkt im normalen Mehrkachel-Fit, weil solve() dort nur
        // als LM-Startwert dient (initRotationCyl/initRotationAzimuthal/initRotation) und die
        // nachfolgende 80-Runden-Verfeinerung den falschen Start selbst wieder einfing -- fuer
        // solveRobust() (das rotation direkt, ohne nachgeschalteten LM-Fit, fuer die IRLS-Gewichtung
        // weiterverwendet) ist die korrekte Rotation dagegen zwingend.
        return quaternionToMatrix(qx / n, qy / n, qz / n, qw / n).transpose()
    }

    data class RobustResult(
        val rotation: Mat3,
        val rmsDegrees: Double,
        val maxDegrees: Double,
        val meanWeight: Double,
        // Gewichteter RMS: spiegelt wider, wie gut die tatsächlich VERWENDETE Rotation zur
        // (herunter-)gewichteten Mehrheit passt. rmsDegrees dagegen zählt auch stark heruntergewichtete
        // Einzel-Ausreißer (z.B. eine ganze falsch gelöste Kachel) mit vollem Quadrat mit -> kann bei
        // nur 1-2 schlechten Kacheln unter vielen guten irreführend hoch wirken.
        val weightedRmsDegrees: Double,
        val lowWeightCount: Int,
    )

    /**
     * Wie [solve], aber iterativ neu gewichtet (IRLS mit Huber-Verlust) statt eines einzelnen
     * Gleichgewichts-Fits: Korrespondenzen mit ungewöhnlich großem Restfehler (z.B. ein falsch
     * gematchter Stern unter vielen guten Ankern derselben Kachel) werden schrittweise
     * heruntergewichtet statt mit vollem Gewicht die gesamte Rotation zu verzerren. Ursprünglich für
     * den Tiny-Sky-Refit gebaut (2026-07-19), beim Rückbau jener Architektur (2026-07-20) mangels
     * Aufrufer entfernt -- die Mathematik selbst war nie die Ursache des damaligen Problems (das lag
     * an einer grundsätzlich anderen Frage, ob überhaupt neu gefittet werden soll). Hier für den
     * NORMALEN Mehrkachel-Fit (calibratePanorama/fitCylindrical/fitAzimuthal/initRotation)
     * wiederhergestellt: bei automatisch erkannten .corr-Matches über viele unabhängige Kacheln
     * (Nutzerfall 2026-07-31: 180 Anker über 20 Kacheln, jede Kachel einzeln 0,7-4,8px RMS, aber ein
     * globaler Fit-RMS von 40,9px) ist ein einzelner Ausreißer unter vielen guten Matches plausibler
     * als ein systematischer Modellfehler.
     */
    fun solveRobust(correspondences: List<Correspondence>, maxIterations: Int = 8): RobustResult? {
        val huberK = 1.345 // Standard-Huber-Konstante (~95% Effizienz bei Normalverteilung).
        var current = correspondences
        var rotation = solve(current) ?: return null
        var round = 0
        while (round < maxIterations) {
            val residuals = current.map { angularErrorDegrees(rotation, it) }
            val sigma = robustSigmaDegrees(residuals)
            val reweighted = if (sigma < 1e-9) {
                current
            } else {
                current.mapIndexed { i, c ->
                    val e = residuals[i]
                    val w = if (e <= huberK * sigma) 1.0 else (huberK * sigma / e)
                    c.copy(weight = w)
                }
            }
            val refit = solve(reweighted) ?: break
            val moved = rotationAngleDegrees(rotation, refit)
            rotation = refit
            current = reweighted
            round++
            if (moved < 0.01) break // Gewichte/Rotation stabil -> konvergiert.
        }
        val finalResiduals = current.map { angularErrorDegrees(rotation, it) }
        val rms = sqrt(finalResiduals.map { it * it }.average())
        val maxErr = finalResiduals.maxOrNull() ?: 0.0
        val meanWeight = current.map { it.weight }.average()
        val weightSum = current.sumOf { it.weight }
        val weightedRms = if (weightSum > 1e-9) {
            sqrt(current.indices.sumOf { current[it].weight * finalResiduals[it] * finalResiduals[it] } / weightSum)
        } else {
            rms
        }
        val lowWeightCount = current.count { it.weight < 0.3 }
        return RobustResult(rotation, rms, maxErr, meanWeight, weightedRms, lowWeightCount)
    }

    private fun angularErrorDegrees(rotation: Mat3, c: Correspondence): Double {
        val predicted = (rotation * c.equatorial.normalized()).normalized()
        val observed = c.pano.normalized()
        val cosAngle = predicted.dot(observed).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosAngle))
    }

    // internal (statt private): TileConsistency.tileReliabilityWeights nutzt dieselbe
    // Median/robuste-Streuung-Mathematik für Kachel-Zuverlässigkeit statt Rotations-Korrespondenzen
    // -- die Formel selbst ist einheitenunabhängig (funktioniert für Grad genauso wie für Bild-px).
    internal fun medianOf(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** Robuste Streuungsschätzung (1.4826x Median-Abweichung vom Median ~ Sigma bei Normalverteilung). */
    internal fun robustSigmaDegrees(residualsDegrees: List<Double>): Double {
        val median = medianOf(residualsDegrees)
        val mad = medianOf(residualsDegrees.map { abs(it - median) })
        return 1.4826 * mad
    }

    /**
     * Huber-Gewicht für EINEN Restfehler gegen die robuste Streuung [sigma] (s. [robustSigmaDegrees]) --
     * aus [solveRobust]s Inline-Formel herausgelöst, damit [TileConsistency.tileReliabilityWeights]
     * dieselbe Mathematik wiederverwendet statt sie erneut zu schreiben. EIN bewusster Unterschied zu
     * [solveRobust]s eigenem `sigma < 1e-9`-Sonderfall: dort bleibt das VORHERIGE Gewicht unverändert
     * (Teil der iterativen Neugewichtung, "nichts zu tun bei perfekter Übereinstimmung"), hier gibt es
     * kein Vorher-Gewicht, das erhalten werden könnte -- ein `sigma` nahe 0 bedeutet hier "alle Werte
     * praktisch identisch", also volles Vertrauen (1.0) für jeden Wert. [solveRobust] bleibt deshalb
     * bewusst bei seiner eigenen Inline-Formel statt auf diese Funktion umgestellt zu werden.
     */
    internal fun huberWeight(residual: Double, sigma: Double, k: Double = 1.345): Double =
        if (sigma < 1e-9 || residual <= k * sigma) 1.0 else k * sigma / residual

    /** Winkel zwischen zwei Rotationen (Rotationswinkel von aᵀ·b). */
    private fun rotationAngleDegrees(a: Mat3, b: Mat3): Double {
        val r = a.transpose() * b
        val trace = r.m00 + r.m11 + r.m22
        val cosAngle = ((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosAngle))
    }

    /** Quaternion (x,y,z,w) -> Rotationsmatrix (ref -> obs). */
    private fun quaternionToMatrix(x: Double, y: Double, z: Double, w: Double): Mat3 = Mat3(
        1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
        2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
        2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y),
    )

    /** Zyklischer Jacobi-Eigensolver für symmetrische n×n-Matrizen. Gibt (Eigenwerte, Eigenvektoren als Spalten). */
    private fun jacobiEigen(input: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val n = input.size
        val a = Array(n) { input[it].copyOf() }
        val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
        repeat(100) {
            // größtes Off-Diagonal-Element finden
            var p = 0; var q = 1; var max = 0.0
            for (i in 0 until n) for (j in i + 1 until n) {
                if (abs(a[i][j]) > max) { max = abs(a[i][j]); p = i; q = j }
            }
            if (max < 1e-14) return@repeat
            val app = a[p][p]; val aqq = a[q][q]; val apq = a[p][q]
            val phi = 0.5 * kotlin.math.atan2(2 * apq, aqq - app)
            val c = kotlin.math.cos(phi); val s = kotlin.math.sin(phi)
            for (i in 0 until n) {
                val aip = a[i][p]; val aiq = a[i][q]
                a[i][p] = c * aip - s * aiq
                a[i][q] = s * aip + c * aiq
            }
            for (i in 0 until n) {
                val api = a[p][i]; val aqi = a[q][i]
                a[p][i] = c * api - s * aqi
                a[q][i] = s * api + c * aqi
            }
            for (i in 0 until n) {
                val vip = v[i][p]; val viq = v[i][q]
                v[i][p] = c * vip - s * viq
                v[i][q] = s * vip + c * viq
            }
        }
        return DoubleArray(n) { a[it][it] } to v
    }
}
