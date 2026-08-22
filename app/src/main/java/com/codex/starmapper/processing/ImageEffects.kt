package com.codex.starmapper.processing

import android.graphics.Bitmap

/**
 * Bildeffekte fürs Basisbild (Weichzeichnen/Graustufen/Invertieren, 0.19.0). Graustufen/Invertieren
 * laufen über einen Farbmatrix-ColorFilter beim Zeichnen (billig, kein Bitmap-Umbau nötig) — nur der
 * Weichzeichner braucht ein tatsächlich verändertes Bitmap.
 */
object ImageEffects {
    /**
     * Weichzeichner: dreifacher separierbarer Box-Blur (horizontal + vertikal je Durchlauf).
     * Drei Box-Blur-Durchläufe nähern sich einer echten Gauß-Unschärfe visuell sehr gut an
     * (etabliertes Verfahren, siehe z.B. Getreuer, "A Survey of Gaussian Convolution Algorithms") —
     * OHNE die Block-/Raster-Artefakte eines Downscale/Upscale-Tricks (der erste Versuch zeigte
     * genau solche Artefakte: Sterne wurden zu eckigen statt runden, weichen Flecken). Läuft
     * direkt auf dem Pixel-Array (Sliding-Window-Summe über eine Präfixsumme je Zeile/Spalte
     * -> Kosten pro Pixel sind UNABHÄNGIG vom Radius), synchron, auf allen API-Leveln identisch.
     */
    fun blur(source: Bitmap, radiusPx: Float): Bitmap {
        val radius = radiusPx.toInt()
        if (radius < 1) return source
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        repeat(3) {
            boxBlurPass(pixels, w, h, radius, horizontal = true)
            boxBlurPass(pixels, w, h, radius, horizontal = false)
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }

    /** Ein Box-Blur-Durchlauf über alle Zeilen (horizontal) oder Spalten (vertikal). */
    private fun boxBlurPass(pixels: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lineCount = if (horizontal) h else w
        val lineLen = if (horizontal) w else h
        val line = IntArray(lineLen)
        val windowSize = radius * 2 + 1
        val extLen = lineLen + 2 * radius
        val prefixA = IntArray(extLen + 1)
        val prefixR = IntArray(extLen + 1)
        val prefixG = IntArray(extLen + 1)
        val prefixB = IntArray(extLen + 1)

        for (line0 in 0 until lineCount) {
            // Zeile/Spalte extrahieren.
            for (i in 0 until lineLen) {
                val idx = if (horizontal) line0 * w + i else i * w + line0
                line[i] = pixels[idx]
            }
            // Präfixsumme über die um [radius] am Rand wiederholte Zeile (Edge-Replication) ->
            // Fensterinhalt für Position i = prefix[i+windowSize] - prefix[i], O(1) pro Pixel.
            for (i in 0 until extLen) {
                val srcIdx = (i - radius).coerceIn(0, lineLen - 1)
                val p = line[srcIdx]
                prefixA[i + 1] = prefixA[i] + ((p ushr 24) and 0xFF)
                prefixR[i + 1] = prefixR[i] + ((p shr 16) and 0xFF)
                prefixG[i + 1] = prefixG[i] + ((p shr 8) and 0xFF)
                prefixB[i + 1] = prefixB[i] + (p and 0xFF)
            }
            for (i in 0 until lineLen) {
                val a = (prefixA[i + windowSize] - prefixA[i]) / windowSize
                val r = (prefixR[i + windowSize] - prefixR[i]) / windowSize
                val g = (prefixG[i + windowSize] - prefixG[i]) / windowSize
                val b = (prefixB[i + windowSize] - prefixB[i]) / windowSize
                val idx = if (horizontal) line0 * w + i else i * w + line0
                pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
