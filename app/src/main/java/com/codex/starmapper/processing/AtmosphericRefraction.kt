package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.SkyPoint
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Atmosphärische Refraktion: nahe am Horizont hebt die Luft die scheinbare Sternposition an
 * (~34' am Horizont, ~0 im Zenit). Der Katalog liefert WAHRE Positionen, das Bild zeigt die
 * gehobenen (scheinbaren). Ein rein geometrisches Projektionsmodell kann diesen höhenabhängigen
 * Versatz NICHT abbilden -> systematischer Restfehler, der zum Rand hin wächst (der RMS-Anstieg).
 *
 * Nur sinnvoll für horizontbezogene Allsky-Bilder (Little-Planet), wo die Bildmitte der Zenit ist.
 * Deshalb an De-Warp gekoppelt (der Fall extremer Rand-Verzeichnung).
 */
object AtmosphericRefraction {
    /**
     * Wahre Richtung -> scheinbare (gehobene) Richtung: dreht [trueDir] in der Vertikalebene um den
     * Refraktionswinkel R(Höhe) zum [zenith] hin. [zenith] = äquatoriale Richtung der Bildmitte.
     */
    fun apparentDirection(trueDir: Vec3, zenith: Vec3): Vec3 {
        val u = trueDir.normalized()
        val z = zenith.normalized()
        val cosZ = u.dot(z).coerceIn(-1.0, 1.0)
        val altitudeDeg = 90.0 - Math.toDegrees(acos(cosZ))
        val rArcmin = refractionArcmin(altitudeDeg)
        if (rArcmin <= 0.0) return u
        // In-Ebenen-Einheitsrichtung von u nach z (senkrecht zu u).
        val px = z.x - cosZ * u.x
        val py = z.y - cosZ * u.y
        val pz = z.z - cosZ * u.z
        val pl = sqrt(px * px + py * py + pz * pz)
        if (pl < 1e-9) return u // im Zenit -> keine Refraktion
        val tx = px / pl; val ty = py / pl; val tz = pz / pl
        val r = Math.toRadians(rArcmin / 60.0)
        val c = cos(r); val s = sin(r)
        return Vec3(u.x * c + tx * s, u.y * c + ty * s, u.z * c + tz * s).normalized()
    }

    /**
     * Saemundsson-Formel: Refraktion in Bogenmin aus der WAHREN Höhe (Grad).
     * Unter ~-1.5deg (klar unter dem Horizont) = 0 (nicht sinnvoll).
     */
    fun refractionArcmin(trueAltitudeDeg: Double): Double {
        if (trueAltitudeDeg < -1.5) return 0.0
        val h = trueAltitudeDeg
        val denom = tan(Math.toRadians(h + 10.3 / (h + 5.11)))
        if (denom == 0.0) return 0.0
        val r = 1.02 / denom
        return if (r < 0.0) 0.0 else r
    }
}

/**
 * Panorama-Lösung MIT Refraktionskorrektur: bildet eine WAHRE Katalogrichtung erst auf ihre
 * scheinbare (gehobene) Richtung ab und dann per Projektion auf den Pixel. So sitzen Sternbild-/
 * Objekt-/Stern-Overlays auch am Rand (niedrige Höhe) korrekt.
 *
 * Subklasse von [PanoramaWcsSolution] (mit der auf die SCHEINBAREN Anker gefitteten Geometrie),
 * damit alle bestehenden `is/as? PanoramaWcsSolution`-Pfade (gekrümmte Linien, Rotation, Gradnetz)
 * unverändert greifen; nur [skyToImage] ergänzt die Refraktion. Wird nur im De-Warp-Fall als
 * angezeigte Lösung verwendet.
 */
class RefractedPanoramaWcsSolution(
    base: PanoramaWcsSolution,
    val zenithEq: Vec3,
    // Stufe 2: optionale glatte Rest-Korrektur -> Lösung überall lokal genau (Mitte + Rand).
    val residual: ResidualCorrection? = null,
) : PanoramaWcsSolution(base.projection, base.rotEquToPano) {
    override fun skyToImage(point: SkyPoint, imageHeight: Int): Offset? {
        val trueDir = raDecToVector(point.raDegrees.toDouble(), point.decDegrees.toDouble())
        val apparent = AtmosphericRefraction.apparentDirection(trueDir, zenithEq)
        val pred = projection.directionToPixel(rotEquToPano * apparent) ?: return null
        return residual?.correct(pred) ?: pred
    }

    // Ohne diese Überschreibung würde der 3-Parameter-Aufruf die geerbte, nahtstellensichere
    // PanoramaWcsSolution-Variante nutzen -- die rechnet mit dem WAHREN statt dem REFRAKTIONS-
    // gehobenen Richtungsvektor, die Refraktionskorrektur würde also für jeden Aufruf mit
    // reference!=null stillschweigend übersprungen.
    override fun skyToImage(point: SkyPoint, imageHeight: Int, reference: Offset?): Offset? {
        val trueDir = raDecToVector(point.raDegrees.toDouble(), point.decDegrees.toDouble())
        val apparent = AtmosphericRefraction.apparentDirection(trueDir, zenithEq)
        val pred = projection.directionToPixel(rotEquToPano * apparent, reference) ?: return null
        return residual?.correct(pred) ?: pred
    }
}
