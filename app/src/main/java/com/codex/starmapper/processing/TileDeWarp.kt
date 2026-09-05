package com.codex.starmapper.processing

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * De-Warp: entzerrt eine (am Rand verzeichnete) Solve-Kachel in einen lokal-gnomonischen
 * (TAN) Patch, damit ASTAP/Nova sie lösen können.
 *
 * Kern-Idee: Am Rand einer Allsky-/Fisheye-/Stereografie-Projektion ist der Maßstab radial
 * stark gestaucht und radial != tangential -> ein einzelnes TAN-WCS passt nicht mehr, der Solve
 * scheitert. Aus bereits gelösten Kacheln liegt aber ein grobes Panorama-Modell [model] vor.
 * Damit resampeln wir die Kachel in einen flachen TAN-Patch (viele Sterne UND TAN-Geometrie ->
 * lösbar). Das Modell dient NUR dem Aufbau des Patches; die echte Astrometrie kommt vom Solve
 * des Patches. Die zurückgerechneten Anker (Original-Pixel <-> äquatoriale Richtung) sind
 * deshalb exakt, auch wenn das Modell am Rand nur grob extrapoliert.
 */
object TileDeWarp {

    /** Aufgebauter Patch + Geometrie, um nach dem Solve exakte Anker zurückzurechnen. */
    class Patch(
        val bitmap: Bitmap,
        val model: PanoramaWcsSolution,
        val centerDir: Vec3,
        val east: Vec3,
        val north: Vec3,
        val tanHalf: Double,
        val size: Int,
        val fovDegrees: Float,
        val centerRaDegrees: Double,
        val centerDecDegrees: Double,
    )

    // --- Vec3-Hilfen (der Datentyp hat keine +/-/x-Operatoren) ---
    private fun add(a: Vec3, b: Vec3) = Vec3(a.x + b.x, a.y + b.y, a.z + b.z)
    private fun scale(a: Vec3, s: Double) = Vec3(a.x * s, a.y * s, a.z * s)
    private fun cross(a: Vec3, b: Vec3) = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x,
    )

    /** Bild-Pixel -> äquatoriale Richtung über das grobe Modell. */
    private fun PanoramaWcsSolution.eqDirAt(px: Double, py: Double): Vec3? {
        val local = projection.pixelToDirection(px, py) ?: return null
        return (rotEquToPano.transpose() * local).normalized()
    }

    /** äquatoriale Richtung -> Bild-Pixel über das grobe Modell. */
    private fun PanoramaWcsSolution.pixelForEqDir(dir: Vec3): Offset? =
        projection.directionToPixel(rotEquToPano * dir)

    /** Orthonormale Ost/Nord-Tangentialbasis um [centerDir] (Pol-Sonderfall abgesichert). */
    private fun eastNorthBasis(centerDir: Vec3): Pair<Vec3, Vec3> {
        val poleRef = if (abs(centerDir.z) > 0.999) Vec3(0.0, 1.0, 0.0) else Vec3(0.0, 0.0, 1.0)
        val east = cross(poleRef, centerDir).normalized()
        val north = cross(centerDir, east).normalized()
        return east to north
    }

    /**
     * Kern-Resampling: pro Ausgabepixel eine Richtung im TAN-Tangentialfeld um [centerDir] bilden,
     * über [model] auf einen Quell-Pixel abbilden und bilinear aus der gepolsterten [region]
     * sampeln. Von [buildPatch] genutzt (Kachel-Boundingbox-Solve).
     */
    private fun resamplePatch(
        region: IntArray, regX: Int, regY: Int, regW: Int, regH: Int,
        centerDir: Vec3, east: Vec3, north: Vec3, tanHalf: Double, size: Int,
        model: PanoramaWcsSolution,
    ): IntArray {
        val out = IntArray(size * size)
        val inv = 2.0 / size
        val black = 0xFF000000.toInt()
        for (v in 0 until size) {
            val yy = ((v + 0.5) * inv - 1.0) * tanHalf
            val rowBase = v * size
            for (u in 0 until size) {
                val xx = ((u + 0.5) * inv - 1.0) * tanHalf
                val dir = add(add(centerDir, scale(east, xx)), scale(north, yy)).normalized()
                val p = model.pixelForEqDir(dir)
                out[rowBase + u] = if (p == null) black
                    else sampleBilinear(region, regX, regY, regW, regH, p.x.toDouble(), p.y.toDouble())
            }
        }
        return out
    }

    /**
     * Baut aus der Kachel-Boundingbox [rx,ry,rw,rh] (Original-Pixel) einen entzerrten TAN-Patch.
     * @return Patch oder null, wenn das Modell die Kachel nicht sinnvoll abbilden kann.
     */
    fun buildPatch(
        source: Bitmap,
        rx: Int, ry: Int, rw: Int, rh: Int,
        model: PanoramaWcsSolution,
    ): Patch? {
        val srcW = source.width
        val srcH = source.height
        val cxp = rx + rw / 2.0
        val cyp = ry + rh / 2.0
        val centerDir = model.eqDirAt(cxp, cyp) ?: return null

        // Winkel-Halbgröße aus Ecken + Kantenmitten (max. Trennwinkel zur Zentralrichtung).
        val samples = listOf(
            rx.toDouble() to ry.toDouble(),
            (rx + rw).toDouble() to ry.toDouble(),
            (rx + rw).toDouble() to (ry + rh).toDouble(),
            rx.toDouble() to (ry + rh).toDouble(),
            cxp to ry.toDouble(),
            cxp to (ry + rh).toDouble(),
            rx.toDouble() to cyp,
            (rx + rw).toDouble() to cyp,
        )
        var maxAngle = 0.0
        var valid = 0
        for ((sx, sy) in samples) {
            val d = model.eqDirAt(sx, sy) ?: continue
            valid++
            val ang = acos(centerDir.dot(d).coerceIn(-1.0, 1.0))
            if (ang > maxAngle) maxAngle = ang
        }
        if (valid < 3 || maxAngle <= 1e-4) return null
        val halfAngle = (maxAngle * 1.12).coerceAtMost(Math.toRadians(55.0))
        val tanHalf = tan(halfAngle)
        if (!tanHalf.isFinite() || tanHalf <= 0.0) return null

        // Orthonormale Basis (Ost/Nord) um die Zentralrichtung (Pol-Sonderfall abgesichert).
        val (east, north) = eastNorthBasis(centerDir)

        val size = max(rw, rh).coerceIn(512, 1500)

        // Sample-Region (Boundingbox + 35% Rand) einmalig lesen -> schnelles bilineares Sampling.
        val pad = (max(rw, rh) * 0.35).toInt().coerceAtLeast(8)
        val regX = (rx - pad).coerceIn(0, srcW - 1)
        val regY = (ry - pad).coerceIn(0, srcH - 1)
        val regW = (rw + 2 * pad).coerceAtMost(srcW - regX)
        val regH = (rh + 2 * pad).coerceAtMost(srcH - regY)
        if (regW < 2 || regH < 2) return null
        val region = IntArray(regW * regH)
        source.getPixels(region, 0, regW, regX, regY, regW, regH)

        val out = resamplePatch(region, regX, regY, regW, regH, centerDir, east, north, tanHalf, size, model)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, size, 0, 0, size, size)

        val (ra, dec) = vectorToRaDec(centerDir)
        return Patch(
            bitmap = bmp,
            model = model,
            centerDir = centerDir,
            east = east,
            north = north,
            tanHalf = tanHalf,
            size = size,
            fovDegrees = (2.0 * Math.toDegrees(halfAngle)).toFloat(),
            centerRaDegrees = ra,
            centerDecDegrees = dec,
        )
    }

    /**
     * Rein geometrisches Modell für ein ECHTES 2:1-Equirectangular-360°-Panoramafoto: bildet die
     * eigene, durch die Bildmaße feste Azimut/Höhe<->Pixel-Beziehung ab (Zeile 0 = Zenit, Bildmitte
     * = Horizont, letzte Zeile = Nadir — Standard-Photo-Sphere-Konvention). KEIN Fit, keine
     * Astrometrie nötig — nur Bildbreite/-höhe. `rotEquToPano` bleibt Identität: vor dem Lösen
     * besteht noch kein Bezug zum echten Himmel, "eqDir" meint hier die foto-native Az/El-Richtung.
     */
    fun equirectangularNativeModel(imageWidth: Int, imageHeight: Int): PanoramaWcsSolution {
        val projection = CylindricalProjection(
            cx = imageWidth / 2.0,
            cy = imageHeight / 2.0,
            fx = imageWidth / (2.0 * PI),
            fy = -imageHeight / PI, // Minus: Zeile 0 (oben) = Zenit (phi=+90°).
            kind = PanoProjectionKind.Equirectangular,
        )
        return PanoramaWcsSolution(projection, Mat3.IDENTITY)
    }

    // Zenit-Radius-Grenze der Tiny-Sky-Ganzansicht -- bewusst deutlich unter 180°/den früheren 170°
    // gehalten (Nutzerentscheidung 2026-08-23): die Arbeitsansicht soll zum Kacheln/Objekte-Platzieren
    // ÜBERWIEGEND Himmel zeigen statt (wie zuvor) fast nur Boden/Vordergrund -- betrifft NUR diese
    // In-App-Vorschau, der spätere Export liest immer das unveränderte Original-2:1-Bild. Bei 100°
    // füllt der Himmel (θ 0°-90°) ca. 84% des Scheiben-Radius (~70% der Fläche) statt vormals 8,75%
    // (<1% Fläche). Geteilte Konstante mit [stereographicSkyDiscRadius], damit Ansicht und
    // automatische Zentrierung (s. dortige Verwendung) garantiert zueinander passen.
    private const val TINY_SKY_MAX_THETA_DEG = 100.0

    /**
     * Rein geometrisches Modell für die stereografische 360°-Ganzansicht ("Tiny Sky", Zenit-zentriert):
     * bildet dieselbe foto-native Az/El-Richtung wie [equirectangularNativeModel] auf ein
     * QUADRATISCHES Ausgabebild ab. Konform (Winkel/lokale Sternmuster-Form bleiben erhalten überall,
     * nur der Maßstab variiert) — anders als Equirectangular, dessen Verzeichnung nahe den Polen die
     * FORM der Muster verzerrt, UND wichtig für [convertOverlayGeometry]: die Konformität macht die
     * lokale Rotations-Verdrehung zwischen den beiden Koordinatensystemen an jedem Himmelspunkt
     * wohldefiniert (winkeltreu).
     */
    fun stereographicOverviewModel(outputSize: Int): PanoramaWcsSolution {
        val maxTheta = Math.toRadians(TINY_SKY_MAX_THETA_DEG)
        val f = (outputSize / 2.0) / (2.0 * tan(maxTheta / 2.0))
        // flipY=true (Nutzerbefund 2026-08-24, in Tiny Sky gezeichnete Formen erscheinen nach dem
        // Verlassen "gespiegelt platziert"): von Hand nachgerechnet (Az/El-Vektor an einem Testpunkt,
        // az=0/theta=60°, gegen equirectangularNativeModel(), dessen fy=-imageHeight/π bereits im Code
        // festgelegt ist) -- ohne flipY hat die lokale Pixel-zu-Pixel-Jacobi-Matrix zwischen dieser
        // stereografischen Ansicht und dem äquirektangularen Original an diesem Punkt Determinante -1,
        // also eine ECHTE Spiegelung (nicht nur eine Drehung) -- unsichtbar für rotationssymmetrische
        // Inhalte (DSO-Kreise/-Ellipsen, daher bisher nicht aufgefallen), aber sichtbar für asymmetrische
        // Nutzer-Zeichnungen (Freihandformen), die über convertOverlayGeometryLocal beim Verlassen von
        // Tiny Sky in den nativen Bestand zurückgeschrieben werden. flipY=true macht die Determinante
        // an diesem Punkt +1 (reine Drehung, keine Spiegelung mehr).
        val projection = StereographicProjection(cx = outputSize / 2.0, cy = outputSize / 2.0, f = f, flipY = true)
        return PanoramaWcsSolution(projection, Mat3.IDENTITY)
    }

    /**
     * Radius (Ausgabe-Pixel) der Himmel-Halbkugel (Zenit bis Horizont, θ=90°) innerhalb der Tiny-Sky-
     * Ganzansicht -- für automatisches Ein-/Auszoomen beim Betreten des Modus (s. Verwendung in
     * StarMapperApp.kt), damit die Startansicht die Himmel-Scheibe füllt statt nur zentriert-aber-winzig
     * zu zeigen. Selbe Formel/Konstante wie [stereographicOverviewModel], daher immer konsistent.
     */
    fun stereographicSkyDiscRadius(outputSize: Int): Float {
        val maxTheta = Math.toRadians(TINY_SKY_MAX_THETA_DEG)
        val f = (outputSize / 2.0) / (2.0 * tan(maxTheta / 2.0))
        return (2.0 * f * tan(Math.toRadians(45.0))).toFloat()
    }

    /**
     * Geometrie der periodischen 360°-Naht INNERHALB der Tiny-Sky-Ansicht (Nutzer-Auftrag 2026-09-03:
     * "Nicht einfach x = width/2 ... hardcoden ... dieselbe Koordinatenlogik verwenden, die auch der
     * TinySky-Transformation zugrunde liegt").
     *
     * WICHTIG -- die Naht ist in dieser Ansicht KEINE senkrechte Linie. Herleitung, direkt aus den
     * beiden hier definierten Modellen:
     *  - [equirectangularNativeModel] bildet mit `x = W/2 + W/(2π) * atan2(v.y, v.x)` ab. Der
     *    atan2-Astwechsel (λ = ±π) liegt also bei `x = 0` UND `x = W`, den beiden BILDKANTEN des
     *    2:1-Panoramas -- das ist die periodische Naht.
     *  - Diese Richtungen erfüllen `v.y == 0, v.x < 0`. [stereographicOverviewModel] bildet sie mit
     *    `φ = atan2(v.y, v.x) = π`, also `cos φ = -1`, `sin φ = 0` ab -> `x = S/2 - r`, `y = S/2`.
     * Die Naht ist damit ein WAAGERECHTER Halbstrahl auf halber Höhe, vom Scheibenmittelpunkt
     * (Zenit, θ=0, r=0) nach LINKS bis zum Scheibenrand (θ = TINY_SKY_MAX_THETA_DEG, r = S/2, also
     * exakt x = 0). Die beiden "Seiten" der Naht sind folglich OBEN und UNTEN, nicht links/rechts.
     *
     * Berechnet wird sie hier trotzdem nicht per Formel, sondern durch ABTASTEN der nativen Bildkante
     * (`x = 0`, volle Bildhöhe) durch dieselbe Projektionskette, die auch [buildStereographicOverview]
     * benutzt -- änderte sich eine der beiden Projektionen, wanderte die Linie automatisch mit.
     *
     * [maxAngularErrorDeg]/[maxPixelErrorPx] beantworten zusätzlich die Frage, ob die Naht mathematisch
     * exakt geschlossen ist: verglichen wird für dieselben Bildhöhen die Richtung bei `x = 0` gegen die
     * bei `x = nativeWidth` (Winkelabstand) und deren Tiny-Sky-Bildpunkte (Pixelabstand). Beides muss
     * praktisch 0 sein; ein sichtbarer Helligkeits-/Stitching-Unterschied im Foto selbst ist davon
     * unabhängig und KEIN Koordinatenfehler.
     *
     * `null`, wenn die Kette für dieses Bild keine brauchbare Naht liefert (unplausible Maße).
     */
    data class TinySkySeam(
        val y: Float,
        val minX: Float,
        val maxX: Float,
        val maxAngularErrorDeg: Double,
        val maxPixelErrorPx: Double,
        val periodPx: Double,
    )

    fun tinySkySeamGeometry(outputSize: Int, nativeWidth: Int, nativeHeight: Int): TinySkySeam? {
        if (outputSize <= 0 || nativeWidth <= 0 || nativeHeight <= 0) return null
        val overview = stereographicOverviewModel(outputSize).projection
        val native = equirectangularNativeModel(nativeWidth, nativeHeight).projection
        val discRadius = stereographicSkyDiscRadius(outputSize)
        val cx = outputSize / 2.0
        val cy = outputSize / 2.0
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var sumY = 0.0
        var count = 0
        var maxAngularErrorDeg = 0.0
        var maxPixelErrorPx = 0.0
        val steps = 256
        for (i in 0..steps) {
            val py = nativeHeight.toDouble() * i / steps
            val dirLeft = native.pixelToDirection(0.0, py) ?: continue
            val pLeft = overview.directionToPixel(dirLeft) ?: continue
            // Kontinuitätsmessung: dieselbe Bildhöhe an der GEGENÜBERLIEGENDEN Kante.
            native.pixelToDirection(nativeWidth.toDouble(), py)?.let { dirRight ->
                val dot = (dirLeft.x * dirRight.x + dirLeft.y * dirRight.y + dirLeft.z * dirRight.z)
                    .coerceIn(-1.0, 1.0)
                maxAngularErrorDeg = max(maxAngularErrorDeg, Math.toDegrees(acos(dot)))
                overview.directionToPixel(dirRight)?.let { pRight ->
                    maxPixelErrorPx = max(
                        maxPixelErrorPx,
                        sqrt(
                            (pLeft.x - pRight.x).toDouble() * (pLeft.x - pRight.x) +
                                (pLeft.y - pRight.y).toDouble() * (pLeft.y - pRight.y),
                        ),
                    )
                }
            }
            // Nur der innerhalb der sichtbaren Himmel-Scheibe liegende Teil wird gezeichnet.
            val r = sqrt((pLeft.x - cx) * (pLeft.x - cx) + (pLeft.y - cy) * (pLeft.y - cy))
            if (r > discRadius) continue
            minX = kotlin.math.min(minX, pLeft.x.toDouble())
            maxX = max(maxX, pLeft.x.toDouble())
            sumY += pLeft.y.toDouble()
            count++
        }
        if (count < 2 || minX > maxX) return null
        return TinySkySeam(
            y = (sumY / count).toFloat(),
            minX = minX.toFloat(),
            maxX = maxX.toFloat(),
            maxAngularErrorDeg = maxAngularErrorDeg,
            maxPixelErrorPx = maxPixelErrorPx,
            // Volle Azimut-Umrundung in nativen Bild-Pixeln -- bei equirectangularNativeModel per
            // Konstruktion exakt die Bildbreite (fx = W/2π -> 2π*fx = W).
            periodPx = equirectangularNativeModel(nativeWidth, nativeHeight)
                .projection.horizontalPeriodPx() ?: nativeWidth.toDouble(),
        )
    }

    enum class TinySkySeamSide { Above, Below }

    /** Ergebnis von [tinySkySeamSnap]; [deltaY] ist die nötige Verschiebung der Kachel in Y (Bild-px). */
    data class TinySkySeamSnap(
        val deltaY: Float,
        val side: TinySkySeamSide,
        val overlapAbovePx: Float,
        val overlapBelowPx: Float,
    )

    /**
     * Prüft, ob die achsenparallele Hüllbox einer Solve-Kachel die 360°-Naht ([seam]) überdeckt, und
     * liefert die Verschiebung, die sie KOMPLETT auf EINE Seite bringt (Nutzer-Vorgabe 2026-09-03:
     * "NICHT die Kachel teilen. NICHT über die 360°-Naht wrappen. Die Größe und [die Position entlang
     * der Naht] bleiben unverändert").
     *
     * Da die Naht in der Tiny-Sky-Ansicht ein WAAGERECHTER Halbstrahl ist (s. [tinySkySeamGeometry]),
     * sind die beiden Seiten OBEN und UNTEN -- verschoben wird deshalb in Y, nicht in X. Gewählt wird
     * die Seite mit dem GRÖSSEREN Kachelanteil; bei exakt gleichem Anteil entscheidet die Lage des
     * Kachelmittelpunkts (deterministisch, kein Flackern -- zusätzlich wird ohnehin nur beim Loslassen
     * geschnappt, nicht pro Zieh-Frame).
     *
     * `null` = Kachel schneidet die Naht nicht (auch: sie liegt komplett rechts vom Scheibenmittelpunkt,
     * wo der Halbstrahl gar nicht existiert) -> nichts zu tun, Kachel bleibt unverändert.
     */
    fun tinySkySeamSnap(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        centerY: Float,
        seam: TinySkySeam,
        safetyMarginPx: Float,
    ): TinySkySeamSnap? {
        val straddlesY = top < seam.y && bottom > seam.y
        val overlapsSeamX = left < seam.maxX && right > seam.minX
        if (!straddlesY || !overlapsSeamX) return null
        val overlapAbove = seam.y - top
        val overlapBelow = bottom - seam.y
        val side = when {
            overlapAbove > overlapBelow -> TinySkySeamSide.Above
            overlapBelow > overlapAbove -> TinySkySeamSide.Below
            centerY <= seam.y -> TinySkySeamSide.Above
            else -> TinySkySeamSide.Below
        }
        val margin = max(0f, safetyMarginPx)
        val deltaY = if (side == TinySkySeamSide.Above) {
            (seam.y - margin) - bottom
        } else {
            (seam.y + margin) - top
        }
        return TinySkySeamSnap(deltaY, side, overlapAbove, overlapBelow)
    }

    /**
     * Ausgabegröße für [buildStereographicOverview], sodass die Tiny-Sky-Ansicht am Zenit (Bildmitte)
     * MINDESTENS dieselbe Pixel-pro-Grad-Dichte erreicht wie das äquirektangulare Quellbild -- Nutzer-
     * Vorgabe (2026-08-24): "die Projektion vom Tiny Sky [soll] der Originalauflösung entspreche[n]".
     * Ein fester Wert (vorher 2000, unabhängig von der Quellauflösung) unterlief das je nach Foto
     * unterschiedlich stark: bei einem 6500px breiten Bild lag die Zenit-Dichte bei nur ~40% der
     * Quelle -- genau dort, wo die meisten Kacheln landen -- und ließ schwächere Sterne für den Löser
     * untergehen.
     *
     * Herleitung: bei Equirectangular ist die Dichte überall exakt `sourceWidth / 360°` (linear in
     * beiden Achsen). Die stereografische Radial-Dichte `dr/dθ` bei θ=0 (Zenit) ist `f` (Pixel/Radiant,
     * mit `f` wie in [stereographicOverviewModel]) und wächst mit `sec²(θ/2)` nach außen -- der Zenit
     * ist also IMMER die dichteste (dünnste Auflösung) Stelle der Scheibe; matcht man dort die
     * Quelldichte, ist JEDE andere Stelle der Scheibe mindestens so hoch aufgelöst wie die Quelle.
     * Auflösen von `f = sourceWidth/(2π)` nach `outputSize` (über dieselbe `f`-Formel wie
     * [stereographicOverviewModel]) ergibt die Formel unten.
     *
     * Sicherheitsdeckel: das Quellbild selbst ist bereits beim Laden auf `MAX_DISPLAY_DIMENSION_PX`
     * (6500, s. StarMapperApp.kt) begrenzt -- bei dieser Breite liefert die Formel ~4930, deutlich
     * unter dem Deckel. Der Deckel greift nur defensiv (z. B. falls die Lade-Grenze künftig steigt),
     * nicht im normalen Betrieb.
     */
    fun stereographicOutputSizeForSource(sourceWidth: Int): Int {
        val maxTheta = Math.toRadians(TINY_SKY_MAX_THETA_DEG)
        val ideal = ceil((2.0 * sourceWidth / PI) * tan(maxTheta / 2.0)).toInt()
        return ideal.coerceIn(512, 6000)
    }

    /**
     * Baut EINMALIG eine stereografische Ganzbild-Vorschau ("Tiny Sky") des kompletten 2:1-Panoramas
     * (siehe [stereographicOverviewModel]) — kein Live-Rendering, wird nur beim Umschalten aufgerufen.
     * Liest die Quelle DIREKT in ihrer vollen Auflösung (kein Vorab-Downscale mehr — das war die
     * Ursache der Verpixelung beim vorigen Anlauf); da dies nur EIN einmaliger Render ist statt eines
     * Live-Loops, sind ein paar Sekunden Rechenzeit für die höhere Qualität akzeptabel. [checkCancelled]
     * wird einmal pro Ausgabezeile aufgerufen und sollte bei Abbruch eine `CancellationException` werfen.
     */
    fun buildStereographicOverview(
        source: Bitmap,
        outputSize: Int,
        checkCancelled: () -> Unit = {},
    ): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val srcPixels = IntArray(srcW * srcH)
        source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val nativeModel = equirectangularNativeModel(srcW, srcH)
        val overviewModel = stereographicOverviewModel(outputSize)
        val out = IntArray(outputSize * outputSize)
        val black = 0xFF000000.toInt()
        for (py in 0 until outputSize) {
            checkCancelled()
            val rowBase = py * outputSize
            for (px in 0 until outputSize) {
                val dir = overviewModel.projection.pixelToDirection(px + 0.5, py + 0.5)
                out[rowBase + px] = if (dir == null) {
                    black
                } else {
                    val src = nativeModel.projection.directionToPixel(dir)
                    if (src == null) black else sampleBilinear(srcPixels, 0, 0, srcW, srcH, src.x.toDouble(), src.y.toDouble(), wrapX = true)
                }
            }
        }
        val bmp = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, outputSize, 0, 0, outputSize, outputSize)
        return bmp
    }

    /** Positions-Hinweis (Himmelsmitte + Suchradius) für einen ROHEN Kachel-Solve. */
    class Hint(
        val centerRaDegrees: Double,
        val centerDecDegrees: Double,
        val radiusDegrees: Double,
    )

    /**
     * Schätzt aus dem groben [model] (aus bereits gelösten Kacheln) Himmelsmitte + Suchradius der
     * Kachel-Boundingbox [rx,ry,rw,rh], damit Nova NICHT blind lösen muss (schneller/robuster).
     * Rechnet NUR das Modell aus (kein Resampling) -> unabhängig von De-Warp; das rohe Bild behält
     * volle Sternqualität. Radius = halbe Kachel-Diagonale (Winkel) * 1.5, begrenzt auf 12..45 Grad
     * (großzügig, damit ein leicht ungenaues Modell die wahre Position nicht ausschließt).
     * [anchorDirs] sind die (spärlichen) Ankerrichtungen, aus denen [model] gefittet wurde — falls
     * angegeben, wird zusätzlich geprüft, ob die Ziel-Kachel weit außerhalb der Streuung dieser
     * Anker liegt (dünnes Ankerset + große Extrapolationsdistanz = Rotation dorthin praktisch
     * unterbestimmt). In dem Fall lieber null (blind lösen) als ein selbstbewusst-falscher enger
     * Suchradius, der den Solver dauerhaft am falschen Himmelsfleck suchen lässt.
     * @return Hint oder null, wenn das Modell die Kachel nicht sinnvoll abbilden kann/darf.
     */
    fun predictHint(
        model: PanoramaWcsSolution,
        rx: Int, ry: Int, rw: Int, rh: Int,
        anchorDirs: List<Vec3> = emptyList(),
    ): Hint? {
        val cxp = rx + rw / 2.0
        val cyp = ry + rh / 2.0
        val centerDir = model.eqDirAt(cxp, cyp) ?: return null
        val corners = listOf(
            rx.toDouble() to ry.toDouble(),
            (rx + rw).toDouble() to ry.toDouble(),
            (rx + rw).toDouble() to (ry + rh).toDouble(),
            rx.toDouble() to (ry + rh).toDouble(),
        )
        var maxAngle = 0.0
        var valid = 0
        for ((sx, sy) in corners) {
            val d = model.eqDirAt(sx, sy) ?: continue
            valid++
            val ang = acos(centerDir.dot(d).coerceIn(-1.0, 1.0))
            if (ang > maxAngle) maxAngle = ang
        }
        if (valid < 3 || maxAngle <= 1e-4) return null
        // Extrapolations-Sicherheit: nur vertrauen, wenn die Ziel-Kachel nicht deutlich weiter weg
        // liegt als die Anker selbst je vom eigenen Schwerpunkt streuen. KEINE Anker-Anzahl-Grenze
        // (frueher "< 30"): Ankeranzahl ist kein Ersatz fuer tatsaechliche Streuung — 36 eng geklumpte
        // Anker (z.B. 4 Kacheln dicht beieinander) sind fuer eine weit entfernte Kachel nicht
        // vertrauenswuerdiger als 9 (Geraete-Beleg 2026-07-22: 4 geklumpte Kacheln, 36 Anker, Hinweis
        // fuer eine entfernte Kachel lag ~40-50° Deklination daneben, weil diese Pruefung ab 30 Ankern
        // uebersprungen wurde). Die geometrische Pruefung selbst skaliert schon richtig mit echter
        // Streuung (gut verteilte Anker -> großer maxSpread -> seltener Reject).
        if (anchorDirs.isNotEmpty()) {
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            for (d in anchorDirs) {
                sx += d.x; sy += d.y; sz += d.z
            }
            val norm = sqrt(sx * sx + sy * sy + sz * sz)
            if (norm > 1e-9) {
                val centroid = Vec3(sx / norm, sy / norm, sz / norm)
                var maxSpread = 0.0
                for (d in anchorDirs) {
                    val a = acos(centroid.dot(d).coerceIn(-1.0, 1.0))
                    if (a > maxSpread) maxSpread = a
                }
                val distToTarget = acos(centroid.dot(centerDir).coerceIn(-1.0, 1.0))
                val safeDistance = maxSpread + Math.toRadians(20.0)
                if (distToTarget > safeDistance) return null
            }
        }
        val radiusDeg = (Math.toDegrees(maxAngle) * 1.5).coerceIn(12.0, 45.0)
        val (ra, dec) = vectorToRaDec(centerDir)
        return Hint(ra, dec, radiusDeg)
    }

    /**
     * Wie [predictHint], aber die Quelle ist nicht ein aus ALLEN bisher gelösten Kacheln neu
     * gefittetes Gesamtmodell, sondern die bereits bewiesene WCS EINER einzelnen, nahegelegenen
     * Kachel — robuster als ein Fit aus geometrisch engen/wenigen Kacheln, der leicht einen
     * schlechten RMS bekommt und so das RMS-Gate von [predictHint] durchfallen lässt, obwohl eine
     * Nachbar-Kachel längst präzise gelöst ist (Nutzerbefund 2026-07-30: Kacheln 1-3 in einer Reihe
     * platziert, Kachel 4 nahe Kachel 1 bekam keinen Hinweis). Reine CD-Matrix+Gnomonik-
     * Extrapolation (imageToSky hat keine eigene Divergenz-Prüfung wie die Vorwärtsrichtung) — für
     * einen groben Such-Hinweis ausreichend; radiusDeg-Formel identisch zu [predictHint].
     * [rx],[ry],[rw],[rh] (Ziel-Kachel) sind wie bei [predictHint] GLOBALE Bildkoordinaten;
     * [tileOffsetX]/[tileOffsetY]/[tileHeight] verorten die Quell-Kachel [tileWcs] im selben System.
     */
    fun predictHintFromTile(
        tileWcs: WcsSolution,
        tileOffsetX: Int, tileOffsetY: Int, tileHeight: Int,
        rx: Int, ry: Int, rw: Int, rh: Int,
    ): Hint? {
        fun dirAt(gx: Double, gy: Double): Vec3 {
            val sky = tileWcs.imageToSky(gx - tileOffsetX, gy - tileOffsetY, tileHeight)
            return raDecToVector(sky.raDegrees.toDouble(), sky.decDegrees.toDouble())
        }
        val cxp = rx + rw / 2.0
        val cyp = ry + rh / 2.0
        val centerDir = dirAt(cxp, cyp)
        val corners = listOf(
            rx.toDouble() to ry.toDouble(),
            (rx + rw).toDouble() to ry.toDouble(),
            (rx + rw).toDouble() to (ry + rh).toDouble(),
            rx.toDouble() to (ry + rh).toDouble(),
        )
        var maxAngle = 0.0
        for ((sx, sy) in corners) {
            val ang = acos(centerDir.dot(dirAt(sx, sy)).coerceIn(-1.0, 1.0))
            if (ang > maxAngle) maxAngle = ang
        }
        if (maxAngle <= 1e-4) return null
        val radiusDeg = (Math.toDegrees(maxAngle) * 1.5).coerceIn(12.0, 45.0)
        val (ra, dec) = vectorToRaDec(centerDir)
        return Hint(ra, dec, radiusDeg)
    }

    /**
     * Rechnet nach dem Patch-Solve exakte Anker (Original-Pixel <-> äquatoriale Richtung) zurück:
     * Für ein Gitter von Patch-Pixeln liefert dieselbe Abbildung wie beim Aufbau den Original-Pixel,
     * und das frisch gelöste [patchWcs] den wahren Himmel.
     */
    fun anchorsFor(
        patch: Patch,
        patchWcs: WcsSolution,
        srcWidth: Int,
        srcHeight: Int,
        grid: Int = 5,
    ): List<Pair<Offset, Vec3>> {
        val result = ArrayList<Pair<Offset, Vec3>>(grid * grid)
        val size = patch.size
        for (gy in 1..grid) {
            val v = size * gy.toDouble() / (grid + 1)
            val yy = (v / size * 2.0 - 1.0) * patch.tanHalf
            for (gx in 1..grid) {
                val u = size * gx.toDouble() / (grid + 1)
                val xx = (u / size * 2.0 - 1.0) * patch.tanHalf
                val dir = add(add(patch.centerDir, scale(patch.east, xx)), scale(patch.north, yy)).normalized()
                val src = patch.model.pixelForEqDir(dir) ?: continue
                if (src.x < 0f || src.x > srcWidth || src.y < 0f || src.y > srcHeight) continue
                val sky = patchWcs.imageToSky(u, v, size)
                result += Offset(src.x, src.y) to
                    raDecToVector(sky.raDegrees.toDouble(), sky.decDegrees.toDouble())
            }
        }
        return result
    }

    /**
     * Analog zu [anchorsFor], aber für eine Kachel, die DIREKT aus dem Tiny-Sky-Bitmap gelöst wurde
     * (kein De-Warp-Patch involviert, s. Plan Nachtrag 1 "Kacheln bleiben im Raum, in dem sie gezeichnet
     * wurden"): rechnet ein Raster von Kachel-lokalen Punkten über [tileWcs] (Tiny-Sky-Crop-Pixel <->
     * äquatoriale Richtung, direkt vom Solver) UND über die rein geometrische Kette
     * [overviewProjection] (Tiny-Sky-Pixel -> Richtung) -> [nativeProjection] (Richtung -> nativer
     * Pixel) in EXAKTE (nativer Pixel, Richtung)-Paare um -- keine Bounding-Box-Näherung, jeder Punkt
     * einzeln umgerechnet. 3x3 (statt [anchorsFor]s 5x5) reicht hier: die höhere Dichte dort kompensiert
     * eine echte NICHTLINEARE Neu-Abtastung (De-Warp-Patch-Resampling), die hier nicht stattfindet --
     * dieselbe Charakteristik wie bei einer normalen, rohen Kachel.
     */
    fun tinySkyAnchorsFor(
        tileWcs: WcsSolution,
        rx: Int, ry: Int, rw: Int, rh: Int,
        overviewProjection: PanoramaProjection,
        nativeProjection: PanoramaProjection,
        grid: Int = 3,
    ): List<Pair<Offset, Vec3>> {
        val result = ArrayList<Pair<Offset, Vec3>>(grid * grid)
        for (gy in 0 until grid) {
            val py = rh * (gy + 0.5) / grid
            for (gx in 0 until grid) {
                val px = rw * (gx + 0.5) / grid
                val sky = tileWcs.imageToSky(px, py, rh)
                val dir = raDecToVector(sky.raDegrees.toDouble(), sky.decDegrees.toDouble())
                val overviewDir = overviewProjection.pixelToDirection(rx + px, ry + py) ?: continue
                val nativePixel = nativeProjection.directionToPixel(overviewDir) ?: continue
                result += nativePixel to dir
            }
        }
        return result
    }

    /**
     * A2 (Nutzer-Vorgabe 2026-08-30): transformiert echte, dichte `.corr`-Sternkorrespondenzen einer
     * Tiny-Sky-Kachel (crop-lokale Pixel, s. [rx]/[ry] = Kachel-Ursprung IM Tiny-Sky-Bitmap) exakt nach
     * nativen 2:1-Bild-Pixeln -- GENAU dieselbe zweistufige geometrische Kette wie [tinySkyAnchorsFor],
     * nur pro echtem `.corr`-Treffer statt pro künstlichem 3x3-Raster. Ersetzt NICHT die alte naive
     * "crop-lokal + tileOffsetX/Y"-Addition (die bliebe für Tiny-Sky-Pixel falsch) -- das ist der Grund,
     * warum diese Funktion überhaupt gebraucht wird. Ein einzelner Punkt, dessen Richtung außerhalb
     * dessen liegt, was [nativeProjection] abbilden kann, wird EINZELN übersprungen (`mapNotNull`) --
     * NIE die ganze Kachel verworfen.
     */
    fun transformTinySkyRefsToNative(
        refs: List<Pair<Offset, Vec3>>,
        rx: Int, ry: Int,
        overviewProjection: PanoramaProjection,
        nativeProjection: PanoramaProjection,
    ): List<Pair<Offset, Vec3>> = refs.mapNotNull { (cropLocal, dir) ->
        val overviewDir = overviewProjection.pixelToDirection(
            (rx + cropLocal.x).toDouble(), (ry + cropLocal.y).toDouble(),
        ) ?: return@mapNotNull null
        val nativePixel = nativeProjection.directionToPixel(overviewDir) ?: return@mapNotNull null
        nativePixel to dir
    }

    private fun sampleBilinear(
        region: IntArray, regX: Int, regY: Int, regW: Int, regH: Int, sx: Double, sy: Double,
        // Nur für den vollen-Bildbreite-zyklischen Fall (buildStereographicOverview): die Naht bei
        // Azimut ±π liefert x==regW (eine Spalte hinter der letzten gültigen) für JEDE Zeile -> ohne
        // Umbruch eine schwarze Radiallinie. De-Warp (buildPatch/resamplePatch) sampelt dagegen ein
        // NICHT-zyklisches Rechteck-Crop -- dort bleibt wrapX=false (Standard), unverändertes Verhalten.
        wrapX: Boolean = false,
    ): Int {
        var lx = sx - regX
        val ly = sy - regY
        if (ly < 0.0 || ly > regH - 1.0) return 0xFF000000.toInt()
        if (wrapX) {
            // Kotlins `%` kann bei negativem Operanden negativ bleiben -> doppelt umbrechen.
            lx = ((lx % regW) + regW) % regW
        } else if (lx < 0.0 || lx > regW - 1.0) {
            return 0xFF000000.toInt()
        }
        val x0 = lx.toInt(); val y0 = ly.toInt()
        val x1 = if (wrapX) (x0 + 1) % regW else (x0 + 1).coerceAtMost(regW - 1)
        val y1 = (y0 + 1).coerceAtMost(regH - 1)
        val fx = lx - x0; val fy = ly - y0
        val c00 = region[y0 * regW + x0]; val c10 = region[y0 * regW + x1]
        val c01 = region[y1 * regW + x0]; val c11 = region[y1 * regW + x1]
        val r = lerp2((c00 shr 16) and 0xFF, (c10 shr 16) and 0xFF, (c01 shr 16) and 0xFF, (c11 shr 16) and 0xFF, fx, fy)
        val g = lerp2((c00 shr 8) and 0xFF, (c10 shr 8) and 0xFF, (c01 shr 8) and 0xFF, (c11 shr 8) and 0xFF, fx, fy)
        val b = lerp2(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp2(c00: Int, c10: Int, c01: Int, c11: Int, fx: Double, fy: Double): Int {
        val top = c00 + (c10 - c00) * fx
        val bot = c01 + (c11 - c01) * fx
        return (top + (bot - top) * fy).toInt().coerceIn(0, 255)
    }
}
