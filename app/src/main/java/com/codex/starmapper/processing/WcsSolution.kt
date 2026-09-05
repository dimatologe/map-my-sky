package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.SkyPoint
import java.io.File
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface WcsSolutionLike {
    fun skyToImage(point: SkyPoint, imageHeight: Int): Offset?

    // Bei periodischen Panorama-Projektionen (Cylindrical-Familie) wird der Azimut relativ zu
    // [reference] entfaltet statt immer den atan2-Hauptzweig zu liefern -- reicht
    // PanoramaProjection.directionToPixel(dir, reference) 1:1 auf die Sky->Bild-Ebene weiter.
    // reference=null oder nicht überschrieben -> identisch zu skyToImage(point, imageHeight)
    // (Hauptzweig); bleibt Default für WcsSolution (TAN, nie periodisch) und MosaicWcsSolution
    // (mischt i.d.R. selbst nicht-periodische Kachel-Lösungen).
    fun skyToImage(point: SkyPoint, imageHeight: Int, reference: Offset?): Offset? = skyToImage(point, imageHeight)
}

/**
 * Panorama-/Fisheye-Lösung: bildet RA/Dec über eine globale Drehung ins Bild-Kugelsystem
 * und dann per [PanoramaProjection] auf den Pixel ab. Die Projektion liefert Pixel direkt
 * in Rasterkoordinaten (y nach unten), daher kein FITS-Flip.
 */
// open: erlaubt die refraktionskorrigierte Variante [RefractedPanoramaWcsSolution] als Subklasse,
// damit alle bestehenden `is/as? PanoramaWcsSolution`-Pfade (gekrümmte Linien, Rotation, Gradnetz)
// unverändert greifen und nur skyToImage die Refraktion ergänzt.
open class PanoramaWcsSolution(
    val projection: PanoramaProjection,
    val rotEquToPano: Mat3,
) : WcsSolutionLike {
    override fun skyToImage(point: SkyPoint, imageHeight: Int): Offset? {
        val eq = raDecToVector(point.raDegrees.toDouble(), point.decDegrees.toDouble())
        return projection.directionToPixel(rotEquToPano * eq)
    }

    override fun skyToImage(point: SkyPoint, imageHeight: Int, reference: Offset?): Offset? {
        val eq = raDecToVector(point.raDegrees.toDouble(), point.decDegrees.toDouble())
        return projection.directionToPixel(rotEquToPano * eq, reference)
    }
}

/**
 * [corrRefsAlreadyNative] (Untersuchungsauftrag 2026-08-31, Stage-2-Koordinatenfehler): `true` NUR für
 * eine Tiny-Sky-Kachel. Für eine normale (native) Kachel ist [tileOffsetX]/[tileOffsetY] der Kachel-
 * Ursprung im nativen Bild -- eine zugehörige `.corr`-Position ist kachel-LOKAL und braucht genau diese
 * Addition, um global/nativ zu werden ([FisheyeRefiner.globalizeTileCorrRefs]/[TileConsistency.
 * overlapDisagreement] tun das). Für eine Tiny-Sky-Kachel ist die zugehörige `.corr`-Position (aus
 * [StarMapperApp]s `solveAllTiles()`) dank [TileDeWarp.transformTinySkyRefsToNative] bereits VOR dem
 * Speichern exakt nach nativen Pixeln transformiert -- [tileOffsetX]/[tileOffsetY] ist für diesen Fall
 * NICHT der Kachel-Ursprung im Tiny-Sky-Bitmap, sondern nur eine grobe, ausschließlich für
 * [MosaicWcsSolution]s Fallback-Buchhaltung gedachte native Bounding-Box-Näherung (per
 * `convertOverlayGeometry`, s. Konstruktionsstelle) -- eine zusätzliche Addition auf die bereits native
 * `.corr`-Position wäre ein doppelter/falscher Versatz. Default `false` erhält das bisherige Verhalten
 * für jede andere [TileWcs]-Konstruktionsstelle unverändert.
 */
data class TileWcs(
    val wcs: WcsSolutionLike,
    val tileOffsetX: Int,
    val tileOffsetY: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val corrRefsAlreadyNative: Boolean = false,
)

class MosaicWcsSolution(
    val tiles: List<TileWcs>,
    // Fallback für Punkte, die in KEINER Kachel liegen (Lücken zwischen Kacheln / weit ausserhalb):
    // glatte globale Lösung statt wildem Extrapolieren einer fernen Kachel-WCS -> Sternbilder in
    // ungelösten Bereichen bleiben plausibel. null -> altes Verhalten (nächste Kachel).
    val fallback: WcsSolutionLike? = null,
) : WcsSolutionLike {
    /**
     * Wie [skyToImage], aber ohne den letzten Rückfall ("nächste Kachel extrapolieren, auch ohne jedes
     * Vertrauen") -- liefert `null` für Himmelsrichtungen, die von KEINER Kachel (auch nicht im
     * Blend-Rand) beansprucht werden UND kein [fallback] existiert. Für Konstellations-/DSO-/Stern-
     * Platzierung ist "irgendetwas anzeigen, auch grob falsch" historisch bewusst gewählt (s.
     * [skyToImage]-Kommentar) -- für das Koordinatennetz (GraticuleRenderer), das AKTIV weite
     * Himmelsbereiche systematisch abtastet, führt genau dieses "wilde Extrapolieren einer fernen
     * Kachel-WCS" (Nutzerbefund 2026-08-27: netzartige Schleifen/Speichen weit über das Bild hinaus,
     * bei einem Mosaik mit gescheitertem globalen Refine/Mesh und dadurch fallback=null) zu klar
     * sichtbaren Artefakten -- eine Tangential-(TAN-)Kachel-Lösung weit jenseits ihres eigenen gültigen
     * Bereichs extrapoliert (jenseits ~90° vom Kachel-Tangentialpunkt kehrt sie sich sogar um). Ein
     * fehlender Netzabschnitt (echte, nicht abgedeckte Lücke) ist für ein Gitter das ehrlichere, weit
     * unauffälligere Ergebnis als eine falsch geformte, aber lückenlos wirkende Linie.
     */
    fun skyToImageReliableOnly(point: SkyPoint, imageHeight: Int): Offset? =
        resolve(point, imageHeight, allowUnreliableExtrapolation = false)

    override fun skyToImage(point: SkyPoint, imageHeight: Int): Offset? =
        resolve(point, imageHeight, allowUnreliableExtrapolation = true)

    private fun resolve(point: SkyPoint, imageHeight: Int, allowUnreliableExtrapolation: Boolean): Offset? {
        // Punkte STRENG innerhalb GENAU EINER Kachel (trust=1, keine andere Kachel beteiligt)
        // verhalten sich exakt wie zuvor. Punkte knapp AUSSERHALB einer Kachel (bis
        // BLEND_MARGIN_FRACTION der Kachelgröße) werden weich zum Fallback hin übergeblendet statt
        // hart umzuschalten -- ein Sternbild, dessen Sterne zufällig auf beiden Seiten einer
        // Kachelkante liegen, reißt sonst sichtbar an genau dieser Kante ab (Nutzerbefund
        // 2026-07-29: Kepheus/Eidechse "abgehackt" exakt an der Kachelgrenze). Überlappen sich zwei
        // oder mehr Kacheln, werden ihre Lösungen gewichtet gemittelt statt hart zwischen ihnen
        // umzuschalten (s. Kommentar bei trustSum unten, Nutzerbefund 2026-07-30).
        data class Candidate(
            val fullOffset: Offset,
            val localX: Float,
            val localY: Float,
            val tile: TileWcs,
            val distSq: Float,
        )
        val candidates = tiles.mapNotNull { tile ->
            val tp = tile.wcs.skyToImage(point, tile.tileHeight) ?: return@mapNotNull null
            val fullX = tp.x + tile.tileOffsetX
            val fullY = tp.y + tile.tileOffsetY
            val cx = tile.tileOffsetX + tile.tileWidth / 2f
            val cy = tile.tileOffsetY + tile.tileHeight / 2f
            val dx = fullX - cx; val dy = fullY - cy
            Candidate(Offset(fullX, fullY), tp.x, tp.y, tile, dx * dx + dy * dy)
        }
        // Vertrauen [0,1] je Achse: 1.0 innerhalb der Kachel, linear auf 0.0 abfallend über
        // BLEND_MARGIN_FRACTION der jeweiligen Kachelgröße hinter der Kante, danach 0.
        fun axisTrust(local: Float, size: Float): Float {
            val marginPx = size * BLEND_MARGIN_FRACTION
            if (marginPx <= 0f) return if (local in 0f..size) 1f else 0f
            val outside = when {
                local < 0f -> -local
                local > size -> local - size
                else -> 0f
            }
            return (1f - outside / marginPx).coerceIn(0f, 1f)
        }
        // Bei MEHREREN Kandidaten mit trust>0 (echte Kachel-Überlappung) wird GEWICHTET gemittelt
        // statt nur den einen mit dem kleinsten distSq zu wählen. Ohne das entscheidet in der
        // Überlappungszone ausschließlich die (mit der Solve-Genauigkeit unverwandte) Kachel-
        // Layout-Geometrie (distSq zum eigenen Kachelzentrum), welche von zwei unabhängig gelösten
        // Kacheln genau EINEN Pixel liefert -- an der Mittelsenkrechten zwischen den Kachelzentren
        // kippt die Wahl hart von A zu B, und weil beide Kacheln unabhängige Solves mit leicht
        // unterschiedlichem Restfehler sind, reißt das Overlay dort sichtbar ab (Nutzerbefund
        // 2026-07-30: "da haben wir ja eigentlich eine Überlappung und da muss es ja umso genauer
        // sein"). trustSum >= 1 -> reines gewichtetes Mittel der Kachel-Kandidaten, normiert durch
        // trustSum (bei GENAU einem Kandidaten mit trust=1 exakt derselbe Pixel wie zuvor). trustSum
        // < 1 -> Rest (1 - trustSum) zusätzlich mit dem Fallback auffüllen -- direkte Verallgemeinerung
        // von candidate*trust + fallback*(1-trust) auf N Kandidaten. Für 3+ an einer Rasterecke
        // überlappende Kacheln ist KEIN Sonderfall nötig, die Summe läuft einfach über mehr Kandidaten.
        var trustSum = 0f
        var weightedX = 0f
        var weightedY = 0f
        for (candidate in candidates) {
            val trust = axisTrust(candidate.localX, candidate.tile.tileWidth.toFloat()) *
                axisTrust(candidate.localY, candidate.tile.tileHeight.toFloat())
            if (trust <= 0f) continue
            trustSum += trust
            weightedX += candidate.fullOffset.x * trust
            weightedY += candidate.fullOffset.y * trust
        }
        if (trustSum > 0f) {
            if (trustSum >= 1f) return Offset(weightedX / trustSum, weightedY / trustSum)
            val fallbackOffset = fallback?.skyToImage(point, imageHeight)
            if (fallbackOffset != null) {
                val missingTrust = 1f - trustSum
                return Offset(weightedX + fallbackOffset.x * missingTrust, weightedY + fallbackOffset.y * missingTrust)
            }
            return Offset(weightedX / trustSum, weightedY / trustSum)
        }
        fallback?.skyToImage(point, imageHeight)?.let { return it }
        return if (allowUnreliableExtrapolation) candidates.minByOrNull { it.distSq }?.fullOffset else null
    }

    /**
     * "Virtuelle Mauer" (Nutzer-Vorschlag): ist [point] von MINDESTENS einer Kachel tatsächlich
     * beansprucht — projiziert deren EIGENE WCS ihn auf ein Pixel innerhalb ihres EIGENEN
     * Rechtecks (+ [marginFraction] Jitter-Rand)?
     *
     * Bewusst NUR [WcsSolution]-Kacheln (echter TAN+SIP-Crop-Solve): Gnomonik hat KEINE
     * Periodizität und faltet nie zurück — ein Punkt weit jenseits des Crops divergiert (riesige
     * Koordinate) oder liefert null (Gegenrichtung), niemals fälschlich einen kleinen, plausiblen
     * In-Kachel-Pixel. Kacheln, deren eigene WCS SELBST ein [PanoramaWcsSolution] ist (De-Warp-
     * Kacheln, s. TileDeWarp), können je nach gefittetem Modell denselben Fehler erben wie das
     * globale Fallback-Modell (Fisheye-Kollaps, Zylinder-Periodizität) — für sie bleibt
     * ausschließlich die Anker-Winkel-Prüfung (isNearAnyAnchor) zuständig, kein Verhaltenswechsel.
     */
    fun isClaimedByAnyTile(point: SkyPoint, marginFraction: Float = TILE_CLAIM_MARGIN_FRACTION): Boolean =
        tiles.any { tile ->
            val wcs = tile.wcs as? WcsSolution ?: return@any false
            val tp = wcs.skyToImage(point, tile.tileHeight) ?: return@any false
            val mx = tile.tileWidth * marginFraction
            val my = tile.tileHeight * marginFraction
            tp.x >= -mx && tp.x <= tile.tileWidth + mx && tp.y >= -my && tp.y <= tile.tileHeight + my
        }

    private companion object {
        // Deckt sich mit dem bestehenden cropTile-Rand in AstapOverlayMapper.createConstellationOverlays
        // (tileWidth/Height * 0.10f) — Sterne knapp am Kachelrand flackern nicht durch normalen Solve-Jitter.
        const val TILE_CLAIM_MARGIN_FRACTION = 0.10f
        // Gleicher Rand-Anteil wie TILE_CLAIM_MARGIN_FRACTION, aber für die WEICHE Überblendung in
        // skyToImage() statt eines harten Ja/Nein-Gates: Punkte in dieser Zone hinter der Kachelkante
        // verblassen linear zum Fallback statt abrupt umzuschalten.
        const val BLEND_MARGIN_FRACTION = 0.10f
    }
}

data class WcsSolution(
    val crPix1: Double,
    val crPix2: Double,
    val crVal1Degrees: Double,
    val crVal2Degrees: Double,
    val cd11: Double,
    val cd12: Double,
    val cd21: Double,
    val cd22: Double,
    val inverseSipX: Map<Pair<Int, Int>, Double> = emptyMap(),
    val inverseSipY: Map<Pair<Int, Int>, Double> = emptyMap(),
    // ASTAP konvertiert Rasterbilder in FITS-Koordinaten (Y von unten); astrometry.net
    // behält bei JPEG/PNG die Rasterreihenfolge bei (Y von oben) -> flipY = false.
    val flipY: Boolean = true,
    // Vorwärts-SIP (FITS A_/B_): Pixel->Welt-Verzeichnung. imageToSky wendet sie an, damit
    // zurückgerechnete Anker (De-Warp/Crop) auch am Patch-Rand exakt sind (nicht nur CD+Gnomonik).
    // Leer -> imageToSky verhält sich exakt wie zuvor.
    val forwardSipX: Map<Pair<Int, Int>, Double> = emptyMap(),
    val forwardSipY: Map<Pair<Int, Int>, Double> = emptyMap(),
) : WcsSolutionLike {
    override fun skyToImage(point: SkyPoint, imageHeight: Int): Offset? {
        val ra0 = crVal1Degrees.toRadians()
        val dec0 = crVal2Degrees.toRadians()
        val ra = point.raDegrees.toDouble().toRadians()
        val dec = point.decDegrees.toDouble().toRadians()
        val deltaRa = normalizedRadians(ra - ra0)

        val denominator = sin(dec) * sin(dec0) + cos(dec) * cos(dec0) * cos(deltaRa)
        if (denominator <= 1e-10) return null

        val tangentXDegrees = (
            cos(dec) * sin(deltaRa) / denominator
            ).toDegrees()
        val tangentYDegrees = (
            (sin(dec) * cos(dec0) - cos(dec) * sin(dec0) * cos(deltaRa)) / denominator
            ).toDegrees()

        val determinant = cd11 * cd22 - cd12 * cd21
        if (kotlin.math.abs(determinant) < 1e-16) return null

        val linearX = (tangentXDegrees * cd22 - tangentYDegrees * cd12) / determinant
        val linearY = (cd11 * tangentYDegrees - cd21 * tangentXDegrees) / determinant
        val correctedX = linearX + polynomial(inverseSipX, linearX, linearY)
        val correctedY = linearY + polynomial(inverseSipY, linearX, linearY)

        val fitsX = crPix1 + correctedX
        val fitsY = crPix2 + correctedY
        if (!fitsX.isFinite() || !fitsY.isFinite()) return null

        return Offset(
            x = (fitsX - 1.0).toFloat(),
            y = if (flipY) (imageHeight - fitsY).toFloat() else (fitsY - 1.0).toFloat(),
        )
    }

    /**
     * Umkehrung Pixel -> RA/Dec (CD-Matrix + inverse Gnomonik). Wendet die vorwärts-SIP an,
     * falls vorhanden (sonst identisch zu reiner CD+Gnomonik) -> auch am Rand exakt.
     */
    fun imageToSky(px: Double, py: Double, imageHeight: Int): SkyPoint {
        val fitsX = px + 1.0
        val fitsY = if (flipY) (imageHeight - py) else (py + 1.0)
        val dx = fitsX - crPix1
        val dy = fitsY - crPix2
        // Vorwärts-SIP (leere Map -> polynomial=0 -> unverändert).
        val dxc = dx + polynomial(forwardSipX, dx, dy)
        val dyc = dy + polynomial(forwardSipY, dx, dy)
        val xi = (cd11 * dxc + cd12 * dyc).toRadians() // Tangentialebene
        val eta = (cd21 * dxc + cd22 * dyc).toRadians()
        val ra0 = crVal1Degrees.toRadians()
        val dec0 = crVal2Degrees.toRadians()
        val rho = sqrt(xi * xi + eta * eta)
        if (rho < 1e-12) return SkyPoint(crVal1Degrees.toFloat(), crVal2Degrees.toFloat())
        val c = atan(rho)
        val sinC = sin(c); val cosC = cos(c)
        val dec = asin(cosC * sin(dec0) + eta * sinC * cos(dec0) / rho)
        val ra = ra0 + atan2(xi * sinC, rho * cos(dec0) * cosC - eta * sin(dec0) * sinC)
        var raDeg = ra.toDegrees()
        raDeg = ((raDeg % 360.0) + 360.0) % 360.0
        return SkyPoint(raDeg.toFloat(), dec.toDegrees().toFloat())
    }

    private fun polynomial(
        coefficients: Map<Pair<Int, Int>, Double>,
        x: Double,
        y: Double,
    ): Double = coefficients.entries.sumOf { (powers, coefficient) ->
        coefficient * x.pow(powers.first) * y.pow(powers.second)
    }
}

object WcsSolutionParser {
    private val sipKey = Regex("""^(AP|BP)_(\d+)_(\d+)$""")
    private val forwardSipKey = Regex("""^(A|B)_(\d+)_(\d+)$""")

    fun fromCards(
        cards: Map<String, String>,
        requirePlateSolved: Boolean,
        flipY: Boolean,
    ): WcsSolution {
        if (requirePlateSolved) {
            check(cards["PLTSOLVD"]?.equals("T", ignoreCase = true) == true) {
                "Keine gültige WCS-Lösung gefunden."
            }
        }
        return WcsSolution(
            crPix1 = cards.requiredDouble("CRPIX1"),
            crPix2 = cards.requiredDouble("CRPIX2"),
            crVal1Degrees = cards.requiredDouble("CRVAL1"),
            crVal2Degrees = cards.requiredDouble("CRVAL2"),
            cd11 = cards.requiredDouble("CD1_1"),
            cd12 = cards.requiredDouble("CD1_2"),
            cd21 = cards.requiredDouble("CD2_1"),
            cd22 = cards.requiredDouble("CD2_2"),
            inverseSipX = parseSip(cards, "AP"),
            inverseSipY = parseSip(cards, "BP"),
            flipY = flipY,
            forwardSipX = parseForwardSip(cards, "A"),
            forwardSipY = parseForwardSip(cards, "B"),
        )
    }

    internal fun parseCards(file: File): Map<String, String> = parseCards(file.readBytes())

    internal fun parseCards(bytes: ByteArray): Map<String, String> {
        val cards = linkedMapOf<String, String>()
        var offset = 0
        while (offset + 80 <= bytes.size) {
            val card = bytes.copyOfRange(offset, offset + 80).toString(Charsets.US_ASCII)
            offset += 80
            val key = card.take(8).trim()
            if (key == "END") break
            if (key.isBlank() || card.getOrNull(8) != '=') continue
            cards[key] = card.substring(10).substringBefore('/').trim().trim('\'')
        }
        return cards
    }

    private fun parseSip(cards: Map<String, String>, prefix: String): Map<Pair<Int, Int>, Double> =
        buildMap {
            cards.forEach { (key, value) ->
                val match = sipKey.matchEntire(key) ?: return@forEach
                if (match.groupValues[1] != prefix) return@forEach
                val coefficient = value.toFitsDoubleOrNull() ?: return@forEach
                put(
                    match.groupValues[2].toInt() to match.groupValues[3].toInt(),
                    coefficient,
                )
            }
        }

    // Vorwärts-SIP A_/B_ (nicht AP_/BP_): matchEntire mit ^(A|B)_ trifft NICHT AP_/BP_ (dort folgt P,
    // kein '_') und nicht A_ORDER (dort keine Ziffern) -> nur echte A_i_j / B_i_j.
    private fun parseForwardSip(cards: Map<String, String>, prefix: String): Map<Pair<Int, Int>, Double> =
        buildMap {
            cards.forEach { (key, value) ->
                val match = forwardSipKey.matchEntire(key) ?: return@forEach
                if (match.groupValues[1] != prefix) return@forEach
                val coefficient = value.toFitsDoubleOrNull() ?: return@forEach
                put(
                    match.groupValues[2].toInt() to match.groupValues[3].toInt(),
                    coefficient,
                )
            }
        }

    private fun Map<String, String>.requiredDouble(key: String): Double =
        get(key)?.toFitsDoubleOrNull() ?: error("WCS-Schlüssel $key fehlt.")
}

private fun String.toFitsDoubleOrNull(): Double? =
    replace('D', 'E', ignoreCase = true).toDoubleOrNull()

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI

private fun Double.pow(power: Int): Double {
    var result = 1.0
    repeat(power) { result *= this }
    return result
}

private fun normalizedRadians(value: Double): Double {
    var result = value
    while (result > PI) result -= 2.0 * PI
    while (result < -PI) result += 2.0 * PI
    return result
}

/**
 * Achsen-ausgerichtete RA/Dec-Hülle (Grad) der von einer [WcsSolutionLike] abgedeckten Himmelsregion,
 * für Bounding-Box-Abfragen gegen einen externen Sternkatalog (s. Tycho2Store.queryStars). [raMinDeg]/
 * [raMaxDeg] sind NICHT auf 0..360 normiert -- raMinDeg kann negativ sein, raMaxDeg über 360 hinaus-
 * gehen, das kodiert den 0°/360°-Sprung (s. Tycho2Store.queryStars für die Abfrage-Auflösung).
 */
data class RaDecBox(val raMinDeg: Double, val raMaxDeg: Double, val decMinDeg: Double, val decMaxDeg: Double)

/**
 * Baut [RaDecBox] aus Bildecken + Kantenmitten (nicht nur Ecken -- bei gekrümmten Projektionen kann
 * der extremste Punkt einer Kante statt einer Ecke liegen), mit [BOX_MARGIN_DEG] Sicherheitsrand (die
 * 8 Abtastpunkte bilden nur eine Näherung der wahren konvexen Hülle). Für [MosaicWcsSolution]:
 * Vereinigung der Boxen aller Kacheln (jede in ihrem EIGENEN lokalen Pixelraum abgetastet). null nur
 * wenn KEIN einziger Abtastpunkt eine gültige Himmelsrichtung liefert (z.B. Polnähe bei Fisheye/Mesh).
 */
fun WcsSolutionLike.raDecBoundingBox(imageWidth: Int, imageHeight: Int): RaDecBox? {
    val points = when (this) {
        is MosaicWcsSolution -> tiles.flatMap { tile -> sampleSkyPoints(tile.wcs, tile.tileWidth, tile.tileHeight) }
        else -> sampleSkyPoints(this, imageWidth, imageHeight)
    }
    if (points.isEmpty()) return null

    // Dec: einfaches Min/Max, kein Sprung möglich.
    val decMin = (points.minOf { it.decDegrees.toDouble() } - BOX_MARGIN_DEG).coerceAtLeast(-90.0)
    val decMax = (points.maxOf { it.decDegrees.toDouble() } + BOX_MARGIN_DEG).coerceAtMost(90.0)

    // RA: sprungsicher über Deltas zu einem Referenzpunkt (erster Abtastpunkt) statt direktem Min/Max
    // -- vermeidet den 0°/360°-Sprung komplett, da alle Deltas betragsmäßig höchstens 180° sind.
    val refRa = points.first().raDegrees.toDouble()
    val deltas = points.map { normalizedDeltaDegrees(it.raDegrees.toDouble() - refRa) }
    val raMin = refRa + deltas.min() - BOX_MARGIN_DEG
    val raMax = refRa + deltas.max() + BOX_MARGIN_DEG
    // Deckelt die Spannweite bei genau 360° (mehr ist bei einer echten Himmelsregion bedeutungslos --
    // Tycho2Store.queryStars behandelt >= 360° Spannweite ohnehin als "ganzer Himmel, kein RA-Filter").
    return RaDecBox(raMin, raMax.coerceAtMost(raMin + 360.0), decMin, decMax)
}

private fun sampleSkyPoints(wcs: WcsSolutionLike, w: Int, h: Int): List<SkyPoint> {
    val wf = w.toDouble()
    val hf = h.toDouble()
    val samples = listOf(
        0.0 to 0.0, wf / 2 to 0.0, wf to 0.0,
        0.0 to hf / 2, wf to hf / 2,
        0.0 to hf, wf / 2 to hf, wf to hf,
    )
    return samples.mapNotNull { (px, py) -> wcs.imageToSkyApprox(px, py, h) }
}

/**
 * Näherungsweise Umkehrung von [WcsSolutionLike.skyToImage]: nativer Bild-Pixel -> Himmelsrichtung.
 * "Näherung", weil (a) [RefractedPanoramaWcsSolution] die Refraktionskorrektur hier NICHT anwendet (nur
 * `skyToImage` ist dafür überschrieben -- für den einzigen verbleibenden Nutzungszweck, die grobe
 * Sichtfeld-Bounding-Box für die Tycho-2-Katalogabfrage (s. `raDecBoundingBox`/`sampleSkyPoints`),
 * unerheblich, für eine Präzisionsplatzierung wäre es das nicht) und (b) [MosaicWcsSolution] bewusst
 * `null` liefert (wird in `raDecBoundingBox()` bereits separat pro Kachel behandelt, hier nie sinnvoll
 * ohne zusätzlichen Kachel-Ownership-Lookup lösbar).
 */
fun WcsSolutionLike.imageToSkyApprox(px: Double, py: Double, imageHeight: Int): SkyPoint? = when (this) {
    is WcsSolution -> imageToSky(px, py, imageHeight)
    is PanoramaWcsSolution -> {
        val dirPano = projection.pixelToDirection(px, py)
        if (dirPano == null) {
            null
        } else {
            val dirEq = (rotEquToPano.transpose() * dirPano).normalized()
            val (ra, dec) = vectorToRaDec(dirEq)
            SkyPoint(ra.toFloat(), dec.toFloat())
        }
    }
    is MosaicWcsSolution -> null
}

private fun normalizedDeltaDegrees(value: Double): Double {
    var result = value % 360.0
    if (result > 180.0) result -= 360.0
    if (result < -180.0) result += 360.0
    return result
}

private const val BOX_MARGIN_DEG = 2.0
