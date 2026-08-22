package com.codex.starmapper.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.OverlayLineStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Gemeinsamer Render-Kern für die „künstlerischen" Strich-Stile (Pinsel/Tinte, Marker, Kreide,
 * Hand). EINE Implementierung für Editor UND Export → die Vorschau ist deckungsgleich mit dem
 * Export (WYSIWYG). Der Editor übergibt Bildschirm-Punkte + skalierte Breite, der Export Bild-
 * Punkte. Amplitude/Segmentlänge hängen nur an der Strichbreite, der Seed ist invariant
 * (overlay.id) → das Zittern ist in beiden identisch (nur uniform skaliert).
 *
 * Solid/Dashed/Dotted bleiben in den Aufrufern (Compose-PathEffect bzw. drawDottedPolyline).
 */
object StrokeRenderer {

    /**
     * @param points Mittellinie (Bildschirm- oder Bild-Koordinaten).
     * @param baseWidth Strichbreite in derselben Einheit wie [points].
     * @param colorArgb Farbe inkl. Deckkraft (ARGB-Int).
     * @param seed invarianter Seed (z. B. overlay.id) für reproduzierbares Zittern.
     * @param taper offene Linie mit Auslauf (true, Sternbildkante) vs. geschlossene Form (false).
     */
    fun draw(
        canvas: Canvas,
        points: List<Offset>,
        baseWidth: Float,
        colorArgb: Int,
        style: OverlayLineStyle,
        seed: Long,
        taper: Boolean,
    ) {
        if (points.size < 2 || baseWidth <= 0f) return
        when (style) {
            OverlayLineStyle.Brush -> {
                val center = StrokeGeometry.wobble(
                    StrokeGeometry.densify(points, segmentLength(baseWidth)),
                    baseWidth * 0.13f,
                    seed,
                )
                if (taper) fillTapered(canvas, center, baseWidth, colorArgb)
                else strokePath(canvas, center, baseWidth, colorArgb)
            }
            OverlayLineStyle.HandDrawn -> {
                val center = StrokeGeometry.wobble(
                    StrokeGeometry.densify(points, segmentLength(baseWidth)),
                    baseWidth * 0.32f,
                    seed,
                )
                strokePath(canvas, center, baseWidth * 0.92f, colorArgb)
            }
            OverlayLineStyle.Marker -> {
                // Breit + halbtransparent, darüber ein etwas dunklerer schmaler Kern.
                strokePath(canvas, points, baseWidth * 1.7f, withAlphaFactor(colorArgb, 0.42f))
                strokePath(canvas, points, baseWidth * 0.55f, withAlphaFactor(colorArgb, 0.5f))
            }
            OverlayLineStyle.Chalk -> {
                // Dichtes Stempeln (kleiner Abstand) -> Stempel überlappen -> deutlich deckender.
                val center = StrokeGeometry.densify(points, (baseWidth * 0.30f).coerceAtLeast(2f))
                drawChalk(canvas, center, baseWidth, colorArgb, seed, taper)
            }
            else -> strokePath(canvas, points, baseWidth, colorArgb)
        }
    }

    private fun segmentLength(baseWidth: Float): Float = (baseWidth * 1.4f).coerceAtLeast(3f)

    private fun fillTapered(canvas: Canvas, center: List<Offset>, baseWidth: Float, colorArgb: Int) {
        val outline = StrokeGeometry.taperedOutline(center, baseWidth)
        if (outline.size < 3) {
            strokePath(canvas, center, baseWidth, colorArgb)
            return
        }
        val path = Path()
        outline.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
    }

    private fun strokePath(canvas: Canvas, points: List<Offset>, width: Float, colorArgb: Int) {
        if (points.size < 2) return
        val path = Path()
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            style = Paint.Style.STROKE
            strokeWidth = width.coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, paint)
    }

    private fun drawChalk(
        canvas: Canvas,
        center: List<Offset>,
        baseWidth: Float,
        colorArgb: Int,
        seed: Long,
        taper: Boolean,
    ) {
        val tip = grainTip
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = Matrix()
        val n = center.size
        val rgb = colorArgb and 0x00FFFFFF
        val baseAlpha = ((colorArgb ushr 24) and 0xFF) / 255f
        for (i in center.indices) {
            val t = if (n > 1) i.toFloat() / (n - 1) else 0.5f
            val taperFactor = if (taper) 0.32f + 0.68f * sin(PI.toFloat() * t).coerceIn(0f, 1f) else 1f
            val r = (baseWidth * 0.62f * taperFactor).coerceAtLeast(1f)
            val jitterX = StrokeGeometry.hashUnit(seed, i * 2) * baseWidth * 0.22f
            val jitterY = StrokeGeometry.hashUnit(seed, i * 2 + 1) * baseWidth * 0.22f
            // Deutlich höhere Deckkraft als zuvor (Kreide war kaum sichtbar); skaliert weiter
            // über die Overlay-Opazität (baseAlpha = Opazitätsregler).
            val grainAlpha = (0.78f + 0.22f * abs(StrokeGeometry.hashUnit(seed, i * 3 + 7))) * baseAlpha
            val scale = (2f * r) / tip.width
            matrix.reset()
            matrix.postScale(scale, scale)
            matrix.postTranslate(center[i].x + jitterX - r, center[i].y + jitterY - r)
            paint.colorFilter = PorterDuffColorFilter((0xFF shl 24) or rgb, PorterDuff.Mode.SRC_IN)
            paint.alpha = (grainAlpha * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(tip, matrix, paint)
        }
    }

    private fun withAlphaFactor(argb: Int, factor: Float): Int {
        val alpha = (((argb ushr 24) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    /** Weiche Korn-Pinselspitze (weiß, variable Deckkraft) – einmal erzeugt, per Tint eingefärbt. */
    private val grainTip: Bitmap by lazy { buildGrainTip(64) }

    private fun buildGrainTip(size: Int): Bitmap {
        val pixels = IntArray(size * size)
        val random = java.util.Random(20260615L)
        val center = (size - 1) / 2f
        val maxRadius = size / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - center) / maxRadius
                val dy = (y - center) / maxRadius
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                // Weicher Rand, aber dichter Kern (linearer Falloff statt quadriert) + kräftigeres
                // Korn -> die Pinselspitze trägt mehr Deckkraft.
                val falloff = (1f - dist).coerceIn(0f, 1f)
                val grain = 0.7f + 0.3f * random.nextFloat()
                val alpha = if (dist > 1f) 0 else (falloff * grain * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * size + x] = (alpha shl 24) or 0x00FFFFFF
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
