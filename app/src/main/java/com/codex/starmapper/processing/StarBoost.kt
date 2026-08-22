package com.codex.starmapper.processing

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Anzeige-Hilfe für die Feinjustierung: rechnet den glatten Untergrund (Lichtglocke einer Stadt,
 * Airglow, Lichtverschmutzungs-Verlauf) heraus, damit schwache Sterne auch in hellen Bildbereichen
 * als Punkte sichtbar werden. NUR für die Vorschau — Solve/Export bleiben unberührt.
 *
 * Verfahren (klassisches Hintergrund-Flatten): Hintergrund = starker Weichzeichner (per Down-/Up-Scale,
 * löscht Sterne, behält den Verlauf) -> `residual = (Bild - Hintergrund) * gain`, geklemmt. Glatte
 * Helligkeitsverläufe werden so flach (nahe Schwarz), punktförmige Sterne heben sich überall gleich ab.
 */
object StarBoost {
    /**
     * @param gain Verstärkung des Rest-Signals (Sterne) nach dem Hintergrund-Abzug.
     * @param maxSide längste Kante der Ausgabe (Anzeige-Hilfe -> klein reicht, schnell).
     * @param bgDivisor Wie stark der Hintergrund heruntergerechnet wird (größer = feinerer Hintergrund).
     */
    fun enhance(src: Bitmap, gain: Float = 3.2f, maxSide: Int = 1600, bgDivisor: Int = 22): Bitmap {
        // HARDWARE-Bitmaps sind nicht lesbar -> in lesbares ARGB_8888 kopieren.
        val readable = if (src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            src
        }
        val scale = min(1f, maxSide.toFloat() / max(readable.width, readable.height).toFloat())
        val w = max(1, (readable.width * scale).roundToInt())
        val h = max(1, (readable.height * scale).roundToInt())
        val work = Bitmap.createScaledBitmap(readable, w, h, true)
            .let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }

        // Hintergrund schätzen (glatt, OHNE Block-Artefakte): mehrstufig herunter (Sterne verschwinden)
        // und in ZWEI Stufen bilinear zurück (eine einzige Upscale-Stufe von sehr klein erzeugt sichtbare
        // Blöcke; die Zwischenstufe glättet die Interpolation deutlich).
        val midW = max(1, w / 6); val midH = max(1, h / 6)
        val bgW = max(1, w / bgDivisor); val bgH = max(1, h / bgDivisor)
        val mid = Bitmap.createScaledBitmap(work, midW, midH, true)
        val tiny = Bitmap.createScaledBitmap(mid, bgW, bgH, true)
        val midUp = Bitmap.createScaledBitmap(tiny, midW, midH, true)
        val bg = Bitmap.createScaledBitmap(midUp, w, h, true)
        mid.recycle(); tiny.recycle(); midUp.recycle()

        val srcPx = IntArray(w * h)
        work.getPixels(srcPx, 0, w, 0, 0, w, h)
        val bgPx = IntArray(w * h)
        bg.getPixels(bgPx, 0, w, 0, 0, w, h)
        bg.recycle()

        val out = IntArray(w * h)
        for (i in srcPx.indices) {
            val s = srcPx[i]
            val b = bgPx[i]
            val r = (((Color.red(s) - Color.red(b)) * gain)).roundToInt().coerceIn(0, 255)
            val g = (((Color.green(s) - Color.green(b)) * gain)).roundToInt().coerceIn(0, 255)
            val bl = (((Color.blue(s) - Color.blue(b)) * gain)).roundToInt().coerceIn(0, 255)
            out[i] = -0x1000000 or (r shl 16) or (g shl 8) or bl // opak (Alpha 0xFF)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)

        if (work !== readable) work.recycle()
        if (readable !== src) readable.recycle()
        return result
    }
}
