package com.codex.starmapper.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.DEFAULT_DRAW_LAYER_ORDER
import com.codex.starmapper.domain.DrawLayer
import com.codex.starmapper.domain.ExportScale
import com.codex.starmapper.domain.OverlayLineStyle
import com.codex.starmapper.domain.OverlayKind
import com.codex.starmapper.domain.ReticleStyle
import com.codex.starmapper.domain.constellationImagePoints
import com.codex.starmapper.domain.constellationNameAnchor
import com.codex.starmapper.domain.trimmedLineEndpoints
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object ExportRenderer {
    fun renderToShareUri(
        context: Context,
        source: Bitmap,
        overlays: List<AnnotationOverlay>,
        scale: ExportScale,
        includeBackground: Boolean,
        includeConstellationAnchors: Boolean,
        annotationEraseMask: Bitmap? = null,
        graticule: GraticuleGeometry? = null,
        graticuleThickness: Float = 0.0012f,
        graticuleColorArgb: Long = 0xFF8FD8FFL,
        graticuleOpacity: Float = 0.7f,
        graticuleShowLabels: Boolean = true,
        milkyWay: MilkyWayGeometry? = null,
        milkyWayOpacity: Float = 0.5f,
        imageBlurIntensity: Float = 0f,
        imageGrayscale: Boolean = false,
        imageInverted: Boolean = false,
        overlayCoordScale: Float = 1f,
        layerDrawOrder: List<DrawLayer> = DEFAULT_DRAW_LAYER_ORDER,
    ): Uri {
        val output = renderBitmap(
            source, overlays, scale, includeBackground, includeConstellationAnchors, annotationEraseMask,
            graticule, graticuleThickness, graticuleColorArgb, graticuleOpacity, graticuleShowLabels,
            milkyWay, milkyWayOpacity,
            imageBlurIntensity, imageGrayscale, imageInverted,
            overlayCoordScale, layerDrawOrder,
        )
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val suffix = if (includeBackground) "bild" else "overlay"
        val file = File(dir, "sternbild_mapper_${suffix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        output.recycle()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun saveToPictures(
        context: Context,
        source: Bitmap,
        overlays: List<AnnotationOverlay>,
        scale: ExportScale,
        includeBackground: Boolean,
        includeConstellationAnchors: Boolean,
        annotationEraseMask: Bitmap? = null,
        graticule: GraticuleGeometry? = null,
        graticuleThickness: Float = 0.0012f,
        graticuleColorArgb: Long = 0xFF8FD8FFL,
        graticuleOpacity: Float = 0.7f,
        graticuleShowLabels: Boolean = true,
        milkyWay: MilkyWayGeometry? = null,
        milkyWayOpacity: Float = 0.5f,
        imageBlurIntensity: Float = 0f,
        imageGrayscale: Boolean = false,
        imageInverted: Boolean = false,
        overlayCoordScale: Float = 1f,
        layerDrawOrder: List<DrawLayer> = DEFAULT_DRAW_LAYER_ORDER,
    ): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return renderToShareUri(
                context, source, overlays, scale, includeBackground, includeConstellationAnchors, annotationEraseMask,
                graticule, graticuleThickness, graticuleColorArgb, graticuleOpacity, graticuleShowLabels,
                milkyWay, milkyWayOpacity,
                imageBlurIntensity, imageGrayscale, imageInverted,
                overlayCoordScale, layerDrawOrder,
            )
        }

        val output = renderBitmap(
            source, overlays, scale, includeBackground, includeConstellationAnchors, annotationEraseMask,
            graticule, graticuleThickness, graticuleColorArgb, graticuleOpacity, graticuleShowLabels,
            milkyWay, milkyWayOpacity,
            imageBlurIntensity, imageGrayscale, imageInverted,
            overlayCoordScale, layerDrawOrder,
        )
        val suffix = if (includeBackground) "bild" else "overlay"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "sternbild_mapper_${suffix}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Sternbild Mapper")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore konnte keinen Export-URI erzeugen.")
        resolver.openOutputStream(uri).use { stream ->
            requireNotNull(stream) { "MediaStore OutputStream ist null." }
            output.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        output.recycle()
        return uri
    }

    private fun renderBitmap(
        source: Bitmap,
        overlays: List<AnnotationOverlay>,
        scale: ExportScale,
        includeBackground: Boolean,
        includeConstellationAnchors: Boolean,
        annotationEraseMask: Bitmap? = null,
        graticule: GraticuleGeometry? = null,
        graticuleThickness: Float = 0.0012f,
        graticuleColorArgb: Long = 0xFF8FD8FFL,
        graticuleOpacity: Float = 0.7f,
        graticuleShowLabels: Boolean = true,
        milkyWay: MilkyWayGeometry? = null,
        milkyWayOpacity: Float = 0.5f,
        imageBlurIntensity: Float = 0f,
        imageGrayscale: Boolean = false,
        imageInverted: Boolean = false,
        overlayCoordScale: Float = 1f,
        layerDrawOrder: List<DrawLayer> = DEFAULT_DRAW_LAYER_ORDER,
    ): Bitmap {
        val renderScale = renderScale(source, scale)
        val width = max(1, (source.width * renderScale).toInt())
        val height = max(1, (source.height * renderScale).toInt())
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        if (includeBackground) {
            // Weichzeichnen (0.19.0) vor dem Skalieren aufs Ausgabe-Bitmap; Graustufen/Invertieren
            // als billiger ColorMatrix-Filter beim Zeichnen — 1:1 zur Editor-Vorschau.
            val blurRadiusPx = imageBlurIntensity * min(source.width, source.height) * 0.03f
            val blurredSource = if (blurRadiusPx > 0.5f) ImageEffects.blur(source, blurRadiusPx) else source
            val bgPaint = if (imageGrayscale || imageInverted) {
                Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(imageColorMatrix(imageGrayscale, imageInverted)) }
            } else {
                null
            }
            canvas.drawBitmap(blurredSource, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bgPaint)
            if (blurredSource !== source) blurredSource.recycle()
        } else {
            canvas.drawColor(Color.TRANSPARENT)
        }

        // Overlays/Netz/Radierer liegen in ANZEIGE-Bildkoordinaten. Beim Original-Export ist die Quelle
        // das voll aufgelöste Bild (overlayCoordScale = Original/Anzeige > 1) -> den Overlay-Koordinaten-
        // raum zusätzlich um diesen Faktor skalieren, damit alles 1:1 mitwächst. Die "Bildgröße" für
        // Größen/Anker/Namen ist dann die ANZEIGE-Dimension (coordW/coordH).
        val coordW = max(1, (source.width / overlayCoordScale).roundToInt())
        val coordH = max(1, (source.height / overlayCoordScale).roundToInt())
        canvas.save()
        canvas.scale(renderScale * overlayCoordScale, renderScale * overlayCoordScale)

        // Zeichenreihenfolge der 5 Schichten frei konfigurierbar (Katalog bearbeiten -> Schichten,
        // Nutzerwunsch 2026-08-20) -- 1:1 zur Editor-Vorschau (StarMapperApp.kt EditorCanvas). Bei
        // layerDrawOrder == DEFAULT_DRAW_LAYER_ORDER identisch zur vorherigen fest verdrahteten Reihenfolge
        // (Milchstraße -> Gradnetz -> Sternbilder/Objekte/Sterne).
        val groupedOverlays = OverlayGeometry.groupByDrawLayer(overlays)
        layerDrawOrder.forEach { layer ->
            when (layer) {
                DrawLayer.MilkyWay -> milkyWay?.let { drawMilkyWay(canvas, it, milkyWayOpacity, min(coordW, coordH)) }
                DrawLayer.Graticule -> graticule?.let {
                    drawGraticule(
                        canvas, it, min(coordW, coordH),
                        graticuleThickness, graticuleColorArgb, graticuleOpacity, graticuleShowLabels,
                    )
                }
                DrawLayer.Constellation, DrawLayer.Objects, DrawLayer.Star -> {
                    val bucket = groupedOverlays[layer].orEmpty()
                    if (bucket.isNotEmpty()) {
                        // Beschriftungs-Radierer: Overlays in eigenen Layer, dann die übermalten
                        // Bereiche ausstanzen (das Hintergrundbild liegt außerhalb des Layers und
                        // bleibt erhalten). WYSIWYG zum Editor.
                        val eraseLayer = if (annotationEraseMask != null) canvas.saveLayer(null, null) else -1
                        drawOverlays(canvas, bucket, includeConstellationAnchors, coordW, coordH)
                        if (annotationEraseMask != null) {
                            SolveMask.punchOut(canvas, annotationEraseMask, coordW, coordH)
                            canvas.restoreToCount(eraseLayer)
                        }
                    }
                }
            }
        }
        canvas.restore()

        return output
    }

    /** Kombinierte Graustufen-/Invertier-Farbmatrix fürs Basisbild (Export-Pendant zum Editor-ColorFilter). */
    private fun imageColorMatrix(grayscale: Boolean, inverted: Boolean): android.graphics.ColorMatrix {
        val matrix = android.graphics.ColorMatrix()
        if (inverted) {
            matrix.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        if (grayscale) {
            matrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        }
        return matrix
    }

    private fun renderScale(source: Bitmap, target: ExportScale): Float {
        val longEdge = max(source.width, source.height).toFloat()
        return when (target) {
            ExportScale.Small -> (2048f / longEdge).coerceAtMost(1f)
            ExportScale.Medium -> ((longEdge * 0.5f).coerceAtLeast(2048f) / longEdge).coerceAtMost(1f)
            ExportScale.Original -> 1f
        }
    }

    // Milchstraße (0.19.0): vorab berechnete Ringe (Bild-px, siehe MilkyWayRenderer) als weiche
    // gefüllte Flächen zeichnen — dieselbe Fill-Farbe/Alpha-Staffelung wie im Sternhimmel-Globus,
    // zusätzlich mit dem Opazitäts-Regler skaliert. 1:1 zur Editor-Vorschau.
    private fun drawMilkyWay(canvas: Canvas, milkyWay: MilkyWayGeometry, opacity: Float, imageMinDim: Int) {
        // Deckkraft-Regler ist WÖRTLICH gemeint: 100% = die dichteste Stufe voll deckend (alpha
        // 1.0) — 1:1 zur Editor-Vorschau (siehe StarMapperApp.kt milkyWay-Draw-Block).
        val alphaScale = opacity.coerceIn(0f, 1f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // Kontur deutlich von der Fläche abgesetzt (reines Weiß + höhere Deckkraft als die Fläche
        // selbst) und mit der Bildgröße skaliert, sonst bei hochauflösenden Fotos kaum sichtbar
        // (analog zur Koordinatennetz-Linienstärke).
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (imageMinDim * 0.0018f).coerceAtLeast(1.5f)
        }
        milkyWay.levels.forEach { level ->
            val baseAlpha = when (level.level) {
                1 -> 0.35f
                2 -> 0.50f
                3 -> 0.65f
                4 -> 0.80f
                else -> 1.00f
            }
            fillPaint.color = withAlpha(0xFFE8EEFFL, (baseAlpha * alphaScale).coerceIn(0f, 1f))
            val outlineAlpha = (baseAlpha + 0.25f).coerceAtMost(1f)
            outlinePaint.color = withAlpha(0xFFFFFFFFL, (outlineAlpha * alphaScale).coerceIn(0f, 1f))
            level.rings.forEach { ring ->
                val pts = ring.points
                if (pts.size < 3) return@forEach
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                path.close()
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, outlinePaint)
            }
        }
    }

    // Koordinatennetz auf die (bereits auf Bildmaßstab skalierte) Leinwand zeichnen. Strichbreite +
    // Textgröße in Bild-px -> der äußere canvas.scale(renderScale) bringt es auf die Ausgabegröße.
    // 1:1 zur Editor-Vorschau. Schatten-Alpha ∝ Deckkraft (keine dunklen, transparenten Zahlen).
    private fun drawGraticule(
        canvas: Canvas,
        graticule: GraticuleGeometry,
        imageMinDim: Int,
        thickness: Float,
        colorArgb: Long,
        opacity: Float,
        showLabels: Boolean,
    ) {
        val minDim = imageMinDim.toFloat()
        val alpha = opacity.coerceIn(0.05f, 1f)
        val alpha255 = (alpha * 255f).toInt().coerceIn(0, 255)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colorArgb.toInt()
            this.alpha = alpha255
            strokeWidth = (thickness * minDim).coerceAtLeast(0.75f)
        }
        graticule.lines.forEach { line ->
            val pts = line.points
            if (pts.size < 2) return@forEach
            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            canvas.drawPath(path, linePaint)
        }
        if (showLabels && graticule.labels.isNotEmpty()) {
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorArgb.toInt()
                this.alpha = alpha255
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = (minDim * 0.016f).coerceAtLeast(10f)
                setShadowLayer(4f, 0f, 0f, Color.argb(alpha255, 0, 0, 0))
            }
            val dx = labelPaint.textSize * 0.3f
            graticule.labels.forEach { label ->
                canvas.drawText(label.text, label.pos.x + dx, label.pos.y + labelPaint.textSize * 0.35f, labelPaint)
            }
        }
    }

    private fun drawOverlays(
        canvas: Canvas,
        overlays: List<AnnotationOverlay>,
        includeConstellationAnchors: Boolean,
        coordWidth: Int,
        coordHeight: Int,
    ) {
        // "Bildgröße" im Overlay-Koordinatenraum (= Anzeige-Auflösung, NICHT die Ausgabe-Pixel des
        // skalierten Canvas) – wichtig für Anker-/Namen-Maße beim Original-Export.
        val imageMinDim = min(coordWidth, coordHeight)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }

        overlays.forEach { overlay ->
            if (overlay.reticle != null) {
                drawReticle(canvas, overlay, shapePaint)
                if (overlay.showName && overlay.text.isNotBlank()) drawMarkerName(canvas, overlay)
                return@forEach
            }
            when (overlay.kind) {
                OverlayKind.Constellation -> {
                    linePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                    linePaint.strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth)
                    val points = overlay.constellationImagePoints()
                    // Geteilte Geometrie (Bild-px) -> identisch zur Editor-Vorschau (WYSIWYG).
                    val anchorRadius = OverlayGeometry.constellationAnchorRadius(overlay, imageMinDim.toFloat())
                    val lineTrimGap = OverlayGeometry.constellationLineTrimGap(
                        anchorRadius,
                        linePaint.strokeWidth,
                        includeConstellationAnchors,
                        overlay.anchorRadiusRatio,
                    )
                    fun drawEdge(edgePts: List<Offset>, edgeIndex: Int) {
                        if (edgePts.size < 2) return
                        when (overlay.lineStyle) {
                            OverlayLineStyle.Solid, OverlayLineStyle.Dashed -> {
                                // EINE durchgezogene Polylinie (Path) statt Einzelsegmente -> keine
                                // „Perlenkette" bei durchgezogenen gekrümmten Linien (1:1 zum Editor).
                                val path = Path()
                                path.moveTo(edgePts[0].x, edgePts[0].y)
                                for (k in 1 until edgePts.size) path.lineTo(edgePts[k].x, edgePts[k].y)
                                linePaint.strokeJoin = Paint.Join.ROUND
                                linePaint.pathEffect = if (overlay.lineStyle == OverlayLineStyle.Dashed) {
                                    DashPathEffect(floatArrayOf(linePaint.strokeWidth * 4f, linePaint.strokeWidth * 2.4f), 0f)
                                } else {
                                    null
                                }
                                canvas.drawPath(path, linePaint)
                                linePaint.pathEffect = null
                            }
                            OverlayLineStyle.Dotted -> canvas.drawDottedPolyline(edgePts, linePaint)
                            else -> StrokeRenderer.draw(
                                canvas = canvas,
                                points = edgePts,
                                baseWidth = linePaint.strokeWidth,
                                colorArgb = linePaint.color,
                                style = overlay.lineStyle,
                                seed = overlay.id * 1000L + edgeIndex,
                                taper = true,
                            )
                        }
                    }
                    val edgePolylines = overlay.edgePolylines
                    if (edgePolylines != null) {
                        edgePolylines.forEachIndexed { edgeIndex, poly ->
                            if (poly.size < 2) return@forEachIndexed
                            drawEdge(trimPolylineEnds(poly, lineTrimGap), edgeIndex)
                        }
                    } else {
                        overlay.constellation?.edges.orEmpty().forEachIndexed { edgeIndex, (a, b) ->
                            val endpoints = trimmedLineEndpoints(points[a], points[b], lineTrimGap) ?: return@forEachIndexed
                            drawEdge(listOf(endpoints.first, endpoints.second), edgeIndex)
                        }
                    }
                    if (overlay.showAnchors ?: includeConstellationAnchors) {
                        val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = withAlpha(overlay.colorArgb, overlay.opacity)
                            style = Paint.Style.STROKE
                            strokeWidth = OverlayGeometry.anchorStrokeWidth(anchorRadius)
                        }
                        points.forEach { point ->
                            canvas.drawCircle(point.x, point.y, anchorRadius, anchorPaint)
                        }
                    }
                    if (overlay.showName) {
                        val imgW = coordWidth.toFloat()
                        val imgH = coordHeight.toFloat()
                        // Anker nur aus den im Bild liegenden Sternen; off-field/gefaltete Sternbilder
                        // bekommen GAR KEINEN Namen (behebt den falsch platzierten Klumpen, wie im Editor).
                        val nameAnchor = overlay.constellationNameAnchor(imgW, imgH)
                        if (nameAnchor != null) {
                            // Beschriftung immer voll deckend – Opazität wirkt nur auf Linien/Anker.
                            textPaint.color = withAlpha(overlay.colorArgb, 1f)
                            textPaint.textAlign = Paint.Align.CENTER
                            textPaint.typeface = overlay.font.toTypeface(bold = true)
                            val nameImg = OverlayGeometry.constellationNameTextSize(overlay)
                            textPaint.textSize = nameImg
                            // Abstand zu Ankern/Linien (wie im Editor).
                            val clearImg = anchorRadius + OverlayGeometry.strokeWidth(overlay.strokeWidth) +
                                OverlayGeometry.CONSTELLATION_NAME_GAP
                            val labelX = nameAnchor.x.coerceIn(nameImg, (imgW - nameImg).coerceAtLeast(nameImg))
                            var baselineY = nameAnchor.y - clearImg
                            if (baselineY - nameImg < 0f) baselineY = nameAnchor.y + clearImg + nameImg
                            baselineY = baselineY.coerceIn(nameImg, (imgH - nameImg * 0.3f).coerceAtLeast(nameImg))
                            canvas.drawText(
                                overlay.constellation?.germanName.orEmpty(),
                                labelX,
                                baselineY,
                                textPaint,
                            )
                        }
                    }
                }
                OverlayKind.Ellipse -> {
                    // markerRing=false: ringlos. Sternmarker (Star-Ebene) -> kleiner gefüllter Punkt
                    // (sonst wären namenlose "alle bis mag"-Sterne unsichtbar); sonst nichts.
                    if (overlay.markerRing) {
                        if (overlay.filled) {
                            shapePaint.style = Paint.Style.FILL
                            shapePaint.pathEffect = null
                            shapePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                            canvas.withOverlayRotation(overlay) { drawOval(overlay.rectAroundCenter(), shapePaint) }
                            shapePaint.style = Paint.Style.STROKE
                        }
                        shapePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                        shapePaint.strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth)
                        shapePaint.pathEffect = pathEffectFor(overlay.lineStyle, shapePaint.strokeWidth)
                        if (overlay.lineStyle != OverlayLineStyle.Solid && overlay.lineStyle != OverlayLineStyle.Dashed) {
                            canvas.drawStyledPolyline(ovalPolyline(overlay), shapePaint, overlay.lineStyle, overlay.id, taper = false)
                        } else {
                            canvas.withOverlayRotation(overlay) {
                                drawOval(overlay.rectAroundCenter(), shapePaint)
                            }
                        }
                        shapePaint.pathEffect = null
                    } else if (overlay.layer == AnnotationLayer.Star && overlay.markerDot) {
                        val dotRadius = (minOf(overlay.size.width, overlay.size.height) * 0.34f).coerceAtLeast(1.5f)
                        shapePaint.style = Paint.Style.FILL
                        shapePaint.pathEffect = null
                        shapePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                        canvas.drawCircle(overlay.center.x, overlay.center.y, dotRadius, shapePaint)
                        shapePaint.style = Paint.Style.STROKE
                    }
                    if (overlay.showName && overlay.text.isNotBlank()) {
                        drawMarkerName(canvas, overlay)
                    }
                }
                OverlayKind.Rectangle -> {
                    if (overlay.filled) {
                        shapePaint.style = Paint.Style.FILL
                        shapePaint.pathEffect = null
                        shapePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                        canvas.withOverlayRotation(overlay) { drawRect(overlay.rectAroundCenter(), shapePaint) }
                        shapePaint.style = Paint.Style.STROKE
                    }
                    shapePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                    shapePaint.strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth)
                    shapePaint.pathEffect = pathEffectFor(overlay.lineStyle, shapePaint.strokeWidth)
                    if (overlay.lineStyle != OverlayLineStyle.Solid && overlay.lineStyle != OverlayLineStyle.Dashed) {
                        canvas.drawStyledPolyline(rectPolyline(overlay), shapePaint, overlay.lineStyle, overlay.id, taper = false)
                    } else {
                        canvas.withOverlayRotation(overlay) {
                            drawRect(overlay.rectAroundCenter(), shapePaint)
                        }
                    }
                    shapePaint.pathEffect = null
                    if (overlay.showName && overlay.text.isNotBlank()) {
                        drawMarkerName(canvas, overlay)
                    }
                }
                OverlayKind.Freehand -> {
                    // Segmente liegen normalisiert (lokal, unrotiert) auf dem Overlay -- Denormalisieren
                    // liefert bereits fertig gedrehte, absolute Bild-SEGMENTE (kein withOverlayRotation
                    // nötig, anders als bei Ellipse/Rechteck, die feste Canvas-Primitive zeichnen).
                    val allSegments = OverlayGeometry.denormalizedFreehandPoints(overlay)
                    // Füllung nur bei GENAU einem Segment -- spiegelt drawOverlay() im Editor 1:1
                    // (mehrere Segmente = Lücken durch den Radiergummi, keine sinnvoll geschlossene Fläche).
                    val singleSegment = allSegments.singleOrNull()?.takeIf { it.size >= 2 }
                    val canFill = overlay.filled && singleSegment != null
                    if (canFill && singleSegment != null) {
                        shapePaint.style = Paint.Style.FILL
                        shapePaint.pathEffect = null
                        shapePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                        val fillPath = Path().apply {
                            moveTo(singleSegment[0].x, singleSegment[0].y)
                            singleSegment.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        canvas.drawPath(fillPath, shapePaint)
                        shapePaint.style = Paint.Style.STROKE
                    }
                    shapePaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                    shapePaint.strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth)
                    shapePaint.pathEffect = pathEffectFor(overlay.lineStyle, shapePaint.strokeWidth)
                    // Rund statt Standard-Butt/Miter (Nutzeranforderung): eine Freihand-Linie ist kein
                    // geometrisches Rechteck/Kreis mit gewollt scharfen Ecken -- sonst wirken
                    // Strichenden/Kurven-Knicke im Export kantig, WYSIWYG-Bruch zum Editor. Nur für
                    // diesen Zweig, danach zurückgesetzt (shapePaint wird von Ellipse/Rechteck geteilt).
                    shapePaint.strokeCap = Paint.Cap.ROUND
                    shapePaint.strokeJoin = Paint.Join.ROUND
                    allSegments.forEach { points ->
                        if (points.size < 2) return@forEach
                        // Geschlossen (Start=Ende) nur bei filled -- passend zu drawOverlay() im Editor.
                        val strokePoints = if (canFill) points + points.first() else points
                        if (overlay.lineStyle != OverlayLineStyle.Solid && overlay.lineStyle != OverlayLineStyle.Dashed) {
                            canvas.drawStyledPolyline(strokePoints, shapePaint, overlay.lineStyle, overlay.id, taper = !canFill)
                        } else {
                            val strokePath = Path().apply {
                                moveTo(strokePoints[0].x, strokePoints[0].y)
                                strokePoints.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            canvas.drawPath(strokePath, shapePaint)
                        }
                    }
                    shapePaint.strokeCap = Paint.Cap.BUTT
                    shapePaint.strokeJoin = Paint.Join.MITER
                    shapePaint.pathEffect = null
                    if (overlay.showName && overlay.text.isNotBlank()) {
                        drawMarkerName(canvas, overlay)
                    }
                }
                OverlayKind.Text -> {
                    textPaint.color = withAlpha(overlay.colorArgb, overlay.opacity)
                    textPaint.textAlign = android.graphics.Paint.Align.CENTER
                    textPaint.typeface = overlay.font.toTypeface(overlay.textBold)
                    textPaint.textSize = OverlayGeometry.textOverlaySize(overlay)
                    canvas.save()
                    canvas.rotate(overlay.rotationDegrees, overlay.center.x, overlay.center.y)
                    // Mehrzeilig: Zeilen an \n, vertikal um die Mitte zentriert (wie im Editor).
                    val lines = overlay.text.split("\n")
                    val lineHeight = textPaint.fontSpacing
                    val firstBaseline = overlay.center.y - (textPaint.ascent() + textPaint.descent()) / 2f -
                        (lines.size - 1) * lineHeight / 2f
                    lines.forEachIndexed { i, line ->
                        canvas.drawText(line, overlay.center.x, firstBaseline + i * lineHeight, textPaint)
                    }
                    canvas.restore()
                }
            }
        }
    }
}

/** Zeichnet den Marker-Namen (DSO/Stern) rechts neben das Symbol – wie im Editor. */
private fun drawMarkerName(canvas: Canvas, overlay: AnnotationOverlay) {
    // Sternnamen (Star-Ebene) folgen dem Deckkraft-Regler; andere Marker-Namen bleiben voll deckend.
    val nameAlpha = if (overlay.layer == AnnotationLayer.Star) overlay.opacity else 1f
    // Schatten-Alpha an die Namens-Deckkraft koppeln, sonst wirkt der Name bei niedriger Deckkraft dunkel.
    val shadowAlpha = (nameAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
    // Muss 1:1 zu drawShapeNameLabel() im Editor (StarMapperApp.kt) passen -- sonst weicht der Export
    // vom WYSIWYG-Editor-Vorschau ab (Nutzerplatzierte Ellipse/Rechteck/Freihand-Schwänzchen +
    // eigene Namensfarbe/-Fett wären im Export sonst unsichtbar bzw. falsch dargestellt).
    val isCallout = overlay.layer == AnnotationLayer.DeepSky ||
        (
            overlay.layer == null &&
                (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand)
            )
    val textColorArgb = if (isCallout) overlay.nameColorArgb else overlay.colorArgb
    val useBold = if (overlay.layer == null) overlay.textBold else true
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(textColorArgb, nameAlpha)
        textAlign = Paint.Align.LEFT
        typeface = overlay.font.toTypeface(bold = useBold)
        textSize = OverlayGeometry.markerNameTextSize(overlay)
        setShadowLayer(6f, 0f, 0f, Color.argb(shadowAlpha, 0, 0, 0))
    }
    if (isCallout) {
        // Callout: kurze Führungslinie vom Kreisrand zum Namen (mit Lücke), Name rotiert um das Objekt.
        val layout = OverlayGeometry.markerLabelLayout(overlay)
        val cx = overlay.center.x
        val cy = overlay.center.y
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(overlay.colorArgb, nameAlpha)
            strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth) * 0.6f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(cx + layout.edge.x, cy + layout.edge.y, cx + layout.lineEnd.x, cy + layout.lineEnd.y, linePaint)
        paint.textAlign = when (layout.align) {
            OverlayGeometry.LabelAlign.Left -> Paint.Align.LEFT
            OverlayGeometry.LabelAlign.Right -> Paint.Align.RIGHT
            OverlayGeometry.LabelAlign.Center -> Paint.Align.CENTER
        }
        canvas.drawText(
            overlay.text,
            cx + layout.nameAnchor.x,
            cy + layout.nameAnchor.y + paint.textSize * 0.35f,
            paint,
        )
    } else {
        canvas.drawText(
            overlay.text,
            overlay.center.x + overlay.size.width / 2f + OverlayGeometry.MARKER_NAME_GAP,
            overlay.center.y + paint.textSize * 0.35f,
            paint,
        )
    }
}

/** „Markieren"-Fadenkreuz zeichnen (offenes Kreuz / halbes Komet-Kreuz), gedreht um die Mitte. */
private fun drawReticle(canvas: Canvas, overlay: AnnotationOverlay, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.pathEffect = null
    paint.color = withAlpha(overlay.colorArgb, overlay.opacity)
    paint.strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth)
    val cx = overlay.center.x
    val cy = overlay.center.y
    // Quadratisch -> gleiche Schenkellänge, kein Stauchen (1:1 zur Editor-Vorschau).
    val half = min(overlay.size.width, overlay.size.height) / 2f
    val hw = half
    val hh = half
    val gapX = half * 0.32f
    val gapY = half * 0.32f
    canvas.withOverlayRotation(overlay) {
        when (overlay.reticle) {
            ReticleStyle.OpenCrosshair -> {
                drawLine(cx - hw, cy, cx - gapX, cy, paint)
                drawLine(cx + gapX, cy, cx + hw, cy, paint)
                drawLine(cx, cy - hh, cx, cy - gapY, paint)
                drawLine(cx, cy + gapY, cx, cy + hh, paint)
            }
            ReticleStyle.CometMarker -> {
                drawLine(cx + gapX, cy, cx + hw, cy, paint)
                drawLine(cx, cy + gapY, cx, cy + hh, paint)
            }
            null -> {}
        }
    }
}

private fun withAlpha(argb: Long, opacity: Float): Int {
    val alpha = (((argb shr 24) and 0xFF) * opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
    val red = ((argb shr 16) and 0xFF).toInt()
    val green = ((argb shr 8) and 0xFF).toInt()
    val blue = (argb and 0xFF).toInt()
    return Color.argb(alpha, red, green, blue)
}

private fun pathEffectFor(lineStyle: OverlayLineStyle, strokeWidth: Float): DashPathEffect? = when (lineStyle) {
    OverlayLineStyle.Solid -> null
    OverlayLineStyle.Dashed -> DashPathEffect(floatArrayOf(strokeWidth * 4f, strokeWidth * 2.4f), 0f)
    OverlayLineStyle.Dotted -> null
    OverlayLineStyle.HandDrawn, OverlayLineStyle.Brush, OverlayLineStyle.Marker, OverlayLineStyle.Chalk -> null
}

private fun Canvas.drawStyledPolyline(
    points: List<Offset>,
    paint: Paint,
    lineStyle: OverlayLineStyle,
    seed: Long,
    taper: Boolean,
) {
    if (points.size < 2) return
    when (lineStyle) {
        OverlayLineStyle.Dotted -> drawDottedPolyline(points, paint)
        OverlayLineStyle.Brush, OverlayLineStyle.HandDrawn, OverlayLineStyle.Marker, OverlayLineStyle.Chalk ->
            StrokeRenderer.draw(this, points, paint.strokeWidth, paint.color, lineStyle, seed, taper)
        else -> points.zipWithNext().forEach { (start, end) ->
            drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }
}

private fun Canvas.drawDottedPolyline(points: List<Offset>, paint: Paint) {
    val radius = (paint.strokeWidth * 0.48f).coerceAtLeast(1.4f)
    val spacing = (paint.strokeWidth * 3.0f).coerceAtLeast(7f)
    val dotPaint = Paint(paint).apply {
        style = Paint.Style.FILL
        pathEffect = null
    }
    var carry = 0f
    points.zipWithNext().forEach { (start, end) ->
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = kotlin.math.hypot(dx, dy)
        if (length <= 0.001f) return@forEach
        var distance = spacing - carry
        while (distance <= length) {
            val t = distance / length
            drawCircle(start.x + dx * t, start.y + dy * t, radius, dotPaint)
            distance += spacing
        }
        carry = (length - (distance - spacing)).coerceIn(0f, spacing)
    }
}

private fun ovalPolyline(overlay: AnnotationOverlay): List<Offset> {
    val radians = overlay.rotationDegrees * PI.toFloat() / 180f
    val segments = 96
    return (0..segments).map { index ->
        val angle = index.toFloat() / segments * 2f * PI.toFloat()
        val local = Offset(cos(angle) * overlay.size.width / 2f, sin(angle) * overlay.size.height / 2f)
        overlay.center + rotate(local, radians)
    }
}

private fun rectPolyline(overlay: AnnotationOverlay): List<Offset> {
    val radians = overlay.rotationDegrees * PI.toFloat() / 180f
    val halfWidth = overlay.size.width / 2f
    val halfHeight = overlay.size.height / 2f
    val corners = listOf(
        Offset(-halfWidth, -halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(-halfWidth, halfHeight),
        Offset(-halfWidth, -halfHeight),
    )
    val points = mutableListOf<Offset>()
    corners.zipWithNext().forEach { (start, end) ->
        val steps = 24
        for (step in 0 until steps) {
            val t = step / steps.toFloat()
            val local = start + (end - start) * t
            points += overlay.center + rotate(local, radians)
        }
    }
    points += overlay.center + rotate(corners.first(), radians)
    return points
}

private fun rotate(point: Offset, radians: Float): Offset {
    val c = cos(radians)
    val s = sin(radians)
    return Offset(point.x * c - point.y * s, point.x * s + point.y * c)
}

/** Kürzt eine Polylinie an BEIDEN Enden um [gap] (Bild-px) – Lücke zum Stern/Ankerring. */
private fun trimPolylineEnds(points: List<Offset>, gap: Float): List<Offset> {
    if (gap <= 0f || points.size < 2) return points
    val fromStart = trimPolylineStart(points, gap)
    return trimPolylineStart(fromStart.reversed(), gap).reversed()
}

private fun trimPolylineStart(points: List<Offset>, gap: Float): List<Offset> {
    if (points.size < 2) return points
    var remaining = gap
    for (i in 0 until points.size - 1) {
        val seg = points[i + 1] - points[i]
        val len = seg.getDistance()
        if (len <= 0f) continue
        if (len >= remaining) {
            val newFirst = points[i] + seg * (remaining / len)
            val rest = ArrayList<Offset>(points.size - i)
            rest += newFirst
            rest.addAll(points.subList(i + 1, points.size))
            return rest
        }
        remaining -= len
    }
    return listOf(points.last())
}

private fun AnnotationOverlay.rectAroundCenter(): RectF = RectF(
    center.x - size.width / 2f,
    center.y - size.height / 2f,
    center.x + size.width / 2f,
    center.y + size.height / 2f,
)

private data class OverlayBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private fun overlayImageBounds(overlay: AnnotationOverlay): OverlayBounds {
    val points = when (overlay.kind) {
        OverlayKind.Constellation -> overlay.constellationImagePoints()
        else -> overlayImageCorners(overlay)
    }.ifEmpty { overlayImageCorners(overlay) }
    return OverlayBounds(
        left = points.minOf { it.x },
        top = points.minOf { it.y },
        right = points.maxOf { it.x },
        bottom = points.maxOf { it.y },
    )
}

private fun overlayImageCorners(overlay: AnnotationOverlay): List<Offset> {
    val radians = overlay.rotationDegrees * PI.toFloat() / 180f
    val halfWidth = overlay.size.width / 2f
    val halfHeight = overlay.size.height / 2f
    return listOf(
        Offset(-halfWidth, -halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(-halfWidth, halfHeight),
    ).map { corner -> overlay.center + rotate(corner, radians) }
}

private inline fun Canvas.withOverlayRotation(overlay: AnnotationOverlay, draw: Canvas.() -> Unit) {
    save()
    rotate(overlay.rotationDegrees, overlay.center.x, overlay.center.y)
    draw()
    restore()
}
