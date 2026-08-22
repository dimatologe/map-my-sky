package com.codex.starmapper.processing

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset

/**
 * Vordergrund-Maske für das Plate-Solving. Die Maske liegt in reduzierter Auflösung
 * (1/[SCALE] des Anzeigebitmaps); beim Anwenden wird sie bilinear hochskaliert, wodurch
 * die Ränder automatisch weich auslaufen. Maskierte Pixel werden geschwärzt, bevor das
 * Bild an einen Solver geht - Anzeige und Overlays nutzen weiterhin das Original.
 */
object SolveMask {
    const val SCALE = 4
    private const val PAINT_COLOR = 0xFFFF5252.toInt()
    private const val MASK_ALPHA_THRESHOLD = 32 // ~12.5% von 255; ignoriert reinen Federrand-Saum.

    fun create(imageWidth: Int, imageHeight: Int): Bitmap = Bitmap.createBitmap(
        (imageWidth / SCALE).coerceAtLeast(1),
        (imageHeight / SCALE).coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )

    /**
     * Ist der Bildpunkt [imagePoint] (Vollbild-Pixelkoordinaten) innerhalb der maskierten
     * (Vordergrund-)Fläche? [mask] liegt in 1/[SCALE]-Auflösung — die Koordinate wird entsprechend
     * herunterskaliert und auf die tatsächliche Maskengröße geklemmt (Rundung an den Rändern).
     * Alpha-Schwelle statt reinem >0, damit der weiche Federrand (BlurMaskFilter, s. [paint]) einen
     * Stern nicht schon bei einem kaum wahrnehmbaren Alpha-Hauch ausschließt.
     */
    fun isMasked(mask: Bitmap, imagePoint: Offset): Boolean {
        val mx = (imagePoint.x / SCALE).toInt().coerceIn(0, mask.width - 1)
        val my = (imagePoint.y / SCALE).toInt().coerceIn(0, mask.height - 1)
        return (mask.getPixel(mx, my) ushr 24) >= MASK_ALPHA_THRESHOLD
    }

    /**
     * Malt einen Pinselpunkt in die Maske. [hardness] 1 = harter (scharfer) Rand, 0 = sehr weicher
     * (gefederter) Rand: ein BlurMaskFilter weicht die Kreiskante proportional zum Radius auf.
     */
    fun paint(mask: Bitmap, imagePoint: Offset, radiusImagePx: Float, erase: Boolean, hardness: Float = 1f) {
        val canvas = Canvas(mask)
        val r = (radiusImagePx / SCALE).coerceAtLeast(1f)
        val (rCore, feather) = brushCore(r, hardness)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (erase) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                color = PAINT_COLOR
            }
            if (feather > 0.4f) maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(imagePoint.x / SCALE, imagePoint.y / SCALE, rCore, paint)
    }

    /**
     * Zerlegt den Pinselradius [r] in soliden Kern + weichen Auslauf, sodass die ÄUSSERE Kante IMMER
     * ≈ [r] bleibt – unabhängig von der Härte. Vorher wuchs der NORMAL-BlurMaskFilter nach außen, sodass
     * „weich" den Pinsel deutlich vergrößerte. Jetzt: Kern = 30%–100% von r (härter = größerer Kern),
     * feather = r − Kern → Kern + feather = r.
     */
    private fun brushCore(r: Float, hardness: Float): Pair<Float, Float> {
        val core = 0.30f + 0.70f * hardness.coerceIn(0f, 1f)
        val rCore = (r * core).coerceAtLeast(0.5f)
        val feather = (r - rCore).coerceAtLeast(0f)
        return rCore to feather
    }

    /**
     * Malt einen durchgehenden Strich von [from] nach [to], indem dicht entlang der Strecke gestempelt
     * wird (Abstand ≤ r/2). Ohne das entstehen bei schneller Fingerbewegung nur einzelne Punkte statt
     * einer Linie (Drag-Events kommen zu selten). [from]/[to] in Bild-px.
     */
    fun paintLine(mask: Bitmap, from: Offset, to: Offset, radiusImagePx: Float, erase: Boolean, hardness: Float = 1f) {
        val canvas = Canvas(mask)
        val r = (radiusImagePx / SCALE).coerceAtLeast(1f)
        val (rCore, feather) = brushCore(r, hardness)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (erase) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                color = PAINT_COLOR
            }
            if (feather > 0.4f) maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
        }
        val fx = from.x / SCALE; val fy = from.y / SCALE
        val tx = to.x / SCALE; val ty = to.y / SCALE
        val dist = kotlin.math.hypot(tx - fx, ty - fy)
        // Stempel-Abstand an der ÄUSSEREN Kante (r) ausrichten, damit die Linie lückenlos bleibt.
        val step = (r / 2f).coerceAtLeast(1f)
        val n = (dist / step).toInt().coerceAtLeast(1)
        for (i in 0..n) {
            val t = i.toFloat() / n
            canvas.drawCircle(fx + (tx - fx) * t, fy + (ty - fy) * t, rCore, paint)
        }
    }

    /** Erzeugt eine Kopie des Quellbildes, in der maskierte Bereiche schwarz sind. */
    fun applyTo(source: Bitmap, mask: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(mask, null, Rect(0, 0, source.width, source.height), paint)
        return result
    }

    /**
     * Stanzt die maskierten Bereiche aus dem aktuellen (eigenen) Layer: übermalte Overlay-Pixel
     * werden transparent (DST_OUT), das darunterliegende Bild scheint durch. Muss auf einem
     * separaten saveLayer aufgerufen werden, der NUR die Overlays enthält (sonst würde das
     * Hintergrundbild mitgelöscht). Wiederverwendet von Editor (Compose) und Export.
     */
    fun punchOut(canvas: Canvas, mask: Bitmap, width: Int, height: Int) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        canvas.drawBitmap(mask, null, Rect(0, 0, width, height), paint)
    }
}
