package com.codex.starmapper.processing

import android.graphics.Bitmap
import android.graphics.Color
import com.codex.starmapper.domain.DetectedStar
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object StarDetector {
    fun detect(bitmap: Bitmap, sensitivity: Float = 0.55f): List<DetectedStar> {
        val tunedSensitivity = sensitivity.coerceIn(0f, 1f)
        val maxStars = (280 + tunedSensitivity * 1320).roundToInt()
        // Kandidatensuche auf einem verkleinerten Bild (schnell). 2000 statt 1400 -> weniger
        // Verschmelzen dichter Sterne. Die endgültigen Zentroide werden danach auf dem
        // VOLL-Auflösungs-Bild sub-pixel-genau nachgerechnet (refineCentroid), wie SExtractor.
        val maxSide = 2000f
        val scale = min(1f, maxSide / max(bitmap.width, bitmap.height).toFloat())
        val sampleWidth = max(1, (bitmap.width * scale).roundToInt())
        val sampleHeight = max(1, (bitmap.height * scale).roundToInt())
        val sampled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
        } else {
            bitmap
        }

        val pixels = IntArray(sampleWidth * sampleHeight)
        sampled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        val starScore = FloatArray(pixels.size)
        var sum = 0.0
        var sumSquares = 0.0

        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
            val maxChannel = max(red, max(green, blue)).toFloat()
            val minChannel = min(red, min(green, blue)).toFloat()
            val chroma = maxChannel - minChannel
            val score = max(luminance, maxChannel * 0.78f + chroma * 0.34f)
            starScore[index] = score
            sum += score
            sumSquares += score * score
        }

        val count = pixels.size.toDouble().coerceAtLeast(1.0)
        val mean = sum / count
        val variance = (sumSquares / count - mean * mean).coerceAtLeast(0.0)
        val stdDev = sqrt(variance)
        val stdMultiplier = 2.85 - tunedSensitivity * 1.55
        val additiveOffset = 15.0 - tunedSensitivity * 11.0
        val minThreshold = 60.0 - tunedSensitivity * 30.0
        val threshold = (mean + stdDev * stdMultiplier + additiveOffset).coerceIn(minThreshold, 246.0).toFloat()

        val candidates = mutableListOf<DetectedStar>()
        for (y in 3 until sampleHeight - 3) {
            for (x in 3 until sampleWidth - 3) {
                val center = starScore[y * sampleWidth + x]
                if (center < threshold || !isLocalMaximum(starScore, sampleWidth, x, y, center)) continue

                var weightedX = 0.0
                var weightedY = 0.0
                var weight = 0.0
                var area = 0
                for (yy in y - 3..y + 3) {
                    for (xx in x - 3..x + 3) {
                        val value = starScore[yy * sampleWidth + xx]
                        if (value < threshold * 0.70f) continue
                        val w = (value - threshold * 0.56f).coerceAtLeast(0f)
                        weightedX += xx * w
                        weightedY += yy * w
                        weight += w
                        area += 1
                    }
                }

                if (weight <= 0.0) continue
                val originalX = (weightedX / weight).toFloat() / scale
                val originalY = (weightedY / weight).toFloat() / scale
                val radius = (sqrt(area / PI).toFloat() / scale).coerceIn(1.2f, 12f)
                candidates += DetectedStar(originalX, originalY, radius, center)
            }
        }

        val suppressionRadius = (12f - tunedSensitivity * 7f).coerceAtLeast(4f) / scale
        val accepted = mutableListOf<DetectedStar>()
        for (candidate in candidates.sortedByDescending { it.score }) {
            if (accepted.any { it.offset.getDistance(candidate.offset) < suppressionRadius }) continue
            accepted += candidate
            if (accepted.size >= maxStars) break
        }

        if (sampled !== bitmap) sampled.recycle()

        // Sub-Pixel-Nachzentrierung auf der VOLLEN Auflösung: der Kandidat (aus dem verkleinerten
        // Bild) ist nur ~grob genau; hier wird der Schwerpunkt aus den Originalpixeln in einem
        // kleinen Fenster neu berechnet. Das ist der entscheidende Genauigkeitsschritt für den
        // Fisheye-Fit (der Fit kann nie genauer sein als die Sternposition).
        val refined = if (scale < 1f) {
            accepted.map { candidate -> refineCentroid(bitmap, candidate) ?: candidate }
        } else {
            accepted
        }
        return refined.sortedBy { it.y }
    }

    /** Verfeinert einen Sternschwerpunkt anhand der Originalpixel in einem kleinen Fenster. */
    private fun refineCentroid(bitmap: Bitmap, seed: DetectedStar): DetectedStar? {
        val win = (seed.radius * 1.5f + 5f).roundToInt().coerceIn(5, 14)
        val x0 = (seed.x.roundToInt() - win).coerceIn(0, bitmap.width - 1)
        val y0 = (seed.y.roundToInt() - win).coerceIn(0, bitmap.height - 1)
        val x1 = (seed.x.roundToInt() + win).coerceIn(0, bitmap.width - 1)
        val y1 = (seed.y.roundToInt() + win).coerceIn(0, bitmap.height - 1)
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        if (w < 3 || h < 3) return null
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, x0, y0, w, h)

        val lum = FloatArray(w * h)
        var background = Float.MAX_VALUE
        var peak = 0f
        for (i in px.indices) {
            val p = px[i]
            val l = Color.red(p) * 0.2126f + Color.green(p) * 0.7152f + Color.blue(p) * 0.0722f
            lum[i] = l
            if (l < background) background = l
            if (l > peak) peak = l
        }
        if (peak - background < 1f) return null
        val threshold = background + (peak - background) * 0.45f

        var sumX = 0.0
        var sumY = 0.0
        var sumW = 0.0
        var area = 0
        for (yy in 0 until h) {
            for (xx in 0 until w) {
                val l = lum[yy * w + xx]
                if (l < threshold) continue
                val weight = (l - background).toDouble()
                sumX += (x0 + xx) * weight
                sumY += (y0 + yy) * weight
                sumW += weight
                area += 1
            }
        }
        if (sumW <= 0.0) return null
        val radius = sqrt(area / PI).toFloat().coerceIn(1.2f, 12f)
        return DetectedStar((sumX / sumW).toFloat(), (sumY / sumW).toFloat(), radius, peak)
    }
}

private fun isLocalMaximum(values: FloatArray, width: Int, x: Int, y: Int, center: Float): Boolean {
    for (yy in y - 1..y + 1) {
        for (xx in x - 1..x + 1) {
            if (xx == x && yy == y) continue
            if (values[yy * width + xx] > center) return false
        }
    }
    return true
}

private fun androidx.compose.ui.geometry.Offset.getDistance(other: androidx.compose.ui.geometry.Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}
