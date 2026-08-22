package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max

/**
 * Glatte 2D-Rest-Korrektur (Grad-3-Polynom) UEBER einer globalen Panorama-Lösung: saugt die lokale
 * Modell-Restabweichung an den Ankern auf, sodass die Lösung UEBERALL lokal genau wird (Mitte UND
 * Rand) statt nur im Mittel. Ergänzt das starre Projektionsmodell (Fisheye/Stereographic/…) um genau
 * die höhere Ordnung, die ein einzelnes Modell über die ganze Hemisphäre nicht abbilden kann
 * (Stitch-Reste, Refraktions-Reste, De-Warp-Anker-Rauschen).
 *
 * Angewandt auf den VORHERGESAGTEN Pixel: endPixel = pred + clamp(P(pred)). Die Korrektur ist auf
 * [maxShift] begrenzt, damit sie ausserhalb der Ankerwolke (Bildecken/Vordergrund) nicht davonläuft.
 */
class ResidualCorrection private constructor(
    private val cx: Double,
    private val cy: Double,
    private val invScale: Double,
    private val coeffX: DoubleArray,
    private val coeffY: DoubleArray,
    private val maxShift: Double,
) {
    /** pred (vorhergesagter Pixel) -> korrigierter Pixel. */
    fun correct(p: Offset): Offset {
        val nx = (p.x - cx) * invScale
        val ny = (p.y - cy) * invScale
        val b = basis(nx, ny)
        var dx = 0.0
        var dy = 0.0
        for (i in b.indices) {
            dx += coeffX[i] * b[i]
            dy += coeffY[i] * b[i]
        }
        dx = dx.coerceIn(-maxShift, maxShift)
        dy = dy.coerceIn(-maxShift, maxShift)
        return Offset((p.x + dx).toFloat(), (p.y + dy).toFloat())
    }

    companion object {
        private const val TERMS = 10 // 1, x, y, x^2, xy, y^2, x^3, x^2 y, x y^2, y^3

        private fun basis(x: Double, y: Double): DoubleArray = doubleArrayOf(
            1.0, x, y, x * x, x * y, y * y, x * x * x, x * x * y, x * y * y, y * y * y,
        )

        /**
         * Fittet die Rest-Korrektur per kleinster Quadrate.
         * @param samples (vorhergesagter Pixel, tatsächlicher Pixel) je Anker; Δ = tatsächlich − vorhergesagt.
         * @return Korrektur oder null (zu wenige Stützstellen / singulär).
         */
        fun fit(samples: List<Pair<Offset, Offset>>, imgW: Int, imgH: Int): ResidualCorrection? {
            if (samples.size < TERMS + 4) return null
            val cx = imgW / 2.0
            val cy = imgH / 2.0
            val scale = max(imgW, imgH) / 2.0
            if (scale <= 0.0) return null
            val invScale = 1.0 / scale
            val ata = Array(TERMS) { DoubleArray(TERMS) }
            val atbx = DoubleArray(TERMS)
            val atby = DoubleArray(TERMS)
            var maxResidual = 0.0
            for ((pred, actual) in samples) {
                val nx = (pred.x - cx) * invScale
                val ny = (pred.y - cy) * invScale
                val b = basis(nx, ny)
                val dx = (actual.x - pred.x).toDouble()
                val dy = (actual.y - pred.y).toDouble()
                maxResidual = max(maxResidual, max(abs(dx), abs(dy)))
                for (i in 0 until TERMS) {
                    atbx[i] += b[i] * dx
                    atby[i] += b[i] * dy
                    for (j in 0 until TERMS) ata[i][j] += b[i] * b[j]
                }
            }
            for (i in 0 until TERMS) ata[i][i] += 1e-6 // leichte Ridge-Regularisierung
            val coeffX = solve(copyMatrix(ata), atbx) ?: return null
            val coeffY = solve(ata, atby) ?: return null
            val maxShift = (maxResidual * 1.5).coerceIn(8.0, max(imgW, imgH) * 0.06)
            return ResidualCorrection(cx, cy, invScale, coeffX, coeffY, maxShift)
        }

        private fun copyMatrix(m: Array<DoubleArray>): Array<DoubleArray> =
            Array(m.size) { m[it].copyOf() }

        /** Gauss-Jordan mit Teilpivotisierung; zerstört [a]. null bei Singularität. */
        private fun solve(a: Array<DoubleArray>, bIn: DoubleArray): DoubleArray? {
            val n = a.size
            val b = bIn.copyOf()
            for (col in 0 until n) {
                var piv = col
                for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
                if (abs(a[piv][col]) < 1e-12) return null
                if (piv != col) {
                    val tr = a[piv]; a[piv] = a[col]; a[col] = tr
                    val tb = b[piv]; b[piv] = b[col]; b[col] = tb
                }
                val d = a[col][col]
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r][col] / d
                    if (f == 0.0) continue
                    for (c in col until n) a[r][c] -= f * a[col][c]
                    b[r] -= f * b[col]
                }
            }
            return DoubleArray(n) { b[it] / a[it][it] }
        }
    }
}
