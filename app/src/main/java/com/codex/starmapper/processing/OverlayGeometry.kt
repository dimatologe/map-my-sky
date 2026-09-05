package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.DrawLayer
import com.codex.starmapper.domain.OverlayKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Einheitliche Overlay-Geometrie – ALLE Werte in BILD-Pixeln (image space).
 *
 * Editor und Export nutzen exakt dieselben Formeln. Dadurch ist die Vorschau WYSIWYG:
 *  - Im Export werden die Werte direkt verwendet (der Export-Downscale via
 *    `canvas.scale(renderScale)` ist uniform und erhält die Proportionen).
 *  - Im Editor wird jeder Wert mit `viewport.scale` (Bildschirm-px je Bild-px)
 *    multipliziert.
 *
 * Folge: die Editor-Anzeige ist eine uniform vergrößerte Kopie des Exports. Zoomen
 * verändert nur die scheinbare Größe, niemals die Proportionen – der Nutzer sieht bei
 * JEDER Zoomstufe genau das, was der Export liefert (gleich große Ankerringe, gleiche
 * Linien-Lücken, gleiche Schriftgrößen relativ zum Bild).
 *
 * Früher rechnete der Editor Ankerringe/Schrift in konstanten `dp` bzw. mit
 * Bildschirm-Clamps – daher waren Ringe beim Rauszoomen riesig und beim Reinzoomen
 * winzig, und der Export sah anders aus als die Vorschau.
 */
object OverlayGeometry {

    /** Anker-Faktor aus dem Slider (Default 0.045 -> Faktor 1.0). */
    private fun anchorFactor(overlay: AnnotationOverlay): Float =
        (overlay.anchorRadiusRatio / 0.045f).coerceIn(0.4f, 1.8f)

    /**
     * Ankerkreis-Radius (Bild-px). Konstanter Bruchteil der kürzeren Bildkante – damit
     * ALLE Sternbilder gleich große Ankerkreise haben, unabhängig von ihrer Bounding-Box.
     * Nur über den Anker-Slider skalierbar.
     */
    fun constellationAnchorRadius(overlay: AnnotationOverlay, imageMinDim: Float): Float =
        imageMinDim * 0.006f * anchorFactor(overlay)

    /** Strichbreite für Linien/Formen (Bild-px). Untergrenze 2026-08-30 auf Nutzer-Vorgabe deutlich
     *  gesenkt (vorher 2f) -- der alte Boden verhinderte spürbar dünnere Konturen, selbst wenn der
     *  Regler auf sein Minimum gestellt wurde. 0.3f bleibt mit Kantenglättung sichtbar/zeichenbar,
     *  ohne ganz zu verschwinden. */
    fun strokeWidth(rawStrokeWidth: Float): Float =
        (rawStrokeWidth * 2f).coerceAtLeast(0.3f)

    /** Ankerring-Strichbreite (Bild-px). */
    fun anchorStrokeWidth(anchorRadius: Float): Float =
        (anchorRadius * 0.18f).coerceIn(1.2f, 5f)

    /**
     * Lücke Linienende -> Stern (Bild-px).
     * Anker sichtbar: Linie endet außerhalb des Rings.
     * Anker aus: nur kleine Lücke, Linie endet knapp vor dem Stern.
     *
     * Auf der KLEINSTEN Ankerstufe ([anchorRadiusRatio] == Min 0.018) sind die Linien durchgehend
     * VERBUNDEN (Lücke 0); erst beim Vergrößern der Ankerkreise wächst die Lücke ein (dann getrennt
     * wie bisher). [anchorRadiusRatio] Default 0.045 -> grow=1 -> altes Verhalten für Alt-Aufrufer.
     */
    fun constellationLineTrimGap(
        anchorRadius: Float,
        strokeWidth: Float,
        anchorsVisible: Boolean,
        anchorRadiusRatio: Float = 0.045f,
    ): Float {
        val minRatio = 0.018f
        val grow = ((anchorRadiusRatio - minRatio) / (0.045f - minRatio)).coerceIn(0f, 1f)
        if (grow <= 0f) return 0f
        val base = if (anchorsVisible) {
            anchorRadius + anchorRadius * 0.22f + strokeWidth / 2f
        } else {
            anchorRadius * 0.6f + strokeWidth / 2f
        }
        return base * grow
    }

    /** Sternbild-Name-Textgröße (Bild-px), inkl. Font-Größen-Ausgleich (Konsistenz über Schriftarten). */
    fun constellationNameTextSize(overlay: AnnotationOverlay): Float =
        (overlay.nameTextSize * overlay.font.sizeScale()).coerceIn(14f, 240f)

    /** Marker-/Objekt-Name-Textgröße (Bild-px), inkl. Font-Größen-Ausgleich. */
    fun markerNameTextSize(overlay: AnnotationOverlay): Float =
        (overlay.nameTextSize * overlay.font.sizeScale()).coerceIn(14f, 120f)

    /** Ausrichtung des Callout-Namens relativ zum Anker. */
    enum class LabelAlign { Left, Right, Center }

    /** Callout-Geometrie (edge/lineEnd/nameAnchor RELATIV zum Zentrum, Bild-px) + Namens-Ausrichtung. */
    data class MarkerLabel(
        val edge: Offset,
        val lineEnd: Offset,
        val nameAnchor: Offset,
        val align: LabelAlign,
    )

    /**
     * Kern von [boundaryRadius] ohne [AnnotationOverlay]-Hülle -- wiederverwendet von
     * [AstapOverlayMapper.createDeepSkyOverlays]s Namens-Platzierungssuche, wo zum Suchzeitpunkt noch
     * KEIN fertiges [AnnotationOverlay] existiert, nur die rohen Form-Arrays (Größe/Rotation je
     * Kandidat). Vorher nutzte die Suche dort eine simplere, umschließende Kreisnäherung
     * (`max(majPx,minPx)/2`, richtungsUNabhängig) für dieselbe Distanz, die [boundaryRadius] beim
     * TATSÄCHLICHEN Zeichnen richtungsABHÄNGIG berechnet -- bei einer länglichen, gedrehten Ellipse
     * wich die an der Suche geprüfte Position dadurch von der später wirklich gezeichneten ab
     * (Nutzerbefund 2026-08-30: Beschriftungen lagen teils im eigenen Objekt/schnitten dessen Kontur).
     * Jetzt rufen BEIDE Stellen exakt diese eine Funktion auf -- Suche und Render können dadurch
     * strukturell nicht mehr auseinanderlaufen.
     */
    fun boundaryRadiusFor(sizeWidth: Float, sizeHeight: Float, rotationDegrees: Float, isRectangle: Boolean, dx: Float, dy: Float): Float {
        val a = sizeWidth / 2f
        val b = sizeHeight / 2f
        // Bildschirm-Richtung zurück ins unrotierte lokale Koordinatensystem der Form drehen --
        // Gegenstück zu drawStyledOval/drawStyledRect, die per rotate(rotationDegrees, center) drehen.
        val rot = Math.toRadians(-rotationDegrees.toDouble())
        val cosR = cos(rot).toFloat()
        val sinR = sin(rot).toFloat()
        val ux = dx * cosR - dy * sinR
        val uy = dx * sinR + dy * cosR
        return if (isRectangle) {
            val denom = max(abs(ux) / a, abs(uy) / b)
            if (denom > 1e-6f) 1f / denom else min(a, b)
        } else {
            val denom = (ux * ux) / (a * a) + (uy * uy) / (b * b)
            if (denom > 1e-6f) 1f / sqrt(denom) else min(a, b)
        }
    }

    /**
     * Abstand vom Zentrum zum TATSÄCHLICHEN Formrand (Ellipse- bzw. Rechteck-Kontur, inkl. Drehung)
     * entlang der Bildschirm-Richtung (dx,dy; Einheitsvektor, y-nach-unten). Ersetzt die frühere
     * Kreis-Näherung (halbe kürzere Kante) -- die ließ die Führungslinie bei länglichen Formen
     * sichtbar INNERHALB der gezeichneten Kontur beginnen statt exakt auf ihr.
     */
    private fun boundaryRadius(overlay: AnnotationOverlay, dx: Float, dy: Float): Float =
        boundaryRadiusFor(overlay.size.width, overlay.size.height, overlay.rotationDegrees, overlay.kind == OverlayKind.Rectangle, dx, dy)

    /**
     * Callout-Layout eines DSO-Markers: Führungslinie vom Formrand (`edge`) nach außen (`lineEnd`),
     * danach eine Lücke, dann der Name am `nameAnchor`. Richtung aus `overlay.labelAngleDeg` (y-nach-unten).
     * Alle Offsets RELATIV zum Overlay-Zentrum in Bild-px (Editor multipliziert mit viewport.scale).
     */
    fun markerLabelLayout(overlay: AnnotationOverlay): MarkerLabel {
        val ts = markerNameTextSize(overlay)
        val theta = Math.toRadians(overlay.labelAngleDeg.toDouble())
        val dx = cos(theta).toFloat()
        val dy = sin(theta).toFloat()
        val r = boundaryRadius(overlay, dx, dy)
        // Vom Mapper gesetzte (ggf. vergrößerte) Länge hat Vorrang; sonst Standard aus Größe.
        val leader = if (overlay.labelLeaderPx > 0f) overlay.labelLeaderPx else max(ts * 0.5f, r * 0.15f)
        val align = when {
            dx > 0.35f -> LabelAlign.Left
            dx < -0.35f -> LabelAlign.Right
            else -> LabelAlign.Center
        }
        return MarkerLabel(
            edge = Offset(r * dx, r * dy),
            lineEnd = Offset((r + leader) * dx, (r + leader) * dy),
            nameAnchor = Offset((r + leader + MARKER_NAME_GAP) * dx, (r + leader + MARKER_NAME_GAP) * dy),
            align = align,
        )
    }

    /** Ergebnis von [labelHandleFromDrag]: neue Werte für die beiden Overlay-Felder. */
    data class LabelHandleDrag(val labelAngleDeg: Float, val labelLeaderPx: Float)

    /**
     * Algebraische Umkehrung von [markerLabelLayout]: aus einem frei gezogenen Zielpunkt
     * (`dragRelativeToCenter`, Bild-px, RELATIV zum Overlay-Zentrum -- gleiches Koordinatensystem
     * wie die Rückgabewerte von [markerLabelLayout] selbst) werden `labelAngleDeg` (Richtung) und
     * `labelLeaderPx` (Länge der Führungslinie ab Formrand) abgeleitet. Nutzergeste fürs
     * "Schwänzchen": der Zielpunkt entspricht ungefähr [MarkerLabel.lineEnd].
     * `labelLeaderPx` wird auf mindestens 1px geklemmt, damit ein sehr knapp gezogener Punkt nicht
     * in [markerLabelLayout]s "0 = Standardlänge"-Sonderfall zurückfällt.
     */
    fun labelHandleFromDrag(overlay: AnnotationOverlay, dragRelativeToCenter: Offset): LabelHandleDrag {
        val distance = hypot(dragRelativeToCenter.x.toDouble(), dragRelativeToCenter.y.toDouble()).toFloat()
        val angleDeg = Math.toDegrees(
            atan2(dragRelativeToCenter.y.toDouble(), dragRelativeToCenter.x.toDouble()),
        ).toFloat()
        val dx = if (distance > 1e-4f) dragRelativeToCenter.x / distance else 1f
        val dy = if (distance > 1e-4f) dragRelativeToCenter.y / distance else 0f
        val r = boundaryRadius(overlay, dx, dy)
        val leaderPx = (distance - r).coerceAtLeast(1f)
        return LabelHandleDrag(angleDeg, leaderPx)
    }

    /**
     * Bounding-Box (Bild-px, [l, t, r, b]) eines NICHT-Callout-Namens (Sternbild-/Stern-Ebene: feste
     * Position rechts vom Symbol, keine Führungslinie, s. `drawShapeNameLabel`s else-Zweig). Nutzt
     * dieselbe Text-Größenschätzung (Zeichenzahl × Textgröße) wie die DSO-Callout-Kollisionsprüfung in
     * [AstapOverlayMapper.createDeepSkyOverlays], damit beide Systeme gegeneinander geprüft werden
     * können (s. dessen externalObstacleBoxes-Parameter). null, wenn kein Name gezeichnet wird.
     */
    fun nonCalloutNameLabelBoundingBoxOrNull(overlay: AnnotationOverlay): FloatArray? {
        if (!overlay.showName || overlay.text.isBlank()) return null
        val effSize = markerNameTextSize(overlay)
        val textW = overlay.text.length * effSize * 0.55f
        val textH = effSize * 1.1f
        val l = overlay.center.x + overlay.size.width / 2f + MARKER_NAME_GAP
        val t = overlay.center.y - textH / 2f
        return floatArrayOf(l, t, l + textW, t + textH)
    }

    /**
     * Grobe, achsparallele Hindernis-Box (Bild-px, [l,t,r,b]) eines MANUELL platzierten Formen-/
     * Zeichnen-/Text-/Fadenkreuz-Overlays (`layer == null`) -- für die automatische DSO-Namens-
     * platzierung (s. [AstapOverlayMapper.createDeepSkyOverlays]s `externalObstacleBoxes`-Parameter),
     * damit ein Auto-Label eine bereits vorhandene, vom Nutzer gezeichnete Form/Beschriftung nicht
     * verdeckt oder schneidet (Nutzer-Vorgabe 2026-08-30). Rotierte Bounding-Box (4 Eckpunkte um
     * [AnnotationOverlay.center] gedreht, danach achsparallel umschlossen) -- bewusst großzügig
     * (umschließend statt konturgenau, analog zu [nonCalloutNameLabelBoundingBoxOrNull]/
     * `constellationNameObstacleBox`). `null` für Auto-Ebenen (Star/DeepSky/Constellation) -- die
     * registrieren sich bereits über andere Wege als Hindernis, hier ginge es sonst um sich selbst.
     */
    fun userOverlayObstacleBox(overlay: AnnotationOverlay): FloatArray? {
        if (overlay.layer != null) return null
        val hw = overlay.size.width / 2f
        val hh = overlay.size.height / 2f
        if (hw <= 0f || hh <= 0f) return null
        val rad = Math.toRadians(overlay.rotationDegrees.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (signX in floatArrayOf(-1f, 1f)) {
            for (signY in floatArrayOf(-1f, 1f)) {
                val lx = signX * hw
                val ly = signY * hh
                val px = overlay.center.x + (lx * cosR - ly * sinR)
                val py = overlay.center.y + (lx * sinR + ly * cosR)
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
            }
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /**
     * Beim Freihand-Zeichnen gesammelte Bild-Punkte-SEGMENTE (je ein Strich zwischen Zweitfinger-
     * Drücken/-Loslassen) -> normalisierte lokale Punkte je Segment (Anteil von halber Breite/Höhe
     * relativ zu [center], UNROTIERT), s. [AnnotationOverlay.freehandSegments]. [center]/[size] sind
     * die aus der Bounding-Box ALLER Punkte ALLER Segmente abgeleiteten Overlay-Werte.
     */
    fun normalizeFreehandPoints(imageSegments: List<List<Offset>>, center: Offset, size: Size): List<List<Offset>> {
        val halfW = (size.width / 2f).coerceAtLeast(1f)
        val halfH = (size.height / 2f).coerceAtLeast(1f)
        return imageSegments.map { segment ->
            segment.map { p -> Offset((p.x - center.x) / halfW, (p.y - center.y) / halfH) }
        }
    }

    /**
     * Algebraische Umkehrung von [normalizeFreehandPoints]: normalisierte lokale Segmente des Overlays
     * + dessen AKTUELLES center/size/rotationDegrees -> absolute Bild-Segmente, fürs Rendering und den
     * Hit-Test. Gleiche Rotationsrichtung wie `rotate(rotationDegrees, center)` beim Zeichnen von
     * Ellipse/Rechteck (drawStyledOval/drawStyledRect) -- Verschieben/Skalieren/Drehen der Form
     * wirkt sich dadurch ohne jeden Sonderfall auch auf den Freihand-Umriss aus.
     */
    fun denormalizedFreehandPoints(overlay: AnnotationOverlay): List<List<Offset>> {
        val segments = overlay.freehandSegments ?: return emptyList()
        val halfW = overlay.size.width / 2f
        val halfH = overlay.size.height / 2f
        val rot = Math.toRadians(overlay.rotationDegrees.toDouble())
        val cosR = cos(rot).toFloat()
        val sinR = sin(rot).toFloat()
        return segments.map { segment ->
            segment.map { p ->
                val lx = p.x * halfW
                val ly = p.y * halfH
                val rx = lx * cosR - ly * sinR
                val ry = lx * sinR + ly * cosR
                Offset(overlay.center.x + rx, overlay.center.y + ry)
            }
        }
    }

    /**
     * Radiergummi-Modus des Zeichnen-Werkzeugs: entfernt aus [segments] (Bild-px, während der
     * laufenden Zeichnen-Sitzung, VOR jeder Normalisierung) alle Punkte innerhalb [radius] um
     * [center] (aktuelle Stift-Spitze). Reißt ein Loch eine zusammenhängende Punktfolge auseinander,
     * entstehen daraus mehrere kürzere Segmente (wie bei einem echten Radiergummi); auf < 2 Punkte
     * geschrumpfte Reste entfallen ersatzlos. Betrifft NUR die eigene, gerade gezeichnete Linie.
     */
    fun eraseFromFreehandSegments(segments: List<List<Offset>>, center: Offset, radius: Float): List<List<Offset>> {
        val radiusSq = radius * radius
        return segments.flatMap { segment ->
            val result = mutableListOf<List<Offset>>()
            var run = mutableListOf<Offset>()
            for (p in segment) {
                val dx = p.x - center.x
                val dy = p.y - center.y
                if (dx * dx + dy * dy <= radiusSq) {
                    if (run.size >= 2) result += run.toList()
                    run = mutableListOf()
                } else {
                    run += p
                }
            }
            if (run.size >= 2) result += run.toList()
            result
        }
    }

    /**
     * Schließt einen frei gezeichneten Strich automatisch, wenn sein Ende nahe genug am eigenen
     * Anfang liegt (wie ein Lasso-/Pfad-Werkzeug) -- ersetzt NUR den letzten Punkt exakt durch den
     * ersten, damit Füllungen (OverlayKind.Freehand, filled=true) sauber ohne sichtbare Lücke
     * schließen, statt knapp daneben offen zu bleiben. Wirkt nur in UNMITTELBARER Nähe des
     * Anfangspunkts ([snapRadius], Bild-px -- Aufrufer skaliert den gewünschten Bildschirm-Abstand
     * bereits per /viewport.scale, analog zu eraseFromFreehandSegments); normales Zeichnen bleibt
     * sonst unangetastet. Mind. 3 Punkte nötig (2-Punkt-Striche würden sonst zu einem Einzelpunkt
     * kollabieren).
     */
    fun closeFreehandSegmentIfNearStart(segment: List<Offset>, snapRadius: Float): List<Offset> {
        if (segment.size < 3) return segment
        val first = segment.first()
        val last = segment.last()
        return if ((last - first).getDistance() <= snapRadius) segment.dropLast(1) + first else segment
    }

    /**
     * Zerlegt eine Kanten-Polylinie (Bild-px), deren x-Koordinaten nach nahtstellen-sicherer
     * Entfaltung (s. PanoramaProjection.directionToPixel(dir, reference)) auch AUSSERHALB
     * [0, imageWidth) liegen dürfen, an jeder Kreuzung eines Vielfachen von [imageWidth] in 1..n
     * Teilstücke -- jedes Teilstück wird um das passende Vielfache von [imageWidth] zurückverschoben,
     * sodass es nahe [0, imageWidth) liegt. Ohne Kreuzung -> Eingabe unverändert als einziges Element.
     * Macht eine über die 360°-Bildnaht laufende Sternbildkante als ZWEI getrennte, je an ihrer
     * Bildkante endende Teilstücke sichtbar, statt sie zu verwerfen oder als eine lange Querlinie zu
     * zeichnen -- Pendant zu GraticuleRenderer.addLine, aber mit exakt interpoliertem Schnittpunkt UND
     * umgeklapptem Fortsetzungsstück statt nur Abbruch, weil eine 2- bis 15-Punkte-Kante (anders als
     * ein dicht abgetastetes Gradnetz) an der Naht sonst auf null sichtbare Punkte schrumpfen würde.
     */
    fun splitPolylineAtSeam(points: List<Offset>, imageWidth: Int): List<List<Offset>> {
        if (points.size < 2 || imageWidth <= 0) return if (points.isEmpty()) emptyList() else listOf(points)
        val w = imageWidth.toFloat()
        val pieces = mutableListOf<MutableList<Offset>>()
        var current = mutableListOf(points[0])
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (a.x != b.x) {
                val loK = ceil(min(a.x, b.x) / w).toInt()
                val hiK = floor(max(a.x, b.x) / w).toInt()
                val crossings = mutableListOf<Pair<Float, Offset>>()
                for (k in loK..hiK) {
                    val xCross = k * w
                    val t = (xCross - a.x) / (b.x - a.x)
                    if (t > 0f && t < 1f) crossings += t to Offset(xCross, a.y + t * (b.y - a.y))
                }
                crossings.sortBy { it.first }
                for ((_, crossPoint) in crossings) {
                    current.add(crossPoint)
                    pieces += current
                    current = mutableListOf(crossPoint)
                }
            }
            current.add(b)
        }
        pieces += current
        return pieces.map { piece ->
            val avgX = piece.sumOf { it.x.toDouble() }.toFloat() / piece.size
            val shift = floor(avgX / w) * w
            if (shift == 0f) piece else piece.map { Offset(it.x - shift, it.y) }
        }
    }

    /** Ergebnis EINER Nahtkreuzung aus [analyzeSeamCrossings] -- Nutzer-Vorgabe 2026-08-28 ("C1
     *  reicht nicht"): C0 (identischer Punkt) allein beweist nicht, dass eine Linie an der Naht
     *  ohne sichtbaren Knick weiterläuft, nur dass beide Seiten denselben Punkt teilen. Zusätzlich
     *  nötig ist ein Tangenten-/Richtungsvergleich (C1) UNMITTELBAR vor/nach der Kreuzung.
     *  [angleDifferenceDeg] ist `Float.NaN`, wenn auf einer Seite zu wenige Rohpunkte für ein
     *  eigenständiges Tangenten-Fenster vorlagen ([leftWindowPoints]/[rightWindowPoints] < 2, nur am
     *  allerersten/-letzten Stück-Rand möglich) -- IMMER mit `.isFinite()` filtern, bevor der Wert
     *  aggregiert/als "sauber" gewertet wird. [branchByImageWidthBefore]/[branchByModelPeriodBefore]
     *  (und die `After`-Pendants) sind `floor(x/Periode)` für die jeweilige Periodendefinition --
     *  weichen sie für denselben Punkt voneinander ab, ordnen `imageWidth` und `horizontalPeriodPx`
     *  diesen Punkt einem unterschiedlichen "Umlauf" zu (s. [analyzeSeamCrossings]-KDoc, Abschnitt 3).
     *
     *  [angleDifferenceDegNear] (Nutzer-Vorgabe 2026-08-28, Folgeauftrag "warum ist angleDiffDeg an der
     *  Naht ungleich 0"): derselbe C1-Test, aber mit einem FESTEN Fenster von nur 1 Rohpunkt statt
     *  [tangentWindow] -- beantwortet, ob [angleDifferenceDeg] eine ECHTE Naht-Diskontinuität misst oder
     *  ein Artefakt der Fenstergröße selbst ist. Ein Sekanten-Fenster über mehrere Rohpunkte schätzt die
     *  Tangente AM Kreuzungspunkt nur NÄHERUNGSWEISE -- bei echter, glatter Kurvenkrümmung innerhalb des
     *  Fensters (z.B. nahe einem Himmelspol, wo die Projektion stark krümmt) akkumuliert dieser Fehler
     *  näherungsweise LINEAR mit der Fensterbreite (Anzahl Rohpunkte), selbst wenn an der Naht KEIN
     *  echter Knick vorliegt. Bei einem GENUINEN, lokalen Knick (Diskontinuität exakt an der Kreuzung)
     *  ist die gemessene Tangente dagegen für JEDE Fensterbreite ab 1 praktisch identisch, weil die
     *  Kurve auf beiden Seiten der Naht selbst dort schon geradlinig/glatt ist, wo das Fenster beginnt.
     *  Faustregel zur Auswertung: `angleDifferenceDegNear` deutlich KLEINER als [angleDifferenceDeg]
     *  (idealerweise nahe 0) -> reiner Krümmungs-Artefakt der Fenstergröße, keine echte Naht-Störung;
     *  beide etwa GLEICH groß -> echte, fensterunabhängige Diskontinuität an der Naht selbst. */
    data class SeamCrossingCheck(
        val seamY: Float,
        val angleDifferenceDeg: Float,
        val angleDifferenceDegNear: Float,
        val leftSegmentLength: Float,
        val rightSegmentLength: Float,
        val leftWindowPoints: Int,
        val rightWindowPoints: Int,
        val unwrappedXBefore: Float,
        val unwrappedYBefore: Float,
        val unwrappedXAfter: Float,
        val unwrappedYAfter: Float,
        val modelPeriodPx: Double?,
        val branchByImageWidthBefore: Int,
        val branchByImageWidthAfter: Int,
        val branchByModelPeriodBefore: Int?,
        val branchByModelPeriodAfter: Int?,
    )

    /**
     * Findet JEDE Kreuzung eines Vielfachen von [imageWidth] in [points] -- exakt dieselbe
     * Kreuzungs-Suche (loK/hiK/lineare Interpolation) wie [splitPolylineAtSeam] selbst, damit die
     * hier analysierten Kreuzungen 1:1 den tatsächlich beim Zeichnen aufgetrennten entsprechen --
     * und liefert pro Kreuzung sowohl den C0-Beleg (beide Seiten nutzen exakt denselben
     * interpolierten Punkt, `seamY` ist deshalb strukturell für beide Seiten identisch, kein
     * separater `deltaY`-Wert nötig) als auch den C1-Beleg: den Winkelunterschied zwischen der
     * Tangente UNMITTELBAR VOR und UNMITTELBAR NACH der Kreuzung.
     *
     * WICHTIGE KORREKTUR (2026-08-28, Nutzer-Vorgabe "mögliche Tautologie prüfen" -- zutreffend, war
     * ein echter Fehler in der ersten Fassung): die Tangenten dürfen NICHT aus `crossPoint`, `a` und
     * `b` allein gebildet werden -- `crossPoint = a + t*(b-a)` macht `crossPoint-a = t*(b-a)` und
     * `b-crossPoint = (1-t)*(b-a)` ALGEBRAISCH zu zwei positiven Vielfachen DESSELBEN Vektors `b-a`,
     * also IMMER exakt kollinear, unabhängig davon, ob die Kurve als Ganzes irgendwo einen echten
     * Knick hat -- ein tautologischer Test, der zwangsläufig ~0° liefert. Stattdessen wird die
     * Richtung aus je einem eigenständigen FENSTER von bis zu [tangentWindow] Rohpunkten VOR `a` bzw.
     * NACH `b` gebildet (`tangentLeft = a - points[i-1-w]`, `tangentRight = points[i+w] - b`) -- diese
     * beiden Vektoren teilen sich KEINEN Punkt und hängen nicht von `t`/`crossPoint` ab, ein echter
     * Knick zwischen den Fenstern bleibt dadurch sichtbar. Arbeitet bewusst auf [points] SELBST (der
     * unwrapped/kontinuierlichen Rohfolge, VOR jedem Auftrennen/Verschieben) -- dadurch sind alle
     * Fensterpunkte automatisch im selben, konsistenten (nicht periodisch verschobenen)
     * Koordinatenrahmen, ein nachträgliches "Zurückwickeln" ist nicht nötig (reine Translation ändert
     * Form/Tangente einer Kurve nicht, s. [[project_gradnetz_randbeschriftung]] Nachtrag 13). Bekannte
     * Einschränkung: liegen zwei Kreuzungen näher als [tangentWindow] Rohpunkte beieinander, kann das
     * Fenster der einen in den Bereich der anderen hineinreichen -- die Messung bleibt dadurch
     * korrekt (misst weiterhin echte Kurvenform), nur weniger lokal als beabsichtigt.
     *
     * [modelPeriodPx] (optional, `PanoramaProjection.horizontalPeriodPx()` des gefitteten Modells) und
     * die `branchBy...`-Felder beantworten Abschnitt 3 der Nutzer-Vorgabe: ob `imageWidth` und die
     * MODELL-eigene Periode einen Punkt demselben "Umlauf" zuordnen -- reine Beobachtung, keine
     * Aussage darüber, welche Periode "richtiger" ist (das lässt sich aus den Pixeldaten allein nicht
     * entscheiden, nur indirekt über die -- nachweislich funktionierende -- Sternbild-Kontrollgruppe).
     *
     * Generisch (reine `List<Offset>` + `imageWidth`, keine Gradnetz-Abhängigkeit) -- wiederverwendbar
     * für JEDE unwrapped Polylinie, die über die 360°-Bildnaht läuft (Gradnetz UND Sternbild-Kanten
     * als Kontrollgruppe, s. `AppDiagnosticExporter`).
     */
    fun analyzeSeamCrossings(
        points: List<Offset>,
        imageWidth: Int,
        modelPeriodPx: Double? = null,
        tangentWindow: Int = 3,
    ): List<SeamCrossingCheck> {
        if (points.size < 2 || imageWidth <= 0) return emptyList()
        val w = imageWidth.toFloat()
        val results = mutableListOf<SeamCrossingCheck>()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (a.x == b.x) continue
            val loK = ceil(min(a.x, b.x) / w).toInt()
            val hiK = floor(max(a.x, b.x) / w).toInt()
            if (loK > hiK) continue
            for (k in loK..hiK) {
                val xCross = k * w
                val t = (xCross - a.x) / (b.x - a.x)
                if (t <= 0f || t >= 1f) continue
                val seamY = a.y + t * (b.y - a.y)

                val angleDiffDeg = windowedTangentAngleDiffDeg(points, i, tangentWindow)
                val angleDiffDegNear = windowedTangentAngleDiffDeg(points, i, 1)

                val leftStart = max(0, (i - 1) - tangentWindow)
                val rightEnd = min(points.size - 1, i + tangentWindow)
                val leftWindowPoints = (i - 1) - leftStart + 1
                val rightWindowPoints = rightEnd - i + 1
                val leftFarForLen = points[leftStart]
                val rightFarForLen = points[rightEnd]

                fun branch(x: Float, period: Float) = floor(x / period).toInt()
                results += SeamCrossingCheck(
                    seamY = seamY,
                    angleDifferenceDeg = angleDiffDeg,
                    angleDifferenceDegNear = angleDiffDegNear,
                    leftSegmentLength = hypot((a.x - leftFarForLen.x).toDouble(), (a.y - leftFarForLen.y).toDouble()).toFloat(),
                    rightSegmentLength = hypot((rightFarForLen.x - b.x).toDouble(), (rightFarForLen.y - b.y).toDouble()).toFloat(),
                    leftWindowPoints = leftWindowPoints,
                    rightWindowPoints = rightWindowPoints,
                    unwrappedXBefore = a.x,
                    unwrappedYBefore = a.y,
                    unwrappedXAfter = b.x,
                    unwrappedYAfter = b.y,
                    modelPeriodPx = modelPeriodPx,
                    branchByImageWidthBefore = branch(a.x, w),
                    branchByImageWidthAfter = branch(b.x, w),
                    branchByModelPeriodBefore = modelPeriodPx?.let { branch(a.x, it.toFloat()) },
                    branchByModelPeriodAfter = modelPeriodPx?.let { branch(b.x, it.toFloat()) },
                )
            }
        }
        return results
    }

    /** Tangenten-Winkeldifferenz (C1) an der Kreuzung zwischen `points[i-1]` und `points[i]`, aus zwei
     *  eigenständigen Fenstern von je bis zu [window] Rohpunkten VOR/NACH der Kreuzung -- Kern von
     *  [analyzeSeamCrossings], ausgelagert, damit dieselbe Kreuzung mit MEHREREN Fenstergrößen gemessen
     *  werden kann (s. [SeamCrossingCheck.angleDifferenceDegNear]-KDoc). `Float.NaN`, wenn auf einer
     *  Seite zu wenige Rohpunkte für ein eigenständiges Fenster vorlagen. */
    private fun windowedTangentAngleDiffDeg(points: List<Offset>, i: Int, window: Int): Float {
        val a = points[i - 1]
        val b = points[i]
        val leftStart = max(0, (i - 1) - window)
        val leftWindowPoints = (i - 1) - leftStart + 1
        val rightEnd = min(points.size - 1, i + window)
        val rightWindowPoints = rightEnd - i + 1
        if (leftWindowPoints < 2 || rightWindowPoints < 2) return Float.NaN
        val leftFar = points[leftStart]
        val rightFar = points[rightEnd]
        val tangentLeftX = (a.x - leftFar.x).toDouble()
        val tangentLeftY = (a.y - leftFar.y).toDouble()
        val tangentRightX = (rightFar.x - b.x).toDouble()
        val tangentRightY = (rightFar.y - b.y).toDouble()
        val angleLeft = atan2(tangentLeftY, tangentLeftX)
        val angleRight = atan2(tangentRightY, tangentRightX)
        var diff = Math.toDegrees(angleRight - angleLeft)
        diff = ((diff + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return diff.toFloat()
    }

    /**
     * Deckelt das Verhältnis zweier positiver Halbachsen ([sigma1] >= [sigma2] vorausgesetzt) auf
     * höchstens [maxRatio] -- schützt lokale Jacobi-basierte Größen-/Formherleitungen
     * ([AstapOverlayMapper.projectedEllipseAxes], die Tiny-Sky-Verlassen-Umrechnung in StarMapperApp.kt)
     * vor numerisch instabilen/singulären Werten nahe Projektions-Extremstellen (Bildpolen o.ä.) --
     * Nutzerbefund 2026-08-24 ("Verzerrung zum Nadir hin ist eine Katastrophe", Diagnose zeigte reale
     * DSO-Ellipsen mit Seitenverhältnissen weit über 1000:1, z.B. 4404x1,03px). Verteilt einen
     * Überschuss symmetrisch UM DAS GEOMETRISCHE MITTEL (bleibt exakt erhalten) statt einseitig zu
     * kappen -- ein Objekt wird dadurch weder unplausibel riesig NOCH auf einen unsichtbaren
     * Sub-Pixel-Strich zusammengestaucht, seine ungefähre Gesamtfläche bleibt unverändert, nur das
     * Seitenverhältnis wird gezähmt. [maxRatio] ist je Aufrufer bewusst unterschiedlich gewählt (s.
     * dortige Kommentare) -- DSO-Katalogobjekte können selbst schon deutlich elongiert sein (z.B.
     * kantige Galaxien), Nutzer-Zeichnungen erst recht (ein bewusst dünner Pfeil/Strich ist gültiger
     * Inhalt) -- dieselbe Mechanik, aber kein pauschaler, für alle Aufrufer gleicher Wert. Rückgabe
     * (sigma1',sigma2') mit sigma1'>=sigma2' und sigma1'/sigma2' <= [maxRatio]; unverändert, falls das
     * Verhältnis bereits im Rahmen liegt.
     */
    fun clampAxisRatio(sigma1: Float, sigma2: Float, maxRatio: Float): Pair<Float, Float> {
        if (sigma1 <= 0f || sigma2 <= 0f || !sigma1.isFinite() || !sigma2.isFinite()) return sigma1 to sigma2
        if (sigma1 / sigma2 <= maxRatio) return sigma1 to sigma2
        val gm = sqrt(sigma1.toDouble() * sigma2.toDouble())
        val factor = exp(ln(maxRatio.toDouble()) / 2.0)
        return (gm * factor).toFloat() to (gm / factor).toFloat()
    }

    /** Freitext-Höhe/Schriftgröße (Bild-px). Obergrenze großzügig -> per Eck-Resize bis bildgroß. */
    fun textOverlaySize(overlay: AnnotationOverlay): Float =
        overlay.size.height.coerceIn(8f, 20000f)

    /** Abstand des Sternbild-Namens über der Bounding-Box (Bild-px). */
    const val CONSTELLATION_NAME_GAP = 10f

    /** Abstand des Marker-/Objekt-Namens rechts neben dem Symbol (Bild-px). */
    const val MARKER_NAME_GAP = 6f

    /** true für Overlays, deren Name per Führungslinie ("Callout") gezeichnet wird -- DSO-Marker UND
     *  manuell gezeichnete Formen (s. StarMapperApp.kt isCallout). Stern-/Sternbild-Namen laufen über
     *  eine feste Position ohne Linie ([nonCalloutNameLabelBoundingBoxOrNull] bzw. eine eigene
     *  Anker-Berechnung), s. [findManualShapeLabelPlacement]-Kommentar. */
    private fun isCalloutOverlay(overlay: AnnotationOverlay): Boolean =
        overlay.layer == AnnotationLayer.DeepSky ||
            (
                overlay.layer == null &&
                    (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand)
                )

    private fun AnnotationLayer?.toDrawLayer(): DrawLayer = when (this) {
        AnnotationLayer.Constellation -> DrawLayer.Constellation
        AnnotationLayer.Star -> DrawLayer.Star
        AnnotationLayer.DeepSky, null -> DrawLayer.Objects
    }

    /** Gruppiert Overlays nach Ziel-Zeichenschicht (s. [DrawLayer]), STABIL (Reihenfolge innerhalb
     *  einer Schicht bleibt wie in [overlays]). layer==DeepSky UND layer==null (JEDE nutzerplatzierte
     *  Form, auch Text/Freihand, nicht nur die Callout-Formen aus [isCalloutOverlay]) landen BEIDE im
     *  Objects-Bucket -- Nutzerwunsch ("gehört ja im Endeffekt auch zu Objekten"). NICHT verwechseln
     *  mit [isCalloutOverlay] (andere, engere Klassifikation nur für die Namens-Beschriftungsart).
     *  Enthält NIE die Keys MilkyWay/Graticule (das sind keine Overlays). */
    fun groupByDrawLayer(overlays: List<AnnotationOverlay>): Map<DrawLayer, List<AnnotationOverlay>> =
        overlays.groupBy { it.layer.toDrawLayer() }

    /** Aktuelle, ABSOLUTE Bounding-Box (Bild-px, [l, t, r, b]) des Namens eines Callout-Overlays (s.
     *  [isCalloutOverlay]), aus dessen JETZIGEN `labelAngleDeg`/`labelLeaderPx` über [markerLabelLayout]
     *  hergeleitet -- für den Hindernis-Abgleich in [findManualShapeLabelPlacement]. null, wenn kein
     *  Name gezeichnet wird. */
    private fun calloutLabelBoxOrNull(overlay: AnnotationOverlay): FloatArray? {
        if (!overlay.showName || overlay.text.isBlank()) return null
        val layout = markerLabelLayout(overlay)
        val effSize = markerNameTextSize(overlay)
        val textW = overlay.text.length * effSize * 0.55f
        val textH = effSize * 1.1f
        val ax = overlay.center.x + layout.nameAnchor.x
        val ay = overlay.center.y + layout.nameAnchor.y
        val l = when (layout.align) {
            LabelAlign.Left -> ax
            LabelAlign.Right -> ax - textW
            LabelAlign.Center -> ax - textW / 2f
        }
        return floatArrayOf(l, ay - textH / 2f, l + textW, ay + textH / 2f)
    }

    /** Schneidet das Segment (x0,y0)-(x1,y1) den Kreis (cx,cy,radius)? Eigene Kopie von
     *  AstapOverlayMapper.segmentCircleHit -- bewusst NICHT den dortigen, bereits am Gerät geprüften
     *  DSO-Code anfassen/wiederverwenden (Plan 2026-08-20), stattdessen dieselbe kleine, unabhängige
     *  Geometrie-Formel hier separat. */
    private fun segmentCircleHit(x0: Float, y0: Float, x1: Float, y1: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val vx = x1 - x0
        val vy = y1 - y0
        val vv = vx * vx + vy * vy
        val t = if (vv <= 0f) 0f else (((cx - x0) * vx + (cy - y0) * vy) / vv).coerceIn(0f, 1f)
        val px = x0 + t * vx
        val py = y0 + t * vy
        val dx = cx - px
        val dy = cy - py
        return dx * dx + dy * dy < radius * radius
    }

    /** Überlappt das Rechteck (left,top,right,bottom) den Kreis (cx,cy,radius)? Eigene Kopie, s.
     *  [segmentCircleHit]-Kommentar. */
    private fun rectCircleHit(left: Float, top: Float, right: Float, bottom: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val nx = cx.coerceIn(left, right)
        val ny = cy.coerceIn(top, bottom)
        val dx = cx - nx
        val dy = cy - ny
        return dx * dx + dy * dy < radius * radius
    }

    /**
     * Sucht für [shape] (gerade benannte manuelle Form: Ellipse/Rechteck/Freihand) einen
     * kollisionsfreien Führungslinien-Winkel + -Länge, damit Name/Linie weder andere Objektkreise noch
     * bereits platzierte Namens-Boxen kreuzen -- dieselbe Grundidee wie
     * [AstapOverlayMapper.createDeepSkyOverlays]s DSO-Platzierung, aber als EIGENE, unabhängige Kopie
     * (dortiger, bereits am Gerät geprüfter Code bleibt unangetastet, s. Plan 2026-08-20) und mit
     * [boundaryRadius] statt einem einfachen Kreisradius -- exakter für Rechtecke/Freihand-Formen.
     * [otherOverlays] sind alle ANDEREN aktuell sichtbaren Overlays (DSO-/Stern-Marker, andere manuelle
     * Formen) -- werden als Kreis um ihr Zentrum genähert (Linie/Box dürfen sie nicht kreuzen) UND,
     * falls sie gerade einen Namen zeigen, deren aktuelle Namens-Box als zusätzliches Hindernis
     * herangezogen. Gibt `null` zurück, wenn kein kollisionsfreier Platz gefunden wurde -- der Aufrufer
     * fällt dann auf das bisherige Standardverhalten zurück (0°, Standardlänge), nie blockierend.
     *
     * Bewusst NUR bei der ERSTEN Namensvergabe aufgerufen (s. Aufrufstelle in StarMapperApp.kt) -- ein
     * späteres Verschieben/Vergrößern der Form platziert den Namen NICHT automatisch neu, damit eine
     * vom Nutzer per Hand nachjustierte Position erhalten bleibt.
     *
     * [imageWidth]/[imageHeight] (Bild-px): eine Kandidaten-Position gilt nur dann als frei, wenn ihre
     * Namens-Box VOLLSTÄNDIG im Bild liegt -- ohne das fand die Suche bei Formen nahe dem Bildrand
     * (wo kaum andere Objekte als Hindernis existieren) anstandslos eine "kollisionsfreie", aber
     * tatsächlich außerhalb des sichtbaren Bildes liegende Stelle (Nutzerbefund 2026-08-27: Namen
     * landeten für randnahe Objekte oft komplett unsichtbar außerhalb des Bildes).
     */
    fun findManualShapeLabelPlacement(
        shape: AnnotationOverlay,
        otherOverlays: List<AnnotationOverlay>,
        imageWidth: Float,
        imageHeight: Float,
    ): LabelHandleDrag? {
        if (shape.text.isBlank()) return null
        val effSize = markerNameTextSize(shape)
        val textW = shape.text.length * effSize * 0.55f
        val textH = effSize * 1.1f
        val gap = MARKER_NAME_GAP

        val obsCx = FloatArray(otherOverlays.size) { otherOverlays[it].center.x }
        val obsCy = FloatArray(otherOverlays.size) { otherOverlays[it].center.y }
        val obsR = FloatArray(otherOverlays.size) { max(otherOverlays[it].size.width, otherOverlays[it].size.height) / 2f }
        var maxObsR = 0f
        for (r0 in obsR) if (r0 > maxObsR) maxObsR = r0

        val placedBoxes = otherOverlays.mapNotNull { other ->
            if (isCalloutOverlay(other)) calloutLabelBoxOrNull(other) else nonCalloutNameLabelBoundingBoxOrNull(other)
        }

        val candAngles = floatArrayOf(0f, -45f, 45f, -90f, 90f, 180f, -135f, 135f)
        val step = max(textH, 8f)
        var bestAngle = 0f
        var bestLeader = -1f
        var bestDist = Float.MAX_VALUE
        for (angle in candAngles) {
            val theta = Math.toRadians(angle.toDouble())
            val dx = cos(theta).toFloat()
            val dy = sin(theta).toFloat()
            val r = boundaryRadius(shape, dx, dy)
            val baseLeader = max(effSize * 0.5f, r * 0.15f)
            val baseDist = r + baseLeader + gap
            val maxDist = baseDist + maxObsR + textW + textH
            var dist = baseDist
            while (dist <= maxDist) {
                val ax = shape.center.x + dist * dx
                val ay = shape.center.y + dist * dy
                val l = when {
                    dx > 0.35f -> ax
                    dx < -0.35f -> ax - textW
                    else -> ax - textW / 2f
                }
                val rBox = l + textW
                val tBox = ay - textH / 2f
                val bBox = ay + textH / 2f
                val edgeX = shape.center.x + r * dx
                val edgeY = shape.center.y + r * dy
                val lineEndX = shape.center.x + (dist - gap) * dx
                val lineEndY = shape.center.y + (dist - gap) * dy
                var blocked = l < 0f || rBox > imageWidth || tBox < 0f || bBox > imageHeight
                var j = 0
                while (!blocked && j < otherOverlays.size) {
                    val cxj = obsCx[j]; val cyj = obsCy[j]; val rj = obsR[j]
                    if (rectCircleHit(l, tBox, rBox, bBox, cxj, cyj, rj)) { blocked = true; break }
                    val dcx = cxj - shape.center.x; val dcy = cyj - shape.center.y
                    val inside = dcx * dcx + dcy * dcy < rj * rj
                    if (!inside && segmentCircleHit(edgeX, edgeY, lineEndX, lineEndY, cxj, cyj, rj)) {
                        blocked = true; break
                    }
                    j++
                }
                if (!blocked && placedBoxes.none { it[0] < rBox && l < it[2] && it[1] < bBox && tBox < it[3] }) {
                    break
                }
                dist += step
            }
            if (dist <= maxDist && dist < bestDist) {
                bestDist = dist
                bestAngle = angle
                val r = boundaryRadius(shape, dx, dy)
                bestLeader = dist - r - gap
            }
        }
        if (bestLeader <= 0f) return null
        return LabelHandleDrag(bestAngle, bestLeader)
    }
}
