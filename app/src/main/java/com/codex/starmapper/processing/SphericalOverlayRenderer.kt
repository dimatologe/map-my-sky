package com.codex.starmapper.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.diagnostics.AppDiagnostics
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.OverlayFont
import com.codex.starmapper.domain.OverlayKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 360°-optimierter Export-Renderpfad -- Proof of Concept (Nutzer-Vorgabe 2026-08-28, Plan-Abschnitt
 * "360°-optimierter Export"). Verzerrt GENAU zwei Overlay-Typen ([isEligible]: `OverlayKind.Text` und
 * kreisförmige `OverlayKind.Ellipse`-Marker) so, dass sie nach dem Wickeln des exportierten 2:1-JPEGs
 * auf eine Kugel in einem 360°-Betrachter lokal korrekt aussehen -- statt der bisherigen flachen
 * Bildschirmgeometrie. Alle übrigen Overlay-Typen bleiben in dieser Runde UNBERÜHRT (fallen weiterhin an
 * `ExportRenderer.drawOverlays` -- diese Datei ruft diese Funktion nirgends auf, sondern wird NUR für die
 * hier eligiblen Overlays vom Aufrufer separat aufgerufen, s. `ExportRenderer.renderBitmap`).
 *
 * HARTE ARCHITEKTUR-VORGABE (Nutzer, nicht verhandelbar): rein render-only. Diese Datei liest
 * [AnnotationOverlay] ausschließlich LESEND, schreibt niemals zurück (auch die Kollisions-Ausweich-Logik
 * unten arbeitet nur mit lokalen `.copy()`-Instanzen, die nie irgendwo gespeichert werden), erzeugt kein
 * dauerhaft gespeichertes verzerrtes Overlay-Modell. Da der Export eine einzelne, nutzerausgelöste Aktion
 * ist (kein Pro-Frame-Renderloop wie der Editor), braucht es keinen Cache mit Invalidierungslogik -- jeder
 * Aufruf berechnet die Warp-Geometrie komplett neu aus dem aktuellen Overlay-Zustand.
 *
 * Mathematik (Plan-Abschnitt "Mathematik, Runde 2" für die vollständige Herleitung + Vergleich gegen die
 * Exponential-Map): alles läuft im PANORAMA-Rahmen der aktiven [PanoramaProjection] (NICHT im
 * äquatorialen RA/Dec-Rahmen -- ein im Editor gedrehtes Overlay soll seine Orientierung relativ zur
 * PANORAMA-Textur behalten, nicht relativ zu astronomisch Nord, die beide nur zusammenfallen, wenn die
 * Kamera keinerlei Rollwinkel um die Blickrichtung hat). Die lokale Tangentialbasis wird EMPIRISCH über
 * [localFrameAt] aus [PanoramaProjection.pixelToDirection]-Proben hergeleitet (Gram-Schmidt) -- dadurch
 * automatisch korrekt orientiert, unabhängig vom fx/fy-Vorzeichen der gefitteten Projektion, und ohne
 * jede `1/cos(lat)`-Stelle (robust bis dicht an den Pol). Die Abbildung selbst ist die INVERSE GNOMONIC
 * (`normalize(normal + u*right + v*up)`) statt einer Exponential-Map -- das entspricht exakt dem, was
 * eine perspektivische 360°-Betrachter-Kamera im Kugelzentrum beim Direktblick auf den Anker zeigt, und
 * ist damit für Display-/UI-Geometrie (Text, Marker) die richtige Wahl (Proportionstreue beim Anblicken),
 * nicht die geodätische Abstandstreue der Exponential-Map (die wäre für eine ECHTE astronomische
 * Winkelausdehnung richtig, hier nicht gebraucht).
 *
 * Korrektur-Runde 2026-08-30 (Nutzer-Vorgabe nach zweitem Gerätetest -- Semantik-/Größen-/Kollisions-/
 * Tessellations-/Pol-Text-Fehler, s. Einzelkommentare unten): [localFrameAt] liefert jetzt zusätzlich die
 * TATSÄCHLICHE, lokale Radiant-pro-Pixel-Skala je Achse (aus DENSELBEN Proben, die ohnehin schon für die
 * Tangentialbasis nötig sind) statt einer global angenommenen `2π/imageWidth`-Konstante -- diese Konstante
 * setzte voraus, dass die gefittete `CylindricalProjection.fx` EXAKT `imageWidth/(2π)` ist, was nur mit
 * dem opt-in `fitCylindricalConstrainedFx` garantiert ist (s. [[project_gradnetz_randbeschriftung]]) und
 * für `fy`/nicht-lineare Cylindrical-/Mercator-Kinds ohnehin nie zutraf. Star-Marker respektieren jetzt
 * `AnnotationOverlay.markerRing`/`markerDot` (1:1 aus `ExportRenderer.drawOverlays` übernommen) statt
 * pauschal über `overlay.filled` gezeichnet zu werden. Marker-Namen nutzen jetzt dieselben Stilquellen wie
 * `ExportRenderer.drawMarkerName` (Textgröße/Farbe/Fett/Deckkraft), nicht mehr die MARKERGRÖSSE als
 * Textgröße. Eine leichte Kollisions-Ausweiche (steigende Führungslinienlänge, wie schon im Flat-Pfad bei
 * der Erstsynchronisierung) verhindert neue, erst durch den unabhängigen Pro-Marker-Warp entstehende
 * Überlappungen. Kreis-/Punkt-Tessellation skaliert jetzt leicht mit dem Radius statt fest bei 16 Proben.
 *
 * Korrektur-Runde 2 (2026-08-30, Nutzer-Meldung "Marker bleiben polnah quadratisch, 18x18px"): die obige
 * Runde-1-Änderung führte einen NEUEN, subtileren Fehler ein -- [localFrameAt]s [LocalFrame.radPerPxRight]/
 * [LocalFrame.radPerPxUp] wurden zur Umrechnung Marker-/Text-PIXELgröße -> Winkelgröße AM JEWEILIGEN ANKER
 * selbst verwendet. Das kürzt sich mathematisch exakt gegen die Verzerrung, die [warpToPixel] beim
 * Zurückprojizieren DERSELBEN Winkelgröße einführt (allgemeines Prinzip: lokale Ableitung invertieren,
 * dann dieselbe Abbildung erneut anwenden, liefert immer wieder den Ausgangswert -- unabhängig davon, wie
 * stark die Projektion an dieser Stelle tatsächlich verzerrt). Ergebnis: ein Marker/Kreis behielt IMMER
 * exakt seine ursprüngliche Pixelgröße, auch nahe den Polen, wo die Equirectangular/Cylindrical-Projektion
 * ihn eigentlich deutlich (horizontal) auseinanderziehen müsste, damit er im 360°-Betrachter rund
 * erscheint. Fix: eine neue, ORTS-UNABHÄNGIGE [RefScale] (s. [referenceScale]) wird EINMAL pro Export an
 * der Bildmitte (dem "Äquator" jeder qualifizierenden Projektion) bestimmt und ERSETZT [frame.
 * radPerPxRight]/[frame.radPerPxUp] überall dort, wo bisher eine PIXEL-Größe in eine WINKEL-Größe
 * umgerechnet wurde ([warpedCirclePath], die Marker-Namens-/Führungslinien-Anker in
 * [drawWarpedCircleMarker], [meshVertexPixel] für Text-Meshes) -- NICHT dort, wo [frame.right]/[frame.up]/
 * [frame.normal] als reine (skalenfreie) Richtungsvektoren für die ORIENTIERUNG dienen, die bleibt zu
 * Recht pro Anker. Per Hand-Simulation verifiziert (Node-Skript, Latitude 86°): vorher 36,7x36,0px
 * (praktisch quadratisch), nachher 519,5x36,0px (~14x horizontal gestreckt, exakt der erwartete
 * 1/cos(86°)≈14,3-Faktor).
 *
 * Diagnose-Ergänzung (2026-08-29/30, Nutzer-Vorgabe): [drawOverlays] liefert [WarpStats] (Zähler für
 * Text/Marker/Führungslinien/Stern-Darstellungsart/Segmentzahl/Kollisionen) statt `Unit`, rein additiv --
 * KEINE Zeichenfunktion/Formel wurde NUR für die Diagnose verändert. Für GENAU einen Stern und GENAU ein
 * DSO (je den mit der größten Poldistanz im aktuellen Aufruf) wird zusätzlich eine ausführliche
 * Kontrolltest-Zeile geloggt (Anker, Mesh-/Pfadgrenzen, Vertex-/Seam-Zahl, Schriftgröße, Kollisionsstatus,
 * Erfolg/Fehlschlag-Grund) -- bewusst nur 1 pro Kategorie statt aller (teils hunderte Sterne), da die
 * Diagnose-Datei gedeckelt ist (s. [AppDiagnostics]). Kein Objektname wird geloggt (Privacy-Konvention
 * dieses Projekts) -- die Pixel-Position im Log reicht zur Zuordnung mit dem, was auf dem Bildschirm zu
 * sehen ist.
 */
object SphericalOverlayRenderer {

    // Sub-Pixel-Toleranz für die adaptive Linien-Bisektion (Kreisrand, Führungslinien) -- dieselbe
    // Größenordnung wie GraticuleRenderer.ADAPTIVE_TOLERANCE_PX (eigenständig hier definiert, KEIN Import
    // aus der für das Gradnetz gesperrten Datei, s. Klassenkommentar). B11 (Nutzer-Vorgabe 2026-08-30,
    // Zielband 0.25-0.35px): auf 0.3f verschärft -- rein numerisch, die Architektur war bereits
    // auflösungsrelativ (die Bisektion prüft die tatsächliche Chord-Abweichung direkt gegen die realen
    // imageWidth/imageHeight-Bildpixel dieses Aufrufs, nicht gegen eine angenommene feste Auflösung wie
    // 6500x3250 -- ein 12000x6000-Export bekommt dadurch automatisch mehr Segmente für dieselbe absolute
    // Pixel-Toleranz, ohne Codeänderung).
    private const val LINE_TOLERANCE_PX = 0.3f
    private const val LINE_MAX_DEPTH = 10

    // Sicherheitsschwelle (Bild-px): ein einzelner Bisektionsschritt, der einen SPRUNG über diese Größe
    // erzeugt, wird als Pol-Überdeckung/Entartung gewertet (s. Plan-Abschnitt "Zenit/Nadir als echte
    // Sonderfälle") -- das Overlay wird dann übersprungen statt eine kaputte Geometrie zu zeichnen. Grob
    // an einem Achtel typischer Bildbreiten kalibriert (großzügig über jeder normalen lokalen Warp-
    // Bewegung eines kleinen Text-/Marker-Overlays, aber klar unter einer echten Pol-Entartung).
    private const val DEGENERATE_JUMP_PX = 800f

    // Kleine numerische Sicherheitsschranke gegen (praktisch nie auftretende) entartete Tangentialproben.
    private const val MIN_TANGENT_LENGTH = 1e-6

    // Adaptive Proben-Distanz für die lokale Tangentialbasis (s. Klassenkommentar "Korrektur-Runde"):
    // Standardfall BASE, verdoppelt (x4 pro Schritt) bis MAX, falls die rohe Tangentenlänge unterhalb
    // TANGENT_LENGTH_SAFE_MIN bleibt (Proben-Signal zu klein relativ zum Rundungsfehler -> nahe einer
    // Projektions-Singularität wie dem Pol einer Cylindrical-Projektion).
    private const val PROBE_EPS_PX_BASE = 2.0
    private const val PROBE_EPS_PX_MAX = 64.0
    private const val TANGENT_LENGTH_SAFE_MIN = 1e-4

    // Adaptive Text-Mesh-Auflösung: Start-/Maximal-Rasterdichte (Zellen pro Achse) + Toleranz.
    private const val MESH_START_CELLS = 2
    private const val MESH_MAX_CELLS = 32
    private const val MESH_TOLERANCE_PX = 0.5f

    // Kollisions-Ausweiche für Marker-Namen (s. Klassenkommentar): so viele steigende Führungslinien-
    // Längen werden versucht, bevor der letzte (am weitesten ausgewichene) Versuch ungeprüft übernommen
    // wird -- ein Name wird NIE komplett verworfen, nur weiter vom Marker weg platziert.
    private const val MAX_LABEL_COLLISION_ATTEMPTS = 3

    // Deckel für die Text-Zwischenbitmap-Pixeldichte relativ zum logischen Koordinatenraum (s.
    // computeWarpedTextMesh) -- reines Sicherheitsnetz gegen eine pathologisch hohe overlayCoordScale,
    // kein erwarteter Normalfall (typische "Original"-Exporte liegen deutlich darunter).
    private const val MAX_TEXT_BITMAP_SCALE = 8f

    /** Aggregierte Zähler EINES [drawOverlays]-Aufrufs (ExportRenderer summiert über alle 3 Layer-Buckets
     *  auf) -- reine Diagnose, beeinflusst nichts am Zeichnen. `*Attempted` zählt jeden Versuch (auch
     *  fehlgeschlagene), `*Warped` nur die tatsächlich sphärisch gezeichneten -- die Differenz macht
     *  lautlose Fehlschläge (entartete Tangentialbasis/Mesh-Auflösung) sichtbar, die vorher spurlos
     *  blieben. `starDotCount`/`starBoundaryCount`/`starSkippedCount` klassifizieren NUR `layer==Star`-
     *  Marker nach tatsächlicher Darstellungsart -- `starBoundaryCount` sollte nach dem Semantik-Fix
     *  IMMER 0 sein (Regressions-Kanarienvogel: Sterne sind laut `AstapOverlayMapper.createStarOverlays`
     *  IMMER `markerRing=false`, ein Stern, der trotzdem als volle Kontur gezeichnet wird, ist ein Bug). */
    data class WarpStats(
        val textAttempted: Int = 0,
        val textWarped: Int = 0,
        val markerAttempted: Int = 0,
        val markerWarped: Int = 0,
        val leaderAttempted: Int = 0,
        val leaderWarped: Int = 0,
        val starDotCount: Int = 0,
        val starBoundaryCount: Int = 0,
        val starSkippedCount: Int = 0,
        val nonStarBoundaryCount: Int = 0,
        val circleCount: Int = 0,
        val circleSegmentSum: Int = 0,
        val maxCircleSegments: Int = 0,
        val collisionCandidates: Int = 0,
        val collisionResolved: Int = 0,
        val collisionUnresolved: Int = 0,
        // Punkt 10/14 (Nutzer-Vorgabe 2026-08-31): Zenit-/Nadir-Beschriftung (PolarArcLabel). "normal"/
        // "blend"/"polar" sind REINE Diagnose-Einteilungen nach Poldistanz (s. polarArcDiagnosticMode) --
        // "blend" nutzt bereits denselben glyphenbasierten Renderer wie "polar", nur bei größerem [rho]
        // (die Small-Circle-Krümmung nimmt dadurch stetig zu, s. Klassenkommentar-Ergänzung, KEIN
        // separater Interpolations-Renderpfad). polarArcMinLatitude/MaxLatitude nur über tatsächlich
        // polar/blend gerenderte Labels (nicht normal) -- Defaults so gewählt, dass ein leerer Export
        // (0 Polar-Labels) nach dem Kombinieren via [plus] nicht fälschlich min>max o.ä. zeigt.
        val normalSphericalLabelCount: Int = 0,
        val polarArcLabelCount: Int = 0,
        val poleBlendLabelCount: Int = 0,
        val exactPoleFallbackCount: Int = 0,
        val polarArcGlyphCount: Int = 0,
        val polarArcMinLatitude: Float = Float.MAX_VALUE,
        val polarArcMaxLatitude: Float = -Float.MAX_VALUE,
        val polarCollisionResolved: Int = 0,
        val polarCollisionHidden: Int = 0,
        val circleMaxProjectedDeviationPx: Float = 0f,
    ) {
        operator fun plus(other: WarpStats) = WarpStats(
            textAttempted + other.textAttempted,
            textWarped + other.textWarped,
            markerAttempted + other.markerAttempted,
            markerWarped + other.markerWarped,
            leaderAttempted + other.leaderAttempted,
            leaderWarped + other.leaderWarped,
            starDotCount + other.starDotCount,
            starBoundaryCount + other.starBoundaryCount,
            starSkippedCount + other.starSkippedCount,
            nonStarBoundaryCount + other.nonStarBoundaryCount,
            circleCount + other.circleCount,
            circleSegmentSum + other.circleSegmentSum,
            max(maxCircleSegments, other.maxCircleSegments),
            collisionCandidates + other.collisionCandidates,
            collisionResolved + other.collisionResolved,
            collisionUnresolved + other.collisionUnresolved,
            normalSphericalLabelCount + other.normalSphericalLabelCount,
            polarArcLabelCount + other.polarArcLabelCount,
            poleBlendLabelCount + other.poleBlendLabelCount,
            exactPoleFallbackCount + other.exactPoleFallbackCount,
            polarArcGlyphCount + other.polarArcGlyphCount,
            min(polarArcMinLatitude, other.polarArcMinLatitude),
            max(polarArcMaxLatitude, other.polarArcMaxLatitude),
            polarCollisionResolved + other.polarCollisionResolved,
            polarCollisionHidden + other.polarCollisionHidden,
            max(circleMaxProjectedDeviationPx, other.circleMaxProjectedDeviationPx),
        )
    }

    // Punkt 10/14: [mode] = "normal"/"blend"/"polar" (s. polarArcDiagnosticMode) für JEDES erfolgreich
    // gewarpte Textlabel, egal welcher Overlay-Art (Text/Marker-Name/Sternbild-Name) -- der Aufrufer
    // (drawOverlays / die neue drawConstellationName) summiert das in WarpStats auf. [exactPoleFallback]
    // markiert den Fall aus Punkt 7 (Small Circle am exakten Pol entartet -> Label übersprungen).
    private data class TextOutcome(
        val warped: Boolean,
        val mode: String = "normal",
        val glyphCount: Int = 0,
        val arcRadiusDeg: Float? = null,
        // Panorama-Breite (Grad, s. exactPolarLatitudeDeg) DES LABEL-PIVOTS -- getrennt von [arcRadiusDeg]
        // (= rho, Kolatitude ZUM Pol): WarpStats.polarArcMinLatitude/MaxLatitude (Punkt 14) sollen die
        // tatsächliche Breite zeigen, nicht rho (das wäre am Pol 0 statt 90, verwirrend im Log).
        val latitudeDeg: Float? = null,
        val readingDirection: String? = null,
        val exactPoleFallback: Boolean = false,
    )

    private data class MarkerOutcome(
        val shapeDrawn: Boolean,
        val shapeMode: String = "none",
        val segmentCount: Int = 0,
        val nameAttempted: Boolean = false,
        val nameWarped: Boolean = false,
        val leaderAttempted: Boolean = false,
        val leaderWarped: Boolean = false,
        val collisionCandidate: Boolean = false,
        val collisionResolved: Boolean = false,
        // Punkt 10/14: Zenit-/Nadir-Diagnose des Namens (falls einer versucht wurde) -- s. TextOutcome/
        // applyTextOutcomeToStats, von drawOverlays()s Ellipse-Zweig aufgerufen.
        val nameTextOutcome: TextOutcome? = null,
    )

    /** Ergebnis von [computeWarpedTextMesh] -- fertiges, aber noch NICHT gezeichnetes Text-Mesh samt
     *  seiner projizierten Bounding-Box (für die Kollisionsprüfung, s. [drawWarpedCircleMarker]). Der
     *  Aufrufer MUSS entweder [drawTextMesh] aufrufen (recycelt die Bitmap) oder selbst `bitmap.recycle()`
     *  aufrufen, falls das Mesh doch verworfen wird (z.B. ein Kollisions-Versuch, der nicht gewählt wurde). */
    private class TextMeshResult(
        val bitmap: Bitmap,
        val cells: Int,
        val verts: FloatArray,
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float,
    )

    /** Ist dieses Overlay in der aktuellen PoC-Stufe warp-fähig? Text IMMER; Ellipse NUR ohne Fadenkreuz
     *  (das ist visuell ein Kreuz, kein Kreis) UND NUR näherungsweise kreisförmig (Seitenverhältnis-
     *  Toleranz 5%) -- echte Ellipsen mit Exzentrizität sind laut Plan eine spätere Erweiterung. */
    fun isEligible(overlay: AnnotationOverlay): Boolean = when (overlay.kind) {
        OverlayKind.Text -> true
        OverlayKind.Ellipse -> overlay.reticle == null && isApproximatelyCircular(overlay)
        else -> false
    }

    private fun isApproximatelyCircular(overlay: AnnotationOverlay): Boolean {
        val w = overlay.size.width
        val h = overlay.size.height
        if (w <= 0f || h <= 0f) return false
        val ratio = max(w, h) / min(w, h)
        return ratio <= 1.05f
    }

    /** Grobe, aber für Diagnosezwecke ausreichende Näherung der Panorama-Breite (Grad; +90 = obere
     *  Bildzeile/Zenit, 0 = Bildmitte/Äquator, -90 = untere Bildzeile/Nadir) rein aus der Pixel-Zeile --
     *  exakt für eine echte äquirektangulare Abbildung (die einzige hier qualifizierende Familie, s.
     *  `ExportRenderer.qualifySpherical`), da dort die Zeile linear in der Elevation ist. NUR fürs
     *  Diagnose-Log, keine Zeichenfunktion nutzt diesen Wert. */
    private fun panoramaLatitudeDeg(pixelY: Float, imageHeight: Int): Float {
        if (imageHeight <= 0) return 0f
        return 90f - (pixelY / imageHeight.toFloat()) * 180f
    }

    private fun latitudeExtremityDeg(pixelY: Float, imageHeight: Int): Float =
        abs(panoramaLatitudeDeg(pixelY, imageHeight))

    private fun fmt(v: Float): String = "%.2f".format(v)

    /** Zwei [minX,minY,maxX,maxY]-Boxen überlappend? Für die Marker-Namens-Kollisionsausweiche. */
    private fun boxesOverlap(a: FloatArray, b: FloatArray): Boolean =
        !(a[2] < b[0] || b[2] < a[0] || a[3] < b[1] || b[3] < a[1])

    /** Punkt 8: Kollisions-PRIORITÄT für die Bearbeitungsreihenfolge in [drawOverlays] -- niedrigere Zahl
     *  = höhere Priorität (beansprucht ihren bevorzugten Label-Platz zuerst), s. dortiger Kommentar. */
    private fun labelPriorityTier(overlay: AnnotationOverlay): Int = when {
        overlay.layer == null -> 0 // Nutzer-Form/-Zeichnung/-Text/-Fadenkreuz: GESAMTE Position manuell.
        overlay.kind == OverlayKind.Constellation && overlay.constellationNameOffset != null -> 0 // Punkt 9.
        overlay.layer == AnnotationLayer.DeepSky || overlay.layer == AnnotationLayer.Constellation -> 1
        else -> 2 // AnnotationLayer.Star.
    }

    /** Punkt 14: verbucht ein [TextOutcome] (egal ob Freitext, Marker-/DSO-Name oder Sternbild-Name) in
     *  den passenden [WarpStats]-Zenit-/Nadir-Zählern -- gemeinsam genutzt von allen drei Aufrufstellen,
     *  damit die drei Zähler-Familien (normal/blend/polar/exactPoleFallback/glyphCount/min-max-Latitude)
     *  garantiert konsistent bleiben statt an jeder Stelle einzeln nachgebaut zu werden. */
    private fun applyTextOutcomeToStats(stats: WarpStats, outcome: TextOutcome): WarpStats {
        if (outcome.exactPoleFallback) return stats.copy(exactPoleFallbackCount = stats.exactPoleFallbackCount + 1)
        if (!outcome.warped) return stats
        return when (outcome.mode) {
            "polar" -> stats.copy(
                polarArcLabelCount = stats.polarArcLabelCount + 1,
                polarArcGlyphCount = stats.polarArcGlyphCount + outcome.glyphCount,
                polarArcMinLatitude = min(stats.polarArcMinLatitude, outcome.latitudeDeg ?: stats.polarArcMinLatitude),
                polarArcMaxLatitude = max(stats.polarArcMaxLatitude, outcome.latitudeDeg ?: stats.polarArcMaxLatitude),
            )
            "blend" -> stats.copy(
                poleBlendLabelCount = stats.poleBlendLabelCount + 1,
                polarArcGlyphCount = stats.polarArcGlyphCount + outcome.glyphCount,
                polarArcMinLatitude = min(stats.polarArcMinLatitude, outcome.latitudeDeg ?: stats.polarArcMinLatitude),
                polarArcMaxLatitude = max(stats.polarArcMaxLatitude, outcome.latitudeDeg ?: stats.polarArcMaxLatitude),
            )
            else -> stats.copy(normalSphericalLabelCount = stats.normalSphericalLabelCount + 1)
        }
    }

    /** Zeichnet [overlays] (bereits auf [isEligible] gefiltert erwartet) sphärisch vorverzerrt.
     *  [imageWidth] muss der KOORDINATENRAUM-Breite entsprechen, in der `overlay.center` usw. bereits
     *  ausgedrückt sind (dieselbe Größe, die `ExportRenderer.renderBitmap` als `coordW` führt) -- NICHT
     *  die tatsächliche Ziel-Bitmap-Pixelbreite, die je nach Exportskalierung abweicht. Liefert
     *  [WarpStats] (reine Diagnose-Zählung, s. Klassenkommentar) -- der Aufrufer summiert über mehrere
     *  Layer-Buckets auf. Die Kollisions-Ausweiche (s. [drawWarpedCircleMarker]) wirkt NUR innerhalb
     *  EINES Aufrufs (ein Layer-Bucket, typischerweise "Objects" ODER "Star") -- Überlappungen ZWISCHEN
     *  Layern (z.B. ein DSO-Name über einem Sternnamen) sind unverändert wie im Flat-Pfad möglich, s.
     *  Klassenkommentar. [outputScale] = Verhältnis Ziel-Ausgabepixel/[imageWidth] (== derselbe Faktor,
     *  den `ExportRenderer.renderBitmap` bereits per `canvas.scale(...)` auf alle Vektor-Zeichenbefehle
     *  anwendet) -- wird NUR für die Auflösung der Text-Zwischenbitmaps gebraucht (s.
     *  [computeWarpedTextMesh]), Vektor-Geometrie (Kreise/Linien) braucht ihn nicht. [ssaaFactor] > 1
     *  aktiviert das Supersampling-Qualitätsexperiment aus [drawTextMesh] (Nutzer-Vorgabe 2026-08-30) --
     *  betrifft NUR Text-Meshes, keine Kreise/Linien (die sind bereits vektor-antialiased). */
    fun drawOverlays(
        canvas: Canvas,
        overlays: List<AnnotationOverlay>,
        projection: PanoramaProjection,
        imageWidth: Int,
        imageHeight: Int,
        outputScale: Float,
        ssaaFactor: Int = 1,
    ): WarpStats {
        var stats = WarpStats()
        if (overlays.isEmpty() || imageWidth <= 0) return stats
        // Latitude-neutrale Referenzskala (s. Klassenkommentar an [RefScale]) -- EINMAL pro Export, NICHT
        // pro Marker/Text. Fehlschlag hier bedeutet, dass selbst die Bildmitte nicht projizierbar ist
        // (Projektion grundsätzlich defekt) -- dann macht auch kein einzelner Anker-Warp mehr Sinn.
        val refScale = referenceScale(projection, imageWidth, imageHeight) ?: return stats

        // Kontrolltest-Kandidaten (2026-08-29): je EIN Stern/DSO mit der größten Poldistanz in DIESEM
        // Aufruf -- s. Klassenkommentar für die Begründung (Log-Größenbudget).
        val starProbe = overlays
            .filter { it.kind == OverlayKind.Ellipse && it.layer == AnnotationLayer.Star }
            .maxByOrNull { latitudeExtremityDeg(it.center.y, imageHeight) }
        val dsoProbe = overlays
            .filter { it.kind == OverlayKind.Ellipse && it.layer == AnnotationLayer.DeepSky }
            .maxByOrNull { latitudeExtremityDeg(it.center.y, imageHeight) }

        // Bereits platzierte Marker-Namens-Bounding-Boxen in DIESEM Aufruf -- reine lokale Buchhaltung
        // für die Kollisions-Ausweiche, s. [drawWarpedCircleMarker]. Nichts davon wird persistiert.
        val placedLabelBoxes = mutableListOf<FloatArray>()

        // Punkt 8 (Nutzer-Vorgabe 2026-08-31): "manuell positionierte Labels > wichtige automatische
        // Labels > weniger wichtige automatische Labels". Da [placedLabelBoxes] first-come-first-served
        // arbeitet (der ERSTE Aufruf beansprucht seinen Platz, spätere weichen aus), setzt eine
        // Prioritäts-SORTIERUNG der Verarbeitungsreihenfolge das direkt um: höhere Priorität zuerst ->
        // beansprucht ihren bevorzugten Platz zuerst. Betrifft NUR diese Reihenfolge (Label-Platz-
        // Vergabe) -- nicht, welche Form über welche andere gezeichnet wird (unabhängig davon, Formen
        // können sich unverändert wie bisher optisch überlappen). [labelPriorityTier] nutzt nur bereits
        // auf [AnnotationOverlay] vorhandene Felder (kein neues UI-seitiges Signal nötig): Tier 0 = echte
        // Nutzer-Objekte (layer==null: Formen/Zeichnungen/Text/Fadenkreuz, deren GESAMTE Position manuell
        // ist) ODER ein manuell verschobener Sternbildname (Punkt 9, `constellationNameOffset`); Tier 1 =
        // DSO-/Sternbild-Namen (auto, aber wenige, individuell bedeutsame Objekte); Tier 2 = Sternnamen
        // (auto, typischerweise am zahlreichsten). `sortedBy` ist stabil -- innerhalb einer Tier bleibt
        // die ursprüngliche Reihenfolge erhalten.
        val prioritizedOverlays = overlays.sortedBy { labelPriorityTier(it) }
        // Punkt 11/14: EIN gemeinsamer Tracker über den ganzen Aufruf (nicht pro Kreis neu) -- liefert
        // die größte tatsächlich aufgetretene finale Pixel-Abweichung für circleMaxProjectedDeviationPx.
        val circleDevTracker = floatArrayOf(0f)

        prioritizedOverlays.forEach { overlay ->
            when (overlay.kind) {
                OverlayKind.Text -> {
                    stats = stats.copy(textAttempted = stats.textAttempted + 1)
                    val outcome = drawWarpedText(canvas, overlay, projection, refScale, outputScale, ssaaFactor)
                    if (outcome.warped) stats = stats.copy(textWarped = stats.textWarped + 1)
                    stats = applyTextOutcomeToStats(stats, outcome)
                }
                OverlayKind.Ellipse -> {
                    stats = stats.copy(markerAttempted = stats.markerAttempted + 1)
                    val isStar = overlay.layer == AnnotationLayer.Star
                    val probeTag = when {
                        starProbe != null && overlay == starProbe -> "star"
                        dsoProbe != null && overlay == dsoProbe -> "dso"
                        else -> null
                    }
                    val outcome = drawWarpedCircleMarker(
                        canvas, overlay, projection, imageWidth, refScale, placedLabelBoxes, outputScale, ssaaFactor,
                        circleDevTracker = circleDevTracker,
                        probeTag = probeTag,
                        panoramaLatDeg = panoramaLatitudeDeg(overlay.center.y, imageHeight),
                    )
                    if (outcome.shapeDrawn) {
                        stats = stats.copy(
                            markerWarped = stats.markerWarped + 1,
                            circleCount = stats.circleCount + 1,
                            circleSegmentSum = stats.circleSegmentSum + outcome.segmentCount,
                            maxCircleSegments = max(stats.maxCircleSegments, outcome.segmentCount),
                        )
                    }
                    stats = when (outcome.shapeMode) {
                        "dot" -> stats.copy(starDotCount = stats.starDotCount + 1)
                        "boundary" -> if (isStar) {
                            stats.copy(starBoundaryCount = stats.starBoundaryCount + 1)
                        } else {
                            stats.copy(nonStarBoundaryCount = stats.nonStarBoundaryCount + 1)
                        }
                        else -> if (isStar) stats.copy(starSkippedCount = stats.starSkippedCount + 1) else stats
                    }
                    if (outcome.nameAttempted) {
                        stats = stats.copy(textAttempted = stats.textAttempted + 1)
                        if (outcome.nameWarped) stats = stats.copy(textWarped = stats.textWarped + 1)
                    }
                    if (outcome.leaderAttempted) {
                        stats = stats.copy(leaderAttempted = stats.leaderAttempted + 1)
                        if (outcome.leaderWarped) stats = stats.copy(leaderWarped = stats.leaderWarped + 1)
                    }
                    if (outcome.collisionCandidate) {
                        stats = stats.copy(collisionCandidates = stats.collisionCandidates + 1)
                        if (outcome.nameWarped) {
                            stats = if (outcome.collisionResolved) {
                                stats.copy(collisionResolved = stats.collisionResolved + 1)
                            } else {
                                stats.copy(collisionUnresolved = stats.collisionUnresolved + 1)
                            }
                        }
                    }
                    outcome.nameTextOutcome?.let { nameOutcome ->
                        stats = applyTextOutcomeToStats(stats, nameOutcome)
                        // Punkt 8/14: separat auf PolarArc-/Blend-Beschriftungen gezählt.
                        // polarCollisionResolved = tatsächlich gezeichnet UND kollisionsfrei platziert.
                        // polarCollisionHidden = GAR NICHT gezeichnet (degenerierter Glyph/exakter Pol,
                        // s. namePolarFallback) -- Punkt 8 verbietet Schriftverkleinerung als Ausweg, aber
                        // erlaubt (analog Punkt 7) das Weglassen bei echter Entartung. Eine gezeichnete,
                        // aber weiterhin überlappende Beschriftung (letzter Versuch ausgeschöpft) zählt in
                        // KEINEM der beiden -- bleibt wie bisher nur in collisionUnresolved erfasst.
                        if (nameOutcome.mode == "polar" || nameOutcome.mode == "blend") {
                            stats = if (!nameOutcome.warped) {
                                stats.copy(polarCollisionHidden = stats.polarCollisionHidden + 1)
                            } else if (outcome.collisionResolved) {
                                stats.copy(polarCollisionResolved = stats.polarCollisionResolved + 1)
                            } else {
                                stats
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        return stats.copy(circleMaxProjectedDeviationPx = max(stats.circleMaxProjectedDeviationPx, circleDevTracker[0]))
    }

    // --- Tangentialbasis + Kugelabbildung -------------------------------------------------------------

    /** [radPerPxRight]/[radPerPxUp] = TATSÄCHLICHE lokale Skala (Radiant pro Bild-Pixel) entlang [right]/
     *  [up] AN DIESEM ANKER -- ersetzt eine zuvor global angenommene Konstante, s. Klassenkommentar
     *  "Korrektur-Runde".
     *
     *  WICHTIG (Nutzer-Meldung 2026-08-30 "Marker bleiben polnah quadratisch"): diese beiden Felder
     *  dürfen NIEMALS benutzt werden, um eine gewünschte PIXEL-Größe (Marker-/Text-Radius) in eine
     *  Winkel-Größe umzurechnen -- nur für die adaptive Epsilon-Wahl in [localFrameAt] selbst. Grund
     *  (allgemeines, nicht auf diese Projektion beschränktes Prinzip): [radPerPxRight] AN EINEM ANKER
     *  ist exakt die lokale Ableitung der Projektion DORT. Wird sie benutzt, um `radiusPx` in einen
     *  Winkel `u = radiusPx * radPerPxRight` umzurechnen, und dieser Winkel wird ANSCHLIESSEND über
     *  DIESELBE Projektion (via [warpToPixel]) wieder zurück in Pixel projiziert, kürzt sich die lokale
     *  Ableitung exakt gegen ihre eigene Inverse -- das Ergebnis ist (bis auf Rundung) IMMER wieder
     *  `radiusPx`, komplett unabhängig davon, wie stark die Projektion an dieser Stelle tatsächlich
     *  verzerrt (z.B. die 1/cos(Breite)-Stauchung nahe den Polen einer Equirectangular/Cylindrical-
     *  Projektion). Ein Kreis nahe Latitude 86° blieb dadurch ~18x18px statt sich (korrekt) zu einer
     *  breiten Ellipse zu verziehen. Die richtige Umrechnung nutzt eine EINZIGE, ortsunabhängige
     *  Referenz-Skala (s. [RefScale]/[referenceScale]) -- NUR [right]/[up]/[normal] (reine Einheits-
     *  Richtungsvektoren, keine Skala) bleiben zurecht pro Anker, weil die Ausrichtung "was ist hier
     *  rechts/oben auf der Kugel" tatsächlich vom Ort abhängt. */
    private data class LocalFrame(
        val normal: Vec3,
        val right: Vec3,
        val up: Vec3,
        val radPerPxRight: Double,
        val radPerPxUp: Double,
    )

    /** Ortsunabhängige Radiant-pro-Pixel-Referenzskala für die Umrechnung Marker-/Text-PIXELgröße ->
     *  Winkelgröße -- Ersatz für die frühere, latitude-kontaminierte Verwendung von [LocalFrame.
     *  radPerPxRight]/[radPerPxUp] AN JEDEM Anker (s. Klassenkommentar an [LocalFrame]). Einmal PRO
     *  EXPORT an einem festen, latitude-neutralen Referenzpunkt bestimmt (Bildmitte -- der "Äquator"
     *  jeder qualifizierenden Cylindrical-Projektion, s. Klassenkommentar oben zur 2:1-Voraussetzung),
     *  NICHT am jeweiligen Marker-Anker. Liefert trotzdem die tatsächlich GEFITTETEN fx/fy-Werte (nicht
     *  die theoretisch ideale `2π/imageWidth`-Konstante) -- exakt das, was die ursprüngliche
     *  "Korrektur-Runde" beheben wollte -- nur eben latitude-neutral statt pro Anker gemessen. */
    private data class RefScale(val perPxRight: Double, val perPxUp: Double)

    private fun referenceScale(projection: PanoramaProjection, imageWidth: Int, imageHeight: Int): RefScale? {
        val frame = localFrameAt(projection, imageWidth / 2.0, imageHeight / 2.0) ?: return null
        return RefScale(frame.radPerPxRight, frame.radPerPxUp)
    }

    /** Anchor-Pixel -> lokale Orthonormalbasis + lokale Pixel-Skala im PANO-Rahmen, rein empirisch aus
     *  [PanoramaProjection.pixelToDirection]-Proben (Gram-Schmidt) -- s. Klassenkommentar für die
     *  Begründung. Die Proben-Distanz wächst adaptiv (s. [PROBE_EPS_PX_BASE]), falls das rohe Tangenten-
     *  Signal bei der Basisdistanz zu klein für verlässliche Rundung wäre (nahe einer Projektions-
     *  Singularität) -- OHNE das kippt die ORIENTIERUNG der Basis dort numerisch instabil, nicht nur ihre
     *  Skala (Nutzer-Meldung "Kipp-/Flip-Probleme nahe Zenit/Pol"). `null` bei endgültig entarteten Proben
     *  (z.B. Anker exakt auf dem Pol) statt eine numerisch instabile Basis zurückzugeben. */
    private fun localFrameAt(projection: PanoramaProjection, px: Double, py: Double): LocalFrame? {
        val normal = projection.pixelToDirection(px, py)?.normalized() ?: return null

        fun tangent(dir: Vec3): Vec3 {
            val dot = normal.dot(dir)
            return Vec3(dir.x - normal.x * dot, dir.y - normal.y * dot, dir.z - normal.z * dot)
        }

        var eps = PROBE_EPS_PX_BASE
        var rawRight: Vec3
        var rawUp: Vec3
        while (true) {
            val rightProbe = projection.pixelToDirection(px + eps, py)?.normalized() ?: return null
            val upProbe = projection.pixelToDirection(px, py - eps)?.normalized() ?: return null
            rawRight = tangent(rightProbe)
            rawUp = tangent(upProbe)
            val minLen = min(rawRight.length(), rawUp.length())
            if (minLen >= TANGENT_LENGTH_SAFE_MIN || eps >= PROBE_EPS_PX_MAX) break
            eps *= 4.0
        }
        if (rawRight.length() < MIN_TANGENT_LENGTH || rawUp.length() < MIN_TANGENT_LENGTH) return null
        val radPerPxRight = rawRight.length() / eps
        val radPerPxUp = rawUp.length() / eps
        val right = rawRight.normalized()
        val up = rawUp.normalized()
        val rightDotUp = up.dot(right)
        val rightOrth = Vec3(
            right.x - up.x * rightDotUp,
            right.y - up.y * rightDotUp,
            right.z - up.z * rightDotUp,
        )
        if (rightOrth.length() < MIN_TANGENT_LENGTH) return null
        return LocalFrame(normal, rightOrth.normalized(), up, radPerPxRight, radPerPxUp)
    }

    /** Inverse Gnomonic: lokaler Tangentialebenen-Versatz (u,v, Radiant) -> Kugelpunkt -> Bild-Pixel.
     *  `reference` reicht die periodische Ast-Kontinuität durch (dasselbe Prinzip wie überall sonst in
     *  diesem Projekt bei periodischen Projektionen). */
    private fun warpToPixel(
        projection: PanoramaProjection,
        frame: LocalFrame,
        u: Double,
        v: Double,
        reference: Offset?,
    ): Offset? {
        if (abs(u) < 1e-9 && abs(v) < 1e-9) return projection.directionToPixel(frame.normal, reference)
        val raw = Vec3(
            frame.normal.x + u * frame.right.x + v * frame.up.x,
            frame.normal.y + u * frame.right.y + v * frame.up.y,
            frame.normal.z + u * frame.right.z + v * frame.up.z,
        )
        val len = raw.length()
        if (len < MIN_TANGENT_LENGTH) return null
        return projection.directionToPixel(Vec3(raw.x / len, raw.y / len, raw.z / len), reference)
    }

    // --- Adaptive Linien-Bisektion (Kreisrand, Führungslinien) ----------------------------------------

    /** Start-Segmentzahl für die Kreis-/Punkt-Approximation -- steigt leicht mit dem Radius (Nutzer-
     *  Vorgabe "Segmentzahl adaptiv erhöhen"), damit größere Marker von Anfang an eine feinere
     *  Ausgangsauflösung bekommen und weniger rekursive Bisektionstiefe brauchen, um unter
     *  [LINE_TOLERANCE_PX] zu konvergieren. Die eigentliche Adaptivität (mehr Unterteilung bei stärkerer
     *  lokaler Krümmung, z.B. nahe Zenit/Pol) leistet weiterhin [refineArc] anhand der TATSÄCHLICHEN
     *  Sehnen-Abweichung, unabhängig vom Grund der Krümmung. */
    private fun circleSampleCount(radiusPx: Float): Int =
        (16 + (radiusPx / 20f).roundToInt()).coerceIn(16, 64)

    /** Tastet einen Kreis vom Radius [radiusPx] um den Anker (in lokalen Bild-Pixeln, VOR Warp) ab und
     *  verfeinert jedes Segment rekursiv, bis die Sehnen-Abweichung unter [LINE_TOLERANCE_PX] fällt oder
     *  [LINE_MAX_DEPTH] erreicht ist. Nutzt [frame]s TATSÄCHLICHE lokale Achsen-Skala (statt einer
     *  global angenommenen), damit der Kreis am Ziel wirklich den beabsichtigten Radius hat -- s.
     *  Klassenkommentar "Korrektur-Runde". `null`, wenn der Anker selbst nicht projizierbar ist oder eine
     *  Entartung (Pol-Überdeckung) erkannt wird -- s. Klassenkommentar oben.
     *
     *  Bisektion nutzt [refineCircleArc] (Nutzer-Vorgabe 2026-08-30 "Kreise wirken polygonal"): der
     *  vorherige [refineArc] bisektierte die (u,v)-Werte selbst LINEAR -- der so gefundene "Mittelpunkt"
     *  ist die SEHNEN-Mitte zwischen zwei Kreispunkten, NICHT ein Punkt auf dem tatsächlichen
     *  Kreisrand (für einen echten Kreis liegt jeder Sehnen-Mittelpunkt STRENG innerhalb, nie auf der
     *  Kurve) -- die Krümmungs-Abweichung wurde dadurch systematisch UNTERSCHÄTZT, ein Segment konnte
     *  fälschlich als "flach genug" durchgehen, obwohl der wahre Bogen dazwischen noch sichtbar
     *  ausbeult. [refineCircleArc] bisektiert stattdessen den WINKEL (die tatsächliche Kreis-
     *  Parametrisierung) und wertet an JEDEM Bisektionsschritt den ECHTEN Kreispunkt bei diesem Winkel
     *  aus -- exakt die vom Nutzer verlangte "echten geometrischen Mittelpunkt der Parametrisierung
     *  berechnen"-Methode. */
    /** [outputScale]: Verhältnis Ziel-Ausgabepixel/Koordinatenraum (s. [drawOverlays]-KDoc) -- Punkt 11
     *  (Nutzer-Vorgabe 2026-08-31, "adaptive Tessellation anhand der FINALEN Exportauflösung, projected
     *  midpoint deviation <= ca. 0.25-0.35px"): [LINE_TOLERANCE_PX] ist ein Koordinatenraum-Maß; bei
     *  [outputScale] != 1 (jeder skalierte Export, s. `ExportRenderer.renderBitmap`s `outputScale`-
     *  Herleitung) wäre die TATSÄCHLICHE Abweichung im fertigen Bild `dev*outputScale` -- ohne Korrektur
     *  könnte ein hochskalierter ("Original"-)Export sichtbar polygonalere Kreise zeigen als ein 1:1-Export
     *  trotz identischer Bisektionstiefe. [maxDevTracker] (1-elementiges FloatArray als Mutable-Referenz --
     *  Kotlin kennt kein `inout`) sammelt die größte tatsächlich AUFGETRETENE finale Abweichung für
     *  `circleMaxProjectedDeviationPx` (Punkt 14), unabhängig davon, ob sie unter der Toleranz lag. */
    private fun warpedCirclePath(
        projection: PanoramaProjection,
        frame: LocalFrame,
        radiusPx: Float,
        refScale: RefScale,
        outputScale: Float,
        maxDevTracker: FloatArray,
    ): List<Offset>? {
        val samples = circleSampleCount(radiusPx)
        val angleStep = 2.0 * PI / samples
        fun uvAt(angle: Double): Pair<Double, Double> =
            (radiusPx * cos(angle) * refScale.perPxRight) to (radiusPx * sin(angle) * refScale.perPxUp)

        val firstUv = uvAt(0.0)
        var reference = warpToPixel(projection, frame, firstUv.first, firstUv.second, null) ?: return null
        val out = ArrayList<Offset>(samples * 2)
        out += reference
        var angleA = 0.0
        for (i in 1..samples) {
            val angleB = i * angleStep
            val a = reference
            val (uB, vB) = uvAt(angleB)
            val b = warpToPixel(projection, frame, uB, vB, a) ?: return null
            if (!refineCircleArc(projection, frame, ::uvAt, angleA, a, angleB, b, 0, out, outputScale, maxDevTracker)) return null
            reference = b
            angleA = angleB
        }
        return out
    }

    /** Winkel-parametrische Bisektion EINES Kreisbogen-Segments -- s. [warpedCirclePath]-KDoc für die
     *  Begründung, warum das (nicht die lineare (u,v)-Bisektion von [refineArc]) für einen echten
     *  Kreisbogen die richtige Methode ist. [uvAt] liefert den (u,v)-Punkt DER PARAMETRISIERUNG bei
     *  einem gegebenen Winkel -- die Kreisformel selbst bleibt dadurch komplett in [warpedCirclePath],
     *  diese Funktion ist rein die rekursive Verfeinerung. [outputScale]/[maxDevTracker]: s.
     *  [warpedCirclePath]-KDoc (Punkt 11). */
    private fun refineCircleArc(
        projection: PanoramaProjection,
        frame: LocalFrame,
        uvAt: (Double) -> Pair<Double, Double>,
        angleA: Double, a: Offset,
        angleB: Double, b: Offset,
        depth: Int,
        out: MutableList<Offset>,
        outputScale: Float,
        maxDevTracker: FloatArray,
    ): Boolean {
        val chord = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        if (chord > DEGENERATE_JUMP_PX) return false
        if (depth >= LINE_MAX_DEPTH) {
            out += b
            return true
        }
        val angleM = (angleA + angleB) / 2.0
        val (uM, vM) = uvAt(angleM)
        val mid = warpToPixel(projection, frame, uM, vM, a) ?: return false
        val dev = distPointToSegment(mid, a, b)
        if (dev > DEGENERATE_JUMP_PX) return false
        val effectiveTolerancePx = LINE_TOLERANCE_PX / outputScale.coerceAtLeast(0.01f)
        if (dev > effectiveTolerancePx) {
            if (!refineCircleArc(projection, frame, uvAt, angleA, a, angleM, mid, depth + 1, out, outputScale, maxDevTracker)) return false
            if (!refineCircleArc(projection, frame, uvAt, angleM, mid, angleB, b, depth + 1, out, outputScale, maxDevTracker)) return false
            return true
        }
        val finalDevPx = dev * outputScale
        if (finalDevPx > maxDevTracker[0]) maxDevTracker[0] = finalDevPx
        out += b
        return true
    }

    /** Winkel-UNABHÄNGIGE, lineare (u,v)-Bisektion -- korrekt für Geometrie, die im Tangentialraum
     *  selbst GERADE ist (z.B. die Führungslinie eines Marker-Namens, s. [drawWarpedCircleMarker]), im
     *  Unterschied zu [refineCircleArc] für echte Kreisbögen (s. dortiger KDoc). */
    private fun refineArc(
        projection: PanoramaProjection,
        frame: LocalFrame,
        uA: Double, vA: Double, a: Offset,
        uB: Double, vB: Double, b: Offset,
        depth: Int,
        out: MutableList<Offset>,
    ): Boolean {
        val chord = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        if (chord > DEGENERATE_JUMP_PX) return false
        if (depth >= LINE_MAX_DEPTH) {
            out += b
            return true
        }
        val uM = (uA + uB) / 2.0
        val vM = (vA + vB) / 2.0
        val mid = warpToPixel(projection, frame, uM, vM, a) ?: return false
        val dev = distPointToSegment(mid, a, b)
        if (dev > DEGENERATE_JUMP_PX) return false
        if (dev > LINE_TOLERANCE_PX) {
            if (!refineArc(projection, frame, uA, vA, a, uM, vM, mid, depth + 1, out)) return false
            if (!refineArc(projection, frame, uM, vM, mid, uB, vB, b, depth + 1, out)) return false
            return true
        }
        out += b
        return true
    }

    private fun distPointToSegment(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        val cx = a.x + t * dx
        val cy = a.y + t * dy
        return hypot((p.x - cx).toDouble(), (p.y - cy).toDouble()).toFloat()
    }

    // --- Zenit/Nadir (PolarArcLabel) -------------------------------------------------------------------
    //
    // Punkt 10 (Nutzer-Vorgabe 2026-08-31, Scope AUSSCHLIESSLICH dieser Renderer/seine temporäre
    // Exportgeometrie -- NICHT die frühere, komplett zurückgebaute TinySky-Zenit-Beschriftung, s.
    // [[feedback_tinysky_zenith_beschriftung]]): "Panorama-Zenit/Nadir" ist HIER exakt (0,0,±1) im
    // PANORAMA-Vec3-Rahmen selbst (s. [CylindricalProjection.pixelToDirection]: Bildzeile 0 = phi=+90° =
    // (0,0,1); NICHT der astronomische Himmelspol, der nur bei rollwinkelfreier Kamera zusammenfällt --
    // exakt wie vom Nutzer verlangt). Ein "Small Circle" konstanter Poldistanz [rho] um diesen Pol ist per
    // Definition eine Kurve konstanten [phi] (Azimut [lambda] variiert) -- GENAU das, was [computePolarArcGlyphs]
    // für jeden einzelnen Buchstaben abtastet, statt (wie die bestehende [computeWarpedTextMesh]) EIN
    // gnomonisches Tangentialebenen-Rechteck über die ganze Textbreite zu spannen (das bei großer
    // Poldistanz-Abweichung zunehmend verzerrt -- exakt der gemeldete Fehler).
    //
    // Herleitung "kein künstlicher Blend-Faktor nötig" (Punkt 5, "nicht nur Rotation, auch Krümmung muss
    // stetig zunehmen"): die Small-Circle-Krümmung eines Textspans fester Pixel-Vorschubbreite skaliert
    // mit 1/sin([rho]) -- UND [rho] selbst nimmt stetig ab, je näher ein Label am Pol liegt. Wird
    // [computePolarArcGlyphs] durchgehend ab [GLYPH_MODE_THRESHOLD_DEG] verwendet (statt erst ab einer
    // zweiten, höheren Schwelle), nimmt die tatsächliche Krümmung dadurch AUTOMATISCH UND STETIG mit der
    // Poldistanz zu -- keine separate Interpolation zwischen zwei Rendermethoden nötig. "blend" vs.
    // "polar" (Diagnose, s. [polarArcDiagnosticMode]) ist rein eine Kategorisierung nach Poldistanz für
    // die Diagnose-Zähler (Punkt 14), KEINE zweite Rendermethode.
    //
    // Herleitung "keine Rotation in der FLACHEN Textur nötig" (Node-Hand-Simulation vor der Umsetzung, s.
    // Session-Notiz): für [CylindricalProjection] (die EINZIGE hier qualifizierende Familie) gilt
    // `x = cx + fx*lambda`, `y = cy + fy*Y(phi)` -- x hängt AUSSCHLIESSLICH von lambda ab, y AUSSCHLIESSLICH
    // von phi, beide LINEAR/monoton in ihrer jeweiligen Variable. Bei KONSTANTEM phi (= auf dem Small
    // Circle) bewegt sich JEDER Buchstabe daher exakt auf einer waagerechten Pixel-Zeile -- die lokale
    // Rotation (aus [localFrameAt] AN DER GLYPH-EIGENEN Position bestimmt, s. [computePolarArcGlyphs])
    // kommt dadurch für diese Projektionsfamilie algebraisch nahe 0 heraus, UNABHÄNGIG von der Poldistanz.
    // Das "Herumlegen um den Pol" (s. Nutzer-ASCII-Skizze) entsteht NICHT durch eine Rotation IN DER
    // FLACHEN EXPORT-TEXTUR, sondern ERST beim Wickeln auf die Kugel + perspektivischer Betrachtung durch
    // einen 360°-Betrachter, der nahe diesem Pol blickt (ein Small Circle um die Blickachse projiziert sich
    // für eine Lochkamera, die GENAU auf diese Achse blickt, als echter Kreis/Bogen um die Bildmitte). Die
    // Rotation wird TROTZDEM projektions-generisch (nicht Cylindrical-spezifisch fest verdrahtet) über
    // [localFrameAt] berechnet -- kostet pro Glyph nur einen zusätzlichen (bereits vorhandenen,
    // verifizierten) Probe-Aufruf, bleibt aber korrekt, falls diese Datei künftig auch für eine andere
    // Projektionsfamilie qualifiziert.
    //
    // Punkt 7 (exakter Pol): [MIN_SIN_RHO] fängt den Fall ab, in dem der Small-Circle-Umfang selbst für
    // einen einzigen kurzen Namen zu klein würde (Umfang ~ 2*pi*sin(rho) -> 0) -- deterministischer
    // Fallback (Label übersprungen, `exactPoleFallbackCount`), NIE NaN/Infinity/zufällige Ausrichtung.

    private const val GLYPH_MODE_THRESHOLD_DEG = 75.0
    private const val POLAR_ARC_DIAGNOSTIC_THRESHOLD_DEG = 85.0
    private const val MIN_SIN_RHO = 0.005 // ~rho < 0.29°

    /** Exakte Panorama-Breite (Grad, +90=Zenit/-90=Nadir) aus der ECHTEN 3D-Richtung -- im Unterschied zu
     *  [panoramaLatitudeDeg] (das nur eine für Equirectangular exakte, für Cylindrical/Mercator lediglich
     *  angenäherte Pixel-Zeilen-Formel ist, s. dortiger KDoc) hier über [asin] aus [dir.z] bestimmt --
     *  korrekt für ALLE drei `CylindricalProjection`-Kinds, da `z = sin(phi)` unabhängig vom jeweiligen
     *  `Y(phi)` gilt. Wird für die Modus-Entscheidung (Punkt 5) verwendet; [panoramaLatitudeDeg] bleibt
     *  unverändert für ihren bestehenden, rein diagnostischen Zweck. */
    private fun exactPolarLatitudeDeg(dir: Vec3): Double = Math.toDegrees(asin(dir.z.coerceIn(-1.0, 1.0)))

    private fun polarArcDiagnosticMode(absLatDeg: Double): String = when {
        absLatDeg < GLYPH_MODE_THRESHOLD_DEG -> "normal"
        absLatDeg < POLAR_ARC_DIAGNOSTIC_THRESHOLD_DEG -> "blend"
        else -> "polar"
    }

    private data class GlyphPlacement(
        val char: Char,
        val pixel: Offset,
        val rotationDeg: Float,
    )

    private data class PolarArcResult(
        val glyphs: List<GlyphPlacement>,
        val arcRadiusDeg: Float,
        val readingDirection: String,
    )

    /** Platziert [text] Buchstabe für Buchstabe auf einem ECHTEN Small Circle konstanter Poldistanz um
     *  Zenit/Nadir (Punkt 10) -- s. Abschnitts-Kommentar oben für die vollständige Herleitung. Text wird
     *  ZENTRIERT um [pivotDir] verteilt (identische Konvention wie [computeWarpedTextMesh]s (0,5;0,5)-
     *  Anker UND wie die bestehende Konstellationsnamen-/Marker-Namen-Zentrierung im Flat-Pfad). `null`
     *  nur, wenn der PIVOT selbst nicht projizierbar ist ODER der Small Circle zu nah am exakten Pol
     *  entartet (Punkt 7, [MIN_SIN_RHO]) -- einzelne entartete Glyphen INNERHALB eines sonst gültigen
     *  Labels werden übersprungen (nicht das ganze Label verworfen), s. `continue` unten. */
    private fun computePolarArcGlyphs(
        text: String,
        pivotDir: Vec3,
        projection: PanoramaProjection,
        refScale: RefScale,
        measurePaint: Paint,
    ): PolarArcResult? {
        if (text.isBlank()) return null
        val pivot = pivotDir.normalized()
        val poleSign = if (pivot.z >= 0.0) 1.0 else -1.0
        val rho = acos(abs(pivot.z).coerceIn(-1.0, 1.0))
        val sinRho = sin(rho)
        if (sinRho < MIN_SIN_RHO) return null
        val lambda0 = atan2(pivot.y, pivot.x)
        val pivotPixel = projection.directionToPixel(pivot, null) ?: return null

        val widths = FloatArray(text.length)
        measurePaint.getTextWidths(text, widths)
        val totalWidth = widths.sum()
        // Punkt 6: EINE deterministische Leserichtung pro Hemisphäre (nicht pro Label) -- verhindert
        // zufällig wirkende Flips zwischen benachbarten Labels (s. Abschnitts-Kommentar).
        val azimuthSign = poleSign
        val readingDirection = if (poleSign > 0.0) "zenith_increasing_azimuth" else "nadir_decreasing_azimuth"

        var cursor = -totalWidth / 2f
        val glyphs = ArrayList<GlyphPlacement>(text.length)
        for (i in text.indices) {
            val advance = widths[i]
            val centerOffsetPx = cursor + advance / 2f
            cursor += advance
            if (text[i].isWhitespace()) continue // kein sichtbarer Glyph -> keine Bounding-Box/Rotation nötig.
            val arcLenRad = centerOffsetPx * refScale.perPxRight
            val dLambda = azimuthSign * arcLenRad / sinRho
            val lambda = lambda0 + dLambda
            val z = poleSign * cos(rho)
            val glyphDir = Vec3(sinRho * cos(lambda), sinRho * sin(lambda), z)
            val glyphPixel = projection.directionToPixel(glyphDir, pivotPixel) ?: continue
            val jumpPx = hypot((glyphPixel.x - pivotPixel.x).toDouble(), (glyphPixel.y - pivotPixel.y).toDouble())
            if (jumpPx > DEGENERATE_JUMP_PX * 4.0) continue
            val glyphFrame = localFrameAt(projection, glyphPixel.x.toDouble(), glyphPixel.y.toDouble())
            val rotationDeg = if (glyphFrame != null) {
                val probe = warpToPixel(projection, glyphFrame, 2.0 * refScale.perPxRight, 0.0, glyphPixel)
                if (probe != null) {
                    Math.toDegrees(atan2((probe.y - glyphPixel.y).toDouble(), (probe.x - glyphPixel.x).toDouble())).toFloat()
                } else {
                    0f
                }
            } else {
                0f
            }
            glyphs += GlyphPlacement(text[i], glyphPixel, rotationDeg)
        }
        if (glyphs.isEmpty()) return null
        return PolarArcResult(glyphs, Math.toDegrees(rho).toFloat(), readingDirection)
    }

    /** Zeichnet [result] Glyph für Glyph (Punkt 4: KEIN Rechteck-Bitmap-Warp für Polartext) -- jeder
     *  Buchstabe einzeln über [Canvas.rotate] an seiner EIGENEN Position+Ausrichtung, `Paint.Align.CENTER`
     *  vorausgesetzt (s. Aufrufer). */
    private fun drawPolarArcText(canvas: Canvas, result: PolarArcResult, paint: Paint) {
        result.glyphs.forEach { g ->
            canvas.save()
            canvas.translate(g.pixel.x, g.pixel.y)
            canvas.rotate(g.rotationDeg)
            canvas.drawText(g.char.toString(), 0f, 0f, paint)
            canvas.restore()
        }
    }

    /** Punkt 8: "Union der projizierten Glyph-Bounds" als Kollisionsbox für ein [PolarArcResult] -- statt
     *  des ursprünglichen geraden Textrechtecks (das für stark gekrümmten Polartext eine schlechte Näherung
     *  wäre). Pro Glyph wird dessen (bereits rotiertes) Rechteck in Welt-Eckpunkte umgerechnet, danach
     *  global min/max gebildet. */
    private fun polarArcBounds(result: PolarArcResult, paint: Paint): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val rect = android.graphics.Rect()
        result.glyphs.forEach { g ->
            paint.getTextBounds(g.char.toString(), 0, 1, rect)
            val rad = Math.toRadians(g.rotationDeg.toDouble())
            val cosR = cos(rad).toFloat()
            val sinR = sin(rad).toFloat()
            val corners = arrayOf(
                Offset(rect.left.toFloat(), rect.top.toFloat()),
                Offset(rect.right.toFloat(), rect.top.toFloat()),
                Offset(rect.right.toFloat(), rect.bottom.toFloat()),
                Offset(rect.left.toFloat(), rect.bottom.toFloat()),
            )
            corners.forEach { c ->
                val rx = c.x * cosR - c.y * sinR
                val ry = c.x * sinR + c.y * cosR
                val wx = g.pixel.x + rx
                val wy = g.pixel.y + ry
                if (wx < minX) minX = wx
                if (wx > maxX) maxX = wx
                if (wy < minY) minY = wy
                if (wy > maxY) maxY = wy
            }
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    // --- Kreisförmiger DSO-/Stern-Marker ---------------------------------------------------------------

    /** Zeichnet EINEN Ellipse-Marker (DSO oder Stern) sphärisch vorverzerrt, inkl. optionalem Namen +
     *  Führungslinie. Formauswahl UND Namens-Stilquellen sind jetzt 1:1 aus `ExportRenderer.drawOverlays`/
     *  `drawMarkerName` übernommen (Nutzer-Vorgabe 2026-08-30 "Semantik-Treue"/"Quellen der Stilwerte") --
     *  vorher wurde JEDES eligible Ellipse-Overlay pauschal über `overlay.filled` am VOLLEN Umfang
     *  gezeichnet, was Sternmarker (die laut `AstapOverlayMapper.createStarOverlays` IMMER
     *  `markerRing=false` haben, s. dort) fälschlich als Ring statt als kleinen Punkt erscheinen ließ, UND
     *  Namen nutzten `textOverlaySize(overlay)` (= die MARKERGRÖSSE, `overlay.size.height`) statt der
     *  echten Namens-Schriftgröße -- bei einem großen DSO erschien der Name dadurch riesig.
     *
     *  Kollisions-Ausweiche für den Namen: bis zu [MAX_LABEL_COLLISION_ATTEMPTS] steigende
     *  Führungslinienlängen (auf einer LOKALEN `.copy()`, nie persistiert) werden versucht, bis das
     *  resultierende, GEWARPTE Namens-Rechteck keine bereits in [placedLabelBoxes] platzierte Box mehr
     *  überlappt -- reagiert auf NACH dem unabhängigen Pro-Marker-Warp entstehende Überlappungen, die die
     *  ursprüngliche Flat-Platzierung nicht vorhersehen konnte. Scope s. [drawOverlays]-KDoc. */
    private fun drawWarpedCircleMarker(
        canvas: Canvas,
        overlay: AnnotationOverlay,
        projection: PanoramaProjection,
        imageWidth: Int,
        refScale: RefScale,
        placedLabelBoxes: MutableList<FloatArray>,
        outputScale: Float,
        ssaaFactor: Int,
        circleDevTracker: FloatArray,
        probeTag: String? = null,
        panoramaLatDeg: Float = 0f,
    ): MarkerOutcome {
        fun anchorTag() = "anchorPx=(${fmt(overlay.center.x)},${fmt(overlay.center.y)}) panoramaLatDeg=${fmt(panoramaLatDeg)}"

        val frame = localFrameAt(projection, overlay.center.x.toDouble(), overlay.center.y.toDouble())
        if (frame == null) {
            if (probeTag != null) {
                AppDiagnostics.record(
                    "spherical_export_probe_marker category=$probeTag id=${overlay.id} ${anchorTag()} " +
                        "outcome=degenerate_frame_null rendererUsed=Spherical360",
                )
            }
            return MarkerOutcome(shapeDrawn = false, shapeMode = "none")
        }

        val shapeMode: String
        val shapeRadiusPx: Float
        var shapeDrawn = false
        var seamPieces = 0
        var pathSize = 0
        var pathBounds: FloatArray? = null
        if (overlay.markerRing) {
            shapeMode = "boundary"
            shapeRadiusPx = (overlay.size.width + overlay.size.height) / 4f
            val path = warpedCirclePath(projection, frame, shapeRadiusPx, refScale, outputScale, circleDevTracker)
            if (path == null) {
                if (probeTag != null) {
                    AppDiagnostics.record(
                        "spherical_export_probe_marker category=$probeTag id=${overlay.id} ${anchorTag()} " +
                            "shapeMode=$shapeMode sourceRadiusPx=${fmt(shapeRadiusPx)} outcome=degenerate_path_null rendererUsed=Spherical360",
                    )
                }
                return MarkerOutcome(shapeDrawn = false, shapeMode = shapeMode)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (overlay.filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                color = withAlpha(overlay.colorArgb, overlay.opacity)
                strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth)
            }
            seamPieces = drawSeamAwarePath(canvas, path, closed = true, paint, imageWidth)
            shapeDrawn = true
            pathSize = path.size
            pathBounds = floatArrayOf(path.minOf { it.x }, path.minOf { it.y }, path.maxOf { it.x }, path.maxOf { it.y })
        } else if (overlay.layer == AnnotationLayer.Star && overlay.markerDot) {
            // Exakt dieselbe 0.34-Skalierung wie ExportRenderer.drawOverlays' Star-Punkt-Zweig --
            // `overlay.filled` wird hier bewusst IGNORIERT, ein Sternpunkt ist immer gefüllt.
            shapeMode = "dot"
            shapeRadiusPx = (minOf(overlay.size.width, overlay.size.height) * 0.34f).coerceAtLeast(1.5f)
            val path = warpedCirclePath(projection, frame, shapeRadiusPx, refScale, outputScale, circleDevTracker)
            if (path == null) {
                if (probeTag != null) {
                    AppDiagnostics.record(
                        "spherical_export_probe_marker category=$probeTag id=${overlay.id} ${anchorTag()} " +
                            "shapeMode=$shapeMode sourceRadiusPx=${fmt(shapeRadiusPx)} outcome=degenerate_path_null rendererUsed=Spherical360",
                    )
                }
                return MarkerOutcome(shapeDrawn = false, shapeMode = shapeMode)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = withAlpha(overlay.colorArgb, overlay.opacity)
            }
            seamPieces = drawSeamAwarePath(canvas, path, closed = true, paint, imageWidth)
            shapeDrawn = true
            pathSize = path.size
            pathBounds = floatArrayOf(path.minOf { it.x }, path.minOf { it.y }, path.maxOf { it.x }, path.maxOf { it.y })
        } else {
            // markerRing=false UND kein Stern-Punkt -> exakt wie im Flat-Pfad: keine Form gezeichnet
            // (z.B. ein Sternbild-Musterstern, der nur über den Ankerring der Konstellation dargestellt
            // wird -- der ist ohnehin kein eligibles Overlay hier, s. isEligible, dieser Zweig ist eine
            // reine Absicherung für den Fall, dass ein künftiges markerRing=false-Overlay hier landet).
            shapeMode = "none"
            shapeRadiusPx = 0f
        }

        if (probeTag != null) {
            val boundsLog = pathBounds?.let {
                "projectedPathMinX=${fmt(it[0])} projectedPathMinY=${fmt(it[1])} projectedPathMaxX=${fmt(it[2])} projectedPathMaxY=${fmt(it[3])} "
            } ?: ""
            AppDiagnostics.record(
                "spherical_export_probe_marker category=$probeTag id=${overlay.id} ${anchorTag()} " +
                    "shapeMode=$shapeMode sourceRadiusPx=${fmt(shapeRadiusPx)} $boundsLog" +
                    "pathVertexCount=$pathSize seamSplitCount=$seamPieces " +
                    "outcome=${if (shapeDrawn) "warped" else "skipped_by_design"} rendererUsed=Spherical360",
            )
        }

        var nameAttempted = false
        var nameWarped = false
        var leaderAttempted = false
        var leaderWarped = false
        var collisionCandidate = false
        var collisionResolved = false
        var nameOutcome: TextOutcome? = null

        if (overlay.showName && overlay.text.isNotBlank()) {
            collisionCandidate = true
            // Stilquellen 1:1 aus ExportRenderer.drawMarkerName (s. Funktions-KDoc oben).
            val isCallout = overlay.layer == AnnotationLayer.DeepSky ||
                (
                    overlay.layer == null &&
                        (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand)
                    )
            val nameColor = if (isCallout) overlay.nameColorArgb else overlay.colorArgb
            // Punkt 6 (Nutzer-Vorgabe 2026-08-30): overlay.nameOpacityLinked koppelt die Beschriftung
            // zusätzlich an die Objekt-Deckkraft, unabhängig von der Ebene (bisher NUR für Star fest
            // verdrahtet) -- exakt dieselbe Bedingung wie ExportRenderer/Editor (WYSIWYG).
            val nameAlpha = if (overlay.layer == AnnotationLayer.Star || overlay.nameOpacityLinked) overlay.opacity else 1f
            val nameBold = if (overlay.layer == null) overlay.textBold else true
            val fontSizePx = OverlayGeometry.markerNameTextSize(overlay)

            val baseLayout = OverlayGeometry.markerLabelLayout(overlay)
            val baseLeaderLen = hypot(
                (baseLayout.lineEnd.x - baseLayout.edge.x).toDouble(),
                (baseLayout.lineEnd.y - baseLayout.edge.y).toDouble(),
            ).toFloat().coerceAtLeast(1f)

            // Punkt 10: Modus (Normal-Mesh vs. PolarArc-Glyphen) EINMAL vor der Kollisions-Ausweiche
            // entschieden (anhand der Basis-Position, attempt=0) -- verhindert einen Methodenwechsel
            // zwischen den bis zu MAX_LABEL_COLLISION_ATTEMPTS Versuchen DERSELBEN Beschriftung.
            val baseNameAnchor = warpToPixel(
                projection, frame,
                baseLayout.nameAnchor.x * refScale.perPxRight, -baseLayout.nameAnchor.y * refScale.perPxUp,
                null,
            )
            val baseNameDir = baseNameAnchor?.let { projection.pixelToDirection(it.x.toDouble(), it.y.toDouble()) }
            val absLatDeg = baseNameDir?.let { abs(exactPolarLatitudeDeg(it)) } ?: 0.0
            val usePolarArc = absLatDeg >= GLYPH_MODE_THRESHOLD_DEG
            var namePolarFallback = false

            var chosenMesh: TextMeshResult? = null
            var chosenPolar: PolarArcResult? = null
            var chosenPolarPaint: Paint? = null
            var chosenEdgeAnchor: Offset? = null
            var chosenNameAnchor: Offset? = null
            var chosenLayout: OverlayGeometry.MarkerLabel? = null

            for (attempt in 0 until MAX_LABEL_COLLISION_ATTEMPTS) {
                val tryOverlay = if (attempt == 0) overlay else overlay.copy(labelLeaderPx = baseLeaderLen * (1f + attempt * 0.9f))
                val layout = OverlayGeometry.markerLabelLayout(tryOverlay)
                val edgeAnchor = warpToPixel(
                    projection, frame,
                    layout.edge.x * refScale.perPxRight, -layout.edge.y * refScale.perPxUp,
                    null,
                )
                val nameAnchor = warpToPixel(
                    projection, frame,
                    layout.nameAnchor.x * refScale.perPxRight, -layout.nameAnchor.y * refScale.perPxUp,
                    edgeAnchor,
                )
                if (edgeAnchor == null || nameAnchor == null) continue
                if (usePolarArc) {
                    val nameDir = projection.pixelToDirection(nameAnchor.x.toDouble(), nameAnchor.y.toDouble())
                    if (nameDir == null) {
                        namePolarFallback = true
                        continue
                    }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = withAlpha(nameColor, nameAlpha)
                        textAlign = Paint.Align.CENTER
                        typeface = overlay.font.toTypeface(nameBold)
                        textSize = fontSizePx
                    }
                    val polar = computePolarArcGlyphs(overlay.text, nameDir, projection, refScale, paint)
                    if (polar == null) {
                        namePolarFallback = true
                        continue
                    }
                    val box = polarArcBounds(polar, paint)
                    val overlaps = placedLabelBoxes.any { boxesOverlap(it, box) }
                    if (!overlaps || attempt == MAX_LABEL_COLLISION_ATTEMPTS - 1) {
                        chosenPolar = polar
                        chosenPolarPaint = paint
                        chosenEdgeAnchor = edgeAnchor
                        chosenNameAnchor = nameAnchor
                        chosenLayout = layout
                        collisionResolved = !overlaps
                        break
                    }
                } else {
                    val nameFrame = localFrameAt(projection, nameAnchor.x.toDouble(), nameAnchor.y.toDouble()) ?: continue
                    val mesh = computeWarpedTextMesh(
                        overlay.text, nameAnchor, overlay, projection, nameFrame, refScale,
                        rotationDegrees = 0f, fontSizePx = fontSizePx, colorArgb = nameColor, alpha = nameAlpha, bold = nameBold,
                        outputScale = outputScale,
                    ) ?: continue
                    val box = floatArrayOf(mesh.minX, mesh.minY, mesh.maxX, mesh.maxY)
                    val overlaps = placedLabelBoxes.any { boxesOverlap(it, box) }
                    if (!overlaps || attempt == MAX_LABEL_COLLISION_ATTEMPTS - 1) {
                        chosenMesh = mesh
                        chosenEdgeAnchor = edgeAnchor
                        chosenNameAnchor = nameAnchor
                        chosenLayout = layout
                        collisionResolved = !overlaps
                        break
                    } else {
                        mesh.bitmap.recycle()
                    }
                }
            }

            if ((chosenMesh != null || chosenPolar != null) && chosenEdgeAnchor != null && chosenNameAnchor != null && chosenLayout != null) {
                leaderAttempted = true
                val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = withAlpha(overlay.colorArgb, overlay.opacity)
                    strokeWidth = OverlayGeometry.strokeWidth(overlay.strokeWidth) * 0.6f
                }
                val leaderOut = ArrayList<Offset>(4)
                leaderOut += chosenEdgeAnchor
                if (refineArc(
                        projection, frame,
                        chosenLayout.edge.x * refScale.perPxRight, -chosenLayout.edge.y * refScale.perPxUp, chosenEdgeAnchor,
                        chosenLayout.nameAnchor.x * refScale.perPxRight, -chosenLayout.nameAnchor.y * refScale.perPxUp, chosenNameAnchor,
                        0, leaderOut,
                    )
                ) {
                    drawSeamAwarePath(canvas, leaderOut, closed = false, leaderPaint, imageWidth)
                    leaderWarped = true
                }
                nameAttempted = true
                var boxLog = ""
                if (chosenPolar != null && chosenPolarPaint != null) {
                    drawPolarArcText(canvas, chosenPolar, chosenPolarPaint)
                    val box = polarArcBounds(chosenPolar, chosenPolarPaint)
                    placedLabelBoxes += box
                    boxLog = "projectedMeshMinX=${fmt(box[0])} projectedMeshMaxX=${fmt(box[2])} " +
                        "projectedMeshMinY=${fmt(box[1])} projectedMeshMaxY=${fmt(box[3])} "
                    nameOutcome = TextOutcome(
                        true,
                        mode = polarArcDiagnosticMode(absLatDeg),
                        glyphCount = chosenPolar.glyphs.size,
                        arcRadiusDeg = chosenPolar.arcRadiusDeg,
                        latitudeDeg = absLatDeg.toFloat(),
                        readingDirection = chosenPolar.readingDirection,
                    )
                } else if (chosenMesh != null) {
                    drawTextMesh(canvas, chosenMesh, outputScale, ssaaFactor)
                    placedLabelBoxes += floatArrayOf(chosenMesh.minX, chosenMesh.minY, chosenMesh.maxX, chosenMesh.maxY)
                    boxLog = "projectedMeshMinX=${fmt(chosenMesh.minX)} projectedMeshMaxX=${fmt(chosenMesh.maxX)} " +
                        "projectedMeshMinY=${fmt(chosenMesh.minY)} projectedMeshMaxY=${fmt(chosenMesh.maxY)} "
                    nameOutcome = TextOutcome(true, mode = "normal")
                }
                nameWarped = true
                if (probeTag != null) {
                    // Punkt 14: panoramaLatitude/mode/arcRadiusDeg/glyphCount/readingDirection ergänzt --
                    // bewusst OHNE den Namenstext selbst (etablierte Privacy-Konvention dieser Datei, s.
                    // Klassenkommentar "Kein Objektname wird geloggt"); Position+id reichen zur Zuordnung.
                    AppDiagnostics.record(
                        "spherical_export_probe_text category=${probeTag}_name id=${overlay.id} " +
                            "anchorPx=(${fmt(chosenNameAnchor.x)},${fmt(chosenNameAnchor.y)}) " +
                            "sourceFontSizePx=${fmt(fontSizePx)} exportFontSizePx=${fmt(fontSizePx)} $boxLog" +
                            "panoramaLatitude=${fmt(absLatDeg.toFloat())} mode=${nameOutcome?.mode} " +
                            "arcRadiusDeg=${nameOutcome?.arcRadiusDeg?.let { fmt(it) } ?: "n/a"} " +
                            "glyphCount=${nameOutcome?.glyphCount ?: 0} readingDirection=${nameOutcome?.readingDirection ?: "n/a"} " +
                            "collisionStatus=${if (collisionResolved) "resolved" else "unresolved"} " +
                            "outcome=warped rendererUsed=Spherical360",
                    )
                }
            } else {
                if (namePolarFallback) nameOutcome = TextOutcome(false, mode = "polar", exactPoleFallback = true)
                if (probeTag != null) {
                    AppDiagnostics.record(
                        "spherical_export_probe_text category=${probeTag}_name id=${overlay.id} " +
                            "outcome=degenerate_all_attempts_failed rendererUsed=Spherical360",
                    )
                }
            }
        }
        return MarkerOutcome(
            shapeDrawn = shapeDrawn,
            shapeMode = shapeMode,
            segmentCount = pathSize,
            nameAttempted = nameAttempted,
            nameWarped = nameWarped,
            leaderAttempted = leaderAttempted,
            leaderWarped = leaderWarped,
            collisionCandidate = collisionCandidate,
            collisionResolved = collisionResolved,
            nameTextOutcome = nameOutcome,
        )
    }

    // --- Text --------------------------------------------------------------------------------------

    private fun drawWarpedText(
        canvas: Canvas,
        overlay: AnnotationOverlay,
        projection: PanoramaProjection,
        refScale: RefScale,
        outputScale: Float,
        ssaaFactor: Int,
    ): TextOutcome {
        val pivotDir = projection.pixelToDirection(overlay.center.x.toDouble(), overlay.center.y.toDouble())
            ?: return TextOutcome(false)
        val absLatDeg = abs(exactPolarLatitudeDeg(pivotDir))
        if (absLatDeg >= GLYPH_MODE_THRESHOLD_DEG) {
            // Punkt 10: mehrzeiliger Text bekommt (analog zum bisherigen Verhalten für den flachen Fall,
            // s. computeWarpedTextMesh) keine eigene Sonderbehandlung je Zeile -- nur die erste Zeile wird
            // polar platziert, weitere Zeilen sind im PoC-Umfang dieser Runde nicht vorgesehen (Freitext-
            // Overlays nahe Zenit/Nadir sind ein Rand-/nicht Akzeptanztest-Fall, s. Punkt 13).
            val firstLine = overlay.text.substringBefore('\n')
            return drawPolarArcOrFallback(
                canvas, firstLine, pivotDir, projection, refScale,
                fontSizePx = OverlayGeometry.textOverlaySize(overlay),
                colorArgb = overlay.colorArgb, alpha = overlay.opacity, bold = overlay.textBold,
                font = overlay.font, absLatDeg = absLatDeg,
            )
        }
        val frame = localFrameAt(projection, overlay.center.x.toDouble(), overlay.center.y.toDouble())
            ?: return TextOutcome(false)
        val anchor = projection.directionToPixel(frame.normal, null) ?: return TextOutcome(false)
        val mesh = computeWarpedTextMesh(
            overlay.text, anchor, overlay, projection, frame, refScale,
            rotationDegrees = overlay.rotationDegrees,
            fontSizePx = OverlayGeometry.textOverlaySize(overlay),
            colorArgb = overlay.colorArgb,
            alpha = overlay.opacity,
            bold = overlay.textBold,
            outputScale = outputScale,
        ) ?: return TextOutcome(false)
        drawTextMesh(canvas, mesh, outputScale, ssaaFactor)
        return TextOutcome(true, mode = "normal")
    }

    /** Gemeinsamer Punkt-10-Einstiegspunkt für ALLE drei Beschriftungsarten (Freitext, Marker-/DSO-Namen,
     *  Sternbild-Namen -- s. [drawWarpedText]/[drawWarpedCircleMarker]/`ExportRenderer.drawOverlays`):
     *  berechnet [computePolarArcGlyphs] und zeichnet bei Erfolg über [drawPolarArcText]. `null`-Fall
     *  (Punkt 7, exakter Pol) wird als [TextOutcome.exactPoleFallback] zurückgemeldet statt zu zeichnen. */
    private fun drawPolarArcOrFallback(
        canvas: Canvas,
        text: String,
        pivotDir: Vec3,
        projection: PanoramaProjection,
        refScale: RefScale,
        fontSizePx: Float,
        colorArgb: Long,
        alpha: Float,
        bold: Boolean,
        font: OverlayFont,
        absLatDeg: Double,
    ): TextOutcome {
        if (text.isBlank()) return TextOutcome(false)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(colorArgb, alpha)
            textAlign = Paint.Align.CENTER
            typeface = font.toTypeface(bold)
            textSize = fontSizePx
        }
        val result = computePolarArcGlyphs(text, pivotDir, projection, refScale, paint)
            ?: return TextOutcome(false, mode = "polar", exactPoleFallback = true)
        drawPolarArcText(canvas, result, paint)
        return TextOutcome(
            true,
            mode = polarArcDiagnosticMode(absLatDeg),
            glyphCount = result.glyphs.size,
            arcRadiusDeg = result.arcRadiusDeg,
            latitudeDeg = absLatDeg.toFloat(),
            readingDirection = result.readingDirection,
        )
    }

    /** Punkt 10 (Sternbildnamen, s. Abschnitts-Kommentar oben): einziger Einstiegspunkt, den
     *  `ExportRenderer.drawOverlays` (der FLACHE Zeichenpfad -- Sternbild-LINIEN bleiben dort
     *  vollständig unverändert, s. Klassenkommentar/Scope-Hinweis) für einen Sternbildnamen aufruft.
     *  Liefert `null`, wenn [nameAnchor] NICHT in Zenit-/Nadir-Nähe liegt -- der Aufrufer zeichnet dann
     *  GENAU wie bisher flach weiter (kein Verhaltens-Unterschied für die weit überwiegende Mehrheit der
     *  Sternbilder). [text] kommt bereits SPRACHAUFGELÖST vom Aufrufer (Punkt-10-Nebenfund: vorher immer
     *  `germanName`, jetzt `ConstellationPattern.localizedDisplayName(lang)`); [nameAnchor] ist bereits
     *  die fertige Position INKLUSIVE eines eventuellen manuellen Versatzes (Punkt 9,
     *  `AnnotationOverlay.constellationNameOffset`, s. `constellationNameAnchor()`) -- diese Funktion
     *  erzeugt daraus NUR die sphärische Export-DARSTELLUNG, schreibt nichts zurück (harte Architektur-
     *  Vorgabe dieser Datei, s. Klassenkommentar oben). Stilquellen 1:1 aus `ExportRenderer.drawOverlays`s
     *  Konstellations-Namenszweig übernommen (IMMER volle Deckkraft, IMMER fett -- Sternbildnamen haben
     *  keine separate Opazität/kein Bold-Feld). */
    fun drawConstellationName(
        canvas: Canvas,
        overlay: AnnotationOverlay,
        text: String,
        nameAnchor: Offset,
        projection: PanoramaProjection,
        imageWidth: Int,
        imageHeight: Int,
        outputScale: Float,
    ): WarpStats? {
        if (text.isBlank()) return null
        val pivotDir = projection.pixelToDirection(nameAnchor.x.toDouble(), nameAnchor.y.toDouble()) ?: return null
        val absLatDeg = abs(exactPolarLatitudeDeg(pivotDir))
        if (absLatDeg < GLYPH_MODE_THRESHOLD_DEG) return null
        val refScale = referenceScale(projection, imageWidth, imageHeight) ?: return null
        val outcome = drawPolarArcOrFallback(
            canvas, text, pivotDir, projection, refScale,
            fontSizePx = OverlayGeometry.constellationNameTextSize(overlay),
            colorArgb = overlay.colorArgb, alpha = 1f, bold = true, font = overlay.font, absLatDeg = absLatDeg,
        )
        // Punkt 13/14: Akzeptanztest nennt explizit Sternbildnamen nahe Zenit/Nadir -- eigene, kompakte
        // Diagnosezeile (bewusst ohne Namenstext, s. Privacy-Konvention oben) nur für tatsächlich
        // qualifizierende Sternbilder (selten genug, um kein Log-Budget-Problem zu sein).
        AppDiagnostics.record(
            "spherical_export_probe_constellation id=${overlay.id} " +
                "anchorPx=(${fmt(nameAnchor.x)},${fmt(nameAnchor.y)}) panoramaLatitude=${fmt(absLatDeg.toFloat())} " +
                "mode=${polarArcDiagnosticMode(absLatDeg)} arcRadiusDeg=${outcome.arcRadiusDeg?.let { fmt(it) } ?: "n/a"} " +
                "glyphCount=${outcome.glyphCount} readingDirection=${outcome.readingDirection ?: "n/a"} " +
                "outcome=${if (outcome.warped) "warped" else if (outcome.exactPoleFallback) "exact_pole_fallback" else "failed"} " +
                "rendererUsed=Spherical360",
        )
        return applyTextOutcomeToStats(WarpStats(), outcome)
    }

    /** Rendert [text] normal (flach) in ein transparentes Zwischen-Bitmap, spannt darüber ein uniformes
     *  Gitter, projiziert jeden Gitterpunkt über [warpToPixel] (Nutzerrotation VORHER im lokalen,
     *  unverzerrten Bitmap-Raum angewendet, NICHT aus der späteren verzerrten Textur abgeleitet) und
     *  liefert das fertige (noch nicht gezeichnete) Mesh -- s. [TextMeshResult]-KDoc für die Recycle-
     *  Pflicht des Aufrufers. Split von der eigentlichen Zeichenoperation ([drawTextMesh]), damit
     *  [drawWarpedCircleMarker] mehrere Kandidaten-Meshes (Kollisions-Ausweiche) berechnen kann, ohne
     *  bereits verworfene direkt auf den Canvas zu zeichnen. Rasterauflösung wird adaptiv gesucht
     *  (`findMeshResolution`) statt blind maximal fein gewählt. [frame] MUSS an [anchorPixel] selbst
     *  verankert sein (`frame.normal` entspricht per Konstruktion der (u,v)=(0,0)-Bildmitte) -- ein
     *  Aufrufer, dessen Text NICHT am selben Punkt wie eine bereits vorhandene Basis sitzt (z.B. ein
     *  Namens-Label neben einem Marker), muss eine EIGENE, an [anchorPixel] neu abgetastete Basis
     *  übergeben, nicht die des Markers selbst wiederverwenden. [fontSizePx]/[colorArgb]/[alpha]/[bold]
     *  werden EXPLIZIT vom Aufrufer übergeben (nicht mehr intern aus [overlay] hergeleitet) -- ein Marker-
     *  Name braucht andere Stilquellen als ein freistehender Text-Overlay (s. [drawWarpedCircleMarker]-
     *  KDoc), [overlay] wird hier nur noch für `overlay.font` (Schriftfamilie) gelesen. Naht-Behandlung
     *  für Text-Meshes ist bewusst NICHT Teil dieses PoC (s. Plan) -- Testpositionen liegen nicht an der
     *  360°-Naht.
     *
     *  Renderqualität (Nutzer-Vorgabe 2026-08-30 "360°-Export sichtbar pixeliger als normaler Export"):
     *  die MESH-GEOMETRIE (`logicalW`/`logicalH`, an [findMeshResolution]/[buildMeshVerts] übergeben)
     *  bleibt im LOGISCHEN Koordinatenraum von [anchorPixel]/[fontSizePx] -- Vektor-Zeichnungen (Kreise/
     *  Linien) profitieren automatisch verlustfrei von `ExportRenderer.renderBitmap`s
     *  `canvas.scale(outputScale, outputScale)`, das NACH dieser Funktion auf ALLES angewendet wird. Ein
     *  Text-MESH ist dagegen ein RASTERBILD (`Canvas.drawBitmapMesh` kann keine Vektor-Glyphen zeichnen)
     *  -- ohne diesen Fix wurde die Zwischen-Bitmap nur in LOGISCHER Auflösung gerendert und beim
     *  finalen `canvas.scale(...)` verlustbehaftet hochskaliert (sichtbare Pixeligkeit, besonders bei
     *  "Original"-Exporten, wo `outputScale` oft deutlich > 1 ist, s. `ExportRenderer.renderBitmap`s
     *  `overlayCoordScale`-Kommentar). Fix: dieselbe LOGISCHE Fläche wird mit [outputScale]-facher
     *  PIXELDICHTE gerendert (Bitmap-Pixelgröße UND `textPaint.textSize` beide mit [outputScale]
     *  skaliert) -- die Mesh-GEOMETRIE selbst bleibt unverändert (nutzt weiterhin `Bitmap.[0,1]x[0,1]`-
     *  normalisierte Zuordnung, unabhängig von der tatsächlichen Bitmap-Pixelzahl). Gedeckelt auf
     *  [MAX_TEXT_BITMAP_SCALE] als reines Sicherheitsnetz. */
    private fun computeWarpedTextMesh(
        text: String,
        anchorPixel: Offset,
        overlay: AnnotationOverlay,
        projection: PanoramaProjection,
        frame: LocalFrame,
        refScale: RefScale,
        rotationDegrees: Float,
        fontSizePx: Float,
        colorArgb: Long,
        alpha: Float,
        bold: Boolean,
        outputScale: Float,
    ): TextMeshResult? {
        if (text.isBlank()) return null
        val resolvedTypeface = overlay.font.toTypeface(bold)

        // Logische (unskalierte) Textmetrik zuerst -- bestimmt die Mesh-Geometrie, s. Funktions-KDoc.
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = resolvedTypeface
            textSize = fontSizePx
        }
        val lines = text.split("\n")
        val logicalLineHeight = measurePaint.fontSpacing
        val logicalTextWidth = lines.maxOf { measurePaint.measureText(it) }
        val logicalTextHeight = logicalLineHeight * lines.size
        if (logicalTextWidth <= 0f || logicalTextHeight <= 0f) return null
        val logicalPad = fontSizePx * 0.15f
        val logicalW = logicalTextWidth + logicalPad * 2f
        val logicalH = logicalTextHeight + logicalPad * 2f

        // Bitmap-Pixeldichte: dieselbe logische Fläche, aber mit outputScale-facher Auflösung.
        val bitmapScale = outputScale.coerceIn(1f, MAX_TEXT_BITMAP_SCALE)
        val bmpW = (logicalW * bitmapScale).roundToInt().coerceAtLeast(1)
        val bmpH = (logicalH * bitmapScale).roundToInt().coerceAtLeast(1)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(colorArgb, alpha)
            textAlign = Paint.Align.LEFT
            typeface = resolvedTypeface
            textSize = fontSizePx * bitmapScale
        }
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bitmap)
        val pad = logicalPad * bitmapScale
        val lineHeight = logicalLineHeight * bitmapScale
        val textHeight = logicalTextHeight * bitmapScale
        val firstBaseline = pad - textPaint.ascent() + (bmpH - pad * 2f - textHeight) / 2f
        lines.forEachIndexed { i, line -> bmpCanvas.drawText(line, pad, firstBaseline + i * lineHeight, textPaint) }

        val cellsChosen = findMeshResolution(projection, frame, refScale, anchorPixel, logicalW, logicalH, rotationDegrees)
        if (cellsChosen == null) {
            bitmap.recycle()
            return null
        }
        val verts = buildMeshVerts(projection, frame, refScale, anchorPixel, logicalW, logicalH, rotationDegrees, cellsChosen)
        if (verts == null) {
            bitmap.recycle()
            return null
        }
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < verts.size) {
            val x = verts[i]
            val y = verts[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 2
        }
        return TextMeshResult(bitmap, cellsChosen, verts, minX, minY, maxX, maxY)
    }

    /** Zeichnet ein per [computeWarpedTextMesh] berechnetes Mesh und recycelt danach seine Bitmap.
     *
     *  Qualitäts-EXPERIMENT (Nutzer-Vorgabe 2026-08-30, NICHT als Lösung des Polproblems gedacht, s.
     *  Plan-Diskussion): `Canvas.drawBitmapMesh`/`drawVertices` unterstützen laut offizieller Android-
     *  Dokumentation KEIN Antialiasing der Mesh-eigenen Kanten (`Paint#ANTI_ALIAS_FLAG` wird ignoriert) --
     *  das ist unabhängig von der Quellbitmap-Auflösung (die bereits per [outputScale] korrekt
     *  hochauflösend ist) und zeigt sich am stärksten dort, wo benachbarte Mesh-Zellen stark
     *  unterschiedlich verzerrt sind (nahe Zenit/Nadir). [ssaaFactor] > 1 rendert GENAU denselben Mesh-
     *  Aufruf zusätzlich in ein `ssaaFactor`-fach dichteres Zwischenbild (deckt exakt die Mesh-Bounding-
     *  Box ab) und skaliert das Ergebnis GEFILTERT auf die normale Zielgröße herunter -- ein klassisches
     *  Supersampling/Downsampling, das die fehlende Mesh-Kanten-Glättung teilweise kompensiert, OHNE
     *  Geometrie/Größe zu verändern (dieselben `verts`, nur eine andere Zwischenauflösung). `ssaaFactor
     *  <= 1` (Default) verhält sich exakt wie zuvor -- keine Verhaltensänderung für den Normalfall. */
    private fun drawTextMesh(canvas: Canvas, mesh: TextMeshResult, outputScale: Float, ssaaFactor: Int) {
        val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (ssaaFactor <= 1) {
            canvas.drawBitmapMesh(mesh.bitmap, mesh.cells, mesh.cells, mesh.verts, 0, null, 0, meshPaint)
            mesh.bitmap.recycle()
            return
        }
        val logicalW = mesh.maxX - mesh.minX
        val logicalH = mesh.maxY - mesh.minY
        if (logicalW <= 0f || logicalH <= 0f) {
            canvas.drawBitmapMesh(mesh.bitmap, mesh.cells, mesh.cells, mesh.verts, 0, null, 0, meshPaint)
            mesh.bitmap.recycle()
            return
        }
        val superScale = outputScale * ssaaFactor
        val superW = (logicalW * superScale).roundToInt().coerceIn(1, 4096)
        val superH = (logicalH * superScale).roundToInt().coerceIn(1, 4096)
        val superBitmap = Bitmap.createBitmap(superW, superH, Bitmap.Config.ARGB_8888)
        val superCanvas = Canvas(superBitmap)
        // Reihenfolge wichtig: scale VOR translate aufgerufen -> effektiv wird ein Punkt zuerst um
        // (-mesh.minX,-mesh.minY) verschoben (in logische Bbox-lokale Koordinaten), DANACH skaliert
        // (Canvas-Transforms komponieren in Aufrufreihenfolge, wirken aber in umgekehrter Reihenfolge
        // auf einen Punkt an).
        superCanvas.scale(superScale, superScale)
        superCanvas.translate(-mesh.minX, -mesh.minY)
        superCanvas.drawBitmapMesh(mesh.bitmap, mesh.cells, mesh.cells, mesh.verts, 0, null, 0, meshPaint)
        mesh.bitmap.recycle()

        val finalW = (logicalW * outputScale).roundToInt().coerceAtLeast(1)
        val finalH = (logicalH * outputScale).roundToInt().coerceAtLeast(1)
        val downsampled = Bitmap.createScaledBitmap(superBitmap, finalW, finalH, true)
        superBitmap.recycle()
        val dstRect = RectF(mesh.minX, mesh.minY, mesh.maxX, mesh.maxY)
        val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(downsampled, null, dstRect, compositePaint)
        downsampled.recycle()
    }

    /** Bild-lokaler (rotierter) Versatz vom Bitmap-Zentrum -> Bild-Pixel-Position, für einen
     *  Mesh-Gitterpunkt bei (fracX,fracY) in [0,1] relativ zur logischen Textblock-Ecke oben-links.
     *  [logicalW]/[logicalH] sind die LOGISCHEN (unskalierten) Abmessungen, s. [computeWarpedTextMesh]-
     *  KDoc -- NICHT die tatsächliche Bitmap-Pixelgröße. [frame] muss an [anchorPixel] selbst verankert
     *  sein -- (fracX,fracY)=(0.5,0.5) reduziert sich dadurch exakt auf `warpToPixel(...,0,0,...)` =
     *  [anchorPixel] selbst. Nutzt [frame]s TATSÄCHLICHE lokale Achsen-Skala (s. Klassenkommentar
     *  "Korrektur-Runde") statt einer global angenommenen Konstante. */
    private fun meshVertexPixel(
        projection: PanoramaProjection, frame: LocalFrame, refScale: RefScale,
        anchorPixel: Offset, logicalW: Float, logicalH: Float, rotationDegrees: Float,
        fracX: Float, fracY: Float,
    ): Offset? {
        val localX = (fracX - 0.5f) * logicalW
        val localY = (fracY - 0.5f) * logicalH
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val rotX = localX * cosR - localY * sinR
        val rotY = localX * sinR + localY * cosR
        val u = rotX * refScale.perPxRight
        val v = -rotY * refScale.perPxUp
        return warpToPixel(projection, frame, u, v, anchorPixel)
    }

    /** Sucht die kleinste Zweierpotenz-Zellenzahl (Start [MESH_START_CELLS], Deckel [MESH_MAX_CELLS]),
     *  bei der JEDE Zelle "nahezu linear" ist (Nutzer-Vorgabe): der direkt gewarpte Zellen-Mittelpunkt
     *  liegt innerhalb [MESH_TOLERANCE_PX] der bilinearen Interpolation der 4 Eckpunkte. `null`, wenn
     *  irgendeine Eckpunkt-Projektion scheitert (die 4 äußersten logischen Ecken sind bei JEDER
     *  Rasterauflösung Teil des Gitters -- ein Fehlschlag dort ist auflösungsunabhängig, ein erneuter
     *  Versuch mit mehr Zellen würde ihn nicht beheben) -- Overlay wird dann übersprungen statt kaputt
     *  gezeichnet (s. Plan-Abschnitt "Zenit/Nadir als echte Sonderfälle"). */
    private fun findMeshResolution(
        projection: PanoramaProjection, frame: LocalFrame, refScale: RefScale,
        anchorPixel: Offset, logicalW: Float, logicalH: Float, rotationDegrees: Float,
    ): Int? {
        var cells = MESH_START_CELLS
        while (cells <= MESH_MAX_CELLS) {
            val corners = Array(cells + 1) { arrayOfNulls<Offset>(cells + 1) }
            var cornersOk = true
            for (j in 0..cells) {
                for (i in 0..cells) {
                    val p = meshVertexPixel(
                        projection, frame, refScale, anchorPixel, logicalW, logicalH, rotationDegrees,
                        i.toFloat() / cells, j.toFloat() / cells,
                    )
                    corners[j][i] = p
                    if (p == null) cornersOk = false
                }
            }
            if (!cornersOk) return null

            var worst = 0f
            var midpointsOk = true
            for (j in 0 until cells) {
                for (i in 0 until cells) {
                    val tl = corners[j][i]!!
                    val tr = corners[j][i + 1]!!
                    val bl = corners[j + 1][i]!!
                    val br = corners[j + 1][i + 1]!!
                    val bilinear = Offset((tl.x + tr.x + bl.x + br.x) / 4f, (tl.y + tr.y + bl.y + br.y) / 4f)
                    val actual = meshVertexPixel(
                        projection, frame, refScale, anchorPixel, logicalW, logicalH, rotationDegrees,
                        (i + 0.5f) / cells, (j + 0.5f) / cells,
                    )
                    if (actual == null) {
                        midpointsOk = false
                    } else {
                        val dev = hypot((actual.x - bilinear.x).toDouble(), (actual.y - bilinear.y).toDouble()).toFloat()
                        if (dev > worst) worst = dev
                    }
                }
            }
            if (!midpointsOk) return null
            if (worst <= MESH_TOLERANCE_PX) return cells
            cells *= 2
        }
        return null
    }

    private fun buildMeshVerts(
        projection: PanoramaProjection, frame: LocalFrame, refScale: RefScale,
        anchorPixel: Offset, logicalW: Float, logicalH: Float, rotationDegrees: Float, cells: Int,
    ): FloatArray? {
        val verts = FloatArray((cells + 1) * (cells + 1) * 2)
        var idx = 0
        for (j in 0..cells) {
            for (i in 0..cells) {
                val p = meshVertexPixel(
                    projection, frame, refScale, anchorPixel, logicalW, logicalH, rotationDegrees,
                    i.toFloat() / cells, j.toFloat() / cells,
                ) ?: return null
                verts[idx++] = p.x
                verts[idx++] = p.y
            }
        }
        return verts
    }

    // --- Naht (360°) ---------------------------------------------------------------------------------

    /** Zeichnet [points] nahtbewusst über [OverlayGeometry.splitPolylineAtSeam] -- exakt derselbe,
     *  unveränderte Helfer wie beim Gradnetz/den Sternbild-Kanten (s. Klassenkommentar: reine
     *  Wiederverwendung, keine Änderung an der gesperrten `GraticuleRenderer.kt`). [imageWidth] ist die
     *  KOORDINATENRAUM-Breite (s. [drawOverlays]-KDoc) -- NICHT `canvas.width` (das wäre die tatsächliche
     *  Ziel-Bitmap-Pixelbreite, die je nach Export-Skalierung/`overlayCoordScale` abweicht und dadurch
     *  bei jedem Nicht-1:1-Export die falsche Periode für die Naht-Erkennung ergäbe). Bei `closed=true`
     *  (Kreis) wird der erste Punkt zusätzlich ans Ende angehängt, bevor aufgetrennt wird. Liefert die
     *  Anzahl der resultierenden Teilstücke (1 = keine Naht gekreuzt) -- reine Diagnose-Zählung für
     *  `seamSplitCount`, s. Klassenkommentar; ändert nichts am Zeichnen selbst. */
    private fun drawSeamAwarePath(canvas: Canvas, points: List<Offset>, closed: Boolean, paint: Paint, imageWidth: Int): Int {
        if (points.size < 2) return 0
        val full = if (closed) points + points.first() else points
        val pieces = if (imageWidth > 0) OverlayGeometry.splitPolylineAtSeam(full, imageWidth) else listOf(full)
        pieces.forEach { piece ->
            if (piece.size < 2) return@forEach
            val path = Path().apply {
                moveTo(piece[0].x, piece[0].y)
                piece.drop(1).forEach { lineTo(it.x, it.y) }
            }
            canvas.drawPath(path, paint)
        }
        return pieces.size
    }

    private fun withAlpha(argb: Long, opacity: Float): Int {
        val alpha = (((argb shr 24) and 0xFF) * opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        val red = ((argb shr 16) and 0xFF).toInt()
        val green = ((argb shr 8) and 0xFF).toInt()
        val blue = (argb and 0xFF).toInt()
        return Color.argb(alpha, red, green, blue)
    }
}
