package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** Einheitsvektor / 3D-Punkt für die Panorama-Geometrie. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z
    fun length(): Double = sqrt(dot(this))
    fun normalized(): Vec3 {
        val n = length()
        return if (n < 1e-12) this else Vec3(x / n, y / n, z / n)
    }
}

/** 3×3-Matrix (zeilenweise). */
data class Mat3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double,
    val m20: Double, val m21: Double, val m22: Double,
) {
    operator fun times(v: Vec3): Vec3 = Vec3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z,
    )

    operator fun times(o: Mat3): Mat3 = Mat3(
        m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
        m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
        m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
        m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
        m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
        m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
        m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
        m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
        m20 * o.m02 + m21 * o.m12 + m22 * o.m22,
    )

    fun transpose(): Mat3 = Mat3(
        m00, m10, m20,
        m01, m11, m21,
        m02, m12, m22,
    )

    companion object {
        val IDENTITY = Mat3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    }
}

/** RA/Dec (Grad) -> äquatorialer Einheitsvektor. */
fun raDecToVector(raDegrees: Double, decDegrees: Double): Vec3 {
    val ra = raDegrees * PI / 180.0
    val dec = decDegrees * PI / 180.0
    val cosDec = cos(dec)
    return Vec3(cosDec * cos(ra), cosDec * sin(ra), sin(dec))
}

/** Äquatorialer Einheitsvektor -> RA/Dec (Grad), RA in [0,360). */
fun vectorToRaDec(v: Vec3): Pair<Double, Double> {
    val n = v.normalized()
    var ra = atan2(n.y, n.x) * 180.0 / PI
    if (ra < 0.0) ra += 360.0
    val dec = acos(n.z.coerceIn(-1.0, 1.0)) // 0..pi from +z
    return ra to (90.0 - dec * 180.0 / PI)
}

/**
 * Ist [dir] nahe genug an MINDESTENS einem echten Kachel-Anker (Winkelabstand <= [marginRad])?
 * Basis für "ist [dir] noch im plausiblen Bereich dessen, was tatsächlich verankert/gelöst wurde" —
 * für Overlay-Rendering: Sterne/DSOs/Sternbilder weit außerhalb der gelösten Region sollen nicht
 * erscheinen, selbst wenn eine Projektion ihnen rechnerisch trotzdem einen Bild-Pixel zuweisen würde
 * (z.B. eine zylindrische Projektion ohne Längengrad-Grenze, oder ein bei großem Winkel
 * kollabierendes Fisheye-Verzeichnungspolynom). [anchorDirs] leer -> true (fail-open, kein Anker =
 * keine Prüfung möglich).
 *
 * Bewusst NÄCHSTER Anker statt Schwerpunkt+Streuung (frühere Version, 0.22.11): bei großflächiger,
 * unregelmäßiger Abdeckung (z.B. ein Fisheye mit Ankern über einen weiten RA/Dec-Bereich, aber ohne
 * Anker im unteren Bildbereich/Vordergrund) reicht ein Kreis um den EINEN Schwerpunkt bis in die am
 * weitesten entfernte Ecke der Abdeckung — ein Stern, der von JEDEM einzelnen Anker weit entfernt
 * ist (z.B. tief-südliche Sternbilder bei einem nach Norden geneigten Weitwinkel-Foto), lag dadurch
 * fälschlich trotzdem noch "im Radius" (Gerätetest 0.22.11/153: Carina/Vela/Dorado u.a. wurden über
 * dem Vordergrund gezeichnet, obwohl kein einziger Anker in ihrer Nähe lag).
 */
fun isNearAnyAnchor(dir: Vec3, anchorDirs: List<Vec3>, marginRad: Double): Boolean {
    if (anchorDirs.isEmpty()) return true
    val cosMargin = cos(marginRad)
    return anchorDirs.any { it.dot(dir) >= cosMargin }
}

/**
 * Abbildung zwischen Bildpixel und Richtung (Einheitsvektor) im Bild-Kugelsystem.
 * Die optische Achse zeigt in +Z, x = Bild rechts, y = Bild unten.
 */
interface PanoramaProjection {
    fun pixelToDirection(px: Double, py: Double): Vec3?
    fun directionToPixel(dir: Vec3): Offset?
}

/** Projektionsart der globalen Panorama-Lösung (Auto-Wahl nach kleinstem Restfehler).
 *  Mesh = [CorrectedProjection] -- Sonderfall: KEINE feste optische Formel und KEIN eigenständiger
 *  Ersatz für die übrigen 6 Kinds, sondern ein glattes Korrektur-Feld ÜBER dem jeweils besten
 *  starren Modell (s. dortige Klassenbeschreibung). Zwei frühere, verworfene Entwürfe (Git-Historie):
 *  ein eigenständiges Dreiecksnetz OHNE Baseline, und danach eine Baseline-Korrektur, deren
 *  räumliche Zuordnung noch im gnomonischen Tangentialraum statt im Pixel-Raum lag -- beide an
 *  echten Gerätetests bei weiten, dünn gekachelten Panoramen gescheitert bzw. hinter dem starren
 *  Gewinner zurückgeblieben, s. Kommentar an [FisheyeRefiner.fitMesh]. Nimmt NICHT an
 *  [FisheyeRefiner.calibratePanorama]s
 *  Formel-Fit-Schleife teil (dort macht ein Fit nach denselben Parametern/derselben BIC-Logik wie
 *  die anderen 5 Kinds keinen Sinn -- Mesh braucht deren GEWINNER erst als Grundlage), sondern wird
 *  separat, NACH calibratePanorama, über [FisheyeRefiner.fitMesh] aufgebaut und nur bei genügend
 *  Kachel-Kontrollpunkten überhaupt versucht. */
enum class PanoProjectionKind { Fisheye, Stereographic, Equirectangular, Cylindrical, Mercator, Rectilinear, Mesh }

/**
 * Zylindrische Projektionsfamilie für gestitchte Panoramen (bis 360°). Im Pano-Frame:
 * λ = Azimut = atan2(v.y, v.x), φ = Höhe = asin(v.z). x = cx + fx·λ, y = cy + fy·Y(φ) mit
 *   Equirectangular: Y = φ
 *   Cylindrical:     Y = tan φ
 *   Mercator:        Y = asinh(tan φ)   (= ln(tan φ + sec φ))
 * Die Vorzeichen von fx/fy kodieren die Parität (horizontal/vertikal gespiegelt); der Fitter
 * probiert die vier Vorzeichen-Kombinationen. Die Rotation liegt (wie bei Fisheye) in
 * [PanoramaWcsSolution.rotEquToPano].
 */
class CylindricalProjection(
    val cx: Double,
    val cy: Double,
    val fx: Double,
    val fy: Double,
    val kind: PanoProjectionKind,
) : PanoramaProjection {

    private fun yOf(phi: Double): Double = when (kind) {
        PanoProjectionKind.Cylindrical -> tan(phi)
        PanoProjectionKind.Mercator -> asinh(tan(phi))
        else -> phi // Equirectangular (Fisheye nutzt diese Klasse nicht)
    }

    private fun phiOf(y: Double): Double = when (kind) {
        PanoProjectionKind.Cylindrical -> atan(y)
        PanoProjectionKind.Mercator -> atan(sinh(y))
        else -> y
    }

    override fun directionToPixel(dir: Vec3): Offset? {
        val v = dir.normalized()
        val lambda = atan2(v.y, v.x)
        val phi = asin(v.z.coerceIn(-1.0, 1.0))
        // Cyl/Merc divergieren nahe ±90° -> begrenzen (sonst y -> ∞).
        if (kind != PanoProjectionKind.Equirectangular && abs(phi) > MAX_PHI) return null
        val x = cx + fx * lambda
        val y = cy + fy * yOf(phi)
        if (!x.isFinite() || !y.isFinite()) return null
        return Offset(x.toFloat(), y.toFloat())
    }

    override fun pixelToDirection(px: Double, py: Double): Vec3? {
        if (fx == 0.0 || fy == 0.0) return null
        val lambda = (px - cx) / fx
        val phi = phiOf((py - cy) / fy)
        if (!lambda.isFinite() || !phi.isFinite()) return null
        val cosPhi = cos(phi)
        return Vec3(cosPhi * cos(lambda), cosPhi * sin(lambda), sin(phi))
    }

    companion object {
        private const val MAX_PHI = 1.4835 // ~85°
    }
}

/**
 * Fisheye-Modell mit radialer Verzeichnung: Pixelradius r = f·(θ + k1·θ³ + k2·θ⁵),
 * θ = Winkel zur optischen Achse. cx,cy = optische Achse, f = Pixel pro Radiant.
 * k1=k2=0 ergibt das equidistante Modell (Phase 1). Die Radialterme werden in Phase 2
 * aus dem Katalog-Cross-Match gefittet und bügeln die Randverzeichnung aus.
 */
class FisheyeProjection(
    val cx: Double,
    val cy: Double,
    val f: Double,
    val k1: Double = 0.0,
    val k2: Double = 0.0,
    val k3: Double = 0.0,
    // Parität: false = Standard (y unten ↔ +y-Richtung), true = gespiegelte y-Achse.
    // Welche Parität das Bild hat (Kamera schaut nach oben, Spiegelung im Strahlengang),
    // ist a priori unbekannt -> der Bootstrap probiert beide und wählt die bessere.
    val flipY: Boolean = false,
) : PanoramaProjection {

    private val sy: Double = if (flipY) -1.0 else 1.0

    /** Vorwärtsmodell θ -> Pixelradius: r = f·(θ + k1·θ³ + k2·θ⁵ + k3·θ⁷). */
    private fun radiusForTheta(theta: Double): Double =
        f * (theta + k1 * theta.pow(3) + k2 * theta.pow(5) + k3 * theta.pow(7))

    override fun pixelToDirection(px: Double, py: Double): Vec3? {
        val dx = px - cx
        val dy = py - cy
        val r = hypot(dx, dy)
        // θ aus r = f·(θ + k1θ³ + k2θ⁵ + k3θ⁷) per Newton (Start: equidistant θ0 = r/f).
        var theta = r / f
        repeat(12) {
            val gp = f * (1.0 + 3.0 * k1 * theta * theta + 5.0 * k2 * theta.pow(4) + 7.0 * k3 * theta.pow(6))
            if (kotlin.math.abs(gp) < 1e-12) return@repeat
            theta -= (radiusForTheta(theta) - r) / gp
        }
        if (theta < 0.0) theta = 0.0
        if (theta >= PI) return null
        val phi = atan2(sy * dy, dx)
        val sinT = sin(theta)
        return Vec3(sinT * cos(phi), sinT * sin(phi), cos(theta))
    }

    override fun directionToPixel(dir: Vec3): Offset? {
        val v = dir.normalized()
        val theta = acos(v.z.coerceIn(-1.0, 1.0))
        if (theta >= PI - 1e-6) return null // Antipode nicht abbildbar
        val r = radiusForTheta(theta)
        val phi = atan2(v.y, v.x)
        return Offset((cx + r * cos(phi)).toFloat(), (cy + sy * r * sin(phi)).toFloat())
    }
}

/**
 * Stereografische (azimutal-konforme) Projektion: Pixelradius r = 2·f·tan(θ/2), θ = Winkel zur
 * optischen Achse. Geschlossene Inverse θ = 2·atan(r/(2f)). Klassische „little planet"/All-Sky-
 * Projektion (Hugin/PTGui); kann – anders als das equidistante Fisheye-Modell ohne Polynom – auch
 * deutlich über 180° abbilden. cx,cy = optische Achse, f = Skalenfaktor (Pixel pro rad nahe Mitte).
 * flipY = gespiegelte y-Achse (Parität wird vom Fitter probiert).
 */
class StereographicProjection(
    val cx: Double,
    val cy: Double,
    val f: Double,
    val flipY: Boolean = false,
) : PanoramaProjection {

    private val sy: Double = if (flipY) -1.0 else 1.0

    override fun pixelToDirection(px: Double, py: Double): Vec3? {
        val dx = px - cx
        val dy = py - cy
        val r = hypot(dx, dy)
        val theta = 2.0 * atan(r / (2.0 * f))
        if (theta >= PI) return null
        val phi = atan2(sy * dy, dx)
        val sinT = sin(theta)
        return Vec3(sinT * cos(phi), sinT * sin(phi), cos(theta))
    }

    override fun directionToPixel(dir: Vec3): Offset? {
        val v = dir.normalized()
        val theta = acos(v.z.coerceIn(-1.0, 1.0))
        if (theta >= PI - 1e-6) return null // Antipode nicht abbildbar
        val r = 2.0 * f * tan(theta / 2.0)
        val phi = atan2(v.y, v.x)
        return Offset((cx + r * cos(phi)).toFloat(), (cy + sy * r * sin(phi)).toFloat())
    }
}

/**
 * Rectilineare (gnomonische) Projektion: Pixelradius r = f·tan θ, θ = Winkel zur optischen Achse.
 * Das ist das ideale Kamera-/Teleobjektiv-Modell (TAN) – gerade Linien bleiben gerade. Nur für
 * θ < ~90° definiert (tan divergiert am Horizont), daher für normale (nicht Fisheye/Pano) Felder.
 * Inverse θ = atan(r/f). cx,cy = optische Achse, f = Pixel pro rad nahe Mitte, flipY = Parität.
 */
class RectilinearProjection(
    val cx: Double,
    val cy: Double,
    val f: Double,
    val flipY: Boolean = false,
) : PanoramaProjection {

    private val sy: Double = if (flipY) -1.0 else 1.0

    override fun pixelToDirection(px: Double, py: Double): Vec3? {
        val dx = px - cx
        val dy = py - cy
        val r = hypot(dx, dy)
        val theta = atan(r / f)
        if (theta >= MAX_THETA) return null
        val phi = atan2(sy * dy, dx)
        val sinT = sin(theta)
        return Vec3(sinT * cos(phi), sinT * sin(phi), cos(theta))
    }

    override fun directionToPixel(dir: Vec3): Offset? {
        val v = dir.normalized()
        val theta = acos(v.z.coerceIn(-1.0, 1.0))
        if (theta >= MAX_THETA) return null // >~85°: tan divergiert
        val r = f * tan(theta)
        val phi = atan2(v.y, v.x)
        return Offset((cx + r * cos(phi)).toFloat(), (cy + sy * r * sin(phi)).toFloat())
    }

    companion object {
        private const val MAX_THETA = 1.4835 // ~85°
    }
}
