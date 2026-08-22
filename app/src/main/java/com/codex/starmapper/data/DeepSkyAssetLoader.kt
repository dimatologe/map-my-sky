package com.codex.starmapper.data

import android.content.Context
import com.codex.starmapper.domain.DeepSkyObject
import com.codex.starmapper.domain.SkyPoint
import org.json.JSONObject

object DeepSkyAssetLoader {
    // Sprachkürzel, für die dso_names.json eigene Felder führt (identisch zu starnames.json/AppLocale,
    // ohne "en" -- das ist die internationale Standard-Form in "name", kein eigenes Feld nötig).
    private val LOCALIZED_TAGS = listOf("de", "zh", "es", "ru", "ar", "ja")

    fun load(context: Context): List<DeepSkyObject> {
        val text = context.assets.open("catalog/dsos.20.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val namesRoot = runCatching {
            JSONObject(context.assets.open("catalog/dso_names.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        val crossIdGroups = loadCrossIdDuplicateGroups(context)
        val features = root.getJSONArray("features")
        val objects = ArrayList<DeepSkyObject>(features.length())

        for (featureIndex in 0 until features.length()) {
            val feature = features.getJSONObject(featureIndex)
            val properties = feature.getJSONObject("properties")
            val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val id = feature.opt("id")?.toString().orEmpty().ifBlank { featureIndex.toString() }
            val magnitude = properties.optString("mag", "")
                .toFloatOrNull()
                ?.takeIf { it in -30f..20f }

            val dim = properties.optString("dim", "")
            val (maj, min) = parseDim(dim)
            val desig = properties.optString("desig", id).ifBlank { id }
            val catalogDesignation = preferredCatalogDesignation(id, desig)
            // Die jeweils NICHT gewählte Bezeichnung (id ODER desig) als erster Eintrag für
            // [DeepSkyObject.alternateDesignations] -- z.B. id="NGC 224"/desig="M 31" ->
            // catalogDesignation="M 31", alternate="NGC 224" (s. Models.kt-Feldkommentar).
            val sameRowAlternate = if (id != desig) {
                (if (catalogDesignation == id) desig else id).takeIf { it.isNotBlank() }
            } else {
                null
            }
            // Populärname-Lookup: primär über die Anzeigebezeichnung (bei Messier-Objekten "M NN",
            // identisch zum Schlüssel-Schema von tools/build_dso_names.py), sonst über die rohe `id`
            // (bei Messier-Objekten oft die NGC/IC-Kreuzreferenz, s. Memory) -- deckt beide Fälle ab,
            // ohne Kollisionsrisiko: dso_names.json führt nur je EINE eindeutige Bezeichnung pro Objekt.
            val nameEntry = namesRoot?.optJSONObject(normalizeDesignation(desig))
                ?: namesRoot?.optJSONObject(normalizeDesignation(id))

            objects += DeepSkyObject(
                id = id,
                name = desig,
                type = properties.optString("type", "").lowercase(),
                point = SkyPoint(
                    raDegrees = coordinates.getDouble(0).toFloat(),
                    decDegrees = coordinates.getDouble(1).toFloat(),
                ),
                magnitude = magnitude,
                dimensions = dim,
                majorAxisArcmin = maj,
                minorAxisArcmin = min,
                properName = nameEntry?.optString("name", "")?.trim().orEmpty(),
                localizedProperNames = LOCALIZED_TAGS
                    .associateWith { nameEntry?.optString(it, "")?.trim().orEmpty() }
                    .filterValues { it.isNotBlank() },
                properNameSource = nameEntry?.optString("src", "")?.trim().orEmpty(),
                catalogDesignation = catalogDesignation,
                alternateDesignations = listOfNotNull(sameRowAlternate),
            )
        }

        return suppressDuplicatePopularNames(deduplicate(objects, crossIdGroups))
            .sortedWith(compareBy<DeepSkyObject> { it.magnitude ?: Float.POSITIVE_INFINITY }.thenBy { it.name })
    }

    /** [dso_crossid_duplicates.json] wird von tools/build_dso_crossid_duplicates.py EINMALIG aus
     *  SIMBADs eigener Objekt-ID (oidref) für den GESAMTEN Katalog erzeugt (Nutzervorschlag
     *  2026-08-19: "wir beziehen das doch sowieso von SIMBAD" -- ersetzt die vorher von Hand,
     *  Einzelfund für Einzelfund gepflegte 15-Paar-Liste; die vollständige Abfrage fand davon
     *  ausgehend 561 Gruppen, alle 15 bereits bekannten Fälle korrekt darunter, s. Verifikation).
     *  Jede innere Liste sind rohe Bezeichnungen (id/desig-Schreibweise wie in dsos.20.json), die
     *  alle zu DEMSELBEN SIMBAD-Objekt gehören. */
    private fun loadCrossIdDuplicateGroups(context: Context): List<List<String>> = runCatching {
        val text = context.assets.open("catalog/dso_crossid_duplicates.json")
            .bufferedReader().use { it.readText() }
        val array = org.json.JSONArray(text)
        (0 until array.length()).map { i ->
            val inner = array.getJSONArray(i)
            (0 until inner.length()).map { j -> inner.getString(j) }
        }
    }.getOrNull().orEmpty()

    /** „90x40" → (90, 40); „330" (rund) → (330, 330); leer/ungültig → (null, null). Bogenminuten. */
    private fun parseDim(dim: String): Pair<Float?, Float?> {
        if (dim.isBlank()) return null to null
        val parts = dim.split('x', 'X', '×')
        val maj = parts.getOrNull(0)?.trim()?.toFloatOrNull()?.takeIf { it > 0f }
        val min = parts.getOrNull(1)?.trim()?.toFloatOrNull()?.takeIf { it > 0f } ?: maj
        return maj to min
    }

    /** Katalog-Datenfehler in dsos.20.json (verifiziert): vereinzelt tragen zwei UNABHÄNGIGE Einträge
     *  denselben Anzeigenamen für dasselbe physische Objekt an fast derselben Position -- z.B.
     *  Lagunennebel M8: einmal korrekt als id="M 8"/type=bn/mag=5.0, einmal nochmal fälschlich unter
     *  id="NGC 6523" mit demselben desig "M 8" und type=oc/mag=999 (kein zweites echtes Katalog-Objekt,
     *  sondern ein Copy-Paste-Fehler in der Quelle -- sonst würde der Marker doppelt gezeichnet).
     *  Kriterium bewusst eng (gleicher normalisierter Name UND < [DEDUP_MAX_DEG]° Abstand), damit echte,
     *  nur zufällig nahe beieinanderliegende Objekte mit unterschiedlichem Namen (z.B. M8 und der
     *  eingebettete Sternhaufen NGC 6530) unangetastet bleiben. Bei einem Treffer gewinnt der Eintrag
     *  mit bekannter Helligkeit (vollständigere Daten). */
    private fun deduplicate(objects: List<DeepSkyObject>, crossIdGroups: List<List<String>>): List<DeepSkyObject> {
        val byName = HashMap<String, MutableList<DeepSkyObject>>()
        for (obj in objects) byName.getOrPut(normalizeDesignation(obj.name)) { mutableListOf() } += obj

        val toDrop = HashSet<DeepSkyObject>()
        for (group in byName.values) {
            if (group.size < 2) continue
            for (i in group.indices) {
                val a = group[i]
                if (a in toDrop) continue
                for (j in i + 1 until group.size) {
                    val b = group[j]
                    if (b in toDrop || !isSamePosition(a, b)) continue
                    toDrop += if (a.magnitude == null && b.magnitude != null) a else b
                }
            }
        }

        // Zweite Passe: echte Kreuzkatalog-Duplikate (dasselbe physische Objekt unabhängig in
        // mehreren Katalogsystemen erfasst, z.B. NGC und PGC für dieselbe Galaxie -- anders als beim
        // M8-Fall oben trägt jede Zeile hier eine ANDERE `desig`, taucht also nicht in derselben
        // byName-Gruppe auf). [crossIdGroups] kommt aus SIMBADs eigener Objekt-ID (oidref, s.
        // [loadCrossIdDuplicateGroups]) -- keine Abstands-/Namens-Heuristik: optisch sehr nahe
        // beieinanderliegende, per Populärname gleich benannte Objekte sind NICHT automatisch
        // dasselbe Objekt (z.B. Seyfert's Sextet- oder Antennae-Komponenten sind laut SIMBAD
        // verschiedene oidrefs trotz identischem Gruppennamen und < 1' Abstand -- dafür ist
        // [suppressDuplicatePopularNames] zuständig, ein bewusst anderer Mechanismus). Pro Gruppe
        // gewinnt die beste [catalogDesignation] (Rang aus [CATALOG_PREFIX_RANK]), dann bekannte
        // Helligkeit als Tie-Break -- bei Gruppen mit 3+ Bezeichnungen (z.B. dieselbe Galaxie unter
        // NGC, PGC UND MCG zugleich) bleibt so trotzdem nur EIN Objekt übrig.
        val byKey = HashMap<String, MutableList<DeepSkyObject>>()
        for (obj in objects) {
            if (obj in toDrop) continue
            byKey.getOrPut(normalizeDesignation(obj.id)) { mutableListOf() } += obj
            byKey.getOrPut(normalizeDesignation(obj.name)) { mutableListOf() } += obj
        }
        // Sammelt je behaltenem Objekt die Katalogbezeichnungen (+ deren eigene same-row-Alternativen)
        // der wegen dieser Gruppe verworfenen Mitglieder -- fuer die "auch bekannt als"-Anzeige
        // (Models.DeepSkyObject.alternateDesignations), statt sie beim Verwerfen einfach zu verlieren.
        val extraAlternates = HashMap<DeepSkyObject, MutableList<String>>()
        for (rawGroup in crossIdGroups) {
            val members = rawGroup.asSequence()
                .mapNotNull { raw -> byKey[normalizeDesignation(raw)]?.firstOrNull { it !in toDrop } }
                .distinct()
                .toList()
            if (members.size < 2) continue
            val keep = members.minWithOrNull(
                compareBy<DeepSkyObject> { CATALOG_PREFIX_RANK[catalogPrefixOf(it.catalogDesignation)] ?: Int.MAX_VALUE }
                    .thenBy { it.magnitude ?: Float.POSITIVE_INFINITY },
            )
            for (member in members) {
                if (member !== keep) {
                    toDrop += member
                    if (keep != null) {
                        val extras = extraAlternates.getOrPut(keep) { mutableListOf() }
                        extras += member.catalogDesignation
                        extras += member.alternateDesignations
                    }
                }
            }
        }

        val survivors = if (toDrop.isEmpty()) objects else objects.filterNot { it in toDrop }
        if (extraAlternates.isEmpty()) return survivors
        return survivors.map { obj ->
            val extra = extraAlternates[obj]
            if (extra.isNullOrEmpty()) {
                obj
            } else {
                obj.copy(alternateDesignations = (obj.alternateDesignations + extra).distinct())
            }
        }
    }

    private fun isSamePosition(a: DeepSkyObject, b: DeepSkyObject): Boolean {
        val dRa = a.point.raDegrees - b.point.raDegrees
        val dDec = a.point.decDegrees - b.point.decDegrees
        return dRa * dRa + dDec * dDec <= DEDUP_MAX_DEG * DEDUP_MAX_DEG
    }

    /** Nutzerbefund 2026-08-19 (Pelikannebel/Rosettennebel/Flammennebel je zweifach im selben Foto):
     *  anders als [deduplicate] geht es hier NICHT um Katalog-Datenfehler -- IC 5067/IC 5070
     *  (Pelikannebel), NGC 2238/NGC 2244 (Rosettennebel: Nebel + eingebetteter Sternhaufen) und
     *  IC 434/NGC 2024 (Flammennebel) sind bei SIMBAD live bestätigt VERSCHIEDENE Objekte (je eigene
     *  oidref) -- astronomisch also kein Duplikat. Diese App ist aber auf Weitfeld-Fotos ausgelegt, wo
     *  selbst 30-45 Bogenminuten auseinanderliegende Objekte oft im selben Bild landen -- zwei
     *  identische Namens-Beschriftungen wirken dann trotzdem wie ein Fehler. Löst NUR den Namen von
     *  allen bis auf ein Objekt der Gruppe (Kriterium: beste Katalogbezeichnung nach
     *  [CATALOG_PREFIX_RANK], dann bekannte Helligkeit) -- die Katalog-Markierung selbst bleibt für
     *  jedes einzelne Objekt bestehen, nur der doppelte Namenstext verschwindet (Rückfall auf die
     *  bloße Katalogbezeichnung). Bewusst als generische Name+Nähe-Regel (nicht als Katalogabgleich
     *  wie [deduplicate]s zweite Passe): reine SIMBAD-Verschiedenheit reicht hier nicht als
     *  Freibrief, weil es dem Nutzer um das sichtbare Bild geht, nicht um die
     *  astronomische Objekt-Identität -- soll automatisch auch künftig neu hinzukommende Namen
     *  abdecken, ohne dass jeder Fall einzeln nachgetragen werden muss. */
    private fun suppressDuplicatePopularNames(objects: List<DeepSkyObject>): List<DeepSkyObject> {
        val byPopularName = HashMap<String, MutableList<DeepSkyObject>>()
        for (obj in objects) {
            if (obj.properName.isBlank()) continue
            byPopularName.getOrPut(normalizeDesignation(obj.properName)) { mutableListOf() } += obj
        }

        val toStrip = HashSet<DeepSkyObject>()
        for (group in byPopularName.values) {
            if (group.size < 2) continue
            val visited = HashSet<Int>()
            for (start in group.indices) {
                if (start in visited) continue
                // Transitives Nähe-Cluster per Breitensuche: A nah bei B nah bei C zählt als eine
                // gemeinsame Gruppe, auch wenn A und C selbst weiter auseinanderliegen (z.B. eine
                // Kette von Nachbarobjekten mit demselben Gruppennamen).
                val cluster = mutableListOf(start)
                visited += start
                var i = 0
                while (i < cluster.size) {
                    val current = group[cluster[i]]
                    for (j in group.indices) {
                        if (j !in visited && isCloseForNameSuppression(current, group[j])) {
                            visited += j
                            cluster += j
                        }
                    }
                    i++
                }
                if (cluster.size < 2) continue
                val members = cluster.map { group[it] }
                val keep = members.minWithOrNull(
                    compareBy<DeepSkyObject> { CATALOG_PREFIX_RANK[catalogPrefixOf(it.catalogDesignation)] ?: Int.MAX_VALUE }
                        .thenBy { it.magnitude ?: Float.POSITIVE_INFINITY },
                )
                for (member in members) if (member !== keep) toStrip += member
            }
        }

        if (toStrip.isEmpty()) return objects
        return objects.map { obj ->
            if (obj in toStrip) {
                obj.copy(properName = "", localizedProperNames = emptyMap(), properNameSource = "")
            } else {
                obj
            }
        }
    }

    private fun isCloseForNameSuppression(a: DeepSkyObject, b: DeepSkyObject): Boolean {
        val dRa = a.point.raDegrees - b.point.raDegrees
        val dDec = a.point.decDegrees - b.point.decDegrees
        return dRa * dRa + dDec * dDec <= NAME_SUPPRESS_MAX_DEG * NAME_SUPPRESS_MAX_DEG
    }

    private fun normalizeDesignation(name: String): String = name.trim().uppercase().replace(Regex("[\\s\\-_]"), "")

    /** Welche Katalogbezeichnung als DIE Katalog-Anzeige eines Objekts gilt, wenn `id` und `desig`
     *  auseinanderfallen (110 Fälle im Katalog, s. Memory/Sitzungsnotizen) -- z.B. id="NGC 224",
     *  desig="M 31" soll überall "M 31" zeigen, nicht "NGC 224". Explizite Rangliste statt stiller
     *  desig-Präferenz, falls sich das mit künftigen Katalog-Updates ändert. Rang nach echter
     *  Häufigkeit/Bekanntheit im eigenen Katalog geordnet (M > NGC > IC > PGC > UGC/UGCA > ACO/ESO >
     *  Nebel-Spezialkataloge); unlistete Präfixe (Int.MAX_VALUE) behalten die bisherige desig-
     *  Präferenz bei -- deckt insbesondere die LMC/SMC/WLM-Sonderfälle (desig="LMC" o.ä., kein
     *  Katalogcode) und die ~91.633 Objekte mit id==desig unverändert ab. */
    private val CATALOG_PREFIX_RANK: Map<String, Int> = mapOf(
        "M" to 1,
        "NGC" to 2,
        "IC" to 3,
        "PGC" to 4,
        "UGC" to 5, "UGCA" to 5,
        "ACO" to 6, "ESO" to 6,
        "SH" to 7, "SH2" to 7, "RCW" to 7, "LBN" to 7, "LDN" to 7, "CED" to 7, "VDB" to 7, "VDBH" to 7,
    )

    /** Alles vor der ersten Ziffer, z.B. "NGC 224" -> "NGC", "M 31" -> "M". Kein Ziffernanteil (z.B.
     *  "LMC") -> die ganze Zeichenkette, landet dann als unbekannter Präfix in [CATALOG_PREFIX_RANK]. */
    private fun catalogPrefixOf(designation: String): String {
        val trimmed = designation.trim()
        val digitIndex = trimmed.indexOfFirst { it.isDigit() }
        return (if (digitIndex > 0) trimmed.substring(0, digitIndex) else trimmed).trim().uppercase()
    }

    private fun preferredCatalogDesignation(id: String, desig: String): String {
        if (id == desig) return desig
        // Nur eingreifen, wenn BEIDE Präfixe in der Rangliste stehen -- sonst bliebe z.B.
        // id="PGC 17223"/desig="LMC" (kein Katalogcode, s.o.) faelschlich bei "PGC 17223" haengen,
        // weil "unlistet" sonst schlechter als jeder gelistete Rang waere, statt neutral zu sein
        // (live an der Verifikation der 3 LMC/SMC/WLM-Sonderfaelle gefunden).
        val idRank = CATALOG_PREFIX_RANK[catalogPrefixOf(id)] ?: return desig
        val desigRank = CATALOG_PREFIX_RANK[catalogPrefixOf(desig)] ?: return desig
        return if (idRank < desigRank) id else desig
    }

    private const val DEDUP_MAX_DEG = 0.2f

    // 1,5° -- bewusst deutlich großzügiger als DEDUP_MAX_DEG: die drei live gefundenen Fälle
    // (Pelikannebel 33', Rosettennebel 4', Flammennebel 42') sollen sicher mit Marge abgedeckt sein,
    // während eindeutig unabhängige, nur zufällig gleich benannte Objekte an entgegengesetzten
    // Himmelspositionen (z.B. "Starfish Cluster" an zwei ~166° auseinanderliegenden Objekten)
    // unangetastet bleiben.
    private const val NAME_SUPPRESS_MAX_DEG = 1.5f
}
