package com.codex.starmapper.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.codex.starmapper.processing.CorrectedProjection
import com.codex.starmapper.processing.CylindricalProjection
import com.codex.starmapper.processing.RichCorrMesh
import com.codex.starmapper.processing.FisheyeProjection
import com.codex.starmapper.processing.Mat3
import com.codex.starmapper.processing.MosaicWcsSolution
import com.codex.starmapper.processing.PanoProjectionKind
import com.codex.starmapper.processing.PanoramaProjection
import com.codex.starmapper.processing.PanoramaWcsSolution
import com.codex.starmapper.processing.RectilinearProjection
import com.codex.starmapper.processing.RefractedPanoramaWcsSolution
import com.codex.starmapper.processing.StereographicProjection
import com.codex.starmapper.processing.TileWcs
import com.codex.starmapper.processing.Vec3
import com.codex.starmapper.processing.WcsSolution
import com.codex.starmapper.processing.WcsSolutionLike
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export/Import einer Astrometrie-Lösung (WCS + Projektionsparameter + Kacheln) als eine einzelne
 * JSON-Datei -- Nutzerwunsch: "damit ich das nicht noch mal neu lösen muss", wenn dasselbe Foto
 * später erneut geöffnet wird. Ausdrückliche Vorgabe (wörtlich genommen, s. feedback_vorgaben_woertlich):
 * "nichts verändert wird, aber die nötigen Daten ... abgegriffen werden können" -- diese Datei liest
 * ausschließlich bereits öffentliche Felder der bestehenden Solve-/Projektionsklassen und ändert an
 * keiner von ihnen etwas. Reine Snapshot-Serialisierung, kein neues Serialisierungs-Framework nötig
 * (org.json ist bereits Projekt-Standard fürs Lesen von Assets/API-Antworten, hier erstmals zum
 * Schreiben genutzt).
 *
 * ARCHITEKTUR-TRENNUNG (Nutzer-Vorgabe 2026-08-30, harte Anforderung, ersetzt die frühere "1:1-
 * Speicherstand"-Entscheidung): eine Solution beschreibt AUSSCHLIESSLICH WO der Himmel im Bild liegt
 * (WCS/Projektion/Rotation/Kacheln) -- NIEMALS WIE der aktuelle Build ihn annotiert/rendert. Automatisch
 * erzeugte Overlays (Sterne/DSOs/Sternbilder/Namen/Leader-Lines/Collision-Ergebnisse/Gradnetz) sind
 * deshalb bewusst NICHT Teil dieser Datei -- der Aufrufer (StarMapperApp.kt) regeneriert sie nach dem
 * Laden vollständig über die AKTUELLEN `syncConstellationLayer`/`syncDeepSkyLayer`/`syncStarLayer`-
 * Funktionen (dieselbe Pipeline, die auch nach einem frischen Solve läuft) -- niemals über einen alten,
 * eingefrorenen Overlay-Snapshot aus einem früheren Build. Ältere Dateien (Format-Version 1), die noch
 * ein Overlay-Array aus der VORHERIGEN Entscheidung tragen, werden beim Einlesen erkannt, aber ihr
 * Overlay-Inhalt wird bewusst NICHT wiederhergestellt (s. [ParsedSolution.legacyOverlayPayloadIgnored]).
 * Manuell platzierte/bearbeitete Overlays (eigene Texte/Formen/verschobene Beschriftungen) sind damit
 * AUCH nicht Teil dieser Datei -- eine künftige, separate "Projektdatei" (noch nicht umgesetzt) wäre
 * dafür der richtige Ort, nicht diese Solution-Datei.
 *
 * internal (nicht public): trägt `SolveTile` (selbst `internal`, s. StarMapperApp.kt) in mehreren
 * Signaturen (`ParsedSolution`, `encode`/`decode`) -- Kotlin verbietet, dass eine `public`-Deklaration
 * einen `internal`-Typ in ihrer Signatur zeigt. Wird ohnehin nur innerhalb dieses Moduls verwendet.
 */
internal object SolutionExport {

    // 3 (2026-09-04): Mesh/CorrectedProjection wird echt serialisiert (Baseline + Kontrollpunkte +
    // Gruppen + Gewichte) statt pauschal blockiert. Aeltere Dateien (Version 1/2) bleiben lesbar --
    // sie enthalten schlicht keinen "Corrected"-Projektionstyp.
    const val FORMAT_VERSION = 3

    /**
     * Kann [this] verlustfrei exportiert werden?
     *
     * Seit Formatversion 3 (Nutzer-Vorgabe 2026-09-04) gilt das AUCH für Mesh: [CorrectedProjection]
     * wird echt serialisiert (Baseline + Kontrollpunkte + Gruppen + Gewichte, s. `projectionToJson`),
     * nicht mehr pauschal blockiert. Grund: im Tiny-Sky-/360°-Fall ist Mesh regelmäßig der GENAUERE
     * aktive Zustand -- ihn nicht speichern zu können war ein echtes Produktproblem, kein Randfall.
     *
     * Weiterhin NICHT exportierbar (bewusst, klar getrennt): [RichCorrMesh.RichCorrMeshProjection]
     * (eigene RBF-Klasse mit gelöstem Gleichungssystem -- eine eigene Serialisierung wäre ein eigenes
     * Vorhaben) und [ResidualCorrection] (weiterhin vollständig gekapselt). In beiden Fällen liefert
     * [exportBlockReason] den konkreten Grund für die Diagnose/UI statt eines stummen `false`.
     */
    fun WcsSolutionLike.isExportable(): Boolean = when (this) {
        is WcsSolution -> true
        // Reihenfolge wichtig: RefractedPanoramaWcsSolution ist eine Subklasse von PanoramaWcsSolution.
        is RefractedPanoramaWcsSolution -> residual == null && projection.isRigid()
        is PanoramaWcsSolution -> projection.isRigid()
        is MosaicWcsSolution -> fallback == null && tiles.all { it.wcs.isExportable() }
        else -> false
    }

    // Mesh (CorrectedProjection) ist seit Formatversion 3 serialisierbar -- es zählt deshalb als
    // exportierbar, sofern seine BASELINE es ist (rekursiv: eine Baseline darf selbst kein
    // RichCorrMesh sein). RichCorrMeshProjection bleibt ausgeschlossen, s. isExportable-KDoc.
    private fun PanoramaProjection.isRigid(): Boolean = when (this) {
        is RichCorrMesh.RichCorrMeshProjection -> false
        is CorrectedProjection -> exportBaseline.isRigid()
        else -> true
    }

    /** Konkreter Grund, warum [this] NICHT exportierbar ist -- `null`, wenn exportierbar. Für
     *  Diagnose (`solution_export_blocked reason=...`) und den UI-Hinweis. */
    fun WcsSolutionLike.exportBlockReason(): String? = when {
        isExportable() -> null
        this is RefractedPanoramaWcsSolution && residual != null -> "residual_correction_not_serializable"
        this is PanoramaWcsSolution && projection.containsRichCorrMesh() -> "detail_mesh_not_serializable"
        this is MosaicWcsSolution && fallback != null -> "mosaic_fallback_not_serializable"
        this is MosaicWcsSolution -> "mosaic_tile_not_serializable"
        else -> "unsupported_solution_type"
    }

    private fun PanoramaProjection.containsRichCorrMesh(): Boolean = when (this) {
        is RichCorrMesh.RichCorrMeshProjection -> true
        is CorrectedProjection -> exportBaseline.containsRichCorrMesh()
        else -> false
    }

    // ---------------------------------------------------------------------------------------------
    // Kleine Abfragen fuer Diagnose/UI (Nutzer-Vorgabe 2026-09-04) -- reine Lesehilfen, kein State.
    // ---------------------------------------------------------------------------------------------

    /** Enthaelt die Loesung (bzw. ihre Baseline-Kette) eine Mesh-Korrektur? */
    fun WcsSolutionLike?.containsCorrectedProjection(): Boolean = when (this) {
        null -> false
        is PanoramaWcsSolution -> projection.hasCorrected()
        is MosaicWcsSolution -> fallback.containsCorrectedProjection() || tiles.any { it.wcs.containsCorrectedProjection() }
        else -> false
    }

    /** Typ der BASELINE unter einer Mesh-Korrektur (z.B. "Equirectangular"), sonst `null`. */
    fun WcsSolutionLike?.meshBaselineKindOrNull(): String? {
        val proj = (this as? PanoramaWcsSolution)?.projection ?: return null
        var cur: PanoramaProjection = proj
        var found = false
        while (cur is CorrectedProjection) {
            found = true
            cur = cur.exportBaseline
        }
        if (!found) return null
        return (cur as? CylindricalProjection)?.kind?.name ?: cur::class.simpleName
    }

    /** Ist die Loesung horizontal periodisch (360°-nahtfaehig)? */
    fun WcsSolutionLike?.hasHorizontalPeriod(): Boolean =
        (this as? PanoramaWcsSolution)?.projection?.horizontalPeriodPx() != null

    private fun PanoramaProjection.hasCorrected(): Boolean = when (this) {
        is CorrectedProjection -> true
        else -> false
    }

    // ---------------------------------------------------------------------------------------------
    // Ergebnis des Einlesens -- reiner Datenhalter, den der Aufrufer (StarMapperApp.kt) in seine
    // eigenen State-Variablen einsetzt (lastSolvedWcs/originalSolvedWcs/singleSolveWcs/solveTiles).
    // Kein Zugriff auf App-State von hier aus -- diese Datei kennt keine Compose-States. KEIN
    // Overlay-Feld mehr (s. Klassenkommentar "Architektur-Trennung") -- der Aufrufer regeneriert
    // automatische Annotationen selbst über die aktuelle Sync-Pipeline.
    // ---------------------------------------------------------------------------------------------
    data class ParsedSolution(
        val imageWidth: Int,
        val imageHeight: Int,
        val lastSolvedWcs: WcsSolutionLike?,
        val originalSolvedWcs: WcsSolutionLike?,
        val singleSolveWcs: WcsSolutionLike?,
        val tiles: List<SolveTile>,
        // true, wenn die Datei noch ein (aus der VORHERIGEN Architektur-Entscheidung stammendes,
        // jetzt bewusst ignoriertes) Overlay-Array trägt -- rein diagnostisch, s. Klassenkommentar.
        val legacyOverlayPayloadIgnored: Boolean,
    )

    fun encode(
        imageWidth: Int,
        imageHeight: Int,
        lastSolvedWcs: WcsSolutionLike?,
        originalSolvedWcs: WcsSolutionLike?,
        singleSolveWcs: WcsSolutionLike?,
        tiles: List<SolveTile>,
    ): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("imageWidth", imageWidth)
        root.put("imageHeight", imageHeight)
        lastSolvedWcs?.takeIf { it.isExportable() }?.let { root.put("lastSolvedWcs", wcsToJson(it)) }
        originalSolvedWcs?.takeIf { it.isExportable() }?.let { root.put("originalSolvedWcs", wcsToJson(it)) }
        singleSolveWcs?.takeIf { it.isExportable() }?.let { root.put("singleSolveWcs", wcsToJson(it)) }
        root.put("tiles", JSONArray().apply { tiles.forEach { put(tileToJson(it)) } })
        return root.toString()
    }

    fun decode(text: String): ParsedSolution {
        val root = JSONObject(text)
        // Legacy-Erkennung (Format-Version 1 konnte ein "overlays"-Array enthalten) -- bewusst NUR die
        // Länge geprüft, der Inhalt wird nie geparst/wiederhergestellt (s. Klassenkommentar).
        val legacyOverlayPayloadIgnored = (root.optJSONArray("overlays")?.length() ?: 0) > 0
        val tilesJson = root.optJSONArray("tiles") ?: JSONArray()
        val tiles = ArrayList<SolveTile>(tilesJson.length())
        for (i in 0 until tilesJson.length()) tiles.add(tileFromJson(tilesJson.getJSONObject(i)))
        return ParsedSolution(
            imageWidth = root.optInt("imageWidth", 0),
            imageHeight = root.optInt("imageHeight", 0),
            lastSolvedWcs = root.optJSONObject("lastSolvedWcs")?.let { wcsFromJson(it) },
            originalSolvedWcs = root.optJSONObject("originalSolvedWcs")?.let { wcsFromJson(it) },
            singleSolveWcs = root.optJSONObject("singleSolveWcs")?.let { wcsFromJson(it) },
            tiles = tiles,
            legacyOverlayPayloadIgnored = legacyOverlayPayloadIgnored,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Vec3 / Mat3 / Offset / Size -- flache Zahlen-Arrays
    // ---------------------------------------------------------------------------------------------

    private fun vec3ToJson(v: Vec3) = JSONArray().put(v.x).put(v.y).put(v.z)
    private fun vec3FromJson(a: JSONArray) = Vec3(a.getDouble(0), a.getDouble(1), a.getDouble(2))

    private fun mat3ToJson(m: Mat3) = JSONArray().apply {
        put(m.m00); put(m.m01); put(m.m02)
        put(m.m10); put(m.m11); put(m.m12)
        put(m.m20); put(m.m21); put(m.m22)
    }

    private fun mat3FromJson(a: JSONArray) = Mat3(
        m00 = a.getDouble(0), m01 = a.getDouble(1), m02 = a.getDouble(2),
        m10 = a.getDouble(3), m11 = a.getDouble(4), m12 = a.getDouble(5),
        m20 = a.getDouble(6), m21 = a.getDouble(7), m22 = a.getDouble(8),
    )

    private fun offsetToJson(o: Offset) = JSONArray().put(o.x.toDouble()).put(o.y.toDouble())
    private fun offsetFromJson(a: JSONArray) = Offset(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())

    private fun sizeToJson(s: Size) = JSONArray().put(s.width.toDouble()).put(s.height.toDouble())
    private fun sizeFromJson(a: JSONArray) = Size(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())

    private fun sipMapToJson(map: Map<Pair<Int, Int>, Double>) = JSONArray().apply {
        map.forEach { (key, value) -> put(JSONArray().put(key.first).put(key.second).put(value)) }
    }

    private fun sipMapFromJson(array: JSONArray?): Map<Pair<Int, Int>, Double> {
        if (array == null) return emptyMap()
        val result = HashMap<Pair<Int, Int>, Double>(array.length())
        for (i in 0 until array.length()) {
            val entry = array.getJSONArray(i)
            result[entry.getInt(0) to entry.getInt(1)] = entry.getDouble(2)
        }
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // PanoramaProjection (nur die 4 starren Typen -- Mesh/CorrectedProjection ist per isExportable()
    // bereits vorher ausgeschlossen und kommt hier nie an)
    // ---------------------------------------------------------------------------------------------

    private fun projectionToJson(p: PanoramaProjection): JSONObject = when (p) {
        is CylindricalProjection -> JSONObject()
            .put("type", "Cylindrical")
            .put("cx", p.cx).put("cy", p.cy).put("fx", p.fx).put("fy", p.fy)
            .put("kind", p.kind.name)
        is FisheyeProjection -> JSONObject()
            .put("type", "Fisheye")
            .put("cx", p.cx).put("cy", p.cy).put("f", p.f)
            .put("k1", p.k1).put("k2", p.k2).put("k3", p.k3).put("flipY", p.flipY)
        is StereographicProjection -> JSONObject()
            .put("type", "Stereographic")
            .put("cx", p.cx).put("cy", p.cy).put("f", p.f).put("flipY", p.flipY)
        is RectilinearProjection -> JSONObject()
            .put("type", "Rectilinear")
            .put("cx", p.cx).put("cy", p.cy).put("f", p.f).put("flipY", p.flipY)
        // Mesh/SparseAnchorMesh: Baseline + ROHE Konstruktor-Eingaben (s. CorrectedProjection.export*).
        // Bewusst NICHT die abgeleiteten Cluster-Knoten: aus diesen Eingaben baut derselbe Konstruktor
        // beim Import bitgleich dieselbe Projektion, inkl. Declustering und Trag-Radius.
        is CorrectedProjection -> JSONObject()
            .put("type", "Corrected")
            .put("baseline", projectionToJson(p.exportBaseline))
            .put(
                "controlPoints",
                JSONArray().apply {
                    for ((px, dir) in p.exportControlPoints) {
                        put(
                            JSONArray().put(px.x.toDouble()).put(px.y.toDouble())
                                .put(dir.x).put(dir.y).put(dir.z),
                        )
                    }
                },
            )
            .put("groupSizes", JSONArray().apply { p.exportGroupSizes.forEach { put(it) } })
            .put("groupWeights", JSONArray().apply { p.exportGroupWeights.forEach { put(it) } })
        else -> error("Nicht-exportierbare Projektion: ${p::class.simpleName}")
    }

    private fun projectionFromJson(json: JSONObject): PanoramaProjection = when (json.getString("type")) {
        "Cylindrical" -> CylindricalProjection(
            cx = json.getDouble("cx"), cy = json.getDouble("cy"),
            fx = json.getDouble("fx"), fy = json.getDouble("fy"),
            kind = PanoProjectionKind.valueOf(json.getString("kind")),
        )
        "Fisheye" -> FisheyeProjection(
            cx = json.getDouble("cx"), cy = json.getDouble("cy"), f = json.getDouble("f"),
            k1 = json.optDouble("k1", 0.0), k2 = json.optDouble("k2", 0.0), k3 = json.optDouble("k3", 0.0),
            flipY = json.optBoolean("flipY", false),
        )
        "Stereographic" -> StereographicProjection(
            cx = json.getDouble("cx"), cy = json.getDouble("cy"), f = json.getDouble("f"),
            flipY = json.optBoolean("flipY", false),
        )
        "Rectilinear" -> RectilinearProjection(
            cx = json.getDouble("cx"), cy = json.getDouble("cy"), f = json.getDouble("f"),
            flipY = json.optBoolean("flipY", false),
        )
        "Corrected" -> {
            val cp = json.getJSONArray("controlPoints")
            val points = ArrayList<Pair<Offset, Vec3>>(cp.length())
            for (i in 0 until cp.length()) {
                val e = cp.getJSONArray(i)
                points += Offset(e.getDouble(0).toFloat(), e.getDouble(1).toFloat()) to
                    Vec3(e.getDouble(2), e.getDouble(3), e.getDouble(4))
            }
            val gs = json.getJSONArray("groupSizes")
            val sizes = ArrayList<Int>(gs.length()).apply { for (i in 0 until gs.length()) add(gs.getInt(i)) }
            val gw = json.getJSONArray("groupWeights")
            val weights = ArrayList<Double>(gw.length()).apply { for (i in 0 until gw.length()) add(gw.getDouble(i)) }
            CorrectedProjection(
                baseline = projectionFromJson(json.getJSONObject("baseline")),
                controlPoints = points,
                groupSizes = sizes,
                groupWeights = weights,
            )
        }
        else -> error("Unbekannter Projektionstyp: ${json.getString("type")}")
    }

    // ---------------------------------------------------------------------------------------------
    // WcsSolutionLike -- getaggte Union
    // ---------------------------------------------------------------------------------------------

    private fun wcsToJson(wcs: WcsSolutionLike): JSONObject = when (wcs) {
        is WcsSolution -> JSONObject()
            .put("type", "Wcs")
            .put("crPix1", wcs.crPix1).put("crPix2", wcs.crPix2)
            .put("crVal1Degrees", wcs.crVal1Degrees).put("crVal2Degrees", wcs.crVal2Degrees)
            .put("cd11", wcs.cd11).put("cd12", wcs.cd12).put("cd21", wcs.cd21).put("cd22", wcs.cd22)
            .put("flipY", wcs.flipY)
            .put("inverseSipX", sipMapToJson(wcs.inverseSipX))
            .put("inverseSipY", sipMapToJson(wcs.inverseSipY))
            .put("forwardSipX", sipMapToJson(wcs.forwardSipX))
            .put("forwardSipY", sipMapToJson(wcs.forwardSipY))
        is RefractedPanoramaWcsSolution -> JSONObject()
            .put("type", "Refracted")
            .put("projection", projectionToJson(wcs.projection))
            .put("rotEquToPano", mat3ToJson(wcs.rotEquToPano))
            .put("zenithEq", vec3ToJson(wcs.zenithEq))
        is PanoramaWcsSolution -> JSONObject()
            .put("type", "Panorama")
            .put("projection", projectionToJson(wcs.projection))
            .put("rotEquToPano", mat3ToJson(wcs.rotEquToPano))
        is MosaicWcsSolution -> JSONObject()
            .put("type", "Mosaic")
            .put(
                "tiles",
                JSONArray().apply {
                    wcs.tiles.forEach { t ->
                        put(
                            JSONObject()
                                .put("wcs", wcsToJson(t.wcs))
                                .put("tileOffsetX", t.tileOffsetX).put("tileOffsetY", t.tileOffsetY)
                                .put("tileWidth", t.tileWidth).put("tileHeight", t.tileHeight),
                        )
                    }
                },
            )
        else -> error("Nicht-exportierbare WCS-Lösung: ${wcs::class.simpleName}")
    }

    private fun wcsFromJson(json: JSONObject): WcsSolutionLike = when (json.getString("type")) {
        "Wcs" -> WcsSolution(
            crPix1 = json.getDouble("crPix1"), crPix2 = json.getDouble("crPix2"),
            crVal1Degrees = json.getDouble("crVal1Degrees"), crVal2Degrees = json.getDouble("crVal2Degrees"),
            cd11 = json.getDouble("cd11"), cd12 = json.getDouble("cd12"),
            cd21 = json.getDouble("cd21"), cd22 = json.getDouble("cd22"),
            inverseSipX = sipMapFromJson(json.optJSONArray("inverseSipX")),
            inverseSipY = sipMapFromJson(json.optJSONArray("inverseSipY")),
            flipY = json.optBoolean("flipY", true),
            forwardSipX = sipMapFromJson(json.optJSONArray("forwardSipX")),
            forwardSipY = sipMapFromJson(json.optJSONArray("forwardSipY")),
        )
        "Panorama" -> PanoramaWcsSolution(
            projection = projectionFromJson(json.getJSONObject("projection")),
            rotEquToPano = mat3FromJson(json.getJSONArray("rotEquToPano")),
        )
        "Refracted" -> RefractedPanoramaWcsSolution(
            base = PanoramaWcsSolution(
                projection = projectionFromJson(json.getJSONObject("projection")),
                rotEquToPano = mat3FromJson(json.getJSONArray("rotEquToPano")),
            ),
            zenithEq = vec3FromJson(json.getJSONArray("zenithEq")),
            residual = null,
        )
        "Mosaic" -> {
            val tilesJson = json.getJSONArray("tiles")
            val tileList = ArrayList<TileWcs>(tilesJson.length())
            for (i in 0 until tilesJson.length()) {
                val t = tilesJson.getJSONObject(i)
                tileList.add(
                    TileWcs(
                        wcs = wcsFromJson(t.getJSONObject("wcs")),
                        tileOffsetX = t.getInt("tileOffsetX"), tileOffsetY = t.getInt("tileOffsetY"),
                        tileWidth = t.getInt("tileWidth"), tileHeight = t.getInt("tileHeight"),
                    ),
                )
            }
            MosaicWcsSolution(tiles = tileList, fallback = null)
        }
        else -> error("Unbekannter WCS-Typ: ${json.getString("type")}")
    }

    // ---------------------------------------------------------------------------------------------
    // SolveTile
    // ---------------------------------------------------------------------------------------------

    private fun tileToJson(tile: SolveTile): JSONObject {
        val json = JSONObject()
            .put("id", tile.id)
            .put("center", offsetToJson(tile.center))
            .put("size", sizeToJson(tile.size))
            .put("rotationDegrees", tile.rotationDegrees.toDouble())
            .put("status", tile.status.name)
            .put("sourceSpace", tile.sourceSpace.name)
            .put("dewarpRequested", tile.dewarpRequested)
            .put("dewarp", tile.dewarp)
        tile.wcs?.let { json.put("wcs", wcsToJson(it)) }
        tile.tinySkyLocalWcs?.let { json.put("tinySkyLocalWcs", wcsToJson(it)) }
        tile.manualHintStarName?.let { json.put("manualHintStarName", it) }
        tile.solvedWithHint?.let { json.put("solvedWithHint", it) }
        tile.solveDurationMs?.let { json.put("solveDurationMs", it) }
        tile.anchors?.let { anchors ->
            json.put(
                "anchors",
                JSONArray().apply {
                    anchors.forEach { (px, v) ->
                        put(JSONArray().put(px.x.toDouble()).put(px.y.toDouble()).put(v.x).put(v.y).put(v.z))
                    }
                },
            )
        }
        return json
    }

    private fun tileFromJson(json: JSONObject): SolveTile {
        val anchorsJson = json.optJSONArray("anchors")
        val anchors = anchorsJson?.let { array ->
            (0 until array.length()).map { i ->
                val e = array.getJSONArray(i)
                Offset(e.getDouble(0).toFloat(), e.getDouble(1).toFloat()) to
                    Vec3(e.getDouble(2), e.getDouble(3), e.getDouble(4))
            }
        }
        return SolveTile(
            id = json.getLong("id"),
            center = offsetFromJson(json.getJSONArray("center")),
            size = sizeFromJson(json.getJSONArray("size")),
            rotationDegrees = json.optDouble("rotationDegrees", 0.0).toFloat(),
            status = SolveTileStatus.valueOf(json.optString("status", "Pending")),
            wcs = json.optJSONObject("wcs")?.let { wcsFromJson(it) as? WcsSolution },
            sourceSpace = TileSourceSpace.valueOf(json.optString("sourceSpace", "Native")),
            dewarpRequested = json.optBoolean("dewarpRequested", false),
            dewarp = json.optBoolean("dewarp", false),
            anchors = anchors,
            tinySkyLocalWcs = json.optJSONObject("tinySkyLocalWcs")?.let { wcsFromJson(it) as? WcsSolution },
            manualHintStarName = json.optString("manualHintStarName", null),
            solvedWithHint = if (json.has("solvedWithHint")) json.getBoolean("solvedWithHint") else null,
            solveDurationMs = if (json.has("solveDurationMs")) json.getLong("solveDurationMs") else null,
        )
    }

}
