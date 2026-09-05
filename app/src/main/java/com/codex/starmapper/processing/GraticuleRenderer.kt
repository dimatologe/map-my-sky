package com.codex.starmapper.processing

import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.SkyPoint
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Eine Polylinie (Meridian oder Parallele) in BILD-Koordinaten. */
data class GraticuleLine(val points: List<Offset>)

/** Eine Gradzahl-Beschriftung in BILD-Koordinaten. */
data class GraticuleLabel(val text: String, val pos: Offset)

/** Nur für die Diagnose (`AppDiagnosticExporter`) -- interne Bereichsschätzung von [GraticuleRenderer.compute]
 *  offengelegt, um "hat die 25-Punkte-Mindest-Messung überhaupt etwas verändert?" ohne Vermutung
 *  direkt nachrechnen zu können (Nutzerbefund 2026-08-27: Ausgabe vor/nach dem Fix identisch --
 *  Ursache muss zweifelsfrei lokalisiert werden statt erneut zu raten). */
data class GraticuleDebugInfo(
    val centerRa: Double,
    val centerDec: Double,
    val centerOnlyPxPerDeg: Float,
    val minPxPerDeg: Float,
    val fovDeg: Double,
    val raHalf: Double,
    val decMin: Double,
    val decMax: Double,
    val decStep: Float,
    val raStepDeg: Float,
    val parallelNullStats: List<String> = emptyList(),
    val wcsTypeName: String = "?",
    val projectionTypeName: String? = null,
    val hasResidualCorrection: Boolean = false,
    val mosaicTileCount: Int? = null,
    val mosaicHasFallback: Boolean? = null,
    val horizontalPeriodPx: Double? = null,
    val parallelAuditStats: List<String> = emptyList(),
    val meridianAuditStats: List<String> = emptyList(),
    val segmentDiagnostics: List<String> = emptyList(),
    val microGapFindings: List<String> = emptyList(),
    val seamCrossingChecks: List<String> = emptyList(),
    val seamCrossingCount: Int = 0,
    val maxSeamAngleDeg: Float? = null,
    val curveQualityStats: List<String> = emptyList(),
    val worstCurveQualityFinding: String? = null,
    val overlapFindings: List<String> = emptyList(),
    val midpointDeviationStats: List<String> = emptyList(),
    val worstMidpointDeviationFinding: String? = null,
    val raSweepOverlapDeg: Double? = null,
)

/** Fertig berechnetes Koordinatennetz (Editor + Export zeichnen exakt dasselbe -> WYSIWYG). */
data class GraticuleGeometry(
    val lines: List<GraticuleLine>,
    val labels: List<GraticuleLabel>,
    val debug: GraticuleDebugInfo? = null,
)

/**
 * Berechnet aus einer WCS-Lösung ein RA/Dec-Gradnetz als Polylinien + Gradzahlen, alles in
 * BILD-Koordinaten. Bewusst ohne Compose-/Android-Abhängigkeit, damit Editor (Live-Vorschau)
 * und [ExportRenderer] (1:1) dieselbe Geometrie zeichnen.
 *
 * Meridiane (konstante RA) und Parallelen (konstante Dec) werden fein abgetastet und über
 * [WcsSolutionLike.skyToImage] projiziert. Das Netz wird IMMER vollständig aus dem EINEN global
 * gefitteten Modell zu Ende gezeichnet -- auch dort, wo keine Kachel direkt gemessen hat (Nutzer-
 * Vorgabe 2026-08-27: "astrometrische Lösung heißt, es wird ein Gitterliniennetz erzeugt, und zwar
 * komplett vom Nord- bis Südhimmel" -- exakt das gleiche Prinzip, nach dem Sternbilder/DSOs/Sterne
 * bereits über dasselbe globale Modell platziert werden, keine Sonderbehandlung fürs Gitter mehr).
 * Jede Linie wird DURCHGEHEND abgetastet: jeder Punkt reicht den vorherigen als Referenz an die
 * periodische Projektion weiter (Azimut-Ast-Kontinuität, `WcsSolutionLike.skyToImage(..., reference)`)
 * -- dadurch "entwickelt" sich die Bildkoordinate über die 360°-Nahtstelle hinweg glatt weiter (kann
 * kurzzeitig außerhalb des Bildes liegen) statt an ihr künstlich neu zu beginnen, was zwei Punkte mit
 * IDENTISCHER Himmelsrichtung auf verschiedene Äste (= verschiedene Bildkoordinaten, sichtbar als
 * Lücke mitten im Bild) reißen könnte. Nur ein echtes `null` vom Modell selbst bricht die Linie
 * (Fisheye-Rückhemisphäre, oder ein Mosaik ohne jedes Vertrauen an dieser Stelle, s.
 * `skyToImageReliableOnly`) -- exakt statt geraten, kein Pixel-Distanz-Schwellenwert. Bei echt
 * horizontal-periodischen Projektionen (Cylindrical-Familie) wird jede so entstandene Teillinie DANACH
 * exakt an der sichtbaren 360°-Bildnaht aufgetrennt ([emitSeamClippedPiece], derselbe Helper
 * ([OverlayGeometry.splitPolylineAtSeam]) wie bei Sternbild-Kanten) -- bewusst NICHT anhand der leicht
 * abweichenden gefitteten Projektionsperiode, sondern anhand der tatsächlichen Bildbreite, damit beide
 * Nahtseiten exakt an demselben Punkt zusammentreffen (s. [emitSeamClippedPiece]-KDoc). Erst danach wird
 * jedes Teilstück EXAKT (Liang-Barsky) auf das Bildrechteck zugeschnitten, statt nur grob "nah genug am
 * Bild" zu behalten -- Linien enden dadurch immer exakt an der tatsächlichen Bildkante, nie mit einer
 * Lücke davor oder einem Überschuss danach.
 *
 * Beschriftung: RA in Stunden (z.B. "6h", "6h30m"), Dec in Grad (z.B. "+45°", "-10°").
 */
object GraticuleRenderer {

    // Dec-Schritt-Leiter (Grad) und RA-Schritt-Leiter (Grad, jeweils "stundenfreundlich":
    // 3.75°=0.25h, 7.5°=0.5h, 15°=1h, 30°=2h, 45°=3h, 90°=6h).
    private val DEC_STEPS = floatArrayOf(0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f)
    private val RA_STEPS_DEG = floatArrayOf(3.75f, 7.5f, 15f, 30f, 45f, 90f)
    private const val TARGET_LINES = 9f
    private const val FRONT_HEMI_COS_LIMIT = -0.0175 // cos(91°): Fisheye-Rückhemisphäre verwerfen

    // Deckel NUR für die Schrittweiten-Berechnung (s. Kommentar dort) -- großzügig über jedem normalen
    // Einzelfoto-Sichtfeld (selbst ein extremes Fisheye-Objektiv erreicht kaum 180°), aber weit unter
    // dem, was ein 360°-Panorama an fovDeg erreichen kann (>360°). Innerhalb 120-200° liefert die
    // DEC_STEPS/RA_STEPS_DEG-Leiter für dieses konkrete Testfoto durchgehend dasselbe Ergebnis (15°/15°,
    // per Hand nachgerechnet) -- der genaue Wert ist also nicht knifflig fein abzustimmen.
    private const val STEP_FOV_CAP_DEG = 120.0

    // Defensiver Deckel für [periodicVisibleCandidates] (Vollständigkeits-Audit): weit mehr, als eine
    // reale Abtastung je braucht (ein unwrapped Lauf überschreitet eine volle Bildbreite typischerweise
    // nur um wenige Prozent, s. raHalf-Kommentar), reiner Schutz gegen einen pathologischen/entarteten
    // Periodenwert, der sonst eine sehr lange k-Schleife erzeugen könnte. Die eigentliche Zeichen-Geometrie
    // ([emitSeamClippedPiece]) braucht diesen Deckel seit dem Nahtfix nicht mehr -- `splitPolylineAtSeam`
    // zerlegt jeden Naht-Übergang linear, ohne k-Schleife.
    private const val PERIODIC_WRAP_K_LIMIT = 20

    // Schwelle für den Mikrolücken-Audit ([findMicroGaps], Nutzer-Vorgabe 2026-08-27 Abschnitt 12) --
    // zwei BILDINTERNE (nicht am Rand geclippte) Endpunkte DERSELBEN Linie innerhalb dieses Pixel-Abstands
    // gelten als verdächtig nah, ohne dass ein nachgewiesener echter Modell-Null-Bereich dazwischen läge.
    // Bewusst klein genug, um echte, weiträumig getrennte Teilstücke (z.B. beidseits eines breiten
    // Fisheye-Rückhemisphären-Lochs) nicht fälschlich zu melden, aber groß genug für die vom Nutzer
    // beschriebenen kleinen Lücken/kurzen fehlenden Fragmente.
    private const val MICRO_GAP_AUDIT_THRESHOLD_PX = 100f

    // Schwellen für [findOverlappingLines] (Nutzer-Vorgabe 2026-08-28, "Linien wirken unterschiedlich
    // dick"): OVERLAP_MIN_PX -- ab welcher X-Überlappung zweier Teilstücke DERSELBEN Linie das
    // überhaupt geprüft wird (klein genug, um echte Überlappungen nicht zu verpassen, groß genug, um
    // reines Gleitkomma-Rauschen an einer exakt berührenden Nahtstelle nicht als Fund zu werten).
    // OVERLAP_Y_TOLERANCE_PX -- wie nah Y an mehreren Stichproben im überlappenden Bereich liegen muss,
    // damit es als "dieselbe Kurve doppelt gezeichnet" statt "zwei zufällig X-überlappende, aber
    // eigentlich weit auseinanderliegende Linien" gilt.
    private const val OVERLAP_MIN_PX = 5f
    private const val OVERLAP_Y_TOLERANCE_PX = 20f

    // Adaptive Unterteilung ([adaptiveRefine], Nutzer-Vorgabe 2026-08-28 Runde 2, Punkt 2 -- "subpixelige
    // geometrische Toleranz ... begründet anhand der Ausgabeauflösung/Stroke-Breite"). Verankert an der
    // bereits etablierten App-eigenen Konvention für "kleinste sichtbar gemeinte Bildschirm-Einheit":
    // `drawGraticuleLayer` (StarMapperApp.kt) deckelt die Gradnetz-Strichbreite selbst nach unten auf
    // `strokeScreen.coerceAtLeast(0.75f)` Bildschirm-Pixel -- 0,75px ist damit bereits die App-eigene
    // Definition von "gerade noch sichtbar gemeint". 0,5 BILD-Pixel liegt sicher darunter (echtes
    // Sub-Pixel) UND deutlich unter jedem in Build 217 (0.26.19/217) real gemeldeten
    // `midpointDeviationPx`-Wert (0,8-4,0px) -- garantiert, dass genau die vom Nutzer als sichtbar eckig
    // gemeldeten Stellen tatsächlich verfeinert werden. Bewusst ein fester BILD-Pixel-Wert, nicht
    // Bildschirm-/Zoom-abhängig: `compute()` kennt den aktuellen Viewport-Zoom nicht und soll ihn auch
    // nicht kennen -- die Geometrie wird EINMAL in Bild-Koordinaten berechnet und über `graticulePathsMemo`
    // gecacht; ein zoomabhängiges `compute()` würde exakt die Performance-Regression zurückbringen, die
    // diese Session bereits einmal behoben hat (s. Performance-Umbau-Plan). Gilt für die native
    // Bildauflösung; bei sehr starkem Hineinzoomen bleibt (wie bei jeder vorab tessellierten Vektorgrafik)
    // ein kleiner Rest-Toleranzspielraum, das ist eine bewusste, dokumentierte Abwägung, kein Bug.
    private const val ADAPTIVE_TOLERANCE_PX = 0.5f

    // Sicherheitsgrenze ("feste maximale Rekursionstiefe"), per Hand abgeschätzt statt geraten: die
    // Sagitta (Abweichung Sehne<->Bogen) einer Kreisbogen-Näherung skaliert QUADRATISCH mit der
    // Segmentlänge (`sagitta = R*(1-cos(Δ/2))`, für kleine Δ ≈ L²·κ/8) -- eine Halbierung der Segmentlänge
    // viertelt die Abweichung. Schlimmster in Build 217 gemeldeter Fall (RA9h): 534,1px Segment, 4,0px
    // Abweichung. Halbierung 1: ~1,0px (noch über ADAPTIVE_TOLERANCE_PX). Halbierung 2: ~0,25px (bereits
    // darunter) -- 2 Rekursionsstufen reichen für den schlimmsten bisher bekannten Fall. MAX_DEPTH=6 lässt
    // dafür 3x Sicherheitsmarge (bis zu 2^6=64 Unterteilungen pro Ursprungssegment im theoretischen
    // Extremfall -- praktisch nur für pathologisch gekrümmte Einzelfälle relevant, der ganz überwiegende
    // Teil der Segmente braucht 0-2 Stufen), ohne bei einem entarteten Sonderfall unbegrenzt zu rekursieren.
    private const val ADAPTIVE_MAX_DEPTH = 6

    // Pol-Abschluss (Nutzer-Vorgabe 2026-08-28 Runde 3, Punkt 3): Sicherheitsspanne (Grad) um den
    // ±89.5°-Bereichsdeckel von decMin/decMax, damit ein Gleitkomma-Rundungsrest die "reicht dieser
    // Bereich tatsächlich bis zum Deckel"-Prüfung nicht knapp verfehlt -- s. Kommentar bei der
    // Meridian-Schleife in compute(). Bewusst sehr klein: decMin/decMax entstehen über `.coerceAtLeast/
    // AtMost(±89.5)`, ein tatsächlich gedeckelter Wert ist danach exakt -89.5/89.5 (IEEE754-exakt), diese
    // Spanne ist reine Verteidigung gegen Rundung, kein fachlicher Toleranzbereich.
    private const val POLE_CLOSURE_EPSILON_DEG = 0.01

    fun compute(
        wcs: WcsSolutionLike,
        imageWidth: Int,
        imageHeight: Int,
        densityFactor: Float,
    ): GraticuleGeometry {
        if (imageWidth <= 0 || imageHeight <= 0) return GraticuleGeometry(emptyList(), emptyList())

        val longEdge = max(imageWidth, imageHeight).toFloat()
        val density = densityFactor.coerceIn(0.3f, 3f)

        // Nur für die Diagnose: WELCHER konkrete Lösungs-/Projektionstyp aktiv ist (reine Klassenname-
        // Reflexion -- kein neuer Zugriff auf private Felder von CorrectedProjection/ResidualCorrection,
        // die bewusst gekapselt bleiben sollen, s. [[project_gestitchte_panoramen_grenze]]). Beantwortet
        // "ist das überhaupt ein Mosaik? Ist eine Mesh-Korrektur (CorrectedProjection) aktiv? Läuft eine
        // Rest-Korrektur (ResidualCorrection) mit?" direkt aus EINER Diagnose, statt es für jede neue
        // Vermutung erneut aus dem Rendering-Verhalten zurückschließen zu müssen.
        val wcsTypeName = wcs::class.simpleName ?: "?"
        val projectionTypeName = (wcs as? PanoramaWcsSolution)?.projection?.let { it::class.simpleName }
        val hasResidualCorrection = (wcs as? RefractedPanoramaWcsSolution)?.residual != null
        val mosaicTileCount = (wcs as? MosaicWcsSolution)?.tiles?.size
        val mosaicHasFallback = (wcs as? MosaicWcsSolution)?.let { it.fallback != null }

        // Horizontale Periode der GEFITTETEN Projektion (nur Cylindrical-Familie liefert einen Wert, s.
        // PanoramaProjection.horizontalPeriodPx) -- Nachtrag 10 (2026-08-27, vom Nutzer selbst
        // hergeleitet): project()/die reference-Kette liefert bewusst eine DURCHGEHEND ENTFALTETE
        // (unwrapped) Bildkoordinate, die über mehr als eine volle Periode hinauswandern kann (raHalf
        // sweept bis zu 370°) -- reines Liang-Barsky-Zuschneiden auf [0,width] kannte diese Periodizität
        // ursprünglich nicht (Randlücke). NUR noch für die Diagnose berechnet/exponiert (`solutionType=`,
        // s. AppDiagnosticExporter) -- für die eigentliche Zeichen-Geometrie (`emitClippedRuns`) UND das
        // Vollständigkeits-/Nahtpaar-Audit unten NICHT mehr verwendet, seit dem Nahtfix (2026-08-27/28,
        // "Naht-Zusammenführung"): `horizontalPeriodPx` ist die Periode des GEFITTETEN Modells, kann aber
        // leicht von der TATSÄCHLICHEN Bildbreite abweichen (Fit-Restfehler -- konkret nachgewiesen:
        // 6477,4297px bei imageWidth=6500px, 0,35% Differenz). Ein Linienstück, das am rechten Bildrand
        // per horizontalPeriodPx-Verschiebung "zurückgefaltet" wurde, landet dadurch NICHT am selben
        // Punkt, an dem das entsprechende Stück am linken Rand natürlich beginnt -- kleiner, aber
        // sichtbarer Y-Versatz an der Naht. Die tatsächliche Bildnaht liegt dagegen IMMER exakt bei
        // x=0/x=imageWidth (so wird ein 360°-Panorama gestitcht) -- [emitSeamClippedPiece] nutzt deshalb
        // `imageWidth` (über `OverlayGeometry.splitPolylineAtSeam`, denselben Helper wie bei Sternbild-
        // Kanten) statt `horizontalPeriodPx` für die Naht-Behandlung, s. dortiges KDoc.
        val horizontalPeriodPx = (wcs as? PanoramaWcsSolution)?.projection?.horizontalPeriodPx()

        // Fisheye (radial): Sterne hinter dem ~180°-Feld verwerfen, sonst projiziert ein gefaltetes
        // Modell sie fälschlich in die Bildmitte. Nur beim radialen Modell sinnvoll.
        val fisheyeRot = (wcs as? PanoramaWcsSolution)
            ?.takeIf { it.projection is FisheyeProjection }
            ?.rotEquToPano

        // [reference] (letzter erfolgreich berechneter Bildpunkt DERSELBEN Linie): reicht bei
        // periodischen Projektionen (Cylindrical-Familie) 1:1 an PanoramaProjection.directionToPixel
        // weiter, das den Azimut dann relativ zu [reference] entfaltet statt immer den atan2-Hauptzweig
        // zu liefern. OHNE das (fehlte bis 2026-08-27) sprang die Berechnung an jedem Abtastpunkt
        // unabhängig auf den Hauptzweig zurück -- entlang eines einzelnen Meridians/einer Parallelen
        // nahe einer Projektions-Polstelle (Himmelspol NICHT an der Bildmitte, praktisch immer der Fall)
        // konnte das benachbarte Abtastpunkte auf verschiedene Äste springen lassen, sichtbar als
        // "Blütenblatt"-Muster aus geschlossenen Schleifen statt einer glatten Kurve (Nutzerbefund).
        fun project(raDeg: Double, decDeg: Double, reference: Offset?): Offset? {
            if (fisheyeRot != null) {
                if ((fisheyeRot * raDecToVector(raDeg, decDeg)).z < FRONT_HEMI_COS_LIMIT) return null
            }
            val sky = SkyPoint(raDeg.toFloat(), decDeg.toFloat())
            // Bei einem Mosaik OHNE geglückten globalen Fit (fallback=null, s. solveAllTiles()) würde
            // die normale skyToImage() für Himmelsrichtungen weit außerhalb JEDER Kachel eine ferne
            // Kachel-WCS wild extrapolieren (TAN jenseits ~90° vom eigenen Tangentialpunkt kehrt sich
            // sogar um) -- das Gitter tastet aktiv weite Himmelsbereiche ab und trifft solche Lücken
            // garantiert, sichtbar als netzartige Schleifen/Speichen weit über das Bild hinaus
            // (Nutzerbefund 2026-08-27). Ein fehlender Netzabschnitt ist dafür das ehrlichere Ergebnis.
            return if (wcs is MosaicWcsSolution) wcs.skyToImageReliableOnly(sky, imageHeight) else wcs.skyToImage(sky, imageHeight, reference)
        }

        // --- Maßstab + sichtbarer Himmelsbereich schätzen ---
        val center = centerSky(wcs, imageWidth, imageHeight)

        // NICHT nur an der Bildmitte messen: bei einer weiten/verdrehten Panorama-Projektion (Himmelspol
        // NICHT bildmittig, das Netz "wölbt" sich stark, s. project()-Kommentar) variiert die Pixel-
        // Dichte extrem über das Bild -- eine einzelne Messung an der Bildmitte unterschätzt den
        // benötigten Himmelsbereich drastisch, wenn die Mitte zufällig in einer dichten Region liegt,
        // während das Netz tatsächlich bis in eine sehr viel dünnere Region reichen muss (z.B. nahe
        // einem im Bild liegenden Himmelspol). Mehrere über das Bild verteilte Punkte abtasten und die
        // DÜNNSTE (kleinste) gemessene Dichte für die Bereichsschätzung verwenden, damit raHalf/decMin/
        // decMax den echten Bedarf nie unterschätzen -- eine zu KLEINE Schätzung lässt Linien lange vor
        // dem Bildrand enden, eine zu GROSSE kostet nur harmlos etwas mehr (später verworfene) Abtastung.
        // Nutzerbefund 2026-08-27: Netz endete bei manchen Parallelen weit vor dem rechten Bildrand,
        // andere waren fast komplett unsichtbar (nur ein winziges Stück nahe dem linken Rand).
        // 5x5-Raster (25 Punkte) statt nur weniger Randpunkte -- die Kosten sind vernachlässigbar (je
        // 2 skyToImage()-Aufrufe, einmalig pro compute()), aber ein feineres Raster senkt das Risiko,
        // die tatsächlich dünnste Region zu verfehlen, deutlich (die "Blütenblatt"-Ausbuchtungen können
        // an beliebiger Stelle zwischen den Bildrändern liegen, nicht nur an Ecken/Kantenmitten).
        val samplePixels = ArrayList<Pair<Double, Double>>(25)
        for (fx in 0..4) {
            for (fy in 0..4) {
                samplePixels += (imageWidth * fx / 4.0) to (imageHeight * fy / 4.0)
            }
        }
        val centerOnlyPxPerDeg = center?.let { pxPerDegAt(wcs, it, imageHeight) } ?: 0f
        var pxPerDeg = centerOnlyPxPerDeg
        if (center != null) {
            for ((px, py) in samplePixels) {
                val sky = wcs.imageToSkyApprox(px, py, imageHeight) ?: continue
                val local = pxPerDegAt(wcs, sky, imageHeight)
                if (local > 0f && (pxPerDeg <= 0f || local < pxPerDeg)) pxPerDeg = local
            }
        }

        val centerRa: Double
        val centerDec: Double
        val fovDeg: Double
        if (center != null && pxPerDeg > 0f) {
            centerRa = center.raDegrees.toDouble()
            centerDec = center.decDegrees.toDouble()
            fovDeg = (longEdge / pxPerDeg).toDouble()
        } else {
            // Fallback (z.B. nicht invertierbares Modell): ganzer Himmel, grobe Schritte.
            centerRa = 0.0
            centerDec = 0.0
            fovDeg = 360.0
        }

        // Schrittweiten wählen (densityFactor>1 -> feiner -> kleinerer Schritt). NUR für die
        // Schrittweite (nicht für die Abtast-REICHWEITE unten) wird fovDeg auf [STEP_FOV_CAP_DEG]
        // gedeckelt: bei einem vollen 360°-Panorama (fovDeg kann bis über 360° gehen) verteilt die
        // unveränderte Formel nur ~9 Linien über den KOMPLETTEN Himmel -- die Schrittweite springt auf
        // den gröbsten Leiterwert (30°), Lücken zwischen Nachbarlinien werden dadurch riesig (Nutzer-
        // befund 2026-08-27: DSO-Häufung liegt >700px von jeder Gitterlinie entfernt, obwohl an
        // derselben Stelle korrekt platziert -- dieselbe WCS, nur das Gitter selbst zu grob). Ein
        // normales Star-Chart hält seine Liniendichte unabhängig von der Kartengröße ungefähr konstant
        // -- ab [STEP_FOV_CAP_DEG] (deutlich mehr als jedes normale Einzelfoto-Sichtfeld) wächst die
        // Schrittweite deshalb nicht mehr weiter mit. Für fovDeg <= [STEP_FOV_CAP_DEG] (der ganz
        // überwiegende Alltagsfall, normale Einzelfotos) ändert sich am Verhalten nichts.
        val stepFovDeg = min(fovDeg, STEP_FOV_CAP_DEG)
        val idealDec = (stepFovDeg / TARGET_LINES / density).toFloat()
        val decStep = nearest(DEC_STEPS, idealDec)
        val cosCenter = max(cos(centerDec * PI / 180.0), 0.2)
        val idealRa = (decStep / cosCenter).toFloat()
        val raStepDeg = nearest(RA_STEPS_DEG, idealRa)

        // Iterationsbereich begrenzen (sonst bei engem FOV Millionen Linien).
        val margin = 1.2
        val decMin = (centerDec - fovDeg * margin).coerceAtLeast(-89.5)
        val decMax = (centerDec + fovDeg * margin).coerceAtMost(89.5)
        val raHalf = min(fovDeg * margin / cosCenter, 185.0)
        val raMin = centerRa - raHalf
        val raMax = centerRa + raHalf

        // Nahtbehandlung ([emitSeamClippedPiece]/[periodicVisibleCandidates]) NUR bei echt horizontal-
        // periodischen Projektionen (Cylindrical-Familie, `horizontalPeriodPx != null`) -- s.
        // [emitSeamClippedPiece]-KDoc. Wird jetzt (Nutzer-Vorgabe 2026-08-28, Runde nach Build 217)
        // bereits HIER (statt erst kurz vor der Parallelen-Schleife) gebraucht, s. `parallelRaHalf` unten.
        val seamAware = horizontalPeriodPx != null

        // Nutzer-Vorgabe 2026-08-28 (Runde nach Build 217, Root-Cause-Fix statt weiterer Diagnose):
        // `raHalf` bleibt GLOBAL unverändert bei bis zu 185° -- gebraucht als Sicherheits-Marge für die
        // MERIDIAN-Auswahl (`firstK..lastK` unten, deren eigene `normalizedK`-Deduplizierung bereits
        // zuverlässig verhindert, dass ein zu weiter Bereich zu doppelten Meridianen führt, s. Kommentar
        // dort) UND unverändert nötig für nicht-periodische Projektionen (Fisheye/Stereographic/
        // Rectilinear/Mesh/TAN/Mosaik, wo RA gar nicht periodisch im Bild-Pixel-Sinn ist -- `raHalf`
        // spiegelt dort echten, nicht-redundanten Sichtfeld-Bedarf wider). NUR die tatsächliche
        // Abtast-Sweep-Breite EINER periodischen DEC-Parallele wird auf exakt einen 360°-Umlauf gedeckelt:
        // eine echte 360°-Zylinderprojektion ist in RA periodisch mit exakt 360° -- JEDE Himmelsrichtung
        // bei dieser Deklination liegt bereits vollständig innerhalb EINES Umlaufs (mathematisch: jedes
        // 360°-breite Intervall deckt den vollen Kreis exakt einmal ab, unabhängig von der Zentrierung --
        // kein Abdeckungsverlust durch das Kappen). Alles über 360° hinaus (die von `raHalf` bewusst
        // eingerechnete, für die Meridian-Auswahl gedachte Marge) tastet exakt denselben Himmelsbereich
        // ein zweites Mal ab. Diagnose-Beleg (Build 217, 0.26.19/217): `raSweepOverlapDeg=10°` bei
        // `raHalf=185°`, exakt 6 Überlappungs-Funde, AUSSCHLIESSLICH auf DEC-Linien (0 auf RA-Linien --
        // Meridiane sweepen nie in RA, waren strukturell nie betroffen, s. [[project_gradnetz_randbeschriftung]]
        // Nachtrag 15). `min(raHalf, 180.0)` verkleinert den Sweep NUR, wenn `raHalf` bereits über 180°
        // liegt (der >360°-Sweep-Fall) -- ein enger werdendes Sichtfeld (raHalf<180°, z.B. ein normales
        // Einzelfoto mit zufällig Cylindrical-Fit) bleibt dadurch komplett unverändert.
        val parallelRaHalf = if (seamAware) min(raHalf, 180.0) else raHalf
        val parallelRaMin = centerRa - parallelRaHalf
        val parallelRaMax = centerRa + parallelRaHalf

        // Direkte Erfolgskontrolle des obigen Fixes: berechnet aus der TATSÄCHLICH für die Parallelen-
        // Abtastung verwendeten (gedeckelten) Sweep-Breite -- muss nach dem Fix 0° sein (statt weiterhin
        // 10° zu zeigen, wie es bei einer Berechnung aus dem unveränderten globalen `raHalf` der Fall
        // wäre). Bei nicht-periodischen Projektionen strukturell 0° (kein Sweep-Konzept dort).
        val raSweepOverlapDeg = if (seamAware) max(0.0, 2.0 * parallelRaHalf - 360.0) else 0.0

        val sampleDeg = (min(decStep, raStepDeg) / 3f).coerceIn(0.25f, 2f).toDouble()

        val lines = ArrayList<GraticuleLine>()
        val labels = ArrayList<GraticuleLabel>()

        // Nur für die Diagnose: pro gezeichneter Parallele (a) wie viele der abgetasteten Punkte waren
        // ein echtes `null` von project() (statt nur außerhalb des Bildrechtecks) UND (b) der tatsächlich
        // erreichte Pixel-X-Bereich VOR dem Rechteck-Zuschnitt UND periodischem Nachfalten -- unterscheidet
        // "Modell liefert dort nichts" (hoher Null-Anteil) von reiner Geometrie.
        val parallelNullStats = mutableListOf<String>()
        // Vollständigkeits-Audit (Nutzer-Vorgabe 2026-08-27, Abschnitt 6): pro Parallele, wie weit war
        // der am schlechtesten abgedeckte periodisch-sichtbare Testpunkt von der TATSÄCHLICH erzeugten
        // Geometrie DIESER Linie entfernt -- unabhängiges Vollständigkeits-Orakel, nicht nur "sieht die
        // Linie glatt aus".
        val parallelAuditStats = mutableListOf<String>()
        // Nutzer-Vorgabe 2026-08-27 (lokaler Restfehler), Abschnitt 4/12: pro tatsächlich gezeichnetem
        // Endstück Anfangs-/End-Ursache offenlegen + verdächtig nah beieinanderliegende, NICHT am
        // Bildrand endende Endpunkte DERSELBEN Linie automatisch aufspüren -- ohne das erst von Hand aus
        // den rohen Punktlisten herauszulesen.
        val segmentDiagnostics = mutableListOf<String>()
        val microGapFindings = mutableListOf<String>()
        // C0+C1-Nahtaudit (Nutzer-Vorgabe 2026-08-28, "C1 reicht nicht"): für JEDE echte Nahtkreuzung auf
        // JEDER Dec-/RA-Linie -- exakte (nicht Y-nächste-Heuristik) Zuordnung über [OverlayGeometry.
        // analyzeSeamCrossings], direkt auf den rohen, noch unaufgetrennten Punkten. Liefert pro Kreuzung
        // Position (C0, strukturell exakt, s. dortiges KDoc) UND Tangentenwinkel-Differenz (C1) -- eine
        // reine Y-Übereinstimmung beweist noch keinen knickfreien Übergang.
        val seamCrossingChecks = mutableListOf<String>()
        var maxSeamAngleDeg: Float? = null
        // Wellen-/Krümmungsaudit (Nutzer-Vorgabe 2026-08-28, neuer sichtbarer Fehler "wellige/eckige
        // Gradnetzlinien"): läuft auf der ROHEN, noch unaufgetrennten Punktfolge (unterscheidet A/B --
        // grobe Approximation einer glatten Kurve vs. echt instabile Punktfolge -- von C/D, die erst in
        // Verarbeitung/Rendering entstehen könnten, s. [analyzeCurveQuality]-KDoc). Pro Linie nur die
        // schlimmste Stelle gemeldet (nicht jeder Punkt), plus der schlimmste Fund über das GESAMTE Netz.
        val curveQualityStats = mutableListOf<String>()
        var worstCurveQualityFinding: String? = null
        var worstCurveQualityJumpDeg = 0f
        // Überlappungs-/Dickenaudit (Nutzer-Vorgabe 2026-08-28, "Linien wirken unterschiedlich dick").
        val overlapFindings = mutableListOf<String>()
        // Mittelpunkt-Abweichungs-Audit -- seit der Runde nach Build 217 kein separater Diagnose-Pass
        // mehr, sondern direktes Nebenprodukt der adaptiven Unterteilung ([adaptiveRefine]): zeigt die
        // TATSÄCHLICH nach der Verfeinerung verbleibende Rest-Abweichung (sollte überall <=
        // [ADAPTIVE_TOLERANCE_PX] liegen -- ein größerer Wert wäre ein sichtbares Warnsignal, dass ein
        // Segment selbst nach [ADAPTIVE_MAX_DEPTH] die Toleranz nicht erreicht hat). Pro Linie nur die
        // schlimmste Stelle, plus ein globaler Worst-Fund.
        val midpointDeviationStats = mutableListOf<String>()
        var worstMidpointDeviationFinding: String? = null
        var worstMidpointDeviationPxGlobal = 0f
        // Nahtbehandlung ([emitSeamClippedPiece]/[periodicVisibleCandidates]) NUR bei echt horizontal-
        // periodischen Projektionen aktivieren (Cylindrical-Familie, erkennbar an `horizontalPeriodPx !=
        // null`) -- bei TAN/Rectilinear divergiert die Bildkoordinate ECHT (nicht periodisch) nahe 90°
        // vom Tangentialpunkt, ein zufälliges Kreuzen eines `k*imageWidth`-Vielfachen wäre dort KEIN
        // echter Nahtübergang (s. [emitSeamClippedPiece]-KDoc). Für [periodicVisibleCandidates]/das
        // Vollständigkeits-Audit bewusst `imageWidth` (NICHT `horizontalPeriodPx` selbst, kann leicht
        // abweichen, s. [emitSeamClippedPiece]-KDoc) -- muss dieselbe Periodizität wie die tatsächlich
        // gezeichnete (nahtbewusste) Geometrie annehmen, sonst meldet der Audit fälschlich die
        // Fit-Restfehler-Diskrepanz als Lücke. (`seamAware` selbst wird bereits weiter oben, bei
        // `parallelRaHalf`, berechnet -- hier nur noch `seamPeriodPx` daraus abgeleitet.)
        val seamPeriodPx = if (seamAware) imageWidth.toDouble() else null

        // --- Parallelen (konstante Dec) ---
        run {
            val firstK = ceil(decMin / decStep).toInt()
            val lastK = floor(decMax / decStep).toInt()
            for (k in firstK..lastK) {
                val dec = (k * decStep).toDouble()
                if (dec <= -89.5 || dec >= 89.5) continue
                // Nutzer-Vorgabe 2026-08-28 (Runde nach Build 217, Punkt 1): Abtast-Sweep exakt EIN
                // 360°-Umlauf statt bis zu 370° (s. `parallelRaHalf` oben) -- verhindert die Doppelgeometrie
                // AN DER QUELLE, nicht nachträglich weggefiltert. `sampleContinuous` selbst unverändert.
                val rawProjected = sampleContinuous(parallelRaMin, parallelRaMax, dec, sampleDeg, ::project)
                val parallelSamples = sampleCount(parallelRaMax - parallelRaMin, sampleDeg)
                // Punkt 2: adaptive Unterteilung NACH der Rohabtastung, VOR Naht-Auftrennen/Zuschnitt --
                // `emitClippedRuns`/`splitPolylineAtSeam` bekommen dadurch transparent eine dichtere, aber
                // sonst identisch aufgebaute Punktfolge, beide bleiben unverändert (Nutzer-Vorgabe Punkt 3).
                val refinement = adaptiveRefine(
                    rawProjected,
                    { t -> (((parallelRaMin + (parallelRaMax - parallelRaMin) * t / parallelSamples) % 360.0 + 360.0) % 360.0) to dec },
                    ::project,
                    ADAPTIVE_TOLERANCE_PX,
                    ADAPTIVE_MAX_DEPTH,
                )
                val projected = refinement.points
                val nullCount = projected.count { it == null }
                val validX = projected.mapNotNull { it?.x }
                val rawXRange = if (validX.isEmpty()) {
                    "none"
                } else {
                    "${formatFloat1(validX.min())}..${formatFloat1(validX.max())}"
                }
                parallelNullStats += "dec=${formatDec(dec)}:null=$nullCount/${projected.size};rawX=$rawXRange"
                val linesStart = lines.size
                val crossings = mutableListOf<OverlayGeometry.SeamCrossingCheck>()
                val segments = emitClippedRuns(listOf(projected), imageWidth, imageHeight, seamAware, horizontalPeriodPx, lines, crossings)
                val ownLines = lines.subList(linesStart, lines.size).toList()
                val auditPoints = projected.filterNotNull()
                    .flatMap { periodicVisibleCandidates(it, imageWidth, imageHeight, seamPeriodPx) }
                val maxGap = auditMaxGapPx(auditPoints, ownLines)
                parallelAuditStats += "dec=${formatDec(dec)}:maxGapPx=${formatAuditGap(maxGap)};testPts=${auditPoints.size}"
                segments.forEachIndexed { idx, seg ->
                    segmentDiagnostics += "grid:DEC;value:${formatDec(dec)};segIdx:$idx;pts:${seg.points.size};" +
                        "first:${formatFloat1(seg.points.first().x)}x${formatFloat1(seg.points.first().y)};" +
                        "last:${formatFloat1(seg.points.last().x)}x${formatFloat1(seg.points.last().y)};" +
                        "startReason:${seg.startReason};endReason:${seg.endReason}"
                }
                findMicroGaps(segments, MICRO_GAP_AUDIT_THRESHOLD_PX).forEach {
                    microGapFindings += "grid:DEC;value:${formatDec(dec)};$it"
                }
                crossings.forEach { c ->
                    seamCrossingChecks += "grid:DEC;value:${formatDec(dec)};seamY:${formatFloat1(c.seamY)};" +
                        "angleDiffDeg:${if (c.angleDifferenceDeg.isFinite()) formatFloat1(c.angleDifferenceDeg) else "n/a"};" +
                        "angleDiffNearDeg:${if (c.angleDifferenceDegNear.isFinite()) formatFloat1(c.angleDifferenceDegNear) else "n/a"};" +
                        "leftLen:${formatFloat1(c.leftSegmentLength)};rightLen:${formatFloat1(c.rightSegmentLength)};" +
                        "leftWinPts:${c.leftWindowPoints};rightWinPts:${c.rightWindowPoints};" +
                        "xBefore:${formatFloat1(c.unwrappedXBefore)};yBefore:${formatFloat1(c.unwrappedYBefore)};" +
                        "xAfter:${formatFloat1(c.unwrappedXAfter)};yAfter:${formatFloat1(c.unwrappedYAfter)};" +
                        "modelPeriodPx:${c.modelPeriodPx?.let { formatFloat1(it.toFloat()) } ?: "n/a"};" +
                        "branchByWidth:${c.branchByImageWidthBefore}->${c.branchByImageWidthAfter};" +
                        "branchByModelPeriod:${c.branchByModelPeriodBefore ?: "n/a"}->${c.branchByModelPeriodAfter ?: "n/a"}"
                    if (c.angleDifferenceDeg.isFinite()) {
                        val absAngle = abs(c.angleDifferenceDeg)
                        if (maxSeamAngleDeg == null || absAngle > maxSeamAngleDeg!!) maxSeamAngleDeg = absAngle
                    }
                }
                val rawCurve = analyzeCurveQuality(projected)
                val worstJump = rawCurve.maxByOrNull { abs(it.curvatureJumpDeg) }
                if (worstJump != null) {
                    curveQualityStats += "grid:DEC;value:${formatDec(dec)};n:${rawCurve.size};" +
                        "maxJumpDeg:${formatFloat1(worstJump.curvatureJumpDeg)};maxTurnDeg:${formatFloat1(rawCurve.maxOf { abs(it.turningAngleDeg) })};" +
                        "atIdx:${worstJump.pointIndex};pos:${formatFloat1(worstJump.x)}x${formatFloat1(worstJump.y)};" +
                        "distToPrev:${formatFloat1(worstJump.pixelDistToPrevious)}"
                    if (abs(worstJump.curvatureJumpDeg) > worstCurveQualityJumpDeg) {
                        worstCurveQualityJumpDeg = abs(worstJump.curvatureJumpDeg)
                        worstCurveQualityFinding = "grid:DEC;value:${formatDec(dec)};jumpDeg:${formatFloat1(worstJump.curvatureJumpDeg)};" +
                            "turnDeg:${formatFloat1(worstJump.turningAngleDeg)};atIdx:${worstJump.pointIndex};" +
                            "pos:${formatFloat1(worstJump.x)}x${formatFloat1(worstJump.y)};distToPrev:${formatFloat1(worstJump.pixelDistToPrevious)}"
                    }
                }
                findOverlappingLines(ownLines).forEach {
                    overlapFindings += "grid:DEC;value:${formatDec(dec)};$it"
                }
                refinement.residuals.maxByOrNull { it.midpointDeviationPx }?.let { worst ->
                    midpointDeviationStats += "grid:DEC;value:${formatDec(dec)};n:${refinement.residuals.size};" +
                        "maxDevPx:${formatFloat1(worst.midpointDeviationPx)};segLenPx:${formatFloat1(worst.segmentPixelLength)};" +
                        "depth:${worst.depth};seg:${formatFloat1(worst.x0)}x${formatFloat1(worst.y0)}->${formatFloat1(worst.x1)}x${formatFloat1(worst.y1)}"
                    if (worst.midpointDeviationPx > worstMidpointDeviationPxGlobal) {
                        worstMidpointDeviationPxGlobal = worst.midpointDeviationPx
                        worstMidpointDeviationFinding = "grid:DEC;value:${formatDec(dec)};devPx:${formatFloat1(worst.midpointDeviationPx)};" +
                            "segLenPx:${formatFloat1(worst.segmentPixelLength)};depth:${worst.depth};" +
                            "seg:${formatFloat1(worst.x0)}x${formatFloat1(worst.y0)}->${formatFloat1(worst.x1)}x${formatFloat1(worst.y1)}"
                    }
                }
                pickLabel(ownLines, imageWidth, imageHeight, horizontalEdges = false)
                    ?.let { labels += GraticuleLabel(formatDec(dec), clampLabelPos(it, imageWidth, imageHeight)) }
            }
        }

        // Wie parallelAuditStats oben, nur pro Meridian -- Meridiane sweepen zwar nie in RA, aber ihre
        // BILD-x-Koordinate kann bei dieser Kamera-Verdrehung trotzdem über eine volle Periode hinaus
        // wandern (Nutzer-Vorgabe 2026-08-27, Abschnitt 3/8: die Periodizität steckt in der Projektion
        // selbst, nicht in der RA-Achse -- betrifft daher beide Linienarten gleichermaßen).
        val meridianAuditStats = mutableListOf<String>()

        // --- Meridiane (konstante RA) ---
        run {
            val firstK = ceil(raMin / raStepDeg).toInt()
            val lastK = floor(raMax / raStepDeg).toInt()
            // raHalf (oben) kann bis zu 185° erreichen -> raMax-raMin bis zu 370°, MEHR als ein voller
            // 360°-Umlauf. Ohne Absicherung durchläuft k dann mehr Werte als es distinkte Meridiane
            // gibt -- derselbe physische Meridian bekommt zwei verschiedene k (z.B. k und k+360/raStepDeg),
            // die nach der %360-Normierung auf dieselbe RA fallen und daher DOPPELT gezeichnet werden
            // (sichtbar als ein Strich mit höherer Deckung auf einem anderen, Nutzerbefund 2026-08-27).
            val cycleSteps = (360.0 / raStepDeg).roundToInt()
            val seenNormalizedK = HashSet<Int>(cycleSteps)
            for (k in firstK..lastK) {
                val normalizedK = ((k % cycleSteps) + cycleSteps) % cycleSteps
                if (!seenNormalizedK.add(normalizedK)) continue
                val raRaw = k * raStepDeg.toDouble()
                val ra = ((raRaw % 360.0) + 360.0) % 360.0
                // Kein Umlauf-Bruch nötig (Dec wraps nie), aber derselbe echte-null-Bruch wie bei den
                // Parallelen -- ein Meridian ist hier bereits EIN einziger "Lauf". Referenz-Kette wie
                // bei sampleContinuous (Azimut-Ast-Kontinuität entlang des Meridians).
                val decSamples = sampleCount(decMax - decMin, sampleDeg)
                val rawProjected = ArrayList<Offset?>(decSamples + 1)
                var reference: Offset? = null
                for (i in 0..decSamples) {
                    val decVal = decMin + (decMax - decMin) * i / decSamples
                    val p = project(ra, decVal, reference)
                    rawProjected += p
                    if (p != null) reference = p
                }
                // Adaptive Unterteilung, identisch zur Parallelen-Schleife (s. dortiger Kommentar) -- VOR
                // Naht-Auftrennen/Zuschnitt, `emitClippedRuns`/`splitPolylineAtSeam` unverändert.
                val refinement = adaptiveRefine(
                    rawProjected,
                    { t -> ra to (decMin + (decMax - decMin) * t / decSamples) },
                    ::project,
                    ADAPTIVE_TOLERANCE_PX,
                    ADAPTIVE_MAX_DEPTH,
                )
                // Pol-Abschluss (Nutzer-Vorgabe 2026-08-28 Runde 3, Punkt 3): OHNE das endet jeder
                // Meridian nur bei decMin/decMax (weiterhin ±89.5°-Sicherheitsdeckel) -- sichtbar als
                // kleiner Ring/eine Öffnung am Himmelspol statt eines glatten Zusammenlaufs, im Tiny-Sky-/
                // 360°-Betrachter besonders auffällig. NUR angehängt, wenn (a) DIESER Bereich tatsächlich
                // am Deckel selbst anliegt (decMax/decMin ist sonst eine bewusste, engere FOV-Grenze -- ein
                // normales Einzelfoto soll NICHT künstlich bis zum Pol gezogen werden) UND (b) project() an
                // Dec=±90° selbst einen ENDLICHEN Punkt liefert -- bei Cylindrical/Mercator (Y=tan(φ) bzw.
                // asinh(tan(φ))) ist der Pol eine ECHTE mathematische Singularität (MAX_PHI-Grenze in
                // CylindricalProjection.directionToPixel), project() liefert dort korrekt `null`, dieser
                // Fix wird für diese beiden Kinds automatisch zum No-Op -- nur Equirectangular (Y=φ, am Pol
                // endlich) und jede andere Projektion, deren Gültigkeitskegel den Pol zufällig einschließt,
                // profitieren. Jeder Meridian berechnet seinen EIGENEN Polpunkt mit SEINER EIGENEN,
                // unmittelbar benachbarten Referenz (reference-aware, etabliertes Prinzip dieser Session) --
                // da die Pol-RICHTUNG selbst RA-unabhängig ist (raDecToVector(*, ±90°) liefert für JEDE RA
                // denselben 3D-Vektor), landen alle Meridiane dadurch automatisch auf demselben periodischen
                // Ast wie ihre eigene, bereits nahe geclusterte Trajektorie -- kein separater globaler Punkt
                // nötig, kein Risiko einer langen Sprung-Linie quer über das Bild. Absichtlich NICHT durch
                // adaptiveRefine unterteilt (kurze, meist schwach gekrümmte Anschluss-Strecke; hält dessen
                // t-Parametrisierung sauber auf [0,decSamples] beschränkt).
                val refinedPoints = refinement.points
                val firstRegular = refinedPoints.firstOrNull { it != null }
                val lastRegular = refinedPoints.lastOrNull { it != null }
                val southPole = if (decMin <= -89.5 + POLE_CLOSURE_EPSILON_DEG && firstRegular != null) {
                    project(ra, -90.0, firstRegular)
                } else {
                    null
                }
                val northPole = if (decMax >= 89.5 - POLE_CLOSURE_EPSILON_DEG && lastRegular != null) {
                    project(ra, 90.0, lastRegular)
                } else {
                    null
                }
                val projected = buildList {
                    if (southPole != null) add(southPole)
                    addAll(refinedPoints)
                    if (northPole != null) add(northPole)
                }
                val linesStart = lines.size
                val crossings = mutableListOf<OverlayGeometry.SeamCrossingCheck>()
                val segments = emitClippedRuns(listOf(projected), imageWidth, imageHeight, seamAware, horizontalPeriodPx, lines, crossings)
                val ownLines = lines.subList(linesStart, lines.size).toList()
                val auditPoints = projected.filterNotNull()
                    .flatMap { periodicVisibleCandidates(it, imageWidth, imageHeight, seamPeriodPx) }
                val maxGap = auditMaxGapPx(auditPoints, ownLines)
                meridianAuditStats += "ra=${formatRaHours(ra)}:maxGapPx=${formatAuditGap(maxGap)};testPts=${auditPoints.size}"
                segments.forEachIndexed { idx, seg ->
                    segmentDiagnostics += "grid:RA;value:${formatRaHours(ra)};segIdx:$idx;pts:${seg.points.size};" +
                        "first:${formatFloat1(seg.points.first().x)}x${formatFloat1(seg.points.first().y)};" +
                        "last:${formatFloat1(seg.points.last().x)}x${formatFloat1(seg.points.last().y)};" +
                        "startReason:${seg.startReason};endReason:${seg.endReason}"
                }
                findMicroGaps(segments, MICRO_GAP_AUDIT_THRESHOLD_PX).forEach {
                    microGapFindings += "grid:RA;value:${formatRaHours(ra)};$it"
                }
                crossings.forEach { c ->
                    seamCrossingChecks += "grid:RA;value:${formatRaHours(ra)};seamY:${formatFloat1(c.seamY)};" +
                        "angleDiffDeg:${if (c.angleDifferenceDeg.isFinite()) formatFloat1(c.angleDifferenceDeg) else "n/a"};" +
                        "angleDiffNearDeg:${if (c.angleDifferenceDegNear.isFinite()) formatFloat1(c.angleDifferenceDegNear) else "n/a"};" +
                        "leftLen:${formatFloat1(c.leftSegmentLength)};rightLen:${formatFloat1(c.rightSegmentLength)};" +
                        "leftWinPts:${c.leftWindowPoints};rightWinPts:${c.rightWindowPoints};" +
                        "xBefore:${formatFloat1(c.unwrappedXBefore)};yBefore:${formatFloat1(c.unwrappedYBefore)};" +
                        "xAfter:${formatFloat1(c.unwrappedXAfter)};yAfter:${formatFloat1(c.unwrappedYAfter)};" +
                        "modelPeriodPx:${c.modelPeriodPx?.let { formatFloat1(it.toFloat()) } ?: "n/a"};" +
                        "branchByWidth:${c.branchByImageWidthBefore}->${c.branchByImageWidthAfter};" +
                        "branchByModelPeriod:${c.branchByModelPeriodBefore ?: "n/a"}->${c.branchByModelPeriodAfter ?: "n/a"}"
                    if (c.angleDifferenceDeg.isFinite()) {
                        val absAngle = abs(c.angleDifferenceDeg)
                        if (maxSeamAngleDeg == null || absAngle > maxSeamAngleDeg!!) maxSeamAngleDeg = absAngle
                    }
                }
                val rawCurve = analyzeCurveQuality(projected)
                val worstJump = rawCurve.maxByOrNull { abs(it.curvatureJumpDeg) }
                if (worstJump != null) {
                    curveQualityStats += "grid:RA;value:${formatRaHours(ra)};n:${rawCurve.size};" +
                        "maxJumpDeg:${formatFloat1(worstJump.curvatureJumpDeg)};maxTurnDeg:${formatFloat1(rawCurve.maxOf { abs(it.turningAngleDeg) })};" +
                        "atIdx:${worstJump.pointIndex};pos:${formatFloat1(worstJump.x)}x${formatFloat1(worstJump.y)};" +
                        "distToPrev:${formatFloat1(worstJump.pixelDistToPrevious)}"
                    if (abs(worstJump.curvatureJumpDeg) > worstCurveQualityJumpDeg) {
                        worstCurveQualityJumpDeg = abs(worstJump.curvatureJumpDeg)
                        worstCurveQualityFinding = "grid:RA;value:${formatRaHours(ra)};jumpDeg:${formatFloat1(worstJump.curvatureJumpDeg)};" +
                            "turnDeg:${formatFloat1(worstJump.turningAngleDeg)};atIdx:${worstJump.pointIndex};" +
                            "pos:${formatFloat1(worstJump.x)}x${formatFloat1(worstJump.y)};distToPrev:${formatFloat1(worstJump.pixelDistToPrevious)}"
                    }
                }
                findOverlappingLines(ownLines).forEach {
                    overlapFindings += "grid:RA;value:${formatRaHours(ra)};$it"
                }
                refinement.residuals.maxByOrNull { it.midpointDeviationPx }?.let { worst ->
                    midpointDeviationStats += "grid:RA;value:${formatRaHours(ra)};n:${refinement.residuals.size};" +
                        "maxDevPx:${formatFloat1(worst.midpointDeviationPx)};segLenPx:${formatFloat1(worst.segmentPixelLength)};" +
                        "depth:${worst.depth};seg:${formatFloat1(worst.x0)}x${formatFloat1(worst.y0)}->${formatFloat1(worst.x1)}x${formatFloat1(worst.y1)}"
                    if (worst.midpointDeviationPx > worstMidpointDeviationPxGlobal) {
                        worstMidpointDeviationPxGlobal = worst.midpointDeviationPx
                        worstMidpointDeviationFinding = "grid:RA;value:${formatRaHours(ra)};devPx:${formatFloat1(worst.midpointDeviationPx)};" +
                            "segLenPx:${formatFloat1(worst.segmentPixelLength)};depth:${worst.depth};" +
                            "seg:${formatFloat1(worst.x0)}x${formatFloat1(worst.y0)}->${formatFloat1(worst.x1)}x${formatFloat1(worst.y1)}"
                    }
                }
                pickLabel(ownLines, imageWidth, imageHeight, horizontalEdges = true)
                    ?.let { labels += GraticuleLabel(formatRaHours(ra), clampLabelPos(it, imageWidth, imageHeight)) }
            }
        }

        // Pol-Überlagerung vermeiden: an den Polen laufen alle Meridiane zusammen -> die RA-Zahlen
        // stapeln sich auf engstem Raum. Labels mit zu geringem Abstand zu einem bereits behaltenen
        // verwerfen (generisch, fängt auch sonstige Überlappungen ab).
        val minLabelSep = min(imageWidth, imageHeight) * 0.03f
        val dedupedLabels = ArrayList<GraticuleLabel>(labels.size)
        for (l in labels) {
            val tooClose = dedupedLabels.any {
                hypot((it.pos.x - l.pos.x).toDouble(), (it.pos.y - l.pos.y).toDouble()) < minLabelSep
            }
            if (!tooClose) dedupedLabels += l
        }
        val debugInfo = GraticuleDebugInfo(
            centerRa = centerRa,
            centerDec = centerDec,
            centerOnlyPxPerDeg = centerOnlyPxPerDeg,
            minPxPerDeg = pxPerDeg,
            fovDeg = fovDeg,
            raHalf = raHalf,
            decMin = decMin,
            decMax = decMax,
            decStep = decStep,
            raStepDeg = raStepDeg,
            parallelNullStats = parallelNullStats,
            wcsTypeName = wcsTypeName,
            projectionTypeName = projectionTypeName,
            hasResidualCorrection = hasResidualCorrection,
            mosaicTileCount = mosaicTileCount,
            mosaicHasFallback = mosaicHasFallback,
            horizontalPeriodPx = horizontalPeriodPx,
            parallelAuditStats = parallelAuditStats,
            meridianAuditStats = meridianAuditStats,
            segmentDiagnostics = segmentDiagnostics,
            microGapFindings = microGapFindings,
            seamCrossingChecks = seamCrossingChecks,
            seamCrossingCount = seamCrossingChecks.size,
            maxSeamAngleDeg = maxSeamAngleDeg,
            curveQualityStats = curveQualityStats,
            worstCurveQualityFinding = worstCurveQualityFinding,
            overlapFindings = overlapFindings,
            midpointDeviationStats = midpointDeviationStats,
            worstMidpointDeviationFinding = worstMidpointDeviationFinding,
            raSweepOverlapDeg = raSweepOverlapDeg,
        )
        return GraticuleGeometry(lines, dedupedLabels, debugInfo)
    }

    /**
     * Tastet eine Parallele (festes [dec]) DURCHGEHEND über [raMin]..[raMax] ab (reine Funktion ihrer
     * beiden Parameter -- der Aufrufer entscheidet über die Sweep-Breite; seit der Runde nach Build 217
     * übergibt [compute]s Parallelen-Schleife bewusst `parallelRaMin`/`parallelRaMax`, gedeckelt auf
     * exakt einen 360°-Umlauf statt der bis zu 370° breiten `raMin`/`raMax`, s. Kommentar bei
     * `parallelRaHalf` in [compute]) -- OHNE die Abtastung an der 360°-Nahtstelle in
     * separate Teilbereiche zu zerlegen. Jeder Punkt reicht dem VORHERIGEN als `reference` an [project]
     * weiter (Azimut-Ast-Kontinuität, s. Kommentar bei `project` in [compute]) und bleibt dadurch
     * DURCHGEHEND auf demselben Ast -- die Bildkoordinate "entwickelt" sich dadurch über die Nahtstelle
     * hinweg glatt weiter (kann kurzzeitig außerhalb [0,width] liegen), [clipPolylineToRect] schneidet
     * das Ergebnis danach exakt aufs sichtbare Bildrechteck zu. Ersetzt eine frühere Fassung, die an
     * jeder Nahtstelle NEU bei `reference=null` begann -- das riss zwei Punkte, die exakt denselben
     * Himmelspunkt (RA=0°=360°) beschreiben, potenziell auf verschiedene Äste (unterschiedliche
     * Bildkoordinate trotz identischer Himmelsrichtung) und erzeugte dadurch eine sichtbare Lücke
     * MITTEN im Bild statt am Rand (Nutzerbefund 2026-08-27, per Screenshot bestätigt).
     */
    private fun sampleContinuous(
        raMin: Double,
        raMax: Double,
        dec: Double,
        sampleDeg: Double,
        project: (Double, Double, Offset?) -> Offset?,
    ): List<Offset?> {
        val samples = sampleCount(raMax - raMin, sampleDeg)
        val result = ArrayList<Offset?>(samples + 1)
        var reference: Offset? = null
        for (s in 0..samples) {
            val raRaw = raMin + (raMax - raMin) * s / samples
            val p = project(((raRaw % 360.0) + 360.0) % 360.0, dec, reference)
            result += p
            if (p != null) reference = p
        }
        return result
    }

    /** Ein Punkt aus [analyzeCurveQuality] mit auffälligem lokalem Richtungswechsel -- Nutzer-Vorgabe
     *  2026-08-28 (neuer sichtbarer Fehler, "wellige/eckige Gradnetzlinien"): unterscheidet A) grobe
     *  Approximation einer glatten Kurve (Richtungswechsel wächst/schrumpft GLEICHMÄSSIG über
     *  mehrere Punkte, s. `curvatureJumpDeg` bleibt klein) von B) echt zickzack-artiger/instabiler
     *  Punktfolge (ein einzelner Punkt weicht stark von seinen Nachbarn ab, `curvatureJumpDeg` groß).
     *  [turningAngleDeg] ist der Richtungswechsel AN diesem Punkt (Winkel zwischen dem eingehenden und
     *  dem ausgehenden Liniensegment) -- bei einer glatt gekrümmten Kurve (z.B. nahe einem Himmelspol,
     *  wo die Projektion selbst stark krümmt) NICHT automatisch ein Fehler, kann dort legitim groß
     *  UND über mehrere Punkte hinweg ähnlich sein. [curvatureJumpDeg] (`turningAngleDeg[i] -
     *  turningAngleDeg[i-1]`) ist die eigentlich aussagekräftige Größe: ein abrupter SPRUNG im
     *  Richtungswechsel (nicht der Wechsel selbst) ist das Zickzack-Signal. */
    private data class CurveQualityFinding(
        val pointIndex: Int,
        val x: Float,
        val y: Float,
        val pixelDistToPrevious: Float,
        val segmentDirectionDeg: Float,
        val turningAngleDeg: Float,
        val curvatureJumpDeg: Float,
    )

    /**
     * Berechnet pro INNEREM Punkt einer (evtl. `null`-lückenhaften) Punktfolge Richtung/Richtungswechsel/
     * Krümmungssprung -- läuft NUR innerhalb zusammenhängender Nicht-`null`-Läufe (wie [splitAtNulls],
     * aber rein lesend, keine Ursachen-Klassifikation nötig). [pointIndex] bezieht sich auf den Index in
     * [points] SELBST (inkl. übersprungener `null`-Einträge), damit ein Fund direkt gegen andere
     * Diagnose-Zeilen (die denselben Index-Raum nutzen) abgleichbar bleibt.
     */
    private fun analyzeCurveQuality(points: List<Offset?>): List<CurveQualityFinding> {
        val findings = mutableListOf<CurveQualityFinding>()
        var runIndices = mutableListOf<Int>()
        fun flushRun() {
            if (runIndices.size < 3) { runIndices = mutableListOf(); return }
            val dirs = FloatArray(runIndices.size - 1)
            for (j in 1 until runIndices.size) {
                val a = points[runIndices[j - 1]]!!
                val b = points[runIndices[j]]!!
                dirs[j - 1] = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            }
            var prevTurn: Float? = null
            for (j in 1 until dirs.size) {
                var turn = dirs[j] - dirs[j - 1]
                turn = ((turn + 180f) % 360f + 360f) % 360f - 180f
                val idx = runIndices[j]
                val p = points[idx]!!
                val prev = points[runIndices[j - 1]]!!
                val jump = if (prevTurn == null) 0f else {
                    var d = turn - prevTurn!!
                    d = ((d + 180f) % 360f + 360f) % 360f - 180f
                    d
                }
                findings += CurveQualityFinding(
                    pointIndex = idx,
                    x = p.x,
                    y = p.y,
                    pixelDistToPrevious = hypot((p.x - prev.x).toDouble(), (p.y - prev.y).toDouble()).toFloat(),
                    segmentDirectionDeg = dirs[j],
                    turningAngleDeg = turn,
                    curvatureJumpDeg = jump,
                )
                prevTurn = turn
            }
            runIndices = mutableListOf()
        }
        for (i in points.indices) {
            if (points[i] == null) { flushRun() } else { runIndices += i }
        }
        flushRun()
        return findings
    }

    /** Ein ENDGÜLTIGES (nicht weiter unterteiltes) Segment aus [adaptiveRefine] -- entweder weil seine
     *  Mittelpunkt-Sehnen-Abweichung [midpointDeviationPx] bereits innerhalb [ADAPTIVE_TOLERANCE_PX] lag,
     *  oder weil [ADAPTIVE_MAX_DEPTH] erreicht wurde (dann bleibt die Abweichung SICHTBAR im Fund
     *  erhalten, statt zu verschwinden -- ein Fall, der selbst nach maximaler Rekursionstiefe die
     *  Toleranz nicht erreicht, muss auffindbar bleiben). [depth] = Anzahl der Halbierungen, die für
     *  DIESES Segment nötig waren (0 = das ursprüngliche Rohsegment war bereits fein genug). Für das
     *  "wo genau" reicht die Positions-Angabe [x0]/[y0]/[x1]/[y1] -- ein Index ins ursprüngliche
     *  Rohpunkt-Array gibt es nach der Unterteilung nicht mehr sinnvoll. */
    private data class MidpointDeviationFinding(
        val depth: Int,
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val segmentPixelLength: Float,
        val midpointDeviationPx: Float,
    )

    /** Ergebnis von [adaptiveRefine]: die verfeinerte (dichtere) Punktfolge PLUS die tatsächlich
     *  erreichte Mittelpunkt-Sehnen-Abweichung jedes ENDGÜLTIGEN Segments -- als Nebenprodukt derselben
     *  Rechnung, keine zweite, separate Abtastung mehr nötig (ersetzt die frühere eigenständige
     *  `analyzeMidpointDeviation` dieser Session, die denselben Mittelpunkt noch einmal unabhängig
     *  abgetastet hatte). */
    private data class RefinementResult(val points: List<Offset?>, val residuals: List<MidpointDeviationFinding>)

    /**
     * Adaptive Unterteilung (Nutzer-Vorgabe 2026-08-28, Runde nach Build 217, Punkt 2 -- "keine sichtbar
     * polygonalen Ecken... mehr Punkte nur dort, wo die Projektion sie braucht"): fügt zwischen je zwei
     * aufeinanderfolgenden, bereits erfolgreich projizierten Punkten aus [points] rekursiv so viele
     * Zwischenpunkte ein, bis der ECHTE Kurvenpunkt auf halbem Weg entlang der Linien-eigenen
     * Parametrisierung ([skyAt], halbe RA bei einer Parallele/halbe Dec bei einem Meridian -- NICHT ein
     * sphärischer Großkreis-Mittelpunkt: eine Parallele abseits des Äquators ist selbst kein Großkreis,
     * dessen Mittelpunkt läge nicht auf ihr und würde etwas anderes messen) innerhalb [tolerancePx] der
     * Pixel-Sehne zwischen den beiden aktuellen Segment-Endpunkten liegt, oder [maxDepth] erreicht ist
     * (Sicherheitsgrenze). Rührt [sampleContinuous]/die Meridian-Abtastschleife selbst NICHT an (bleiben
     * unverändert, weiterhin geschützte Kernfunktionen) -- arbeitet rein als Nachbearbeitung auf deren
     * bereits fertiger Roh-Punktfolge, bevor diese an [emitClippedRuns] (Naht-Auftrennen/Zuschnitt,
     * ebenfalls unverändert) weitergereicht wird. `null`-Einträge (echte Modell-Lücken) werden
     * unverändert durchgereicht, nie selbst verfeinert.
     *
     * Referenz-Kette bei periodischen Projektionen (Nutzer-Vorgabe: "den passenden unwrapped Ast über
     * eine Reference nahe der interpolierten Pixelposition auswählen"): jeder neu eingefügte Mittelpunkt
     * wird mit dem jeweils LINKEN Segment-Endpunkt als `reference` projiziert -- der ist per Konstruktion
     * bereits nahe der zu erwartenden Position (Parameterabstand höchstens die halbe aktuelle
     * Segmentbreite), exakt die verlangte "Reference nahe der interpolierten Pixelposition".
     */
    private fun adaptiveRefine(
        points: List<Offset?>,
        skyAt: (Double) -> Pair<Double, Double>,
        project: (Double, Double, Offset?) -> Offset?,
        tolerancePx: Float,
        maxDepth: Int,
    ): RefinementResult {
        if (points.isEmpty()) return RefinementResult(points, emptyList())
        val out = ArrayList<Offset?>(points.size)
        val residuals = mutableListOf<MidpointDeviationFinding>()
        out += points[0]
        var prevPoint = points[0]
        var prevT = 0.0
        for (i in 1 until points.size) {
            val p = points[i]
            if (p != null && prevPoint != null) {
                val segmentPoints = mutableListOf<Offset>()
                refineSegment(prevPoint, prevT, p, i.toDouble(), skyAt, project, tolerancePx, 0, maxDepth, segmentPoints, residuals)
                out += segmentPoints
            } else {
                out += p
            }
            prevPoint = p
            prevT = i.toDouble()
        }
        return RefinementResult(out, residuals)
    }

    /** Rekursiver Kern von [adaptiveRefine] für EIN Segment `a`(bei Parameter `ta`)->`b`(bei `tb`). Bricht
     *  IMMER genau EINEN Fund für das jeweils endgültige Segment nach [residuals] durch (auch wenn
     *  [maxDepth] erreicht wurde, OHNE dass die Toleranz erreicht wäre -- s. [MidpointDeviationFinding]-
     *  KDoc) und genau EINEN Punkt (das jeweilige `b`) nach [out]. */
    private fun refineSegment(
        a: Offset,
        ta: Double,
        b: Offset,
        tb: Double,
        skyAt: (Double) -> Pair<Double, Double>,
        project: (Double, Double, Offset?) -> Offset?,
        tolerancePx: Float,
        depth: Int,
        maxDepth: Int,
        out: MutableList<Offset>,
        residuals: MutableList<MidpointDeviationFinding>,
    ) {
        val tm = (ta + tb) / 2.0
        val (raM, decM) = skyAt(tm)
        val mid = project(raM, decM, a)
        if (mid == null) {
            // Mittelpunkt nicht projizierbar (z.B. Modell-Grenze knapp zwischen zwei sonst gültigen
            // Punkten) -- Segment unverändert übernehmen, kein Unterteilungsversuch möglich.
            out += b
            return
        }
        val dev = distPointToSegment(mid, a, b)
        if (dev > tolerancePx && depth < maxDepth) {
            refineSegment(a, ta, mid, tm, skyAt, project, tolerancePx, depth + 1, maxDepth, out, residuals)
            refineSegment(mid, tm, b, tb, skyAt, project, tolerancePx, depth + 1, maxDepth, out, residuals)
            return
        }
        val segLen = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
        residuals += MidpointDeviationFinding(depth, a.x, a.y, b.x, b.y, segLen, dev)
        out += b
    }

    /**
     * Sucht innerhalb DERSELBEN Dec-/RA-Linie nach Paaren fertiger [GraticuleLine]s, deren X-Bereiche
     * sich über eine spürbare Strecke ÜBERLAPPEN (nicht nur an einem gemeinsamen Nahtpunkt berühren)
     * UND deren Y-Werte im überlappenden Bereich nah beieinander liegen -- Nutzer-Vorgabe 2026-08-28
     * ("Gradnetzlinien wirken unterschiedlich dick"): ein plausibler Mechanismus dafür ist, dass
     * `raHalf` (Parallelen können bis zu 370° RA überstreichen, s. Kommentar in [compute]) denselben
     * Himmelsbereich zweimal abtastet -- beide Kopien würden nach dem Naht-Auftrennen an derselben
     * Bildstelle landen und dort übereinandergezeichnet dicker wirken. Rein geometrisch/nachträglich
     * geprüft (Bounding-Box-Überlappung + Y-Abgleich an mehreren Stichproben), KEINE Änderung an
     * `raHalf`/`sampleContinuous` selbst -- reine Beobachtung, kein Fix.
     */
    private fun findOverlappingLines(ownLines: List<GraticuleLine>): List<String> {
        if (ownLines.size < 2) return emptyList()
        val findings = mutableListOf<String>()
        for (i in ownLines.indices) {
            for (j in i + 1 until ownLines.size) {
                val a = ownLines[i].points
                val b = ownLines[j].points
                val aMinX = a.minOf { it.x }; val aMaxX = a.maxOf { it.x }
                val bMinX = b.minOf { it.x }; val bMaxX = b.maxOf { it.x }
                val overlapMinX = max(aMinX, bMinX)
                val overlapMaxX = min(aMaxX, bMaxX)
                val overlapWidth = overlapMaxX - overlapMinX
                if (overlapWidth < OVERLAP_MIN_PX) continue
                val sampleCountLocal = 5
                var closeCount = 0
                var maxYDiff = 0f
                var sampled = 0
                for (s in 0 until sampleCountLocal) {
                    val x = overlapMinX + overlapWidth * s / (sampleCountLocal - 1)
                    val yA = interpolateYAtX(a, x) ?: continue
                    val yB = interpolateYAtX(b, x) ?: continue
                    sampled++
                    val diff = abs(yA - yB)
                    if (diff > maxYDiff) maxYDiff = diff
                    if (diff < OVERLAP_Y_TOLERANCE_PX) closeCount++
                }
                if (sampled >= 3 && closeCount == sampled) {
                    findings += "segA:$i;segB:$j;overlapWidthPx:${formatFloat1(overlapWidth)};maxYDiffPx:${formatFloat1(maxYDiff)};" +
                        "samples:$sampled"
                }
            }
        }
        return findings
    }

    /** Lineare Interpolation von Y bei gegebenem [x] entlang einer Polylinie -- nutzt das ERSTE
     *  Segment, dessen X-Spanne [x] enthält (bei nicht-monotonem X, z.B. sehr starker lokaler Krümmung,
     *  eine bewusste Vereinfachung für diesen rein diagnostischen Zweck). `null`, wenn [x] außerhalb
     *  jeder Segment-X-Spanne der Polylinie liegt. */
    private fun interpolateYAtX(points: List<Offset>, x: Float): Float? {
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (a.x == b.x) continue
            val lo = min(a.x, b.x)
            val hi = max(a.x, b.x)
            if (x < lo || x > hi) continue
            val t = (x - a.x) / (b.x - a.x)
            return a.y + t * (b.y - a.y)
        }
        return null
    }

    /** Ein von [splitAtNulls] erzeugtes zusammenhängendes Rohstück (mind. 2 Punkte), VOR jedem
     *  Rechteck-Zuschnitt/periodischem Nachfalten, samt der Ursache für Anfang/Ende: `PARAM_RANGE_END`
     *  (Rand des abgetasteten RA-/Dec-Bereichs) oder `MODEL_NULL(n)` (unmittelbar davor/danach lieferte
     *  project() `n` echte `null`-Samples in Folge -- `n=1` = mögliche numerische Einzelprobe statt
     *  eines echten Lochs, s. Nutzer-Vorgabe 2026-08-27 Abschnitt 7). */
    private data class RawPiece(val points: List<Offset>, val startReason: String, val endReason: String)

    /** Nur für die Diagnose (Nutzer-Vorgabe 2026-08-27, Abschnitt 4): ein fertig zugeschnittenes,
     *  gezeichnetes Liniensegment samt klassifizierter Anfangs-/End-Ursache. */
    private data class ClippedSegment(val points: List<Offset>, val startReason: String, val endReason: String)

    /** Zerlegt eine (evtl. `null`-lückenhafte) Abtast-Punktfolge an jedem echten `null` in saubere
     *  Teilstücke (mind. 2 Punkte) samt Anfangs-/End-Ursache. Braucht dafür einen indexbasierten statt
     *  rein sequenziellen Durchlauf, weil die End-Ursache eines Stücks von den NACHFOLGENDEN Punkten
     *  abhängt (Länge des folgenden Null-Laufs), die beim sequenziellen Erreichen des Stück-Endes noch
     *  nicht bekannt sind. */
    private fun splitAtNulls(points: List<Offset?>): List<RawPiece> {
        data class Range(val startIdx: Int, val endIdx: Int)
        val ranges = mutableListOf<Range>()
        var runStart = -1
        for (i in points.indices) {
            val p = points[i]
            val valid = p != null && p.x.isFinite() && p.y.isFinite()
            if (valid) {
                if (runStart < 0) runStart = i
            } else {
                if (runStart >= 0 && i - runStart >= 2) ranges += Range(runStart, i - 1)
                runStart = -1
            }
        }
        if (runStart >= 0 && points.size - runStart >= 2) ranges += Range(runStart, points.size - 1)

        fun nullRunBefore(idx: Int): Int {
            var n = 0
            var i = idx - 1
            while (i >= 0 && points[i] == null) { n++; i-- }
            return n
        }
        fun nullRunAfter(idx: Int): Int {
            var n = 0
            var i = idx + 1
            while (i < points.size && points[i] == null) { n++; i++ }
            return n
        }

        return ranges.map { r ->
            val startReason = if (r.startIdx == 0) "PARAM_RANGE_END" else "MODEL_NULL(${nullRunBefore(r.startIdx)})"
            val endReason = if (r.endIdx == points.size - 1) "PARAM_RANGE_END" else "MODEL_NULL(${nullRunAfter(r.endIdx)})"
            RawPiece(points.subList(r.startIdx, r.endIdx + 1).map { it!! }, startReason, endReason)
        }
    }

    /** [splitAtNulls] + nahtbewusstes Zuschneiden ([emitSeamClippedPiece]) für jeden abgetasteten Lauf.
     *  Schreibt die reine Geometrie nach [out] (wie bisher, unverändertes Render-Ergebnis) UND liefert
     *  zusätzlich die [ClippedSegment]s samt Anfangs-/End-Ursache zurück, für den Segment-/Mikrolücken-
     *  Audit an der Aufrufstelle. [seamAware] muss `true` sein, damit überhaupt an Vielfachen von [width]
     *  aufgetrennt wird -- s. [emitSeamClippedPiece]-KDoc für die Sicherheitsbegründung. Ruft bei
     *  [seamAware]=true zusätzlich [OverlayGeometry.analyzeSeamCrossings] auf JEDEM Rohstück auf (VOR dem
     *  Auftrennen/Zuschneiden, exakte Kreuzungs-Zuordnung statt der verworfenen, Y-nächsten Heuristik aus
     *  `findSeamPairs`, s. Nutzer-Vorgabe 2026-08-28 "C1 reicht nicht") und sammelt die Ergebnisse in
     *  [seamChecksOut]. */
    private fun emitClippedRuns(
        runs: List<List<Offset?>>,
        width: Int,
        height: Int,
        seamAware: Boolean,
        modelPeriodPx: Double?,
        out: MutableList<GraticuleLine>,
        seamChecksOut: MutableList<OverlayGeometry.SeamCrossingCheck>,
    ): List<ClippedSegment> {
        val segments = mutableListOf<ClippedSegment>()
        for (run in runs) {
            for (piece in splitAtNulls(run)) {
                if (seamAware) seamChecksOut += OverlayGeometry.analyzeSeamCrossings(piece.points, width, modelPeriodPx)
                for (seg in emitSeamClippedPiece(piece, width, height, seamAware)) {
                    out += GraticuleLine(seg.points)
                    segments += seg
                }
            }
        }
        return segments
    }

    /**
     * Schneidet [piece] aufs Bildrechteck zu -- bei [seamAware]=true wird [piece] VORHER exakt an der
     * sichtbaren 360°-Bildnaht aufgetrennt ([OverlayGeometry.splitPolylineAtSeam], EXAKT derselbe Helper,
     * mit dem Sternbild-Kanten bereits nahtsicher gezeichnet werden, s.
     * `AstapOverlayMapper.createConstellationOverlays` + `OverlayGeometry.splitPolylineAtSeam`-KDoc).
     * Nutzer-Vorgabe 2026-08-27 ("Naht-Zusammenführung"): das reine periodische Nachfalten aus Nachtrag 10
     * (Verschieben um `horizontalPeriodPx`, EIGENE Periode des GEFITTETEN Projektionsmodells, dann
     * unabhängig je Kopie ans Rechteck geclippt) reichte für VOLLSTÄNDIGKEIT, aber nicht für eine
     * PIXELGENAUE Nahtkopplung -- Diagnose bestätigt `horizontalPeriodPx=6477.4297` bei `imageWidth=6500`
     * (0,35% Differenz, Fit-Restfehler des Zylindermodells). Die Bildnaht selbst liegt aber IMMER exakt
     * bei `x=0`/`x=imageWidth` (so wird ein 360°-Panorama gestitcht, unabhängig vom Fit-Ergebnis) --
     * `splitPolylineAtSeam` nutzt deshalb bewusst `imageWidth`, nicht `horizontalPeriodPx`, als
     * Umbruch-Periode. Dadurch entsteht am Schnittpunkt ein EXAKT gemeinsamer Punkt (identische Y, nur X
     * wird beim Normalisieren je Teilstück verschoben) -- anders als beim alten Mechanismus, wo ein
     * rechtsseitig bei x=width geclipptes Stück (`k=0`, `X(t)=width`) und ein linksseitig bei x=0
     * geclipptes Stück (`k=-1`-Kopie, `X(t)=horizontalPeriodPx`) tatsächlich VERSCHIEDENE t-Werte (also
     * verschiedene Y-Werte derselben Kurve) repräsentierten -- exakt der gemeldete kleine Y-Versatz an der
     * Naht. `splitPolylineAtSeam` deckt dabei automatisch auch den Vollständigkeits-Fall aus Nachtrag 10 ab
     * (ein durchgehend entfaltetes Stück kann über mehr als eine Bildbreite hinausgehen, z.B. bei einem
     * 370°-RA-Schwenk, s. `raHalf`) -- es zerlegt JEDEN Naht-Übergang, egal wie viele, nicht nur einen
     * festen `k`-Bereich.
     *
     * [seamAware] MUSS an der Cylindrical-Periodizität hängen (Aufrufer übergibt `horizontalPeriodPx !=
     * null`), NICHT unbedingt aufgerufen werden: nur bei einer echten 360°-Zylinder-Projektion ist
     * `x=0`↔`x=width` tatsächlich derselbe Himmelspunkt. Bei TAN/Rectilinear divergiert die Bildkoordinate
     * dagegen ECHT (nicht periodisch) gegen unendlich, je näher eine Himmelsrichtung an 90° vom
     * Tangentialpunkt liegt (`r=f·tan(θ)`) -- ein zufälliges Kreuzen eines `k*width`-Vielfachen wäre dort
     * KEIN echter Nahtübergang, sondern reines Rauschen, und ein Auftrennen+Zurückfalten würde eine
     * frei erfundene "Geisterlinie" an einer bedeutungslosen Stelle erzeugen. Fisheye/Stereographic/Mesh
     * sind ebenfalls nicht in diesem Sinne periodisch (radial um ein Zentrum, kein Links-rechts-Umbruch).
     * Bei `seamAware=false` (oder `width<=0`) bleibt [piece] unverändert EIN Stück -- exakt das alte,
     * unveränderte Verhalten für alle nicht-periodischen Projektionen.
     *
     * Jedes resultierende, endgültig zugeschnittene Teilstück bekommt seine Anfangs-/End-Ursache
     * klassifiziert (Priorität): (1) echtes Rohstück-Ende (nur beim allerersten/letzten Naht-Teilstück
     * möglich) -> dessen Ursache aus [RawPiece] (`PARAM_RANGE_END`/`MODEL_NULL(n)`); (2) ein von
     * `splitPolylineAtSeam` eingefügter Naht-Schnittpunkt -> `SEAM_SPLIT`; (3) sonst Bildrand ->
     * `CLIPPED_LEFT/RIGHT/TOP/BOTTOM`; (4) sonst (sollte praktisch nie vorkommen) -> `OTHER`.
     */
    private fun emitSeamClippedPiece(piece: RawPiece, width: Int, height: Int, seamAware: Boolean): List<ClippedSegment> {
        val results = mutableListOf<ClippedSegment>()
        val w = width.toFloat()
        val h = height.toFloat()
        val eps = 1.0f

        fun classify(p: Offset, trueEndpoint: Offset?, trueReason: String, isSeamSplit: Boolean): String = when {
            trueEndpoint != null && abs(p.x - trueEndpoint.x) < eps && abs(p.y - trueEndpoint.y) < eps -> trueReason
            isSeamSplit -> "SEAM_SPLIT"
            abs(p.x - 0f) < eps -> "CLIPPED_LEFT"
            abs(p.x - w) < eps -> "CLIPPED_RIGHT"
            abs(p.y - 0f) < eps -> "CLIPPED_TOP"
            abs(p.y - h) < eps -> "CLIPPED_BOTTOM"
            else -> "OTHER"
        }

        val seamPieces = if (seamAware && width > 0) OverlayGeometry.splitPolylineAtSeam(piece.points, width) else listOf(piece.points)
        val lastIdx = seamPieces.size - 1
        seamPieces.forEachIndexed { idx, seamPiece ->
            if (seamPiece.size < 2) return@forEachIndexed
            val startIsTrue = idx == 0
            val endIsTrue = idx == lastIdx
            for (run in clipPolylineToRect(seamPiece, width, height)) {
                if (run.size < 2) continue
                val startReason = classify(run.first(), if (startIsTrue) seamPiece.first() else null, piece.startReason, !startIsTrue)
                val endReason = classify(run.last(), if (endIsTrue) seamPiece.last() else null, piece.endReason, !endIsTrue)
                results += ClippedSegment(run, startReason, endReason)
            }
        }
        return results
    }

    /**
     * Mikrolücken-Audit (Nutzer-Vorgabe 2026-08-27, Abschnitt 12): innerhalb DERSELBEN Dec-/RA-Linie
     * jedes Paar unterschiedlicher Segmente prüfen -- Alarm, wenn beide beteiligten Endpunkte BILDINTERN
     * liegen (keine `CLIPPED_*`-Ursache und kein `SEAM_SPLIT` -- beides ist der Bildrand/die Naht selbst,
     * kein Fehler) UND sie in Pixeln nah beieinander liegen. Genau dieses Muster (zwei eigentlich
     * zusammengehörige Stücke, die nicht am Bildrand, sondern mitten im Bild knapp nebeneinander enden)
     * entspricht den vom Nutzer markierten Mikrolücken/isolierten Fragmenten. `SEAM_SPLIT`-Paare haben seit
     * dem Nahtfix ihre eigene, gezieltere C0+C1-Diagnose ([OverlayGeometry.analyzeSeamCrossings], aufgerufen
     * in [emitClippedRuns]) -- hier ausgeschlossen, damit sie den allgemeinen Mikrolücken-Audit nicht mit
     * (erwarteten, praktisch deckungsgleichen) Naht-Paaren flutet. */
    private fun findMicroGaps(segments: List<ClippedSegment>, maxGapPx: Float): List<String> {
        data class Endpoint(val pos: Offset, val reason: String, val segIdx: Int, val isStart: Boolean)
        val interior = mutableListOf<Endpoint>()
        segments.forEachIndexed { idx, seg ->
            if (!seg.startReason.startsWith("CLIPPED_") && seg.startReason != "SEAM_SPLIT") {
                interior += Endpoint(seg.points.first(), seg.startReason, idx, true)
            }
            if (!seg.endReason.startsWith("CLIPPED_") && seg.endReason != "SEAM_SPLIT") {
                interior += Endpoint(seg.points.last(), seg.endReason, idx, false)
            }
        }
        val findings = mutableListOf<String>()
        for (i in interior.indices) {
            for (j in i + 1 until interior.size) {
                val a = interior[i]
                val b = interior[j]
                if (a.segIdx == b.segIdx) continue // dieselbe Rohlinie, kein "anderes" Segment
                val d = hypot((a.pos.x - b.pos.x).toDouble(), (a.pos.y - b.pos.y).toDouble()).toFloat()
                if (d <= maxGapPx) {
                    findings += "gapPx=${formatFloat1(d)};a=${formatFloat1(a.pos.x)}x${formatFloat1(a.pos.y)}(${a.reason});" +
                        "b=${formatFloat1(b.pos.x)}x${formatFloat1(b.pos.y)}(${b.reason})"
                }
            }
        }
        return findings
    }

    // `findSeamPairs` (Nutzer-Vorgabe 2026-08-27) hier bis 0.26.16/214 -- entfernt (2026-08-28, "C1 reicht
    // nicht"): paarte Randpunkte über eine Y-nächste HEURISTIK nach dem Zuschnitt (setzte fälschlich
    // `points.first()` == potenzieller linker Rand / `points.last()` == potenzieller rechter Rand voraus;
    // bei umgekehrt orientierten Teilstücken -- nachweislich der Regelfall bei Meridianen, deren Dec-Sweep
    // von "innen nach links" bzw. "von rechts nach innen" läuft -- fand sie dadurch keinen oder den
    // falschen Kandidaten). Ersetzt durch [OverlayGeometry.analyzeSeamCrossings], aufgerufen direkt in
    // [emitClippedRuns] auf den ROHEN (noch unaufgetrennten) Stücken -- dort ist die Zuordnung "welche
    // beiden Punkte gehören zu genau dieser einen Kreuzung" durch schlichte Index-Nachbarschaft (i-1,i)
    // exakt bekannt, keine Heuristik nötig, UND es wird zusätzlich die Tangentenrichtung (C1), nicht nur
    // die Position (C0), verglichen.

    /** Alle periodisch äquivalenten Kopien von [p] (`p.x + k*seamPeriodPx`), die innerhalb des
     *  sichtbaren Bildrechtecks liegen -- bei `seamPeriodPx=null` nur [p] selbst, falls es im Rechteck
     *  liegt. [seamPeriodPx] ist seit dem Nahtfix 2026-08-27 bewusst NICHT die (leicht abweichende)
     *  gefittete Projektionsperiode (`PanoramaProjection.horizontalPeriodPx`), sondern `imageWidth`
     *  selbst -- exakt dieselbe Wahl wie in [emitSeamClippedPiece]/[OverlayGeometry.splitPolylineAtSeam],
     *  damit das Vollständigkeits-Audit unten dieselbe Periodizität annimmt wie die tatsächlich
     *  gezeichnete Geometrie (sonst würde es fälschlich eine ~0,35%-Diskrepanz als Lücke melden, s.
     *  [emitSeamClippedPiece]-KDoc). Weiterhin gemeinsam genutzt vom Vollständigkeits-Audit unten. */
    private fun periodicVisibleCandidates(p: Offset, width: Int, height: Int, seamPeriodPx: Double?): List<Offset> {
        if (p.y < 0f || p.y > height) return emptyList()
        if (seamPeriodPx == null || !seamPeriodPx.isFinite() || seamPeriodPx <= 0.0) {
            return if (p.x in 0f..width.toFloat()) listOf(p) else emptyList()
        }
        val kMin = ceil((0.0 - p.x) / seamPeriodPx).toInt().coerceAtLeast(-PERIODIC_WRAP_K_LIMIT)
        val kMax = floor((width - p.x) / seamPeriodPx).toInt().coerceAtMost(PERIODIC_WRAP_K_LIMIT)
        if (kMin > kMax) return emptyList()
        return (kMin..kMax).map { k -> Offset((p.x + k * seamPeriodPx).toFloat(), p.y) }
    }

    /**
     * Vollständigkeits-Audit (Nutzer-Vorgabe 2026-08-27, Abschnitt 6): unabhängiger Check -- NICHT
     * "sieht die Linie glatt aus" (das allein hatte die Randlücke nicht aufgedeckt, s. Kommentar bei
     * [compute]), sondern "ist jede laut Einzelpunkt-Abtastung sichtbare Himmelskoordinate (inkl.
     * periodischer Kopien, s. [periodicVisibleCandidates]) tatsächlich nah an einem Punkt der
     * TATSÄCHLICH erzeugten Geometrie DERSELBEN Linie". [testPoints] sind bereits auf sichtbare
     * (periodisch entfaltete) Kandidaten reduziert; [ownLines] ist NUR die für diese eine Dec-/RA-Linie
     * gerade erzeugte Geometrie, nicht das ganze Netz. Liefert die größte gefundene Lücke in Pixeln
     * (0 = perfekt deckungsgleich); `Float.MAX_VALUE`, wenn für KEINEN Testpunkt überhaupt Geometrie
     * existierte (sollte nach dem Fix praktisch nie vorkommen -- jeder Testpunkt stammt aus einem
     * bereits erfolgreich projizierten Rohpunkt).
     */
    private fun auditMaxGapPx(testPoints: List<Offset>, ownLines: List<GraticuleLine>): Float {
        var worst = 0f
        for (tp in testPoints) {
            var best = Float.MAX_VALUE
            for (line in ownLines) {
                val pts = line.points
                for (i in 1 until pts.size) {
                    val d = distPointToSegment(tp, pts[i - 1], pts[i])
                    if (d < best) best = d
                }
            }
            if (best > worst) worst = best
        }
        return worst
    }

    /** Kürzester Abstand von [p] zum Liniensegment [a]-[b]. */
    private fun distPointToSegment(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        val cx = a.x + t * dx
        val cy = a.y + t * dy
        return hypot((p.x - cx).toDouble(), (p.y - cy).toDouble()).toFloat()
    }

    /** Formatiert eine [auditMaxGapPx]-Lücke für die Diagnose -- `Float.MAX_VALUE` (kein Testpunkt hatte
     *  überhaupt Geometrie in der Nähe) als klar erkennbares "NO_GEOMETRY" statt einer bedeutungslos
     *  riesigen Zahl. */
    private fun formatAuditGap(gapPx: Float): String =
        if (gapPx >= 1_000_000f) "NO_GEOMETRY" else formatFloat1(gapPx)

    /**
     * Schneidet eine Polylinie EXAKT auf das Bildrechteck [0,width]x[0,height] zu (Liang-Barsky pro
     * Segment) -- ersetzt die frühere "irgendein Punkt nah genug am Bild"-Toleranzzone. Eine Linie
     * endet dadurch immer exakt an der tatsächlichen Bildkante (keine Lücke davor, kein Überschuss
     * danach), und kann in mehrere Teilstücke zerfallen, wenn sie das Rechteck mehrfach verlässt/betritt.
     */
    private fun clipPolylineToRect(points: List<Offset>, width: Int, height: Int): List<List<Offset>> {
        if (points.size < 2) return emptyList()
        val w = width.toFloat()
        val h = height.toFloat()
        val result = mutableListOf<MutableList<Offset>>()
        var current: MutableList<Offset>? = null
        for (i in 0 until points.size - 1) {
            val clipped = clipSegmentToRect(points[i], points[i + 1], w, h)
            if (clipped == null) {
                current = null
                continue
            }
            val (c0, c1) = clipped
            val cur = current
            if (cur == null) {
                current = mutableListOf(c0, c1).also { result += it }
            } else {
                cur += c1
            }
        }
        return result
    }

    /** Liang-Barsky-Segmentschnitt gegen [0,w]x[0,h]; `null`, wenn das Segment das Rechteck nie berührt. */
    private fun clipSegmentToRect(p0: Offset, p1: Offset, w: Float, h: Float): Pair<Offset, Offset>? {
        var t0 = 0.0
        var t1 = 1.0
        val dx = (p1.x - p0.x).toDouble()
        val dy = (p1.y - p0.y).toDouble()
        val checks = arrayOf(
            -dx to (p0.x - 0f).toDouble(),
            dx to (w - p0.x).toDouble(),
            -dy to (p0.y - 0f).toDouble(),
            dy to (h - p0.y).toDouble(),
        )
        for ((p, q) in checks) {
            if (p == 0.0) {
                if (q < 0.0) return null
            } else {
                val r = q / p
                if (p < 0.0) {
                    if (r > t1) return null
                    if (r > t0) t0 = r
                } else {
                    if (r < t0) return null
                    if (r < t1) t1 = r
                }
            }
        }
        return Offset((p0.x + t0 * dx).toFloat(), (p0.y + t0 * dy).toFloat()) to
            Offset((p0.x + t1 * dx).toFloat(), (p0.y + t1 * dy).toFloat())
    }

    /**
     * Label-Anker: der Punkt der TATSÄCHLICH gezeichneten Linie ([ownLines], nur diese eine Dec-/RA-
     * Linie), der seinem Rahmen-Rand am nächsten ist. RA/Meridiane -> obere ODER untere Kante
     * (horizontalEdges=true), Dec/Parallelen -> linke ODER rechte Kante. So sitzen die Stunden
     * konsistent oben/unten und die Grad links/rechts am Rahmen (Sternkarten-Optik), statt je nach
     * Linienverlauf am „höchsten/linkesten" Punkt zu streuen. Wählt seit dem Nahtfix 2026-08-27
     * bewusst aus der FERTIGEN, bereits nahtbewusst zugeschnittenen Geometrie ([emitSeamClippedPiece])
     * statt (wie zuvor) unabhängig aus periodisch entfalteten Roh-Kandidaten -- garantiert, dass ein
     * Label immer exakt auf einem tatsächlich gezeichneten Pixel sitzt, keine zweite, separate
     * Sichtbarkeits-Annahme mehr nötig.
     */
    private fun pickLabel(ownLines: List<GraticuleLine>, width: Int, height: Int, horizontalEdges: Boolean): Offset? {
        var best: Offset? = null
        var bestDist = Float.MAX_VALUE
        for (line in ownLines) {
            for (p in line.points) {
                val dist = if (horizontalEdges) minOf(p.y, height - p.y) else minOf(p.x, width - p.x)
                if (dist < bestDist) {
                    bestDist = dist
                    best = p
                }
            }
        }
        return best
    }

    /**
     * Hält den Label-Anker so weit im Bild, dass der links-ausgerichtete Text vollständig sichtbar
     * bleibt (auch wenn die Projektion/Linie teilweise außerhalb liegt). Rechts mehr Rand, weil der
     * Text nach rechts wächst; oben/unten Platz für die Texthöhe. Bildkoordinaten -> gilt für Editor
     * UND Export gleichermaßen.
     */
    private fun clampLabelPos(p: Offset, width: Int, height: Int): Offset {
        val minDim = min(width, height)
        val leftPad = minDim * 0.012f
        val rightPad = minDim * 0.075f
        val vPad = minDim * 0.03f
        return Offset(
            p.x.coerceIn(leftPad, width - rightPad),
            p.y.coerceIn(vPad, height - vPad),
        )
    }

    /** Pixel pro Grad in der Nähe von [sky] (2-Punkt-Sampling, wie AstapOverlayMapper). [p0] als
     *  Referenz an den zweiten Punkt durchgereicht (Azimut-Ast-Kontinuität, s. Kommentar bei `project`
     *  in [compute]) -- ohne das könnte der 0,2°-Schritt bei einem Bezugspunkt nahe der Projektions-
     *  Nahtstelle fälschlich einen riesigen Sprung statt einer kleinen lokalen Distanz messen. */
    private fun pxPerDegAt(wcs: WcsSolutionLike, sky: SkyPoint, imageHeight: Int): Float {
        val p0 = wcs.skyToImage(sky, imageHeight) ?: return 0f
        val step = if (sky.decDegrees < 89f) 0.2f else -0.2f
        val p1 = wcs.skyToImage(SkyPoint(sky.raDegrees, sky.decDegrees + step), imageHeight, p0) ?: return 0f
        return hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat() / 0.2f
    }

    /** RA/Dec in der Bildmitte (für Schrittwahl + sichtbaren Bereich). null = nicht bestimmbar. */
    private fun centerSky(wcs: WcsSolutionLike, width: Int, height: Int): SkyPoint? = when (wcs) {
        is WcsSolution -> wcs.imageToSky(width / 2.0, height / 2.0, height)
        is PanoramaWcsSolution -> {
            val dir = wcs.projection.pixelToDirection(width / 2.0, height / 2.0)
            if (dir == null) {
                null
            } else {
                val (ra, dec) = vectorToRaDec(wcs.rotEquToPano.transpose() * dir)
                SkyPoint(ra.toFloat(), dec.toFloat())
            }
        }
        is MosaicWcsSolution -> {
            val tile = wcs.tiles.firstOrNull()
            if (tile == null) null else centerSky(tile.wcs, tile.tileWidth, tile.tileHeight)
        }
    }

    private fun sampleCount(spanDeg: Double, sampleDeg: Double): Int =
        (abs(spanDeg) / sampleDeg).roundToInt().coerceIn(2, 1000)

    private fun nearest(ladder: FloatArray, value: Float): Float =
        ladder.minByOrNull { abs(it - value) } ?: ladder.last()

    private fun formatDec(decDeg: Double): String {
        val rounded = (decDeg * 10.0).roundToInt() / 10.0
        val sign = if (rounded < 0) "-" else "+"
        val abs = abs(rounded)
        val body = if (abs % 1.0 == 0.0) abs.toInt().toString() else trimDecimal(abs)
        return "$sign$body°"
    }

    private fun formatRaHours(raDeg: Double): String {
        var totalMin = (raDeg / 15.0 * 60.0).roundToInt()
        totalMin = ((totalMin % 1440) + 1440) % 1440 // 0..1439 Minuten = 24h
        val h = totalMin / 60
        val m = totalMin % 60
        return if (m == 0) "${h}h" else "${h}h${m}m"
    }

    private fun trimDecimal(v: Double): String {
        val s = (Math.round(v * 10.0) / 10.0).toString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    /** Nur für Diagnose-Strings (parallelNullStats) -- feste US-Locale, damit "." statt "," (unabhängig
     *  von der Geräte-Locale) und die Textform mit den übrigen Diagnose-Feldern konsistent bleibt. */
    private fun formatFloat1(v: Float): String = String.format(Locale.US, "%.1f", v)
}
