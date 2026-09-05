package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.SkyPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase-2-Verfeinerung des Fisheye-Solves – der literaturbelegte Offline-Weg
 * (A&A 2025/2019): detektierte Sterne im GANZEN Bild gegen einen Katalog cross-matchen
 * und ein radiales Verzeichnungsmodell + Orientierung global fitten.
 *
 * Ausgehend von der zentralen Startlösung (Phase 1) werden Katalogsterne projiziert,
 * den detektierten Blobs WECHSELSEITIG zugeordnet (Mutual-NN + Lowe-Ratio-Test) und
 * (cx, cy, f, k1, k2, k3, Rotation) per Levenberg-Marquardt (numerische Jacobi-Matrix)
 * minimiert. Die Parität (flipY) wird vom Bootstrap übernommen und unverändert beibehalten.
 * Ausreißer werden getrimmt. Das Ergebnis wird nur übernommen, wenn der RMS-Restfehler sinkt.
 */
object FisheyeRefiner {

    // Strafwert (Pixel) für Restfehler, wenn ein Ankerpunkt AUSSERHALB des Gültigkeitswinkels der
    // Projektion liegt (directionToPixel==null, z.B. jenseits ~85° bei Rectilinear). Groß genug, um
    // jeden echten Pixel-Restfehler zu dominieren, aber endlich (kein NaN/Infinity in der LM-Matrix).
    // Ohne diese Strafe sähe „außerhalb" für den Optimierer wie ein PERFEKTER Treffer aus (Restfehler
    // 0) -> keine Gradientenkraft, die Brennweite/Rotation zu korrigieren -> der Fit bleibt in einem
    // scheinbar guten, aber geometrisch falschen Minimum stecken (betrifft v.a. Rectilinear, dessen
    // enger Kegel diesen Fehlermodus zuerst offenlegt).
    private const val INVALID_PROJECTION_PENALTY = 1.0e5

    // Nachbedingung für jeden Fisheye-Fit (fitFixed/refine): r(θ)=f·(θ+k1θ³+k2θ⁵+k3θ⁷) ist für
    // beliebige k1/k2/k3 NICHT automatisch monoton -- ihre Ableitung r'(θ)=f·(1+3k1θ²+5k2θ⁴+7k3θ⁶)
    // kann für realistische Koeffizienten (z.B. k1=-0.23) schon deutlich vor der Antipode negativ
    // werden, wodurch weit entfernte Sterne auf einen kleinen, gültig aussehenden Bildradius
    // zurückfallen ("Spinnennetz"-Kollaps, mit genau diesem k1-Wert bereits in WcsSolutionTest/
    // AstapOverlayMapperTest bewiesen). directionToPixel prüft nur die Antipode, keine Monotonie --
    // das muss der FIT selbst sicherstellen, nicht die Projektionsklasse (die bewusst unverändert
    // bleibt, s. Kommentar an den beiden Aufrufstellen unten).
    private const val MONOTONIC_CHECK_MAX_THETA_DEG = 179

    /** r'(θ) auf [0°,179°] in 1°-Schritten durchgehend > 0 (r(0)=0 -> r(θ)>0 für alle θ>0 folgt mit)? */
    private fun isMonotonicFisheyeRadius(f: Double, k1: Double, k2: Double, k3: Double): Boolean {
        if (f <= 0.0) return false
        for (thetaDeg in 0..MONOTONIC_CHECK_MAX_THETA_DEG) {
            val theta = Math.toRadians(thetaDeg.toDouble())
            val t2 = theta * theta
            val deriv = f * (1.0 + 3.0 * k1 * t2 + 5.0 * k2 * t2 * t2 + 7.0 * k3 * t2 * t2 * t2)
            if (deriv <= 0.0) return false
        }
        return true
    }

    // Gewichtung des BIC-Strafterms in complexityPenalizedScore (k·ln(n)) ggü. dem Fit-Gütemaß
    // (2n·ln(rms)). 1.0 = unveränderte Literatur-BIC-Formel (Schwarz 1978) -- NICHT an echten
    // Gerätedaten verifiziert (s. "solvetiles_candidates"-Diagnose in StarMapperApp). Fällt Fisheye
    // auf dünnen/weit gestreuten Mehr-Kachel-Ankersets weiterhin zu leicht ins Auge: erhöhen.
    // Verliert ein echtes Fisheye-Objektiv-Foto (enges/mittleres FOV) dadurch fälschlich an
    // Stereographic/Zylinder-Modelle: verringern.
    private const val COMPLEXITY_PENALTY_WEIGHT = 1.0

    // Nutzer-Vorgabe 2026-08-28 (Runde 3, Punkt 1/2 -- Periodenfehler-Root-Cause): fitCylindrical()
    // fittet cx/cy/fx/fy/Rotation vollkommen frei per Levenberg-Marquardt gegen die beobachteten
    // Sternpositionen -- NICHTS in diesem Fit kennt/erzwingt, dass ein vollständiges 2:1-Equirectangular-
    // Panorama seine Textur-Periode exakt bei imageWidth hat (`horizontalPeriodPx = |fx*2π|` ist ein
    // reines FIT-Ergebnis, kein geometrischer Fakt). Für dieses Testfoto (6500x3250) ergab der freie Fit
    // horizontalPeriodPx=6477.4297 statt 6500 -- 22,57px Differenz, exakt der vom Nutzer gemessene
    // verbleibende DEC-Abschlussfehler in microGapFindings. FULL_PANORAMA_ASPECT_TOLERANCE: wie nah
    // imageWidth/imageHeight an exakt 2,0 liegen muss, um als "echtes vollständiges 2:1-Panorama" zu
    // gelten (2% -- ein sauber gestitchtes Panorama liegt normalerweise auf wenige Pixel genau bei
    // exakt 2:1, 2% ist großzügig genug für Rundungsreste beim Stitchen/Export, aber eng genug, um ein
    // zufällig ähnlich breites normales Foto nicht fälschlich als Panorama zu behandeln).
    private const val FULL_PANORAMA_ASPECT_TOLERANCE = 0.02

    // Wie viel relativer RMS-Anstieg akzeptiert wird, um die harte horizontalPeriodPx=imageWidth-
    // Nebenbedingung zu übernehmen (>1.0 = Verschlechterung erlaubt, 1.0 = keine). 1.10 = höchstens 10%
    // schlechter als der freie Fit -- bewusst konservativ ("ohne die Sternanpassung unnötig zu
    // verschlechtern", Nutzer-Vorgabe): die verbleibenden 6 freien Parameter (cx,cy,fy,Rotation) fangen
    // den Großteil der durch die fx-Fixierung "verlorenen" Fit-Freiheit i.d.R. wieder auf, wenn das Foto
    // tatsächlich sauber gestitcht ist -- ein GROSSER RMS-Sprung wäre dagegen ein Signal, dass das Foto
    // selbst nicht wirklich diese exakte Periode hat (z.B. echte Stitching-Verzerrung), dann bleibt der
    // freie Fit die bessere Wahl für die Sternpositionsgenauigkeit (Vorrang vor perfekter Netz-Topologie).
    private const val FULL_PANORAMA_PERIOD_MAX_RMS_RATIO = 1.10

    data class Result(
        val solution: PanoramaWcsSolution,
        val matchedStars: Int,
        val residualArcmin: Float,
        val improved: Boolean,
    )

    private const val PARAM_COUNT = 9 // cx, cy, f, k1, k2, k3, wx, wy, wz
    private const val ITERATIONS = 30
    private const val MIN_MATCHES = 10
    // Ratio-Test auf dem Abstandsquadrat: nächster Blob muss <= 0.5 * zweitnächster sein
    // (≈ 0.71 im Abstand) -> mehrdeutige Zuordnungen werden verworfen.
    private const val RATIO_SQ = 0.5

    fun refine(
        initial: PanoramaWcsSolution,
        catalog: List<Vec3>,
        detected: List<Offset>,
        imageWidth: Int,
        imageHeight: Int,
    ): Result {
        val proj0 = initial.projection as? FisheyeProjection
            ?: return Result(initial, 0, Float.NaN, false)
        if (catalog.size < MIN_MATCHES || detected.size < MIN_MATCHES) {
            return Result(initial, 0, Float.NaN, false)
        }

        val flip = proj0.flipY
        val diag = hypot(imageWidth.toDouble(), imageHeight.toDouble())
        val tolStart = 0.045 * diag
        val tolEnd = 0.008 * diag

        var cx = proj0.cx
        var cy = proj0.cy
        var f = proj0.f
        var k1 = proj0.k1
        var k2 = proj0.k2
        var k3 = proj0.k3
        var rot = initial.rotEquToPano

        val baseline = crossMatch(catalog, detected, cx, cy, f, k1, k2, k3, rot, tolStart, flip)
        if (baseline.size < MIN_MATCHES) return Result(initial, 0, Float.NaN, false)
        val baselineRms = rms(baseline, cx, cy, f, k1, k2, k3, rot, flip)
        var bestRms = baselineRms
        var lambda = 1e-3

        repeat(ITERATIONS) { iter ->
            val tol = tolStart + (tolEnd - tolStart) * (iter.toDouble() / (ITERATIONS - 1))
            var matches = crossMatch(catalog, detected, cx, cy, f, k1, k2, k3, rot, tol, flip)
            if (matches.size < MIN_MATCHES) return@repeat
            matches = trimOutliers(matches, cx, cy, f, k1, k2, k3, rot, flip)
            if (matches.size < MIN_MATCHES) return@repeat

            val r0 = residuals(matches, cx, cy, f, k1, k2, k3, rot, flip)
            val currentRms = rmsOf(r0)

            // Numerische Jacobi-Matrix (Vorwärtsdifferenzen) um den aktuellen Punkt.
            val steps = doubleArrayOf(
                0.5, 0.5, maxOf(1.0, f * 1e-3), 1e-4, 1e-4, 1e-4, 1e-4, 1e-4, 1e-4,
            )
            val n = r0.size
            val jac = Array(n) { DoubleArray(PARAM_COUNT) }
            for (j in 0 until PARAM_COUNT) {
                val rp = residualsPerturbed(matches, cx, cy, f, k1, k2, k3, rot, j, steps[j], flip)
                for (i in 0 until n) jac[i][j] = (rp[i] - r0[i]) / steps[j]
            }

            // Normalgleichungen JᵀJ Δ = -Jᵀ r, mit LM-Dämpfung auf der Diagonale.
            val jtj = Array(PARAM_COUNT) { DoubleArray(PARAM_COUNT) }
            val jtr = DoubleArray(PARAM_COUNT)
            for (i in 0 until n) {
                for (a in 0 until PARAM_COUNT) {
                    jtr[a] += jac[i][a] * r0[i]
                    for (b in 0 until PARAM_COUNT) jtj[a][b] += jac[i][a] * jac[i][b]
                }
            }
            val damped = Array(PARAM_COUNT) { a -> jtj[a].copyOf() }
            for (a in 0 until PARAM_COUNT) damped[a][a] += lambda * (jtj[a][a] + 1e-9)
            val rhs = DoubleArray(PARAM_COUNT) { -jtr[it] }
            val delta = solveLinear(damped, rhs) ?: run { lambda *= 4; return@repeat }

            // Kandidat anwenden.
            val nCx = cx + delta[0]
            val nCy = cy + delta[1]
            val nF = (f + delta[2]).coerceAtLeast(1.0)
            val nK1 = (k1 + delta[3]).coerceIn(-1.0, 1.0)
            val nK2 = (k2 + delta[4]).coerceIn(-1.0, 1.0)
            val nK3 = (k3 + delta[5]).coerceIn(-1.0, 1.0)
            val nRot = rotationVector(delta[6], delta[7], delta[8]) * rot
            val candidateRms = rms(matches, nCx, nCy, nF, nK1, nK2, nK3, nRot, flip)

            if (candidateRms < currentRms) {
                cx = nCx; cy = nCy; f = nF; k1 = nK1; k2 = nK2; k3 = nK3; rot = nRot
                bestRms = candidateRms
                lambda = (lambda / 3).coerceAtLeast(1e-9)
            } else {
                lambda = (lambda * 4).coerceAtMost(1e6)
            }
        }

        val finalMatches = crossMatch(catalog, detected, cx, cy, f, k1, k2, k3, rot, tolEnd, flip)
        // Monotonie-Pflicht: ein nachgeschärftes Polynom, das zwischen den Kachel-Ankern zurückfaltet,
        // darf nie übernommen werden, egal wie gut sein RMS an den geprüften Punkten aussieht -- sonst
        // bleibt die ursprüngliche (unverfeinerte, bereits vertrauenswürdige) Lösung erhalten.
        val improved = bestRms < baselineRms - 1e-6 && finalMatches.size >= MIN_MATCHES &&
            isMonotonicFisheyeRadius(f, k1, k2, k3)
        val arcminPerPx = (1.0 / f) * (180.0 / PI) * 60.0
        return if (improved) {
            Result(
                solution = PanoramaWcsSolution(FisheyeProjection(cx, cy, f, k1, k2, k3, flipY = flip), rot),
                matchedStars = finalMatches.size,
                residualArcmin = (bestRms * arcminPerPx).toFloat(),
                improved = true,
            )
        } else {
            Result(initial, baseline.size, (baselineRms * arcminPerPx).toFloat(), false)
        }
    }

    /**
     * Fittet die Fisheye-WCS an MANUELL gesetzte, garantiert korrekte Referenzen
     * (Bildpixel ↔ äquatoriale Katalog-Richtung). Robuster Vollbild-Weg der Allsky-Kalibrierung:
     * keine Cross-Match-Kontamination, kein Bootstrap-Pfad. Beide Paritäten werden probiert; die
     * mit kleinerem Reprojektions-Restfehler gewinnt (eine Spiegelung kann eine reine Rotation
     * nicht ausgleichen → muss explizit getestet werden).
     * @return gefittete Lösung oder null (zu wenige/unbrauchbare Referenzen).
     */
    fun calibrateFromReferences(
        references: List<Pair<Offset, Vec3>>,
        imageWidth: Int,
        imageHeight: Int,
    ): PanoramaWcsSolution? {
        if (references.size < 3) return null
        val matches = references.map { (pixel, dir) -> dir.normalized() to pixel }
        val focalEstimate = estimateFocal(matches) ?: return null
        return listOf(false, true)
            .mapNotNull { flip -> fitFixed(matches, imageWidth, imageHeight, flip, focalEstimate) }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * Baut aus einem BELIEBIGEN, bereits gelösten [baseFit] (jede der 6 Projektionsfamilien) einen
     * sauberen, garantiert nie zurückfaltenden (k=0) Fisheye-Ersatz-Seed für die Kalibrierungs-
     * Vorschau (FOV-Regler + Feinjustierung) -- unabhängig davon, welches Modell tatsächlich
     * gewonnen hat. Vorher wurde angenommen, "+Z im Pano-Frame" bedeute immer "Blickrichtung" --
     * das stimmt nur für die azimutale Familie (Fisheye/Stereographic/Rectilinear). Für die
     * Zylinder-Familie (Equirectangular/Cylindrical/Mercator) liegt die Bildmitte auf +X, +Z ist
     * deren Pol-/Hochachse -- ein daraus geerbtes rotEquToPano zeigte den Rückseiten-Cull dadurch
     * gegen die falsche Achse (Referenzsterne wie Polaris/Alioth landeten astronomisch unmöglich,
     * teils im fotografierten Vordergrund). Fix: die echte Blickrichtung wird über Pixel-Sonden UND
     * [baseFit]s EIGENE pixelToDirection/rotEquToPano-Konvention ermittelt (exakt das bereits
     * produktiv genutzte Muster aus TileDeWarp.eqDirAt) und daraus eine frische "Look-at"-Rotation
     * gebaut -- keine Fallunterscheidung nach Projektionstyp nötig.
     *
     * @param fovLongDeg FOV der langen Bildkante in Grad (Regler-Wert) -> Brennweite. `null` leitet
     *   die Brennweite stattdessen aus der lokalen Skala von [baseFit] nahe der Bildmitte ab.
     */
    fun equidistantSeedFrom(
        baseFit: PanoramaWcsSolution,
        imageWidth: Int,
        imageHeight: Int,
        fovLongDeg: Float? = null,
    ): PanoramaWcsSolution {
        val cx = imageWidth / 2.0
        val cy = imageHeight / 2.0
        val probe = min(imageWidth, imageHeight) * 0.05
        val fallbackF = { deg: Double -> max(imageWidth, imageHeight) / Math.toRadians(deg) }

        fun eqDirAt(px: Double, py: Double): Vec3? {
            val local = baseFit.projection.pixelToDirection(px, py) ?: return null
            return (baseFit.rotEquToPano.transpose() * local).normalized()
        }

        val z = eqDirAt(cx, cy)
        if (z == null) {
            // Pathologischer Fallback (Bildmitte liefert keine Richtung -- praktisch nie): altes
            // Verhalten nachbilden statt abzustürzen.
            val f = fovLongDeg?.let { fallbackF(it.toDouble()) } ?: fallbackF(120.0)
            return PanoramaWcsSolution(FisheyeProjection(cx, cy, f, 0.0, 0.0, 0.0, false), baseFit.rotEquToPano)
        }
        val eqRight = eqDirAt(cx + probe, cy)
        val eqUp = eqDirAt(cx, cy - probe)

        // "Look-at"-Rotation: z = Blickrichtung, x = "rechts" senkrecht zu z, y = z×x (rechtshändig,
        // bildet z korrekt auf (0,0,1) ab -- x/y tragen keine Spiegel-Mehrdeutigkeit, nur flipY unten).
        val rightPerp = eqRight
            ?.let { r -> Vec3(r.x - z.x * z.dot(r), r.y - z.y * z.dot(r), r.z - z.z * z.dot(r)) }
            ?.takeIf { it.length() > 1e-6 }
        val x = (rightPerp ?: run {
            // Entartungsfall (eqRight ~parallel zu z): Pol-Ausweichtrick, wie TileDeWarp.eastNorthBasis.
            val poleRef = if (abs(z.z) > 0.999) Vec3(0.0, 1.0, 0.0) else Vec3(0.0, 0.0, 1.0)
            cross(poleRef, z)
        }).normalized()
        val y = cross(z, x)
        val rot = Mat3(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z)

        val f = when {
            fovLongDeg != null -> fallbackF(fovLongDeg.toDouble())
            eqRight != null -> {
                val angleSep = acos(z.dot(eqRight).coerceIn(-1.0, 1.0))
                if (angleSep > 1e-9) probe / angleSep else fallbackF(120.0)
            }
            else -> fallbackF(120.0)
        }
        // Parität empirisch statt angenommen: "oben" (eqUp) muss im neuen Seed pixelY<cy ergeben.
        val flip = eqUp?.let { (rot * it).y > 0.0 } ?: false

        return PanoramaWcsSolution(FisheyeProjection(cx, cy, f, 0.0, 0.0, 0.0, flip), rot)
    }

    private fun cross(a: Vec3, b: Vec3) = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x,
    )

    /** Ergebnis der Panorama-Kalibrierung inkl. gewähltem Projektionsmodell + Restfehler (px). */
    data class PanoCalibration(
        val solution: PanoramaWcsSolution,
        val kind: PanoProjectionKind,
        val rms: Double,
    )

    /**
     * Stimmgewicht pro Ankerpunkt, damit jede Gruppe (Kachel) höchstens EIN volles Stimmgewicht
     * zum gemeinsamen Fit beiträgt — unabhängig davon, ob sie z.B. 9 (normales 3x3-Raster) oder 25
     * (De-Warp 5x5-Raster) Punkte liefert. Beide sind reine Abtastungen EINER bereits fertig
     * gelösten Kachel-WCS, keine unabhängigen Messungen -> ohne Normierung würde die dichter
     * abgetastete Kachel den gemeinsamen Fit proportional zur Rasterdichte dominieren, nicht zur
     * tatsächlichen Zuverlässigkeit. [floor] verhindert, dass eine stark rand-beschnittene Kachel
     * mit sehr wenigen überlebenden Punkten ÜBERPROPORTIONAL viel Gewicht pro Punkt bekommt.
     *
     * [reliability] (optional, 1:1 zu [groupSizes]) multipliziert zusätzlich ein Vertrauens-Gewicht
     * je Kachel hinein (s. TileConsistency.tileReliabilityWeights) -- unabhängig von der reinen
     * Raster-DICHTE oben: eine Kachel mit wenigen, aber unsicheren Sternpaarungen bekommt dadurch
     * zusätzlich weniger Stimme, nicht nur wegen ihrer Punktzahl. `null` (Default, jeder bestehende
     * Aufrufer) entspricht exakt dem bisherigen Verhalten. Ein zu kurzer/langer [reliability] wirft
     * NICHT (diese Funktion hat noch nie geworfen) -- fehlende Einträge zählen als volles Vertrauen.
     */
    fun tileVoteWeights(groupSizes: List<Int>, floor: Int = 9, reliability: List<Double>? = null): List<Double> =
        groupSizes.indices.flatMap { groupIndex ->
            val n = groupSizes[groupIndex]
            if (n <= 0) return@flatMap emptyList<Double>()
            val r = reliability?.getOrNull(groupIndex) ?: 1.0
            List(n) { r / maxOf(n, floor) }
        }

    /**
     * Wandelt die kachel-LOKALEN `.corr`-Solve-Treffer jeder gelösten (bereits Outlier-gefilterten)
     * Kachel in globale Bildpixel-Koordinaten um (`local + tileOffsetX/Y`) und fasst sie zu einem
     * gemeinsamen Pool zusammen -- Eingabe für die Ganzbild-Nachschärfung in solveAllTiles(). Nur
     * Kacheln aus [idToTileWcs] zählen (dort bereits Outlier-gefiltert); Einträge in [corrRefsById]
     * für andere/gelöschte/nicht mehr passende Kachel-IDs werden dabei automatisch ignoriert.
     */
    fun globalizeTileCorrRefs(
        idToTileWcs: List<Pair<Long, TileWcs>>,
        corrRefsById: Map<Long, List<Pair<Offset, Vec3>>>,
        // Obergrenze je Kachel. War 40 (Gerätebeleg 2026-07-27: 30-31 Kacheln / ~5500 Punkte ->
        // 66-71s Refit-Zeit) -- verwarf bei typischen Solves aber bis zu ~90% der echten Treffer
        // (Gerätebeleg 2026-07-29: eine Kachel mit 524 echten Treffern lieferte nur 40 an die
        // Nachschärfung). Bei 9 Kacheln lag die Refit-Zeit mit 40/Kachel (360 Punkte) bei nur 4.1s,
        // linear hochgerechnet bleibt 80/Kachel für ähnliche Kachelzahlen unkritisch. Gleichmäßiges
        // Sub-Sampling statt Abschneiden, um keine Bias durch die Erkennungsreihenfolge von
        // astrometry.net einzubauen.
        maxPerTile: Int = 80,
    ): List<Pair<Offset, Vec3>> = idToTileWcs.flatMap { (tileId, tw) ->
        val refs = corrRefsById[tileId] ?: emptyList()
        val sampled = if (refs.size <= maxPerTile) {
            refs
        } else {
            val stride = refs.size.toDouble() / maxPerTile
            (0 until maxPerTile).map { i -> refs[(i * stride).toInt().coerceAtMost(refs.size - 1)] }
        }
        // FIX (Untersuchungsauftrag 2026-08-31, Stage-2-Koordinatenfehler): für eine Tiny-Sky-Kachel
        // (tw.corrRefsAlreadyNative) ist [local] bereits eine native Pixel-Position (s. TileWcs-KDoc) --
        // die Addition von tileOffsetX/Y (dort nur eine grobe Mosaik-Fallback-Näherung, KEIN Kachel-
        // Ursprung) würde sie um genau diesen -- oft tausende Pixel großen -- Betrag verfälschen. Für
        // eine normale Kachel (tw.corrRefsAlreadyNative == false, unverändertes Verhalten) bleibt die
        // Addition unverändert nötig, da [local] dort weiterhin kachel-lokal ist.
        sampled.map { (local, dir) ->
            val global = if (tw.corrRefsAlreadyNative) local else Offset(local.x + tw.tileOffsetX, local.y + tw.tileOffsetY)
            global to dir
        }
    }

    /**
     * Baut `groupSizes`/`groupWeights` für [fitMesh] PARALLEL zu [globalizeTileCorrRefs]s flacher
     * Punktliste -- MUSS mit demselben [idToTileWcs]/[corrRefsById]/[maxPerTile] wie der zugehörige
     * [globalizeTileCorrRefs]-Aufruf aufgerufen werden, sonst verletzt das Ergebnis [fitMesh]s
     * `references.size == groupSizes.sum()`-Vorbedingung.
     *
     * [tileIds]/[reliability] wie von `TileConsistency.tileReliabilityWeights` geliefert (1:1
     * zueinander) -- NICHT notwendigerweise dieselbe Reihenfolge/Teilmenge wie [idToTileWcs] (das nur
     * Kacheln mit erfolgreich abgeleiteter eigener WCS enthält, s. solveAllTiles()): wird deshalb per
     * Kachel-ID neu ausgerichtet statt positional übernommen. Fehlt eine ID in [tileIds], zählt sie
     * als volles Vertrauen (1.0) -- dieselbe Konvention wie [tileVoteWeights].
     */
    fun corrRefGroupSizesAndWeights(
        idToTileWcs: List<Pair<Long, TileWcs>>,
        corrRefsById: Map<Long, List<Pair<Offset, Vec3>>>,
        tileIds: List<Long>,
        reliability: List<Double>,
        maxPerTile: Int = 80,
    ): Pair<List<Int>, List<Double>> {
        val reliabilityById = tileIds.zip(reliability).toMap()
        val sizes = ArrayList<Int>(idToTileWcs.size)
        val weights = ArrayList<Double>(idToTileWcs.size)
        for ((id, _) in idToTileWcs) {
            sizes += minOf(corrRefsById[id]?.size ?: 0, maxPerTile)
            weights += reliabilityById[id] ?: 1.0
        }
        return sizes to weights
    }

    /**
     * Freie Parameter (cx,cy,[fx,fy|f],[k1,k2,k3],3 Rotations-DOF) je Projektionsmodell -- Grundlage
     * für [complexityPenalizedScore]. Parität (flipY / fx-fy-Vorzeichen) ist KEIN zusätzlicher
     * Parameter: sie ist eine diskrete Vorauswahl INNERHALB eines kind (s. calibratePanorama unten),
     * ändert paramCountFor für beide/alle Varianten gleich und beeinflusst die finale Modellwahl
     * daher nicht.
     */
    private fun paramCountFor(kind: PanoProjectionKind): Int = when (kind) {
        PanoProjectionKind.Fisheye -> PARAM_COUNT // 9: cx, cy, f, k1, k2, k3, 3 Rotations-DOF
        PanoProjectionKind.Stereographic, PanoProjectionKind.Rectilinear -> 6 // cx, cy, f, 3 Rotations-DOF
        PanoProjectionKind.Equirectangular, PanoProjectionKind.Cylindrical,
        PanoProjectionKind.Mercator,
        -> 7 // cx, cy, fx, fy, 3 Rotations-DOF
        // Nur für Exhaustivität: Mesh nimmt NICHT an dieser BIC-Auswahl teil (s. Kommentar an
        // PanoProjectionKind.Mesh und an fitMesh()) -- calibratePanorama bekommt Mesh nie in `allowed`
        // übergeben, dieser Zweig wird also praktisch nie erreicht. Ein Parameterzähler ist für ein
        // nichtparametrisches, aus bereits unabhängig geprüften Kachel-Punkten aufgebautes Netz auch
        // konzeptionell keine sinnvolle Größe (die "Freiheitsgrade" sind an bereits woanders
        // validierten Daten festgemacht, nicht frei an DIESE Bewertungspunkte gefittet).
        PanoProjectionKind.Mesh -> PARAM_COUNT
    }

    /**
     * BIC-artiger Vergleichswert für die Modellwahl in calibratePanorama (kleiner = besser):
     * 2n·ln(rms) + λ·k·ln(n) -- n = Ankerzahl (references.size, über alle Kandidaten EINES Aufrufs
     * konstant), k = paramCountFor(kind), λ = COMPLEXITY_PENALTY_WEIGHT. Bestraft ein Modell mit mehr
     * freien Parametern (v.a. Fisheye mit 9 ggü. 6-7 bei den übrigen) dafür, dass es bei dünnen/weit
     * gestreuten Ankersets seinen kleineren RMS oft nur durch Überanpassung statt durch tatsächlich
     * besseren geometrischen Fit erreicht ("Spinnennetz"-Kollaps-Vorstufe). Reine interne
     * Vergleichsgröße; PanoCalibration.rms bleibt unverändert der ehrliche, ungewichtete
     * Pixel-Restfehler. rms=0.0 (perfekter synthetischer Fit) ergibt ln(rms)=-Infinity -- ein
     * gültiger, total geordneter Double-Wert (kein NaN), ein (nahezu) perfekter Fit gewinnt dadurch
     * immer, wie beabsichtigt.
     */
    private fun complexityPenalizedScore(rms: Double, kind: PanoProjectionKind, n: Int): Double =
        2.0 * n * ln(rms) + COMPLEXITY_PENALTY_WEIGHT * paramCountFor(kind) * ln(n.toDouble())

    /**
     * Geometrische Plausibilität des Bild-FUSSABDRUCKS eines Kandidaten (Nutzer-Auftrag 2026-09-03, A4).
     *
     * Hintergrund (Gerätebefund): ein Mercator-Fit gewann die Auswahl mit dem besten RMS auf den Ankern,
     * bildete danach aber PRAKTISCH DEN GANZEN HIMMEL ins Bildrechteck ab (`constellation_audit ...
     * not_in_fov=0` bei einem normalen 6392x4101-Foto). Das ist kein Bug in der FOV-Prüfung, sondern
     * echte Geometrie eines entarteten Fits: die Cylindrical-Familie bildet mit `x = cx + fx*lambda` ab,
     * die volle Azimut-Umrundung belegt also nur `2*PI*fx` Pixel. Wird der Bildausschnitt beim Fit nahe
     * den Mercator-Pol gelegt (dort ist `asinh(tan(phi))` extrem expansiv), passt derselbe kleine
     * Ankersatz auch mit WINZIGEM `fx` -- und dann liegt der komplette Himmel innerhalb weniger tausend
     * Pixel. Der Fit ist auf den Ankern gut und trotzdem als Kameramodell absurd.
     *
     * Messgrößen, beide rein geometrisch (keine Sternbild-Zählung, keine Namensliste):
     * - [angularSpanDeg]: größter paarweiser Winkelabstand der Richtungen, auf die das BILDRECHTECK
     *   (Rand + Mitte) unter diesem Modell abbildet -- "wie viel Himmel behauptet das Modell zu zeigen".
     * - [supportSpanDeg]: derselbe Wert für die tatsächlich GELÖSTEN Anker -- "wie viel Himmel ist
     *   wirklich belegt".
     * - [wrapDetected]: das Bild enthält mindestens eine volle Azimut-Umrundung (nur bei periodischen
     *   Projektionen möglich) -- rein informativ, allein KEIN Ausschlussgrund (ein echtes 360°-Panorama
     *   tut das zu Recht).
     *
     * [plausible] hängt bewusst NICHT an einem getunten Winkel-Schwellwert, sondern an einer
     * strukturellen Aussage:
     *
     *   abgelehnt genau dann, wenn (a) EINE VOLLE Azimut-Umrundung in den Bildbereich passt, den die
     *   Sternbild-Platzierung als "nah am Bild" wertet ([wrapDetected]; nur bei periodischen
     *   Projektionen überhaupt möglich, `horizontalPeriodPx <= 2*imageWidth` -- exakt der ±50%-Überstand
     *   aus `AstapOverlayMapper.createConstellationOverlays`), UND (b) die gelösten Anker selbst nicht
     *   einmal die halbe Umrundung belegen ([supportAzimuthSpanDeg] < 180°).
     *
     * Damit ist ein echtes 360°-Panorama nie betroffen (dort belegen die Kacheln den vollen Azimut), und
     * Fisheye/Stereographic/Rectilinear können strukturell gar nicht betroffen sein (`horizontalPeriodPx`
     * ist dort `null` -- kein Wrap, keine Ablehnung). Betroffen ist ausschließlich die Cylindrical-
     * Familie in genau dem Fall, in dem sie den ganzen Himmel ins Bild faltet, ohne dass die Daten das
     * hergeben. [angularSpanDeg]/[supportSpanDeg] werden weiterhin gemessen und protokolliert, gehen aber
     * NICHT in die Entscheidung ein (reine Transparenz-Größen).
     */
    data class FootprintSanity(
        val angularSpanDeg: Double,
        val supportSpanDeg: Double,
        val supportAzimuthSpanDeg: Double,
        val wrapDetected: Boolean,
        val plausible: Boolean,
        val reason: String,
    )

    private const val FOOTPRINT_MAX_SAMPLED_REFERENCES = 200

    /** Azimut-Abdeckung (Grad) der Anker IM PANO-RAHMEN des Kandidaten: 360° minus der größten Lücke
     *  auf dem Kreis. Weniger als 2 Anker -> 0. */
    private fun azimuthSpanDeg(lambdasDeg: List<Double>): Double {
        if (lambdasDeg.size < 2) return 0.0
        val sorted = lambdasDeg.map { ((it % 360.0) + 360.0) % 360.0 }.sorted()
        var largestGap = 360.0 - (sorted.last() - sorted.first())
        for (i in 1 until sorted.size) {
            val gap = sorted[i] - sorted[i - 1]
            if (gap > largestGap) largestGap = gap
        }
        return (360.0 - largestGap).coerceIn(0.0, 360.0)
    }

    private fun maxPairwiseAngleDeg(dirs: List<Vec3>): Double {
        var best = 0.0
        for (i in dirs.indices) {
            for (j in i + 1 until dirs.size) {
                val dot = (dirs[i].x * dirs[j].x + dirs[i].y * dirs[j].y + dirs[i].z * dirs[j].z)
                    .coerceIn(-1.0, 1.0)
                val a = Math.toDegrees(acos(dot))
                if (a > best) best = a
            }
        }
        return best
    }

    fun evaluateFootprint(
        calibration: PanoCalibration,
        references: List<Pair<Offset, Vec3>>,
        imageWidth: Int,
        imageHeight: Int,
    ): FootprintSanity {
        val proj = calibration.solution.projection
        val w = imageWidth.toDouble()
        val h = imageHeight.toDouble()
        val samples = ArrayList<Vec3>(25)
        for (iy in 0..4) {
            for (ix in 0..4) {
                proj.pixelToDirection(w * ix / 4.0, h * iy / 4.0)?.let { samples += it.normalized() }
            }
        }
        // "Nah am Bild" ist in AstapOverlayMapper.createConstellationOverlays [-0.5W, 1.5W], also 2W
        // breit -- passt eine volle Umrundung (horizontalPeriodPx) da hinein, gilt JEDE Himmelsrichtung
        // als bildnah. Genau das ist die beobachtete Pathologie (not_in_fov=0).
        val wrapDetected = proj.horizontalPeriodPx()?.let { it <= 2.0 * w } == true
        val rot = calibration.solution.rotEquToPano
        // maxPairwiseAngleDeg ist O(n^2) und diese Bewertung läuft pro Kandidat (also bis zu 7x je Fit,
        // auch bei jedem Projektionswechsel). Bei sehr vielen Ankern gleichmäßig ausdünnen -- für eine
        // Spannweiten-/Abdeckungs-Aussage völlig ausreichend, hält die Kosten konstant.
        val stride = maxOf(1, references.size / FOOTPRINT_MAX_SAMPLED_REFERENCES)
        val sampledRefs = references.filterIndexed { i, _ -> i % stride == 0 }
        val supportAzimuthSpan = azimuthSpanDeg(
            sampledRefs.map { (_, dir) ->
                val p = rot * dir.normalized()
                Math.toDegrees(kotlin.math.atan2(p.y, p.x))
            },
        )
        val angularSpan = if (samples.size >= 3) maxPairwiseAngleDeg(samples) else 0.0
        val supportSpan = maxPairwiseAngleDeg(sampledRefs.map { it.second.normalized() })
        // Ein Bild mit echtem 2:1-Seitenverhältnis IST ein Vollpanorama -- dort ist eine volle
        // Umrundung im Bild per Definition richtig, auch wenn (noch) nur wenige, gebündelte Kacheln
        // gelöst sind. Diese Klasse wird deshalb NIE ausgeschlossen: sonst könnte ein sparsam gekacheltes
        // Panorama plötzlich Fisheye statt Cylindrical/Equirectangular wählen und damit die
        // eingefrorene Gradnetz-/Perioden-Mechanik (enforceFullPanoramaPeriod) umgehen.
        val looksLikeFullPanorama = h > 0.0 && abs(w / h - 2.0) <= FULL_PANORAMA_ASPECT_TOLERANCE
        val implausible = wrapDetected && !looksLikeFullPanorama && supportAzimuthSpan < 180.0
        return FootprintSanity(
            angularSpanDeg = angularSpan,
            supportSpanDeg = supportSpan,
            supportAzimuthSpanDeg = supportAzimuthSpan,
            wrapDetected = wrapDetected,
            plausible = !implausible,
            reason = when {
                implausible -> "full_azimuth_wrap_inside_image_without_anchor_support"
                wrapDetected -> "ok_full_wrap_but_anchors_cover_azimuth"
                else -> "ok"
            },
        )
    }

    /**
     * Fittet aus festen Referenzen (Pixel ↔ äquatoriale Richtung) das am besten passende
     * Panorama-Modell aus [allowed] und gibt das mit dem kleinsten Reprojektions-Restfehler zurück.
     * So funktioniert es für echte Fisheye-Objektive UND für gestitchte Panoramen (Equirectangular/
     * Zylindrisch/Mercator, bis 360°) – ohne dass der Nutzer die Projektion kennen muss (Auto), bzw.
     * mit `allowed = {einKind}` als manueller Override.
     */
    fun calibratePanorama(
        references: List<Pair<Offset, Vec3>>,
        imageWidth: Int,
        imageHeight: Int,
        allowed: Set<PanoProjectionKind>,
        // Optionales Stimmgewicht pro Referenz (s. [tileVoteWeights]), 1:1 zu references. Wirkt NUR
        // auf die interne Fit-Kostenfunktion; der zurückgegebene PanoCalibration.rms bleibt der
        // ehrliche, ungewichtete Restfehler. null (Default) = heutiges, ungewichtetes Verhalten.
        weights: List<Double>? = null,
        // Rein diagnostisch (ändert kein Verhalten, Default = No-Op): meldet für JEDEN versuchten
        // Kandidaten das Ergebnis (RMS bei Erfolg, null bei Fit-Fehlschlag) — damit sich bei "Auto"
        // im Nachhinein prüfen lässt, ob z. B. Fisheye/Rectilinear für ein bestimmtes Anker-Set
        // schlicht scheitern (statt nur knapp zu verlieren), bevor an der Auswahl-Logik gedreht wird.
        onCandidate: (PanoProjectionKind, Double?) -> Unit = { _, _ -> },
        // Diagnose-Rückkanal für die Fußabdruck-Plausibilität je Kandidat (s. evaluateFootprint) --
        // Default = No-Op, verändert für keinen bestehenden Aufrufer etwas.
        onFootprint: (PanoProjectionKind, FootprintSanity) -> Unit = { _, _ -> },
        // Nutzer-Vorgabe 2026-08-28 (Runde 3, Punkt 1/2): `false` (Default) = bisheriges, unverändertes
        // Verhalten für ALLE bestehenden Aufrufer (Kachel-lokale Fits, Positions-/Feinjustier-Hinweise,
        // Ausreißer-/Qualitäts-Scoring -- s. [[project_gradnetz_randbeschriftung]] für die vollständige
        // Aufrufstellen-Analyse, WARUM dieser Parameter bewusst opt-in statt automatisch aus
        // imageWidth/imageHeight hergeleitet ist: 11 von 12 bestehenden Aufrufstellen bekommen bereits
        // die vollen Bild-Maße übergeben, auch reine Zwischen-/Scoring-Fits -- ein automatischer
        // Dimensions-Check allein würde die Nebenbedingung dort ungewollt mit-aktivieren). `true` NUR an
        // den Aufrufstellen, deren Ergebnis tatsächlich zur finalen, nutzersichtbaren WCS wird -- prüft
        // dann INTERN (s. u.), ob [imageWidth]/[imageHeight] überhaupt einem vollständigen 2:1-Panorama
        // entsprechen, bevor irgendetwas erzwungen wird.
        enforceFullPanoramaPeriod: Boolean = false,
    ): PanoCalibration? {
        if (references.size < 3 || allowed.isEmpty()) return null
        if (weights != null && weights.size != references.size) return null
        val matches = references.map { (pixel, dir) -> dir.normalized() to pixel }
        // EINMAL berechnet statt (vor 0.22.24) bis zu 18x redundant je Sub-Fit-Versuch unten --
        // estimateFocal() ist ein O(n²)-Allpaar-Vergleich über dieselbe, hier konstante matches-Liste,
        // hängt nicht von kind/flip/Vorzeichen ab. Scheitert die Schätzung, scheitern ohnehin alle
        // Sub-Fits genauso (identisches Verhalten zu vorher, nur ohne die 18 vergeblichen Versuche).
        val focalEstimate = estimateFocal(matches) ?: return null
        val weightArr = weights?.toDoubleArray()
        val out = ArrayList<PanoCalibration>()
        if (PanoProjectionKind.Fisheye in allowed) {
            val best = listOf(false, true)
                .mapNotNull { flip -> fitFixed(matches, imageWidth, imageHeight, flip, focalEstimate, weightArr) }
                .minByOrNull { it.second }
            onCandidate(PanoProjectionKind.Fisheye, best?.second)
            best?.let { out += PanoCalibration(it.first, PanoProjectionKind.Fisheye, it.second) }
        }
        if (PanoProjectionKind.Stereographic in allowed) {
            val best = listOf(false, true)
                .mapNotNull { flip -> fitAzimuthal(matches, imageWidth, imageHeight, flip, ::StereographicProjection, focalEstimate, weightArr) }
                .minByOrNull { it.second }
            onCandidate(PanoProjectionKind.Stereographic, best?.second)
            best?.let { out += PanoCalibration(it.first, PanoProjectionKind.Stereographic, it.second) }
        }
        if (PanoProjectionKind.Rectilinear in allowed) {
            val best = listOf(false, true)
                .mapNotNull { flip -> fitAzimuthal(matches, imageWidth, imageHeight, flip, ::RectilinearProjection, focalEstimate, weightArr) }
                .minByOrNull { it.second }
            onCandidate(PanoProjectionKind.Rectilinear, best?.second)
            best?.let { out += PanoCalibration(it.first, PanoProjectionKind.Rectilinear, it.second) }
        }
        // Parität über die Vorzeichen von fx/fy (horizontal/vertikal gespiegelt) abdecken.
        val signs = listOf(1.0 to -1.0, -1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)
        for (kind in listOf(
            PanoProjectionKind.Equirectangular,
            PanoProjectionKind.Cylindrical,
            PanoProjectionKind.Mercator,
        )) {
            if (kind !in allowed) continue
            val best = signs.mapNotNull { (sx, sy) -> fitCylindrical(matches, kind, imageWidth, imageHeight, sx, sy, focalEstimate, weightArr) }
                .minByOrNull { it.second }
            onCandidate(kind, best?.second)
            best?.let { out += PanoCalibration(it.first, kind, it.second) }
        }
        val n = references.size
        // A4 (Nutzer-Auftrag 2026-09-03): geometrisch offensichtlich unplausible Kandidaten dürfen die
        // AUTOMATIK nicht gewinnen (s. evaluateFootprint). Bewusst als reiner AUSSCHLUSS formuliert, nie
        // als Bevorzugung: bleibt nach dem Filter kein Kandidat übrig, wird der ungefilterte Satz
        // verwendet -- dadurch bleibt eine MANUELLE Einzelwahl (allowed enthält dann nur diese eine
        // Modellart, z. B. Mercator) immer anwendbar, und ein Bild, für das schlicht kein plausibles
        // Modell existiert, verliert nicht seine Lösung. Für jeden Kandidaten mit Fit wird die Bewertung
        // an [onFootprint] gemeldet (Diagnose, kein Einfluss auf das Ergebnis).
        val sanityByKind = out.associate { cal -> cal.kind to evaluateFootprint(cal, references, imageWidth, imageHeight) }
        sanityByKind.forEach { (kind, s) -> onFootprint(kind, s) }
        val plausibleOut = out.filter { sanityByKind[it.kind]?.plausible != false }
        val pool = if (plausibleOut.isNotEmpty()) plausibleOut else out
        val winner = pool.minByOrNull { complexityPenalizedScore(it.rms, it.kind, n) } ?: return null
        if (!enforceFullPanoramaPeriod) return winner
        return applyFullPanoramaPeriodConstraint(winner, matches, imageWidth, imageHeight, focalEstimate, weightArr)
    }

    /**
     * Nutzer-Vorgabe 2026-08-28 (Runde 3, Punkt 1/2 -- "Periodenfehler"): [winner]s freier Fit lässt
     * `horizontalPeriodPx` (= `|fx*2π|`) irgendwo nahe, aber nicht exakt bei der tatsächlichen
     * Textur-Periode landen -- bei einem ECHTEN vollständigen 2:1-Equirectangular-Panorama ist diese
     * Periode aber KEIN Fit-Ergebnis, sondern eine geometrische Tatsache: 360° horizontal ↔ exakt
     * [imageWidth] Pixel. Erzwingt `fx = ±imageWidth/(2π)` als harte Nebenbedingung und fittet die
     * übrigen 6 Parameter (cx,cy,fy,Rotation) frei darum herum -- NUR wenn (a) [winner] überhaupt aus
     * der Zylindrischen Familie kommt (Equirectangular/Cylindrical/Mercator, s.
     * [CylindricalProjection] -- die einzige Familie mit einem `fx`/einer horizontalen Periode) UND
     * (b) [imageWidth]/[imageHeight] tatsächlich (innerhalb [FULL_PANORAMA_ASPECT_TOLERANCE]) einem
     * vollständigen 2:1-Bild entsprechen (sonst bliebe [winner] unverändert -- ein normales Einzelfoto
     * oder ein partielles Panorama darf NICHT künstlich auf eine 360°-Periode gezwungen werden).
     * Übernimmt das eingeschränkte Ergebnis nur, wenn dessen RMS nicht mehr als
     * [FULL_PANORAMA_PERIOD_MAX_RMS_RATIO] schlechter ist als [winner]s freier RMS -- sonst bleibt
     * [winner] unverändert (Vorrang für die Sternpositionsgenauigkeit vor perfekter Netz-Topologie,
     * falls das Foto selbst nicht exakt periodisch ist). Fittet bewusst DIESELBE [winner].kind (keine
     * erneute Modellwahl) und DASSELBE fx/fy-Vorzeichenpaar wie [winner] (aus dessen bereits gefittetem
     * `CylindricalProjection` ausgelesen) -- kein zweiter, unabhängiger Vorzeichen-Suchlauf nötig.
     */
    private fun applyFullPanoramaPeriodConstraint(
        winner: PanoCalibration,
        matches: List<Pair<Vec3, Offset>>,
        imageWidth: Int,
        imageHeight: Int,
        focalEstimate: Double,
        weightArr: DoubleArray?,
    ): PanoCalibration {
        val proj = winner.solution.projection as? CylindricalProjection ?: return winner
        if (imageWidth <= 0 || imageHeight <= 0) return winner
        val aspect = imageWidth.toDouble() / imageHeight.toDouble()
        if (abs(aspect - 2.0) > FULL_PANORAMA_ASPECT_TOLERANCE) return winner
        val fxSign = if (proj.fx >= 0.0) 1.0 else -1.0
        val fySign = if (proj.fy >= 0.0) 1.0 else -1.0
        val fixedFx = fxSign * imageWidth.toDouble() / (2.0 * PI)
        val constrained = fitCylindricalConstrainedFx(
            matches, winner.kind, imageWidth, imageHeight, fixedFx, fySign, focalEstimate, weightArr,
        ) ?: return winner
        val (constrainedSolution, constrainedRms) = constrained
        if (!constrainedRms.isFinite()) return winner
        return if (constrainedRms <= winner.rms * FULL_PANORAMA_PERIOD_MAX_RMS_RATIO) {
            PanoCalibration(constrainedSolution, winner.kind, constrainedRms)
        } else {
            winner
        }
    }

    /**
     * Reprojektions-RMS von [fit] über einen beliebigen Anker-Satz (Pixel, Himmelsrichtung) —
     * unabhängig davon, ob/wie [fit] selbst gefittet wurde. Für das Fallback-Gate der Ganzbild-
     * Nachschärfung (s. StarMapperApp.solveAllTiles): prüft, ob eine neu gefittete Lösung noch zu
     * bereits bekannten, vertrauenswürdigen Ankern (z. B. den ursprünglichen Kachel-Ankern) passt.
     * Punkte außerhalb der Projektions-Gültigkeit zählen als Strafwert (nicht als 0/"perfekt") —
     * dieselbe Logik wie [INVALID_PROJECTION_PENALTY] bei den gewichteten Fits.
     *
     * Untersuchungs-/Reparaturauftrag 2026-08-31 (RichCorrMesh-Seam-Verdacht): [pixel] (die tatsächlich
     * GEMESSENE Position) wird jetzt als `reference` an [PanoramaProjection.directionToPixel] durchgereicht
     * statt (wie zuvor) immer den `atan2`-Hauptzweig zu nehmen -- bei periodischen Cylindrical-Projektionen
     * wird der Azimut dadurch auf den zu [pixel] NÄCHSTEN Ast entfaltet (identisches, bereits etabliertes
     * Prinzip wie überall sonst in diesem Projekt bei periodischen Projektionen, s.
     * [PanoramaProjection.directionToPixel]-KDoc). Ein Stern nahe der 0°/360°-Bild-Naht bekam bisher u.U.
     * eine Vorhersage auf der GEGENÜBERLIEGENDEN Bildseite (Fehler bis zu einer vollen horizontalen Periode)
     * -- verfälschte NICHT nur RichCorrMesh (baselineRms/trainRms/crossValRms, s. [RichCorrMesh.fit]),
     * sondern JEDEN Aufrufer dieser Funktion (u.a. `global_refine`s Sanity-/Match-RMS-Gates in
     * `StarMapperApp.kt`). Für NICHT-periodische Projektionen (TAN/Fisheye/Stereographic/Rectilinear) ist
     * `directionToPixel(dir, reference)` per Interface-Default IDENTISCH zu `directionToPixel(dir)` --
     * keine Verhaltensänderung dort, reine Korrektur für die periodische Cylindrical-Familie.
     */
    fun reprojectionRms(fit: PanoramaWcsSolution, refs: List<Pair<Offset, Vec3>>): Double {
        if (refs.isEmpty()) return Double.MAX_VALUE
        var sum = 0.0
        for ((pixel, dir) in refs) {
            val p = fit.projection.directionToPixel(fit.rotEquToPano * dir, pixel)
            val d = if (p == null) {
                INVALID_PROJECTION_PENALTY
            } else {
                hypot((p.x - pixel.x).toDouble(), (p.y - pixel.y).toDouble())
            }
            sum += d * d
        }
        return sqrt(sum / refs.size)
    }

    /**
     * Baut ein [PanoProjectionKind.Mesh]-Modell als glatte KORREKTUR über dem bereits gefitteten
     * [baseline]-Modell (s. [CorrectedProjection]), NICHT als eigenständigen Formel-Ersatz. Zwei
     * verworfene Vorläufer (Git-Historie): ein eigenständiges Dreiecksnetz OHNE Baseline
     * (Gerätebeleg 2026-08-16: RMS 44.590 bzw. 70.710 statt 40/87 der starren Modelle -- kollabierte
     * bei weiten, dünn gekachelten Panoramen auf Strafwerte) und danach eine Baseline-Korrektur, die
     * ihre räumliche Zuordnung noch per Dreiecksnetz im gnomonischen Tangentialraum traf (Gerätebeleg
     * 2026-08-16, zweiter Test: sicher, aber blieb konstant ~2% hinter dem starren Gewinner zurück --
     * s. [CorrectedProjection]s Klassenkommentar für die Begründung, warum dieser Raum das deckelt).
     *
     * [references]/[groupSizes] wie zuvor: (Bild-Pixel, ÄQUATORIALE Richtung) flach über alle
     * Kacheln, gruppiert nach Herkunfts-Kachel (1:1 zu [tileVoteWeights]s gleichnamigem Parameter) --
     * Grundlage sowohl für [CorrectedProjection]s Kachel-Declustering als auch für die
     * Leave-One-Tile-Out-Kreuzvalidierung unten. [baseline] ist der Gewinner der starren
     * 6-Modell-Auswahl (s. [calibratePanorama]); dessen Rotation ([PanoramaWcsSolution.rotEquToPano])
     * wird UNVERÄNDERT übernommen -- die Korrektur lebt im selben Pano-Frame wie die Baseline, die
     * sie ergänzt.
     *
     * [PanoCalibration.rms] wird per Leave-One-Tile-Out ermittelt: für jede Kachel abwechselnd deren
     * Punkte weglassen, aus dem Rest neu korrigieren, an genau diesen weggelassenen Punkten per
     * [reprojectionRms] prüfen -- eine EHRLICHE Schätzung, wie gut die Korrektur eine Region
     * vorhersagt, die sie beim Training nicht gesehen hat (genau der Fall, der beim späteren Rendern
     * ZWISCHEN den Kacheln auftritt). Da [CorrectedProjection] außerhalb ihrer Abdeckung graceful auf
     * [baseline] zurückfällt, ist auch eine "schlecht abgedeckte" Kreuzvalidierungs-Runde höchstens
     * so ungenau wie [baseline] selbst.
     *
     * Gibt null zurück, wenn zu wenige/zu einseitig verteilte Punkte vorliegen (u.a. bei nur EINER
     * Kachel-Gruppe: dann bleibt bei jeder Kreuzvalidierungs-Runde kein Trainingsrest übrig), oder
     * wenn [baseline] an KEINEM einzigen Kontrollpunkt selbst eine Vorhersage liefert (dann gibt es
     * nichts, das sich korrigieren ließe).
     *
     * [groupWeights] (optional, 1:1 zu [groupSizes], auch für leere Gruppen): Vertrauens-Gewicht je
     * Kachel-Gruppe (s. TileConsistency.tileReliabilityWeights) -- fließt in [CorrectedProjection]s
     * Umgebungs-Mittelung ein, damit eine unsichere Kachel dort proportional weniger Einfluss hat.
     * `null` (Default) entspricht dem bisherigen, ungewichteten Verhalten. Bei Größen-Mismatch (wie
     * bei [calibratePanorama]s `weights`) null-artig behandelt (kein Absturz, s. buildCorrectedProjection).
     */
    fun fitMesh(
        references: List<Pair<Offset, Vec3>>,
        groupSizes: List<Int>,
        baseline: PanoramaWcsSolution,
        groupWeights: List<Double>? = null,
    ): PanoCalibration? {
        if (references.size < 3 || references.size != groupSizes.sum()) return null
        val weights = groupWeights?.takeIf { it.size == groupSizes.size }
        val rot = baseline.rotEquToPano
        val corrected = buildCorrectedProjection(references, groupSizes, rot, baseline.projection, weights) ?: return null
        // groupWeights ist 1:1 zu groupSizes (auch leere Gruppen); für die Kreuzvalidierung auf nur
        // die nicht-leeren Gruppen ausdünnen, exakt wie groupRanges() das für die Indizes selbst tut.
        val rangeWeights = weights?.let { w -> groupSizes.indices.filter { groupSizes[it] > 0 }.map { w[it] } }
        val rms = meshLeaveOneTileOutRms(references, groupRanges(groupSizes), rot, baseline.projection, rangeWeights)
            ?: return null
        return PanoCalibration(PanoramaWcsSolution(corrected, rot), PanoProjectionKind.Mesh, rms)
    }

    /**
     * [CorrectedProjection] aus [references] (äquatorial, wird hier ins Pano-Frame dieser Baseline
     * gedreht) + [groupSizes] (Kachel-Declustering, s. [CorrectedProjection]s Klassenkommentar) über
     * [basisProjection] -- oder null, wenn KEIN einziger Kontrollpunkt eine Basis-Vorhersage bekam
     * (dann bliebe nur eine leere Korrektur, nicht der Rede wert). [groupWeights] s. [fitMesh].
     */
    private fun buildCorrectedProjection(
        references: List<Pair<Offset, Vec3>>,
        groupSizes: List<Int>,
        rot: Mat3,
        basisProjection: PanoramaProjection,
        groupWeights: List<Double>? = null,
    ): CorrectedProjection? {
        val panoRefs = references.map { (px, dir) -> px to (rot * dir.normalized()) }
        val corrected = if (groupWeights != null) {
            CorrectedProjection(basisProjection, panoRefs, groupSizes, groupWeights)
        } else {
            CorrectedProjection(basisProjection, panoRefs, groupSizes)
        }
        return if (corrected.nodeCount == 0) null else corrected
    }

    /** Index-Bereiche je Kachel-Gruppe innerhalb der flachen references-Liste (s. [fitMesh]). */
    private fun groupRanges(groupSizes: List<Int>): List<IntRange> {
        val ranges = ArrayList<IntRange>(groupSizes.size)
        var start = 0
        for (size in groupSizes) {
            if (size > 0) ranges += start until (start + size)
            start += size
        }
        return ranges
    }

    /**
     * Leave-One-Tile-Out-RMS für eine aus [references] über [basisProjection] gebaute Korrektur, s.
     * Kommentar an [fitMesh]. Pro Runde wird eine [tileRanges]-Gruppe komplett weggelassen und aus
     * den VERBLEIBENDEN Gruppen (samt ihrer eigenen Größen, fürs erneute Declustering in
     * [buildCorrectedProjection]) neu korrigiert -- schlägt das fehl (z.B. bleibt nur eine einzige
     * Gruppe mit brauchbaren Punkten übrig), wird diese Runde übersprungen statt mit einem
     * Strafwert gezählt. [rangeWeights] (optional, 1:1 zu [tileRanges]) s. [fitMesh]s [groupWeights] --
     * die zurückgehaltene Gruppe selbst wird bewusst UNGEWICHTET geprüft (nur der Trainingseinfluss
     * wird gewichtet), damit [PanoCalibration.rms] wie bisher der ehrliche, ungewichtete Restfehler
     * bleibt (s. [complexityPenalizedScore]s Kommentar zu diesem Prinzip).
     */
    private fun meshLeaveOneTileOutRms(
        references: List<Pair<Offset, Vec3>>,
        tileRanges: List<IntRange>,
        rot: Mat3,
        basisProjection: PanoramaProjection,
        rangeWeights: List<Double>? = null,
    ): Double? {
        var sumSq = 0.0
        var count = 0
        for ((rangeIndex, heldOutRange) in tileRanges.withIndex()) {
            val heldOut = heldOutRange.map { references[it] }
            val trainIndices = tileRanges.indices.filter { it != rangeIndex }
            val trainRanges = trainIndices.map { tileRanges[it] }
            val train = trainRanges.flatMap { r -> r.map { references[it] } }
            val trainGroupSizes = trainRanges.map { it.last - it.first + 1 }
            val trainWeights = rangeWeights?.let { w -> trainIndices.map { w[it] } }
            val trainCorrected = buildCorrectedProjection(train, trainGroupSizes, rot, basisProjection, trainWeights)
                ?: continue
            val foldRms = reprojectionRms(PanoramaWcsSolution(trainCorrected, rot), heldOut)
            if (foldRms.isFinite()) {
                sumSq += foldRms * foldRms * heldOut.size
                count += heldOut.size
            }
        }
        return if (count > 0) sqrt(sumSq / count) else null
    }

    /** LM-Fit der zylindrischen Familie (cx,cy,fx,fy,Rotation) für eine Vorzeichen-/Paritätswahl. */
    private fun fitCylindrical(
        matches: List<Pair<Vec3, Offset>>,
        kind: PanoProjectionKind,
        imageWidth: Int,
        imageHeight: Int,
        fxSign: Double,
        fySign: Double,
        focalEstimate: Double,
        weights: DoubleArray? = null,
    ): Pair<PanoramaWcsSolution, Double>? {
        val paramCount = 7
        var cx = imageWidth / 2.0
        var cy = imageHeight / 2.0
        val scale = focalEstimate
        var fx = fxSign * scale
        var fy = fySign * scale
        var rot = initRotationCyl(matches, cx, cy, fx, fy, kind, weights) ?: return null
        var bestCost = rmsCyl(matches, cx, cy, fx, fy, kind, rot, weights)
        var lambda = 1e-3
        repeat(80) {
            val r0 = residualsCyl(matches, cx, cy, fx, fy, kind, rot)
            val n = r0.size
            if (n < paramCount) return@repeat
            val steps = doubleArrayOf(
                0.5, 0.5,
                maxOf(1.0, kotlin.math.abs(fx) * 1e-3),
                maxOf(1.0, kotlin.math.abs(fy) * 1e-3),
                1e-4, 1e-4, 1e-4,
            )
            val jac = Array(n) { DoubleArray(paramCount) }
            for (j in 0 until paramCount) {
                val rp = residualsCylPerturbed(matches, cx, cy, fx, fy, kind, rot, j, steps[j])
                for (i in 0 until n) jac[i][j] = (rp[i] - r0[i]) / steps[j]
            }
            val jtj = Array(paramCount) { DoubleArray(paramCount) }
            val jtr = DoubleArray(paramCount)
            for (i in 0 until n) {
                val w = weights?.get(i / 2) ?: 1.0
                for (a in 0 until paramCount) {
                    jtr[a] += w * jac[i][a] * r0[i]
                    for (b in 0 until paramCount) jtj[a][b] += w * jac[i][a] * jac[i][b]
                }
            }
            val damped = Array(paramCount) { a -> jtj[a].copyOf() }
            for (a in 0 until paramCount) damped[a][a] += lambda * (jtj[a][a] + 1e-9)
            val delta = solveLinear(damped, DoubleArray(paramCount) { -jtr[it] })
                ?: run { lambda *= 4; return@repeat }
            val nCx = cx + delta[0]
            val nCy = cy + delta[1]
            val nFx = fx + delta[2]
            val nFy = fy + delta[3]
            val nRot = rotationVector(delta[4], delta[5], delta[6]) * rot
            val candidate = rmsCyl(matches, nCx, nCy, nFx, nFy, kind, nRot, weights)
            if (candidate < bestCost) {
                cx = nCx; cy = nCy; fx = nFx; fy = nFy; rot = nRot
                bestCost = candidate
                lambda = (lambda / 3).coerceAtLeast(1e-9)
            } else {
                lambda = (lambda * 4).coerceAtMost(1e6)
            }
        }
        val finalRms = rmsCyl(matches, cx, cy, fx, fy, kind, rot)
        if (!finalRms.isFinite()) return null
        return PanoramaWcsSolution(CylindricalProjection(cx, cy, fx, fy, kind), rot) to finalRms
    }

    /**
     * Wie [fitCylindrical], aber [fixedFx] wird NIE gestört/aktualisiert -- exakt derselbe LM-Algorithmus,
     * nur um den fx-Freiheitsgrad reduziert (paramCount 7 -> 6: cx,cy,fy,Rotation). Nutzt
     * [residualsCylPerturbed] weiter (dessen paramIndex-Zuordnung 0=cx,1=cy,2=fx,3=fy,4..6=Rotation NICHT
     * verändert wurde), überspringt darin aber Index 2 (fx) -- [realParamIndex] bildet die LOKALEN
     * Indizes 0..5 dieser Funktion auf die entsprechenden ECHTEN Indizes [0,1,3,4,5,6] ab. Eigenständige
     * Funktion statt eines Flags in [fitCylindrical] selbst -- hält den bereits bewährten, unveränderten
     * freien Fit für ALLE anderen Aufrufer komplett unangetastet (Null-Risiko dort).
     */
    private fun fitCylindricalConstrainedFx(
        matches: List<Pair<Vec3, Offset>>,
        kind: PanoProjectionKind,
        imageWidth: Int,
        imageHeight: Int,
        fixedFx: Double,
        fySign: Double,
        focalEstimate: Double,
        weights: DoubleArray? = null,
    ): Pair<PanoramaWcsSolution, Double>? {
        val paramCount = 6
        val realParamIndex = intArrayOf(0, 1, 3, 4, 5, 6)
        var cx = imageWidth / 2.0
        var cy = imageHeight / 2.0
        val fx = fixedFx
        var fy = fySign * focalEstimate
        var rot = initRotationCyl(matches, cx, cy, fx, fy, kind, weights) ?: return null
        var bestCost = rmsCyl(matches, cx, cy, fx, fy, kind, rot, weights)
        var lambda = 1e-3
        repeat(80) {
            val r0 = residualsCyl(matches, cx, cy, fx, fy, kind, rot)
            val n = r0.size
            if (n < paramCount) return@repeat
            val steps = doubleArrayOf(
                0.5, 0.5,
                maxOf(1.0, kotlin.math.abs(fy) * 1e-3),
                1e-4, 1e-4, 1e-4,
            )
            val jac = Array(n) { DoubleArray(paramCount) }
            for (j in 0 until paramCount) {
                val rp = residualsCylPerturbed(matches, cx, cy, fx, fy, kind, rot, realParamIndex[j], steps[j])
                for (i in 0 until n) jac[i][j] = (rp[i] - r0[i]) / steps[j]
            }
            val jtj = Array(paramCount) { DoubleArray(paramCount) }
            val jtr = DoubleArray(paramCount)
            for (i in 0 until n) {
                val w = weights?.get(i / 2) ?: 1.0
                for (a in 0 until paramCount) {
                    jtr[a] += w * jac[i][a] * r0[i]
                    for (b in 0 until paramCount) jtj[a][b] += w * jac[i][a] * jac[i][b]
                }
            }
            val damped = Array(paramCount) { a -> jtj[a].copyOf() }
            for (a in 0 until paramCount) damped[a][a] += lambda * (jtj[a][a] + 1e-9)
            val delta = solveLinear(damped, DoubleArray(paramCount) { -jtr[it] })
                ?: run { lambda *= 4; return@repeat }
            val nCx = cx + delta[0]
            val nCy = cy + delta[1]
            val nFy = fy + delta[2]
            val nRot = rotationVector(delta[3], delta[4], delta[5]) * rot
            val candidate = rmsCyl(matches, nCx, nCy, fx, nFy, kind, nRot, weights)
            if (candidate < bestCost) {
                cx = nCx; cy = nCy; fy = nFy; rot = nRot
                bestCost = candidate
                lambda = (lambda / 3).coerceAtLeast(1e-9)
            } else {
                lambda = (lambda * 4).coerceAtMost(1e6)
            }
        }
        val finalRms = rmsCyl(matches, cx, cy, fx, fy, kind, rot)
        if (!finalRms.isFinite()) return null
        return PanoramaWcsSolution(CylindricalProjection(cx, cy, fx, fy, kind), rot) to finalRms
    }

    private fun initRotationCyl(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, fx: Double, fy: Double, kind: PanoProjectionKind,
        weights: DoubleArray? = null,
    ): Mat3? {
        val proj = CylindricalProjection(cx, cy, fx, fy, kind)
        val corr = matches.withIndex().mapNotNull { (idx, pair) ->
            val (dir, obs) = pair
            val cam = proj.pixelToDirection(obs.x.toDouble(), obs.y.toDouble()) ?: return@mapNotNull null
            RotationFit.Correspondence(equatorial = dir, pano = cam, weight = weights?.get(idx) ?: 1.0)
        }
        // solveRobust (Huber-IRLS) statt solve(): daempft einzelne Ausreisser unter den
        // automatisch erkannten Matches, statt sie mit vollem Gewicht in den LM-Startwert
        // einfliessen zu lassen (s. Kommentar bei solveRobust). Nur der Startwert -- die
        // nachfolgende LM-Verfeinerung (repeat(80) oben) bleibt unveraendert.
        return RotationFit.solveRobust(corr)?.rotation
    }

    private fun projectCyl(
        eq: Vec3, cx: Double, cy: Double, fx: Double, fy: Double, kind: PanoProjectionKind, rot: Mat3,
    ): Offset? = CylindricalProjection(cx, cy, fx, fy, kind).directionToPixel(rot * eq)

    private fun residualsCyl(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, fx: Double, fy: Double, kind: PanoProjectionKind, rot: Mat3,
    ): DoubleArray {
        val out = DoubleArray(matches.size * 2)
        for (i in matches.indices) {
            val (eq, obs) = matches[i]
            val p = projectCyl(eq, cx, cy, fx, fy, kind, rot)
            if (p == null) {
                // NICHT als perfekter Treffer (Restfehler 0) werten -> siehe INVALID_PROJECTION_PENALTY.
                out[2 * i] = INVALID_PROJECTION_PENALTY; out[2 * i + 1] = INVALID_PROJECTION_PENALTY
            } else {
                out[2 * i] = (p.x - obs.x).toDouble()
                out[2 * i + 1] = (p.y - obs.y).toDouble()
            }
        }
        return out
    }

    private fun rmsCyl(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, fx: Double, fy: Double, kind: PanoProjectionKind, rot: Mat3,
        weights: DoubleArray? = null,
    ): Double = rmsOf(residualsCyl(matches, cx, cy, fx, fy, kind, rot), weights)

    private fun residualsCylPerturbed(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, fx: Double, fy: Double, kind: PanoProjectionKind, rot: Mat3,
        paramIndex: Int, step: Double,
    ): DoubleArray = when (paramIndex) {
        0 -> residualsCyl(matches, cx + step, cy, fx, fy, kind, rot)
        1 -> residualsCyl(matches, cx, cy + step, fx, fy, kind, rot)
        2 -> residualsCyl(matches, cx, cy, fx + step, fy, kind, rot)
        3 -> residualsCyl(matches, cx, cy, fx, fy + step, kind, rot)
        4 -> residualsCyl(matches, cx, cy, fx, fy, kind, rotationVector(step, 0.0, 0.0) * rot)
        5 -> residualsCyl(matches, cx, cy, fx, fy, kind, rotationVector(0.0, step, 0.0) * rot)
        else -> residualsCyl(matches, cx, cy, fx, fy, kind, rotationVector(0.0, 0.0, step) * rot)
    }

    /** LM-Fit (cx,cy,f,k1,k2,k3,Rotation) an feste Korrespondenzen für eine Parität. */
    private fun fitFixed(
        matches: List<Pair<Vec3, Offset>>,
        imageWidth: Int,
        imageHeight: Int,
        flipY: Boolean,
        focalEstimate: Double,
        weights: DoubleArray? = null,
    ): Pair<PanoramaWcsSolution, Double>? {
        var cx = imageWidth / 2.0
        var cy = imageHeight / 2.0
        var f = focalEstimate
        var k1 = 0.0
        var k2 = 0.0
        var k3 = 0.0
        var rot = initRotation(matches, cx, cy, f, flipY, weights) ?: return null

        var bestCost = rms(matches, cx, cy, f, k1, k2, k3, rot, flipY, weights)
        var lambda = 1e-3
        repeat(80) {
            val r0 = residuals(matches, cx, cy, f, k1, k2, k3, rot, flipY)
            val n = r0.size
            if (n < PARAM_COUNT) return@repeat
            val steps = doubleArrayOf(
                0.5, 0.5, maxOf(1.0, f * 1e-3), 1e-4, 1e-4, 1e-4, 1e-4, 1e-4, 1e-4,
            )
            val jac = Array(n) { DoubleArray(PARAM_COUNT) }
            for (j in 0 until PARAM_COUNT) {
                val rp = residualsPerturbed(matches, cx, cy, f, k1, k2, k3, rot, j, steps[j], flipY)
                for (i in 0 until n) jac[i][j] = (rp[i] - r0[i]) / steps[j]
            }
            val jtj = Array(PARAM_COUNT) { DoubleArray(PARAM_COUNT) }
            val jtr = DoubleArray(PARAM_COUNT)
            for (i in 0 until n) {
                val w = weights?.get(i / 2) ?: 1.0
                for (a in 0 until PARAM_COUNT) {
                    jtr[a] += w * jac[i][a] * r0[i]
                    for (b in 0 until PARAM_COUNT) jtj[a][b] += w * jac[i][a] * jac[i][b]
                }
            }
            val damped = Array(PARAM_COUNT) { a -> jtj[a].copyOf() }
            for (a in 0 until PARAM_COUNT) damped[a][a] += lambda * (jtj[a][a] + 1e-9)
            val delta = solveLinear(damped, DoubleArray(PARAM_COUNT) { -jtr[it] })
                ?: run { lambda *= 4; return@repeat }
            val nCx = cx + delta[0]
            val nCy = cy + delta[1]
            val nF = (f + delta[2]).coerceAtLeast(1.0)
            val nK1 = (k1 + delta[3]).coerceIn(-1.0, 1.0)
            val nK2 = (k2 + delta[4]).coerceIn(-1.0, 1.0)
            val nK3 = (k3 + delta[5]).coerceIn(-1.0, 1.0)
            val nRot = rotationVector(delta[6], delta[7], delta[8]) * rot
            val candidate = rms(matches, nCx, nCy, nF, nK1, nK2, nK3, nRot, flipY, weights)
            if (candidate < bestCost) {
                cx = nCx; cy = nCy; f = nF; k1 = nK1; k2 = nK2; k3 = nK3; rot = nRot
                bestCost = candidate
                lambda = (lambda / 3).coerceAtLeast(1e-9)
            } else {
                lambda = (lambda * 4).coerceAtMost(1e6)
            }
        }
        val finalRms = rms(matches, cx, cy, f, k1, k2, k3, rot, flipY)
        // Kandidat verwerfen statt eines zurückfaltenden Polynoms zurückzugeben -- s. Kommentar an
        // isMonotonicFisheyeRadius. calibratePanorama/calibrateFromReferences behandeln null bereits
        // als "dieser Kandidat/diese Parität ist gescheitert" (mapNotNull), keine Aufrufer-Änderung nötig.
        if (!isMonotonicFisheyeRadius(f, k1, k2, k3)) return null
        return PanoramaWcsSolution(FisheyeProjection(cx, cy, f, k1, k2, k3, flipY = flipY), rot) to finalRms
    }

    /** LM-Fit (cx,cy,f,Rotation) der stereografischen Projektion für eine Parität. */
    // Generischer Azimutal-Fit (cx,cy,f,Rotation) für eine gegebene Projektions-Factory (Stereographic
    // ODER Rectilinear – beide haben denselben Parametersatz cx,cy,f,flipY). Ersetzt den früheren
    // stereographie-spezifischen Fit; Rectilinear teilt sich denselben Code.
    private fun fitAzimuthal(
        matches: List<Pair<Vec3, Offset>>,
        imageWidth: Int,
        imageHeight: Int,
        flipY: Boolean,
        makeProj: (Double, Double, Double, Boolean) -> PanoramaProjection,
        focalEstimate: Double,
        weights: DoubleArray? = null,
    ): Pair<PanoramaWcsSolution, Double>? {
        val paramCount = 6
        var cx = imageWidth / 2.0
        var cy = imageHeight / 2.0
        var f = focalEstimate
        var rot = initRotationAzimuthal(matches, cx, cy, f, flipY, makeProj, weights) ?: return null
        var bestCost = rmsAzimuthal(matches, cx, cy, f, rot, flipY, makeProj, weights)
        var lambda = 1e-3
        repeat(80) {
            val r0 = residualsAzimuthal(matches, cx, cy, f, rot, flipY, makeProj)
            val n = r0.size
            if (n < paramCount) return@repeat
            val steps = doubleArrayOf(0.5, 0.5, maxOf(1.0, f * 1e-3), 1e-4, 1e-4, 1e-4)
            val jac = Array(n) { DoubleArray(paramCount) }
            for (j in 0 until paramCount) {
                val rp = residualsAzimuthalPerturbed(matches, cx, cy, f, rot, j, steps[j], flipY, makeProj)
                for (i in 0 until n) jac[i][j] = (rp[i] - r0[i]) / steps[j]
            }
            val jtj = Array(paramCount) { DoubleArray(paramCount) }
            val jtr = DoubleArray(paramCount)
            for (i in 0 until n) {
                val w = weights?.get(i / 2) ?: 1.0
                for (a in 0 until paramCount) {
                    jtr[a] += w * jac[i][a] * r0[i]
                    for (b in 0 until paramCount) jtj[a][b] += w * jac[i][a] * jac[i][b]
                }
            }
            val damped = Array(paramCount) { a -> jtj[a].copyOf() }
            for (a in 0 until paramCount) damped[a][a] += lambda * (jtj[a][a] + 1e-9)
            val delta = solveLinear(damped, DoubleArray(paramCount) { -jtr[it] })
                ?: run { lambda *= 4; return@repeat }
            val nCx = cx + delta[0]
            val nCy = cy + delta[1]
            val nF = (f + delta[2]).coerceAtLeast(1.0)
            val nRot = rotationVector(delta[3], delta[4], delta[5]) * rot
            val candidate = rmsAzimuthal(matches, nCx, nCy, nF, nRot, flipY, makeProj, weights)
            if (candidate < bestCost) {
                cx = nCx; cy = nCy; f = nF; rot = nRot
                bestCost = candidate
                lambda = (lambda / 3).coerceAtLeast(1e-9)
            } else {
                lambda = (lambda * 4).coerceAtMost(1e6)
            }
        }
        val finalRms = rmsAzimuthal(matches, cx, cy, f, rot, flipY, makeProj)
        if (!finalRms.isFinite()) return null
        return PanoramaWcsSolution(makeProj(cx, cy, f, flipY), rot) to finalRms
    }

    private fun initRotationAzimuthal(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, flipY: Boolean,
        makeProj: (Double, Double, Double, Boolean) -> PanoramaProjection,
        weights: DoubleArray? = null,
    ): Mat3? {
        val proj = makeProj(cx, cy, f, flipY)
        val corr = matches.withIndex().mapNotNull { (idx, pair) ->
            val (dir, obs) = pair
            val cam = proj.pixelToDirection(obs.x.toDouble(), obs.y.toDouble()) ?: return@mapNotNull null
            RotationFit.Correspondence(equatorial = dir, pano = cam, weight = weights?.get(idx) ?: 1.0)
        }
        // solveRobust (Huber-IRLS) statt solve(): s. Kommentar in initRotationCyl.
        return RotationFit.solveRobust(corr)?.rotation
    }

    private fun projectAzimuthal(
        eq: Vec3, cx: Double, cy: Double, f: Double, rot: Mat3, flipY: Boolean,
        makeProj: (Double, Double, Double, Boolean) -> PanoramaProjection,
    ): Offset? = makeProj(cx, cy, f, flipY).directionToPixel(rot * eq)

    private fun residualsAzimuthal(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, rot: Mat3, flipY: Boolean,
        makeProj: (Double, Double, Double, Boolean) -> PanoramaProjection,
    ): DoubleArray {
        val out = DoubleArray(matches.size * 2)
        for (i in matches.indices) {
            val (eq, obs) = matches[i]
            val p = projectAzimuthal(eq, cx, cy, f, rot, flipY, makeProj)
            if (p == null) {
                // NICHT als perfekter Treffer (Restfehler 0) werten -> siehe INVALID_PROJECTION_PENALTY.
                out[2 * i] = INVALID_PROJECTION_PENALTY; out[2 * i + 1] = INVALID_PROJECTION_PENALTY
            } else {
                out[2 * i] = (p.x - obs.x).toDouble()
                out[2 * i + 1] = (p.y - obs.y).toDouble()
            }
        }
        return out
    }

    private fun residualsAzimuthalPerturbed(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, rot: Mat3, paramIndex: Int, step: Double, flipY: Boolean,
        makeProj: (Double, Double, Double, Boolean) -> PanoramaProjection,
    ): DoubleArray = when (paramIndex) {
        0 -> residualsAzimuthal(matches, cx + step, cy, f, rot, flipY, makeProj)
        1 -> residualsAzimuthal(matches, cx, cy + step, f, rot, flipY, makeProj)
        2 -> residualsAzimuthal(matches, cx, cy, f + step, rot, flipY, makeProj)
        3 -> residualsAzimuthal(matches, cx, cy, f, rotationVector(step, 0.0, 0.0) * rot, flipY, makeProj)
        4 -> residualsAzimuthal(matches, cx, cy, f, rotationVector(0.0, step, 0.0) * rot, flipY, makeProj)
        else -> residualsAzimuthal(matches, cx, cy, f, rotationVector(0.0, 0.0, step) * rot, flipY, makeProj)
    }

    private fun rmsAzimuthal(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, rot: Mat3, flipY: Boolean,
        makeProj: (Double, Double, Double, Boolean) -> PanoramaProjection,
        weights: DoubleArray? = null,
    ): Double = rmsOf(residualsAzimuthal(matches, cx, cy, f, rot, flipY, makeProj), weights)

    /** Grobe f-Schätzung: Pixelabstand/Winkelabstand des am weitesten getrennten Paares. */
    private fun estimateFocal(matches: List<Pair<Vec3, Offset>>): Double? {
        var bestPx = 0.0
        var bestF: Double? = null
        for (i in matches.indices) {
            for (j in i + 1 until matches.size) {
                val pi = matches[i].second
                val pj = matches[j].second
                val pxDist = hypot((pi.x - pj.x).toDouble(), (pi.y - pj.y).toDouble())
                if (pxDist <= bestPx) continue
                val ang = acos(matches[i].first.normalized().dot(matches[j].first.normalized()).coerceIn(-1.0, 1.0))
                if (ang > 1e-4) {
                    bestPx = pxDist
                    bestF = pxDist / ang
                }
            }
        }
        return bestF?.takeIf { it.isFinite() && it > 1.0 }
    }

    /** Start-Rotation per Wahba: Kamerarichtungen der Referenzpixel ↔ Katalogrichtungen. */
    private fun initRotation(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, flipY: Boolean,
        weights: DoubleArray? = null,
    ): Mat3? {
        val proj = FisheyeProjection(cx, cy, f, flipY = flipY)
        val corr = matches.withIndex().mapNotNull { (idx, pair) ->
            val (dir, obs) = pair
            val cam = proj.pixelToDirection(obs.x.toDouble(), obs.y.toDouble()) ?: return@mapNotNull null
            RotationFit.Correspondence(equatorial = dir, pano = cam, weight = weights?.get(idx) ?: 1.0)
        }
        // solveRobust (Huber-IRLS) statt solve(): s. Kommentar in initRotationCyl.
        return RotationFit.solveRobust(corr)?.rotation
    }

    // --- Hilfsfunktionen -----------------------------------------------------

    private fun project(
        eq: Vec3, cx: Double, cy: Double, f: Double, k1: Double, k2: Double, k3: Double, rot: Mat3,
        flipY: Boolean,
    ): Offset? = FisheyeProjection(cx, cy, f, k1, k2, k3, flipY = flipY).directionToPixel(rot * eq)

    private fun crossMatch(
        catalog: List<Vec3>, detected: List<Offset>,
        cx: Double, cy: Double, f: Double, k1: Double, k2: Double, k3: Double, rot: Mat3,
        tol: Double, flipY: Boolean,
    ): List<Pair<Vec3, Offset>> {
        val tolSq = tol * tol
        val proj = ArrayList<Pair<Vec3, Offset>>(catalog.size)
        for (eq in catalog) {
            val p = project(eq, cx, cy, f, k1, k2, k3, rot, flipY) ?: continue
            proj.add(eq to p)
        }
        if (proj.isEmpty() || detected.isEmpty()) return emptyList()

        val projNearestBlob = IntArray(proj.size) { -1 }
        val projNearestDsq = DoubleArray(proj.size) { Double.MAX_VALUE }
        val projSecondDsq = DoubleArray(proj.size) { Double.MAX_VALUE }
        val blobNearestProj = IntArray(detected.size) { -1 }
        val blobNearestDsq = DoubleArray(detected.size) { Double.MAX_VALUE }
        for (pi in proj.indices) {
            val pp = proj[pi].second
            for (bi in detected.indices) {
                val d = detected[bi]
                val dx = (pp.x - d.x).toDouble()
                val dy = (pp.y - d.y).toDouble()
                val dsq = dx * dx + dy * dy
                if (dsq < projNearestDsq[pi]) {
                    projSecondDsq[pi] = projNearestDsq[pi]
                    projNearestDsq[pi] = dsq
                    projNearestBlob[pi] = bi
                } else if (dsq < projSecondDsq[pi]) {
                    projSecondDsq[pi] = dsq
                }
                if (dsq < blobNearestDsq[bi]) { blobNearestDsq[bi] = dsq; blobNearestProj[bi] = pi }
            }
        }
        val out = ArrayList<Pair<Vec3, Offset>>()
        for (pi in proj.indices) {
            val bi = projNearestBlob[pi]
            if (bi >= 0 && projNearestDsq[pi] <= tolSq && blobNearestProj[bi] == pi) {
                // Ratio-Test (Lowe): nur akzeptieren, wenn der nächste Blob DEUTLICH näher ist
                // als der zweitnächste – mehrdeutige Zuordnungen im dichten Feld werden verworfen.
                if (projNearestDsq[pi] <= RATIO_SQ * projSecondDsq[pi]) {
                    out.add(proj[pi].first to detected[bi])
                }
            }
        }
        return out
    }

    /**
     * Gemeinsamer Kern von [crossMatchSolution]/[crossMatchWcs]: wechselseitige Nächster-Nachbar-
     * Zuordnung (Mutual-NN) + Lowe-Ratio-Test zwischen bereits projizierten Katalogpunkten und
     * erkannten Bild-Blobs, unabhängig davon, WIE die Projektion zustande kam (Panorama-Modell oder
     * generisches WcsSolutionLike) — identischer Algorithmus wie [crossMatch].
     */
    private fun matchProjectedToBlobs(
        proj: List<Pair<Vec3, Offset>>,
        detected: List<Offset>,
        tol: Double,
    ): List<Pair<Vec3, Offset>> {
        if (proj.isEmpty() || detected.isEmpty()) return emptyList()
        val tolSq = tol * tol
        val projNearestBlob = IntArray(proj.size) { -1 }
        val projNearestDsq = DoubleArray(proj.size) { Double.MAX_VALUE }
        val projSecondDsq = DoubleArray(proj.size) { Double.MAX_VALUE }
        val blobNearestProj = IntArray(detected.size) { -1 }
        val blobNearestDsq = DoubleArray(detected.size) { Double.MAX_VALUE }
        for (pi in proj.indices) {
            val pp = proj[pi].second
            for (bi in detected.indices) {
                val d = detected[bi]
                val dx = (pp.x - d.x).toDouble()
                val dy = (pp.y - d.y).toDouble()
                val dsq = dx * dx + dy * dy
                if (dsq < projNearestDsq[pi]) {
                    projSecondDsq[pi] = projNearestDsq[pi]
                    projNearestDsq[pi] = dsq
                    projNearestBlob[pi] = bi
                } else if (dsq < projSecondDsq[pi]) {
                    projSecondDsq[pi] = dsq
                }
                if (dsq < blobNearestDsq[bi]) { blobNearestDsq[bi] = dsq; blobNearestProj[bi] = pi }
            }
        }
        val out = ArrayList<Pair<Vec3, Offset>>()
        for (pi in proj.indices) {
            val bi = projNearestBlob[pi]
            if (bi >= 0 && projNearestDsq[pi] <= tolSq && blobNearestProj[bi] == pi) {
                // Ratio-Test (Lowe): wie crossMatch, mehrdeutige Zuordnungen im dichten Feld verworfen.
                if (projNearestDsq[pi] <= RATIO_SQ * projSecondDsq[pi]) {
                    out.add(proj[pi].first to detected[bi])
                }
            }
        }
        return out
    }

    /**
     * Wie [crossMatch], aber generisch über die PanoramaProjection-Schnittstelle statt hart an
     * FisheyeProjection gebunden — funktioniert dadurch für jedes der 6 PanoProjectionKind-
     * Modelle. Für die Ganzbild-Nachschärfung des Kachel-Fits (s. StarMapperApp.solveAllTiles):
     * projiziert Katalogsterne über die bereits gefittete [seed]-Lösung und ordnet sie den
     * erkannten Bild-Blobs wechselseitig zu (Mutual-NN + Lowe-Ratio-Test, identischer Algorithmus
     * wie [crossMatch]).
     */
    fun crossMatchSolution(
        seed: PanoramaWcsSolution,
        catalog: List<Vec3>,
        detected: List<Offset>,
        tol: Double,
    ): List<Pair<Vec3, Offset>> {
        val proj = catalog.mapNotNull { eq ->
            seed.projection.directionToPixel(seed.rotEquToPano * eq)?.let { eq to it }
        }
        return matchProjectedToBlobs(proj, detected, tol)
    }

    /**
     * Wie [crossMatchSolution], aber generisch über [WcsSolutionLike] statt [PanoramaWcsSolution] --
     * funktioniert dadurch AUCH für eine einfache Kachel-WCS (TAN+SIP, kein Panorama-Modell). Für
     * die Pro-Kachel-RMS-Kennzahl im Kachel-Info-Popup (s. StarMapperApp.solveAllTiles): projiziert
     * Katalogsterne über die bereits gelöste Kachel-eigene [wcs] und ordnet sie den in der
     * Kachel-Bounding-Box erkannten Bild-Blobs wechselseitig zu.
     */
    fun crossMatchWcs(
        wcs: WcsSolutionLike,
        catalog: List<Vec3>,
        detected: List<Offset>,
        imageHeight: Int,
        tol: Double,
    ): List<Pair<Vec3, Offset>> {
        val proj = catalog.mapNotNull { eq ->
            val (raDeg, decDeg) = vectorToRaDec(eq)
            wcs.skyToImage(SkyPoint(raDeg.toFloat(), decDeg.toFloat()), imageHeight)?.let { eq to it }
        }
        return matchProjectedToBlobs(proj, detected, tol)
    }

    /**
     * Wie [reprojectionRms], aber generisch über [WcsSolutionLike] -- Gegenstück zu [crossMatchWcs]
     * für die eigentliche RMS-Berechnung der so gefundenen Treffer.
     */
    fun reprojectionRmsWcs(wcs: WcsSolutionLike, refs: List<Pair<Offset, Vec3>>, imageHeight: Int): Double {
        if (refs.isEmpty()) return Double.MAX_VALUE
        var sum = 0.0
        for ((pixel, dir) in refs) {
            val (raDeg, decDeg) = vectorToRaDec(dir)
            val p = wcs.skyToImage(SkyPoint(raDeg.toFloat(), decDeg.toFloat()), imageHeight)
            val d = if (p == null) {
                INVALID_PROJECTION_PENALTY
            } else {
                hypot((p.x - pixel.x).toDouble(), (p.y - pixel.y).toDouble())
            }
            sum += d * d
        }
        return sqrt(sum / refs.size)
    }

    private fun trimOutliers(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, k1: Double, k2: Double, k3: Double, rot: Mat3,
        flipY: Boolean,
    ): List<Pair<Vec3, Offset>> {
        val dists = matches.map { (eq, obs) ->
            val p = project(eq, cx, cy, f, k1, k2, k3, rot, flipY) ?: return@map Double.MAX_VALUE
            hypot((p.x - obs.x).toDouble(), (p.y - obs.y).toDouble())
        }
        val sorted = dists.sorted()
        val median = if (sorted.isEmpty()) 0.0 else sorted[sorted.size / 2]
        val limit = (2.5 * median).coerceAtLeast(2.0)
        return matches.filterIndexed { i, _ -> dists[i] <= limit }
    }

    private fun residuals(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, k1: Double, k2: Double, k3: Double, rot: Mat3,
        flipY: Boolean,
    ): DoubleArray {
        val out = DoubleArray(matches.size * 2)
        for (i in matches.indices) {
            val (eq, obs) = matches[i]
            val p = project(eq, cx, cy, f, k1, k2, k3, rot, flipY)
            if (p == null) {
                out[2 * i] = 0.0; out[2 * i + 1] = 0.0
            } else {
                out[2 * i] = (p.x - obs.x).toDouble()
                out[2 * i + 1] = (p.y - obs.y).toDouble()
            }
        }
        return out
    }

    private fun residualsPerturbed(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, k1: Double, k2: Double, k3: Double, rot: Mat3,
        paramIndex: Int, step: Double, flipY: Boolean,
    ): DoubleArray = when (paramIndex) {
        0 -> residuals(matches, cx + step, cy, f, k1, k2, k3, rot, flipY)
        1 -> residuals(matches, cx, cy + step, f, k1, k2, k3, rot, flipY)
        2 -> residuals(matches, cx, cy, f + step, k1, k2, k3, rot, flipY)
        3 -> residuals(matches, cx, cy, f, k1 + step, k2, k3, rot, flipY)
        4 -> residuals(matches, cx, cy, f, k1, k2 + step, k3, rot, flipY)
        5 -> residuals(matches, cx, cy, f, k1, k2, k3 + step, rot, flipY)
        6 -> residuals(matches, cx, cy, f, k1, k2, k3, rotationVector(step, 0.0, 0.0) * rot, flipY)
        7 -> residuals(matches, cx, cy, f, k1, k2, k3, rotationVector(0.0, step, 0.0) * rot, flipY)
        else -> residuals(matches, cx, cy, f, k1, k2, k3, rotationVector(0.0, 0.0, step) * rot, flipY)
    }

    private fun rms(
        matches: List<Pair<Vec3, Offset>>,
        cx: Double, cy: Double, f: Double, k1: Double, k2: Double, k3: Double, rot: Mat3,
        flipY: Boolean,
        weights: DoubleArray? = null,
    ): Double = rmsOf(residuals(matches, cx, cy, f, k1, k2, k3, rot, flipY), weights)

    /**
     * Mittlerer Restfehler pro Stern. Mit [weights] (1:1 zu den Punkten, s. [tileVoteWeights])
     * ein gewichtetes RMS für die interne Fit-Kostenfunktion; ohne (Default) exakt die bisherige,
     * ungewichtete Formel — für uniforme Gewichte 1.0 sind beide Zweige wertgleich (den = nPoints).
     */
    private fun rmsOf(res: DoubleArray, weights: DoubleArray? = null): Double {
        if (res.isEmpty()) return Double.MAX_VALUE
        if (weights == null) {
            var s = 0.0
            for (v in res) s += v * v
            // res hat 2 Komponenten pro Stern -> Mittel über Sterne.
            return sqrt(s / (res.size / 2))
        }
        var num = 0.0
        var den = 0.0
        val nPoints = res.size / 2
        for (p in 0 until nPoints) {
            val w = weights[p]
            num += w * (res[2 * p] * res[2 * p] + res[2 * p + 1] * res[2 * p + 1])
            den += w
        }
        return if (den > 0.0) sqrt(num / den) else Double.MAX_VALUE
    }

    /** Rodrigues: kleiner Rotationsvektor (wx,wy,wz) -> Rotationsmatrix. */
    private fun rotationVector(wx: Double, wy: Double, wz: Double): Mat3 {
        val a = sqrt(wx * wx + wy * wy + wz * wz)
        if (a < 1e-12) return Mat3.IDENTITY
        val x = wx / a; val y = wy / a; val z = wz / a
        val c = cos(a); val s = sin(a); val t = 1 - c
        return Mat3(
            t * x * x + c, t * x * y - s * z, t * x * z + s * y,
            t * x * y + s * z, t * y * y + c, t * y * z - s * x,
            t * x * z - s * y, t * y * z + s * x, t * z * z + c,
        )
    }

    /** Gauß-Elimination mit Teilpivotisierung für kleine dichte Systeme. */
    private fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> DoubleArray(n + 1).also { row -> a[i].copyInto(row); row[n] = b[i] } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[pivot][col])) pivot = r
            if (kotlin.math.abs(m[pivot][col]) < 1e-15) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r][col] / m[col][col]
                for (c in col..n) m[r][c] -= factor * m[col][c]
            }
        }
        return DoubleArray(n) { i -> m[i][n] / m[i][i] }
    }
}
