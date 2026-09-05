package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.SkyPoint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Erkennung falsch gelöster Kacheln: Nova liefert gelegentlich einen STILLEN Falschtreffer
 * (Zufalls-Quad-Match bei sternarmen/verrauschten Ausschnitten) — die Kachel gilt als gelöst,
 * ihre WCS zeigt aber auf die falsche Himmelsregion. Ihre Anker widersprechen dann allen anderen
 * und vergiften den globalen Fit (rms explodiert, Rest-Korrektur kann nichts retten).
 *
 * [flagOutliers] prüft die Anker jeder Kachel gegen den gemeinsamen Panorama-Fit und flaggt
 * Kacheln, deren Median-Reprojektionsfehler weit über dem der übrigen liegt. Die Schwelle ist
 * bewusst großzügig: Modell-Restfehler am Rand (zig Pixel) flaggt NIE, ein Falschtreffer
 * (hunderte bis tausende Pixel) immer.
 */
object TileConsistency {
    /** Unter 3 Kacheln ist nicht entscheidbar, welche von widersprechenden falsch ist. */
    const val MIN_TILES = 3

    /** Mindest-Fehlerschwelle als Anteil der langen Bildkante (6320 px -> ~190 px). */
    const val MIN_ERROR_FRACTION = 0.03f

    /** Kachel-Median muss zusätzlich ein Mehrfaches des Gesamt-Medians sein. */
    const val MEDIAN_FACTOR = 4.0

    /** Pro Runde wird nur die schlechteste Kachel geflaggt und neu gefittet. */
    const val MAX_ROUNDS = 3

    data class Outlier(val tileId: Long, val medianErrorPx: Double)

    // Mindest-Trefferzahl für die eigene Kachel-Genauigkeit (s. TileOwnAccuracy): darunter ist eine
    // RMS-Angabe zu verrauscht, um als belastbare Kennzahl angezeigt/gewichtet zu werden --
    // TileOwnAccuracy bekommt dann rmsPx=null, matchCount bleibt aber gesetzt (Popup zeigt "noch
    // nicht bewertbar (nur N von mind. M erkannt)" statt einer unbelastbaren Zahl). Startwert
    // vorläufig, angelehnt an FisheyeRefiner.MIN_MATCHES=10 für den Ganzbild-Fall, hier bewusst
    // niedriger, da eine einzelne Kachel viel weniger Himmelsfläche/Sterne abdeckt als das
    // Gesamtbild -- UNVERIFIZIERT gegen echte Gerätefälle. (Verschoben aus StarMapperApp.kt --
    // 2026-08-17, damit sowohl der Fit- als auch der Überlappungs-Zuverlässigkeits-Mechanismus
    // unten denselben Typ ohne UI-Abhängigkeit nutzen können.)
    const val MIN_TILE_OWN_RMS_MATCHES = 6

    /**
     * Echte, unabhängige Pro-Kachel-Solve-Genauigkeit fürs Kachel-Info-Popup (s. tileOwnRmsById in
     * StarMapperApp.kt) UND jetzt auch für [tileReliabilityWeights]. [rmsPx] ist null, wenn
     * [matchCount] unter [MIN_TILE_OWN_RMS_MATCHES] liegt -- ein RMS aus zu wenigen Punkten wäre
     * nicht belastbar, die Trefferzahl selbst bleibt aber informativ (Nutzerfrage 2026-07-31: die
     * "noch nicht bewertbar"-Meldung zeigte bislang keine Zahl).
     */
    data class TileOwnAccuracy(val rmsPx: Double?, val matchCount: Int)

    /**
     * Bevorzugter (`.corr`-basierter) Ausschnitt der vollen `tileOwnRmsById`-Berechnung in
     * StarMapperApp.kt, OHNE deren Ganzbild-Blob-Rückfall -- der braucht Ganzbild-Sternerkennung,
     * die beim frühen Aufruf hier (noch VOR dem globalen Modell-Fit, s. [tileReliabilityWeights])
     * bewusst noch nicht vorausgesetzt wird. Deckt deshalb nur Kacheln ab, die echte `.corr`-Treffer
     * haben (LocalAstrometry-Rohkachel-Solves) -- für ASTAP/Nova-gelöste Kacheln bleibt die
     * Zuverlässigkeit an dieser Stelle unbekannt (zählt bei [tileReliabilityWeights] als volles
     * Vertrauen), wird aber später von der vollständigen Berechnung (mit Rückfall) fürs Kachel-Info-
     * Popup trotzdem nachgetragen -- diese Funktion ersetzt jene nicht, sie liefert nur einen frühen
     * Teilausschnitt für die Gewichtung.
     */
    fun corrBasedOwnAccuracy(
        idToTileWcs: List<Pair<Long, TileWcs>>,
        corrRefsById: Map<Long, List<Pair<Offset, Vec3>>>,
    ): Map<Long, TileOwnAccuracy> {
        val result = HashMap<Long, TileOwnAccuracy>()
        for ((tileId, tw) in idToTileWcs) {
            val corrRefs = corrRefsById[tileId]
            if (corrRefs.isNullOrEmpty()) continue
            // Stabilisierungs-Pass (Untersuchungsauftrag 2026-08-31): [tw.wcs] sagt IMMER im lokalen,
            // zu [tw.tileOffsetX]/[tileOffsetY] RELATIVEN Bildraum vorher (für eine Tiny-Sky-Kachel ist
            // das der Nachtrag-Refit über [tw.tileOffsetX]/[tileOffsetY] als grobe native Bounding-Box-
            // Näherung, s. TileWcs-Konstruktionsstelle in solveAllTiles()) -- [corrRefs] ist für eine
            // Tiny-Sky-Kachel ([tw.corrRefsAlreadyNative]) dagegen bereits NATIV. Ohne Umrechnung würde
            // hier eine native Position gegen eine lokale Vorhersage verglichen -- derselbe Fehler-Typ
            // wie der bereits behobene Stage-1/2-Koordinatenraum-Bug, hier aber umgekehrt: nicht die
            // Vorhersage braucht den Offset, sondern die IST-Position muss ihn VERLIEREN (lokal werden),
            // bevor sie mit [tw.wcs] verglichen wird -- identisch zur bereits korrekten Umrechnung des
            // Rückfallpfads (Ganzbild-Blobs -> localBlobs, s. StarMapperApp.kt).
            val effectiveRefs = if (tw.corrRefsAlreadyNative) {
                corrRefs.map { (px, dir) -> Offset(px.x - tw.tileOffsetX, px.y - tw.tileOffsetY) to dir }
            } else {
                corrRefs
            }
            result[tileId] = TileOwnAccuracy(
                rmsPx = if (corrRefs.size >= MIN_TILE_OWN_RMS_MATCHES) {
                    FisheyeRefiner.reprojectionRmsWcs(tw.wcs, effectiveRefs, tw.tileHeight)
                } else {
                    null
                },
                matchCount = corrRefs.size,
            )
        }
        return result
    }

    /** Analog [MIN_TILE_OWN_RMS_MATCHES], aber für [overlapDisagreement] -- ebenso UNVERIFIZIERT
     *  gegen echte Gerätefälle, am Gerät nachjustierbar. */
    const val MIN_OVERLAP_MATCHES = 3

    // Randtoleranz um eine Nachbarkachel, damit ein Punkt hauchdünn außerhalb ihres Rechtecks
    // (normaler Solve-Jitter, kein echtes "liegt nicht drin") nicht fälschlich als "keine
    // Überlappung" gewertet wird. Gleicher Gedanke/Größenordnung wie die vorhandene Randtoleranz
    // in MosaicWcsSolution (dort für die Überblend-Mittelung, hier fürs bloße Erkennen der
    // Überlappung selbst).
    private const val OVERLAP_MARGIN_FRACTION = 0.10

    /** Ergebnis von [overlapDisagreement] je Kachel -- s. [TileOwnAccuracy] fürs Gegenstück. */
    data class OverlapAccuracy(val rmsPx: Double?, val matchCount: Int)

    /**
     * Zweites, von [TileOwnAccuracy] UNABHÄNGIGES Zuverlässigkeits-Signal: für jede Kachel werden
     * ihre eigenen `.corr`-Sternpaarungen ([corrRefsById]) daraufhin geprüft, ob eine ANDERE, bereits
     * unabhängig gelöste Kachel denselben Bildbereich beansprucht -- falls ja, wird verglichen, wohin
     * die andere Kachel denselben Stern projizieren würde. Der Widerspruch (Bild-px) zählt SYMMETRISCH
     * für BEIDE beteiligten Kacheln (welche der beiden falsch liegt, lässt sich aus dem Widerspruch
     * allein nicht sagen -- seine Stärke ist für beide gleichermaßen aussagekräftig). Eine Kachel, die
     * in sich konsistent, aber insgesamt falsch orientiert ist, sieht in [TileOwnAccuracy] gut aus,
     * widerspricht aber einer echten Nachbarkachel -- genau diesen Fall deckt [TileOwnAccuracy] allein
     * strukturell nicht ab.
     *
     * Braucht nur [WcsSolutionLike.skyToImage] (Richtung->Pixel), keine Pixel->Richtung-Umkehrung --
     * funktioniert deshalb einheitlich für alle Kachel-Arten (auch entzerrte De-Warp-Kacheln, deren
     * WCS keine Umkehrung anbietet). Kacheln OHNE eigene `.corr`-Treffer (ASTAP/Nova/De-Warp) können
     * trotzdem als ANTWORTENDE Nachbarkachel auftauchen, nur nicht als SONDIERENDE.
     */
    fun overlapDisagreement(
        idToTileWcs: List<Pair<Long, TileWcs>>,
        corrRefsById: Map<Long, List<Pair<Offset, Vec3>>>,
    ): Map<Long, OverlapAccuracy> {
        val errorsByTile = HashMap<Long, MutableList<Double>>()
        for ((sourceId, sourceTile) in idToTileWcs) {
            val refs = corrRefsById[sourceId] ?: continue
            for ((localPixel, dir) in refs) {
                // Stabilisierungs-Pass (Untersuchungsauftrag 2026-08-31): identischer Fehler-Typ wie der
                // bereits behobene FisheyeRefiner.globalizeTileCorrRefs-Bug -- [localPixel] ist für eine
                // Tiny-Sky-Kachel ([sourceTile.corrRefsAlreadyNative]) bereits NATIV, [sourceTile.
                // tileOffsetX]/[tileOffsetY] dort nur eine grobe Mosaik-Fallback-Näherung (kein Kachel-
                // Ursprung) -- die Addition würde denselben doppelten Versatz erzeugen. Die GEGENSEITIGE
                // (otherTile-)Umrechnung unten bleibt unverändert korrekt: otherTile.wcs sagt IMMER
                // relativ zu otherTile.tileOffsetX/Y vorher, unabhängig vom Kacheltyp von sourceTile.
                val globalX = if (sourceTile.corrRefsAlreadyNative) localPixel.x else localPixel.x + sourceTile.tileOffsetX
                val globalY = if (sourceTile.corrRefsAlreadyNative) localPixel.y else localPixel.y + sourceTile.tileOffsetY
                for ((otherId, otherTile) in idToTileWcs) {
                    if (otherId == sourceId) continue
                    val marginX = otherTile.tileWidth * OVERLAP_MARGIN_FRACTION
                    val marginY = otherTile.tileHeight * OVERLAP_MARGIN_FRACTION
                    val localX = globalX - otherTile.tileOffsetX
                    val localY = globalY - otherTile.tileOffsetY
                    if (localX < -marginX || localX > otherTile.tileWidth + marginX ||
                        localY < -marginY || localY > otherTile.tileHeight + marginY
                    ) {
                        continue
                    }
                    val (raDeg, decDeg) = vectorToRaDec(dir)
                    val predicted = otherTile.wcs.skyToImage(
                        SkyPoint(raDeg.toFloat(), decDeg.toFloat()),
                        otherTile.tileHeight,
                    ) ?: continue
                    val predictedGlobalX = predicted.x + otherTile.tileOffsetX
                    val predictedGlobalY = predicted.y + otherTile.tileOffsetY
                    val d = hypot((globalX - predictedGlobalX).toDouble(), (globalY - predictedGlobalY).toDouble())
                    errorsByTile.getOrPut(sourceId) { mutableListOf() } += d
                    errorsByTile.getOrPut(otherId) { mutableListOf() } += d
                }
            }
        }
        return errorsByTile.mapValues { (_, errors) ->
            OverlapAccuracy(
                rmsPx = if (errors.size >= MIN_OVERLAP_MATCHES) sqrt(errors.sumOf { it * it } / errors.size) else null,
                matchCount = errors.size,
            )
        }
    }

    // Untergrenze fürs Zuverlässigkeits-Gewicht: verhindert, dass eine extrem auffällige Kachel auf
    // (nahezu) 0 fällt -- ein harter Ausschluss bleibt bewusst allein Sache von [flagOutliers], diese
    // Gewichtung soll nur ABSTUFEN, nicht selbst entscheiden, dass eine Kachel komplett ignoriert wird.
    // Reine Sicherheits-Klammer, nicht aus Daten hergeleitet.
    private const val MIN_RELIABILITY = 0.1

    /**
     * Kombiniert [TileOwnAccuracy] und [OverlapAccuracy] (das schlechtere von beiden gewinnt, fehlt
     * eines von beiden zählt nur das vorhandene) zu EINEM Zuverlässigkeits-Gewicht je Kachel
     * (0..1, ausgeschlossen [MIN_RELIABILITY] als Untergrenze). Selbstlernend statt fest verdrahteter
     * Pixel-Schwelle: der Fehler jeder Kachel wird gegen die robuste Streuung ALLER Kacheln DIESES
     * Solves bewertet (Median + 1.4826*MAD, s. [RotationFit.robustSigmaDegrees]), nicht gegen einen
     * absoluten Erfahrungswert -- dasselbe Prinzip wie [RotationFit.solveRobust] (Huber-IRLS), hier
     * auf Kachel-GRUPPEN statt Rotations-Korrespondenzen angewandt, EINMALIG (keine Wiederhol-
     * Schleife -- die eigene Genauigkeit und der Überlappungs-Widerspruch einer Kachel ändern sich
     * nicht, wenn man das Panorama-Modell neu fittet, anders als die Rotations-Restfehler in
     * solveRobust, die sich bei jeder neuen Rotation ändern -- eine Schleife würde hier also nur
     * Rechenzeit kosten, ohne dass sich die Gewichte dabei noch ändern würden).
     *
     * Fehlt für eine Kachel JEDES Signal, bleibt ihr Gewicht bei 1.0 (volles Vertrauen) -- fehlende
     * Daten sind kein Beleg für eine schlechte Kachel.
     */
    fun tileReliabilityWeights(
        tileIds: List<Long>,
        ownAccuracy: Map<Long, TileOwnAccuracy>,
        overlapAccuracy: Map<Long, OverlapAccuracy>,
    ): List<Double> {
        val errorEstimate: List<Double?> = tileIds.map { id ->
            val own = ownAccuracy[id]?.rmsPx
            val overlap = overlapAccuracy[id]?.rmsPx
            if (own != null && overlap != null) maxOf(own, overlap) else own ?: overlap
        }
        val known = errorEstimate.filterNotNull()
        if (known.size < MIN_TILES) return List(tileIds.size) { 1.0 }
        val sigma = RotationFit.robustSigmaDegrees(known)
        return errorEstimate.map { e ->
            if (e == null) 1.0 else RotationFit.huberWeight(e, sigma).coerceAtLeast(MIN_RELIABILITY)
        }
    }

    /**
     * @param tileAnchors Anker (Bildpixel <-> äquatoriale Richtung) je GELOESTER Kachel.
     * @return zu flaggende Kacheln, schlechteste zuerst; leer wenn alles konsistent ist.
     */
    fun flagOutliers(
        tileAnchors: List<Pair<Long, List<Pair<Offset, Vec3>>>>,
        imageWidth: Int,
        imageHeight: Int,
        allowed: Set<PanoProjectionKind>,
    ): List<Outlier> {
        val flagged = ArrayList<Outlier>()
        val remaining = tileAnchors.filter { it.second.isNotEmpty() }.toMutableList()
        if (remaining.size < MIN_TILES) return flagged
        val minError = (MIN_ERROR_FRACTION * max(imageWidth, imageHeight)).toDouble()
        val nullError = hypot(imageWidth.toDouble(), imageHeight.toDouble())
        repeat(MAX_ROUNDS) {
            if (remaining.size <= 2) return flagged
            val anchors = remaining.flatMap { it.second }
            // Kachel-Stimmgewicht (s. FisheyeRefiner.tileVoteWeights): ohne das würde die Referenz,
            // gegen die jede Kachel geprüft wird, selbst zur anker-reichsten Kachel hin verzerrt —
            // echte, aber dichter/dünner abgetastete Kacheln könnten dadurch fälschlich als
            // Ausreißer erscheinen bzw. echte Ausreißer maskiert werden.
            val weights = FisheyeRefiner.tileVoteWeights(remaining.map { it.second.size })
            val fit = FisheyeRefiner.calibratePanorama(anchors, imageWidth, imageHeight, allowed, weights)
                ?.solution ?: return flagged
            val medians = remaining.map { (id, list) ->
                val errors = list.map { (pixel, dir) ->
                    val p = fit.projection.directionToPixel(fit.rotEquToPano * dir)
                    // Nicht projizierbar (z.B. Gegenrichtung) = maximal falsch.
                    if (p == null) nullError else hypot((p.x - pixel.x).toDouble(), (p.y - pixel.y).toDouble())
                }
                id to median(errors)
            }
            val overallMedian = median(medians.map { it.second })
            val threshold = max(minError, MEDIAN_FACTOR * overallMedian)
            val worst = medians.maxByOrNull { it.second } ?: return flagged
            if (worst.second <= threshold) return flagged
            flagged += Outlier(worst.first, worst.second)
            remaining.removeAll { it.first == worst.first }
        }
        return flagged
    }

    /**
     * Median-Reprojektionsfehler (Bildpixel) JEDER übergebenen Kachel gegen EINEN gemeinsamen,
     * gewichteten Fit über alle Kacheln — anders als [flagOutliers] kein iteratives Ausreißer-
     * Entfernen, sondern ein einmaliger Fit + Fehler für ALLE Kacheln (auch die unauffälligen).
     * Grundlage für die Qualitäts-Einstufung im Kachel-Info-Popup. Unter [MIN_TILES] Kacheln
     * leere Map (nicht entscheidbar, s. Klassen-Dokumentation).
     */
    fun perTileError(
        tileAnchors: List<Pair<Long, List<Pair<Offset, Vec3>>>>,
        imageWidth: Int,
        imageHeight: Int,
        allowed: Set<PanoProjectionKind>,
    ): Map<Long, Double> {
        val usable = tileAnchors.filter { it.second.isNotEmpty() }
        if (usable.size < MIN_TILES) return emptyMap()
        val nullError = hypot(imageWidth.toDouble(), imageHeight.toDouble())
        val anchors = usable.flatMap { it.second }
        val weights = FisheyeRefiner.tileVoteWeights(usable.map { it.second.size })
        val fit = FisheyeRefiner.calibratePanorama(anchors, imageWidth, imageHeight, allowed, weights)
            ?.solution ?: return emptyMap()
        return usable.associate { (id, list) ->
            val errors = list.map { (pixel, dir) ->
                val p = fit.projection.directionToPixel(fit.rotEquToPano * dir)
                if (p == null) nullError else hypot((p.x - pixel.x).toDouble(), (p.y - pixel.y).toDouble())
            }
            id to median(errors)
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
