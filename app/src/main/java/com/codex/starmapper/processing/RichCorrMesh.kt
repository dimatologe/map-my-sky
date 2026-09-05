package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.diagnostics.AppDiagnostics
import com.codex.starmapper.domain.SkyPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * RichCorrMesh: lokale astrometrische Sternkorrektur ÜBER einem starren Basismodell, aus ECHTEN, von
 * astrometry.net verifizierten `.corr`-Sternkorrespondenzen -- NICHT aus synthetischen Kachel-Ankern
 * (das ist der bereits bestehende, hier bewusst UNVERÄNDERTE "SparseAnchorMesh"-Pfad, s.
 * [FisheyeRefiner.fitMesh]/[CorrectedProjection]). Architektur (Nutzer-Vorgabe 2026-08-30, Teil A):
 * ```
 * Basisprojektion + lokale astrometrische Sternkorrektur
 * ```
 * NICHT als 7. eigenständige Projektion, sondern als Korrektur-Layer -- exakt wie [CorrectedProjection],
 * aber mit fundamental anderer Interpolationsmathematik: statt Kontrollpunkte kachelweise zu EINEM
 * Repräsentativpunkt zu mitteln (IDW über Kachel-Gruppen), interpoliert [RichCorrMeshProjection] jeden
 * EINZELNEN validierten Kontrollstern EXAKT (kompakt getragene Radialbasisfunktion, Wendland C2, im
 * NATIVEN Pixel-Raum -- nicht im gnomonischen Tangentialraum, dessen Verzerrung bei weiten Sichtfeldern
 * der frühere, verworfene Delaunay-Versuch geerbt hatte, s. [FisheyeRefiner.fitMesh]s Klassenkommentar).
 * An einem Kontrollstern selbst ist die Korrektur (bis auf Gleitkomma-Rundung) IDENTISCH mit dessen
 * gemessenem Residuum, zwischen Kontrollsternen glatt interpoliert, außerhalb des Trag-Radius exakt 0
 * (reine Baseline) -- strukturell garantiert, kein Extrapolations-Risiko.
 */
object RichCorrMesh {

    // ---- A4: Validierung ----

    data class ValidationResult(
        val accepted: List<Pair<Offset, Vec3>>,
        val rejectedOutliers: Int,
    )

    // Harte Untergrenze für die robuste Streuung (Median+1.4826*MAD, s. RotationFit.robustSigmaDegrees,
    // hier auf Pixel- statt Gradwerten -- die Formel selbst ist einheitenunabhängig). Ohne Deckel würde
    // ein zufällig extrem "sauberer" Kachel-Ausschnitt (fast identische Residuen) das Ausreißer-Fenster
    // auf Bruchteile eines Pixels zusammenziehen und normales Rauschen selbst als Ausreißer verwerfen.
    private const val MIN_SIGMA_PX = 0.5

    // Punkte mit Residuum > REJECT_SIGMA_FACTOR*sigma gelten als Fehlmatch (Blend/kosmischer Strahl/
    // Verwechslung) -- großzügiger als TileConsistency.MEDIAN_FACTOR=4.0 (das arbeitet auf robusten
    // PER-KACHEL-RMS-Werten; hier auf EINZELNEN Residuen einer i.d.R. bereits recht sauberen .corr-Liste
    // -- astrometry.net hat selbst schon grob gefiltert, dieser Schritt fängt nur noch die Ausreißer ab,
    // die trotzdem durchrutschen).
    private const val REJECT_SIGMA_FACTOR = 6.0
    private const val MIN_DEDUP_DISTANCE_PX = 1.5

    // Untersuchungs-Auftrag 2026-08-31 ("Untersuche exakt die Unterschiede... Protokolliere für die
    // ersten verworfenen Rich-Mesh-Referenzen..."): reine Diagnose-Ergänzung, KEINE Verhaltensänderung
    // an rejectResidualOutliers/validateAndGroup/fit selbst -- [diagLabel]/[diagBudget] sind optional
    // (Default null/kein Logging), die bestehende lokale Pro-Kachel-Aufrufstelle (StarMapperApp.kt,
    // direkt nach dem Roh-Crop-Solve) bleibt dadurch UNVERÄNDERT (ruft weiterhin die 3-Parameter-Form
    // auf). Nur [validateAndGroup]s GLOBALE Validierungsstufe (die tatsächlich in
    // richCorrMeshAcceptedControlPoints mündet) aktiviert das Logging, s. dort.
    private const val RICH_MESH_REJECT_LOG_BUDGET = 8

    /**
     * A4, Stufe 1 (lokal, pro Kachel): verwirft einzelne `.corr`-Treffer, deren Residuum gegen [wcs]
     * (die kachel-eigene, bereits gefittete Lösung) unrealistisch groß ist -- robuste Median/MAD-
     * Schätzung, KEIN fixer Pixel-Schwellwert. Bei zu wenigen Punkten für eine sinnvolle robuste
     * Schätzung (< 5) werden alle Punkte unverändert akzeptiert (Stufe 2, [rejectResidualOutliers]
     * gegen die globale Baseline, greift danach ohnehin nochmal).
     *
     * [diagLabel]/[diagBudget] (optional, Default aus -> unverändertes Verhalten): wenn gesetzt, wird
     * für jede verworfene Referenz, solange [diagBudget] (gemeinsamer, aufrufer-verwalteter Zähler,
     * s. [validateAndGroup]) noch > 0 ist, eine `rich_corr_mesh_rejected_ref`-Diagnosezeile geschrieben
     * -- s. [logRejectedReference]-KDoc für den genauen Inhalt (u.a. Hauptzweig- VS referenz-bewusste
     * Vorhersage, macht einen Naht-/Wrap-Verdacht direkt sichtbar, s. Antworttext).
     *
     * Reparaturauftrag 2026-08-31 (Seam-Verdacht bestätigt/behoben): [px] (die tatsächlich GEMESSENE
     * Position) wird als `reference` an [WcsSolutionLike.skyToImage] durchgereicht statt (wie zuvor)
     * immer den `atan2`-Hauptzweig zu nehmen -- identisches Prinzip wie der parallele Fix in
     * [FisheyeRefiner.reprojectionRms]. Verhindert, dass ein Kontrollstern nahe der 0°/360°-Bild-Naht
     * fälschlich als Ausreißer verworfen wird, nur weil die (unreferenzierte) Vorhersage auf dem
     * gegenüberliegenden Bildperioden-Ast landete.
     */
    fun rejectResidualOutliers(
        refs: List<Pair<Offset, Vec3>>,
        wcs: WcsSolutionLike,
        imageHeight: Int,
        diagLabel: String? = null,
        diagBudget: IntArray? = null,
    ): ValidationResult {
        if (refs.size < 5) return ValidationResult(refs, 0)
        val residuals = DoubleArray(refs.size)
        for (i in refs.indices) {
            val (px, dir) = refs[i]
            val (raDeg, decDeg) = vectorToRaDec(dir)
            val predicted = wcs.skyToImage(SkyPoint(raDeg.toFloat(), decDeg.toFloat()), imageHeight, px)
            residuals[i] = if (predicted == null) {
                Double.MAX_VALUE
            } else {
                hypot((px.x - predicted.x).toDouble(), (px.y - predicted.y).toDouble())
            }
        }
        val sigma = RotationFit.robustSigmaDegrees(residuals.toList()).coerceAtLeast(MIN_SIGMA_PX)
        val threshold = REJECT_SIGMA_FACTOR * sigma
        val accepted = ArrayList<Pair<Offset, Vec3>>(refs.size)
        var rejected = 0
        for (i in refs.indices) {
            if (residuals[i].isFinite() && residuals[i] <= threshold) {
                accepted += refs[i]
            } else {
                rejected++
                if (diagLabel != null && diagBudget != null && diagBudget[0] > 0) {
                    diagBudget[0] = diagBudget[0] - 1
                    logRejectedReference(diagLabel, refs[i], wcs, imageHeight, residuals[i], threshold, sigma)
                }
            }
        }
        return ValidationResult(accepted, rejected)
    }

    /**
     * Protokolliert EINE verworfene Referenz mit allen vom Untersuchungsauftrag verlangten Feldern:
     * Eingabekoordinaten (`inputPx`, die tatsächlich vom Solver gemessene Position), erwartete
     * Bildkoordinaten (`expectedPxMainBranch`, [wcs]s Standard-`skyToImage`-Vorhersage -- GENAU das, was
     * [rejectResidualOutliers] tatsächlich zum Verwerfen nutzt), Residuum + exakter Reject-Grund.
     *
     * ZUSÄTZLICH (über die reine Protokollierung hinaus, aber ohne jede Verhaltensänderung): dieselbe
     * Vorhersage EIN ZWEITES MAL über die referenz-bewusste `skyToImage(point, imageHeight, reference)`-
     * Überladung (reicht bei periodischen Cylindrical-Projektionen den Azimut relativ zu [inputPx]
     * entfaltet durch, s. [WcsSolutionLike]-KDoc) -- weicht diese Vorhersage deutlich (> 1px) von der
     * Hauptzweig-Vorhersage ab, ist das ein STARKES Indiz, dass die Referenz eigentlich korrekt ist und
     * NUR wegen eines Panorama-Seam/Wrap-Artefakts in der (unreferenzierten) Hauptzweig-Vorhersage
     * verworfen wurde -- s. Antworttext für die vollständige Einordnung dieses Verdachts.
     */
    private fun logRejectedReference(
        label: String,
        ref: Pair<Offset, Vec3>,
        wcs: WcsSolutionLike,
        imageHeight: Int,
        residualPx: Double,
        thresholdPx: Double,
        sigmaPx: Double,
    ) {
        val (px, dir) = ref
        val (raDeg, decDeg) = vectorToRaDec(dir)
        val skyPoint = SkyPoint(raDeg.toFloat(), decDeg.toFloat())
        val mainBranch = wcs.skyToImage(skyPoint, imageHeight)
        val referenceAware = wcs.skyToImage(skyPoint, imageHeight, px)
        val refAwareResidualPx = referenceAware?.let {
            hypot((px.x - it.x).toDouble(), (px.y - it.y).toDouble())
        }
        val seamBranchSuspect = mainBranch != null && referenceAware != null &&
            (abs(referenceAware.x - mainBranch.x) > 1f || abs(referenceAware.y - mainBranch.y) > 1f)
        val rejectReason = if (mainBranch == null) "baseline_projection_returned_null" else "residual_exceeds_sigma_threshold"
        AppDiagnostics.record(
            "rich_corr_mesh_rejected_ref label=$label " +
                "inputPx=(${fmtPx(px.x)},${fmtPx(px.y)}) raDeg=${fmtPx(raDeg.toFloat())} decDeg=${fmtPx(decDeg.toFloat())} " +
                "expectedPxMainBranch=${mainBranch?.let { "(${fmtPx(it.x)},${fmtPx(it.y)})" } ?: "null"} " +
                "residualMainBranchPx=${fmtPx(residualPx.toFloat())} thresholdPx=${fmtPx(thresholdPx.toFloat())} sigmaPx=${fmtPx(sigmaPx.toFloat())} " +
                "expectedPxReferenceAware=${referenceAware?.let { "(${fmtPx(it.x)},${fmtPx(it.y)})" } ?: "null"} " +
                "residualReferenceAwarePx=${refAwareResidualPx?.let { fmtPx(it.toFloat()) } ?: "null"} " +
                "seamBranchSuspect=$seamBranchSuspect rejectReason=$rejectReason",
        )
    }

    private fun fmtPx(v: Float): String = "%.2f".format(v)

    /**
     * A4: nahezu identische Kontrollpunkte (< [MIN_DEDUP_DISTANCE_PX] auseinander -- z.B. derselbe
     * Stern doppelt erkannt, oder zwei sich überlappende Kacheln) würden das lineare Gleichungssystem
     * schlecht konditionieren (fast-singuläre Zeilen). Pro Cluster bleibt nur der zuerst gesehene Punkt
     * übrig -- die Auswahl selbst ist unkritisch, da beide Punkte praktisch identisch sind.
     */
    fun deduplicate(refs: List<Pair<Offset, Vec3>>): List<Pair<Offset, Vec3>> {
        if (refs.size < 2) return refs
        val kept = ArrayList<Pair<Offset, Vec3>>(refs.size)
        val minDistSq = MIN_DEDUP_DISTANCE_PX * MIN_DEDUP_DISTANCE_PX
        outer@ for (candidate in refs) {
            for (existing in kept) {
                val dx = (candidate.first.x - existing.first.x).toDouble()
                val dy = (candidate.first.y - existing.first.y).toDouble()
                if (dx * dx + dy * dy < minDistSq) continue@outer
            }
            kept += candidate
        }
        return kept
    }

    /**
     * A4, Stufe 2 (global): wendet [rejectResidualOutliers] + [deduplicate] GRUPPENWEISE auf bereits
     * globalisierte Refs an (native Pixel, s. FisheyeRefiner.globalizeTileCorrRefs) -- gruppenweise
     * statt auf der flachen Liste, damit die zurückgegebenen [groupSizes] weiterhin exakt zu den
     * zurückgegebenen Refs passen (Vorbedingung von [fit]). [baseline] hier ist die GLOBALE
     * Panorama-Baseline (rigidCalibration), NICHT mehr die einzelne Kachel-WCS aus Stufe 1 (die lief
     * bereits vorher, direkt beim Kachel-Solve, s. StarMapperApp.solveAllTiles()).
     */
    fun validateAndGroup(
        references: List<Pair<Offset, Vec3>>,
        groupSizes: List<Int>,
        baseline: WcsSolutionLike,
        imageHeight: Int,
    ): Pair<List<Pair<Offset, Vec3>>, List<Int>> {
        val outRefs = ArrayList<Pair<Offset, Vec3>>(references.size)
        val outSizes = ArrayList<Int>(groupSizes.size)
        // Untersuchungs-Auftrag 2026-08-31: gemeinsamer Zähler über ALLE Gruppen dieses Aufrufs (nicht
        // pro Gruppe neu) -- begrenzt die neuen rich_corr_mesh_rejected_ref-Zeilen auf insgesamt
        // RICH_MESH_REJECT_LOG_BUDGET, unabhängig davon, wie viele Kachel-Gruppen tatsächlich Ausreißer
        // haben (verhindert, dass ein Panorama mit vielen Kacheln die gedeckelte Diagnosedatei flutet).
        val diagBudget = intArrayOf(RICH_MESH_REJECT_LOG_BUDGET)
        var totalRejected = 0
        var start = 0
        for ((groupIndex, size) in groupSizes.withIndex()) {
            val end = (start + size).coerceAtMost(references.size)
            val slice = if (start < end) references.subList(start, end) else emptyList()
            start += size
            val validated = rejectResidualOutliers(
                slice, baseline, imageHeight,
                diagLabel = "group$groupIndex", diagBudget = diagBudget,
            )
            totalRejected += validated.rejectedOutliers
            val deduped = deduplicate(validated.accepted)
            outRefs += deduped
            outSizes += deduped.size
        }
        AppDiagnostics.record(
            "rich_corr_mesh_validate inputRefs=${references.size} inputGroups=${groupSizes.size} " +
                "nonEmptyInputGroups=${groupSizes.count { it > 0 }} rejectedOutliers=$totalRejected " +
                "acceptedAfterDedup=${outRefs.size} nonEmptyOutputGroups=${outSizes.count { it > 0 }} " +
                "loggedRejectedRefs=${RICH_MESH_REJECT_LOG_BUDGET - diagBudget[0]}",
        )
        return outRefs to outSizes
    }

    // ---- A5+A8: Fit + Kreuzvalidierung ----

    data class RichCorrMeshFit(
        val solution: PanoramaWcsSolution,
        val baselineRmsPx: Double,
        val trainRmsPx: Double,
        val crossValidationRmsPx: Double,
        val acceptedControlPoints: Int,
        val coverageFraction: Double,
    )

    // Deckel für das dichte Gleichungssystem (Gauss-Jordan ist O(n^3); die Kreuzvalidierung löst es
    // zusätzlich einmal PRO Kachel-Gruppe erneut, jeweils fast in voller Größe) -- 300 Kontrollpunkte
    // halten das auf einem Mobilgerät im Bereich weniger Sekunden. Vorläufiger Wert, ggf. nach
    // Gerätebeleg anzupassen -- exakt wie FisheyeRefiner.globalizeTileCorrRefs.maxPerTile bereits
    // einmal empirisch nachjustiert wurde (s. dessen Kommentar).
    private const val MAX_CONTROL_POINTS_FOR_FIT = 300
    private const val NEIGHBOR_RADIUS_FACTOR = 4.0
    private const val MIN_REFERENCES_FOR_FIT = 5

    // Stabilisierungs-Pass (Untersuchungsauftrag 2026-08-31): [logCrossValidationDetail] war zur
    // A8-Ursachenanalyse gedacht (jetzt erledigt) -- kostet aber echte, REDUNDANTE Rechenzeit (baut pro
    // Leave-Group-Out-Fold ein zweites RichCorrMeshProjection, also denselben O(n^3)-Gauss-Jordan-Solve
    // ein zweites Mal, zusätzlich zum bereits bestehenden in [leaveOneGroupOutRms]). Standardmäßig AUS,
    // damit der normale Produktions-Solve dadurch nicht unnötig verlangsamt wird -- für künftige
    // RichCorrMesh-Arbeit bei Bedarf hier auf `true` stellen (bewusst ein reiner Quelltext-Schalter,
    // kein neuer Nutzer-UI-Schalter -- keine neue Funktion in diesem Stabilisierungs-Pass).
    private const val CV_DETAIL_DIAGNOSTIC_ENABLED = false

    /**
     * Baut RichCorrMesh aus bereits validierten/deduplizierten [references] (äquatorial, wird hier ins
     * Pano-Frame der [baseline] gedreht -- identisches Prinzip wie [FisheyeRefiner.fitMesh]s
     * buildCorrectedProjection) + [groupSizes] (NUR für die Leave-one-tile-out-Kreuzvalidierung, KEINE
     * Deklusterung mehr -- jeder einzelne Punkt bleibt eigener Kontrollpunkt, s. Klassenkommentar an
     * [RichCorrMeshProjection]). Gibt `null` zurück bei zu wenigen Punkten/Gruppen ODER wenn die
     * kreuzvalidierte Vorhersagegüte die reine Baseline NICHT tatsächlich schlägt (A8, wörtlich: "Nur
     * bei echter Verbesserung gegenüber der Baseline darf RichCorrMesh als hochwertiger
     * Korrekturmodus gelten").
     */
    fun fit(
        references: List<Pair<Offset, Vec3>>,
        groupSizes: List<Int>,
        baseline: PanoramaWcsSolution,
        imageWidth: Int,
        imageHeight: Int,
    ): RichCorrMeshFit? = fitCandidate(references, groupSizes, baseline, imageWidth, imageHeight)
        ?.let { if (it.autoEligible) it.fit else null }

    /**
     * Technisch gültiger RichCorrMesh-Kandidat + die Frage, ob er auch AUTOMATISCH verwendet werden darf
     * (Nutzer-Auftrag 2026-09-03: "candidateValid / manuallySelectable" von "autoEligible / recommended"
     * trennen).
     *
     * Vorher verwarf [fit] einen bereits VOLLSTÄNDIG GEBAUTEN Fit an den beiden QUALITÄTS-Gates
     * (`cross_validation_produced_no_result`, `does_not_beat_baseline_a8`) mit `return null` -- die
     * mathematisch einwandfreie Projektion ging dadurch komplett verloren und konnte auch manuell nicht
     * mehr gewählt werden. [fitCandidate] behält sie stattdessen und markiert sie nur als
     * `autoEligible = false`.
     *
     * `null` bleibt es weiterhin bei den drei Gates, an denen gar KEINE nutzbare Projektion entsteht:
     * `too_few_references_or_size_mismatch`, `fewer_than_2_nonempty_groups` (beide: es wird erst gar
     * kein Projektionsobjekt gebaut) und `zero_control_points_after_baseline_projection` (Objekt
     * gebaut, aber mit 0 Kontrollpunkten -> es gibt buchstäblich nichts zu interpolieren).
     *
     * [fit] selbst ist unverändert in Verhalten UND Diagnose-Ausgabe (identische Gate-Logs an identischer
     * Stelle) -- der automatische Pfad merkt von dieser Trennung nichts.
     */
    data class RichCorrMeshCandidate(
        val fit: RichCorrMeshFit,
        val autoEligible: Boolean,
        val rejectReason: String?,
    )

    fun fitCandidate(
        references: List<Pair<Offset, Vec3>>,
        groupSizes: List<Int>,
        baseline: PanoramaWcsSolution,
        imageWidth: Int,
        imageHeight: Int,
    ): RichCorrMeshCandidate? {
        // Untersuchungs-Auftrag 2026-08-31: EIN Diagnosezeile PRO Gate, direkt vor dem jeweiligen
        // `return null` -- macht sichtbar, an WELCHER der 5 möglichen Bedingungen ein Fit tatsächlich
        // scheitert (der bestehende Aufrufer-Log in StarMapperApp.kt zeigt bei JEDER dieser 5
        // Bedingungen identisch `richCorrMeshAcceptedControlPoints=0`, ohne sie zu unterscheiden).
        if (references.size < MIN_REFERENCES_FOR_FIT || references.size != groupSizes.sum()) {
            AppDiagnostics.record(
                "rich_corr_mesh_fit_gate gate=too_few_references_or_size_mismatch " +
                    "references=${references.size} groupSizesSum=${groupSizes.sum()} minRequired=$MIN_REFERENCES_FOR_FIT",
            )
            return null
        }
        val ranges = groupRanges(groupSizes)
        if (ranges.size < 2) {
            AppDiagnostics.record(
                "rich_corr_mesh_fit_gate gate=fewer_than_2_nonempty_groups " +
                    "totalGroups=${groupSizes.size} nonEmptyGroups=${ranges.size} references=${references.size}",
            )
            return null
        }
        val (sampledRefs, sampledRanges) = subsampleKeepingGroups(references, ranges, MAX_CONTROL_POINTS_FOR_FIT)
        val rot = baseline.rotEquToPano
        val panoRefs = sampledRefs.map { (px, dir) -> px to (rot * dir.normalized()) }
        val projection = RichCorrMeshProjection(baseline.projection, panoRefs)
        if (projection.controlPointCount == 0) {
            AppDiagnostics.record(
                "rich_corr_mesh_fit_gate gate=zero_control_points_after_baseline_projection " +
                    "sampledRefs=${sampledRefs.size} -- jeder baseline.directionToPixel(dir)-Aufruf lieferte null " +
                    "(Basismodell kann keinen einzigen Kontrollstern abbilden)",
            )
            return null
        }
        val solution = PanoramaWcsSolution(projection, rot)
        val baselineRms = FisheyeRefiner.reprojectionRms(baseline, sampledRefs)
        val trainRms = FisheyeRefiner.reprojectionRms(solution, sampledRefs)
        val coverage = estimateCoverageFraction(sampledRefs.map { it.first }, projection.supportRadius, imageWidth, imageHeight)
        // Ab hier existiert eine mathematisch vollständige, benutzbare Projektion (controlPointCount > 0,
        // directionToPixel/pixelToDirection bedienbar). Die verbleibenden zwei Gates sind reine
        // QUALITÄTS-Urteile -> sie schalten nur noch `autoEligible` ab, statt den Kandidaten zu verwerfen.
        fun candidate(autoEligible: Boolean, reason: String?, crossVal: Double) = RichCorrMeshCandidate(
            fit = RichCorrMeshFit(solution, baselineRms, trainRms, crossVal, sampledRefs.size, coverage),
            autoEligible = autoEligible,
            rejectReason = reason,
        )
        val crossValRms = leaveOneGroupOutRms(sampledRefs, panoRefs, sampledRanges, rot, baseline.projection)
        if (crossValRms == null) {
            AppDiagnostics.record(
                "rich_corr_mesh_fit_gate gate=cross_validation_produced_no_result " +
                    "groups=${sampledRanges.size} sampledRefs=${sampledRefs.size} -- jeder Leave-one-group-out-Fold " +
                    "hatte entweder ein leeres Trainingsset oder 0 Kontrollpunkte nach erneuter Baseline-Projektion",
            )
            return candidate(false, "cross_validation_produced_no_result", Double.NaN)
        }
        // Untersuchungsauftrag 2026-08-31 (fünfte Runde): rein additive Beweis-Diagnose, warum
        // crossValRms hier wiederholt exakt gleich baselineRms war -- läuft, wenn aktiviert (s.
        // CV_DETAIL_DIAGNOSTIC_ENABLED-KDoc), immer wenn ein echtes crossValRms vorliegt (unabhängig
        // davon, ob das A8-Gate gleich danach ablehnt), beeinflusst den Rückgabewert dieser Funktion
        // nicht (reine AppDiagnostics.record-Aufrufe). Standardmäßig AUS (Stabilisierungs-Pass 2026-08-31),
        // da sie den Leave-Group-Out-Aufwand verdoppelt -- s. CV_DETAIL_DIAGNOSTIC_ENABLED.
        if (CV_DETAIL_DIAGNOSTIC_ENABLED) {
            logCrossValidationDetail(sampledRefs, panoRefs, sampledRanges, rot, baseline.projection, pointBudgetPerFold = 3)
        }
        if (!crossValRms.isFinite() || crossValRms >= baselineRms) {
            AppDiagnostics.record(
                "rich_corr_mesh_fit_gate gate=does_not_beat_baseline_a8 " +
                    "crossValidationRmsPx=${"%.2f".format(crossValRms)} baselineRmsPx=${"%.2f".format(baselineRms)} " +
                    "trainRmsPx=${"%.2f".format(trainRms)} sampledRefs=${sampledRefs.size} groups=${sampledRanges.size}",
            )
            return candidate(false, "does_not_beat_baseline_a8", crossValRms)
        }
        return candidate(true, null, crossValRms)
    }

    private fun groupRanges(groupSizes: List<Int>): List<IntRange> {
        val ranges = ArrayList<IntRange>(groupSizes.size)
        var start = 0
        for (size in groupSizes) {
            if (size > 0) ranges += start until (start + size)
            start += size
        }
        return ranges
    }

    /** Even-Stride-Downsampling PRO Gruppe (nicht global über die flache Liste), damit kein einzelner
     *  Kreuzvalidierungs-Fold durch den Deckel leerläuft. */
    private fun subsampleKeepingGroups(
        references: List<Pair<Offset, Vec3>>,
        ranges: List<IntRange>,
        maxTotal: Int,
    ): Pair<List<Pair<Offset, Vec3>>, List<IntRange>> {
        if (references.size <= maxTotal) return references to ranges
        val fraction = maxTotal.toDouble() / references.size
        val outRefs = ArrayList<Pair<Offset, Vec3>>(maxTotal)
        val outRanges = ArrayList<IntRange>(ranges.size)
        for (range in ranges) {
            val size = range.last - range.first + 1
            val keep = (size * fraction).toInt().coerceAtLeast(1).coerceAtMost(size)
            val stride = size.toDouble() / keep
            val start = outRefs.size
            for (i in 0 until keep) {
                val srcIndex = (range.first + (i * stride).toInt()).coerceAtMost(range.last)
                outRefs += references[srcIndex]
            }
            outRanges += start until outRefs.size
        }
        return outRefs to outRanges
    }

    /** Leave-one-tile-out, identisches Prinzip wie [FisheyeRefiner]s private meshLeaveOneTileOutRms für
     *  SparseAnchorMesh -- eigene Kopie statt Wiederverwendung, da diese hier KEINE Kachel-Deklusterung
     *  durchführt (s. Klassenkommentar an [RichCorrMeshProjection]). */
    private fun leaveOneGroupOutRms(
        references: List<Pair<Offset, Vec3>>,
        panoRefs: List<Pair<Offset, Vec3>>,
        ranges: List<IntRange>,
        rot: Mat3,
        basisProjection: PanoramaProjection,
    ): Double? {
        var sumSq = 0.0
        var count = 0
        for ((rangeIndex, heldOutRange) in ranges.withIndex()) {
            val heldOutEq = heldOutRange.map { references[it] }
            val trainIndices = ranges.indices.filter { it != rangeIndex }.flatMap { ranges[it].toList() }
            if (trainIndices.isEmpty()) continue
            val trainPano = trainIndices.map { panoRefs[it] }
            val foldProjection = RichCorrMeshProjection(basisProjection, trainPano)
            if (foldProjection.controlPointCount == 0) continue
            val foldSolution = PanoramaWcsSolution(foldProjection, rot)
            val foldRms = FisheyeRefiner.reprojectionRms(foldSolution, heldOutEq)
            if (foldRms.isFinite()) {
                sumSq += foldRms * foldRms * heldOutEq.size
                count += heldOutEq.size
            }
        }
        return if (count > 0) sqrt(sumSq / count) else null
    }

    /**
     * Untersuchungsauftrag 2026-08-31 (fünfte Runde): rein additive Diagnose FÜR [leaveOneGroupOutRms] --
     * KEINE Verhaltensänderung an `fit()`/`leaveOneGroupOutRms`/[RichCorrMeshProjection]/A8/Outlier-
     * Schwellen. Eigene, unabhängige Kopie derselben Fold-Schleife (bewusst NICHT [leaveOneGroupOutRms]
     * selbst erweitert -- verhindert jedes Risiko, die tatsächliche A8-Gate-Entscheidung zu beeinflussen;
     * ihr Rückgabewert wird von [fit] nirgends gelesen). [RichCorrMeshProjection] selbst wird NICHT
     * verändert -- Trainings-Kontrollpunkt-Positionen werden hier unabhängig über
     * `basisProjection.directionToPixel(dir, actualPixel)` neu berechnet, exakt dieselbe Formel wie
     * [RichCorrMeshProjection.init] intern nutzt (rein lesend dupliziert).
     *
     * Beantwortet die Hauptfrage ("erzeugt die Kreuzvalidierung tatsächlich eine von der Baseline
     * verschiedene Korrektur, oder ist correctionDx/Dy praktisch immer 0?") direkt: protokolliert pro
     * Validierungspunkt (max. [pointBudgetPerFold] je Fold, aber ALLE Punkte fließen in die Fold-
     * Zusammenfassung ein) `baselinePredictedPx`/`richPredictedPx`/`correctionDx`/`correctionDy` UND ob
     * überhaupt ein TRAININGS-Kontrollpunkt innerhalb von `foldProjection.supportRadius` liegt
     * (`numberOfTrainingControlsInsideSupport`) -- unterscheidet eindeutig "Korrektur ist 0, weil kein
     * Trainingspunkt in Reichweite" (Trag-Radius-/Fold-Strategie-Frage) von "Korrektur ist 0 aus einem
     * anderen Grund" (Bug im Auswertungspfad selbst, s. Frage 7 im Nutzerauftrag).
     */
    private fun logCrossValidationDetail(
        references: List<Pair<Offset, Vec3>>,
        panoRefs: List<Pair<Offset, Vec3>>,
        ranges: List<IntRange>,
        rot: Mat3,
        basisProjection: PanoramaProjection,
        pointBudgetPerFold: Int,
    ) {
        fun fmtD(v: Double): String = "%.2f".format(v)
        fun fmtOffset(o: Offset?): String = if (o == null) "null" else "(${fmtD(o.x.toDouble())},${fmtD(o.y.toDouble())})"
        for ((rangeIndex, heldOutRange) in ranges.withIndex()) {
            val heldOutIndices = heldOutRange.toList()
            val trainIndices = ranges.indices.filter { it != rangeIndex }.flatMap { ranges[it].toList() }
            if (trainIndices.isEmpty()) {
                AppDiagnostics.record(
                    "rich_corr_mesh_cv_fold heldOutGroup=$rangeIndex trainGroups=0 " +
                        "validationPoints=${heldOutIndices.size} skipped=empty_train_set",
                )
                continue
            }
            val trainPano = trainIndices.map { panoRefs[it] }
            val foldProjection = RichCorrMeshProjection(basisProjection, trainPano)
            if (foldProjection.controlPointCount == 0) {
                AppDiagnostics.record(
                    "rich_corr_mesh_cv_fold heldOutGroup=$rangeIndex trainGroups=${ranges.size - 1} " +
                        "trainPoints=${trainPano.size} validationPoints=${heldOutIndices.size} " +
                        "skipped=zero_control_points_after_baseline_projection",
                )
                continue
            }
            val heldOutEq = heldOutIndices.map { references[it] }
            val foldBaselineRms = FisheyeRefiner.reprojectionRms(PanoramaWcsSolution(basisProjection, rot), heldOutEq)
            val foldRichRms = FisheyeRefiner.reprojectionRms(PanoramaWcsSolution(foldProjection, rot), heldOutEq)
            val trainBasePx = trainPano.mapNotNull { (actualPx, panoDir) -> basisProjection.directionToPixel(panoDir, actualPx) }
            var zeroCorrectionCount = 0
            var nonZeroCorrectionCount = 0
            var supportNeighborSum = 0
            var loggedForThisFold = 0
            for (globalIdx in heldOutIndices) {
                val (measuredPx, panoDir) = panoRefs[globalIdx]
                val baselinePredictedPx = basisProjection.directionToPixel(panoDir, measuredPx)
                val richPredictedPx = foldProjection.directionToPixel(panoDir, measuredPx)
                val correctionDx = if (baselinePredictedPx != null && richPredictedPx != null) {
                    (richPredictedPx.x - baselinePredictedPx.x).toDouble()
                } else {
                    null
                }
                val correctionDy = if (baselinePredictedPx != null && richPredictedPx != null) {
                    (richPredictedPx.y - baselinePredictedPx.y).toDouble()
                } else {
                    null
                }
                val correctionMag = if (correctionDx != null && correctionDy != null) hypot(correctionDx, correctionDy) else null
                val distances = if (baselinePredictedPx != null) {
                    trainBasePx.map {
                        hypot((it.x - baselinePredictedPx.x).toDouble(), (it.y - baselinePredictedPx.y).toDouble())
                    }.sorted()
                } else {
                    emptyList()
                }
                val countInsideSupport = distances.count { it < foldProjection.supportRadius }
                if (correctionMag != null && correctionMag > 0.01) nonZeroCorrectionCount++ else zeroCorrectionCount++
                supportNeighborSum += countInsideSupport
                if (loggedForThisFold < pointBudgetPerFold) {
                    loggedForThisFold++
                    val (raDeg, decDeg) = vectorToRaDec(references[globalIdx].second)
                    val baselineResidualPx = baselinePredictedPx?.let {
                        hypot((measuredPx.x - it.x).toDouble(), (measuredPx.y - it.y).toDouble())
                    }
                    val richResidualPx = richPredictedPx?.let {
                        hypot((measuredPx.x - it.x).toDouble(), (measuredPx.y - it.y).toDouble())
                    }
                    AppDiagnostics.record(
                        "rich_corr_mesh_cv_point heldOutGroup=$rangeIndex " +
                            "validationPointPx=${fmtOffset(measuredPx)} " +
                            "raDeg=${"%.4f".format(raDeg)} decDeg=${"%.4f".format(decDeg)} " +
                            "baselinePredictedPx=${fmtOffset(baselinePredictedPx)} " +
                            "baselineResidualPx=${baselineResidualPx?.let { fmtD(it) } ?: "null"} " +
                            "richPredictedPx=${fmtOffset(richPredictedPx)} " +
                            "richResidualPx=${richResidualPx?.let { fmtD(it) } ?: "null"} " +
                            "correctionDx=${correctionDx?.let { fmtD(it) } ?: "null"} " +
                            "correctionDy=${correctionDy?.let { fmtD(it) } ?: "null"} " +
                            "correctionMagnitudePx=${correctionMag?.let { fmtD(it) } ?: "null"} " +
                            "supportRadiusPx=${fmtD(foldProjection.supportRadius)} " +
                            "nearestTrainingControlDistancePx=${distances.firstOrNull()?.let { fmtD(it) } ?: "n/a"} " +
                            "nearest5TrainingControlDistancesPx=${distances.take(5).joinToString(",") { fmtD(it) }} " +
                            "numberOfTrainingControlsInsideSupport=$countInsideSupport " +
                            "insideAnySupport=${countInsideSupport > 0}",
                    )
                }
            }
            val meanSupportNeighborsText = if (heldOutIndices.isNotEmpty()) {
                fmtD(supportNeighborSum.toDouble() / heldOutIndices.size)
            } else {
                "n/a"
            }
            AppDiagnostics.record(
                "rich_corr_mesh_cv_fold heldOutGroup=$rangeIndex trainGroups=${ranges.size - 1} " +
                    "trainPoints=${trainPano.size} validationPoints=${heldOutIndices.size} " +
                    "meanSupportNeighbors=$meanSupportNeighborsText " +
                    "zeroCorrectionCount=$zeroCorrectionCount nonZeroCorrectionCount=$nonZeroCorrectionCount " +
                    "baselineRms=${fmtD(foldBaselineRms)} richRms=${fmtD(foldRichRms)}",
            )
        }
    }

    /** Grober Flächenanteil (Rastersample, [gridSize]x[gridSize]) innerhalb des Trag-Radius mindestens
     *  eines Kontrollpunkts -- reine Diagnose-/Transparenzgröße (s. Antworttext, richCorrMeshCoverage),
     *  beeinflusst die eigentliche Korrektur selbst nicht. */
    private fun estimateCoverageFraction(
        points: List<Offset>,
        supportRadius: Double,
        imageWidth: Int,
        imageHeight: Int,
        gridSize: Int = 24,
    ): Double {
        if (points.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return 0.0
        val rSq = supportRadius * supportRadius
        var covered = 0
        var total = 0
        for (gy in 0 until gridSize) {
            val py = imageHeight * (gy + 0.5) / gridSize
            for (gx in 0 until gridSize) {
                val px = imageWidth * (gx + 0.5) / gridSize
                total++
                if (points.any { val dx = it.x - px; val dy = it.y - py; dx * dx + dy * dy <= rSq }) covered++
            }
        }
        return if (total > 0) covered.toDouble() / total else 0.0
    }

    // ---- Interpolierendes Korrekturfeld ----

    /**
     * [PanoramaProjection]-Korrekturfeld über [baseline], das jeden einzelnen [controlPoints]-Eintrag
     * EXAKT interpoliert (kompakt getragene Wendland-C2-Radialbasisfunktion im NATIVEN Pixel-Raum).
     * [controlPoints] wie bei [CorrectedProjection]: (Bild-Pixel, PANO-FRAME-Richtung) -- die Rotation
     * ins Pano-Frame ist bereits vom Aufrufer erledigt.
     *
     * Unterschied zu [CorrectedProjection] (bewusst NICHT verändert, bleibt der bestehende
     * "SparseAnchorMesh"-Pfad): KEINE Kachel-Deklusterung -- jeder Kontrollpunkt bleibt individuell.
     * Wendlands C2-Kern ist per Konstruktion exakt 0 jenseits des Trag-Radius (Wert UND 1. Ableitung
     * an der Grenze), ein separater Konfidenzwert (wie [CorrectedProjection.blendedCorrection]s
     * drittes Element) ist deshalb nicht nötig -- "außerhalb der Abdeckung -> reine Baseline" ist
     * strukturell garantiert, nicht nur angenähert.
     */
    class RichCorrMeshProjection(
        private val baseline: PanoramaProjection,
        controlPoints: List<Pair<Offset, Vec3>>,
    ) : PanoramaProjection {

        private val pointsX: DoubleArray
        private val pointsY: DoubleArray
        private val weightsX: DoubleArray
        private val weightsY: DoubleArray
        val controlPointCount: Int
        val supportRadius: Double

        init {
            val basePixels = ArrayList<Offset>(controlPoints.size)
            val actualPixels = ArrayList<Offset>(controlPoints.size)
            for ((actualPixel, dir) in controlPoints) {
                // Reparaturauftrag 2026-08-31 (derselbe Branch-Auswahl-Fix wie rejectResidualOutliers/
                // reprojectionRms, hier auf den EINEN Punkt angewendet, an dem RichCorrMeshProjection
                // selbst -- nicht nur die Validierung davor -- einen Residuum-Basiswert bildet): [actualPixel]
                // als Referenz durchreichen, sonst würde ein nahtnaher Kontrollpunkt, der die Validierung
                // jetzt korrekt übersteht, hier trotzdem einen um bis zu eine volle Periode falschen
                // basePixel bekommen -- der RBF-Fit würde dann selbst mit "sauberen" Kontrollpunkten aus
                // einem falschen Residuum lernen.
                val basePixel = baseline.directionToPixel(dir, actualPixel) ?: continue
                basePixels += basePixel
                actualPixels += actualPixel
            }
            val n = basePixels.size
            if (n == 0) {
                pointsX = DoubleArray(0)
                pointsY = DoubleArray(0)
                weightsX = DoubleArray(0)
                weightsY = DoubleArray(0)
                controlPointCount = 0
                supportRadius = 0.0
            } else {
                pointsX = DoubleArray(n) { basePixels[it].x.toDouble() }
                pointsY = DoubleArray(n) { basePixels[it].y.toDouble() }
                val residualX = DoubleArray(n) { actualPixels[it].x - basePixels[it].x.toDouble() }
                val residualY = DoubleArray(n) { actualPixels[it].y - basePixels[it].y.toDouble() }
                val radius = estimateSupportRadius(pointsX, pointsY)
                supportRadius = radius
                val a = Array(n) { i -> DoubleArray(n) { j -> wendland(distance(pointsX, pointsY, i, j), radius) } }
                val solved = solveDual(a, residualX, residualY)
                if (solved == null) {
                    weightsX = DoubleArray(0)
                    weightsY = DoubleArray(0)
                    controlPointCount = 0
                } else {
                    weightsX = solved.first
                    weightsY = solved.second
                    controlPointCount = n
                }
            }
        }

        private fun evaluateCorrection(qx: Double, qy: Double): Pair<Double, Double> {
            if (controlPointCount == 0) return 0.0 to 0.0
            var dx = 0.0
            var dy = 0.0
            for (i in 0 until controlPointCount) {
                val ddx = qx - pointsX[i]
                val ddy = qy - pointsY[i]
                val r = sqrt(ddx * ddx + ddy * ddy)
                if (r >= supportRadius) continue
                val w = wendland(r, supportRadius)
                dx += weightsX[i] * w
                dy += weightsY[i] * w
            }
            return dx to dy
        }

        override fun directionToPixel(dir: Vec3): Offset? {
            val base = baseline.directionToPixel(dir) ?: return null
            return applyCorrection(base)
        }

        override fun directionToPixel(dir: Vec3, reference: Offset?): Offset? {
            val base = baseline.directionToPixel(dir, reference) ?: return null
            return applyCorrection(base)
        }

        private fun applyCorrection(base: Offset): Offset {
            val (dx, dy) = evaluateCorrection(base.x.toDouble(), base.y.toDouble())
            val x = base.x + dx.toFloat()
            val y = base.y + dy.toFloat()
            return if (x.isFinite() && y.isFinite()) Offset(x, y) else base
        }

        override fun pixelToDirection(px: Double, py: Double): Vec3? {
            val (dx, dy) = evaluateCorrection(px, py)
            return baseline.pixelToDirection(px - dx, py - dy) ?: baseline.pixelToDirection(px, py)
        }

        override fun horizontalPeriodPx(): Double? = baseline.horizontalPeriodPx()
    }

    /** Median-Nächster-Nachbar-Abstand * [NEIGHBOR_RADIUS_FACTOR] -- robust gegen einzelne, sehr dicht
     *  benachbarte Punkte (Median statt Mittelwert), skaliert automatisch mit der tatsächlichen
     *  Punktdichte. N<=1 -> großzügiger Fixwert (keine Nachbarschaft definierbar). */
    private fun estimateSupportRadius(px: DoubleArray, py: DoubleArray): Double {
        val n = px.size
        if (n <= 1) return 200.0
        val nearest = DoubleArray(n) { Double.MAX_VALUE }
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) continue
                val dx = px[i] - px[j]
                val dy = py[i] - py[j]
                val d = sqrt(dx * dx + dy * dy)
                if (d < nearest[i]) nearest[i] = d
            }
        }
        val sorted = nearest.sorted()
        val median = sorted[sorted.size / 2]
        return (median * NEIGHBOR_RADIUS_FACTOR).coerceAtLeast(MIN_DEDUP_DISTANCE_PX * NEIGHBOR_RADIUS_FACTOR)
    }

    private fun distance(px: DoubleArray, py: DoubleArray, i: Int, j: Int): Double {
        val dx = px[i] - px[j]
        val dy = py[i] - py[j]
        return sqrt(dx * dx + dy * dy)
    }

    // Wendland C2 (kompakter Träger [0,radius]): am Rand (distance=radius) sind Wert UND 1. Ableitung
    // exakt 0 -> glatter Übergang zur reinen Baseline, kein Sprung/Knick. Diagonale (distance=0): t=1 ->
    // t^4=1, (4*0/r+1)=1 -> Ergebnis exakt 1 (garantiert lösbares Gleichungssystem für verschiedene Punkte).
    private fun wendland(distance: Double, radius: Double): Double {
        if (radius <= 0.0 || distance >= radius) return 0.0
        val t = 1.0 - distance / radius
        val t2 = t * t
        val t4 = t2 * t2
        return t4 * (4.0 * distance / radius + 1.0)
    }

    /** Gauss-Jordan mit Teilpivotisierung, EIN Durchlauf für zwei rechte Seiten (dx UND dy teilen sich
     *  dieselbe Kernel-Matrix) -- gibt `null` bei (numerisch) singulärer Matrix zurück. */
    private fun solveDual(a: Array<DoubleArray>, bx: DoubleArray, by: DoubleArray): Pair<DoubleArray, DoubleArray>? {
        val n = a.size
        val m = Array(n) { i ->
            DoubleArray(n + 2).also { row ->
                System.arraycopy(a[i], 0, row, 0, n)
                row[n] = bx[i]
                row[n + 1] = by[i]
            }
        }
        for (col in 0 until n) {
            var pivotRow = col
            var maxAbs = abs(m[col][col])
            for (r in col + 1 until n) {
                val v = abs(m[r][col])
                if (v > maxAbs) {
                    maxAbs = v
                    pivotRow = r
                }
            }
            if (maxAbs < 1e-9) return null
            if (pivotRow != col) {
                val tmp = m[col]
                m[col] = m[pivotRow]
                m[pivotRow] = tmp
            }
            val pivotVal = m[col][col]
            for (c in col until n + 2) m[col][c] = m[col][c] / pivotVal
            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r][col]
                if (factor == 0.0) continue
                for (c in col until n + 2) m[r][c] -= factor * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] } to DoubleArray(n) { m[it][n + 1] }
    }
}
