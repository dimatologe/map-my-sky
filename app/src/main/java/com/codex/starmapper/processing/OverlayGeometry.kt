package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.DrawLayer
import com.codex.starmapper.domain.OverlayKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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

    /** Strichbreite für Linien/Formen (Bild-px). */
    fun strokeWidth(rawStrokeWidth: Float): Float =
        (rawStrokeWidth * 2f).coerceAtLeast(2f)

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
     * Abstand vom Zentrum zum TATSÄCHLICHEN Formrand (Ellipse- bzw. Rechteck-Kontur, inkl. Drehung)
     * entlang der Bildschirm-Richtung (dx,dy; Einheitsvektor, y-nach-unten). Ersetzt die frühere
     * Kreis-Näherung (halbe kürzere Kante) -- die ließ die Führungslinie bei länglichen Formen
     * sichtbar INNERHALB der gezeichneten Kontur beginnen statt exakt auf ihr.
     */
    private fun boundaryRadius(overlay: AnnotationOverlay, dx: Float, dy: Float): Float {
        val a = overlay.size.width / 2f
        val b = overlay.size.height / 2f
        // Bildschirm-Richtung zurück ins unrotierte lokale Koordinatensystem der Form drehen --
        // Gegenstück zu drawStyledOval/drawStyledRect, die per rotate(rotationDegrees, center) drehen.
        val rot = Math.toRadians(-overlay.rotationDegrees.toDouble())
        val cosR = cos(rot).toFloat()
        val sinR = sin(rot).toFloat()
        val ux = dx * cosR - dy * sinR
        val uy = dx * sinR + dy * cosR
        return if (overlay.kind == OverlayKind.Rectangle) {
            val denom = max(abs(ux) / a, abs(uy) / b)
            if (denom > 1e-6f) 1f / denom else min(a, b)
        } else {
            val denom = (ux * ux) / (a * a) + (uy * uy) / (b * b)
            if (denom > 1e-6f) 1f / sqrt(denom) else min(a, b)
        }
    }

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
     */
    fun findManualShapeLabelPlacement(shape: AnnotationOverlay, otherOverlays: List<AnnotationOverlay>): LabelHandleDrag? {
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
                var blocked = false
                var j = 0
                while (j < otherOverlays.size) {
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
