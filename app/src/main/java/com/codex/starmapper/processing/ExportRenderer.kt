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
import androidx.exifinterface.media.ExifInterface
import com.codex.starmapper.diagnostics.AppDiagnostics
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.DEFAULT_DRAW_LAYER_ORDER
import com.codex.starmapper.domain.DrawLayer
import com.codex.starmapper.domain.ExportImageFormat
import com.codex.starmapper.domain.ExportProjectionMode
import com.codex.starmapper.domain.ExportScale
import com.codex.starmapper.domain.OverlayLineStyle
import com.codex.starmapper.domain.OverlayKind
import com.codex.starmapper.domain.ReticleStyle
import com.codex.starmapper.domain.constellationImagePoints
import com.codex.starmapper.domain.constellationNameAnchor
import com.codex.starmapper.domain.localizedDisplayName
import com.codex.starmapper.domain.trimmedLineEndpoints
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Zusatzkontext NUR für die Export-Diagnose (Nutzer-Auftrag 2026-09-02, "Originalexport"-Audit) -- rein
 * additiv, betrifft nichts am eigentlichen Render-/Kompressionsverhalten. `null` (Default an beiden
 * Aufrufstellen) unterdrückt die neue `export_pixel_source`-Diagnosezeile komplett, z.B. für
 * `onExportSolveCrop`, das kein Original-vs-Working-Bitmap-Unterscheidungsbedürfnis hat.
 */
data class ExportSourceDiagnostics(
    val originalWidth: Int,
    val originalHeight: Int,
    val workingWidth: Int,
    val workingHeight: Int,
    // "ORIGINAL_FILE", wenn source aus einem frischen Volldekodierung der Bilddatei stammt (nur bei
    // ExportScale.Original versucht), sonst "WORKING_BITMAP" (das bereits geladene, ggf. für die Anzeige
    // herunterskalierte Editor-Bitmap -- bei Small/Medium immer, bei Original nur als Rückfall, falls die
    // Volldekodierung fehlschlug, s. decodeOriginalBitmap-KDoc in StarMapperApp.kt).
    val backgroundSource: String,
    val originalFileBytes: Long?,
)

object ExportRenderer {
    // JPEG-Kompressionsqualität für den "mit Hintergrund"-Export (Nutzer-Einstellung Jpeg) bei Small/
    // Medium-Skalierung. 95 statt 100: bei JPEG bringt 100 dort kaum sichtbaren Zusatznutzen, aber oft
    // spürbar größere Dateien -- etablierter Kompromiss ("visuell verlustfrei") für diese kleineren
    // Vorschau-/Teilen-Größen. Für ExportScale.Original gilt das NICHT (s. qualityFor) -- dort soll
    // laut Nutzer-Auftrag 2026-09-02 die höchstmögliche Qualität verwendet werden (kein zusätzlicher
    // Kompressions-Kompromiss auf Original-Pixeln, nur die ohnehin unvermeidliche JPEG-Neukodierung
    // selbst, s. Kommentar an writeGPanoXmpMetadata/Bericht "Originalexport").
    private const val JPEG_QUALITY_SCALED = 95
    private const val JPEG_QUALITY_ORIGINAL = 100

    // "nur Overlay" (includeBackground=false) bleibt UNABHÄNGIG von der Nutzer-Einstellung immer Png --
    // JPEG kennt keine Transparenz, ein transparentes Overlay-PNG würde als JPEG seinen Zweck verlieren.
    // Öffentlich (nicht private): der Aufrufer (StarMapperApp.kt) braucht denselben Wert, um beim Teilen
    // (Share-Intent) den korrekten MIME-Typ zu setzen -- eine zweite, duplizierte Formel könnte auseinanderlaufen.
    fun effectiveFormat(imageFormat: ExportImageFormat, includeBackground: Boolean): ExportImageFormat =
        if (includeBackground) imageFormat else ExportImageFormat.Png

    private fun compressFormatFor(format: ExportImageFormat): Bitmap.CompressFormat = when (format) {
        ExportImageFormat.Png -> Bitmap.CompressFormat.PNG
        ExportImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
    }

    private fun qualityFor(format: ExportImageFormat, scale: ExportScale): Int = when (format) {
        ExportImageFormat.Png -> 100 // von Bitmap.compress für PNG ohnehin ignoriert (verlustfrei).
        ExportImageFormat.Jpeg -> if (scale == ExportScale.Original) JPEG_QUALITY_ORIGINAL else JPEG_QUALITY_SCALED
    }

    private fun extensionFor(format: ExportImageFormat): String = when (format) {
        ExportImageFormat.Png -> "png"
        ExportImageFormat.Jpeg -> "jpg"
    }

    // Öffentlich, s. effectiveFormat-Kommentar -- derselbe Grund (StarMapperApp.kt braucht den MIME-Typ
    // fürs Teilen).
    fun mimeTypeFor(format: ExportImageFormat): String = when (format) {
        ExportImageFormat.Png -> "image/png"
        ExportImageFormat.Jpeg -> "image/jpeg"
    }

    /**
     * XMP-Paket nach dem Google-"Photo Sphere"-Standard (GPano-Namensraum) -- macht externe Galerie-Apps
     * (z.B. Samsung, Google Fotos) auf ein volles, unbeschnittenes äquirektangulares 360°-Panorama
     * aufmerksam (dieselbe Bildgröße für Full-/CroppedArea, da der Export nie beschnitten ist). Nur für
     * JPEG einbettbar (s. writeGPanoXmpMetadata) -- PNG-Einbettung ist von Betrachter-Apps praktisch nicht
     * zuverlässig unterstützt.
     */
    private fun buildGPanoXmpPacket(widthPx: Int, heightPx: Int): String {
        // BOM (U+FEFF) im begin-Attribut ist Teil des XMP-Pakethülle-Standards (dient Lesern zur
        // Kodierungserkennung) -- über den Zahlenwert erzeugt statt eines literalen Zeichens im
        // Quelltext, um jedes Risiko einer stillen Beschädigung des unsichtbaren Zeichens auszuschließen.
        val bom = 0xFEFF.toChar()
        return "<?xpacket begin=\"$bom\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n" +
            " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
            "  <rdf:Description rdf:about=\"\"\n" +
            "    xmlns:GPano=\"http://ns.google.com/photos/1.0/panorama/\"\n" +
            "    GPano:UsePanoramaViewer=\"True\"\n" +
            "    GPano:ProjectionType=\"equirectangular\"\n" +
            "    GPano:FullPanoWidthPixels=\"$widthPx\"\n" +
            "    GPano:FullPanoHeightPixels=\"$heightPx\"\n" +
            "    GPano:CroppedAreaImageWidthPixels=\"$widthPx\"\n" +
            "    GPano:CroppedAreaImageHeightPixels=\"$heightPx\"\n" +
            "    GPano:CroppedAreaLeftPixels=\"0\"\n" +
            "    GPano:CroppedAreaTopPixels=\"0\"/>\n" +
            " </rdf:RDF>\n" +
            "</x:xmpmeta>\n" +
            "<?xpacket end=\"w\"?>"
    }

    /** XMP-Schreiben ist eine sekundäre Zusatz-Eigenschaft -- ein Fehlschlag hier darf den Export selbst
     *  (bereits erfolgreich geschrieben) nicht ungültig machen, daher `runCatching` mit Diagnose-Log statt
     *  Exception nach außen. */
    private fun writeGPanoXmpMetadata(file: File, widthPx: Int, heightPx: Int) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_XMP, buildGPanoXmpPacket(widthPx, heightPx))
            exif.saveAttributes()
        }.onFailure {
            AppDiagnostics.record("export_xmp_write_failed target=file error=${it.javaClass.simpleName}")
        }
    }

    /** Wie [writeGPanoXmpMetadata] (File), aber für einen MediaStore-`Uri` (s. [saveToPictures]) -- über
     *  einen zweiten, beschreibbaren FileDescriptor auf denselben (bereits geschriebenen) Eintrag, da
     *  `ExifInterface` einen seekbaren Zugriff zum nachträglichen Einfügen des XMP-Segments braucht (ein
     *  reiner `OutputStream` von `ContentResolver.openOutputStream` reicht dafür nicht). */
    private fun writeGPanoXmpMetadata(context: Context, uri: Uri, widthPx: Int, heightPx: Int) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_XMP, buildGPanoXmpPacket(widthPx, heightPx))
                exif.saveAttributes()
            }
        }.onFailure {
            AppDiagnostics.record("export_xmp_write_failed target=uri error=${it.javaClass.simpleName}")
        }
    }

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
        imageFormat: ExportImageFormat = ExportImageFormat.Png,
        // 360°-optimierter Export (neu, additiv -- Default = bisheriges Verhalten für ALLE bestehenden
        // Aufrufer). wcs wird NUR für die Spherical360-Qualifikationsprüfung + den neuen Renderpfad
        // gebraucht (s. SphericalOverlayRenderer) -- beeinflusst sonst nichts am bestehenden Export.
        wcs: WcsSolutionLike? = null,
        projectionMode: ExportProjectionMode = ExportProjectionMode.Flat,
        // Reines Qualitäts-EXPERIMENT (Nutzer-Vorgabe 2026-08-30, s. SphericalOverlayRenderer.drawTextMesh)
        // -- 1 = bisheriges Verhalten für ALLE bestehenden Aufrufer. Wirkt NUR auf Spherical360-Text-Meshes.
        textSupersampleFactor: Int = 1,
        // Sprachkürzel für Sternbildnamen (Punkt 10-Nebenfund 2026-08-31: der Konstellations-Namenszweig in
        // drawOverlays() nutzte bisher IMMER ConstellationPattern.germanName, unabhängig von der App-
        // Sprache -- anders als DSO-/Sternnamen, die bereits über overlay.text/lang korrekt lokalisiert
        // sind). Default "en" NUR für Aufrufer ohne Sprachbezug (z.B. onExportSolveCrop, das immer mit
        // overlays=emptyList() aufruft -- der Wert wird dort nie gelesen); Aufrufer MIT echten Overlays
        // sollen den tatsächlichen AppLocale.resolvedLanguageTag übergeben.
        lang: String = "en",
        sourceDiagnostics: ExportSourceDiagnostics? = null,
    ): Uri {
        val output = renderBitmap(
            source, overlays, scale, includeBackground, includeConstellationAnchors, annotationEraseMask,
            graticule, graticuleThickness, graticuleColorArgb, graticuleOpacity, graticuleShowLabels,
            milkyWay, milkyWayOpacity,
            imageBlurIntensity, imageGrayscale, imageInverted,
            overlayCoordScale, layerDrawOrder, wcs, projectionMode, textSupersampleFactor, lang,
        )
        val format = effectiveFormat(imageFormat, includeBackground)
        val jpegQuality = qualityFor(format, scale)
        // Bestätigt Plan-Punkt "Spherical360-Renderer nicht an JPEG gekoppelt": renderBitmap() oben
        // erhält NIE das Zielformat -- die 360°-Warp-Geometrie/-Auflösung/-Qualität ist damit bauartbedingt
        // für JPEG und PNG identisch, nur dieser eine Kompressionsschritt danach unterscheidet sich.
        AppDiagnostics.record("spherical_export_format target=share imageFormat=${format.name} outputWidth=${output.width} outputHeight=${output.height}")
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val suffix = if (includeBackground) "bild" else "overlay"
        val file = File(dir, "sternbild_mapper_${suffix}_${System.currentTimeMillis()}.${extensionFor(format)}")
        FileOutputStream(file).use { output.compress(compressFormatFor(format), jpegQuality, it) }
        if (format == ExportImageFormat.Jpeg) {
            writeGPanoXmpMetadata(file, output.width, output.height)
        }
        if (sourceDiagnostics != null) {
            recordPixelSourceDiagnostic(sourceDiagnostics, source, output, jpegQuality, file.length())
        }
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
        imageFormat: ExportImageFormat = ExportImageFormat.Png,
        wcs: WcsSolutionLike? = null,
        projectionMode: ExportProjectionMode = ExportProjectionMode.Flat,
        textSupersampleFactor: Int = 1,
        // s. renderToShareUri-KDoc (Punkt 10-Nebenfund 2026-08-31).
        lang: String = "en",
        sourceDiagnostics: ExportSourceDiagnostics? = null,
    ): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return renderToShareUri(
                context, source, overlays, scale, includeBackground, includeConstellationAnchors, annotationEraseMask,
                graticule, graticuleThickness, graticuleColorArgb, graticuleOpacity, graticuleShowLabels,
                milkyWay, milkyWayOpacity,
                imageBlurIntensity, imageGrayscale, imageInverted,
                overlayCoordScale, layerDrawOrder, imageFormat, wcs, projectionMode, textSupersampleFactor, lang,
                sourceDiagnostics,
            )
        }

        val output = renderBitmap(
            source, overlays, scale, includeBackground, includeConstellationAnchors, annotationEraseMask,
            graticule, graticuleThickness, graticuleColorArgb, graticuleOpacity, graticuleShowLabels,
            milkyWay, milkyWayOpacity,
            imageBlurIntensity, imageGrayscale, imageInverted,
            overlayCoordScale, layerDrawOrder, wcs, projectionMode, textSupersampleFactor, lang,
        )
        val format = effectiveFormat(imageFormat, includeBackground)
        val jpegQuality = qualityFor(format, scale)
        AppDiagnostics.record("spherical_export_format target=save imageFormat=${format.name} outputWidth=${output.width} outputHeight=${output.height}")
        val suffix = if (includeBackground) "bild" else "overlay"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "sternbild_mapper_${suffix}_${System.currentTimeMillis()}.${extensionFor(format)}")
            put(MediaStore.Images.Media.MIME_TYPE, mimeTypeFor(format))
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Sternbild Mapper")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore konnte keinen Export-URI erzeugen.")
        resolver.openOutputStream(uri).use { stream ->
            requireNotNull(stream) { "MediaStore OutputStream ist null." }
            output.compress(compressFormatFor(format), jpegQuality, stream)
        }
        if (format == ExportImageFormat.Jpeg) {
            // Vor dem Freigeben (IS_PENDING=0) -- unser Prozess darf den gerade selbst geschriebenen,
            // noch ausstehenden Eintrag weiterhin per zweitem FileDescriptor bearbeiten.
            writeGPanoXmpMetadata(context, uri, output.width, output.height)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        if (sourceDiagnostics != null) {
            // Dateigröße nach dem Freigeben über den MediaStore-Uri selbst abfragen (kein separater
            // File-Pfad wie bei renderToShareUri verfügbar) -- statSize liefert die tatsächlich
            // geschriebene Byte-Zahl, unabhängig vom Kompressions-/Metadaten-Overhead danach.
            val exportBytes = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull()
            recordPixelSourceDiagnostic(sourceDiagnostics, source, output, jpegQuality, exportBytes)
        }
        output.recycle()
        return uri
    }

    /** Gemeinsame Diagnose-Zeile für beide Export-Wege (Nutzer-Auftrag 2026-09-02, "Originalexport"). */
    private fun recordPixelSourceDiagnostic(
        d: ExportSourceDiagnostics,
        source: Bitmap,
        output: Bitmap,
        jpegQuality: Int,
        exportFileBytes: Long?,
    ) {
        val backgroundUpscaled = source.width < output.width || source.height < output.height
        AppDiagnostics.record(
            "export_pixel_source originalWidth=${d.originalWidth} originalHeight=${d.originalHeight} " +
                "workingWidth=${d.workingWidth} workingHeight=${d.workingHeight} " +
                "backgroundSource=${d.backgroundSource} " +
                "backgroundDecodedWidth=${source.width} backgroundDecodedHeight=${source.height} " +
                "outputWidth=${output.width} outputHeight=${output.height} backgroundUpscaled=$backgroundUpscaled " +
                "jpegQuality=$jpegQuality originalFileBytes=${d.originalFileBytes ?: "unknown"} " +
                "exportFileBytes=${exportFileBytes ?: "unknown"} " +
                "originalColorProfile=${source.colorSpaceNameOrUnknown()} exportColorProfile=${output.colorSpaceNameOrUnknown()} " +
                "originalExifPreserved=false",
        )
    }

    // Bitmap.getColorSpace() ist erst ab API 26 vorhanden (App-minSdk=26, also immer verfügbar) -- kann
    // trotzdem null sein (z.B. bei manchen Software-Dekodierungen ohne explizites Profil).
    private fun Bitmap.colorSpaceNameOrUnknown(): String = colorSpace?.name ?: "unknown"

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
        wcs: WcsSolutionLike? = null,
        projectionMode: ExportProjectionMode = ExportProjectionMode.Flat,
        textSupersampleFactor: Int = 1,
        // s. renderToShareUri-KDoc (Punkt 10-Nebenfund 2026-08-31).
        lang: String = "en",
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
        // Verhältnis Ziel-Ausgabepixel / logischer Koordinatenraum -- derselbe Faktor, den
        // canvas.scale(...) gleich auf ALLE Vektor-Zeichenbefehle anwendet (die dadurch verlustfrei in
        // voller Zielauflösung rasterisieren). Ein Text-MESH ist dagegen ein Rasterbild -- ohne diesen
        // Faktor an SphericalOverlayRenderer weiterzugeben, würde dessen Zwischen-Bitmap nur in
        // logischer (Anzeige-)Auflösung gerendert und beim Original-Export (oft outputScale > 1, das
        // Quellbild wird ja in voller nativer Auflösung neu dekodiert) sichtbar verpixelt hochskaliert
        // -- Nutzer-Befund 2026-08-30 "360°-Export sichtbar pixeliger als normaler Export".
        val outputScale = renderScale * overlayCoordScale
        canvas.save()
        canvas.scale(outputScale, outputScale)

        // Zeichenreihenfolge der 5 Schichten frei konfigurierbar (Katalog bearbeiten -> Schichten,
        // Nutzerwunsch 2026-08-20) -- 1:1 zur Editor-Vorschau (StarMapperApp.kt EditorCanvas). Bei
        // layerDrawOrder == DEFAULT_DRAW_LAYER_ORDER identisch zur vorherigen fest verdrahteten Reihenfolge
        // (Milchstraße -> Gradnetz -> Sternbilder/Objekte/Sterne).
        // 360°-optimierter Export (neu): NUR aktiv, wenn explizit gewählt UND die WCS tatsächlich einem
        // vollständigen 2:1-Panorama mit horizontaler Periode entspricht (dieselbe Projektionsfamilie,
        // die SphericalOverlayRenderer.pixelToDirection/directionToPixel voraussetzt) -- sonst 100%
        // unverändertes Altverhalten. `sphericalProjection` bleibt `null`, wenn nicht qualifiziert;
        // ExportRenderer.drawOverlays (unverändert) bleibt in JEDEM Fall der alleinige Zeichenpfad für
        // alle Overlays, die SphericalOverlayRenderer.isEligible nicht als warp-fähig einstuft.
        val qualification = if (projectionMode == ExportProjectionMode.Spherical360) {
            qualifySpherical(wcs, coordW, coordH)
        } else {
            null
        }
        val sphericalProjection = (qualification as? SphericalQualification.Qualified)?.projection

        // Diagnose-Zähler (2026-08-29, Nutzer-Vorgabe): reine Mitzählung, ändert nichts am Zeichnen
        // selbst -- s. spherical_export_dispatch/spherical_export_components am Ende dieser Funktion.
        var sphericalOverlayCount = 0
        var normalFallbackCount = 0
        var skippedAlreadySphericalCount = 0
        var fallbackUnsupportedCount = 0
        var warpStats = SphericalOverlayRenderer.WarpStats()

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
                        if (sphericalProjection != null) {
                            val (warped, flat) = bucket.partition { SphericalOverlayRenderer.isEligible(it) }
                            warpStats += drawOverlays(
                                canvas, flat, includeConstellationAnchors, coordW, coordH,
                                lang, sphericalProjection, outputScale,
                            )
                            warpStats += SphericalOverlayRenderer.drawOverlays(
                                canvas, warped, sphericalProjection, coordW, coordH, outputScale, textSupersampleFactor,
                            )
                            sphericalOverlayCount += warped.size
                            normalFallbackCount += flat.size
                            flat.forEach { ov ->
                                if (ov.kind == OverlayKind.Constellation) skippedAlreadySphericalCount++ else fallbackUnsupportedCount++
                            }
                        } else {
                            drawOverlays(canvas, bucket, includeConstellationAnchors, coordW, coordH, lang)
                            normalFallbackCount += bucket.size
                        }
                        if (annotationEraseMask != null) {
                            SolveMask.punchOut(canvas, annotationEraseMask, coordW, coordH)
                            canvas.restoreToCount(eraseLayer)
                        }
                    }
                }
            }
        }
        canvas.restore()

        // Diagnose (2026-08-29, Nutzer-Vorgabe "läuft der 360°-Export überhaupt durch den Renderer?"):
        // exakt EINE Zeile pro Export, egal ob Flat oder Spherical360 gewählt wurde -- zeigt sofort, ob
        // der Modus überhaupt ankam, ob die WCS qualifiziert hat (und falls nicht: welcher der 4 Checks
        // sie ablehnte), und wie viele Overlays tatsächlich welchen Zeichenpfad durchlaufen haben.
        AppDiagnostics.record(
            "spherical_export_dispatch exportMode=${projectionMode.name} " +
                "renderer=${if (sphericalProjection != null) "Spherical360" else "Flat"} " +
                "wcsType=${wcs?.javaClass?.simpleName ?: "null"} " +
                "qualifyReject=${(qualification as? SphericalQualification.Rejected)?.reason ?: "none"} " +
                "sphericalRenderWidth=$coordW sphericalRenderHeight=$coordH " +
                "outputWidth=$width outputHeight=$height textIntermediateScale=${"%.3f".format(outputScale)} " +
                "supersampleFactor=$textSupersampleFactor antialiasing=true bitmapFiltering=true " +
                "overlayCount=${overlays.size} " +
                "sphericalOverlayCount=$sphericalOverlayCount normalFallbackCount=$normalFallbackCount",
        )
        if (projectionMode == ExportProjectionMode.Spherical360) {
            // Nur bei tatsächlich gewähltem Spherical360 -- bei Flat sind alle diese Zähler bedeutungslos
            // (der gesamte Bestand läuft ohnehin über den unveränderten Flat-Pfad, s. oben).
            // warpedMarkerCount/warpedEllipseCount sind in diesem PoC-Stand identisch (die einzige heute
            // warp-fähige Marker-Form IST der näherungsweise kreisförmige Ellipse-Marker) -- getrennt
            // gehalten, damit ein künftiger zweiter Marker-Typ (z.B. echte Ellipsen) ohne Formatänderung
            // dazukommen kann. *Attempted-Felder zusätzlich zu den vom Nutzer angefragten *Count-Feldern:
            // Differenz zu *WarpedCount deckt lautlose Fehlschläge (entartete Basis/Mesh) auf, die vorher
            // spurlos blieben.
            AppDiagnostics.record(
                "spherical_export_components warpedTextCount=${warpStats.textWarped} " +
                    "warpedMarkerCount=${warpStats.markerWarped} warpedEllipseCount=${warpStats.markerWarped} " +
                    "warpedLeaderCount=${warpStats.leaderWarped} " +
                    "skippedAlreadySphericalCount=$skippedAlreadySphericalCount " +
                    "fallbackUnsupportedCount=$fallbackUnsupportedCount " +
                    "textAttempted=${warpStats.textAttempted} markerAttempted=${warpStats.markerAttempted} " +
                    "leaderAttempted=${warpStats.leaderAttempted}",
            )
            // Nutzer-Vorgabe 2026-08-30 (zweiter Gerätetest, Semantik-/Größen-/Kollisions-/Tessellations-
            // Fehler): starDotCount = Sterne korrekt als gefüllter Punkt gezeichnet, starBoundaryCount MUSS
            // 0 sein (Regressions-Kanarienvogel -- ein Stern als volle Kontur wäre exakt der gemeldete
            // "Punkt sieht aus wie Ring"-Bug), starSkippedCount = Sterne ohne jede Form (markerRing=false
            // UND kein markerDot). avgCircleSegments = Ø tatsächlich gezeichnete Pfad-Vertices pro Kreis/
            // Punkt (Tessellationsdichte). collisionCandidates/-Resolved/-Unresolved = Marker mit Namen,
            // davon wie viele die Kollisions-Ausweiche (steigende Führungslinienlänge) tatsächlich lösen
            // konnte.
            val avgCircleSegments = if (warpStats.circleCount > 0) {
                warpStats.circleSegmentSum.toFloat() / warpStats.circleCount.toFloat()
            } else {
                0f
            }
            AppDiagnostics.record(
                "spherical_export_shapes starDotCount=${warpStats.starDotCount} " +
                    "starBoundaryCount=${warpStats.starBoundaryCount} starSkippedCount=${warpStats.starSkippedCount} " +
                    "nonStarBoundaryCount=${warpStats.nonStarBoundaryCount} circleCount=${warpStats.circleCount} " +
                    "avgCircleSegments=${"%.1f".format(avgCircleSegments)} maxCircleSegments=${warpStats.maxCircleSegments} " +
                    "collisionCandidates=${warpStats.collisionCandidates} " +
                    "collisionResolved=${warpStats.collisionResolved} collisionUnresolved=${warpStats.collisionUnresolved}",
            )
            // Punkt 14 (Nutzer-Vorgabe 2026-08-31): Zenit-/Nadir-Beschriftung (PolarArcLabel). Min/Max-
            // Latitude nur aussagekräftig, wenn mindestens ein polar/blend-Label vorkam (sonst bleiben die
            // WarpStats-Defaults MAX_VALUE/-MAX_VALUE stehen) -- deshalb defensiv auf "n/a" abgebildet statt
            // einen irreführenden Extremwert zu loggen.
            val hasPolarLabels = warpStats.polarArcLabelCount + warpStats.poleBlendLabelCount > 0
            AppDiagnostics.record(
                "spherical_export_polar normalSphericalLabelCount=${warpStats.normalSphericalLabelCount} " +
                    "polarArcLabelCount=${warpStats.polarArcLabelCount} poleBlendLabelCount=${warpStats.poleBlendLabelCount} " +
                    "exactPoleFallbackCount=${warpStats.exactPoleFallbackCount} polarArcGlyphCount=${warpStats.polarArcGlyphCount} " +
                    "polarArcMinLatitude=${if (hasPolarLabels) "%.2f".format(warpStats.polarArcMinLatitude) else "n/a"} " +
                    "polarArcMaxLatitude=${if (hasPolarLabels) "%.2f".format(warpStats.polarArcMaxLatitude) else "n/a"} " +
                    "polarCollisionResolved=${warpStats.polarCollisionResolved} polarCollisionHidden=${warpStats.polarCollisionHidden} " +
                    "circleMaxProjectedDeviationPx=${"%.3f".format(warpStats.circleMaxProjectedDeviationPx)}",
            )
        }

        return output
    }

    /**
     * Diagnose-Ergebnis der Spherical360-Qualifikationsprüfung (2026-08-29, Nutzer-Vorgabe "läuft der
     * 360°-Export überhaupt durch den Renderer?"): dieselbe Logik wie zuvor, aber statt bei Nicht-
     * Qualifikation stillschweigend `null` zu liefern, trägt [Rejected] jetzt einen menschenlesbaren
     * Grund -- landet unverändert in [spherical_export_dispatch]/im Diagnose-Log, ändert NICHTS an der
     * eigentlichen Entscheidung (der Aufrufer fällt bei [Rejected] exakt wie vorher aufs Flat-Verhalten
     * zurück).
     */
    private sealed interface SphericalQualification {
        data class Qualified(val projection: PanoramaProjection) : SphericalQualification
        data class Rejected(val reason: String) : SphericalQualification
    }

    /**
     * Prüft, ob [wcs] für den 360°-optimierten Export tatsächlich geeignet ist: eine [PanoramaWcsSolution]
     * mit [CylindricalProjection] (die einzige Familie mit einer horizontalen Periode/einem `fx`, s.
     * [SphericalOverlayRenderer]-Klassenkommentar) UND ein (nahezu) vollständiges 2:1-Bild -- dieselbe
     * Toleranz wie [FisheyeRefiner.FULL_PANORAMA_ASPECT_TOLERANCE] (2%), hier eigenständig als
     * Literal geführt, da jene Konstante `private` im Fit-Code bleibt (bewusst nicht exponiert, um den
     * für das Gradnetz/den Astrometrie-Fit gesperrten Code nicht anzufassen).
     */
    private fun qualifySpherical(wcs: WcsSolutionLike?, imageWidth: Int, imageHeight: Int): SphericalQualification {
        if (wcs == null) return SphericalQualification.Rejected("wcs_null")
        if (wcs !is PanoramaWcsSolution) return SphericalQualification.Rejected("wcs_type=${wcs.javaClass.simpleName}")
        val projection = wcs.projection
        if (projection !is CylindricalProjection) {
            return SphericalQualification.Rejected("projection_type=${projection.javaClass.simpleName}")
        }
        if (imageWidth <= 0 || imageHeight <= 0) return SphericalQualification.Rejected("invalid_image_dimensions")
        val aspect = imageWidth.toDouble() / imageHeight.toDouble()
        if (abs(aspect - 2.0) > 0.02) return SphericalQualification.Rejected("aspect_ratio=${"%.4f".format(aspect)}")
        return SphericalQualification.Qualified(projection)
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

    /** [lang]/[sphericalProjection]/[outputScale]: Punkt-10-Erweiterung (2026-08-31) -- Sternbild-NAMEN
     *  (nicht die Linien, die bleiben unverändert flach, s. `SphericalOverlayRenderer.drawConstellationName`-
     *  KDoc) werden bei gesetztem [sphericalProjection] UND Zenit-/Nadir-Nähe an die sphärische Export-
     *  Darstellung delegiert; [lang] behebt nebenbei einen eigenständigen Fund (Sternbildnamen nutzten
     *  bisher IMMER `germanName`, unabhängig von der App-Sprache). Rückgabe = akkumulierte
     *  [SphericalOverlayRenderer.WarpStats]-Beiträge aus genau diesem Zweig (leer, wenn nie qualifiziert
     *  -- ALLE bestehenden Aufrufer, die diese 3 Parameter nicht setzen, sehen dadurch exakt das bisherige
     *  Verhalten UND können die Rückgabe ignorieren). */
    private fun drawOverlays(
        canvas: Canvas,
        overlays: List<AnnotationOverlay>,
        includeConstellationAnchors: Boolean,
        coordWidth: Int,
        coordHeight: Int,
        lang: String = "en",
        sphericalProjection: PanoramaProjection? = null,
        outputScale: Float = 1f,
    ): SphericalOverlayRenderer.WarpStats {
        var constellationWarpStats = SphericalOverlayRenderer.WarpStats()
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
                        // Trimmen ZUERST (echte Sternanker), DANACH an der Bild-Naht aufteilen -- die
                        // neuen Schnittpunkte sind keine Anker und dürfen nicht nochmal angeschnitten
                        // werden. Identisches Vorgehen wie im Editor (s. StarMapperApp.kt).
                        edgePolylines.forEachIndexed { edgeIndex, poly ->
                            if (poly.size < 2) return@forEachIndexed
                            val trimmed = trimPolylineEnds(poly, lineTrimGap)
                            // GOLDEN-RUECKBAU 2026-09-03: identisch zum Editor -- Naht-Aufteilung nur
                            // bei periodischer Projektion (overlay.seamAware, s. dessen KDoc). WYSIWYG.
                            val pieces = if (overlay.seamAware) {
                                OverlayGeometry.splitPolylineAtSeam(trimmed, coordWidth)
                            } else {
                                listOf(trimmed)
                            }
                            pieces.forEach { piece ->
                                if (piece.size >= 2) drawEdge(piece, edgeIndex)
                            }
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
                        // Punkt-10-Nebenfund: vorher IMMER germanName, unabhängig von der App-Sprache --
                        // s. Funktions-KDoc. localizedDisplayName ist dieselbe Logik wie die Editor-
                        // Vorschau (ConstellationPattern.localizedName(), StarMapperApp.kt), nur als reine
                        // Funktion von [lang] statt Compose-reaktiv.
                        val displayName = overlay.constellation?.localizedDisplayName(lang).orEmpty()
                        if (nameAnchor != null && displayName.isNotEmpty()) {
                            // Punkt 10: nahe Zenit/Nadir -> sphärische PolarArc-/Mesh-Darstellung statt der
                            // flachen (die Linien/Anker oben bleiben in JEDEM Fall unverändert flach --
                            // bereits korrekt sky-abgetastet, s. SphericalOverlayRenderer-Klassenkommentar).
                            val sphericalStats = sphericalProjection?.let { proj ->
                                SphericalOverlayRenderer.drawConstellationName(
                                    canvas, overlay, displayName, nameAnchor, proj, coordWidth, coordHeight, outputScale,
                                )
                            }
                            if (sphericalStats != null) {
                                constellationWarpStats += sphericalStats
                            } else {
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
                                canvas.drawText(displayName, labelX, baselineY, textPaint)
                            }
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
        return constellationWarpStats
    }
}

/** Zeichnet den Marker-Namen (DSO/Stern) rechts neben das Symbol – wie im Editor. */
private fun drawMarkerName(canvas: Canvas, overlay: AnnotationOverlay) {
    // Sternnamen (Star-Ebene) folgen dem Deckkraft-Regler; andere Marker-Namen bleiben voll deckend.
    // Punkt 6 (Nutzer-Vorgabe 2026-08-30): overlay.nameOpacityLinked koppelt die Beschriftung
    // zusätzlich an die Objekt-Deckkraft, unabhängig von der Ebene (bisher NUR für Star fest verdrahtet).
    val nameAlpha = if (overlay.layer == AnnotationLayer.Star || overlay.nameOpacityLinked) overlay.opacity else 1f
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
        val nameX = cx + layout.nameAnchor.x
        val nameY = cy + layout.nameAnchor.y + paint.textSize * 0.35f
        canvas.drawText(overlay.text, nameX, nameY, paint)
    } else {
        val nameX = overlay.center.x + overlay.size.width / 2f + OverlayGeometry.MARKER_NAME_GAP
        val nameY = overlay.center.y + paint.textSize * 0.35f
        canvas.drawText(overlay.text, nameX, nameY, paint)
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
