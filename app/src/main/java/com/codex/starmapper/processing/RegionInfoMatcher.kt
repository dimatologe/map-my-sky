package com.codex.starmapper.processing

import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.CatalogStar
import com.codex.starmapper.domain.ConstellationPattern
import com.codex.starmapper.domain.DeepSkyObject
import com.codex.starmapper.domain.Hemisphere

/** Ein Fakt-Paar (z. B. "Typ" -> "Planetarischer Nebel") für die Region-Info-Liste. */
data class RegionInfoFact(val label: String, val value: String)

/**
 * Eine Zeile der Region-Info-Liste. [facts] sind rein aus den gebündelten Katalogen abgeleitet
 * (immer verfügbar, offline). [wikipediaTitleCandidates] ist LEER, wenn ein Wikipedia-Abruf für
 * dieses Objekt gar nicht erst versucht werden soll (s. [RegionInfoMatcher.buildEntries]).
 * [attemptWikidataFacts] steuert, ob zusätzlich Online-Fakten (Entfernung/Spektraltyp,
 * [WikidataFactsService]) versucht werden -- nur für DSO/Stern sinnvoll, nicht für Sternbilder.
 * [nameSource] ist leer ODER z.B. "Stellarium", wenn [title] einen Populärnamen zeigt, der aus
 * dieser Quelle stammt (s. [com.codex.starmapper.domain.DeepSkyObject.properNameSource]).
 */
data class RegionInfoEntry(
    val cacheKey: String,
    val title: String,
    val subtitle: String,
    val facts: List<RegionInfoFact>,
    val wikipediaTitleCandidates: List<String>,
    val attemptWikidataFacts: Boolean = false,
    val nameSource: String = "",
)

/**
 * Baut die Region-Info-Liste aus den AKTUELL sichtbaren (Katalog-)Overlays -- keine eigene
 * FOV-/Sichtbarkeits-Logik, reine Zuordnung `AnnotationOverlay -> Original-Katalogeintrag` +
 * Aufbereitung als lesbare Fakten. [lang] ist ein BCP-47-Kürzel (de/en/zh/es/ru/ar/ja, s.
 * `AppLocale.resolvedLanguageTag`). Für Details zur Herleitung der Typ-Codes und der
 * Wikipedia-Gate-Logik siehe den Plan „Region-Info".
 *
 * Stern- und Sternbild-EIGENNAMEN existieren nur auf Deutsch/Englisch in den gebündelten Katalogen
 * (`CatalogStar.name/nameDe`, `ConstellationPattern.name/germanName`) -- für die 5 neuen Sprachen wird
 * bewusst auf die international gültige Form zurückgegriffen (Katalogname bzw. lateinischer
 * Sternbildname), NICHT geraten. Nur die (begrenzte, gut einschätzbare) UI-Beschriftung der Fakten
 * selbst ist unten für alle 7 Sprachen übersetzt.
 */
object RegionInfoMatcher {

    // DSO-Typ-Code -> Bezeichnung je Sprache (dsos.20.json, alle 16 vorkommenden Codes ausgezählt).
    // "i"/"sd" bewusst neutral gehalten (Original-Daten widersprüchlich, s. Plan).
    private val TYPE_LABELS: Map<String, Map<String, String>> = mapOf(
        "s" to mapOf(
            "de" to "Spiralgalaxie", "en" to "Spiral galaxy", "zh" to "螺旋星系", "es" to "Galaxia espiral",
            "ru" to "Спиральная галактика", "ar" to "مجرة حلزونية", "ja" to "渦巻銀河",
        ),
        "g" to mapOf(
            "de" to "Galaxie", "en" to "Galaxy", "zh" to "星系", "es" to "Galaxia", "ru" to "Галактика",
            "ar" to "مجرة", "ja" to "銀河",
        ),
        "s0" to mapOf(
            "de" to "Linsenförmige Galaxie (S0)", "en" to "Lenticular galaxy (S0)", "zh" to "透镜状星系 (S0)",
            "es" to "Galaxia lenticular (S0)", "ru" to "Линзовидная галактика (S0)",
            "ar" to "مجرة عدسية (S0)", "ja" to "レンズ状銀河 (S0)",
        ),
        "e" to mapOf(
            "de" to "Elliptische Galaxie", "en" to "Elliptical galaxy", "zh" to "椭圆星系",
            "es" to "Galaxia elíptica", "ru" to "Эллиптическая галактика", "ar" to "مجرة إهليلجية",
            "ja" to "楕円銀河",
        ),
        "i" to mapOf(
            "de" to "Galaxie (Typ ungeklärt)", "en" to "Galaxy (type unclear)", "zh" to "星系（类型未定）",
            "es" to "Galaxia (tipo incierto)", "ru" to "Галактика (тип не определён)",
            "ar" to "مجرة (نوع غير محدد)", "ja" to "銀河（種類不明）",
        ),
        "gg" to mapOf(
            "de" to "Galaxienhaufen/-gruppe", "en" to "Galaxy cluster/group", "zh" to "星系团/星系群",
            "es" to "Cúmulo/grupo de galaxias", "ru" to "Скопление/группа галактик",
            "ar" to "عنقود/مجموعة مجرات", "ja" to "銀河団・銀河群",
        ),
        "pn" to mapOf(
            "de" to "Planetarischer Nebel", "en" to "Planetary nebula", "zh" to "行星状星云",
            "es" to "Nebulosa planetaria", "ru" to "Планетарная туманность", "ar" to "سديم كوكبي",
            "ja" to "惑星状星雲",
        ),
        "dn" to mapOf(
            "de" to "Dunkelnebel", "en" to "Dark nebula", "zh" to "暗星云", "es" to "Nebulosa oscura",
            "ru" to "Тёмная туманность", "ar" to "سديم مظلم", "ja" to "暗黒星雲",
        ),
        "bn" to mapOf(
            "de" to "Nebel", "en" to "Nebula", "zh" to "星云", "es" to "Nebulosa", "ru" to "Туманность",
            "ar" to "سديم", "ja" to "星雲",
        ),
        "oc" to mapOf(
            "de" to "Offener Sternhaufen", "en" to "Open cluster", "zh" to "疏散星团",
            "es" to "Cúmulo abierto", "ru" to "Рассеянное скопление", "ar" to "عنقود مفتوح",
            "ja" to "散開星団",
        ),
        "rn" to mapOf(
            "de" to "Reflexionsnebel", "en" to "Reflection nebula", "zh" to "反射星云",
            "es" to "Nebulosa de reflexión", "ru" to "Отражательная туманность",
            "ar" to "سديم انعكاسي", "ja" to "反射星雲",
        ),
        "snr" to mapOf(
            "de" to "Supernova-Überrest", "en" to "Supernova remnant", "zh" to "超新星遗迹",
            "es" to "Remanente de supernova", "ru" to "Остаток сверхновой", "ar" to "بقايا مستعر أعظم",
            "ja" to "超新星残骸",
        ),
        "gc" to mapOf(
            "de" to "Kugelsternhaufen", "en" to "Globular cluster", "zh" to "球状星团",
            "es" to "Cúmulo globular", "ru" to "Шаровое скопление", "ar" to "عنقود كروي",
            "ja" to "球状星団",
        ),
        "en" to mapOf(
            "de" to "Emissionsnebel", "en" to "Emission nebula", "zh" to "发射星云",
            "es" to "Nebulosa de emisión", "ru" to "Эмиссионная туманность", "ar" to "سديم انبعاثي",
            "ja" to "輝線星雲",
        ),
        "sfr" to mapOf(
            "de" to "Sternentstehungsgebiet", "en" to "Star-forming region", "zh" to "恒星形成区",
            "es" to "Región de formación estelar", "ru" to "Область звездообразования",
            "ar" to "منطقة تشكل نجمي", "ja" to "星形成領域",
        ),
        "sd" to mapOf(
            "de" to "Zwerggalaxie (Typ ungeklärt)", "en" to "Dwarf galaxy (type unclear)",
            "zh" to "矮星系（类型未定）", "es" to "Galaxia enana (tipo incierto)",
            "ru" to "Карликовая галактика (тип не определён)", "ar" to "مجرة قزمة (نوع غير محدد)",
            "ja" to "矮小銀河（種類不明）",
        ),
    )

    // Kurze, wiederkehrende Fakten-Labels je Sprache (Typ/Helligkeit/Größe/Sternbild/...).
    private val FACT_LABELS: Map<String, Map<String, String>> = mapOf(
        "type" to mapOf(
            "de" to "Typ", "en" to "Type", "zh" to "类型", "es" to "Tipo", "ru" to "Тип",
            "ar" to "النوع", "ja" to "タイプ",
        ),
        "magnitude" to mapOf(
            "de" to "Helligkeit", "en" to "Magnitude", "zh" to "星等", "es" to "Magnitud",
            "ru" to "Звёздная величина", "ar" to "القدر الظاهري", "ja" to "等級",
        ),
        "size" to mapOf(
            "de" to "Größe", "en" to "Size", "zh" to "大小", "es" to "Tamaño", "ru" to "Размер",
            "ar" to "الحجم", "ja" to "大きさ",
        ),
        "also_known_as" to mapOf(
            "de" to "Auch bekannt als", "en" to "Also known as", "zh" to "别名",
            "es" to "También conocido como", "ru" to "Также известен как",
            "ar" to "يُعرف أيضًا باسم", "ja" to "別名",
        ),
        "constellation" to mapOf(
            "de" to "Sternbild", "en" to "Constellation", "zh" to "星座", "es" to "Constelación",
            "ru" to "Созвездие", "ar" to "الكوكبة", "ja" to "星座",
        ),
        "star" to mapOf(
            "de" to "Stern", "en" to "Star", "zh" to "恒星", "es" to "Estrella", "ru" to "Звезда",
            "ar" to "نجم", "ja" to "恒星",
        ),
        "latin_name" to mapOf(
            "de" to "Lateinischer Name", "en" to "Latin name", "zh" to "拉丁名",
            "es" to "Nombre latino", "ru" to "Латинское название", "ar" to "الاسم اللاتيني",
            "ja" to "ラテン名",
        ),
        "hemisphere" to mapOf(
            "de" to "Hemisphäre", "en" to "Hemisphere", "zh" to "半球", "es" to "Hemisferio",
            "ru" to "Полушарие", "ar" to "نصف الكرة", "ja" to "半球",
        ),
        "north_sky" to mapOf(
            "de" to "Nordhimmel", "en" to "Northern sky", "zh" to "北天", "es" to "Cielo norte",
            "ru" to "Северное небо", "ar" to "السماء الشمالية", "ja" to "北天",
        ),
        "south_sky" to mapOf(
            "de" to "Südhimmel", "en" to "Southern sky", "zh" to "南天", "es" to "Cielo sur",
            "ru" to "Южное небо", "ar" to "السماء الجنوبية", "ja" to "南天",
        ),
        "both_hemispheres" to mapOf(
            "de" to "beide Hemisphären", "en" to "both hemispheres", "zh" to "南北半球",
            "es" to "ambos hemisferios", "ru" to "оба полушария", "ar" to "كلا نصفي الكرة",
            "ja" to "両半球",
        ),
    )

    private fun label(key: String, lang: String): String =
        FACT_LABELS[key]?.get(lang) ?: FACT_LABELS[key]?.get("en") ?: key

    fun typeLabel(code: String, lang: String): String {
        val entry = TYPE_LABELS[code.lowercase()] ?: return code
        return entry[lang] ?: entry["en"] ?: code
    }

    fun buildEntries(
        overlays: List<AnnotationOverlay>,
        deepSkyObjects: List<DeepSkyObject>,
        catalogStars: List<CatalogStar>,
        lang: String,
    ): List<RegionInfoEntry> {
        // Ein DSO kann unter seiner Katalogbezeichnung ODER (falls vorhanden) unter jeder
        // Populärnamen-Sprachvariante als overlay.text auftauchen (s. AstapOverlayMapper.
        // createDeepSkyOverlays -> properDisplayName) -- Index über alle bekannten Varianten,
        // analog zum Stern-Index unten, damit die Rückführung unabhängig von der gerade aktiven
        // Anzeigesprache funktioniert.
        val deepSkyByName = HashMap<String, DeepSkyObject>()
        for (dso in deepSkyObjects) {
            val nameKeys = listOf(dso.name, dso.properName) + dso.localizedProperNames.values
            for (key in nameKeys) {
                if (key.isNotBlank()) deepSkyByName.putIfAbsent(key, dso)
            }
        }
        // Ein Stern kann unter jeder seiner Sprachvarianten als overlay.text auftauchen (je nachdem,
        // in welcher Sprache er beschriftet wurde) -- Index über alle bekannten Varianten, damit die
        // Rückführung unabhängig von der gerade aktiven Anzeigesprache funktioniert.
        val starIndex = HashMap<String, CatalogStar>()
        for (star in catalogStars) {
            val nameKeys = listOf(star.name, star.properName) +
                star.localizedNames.values + star.localizedProperNames.values
            for (key in nameKeys) {
                if (key.isNotBlank()) starIndex.putIfAbsent(key, star)
            }
        }

        val seen = HashSet<String>()
        val entries = mutableListOf<RegionInfoEntry>()
        for (overlay in overlays) {
            val entry = when (overlay.layer) {
                AnnotationLayer.DeepSky -> deepSkyByName[overlay.text]?.let { buildDeepSkyEntry(it, lang) }
                AnnotationLayer.Star -> starIndex[overlay.text]?.let { buildStarEntry(it, lang) }
                AnnotationLayer.Constellation -> overlay.constellation?.let { buildConstellationEntry(it, lang) }
                null -> null
            } ?: continue
            if (seen.add(entry.cacheKey)) entries += entry
        }
        return entries
    }

    private fun buildDeepSkyEntry(dso: DeepSkyObject, lang: String): RegionInfoEntry {
        val facts = mutableListOf(RegionInfoFact(label("type", lang), typeLabel(dso.type, lang)))
        dso.magnitude?.let {
            facts += RegionInfoFact(label("magnitude", lang), "%.1f mag".format(it))
        }
        dso.majorAxisArcmin?.let { maj ->
            val min = dso.minorAxisArcmin
            val size = if (min != null && min != maj) "%.1f' × %.1f'".format(maj, min) else "%.1f'".format(maj)
            facts += RegionInfoFact(label("size", lang), size)
        }
        // Weitere Katalogbezeichnungen desselben Objekts (id≠desig-Fälle + SIMBAD-oidref-Kreuzreferenz-
        // Gruppen, s. DeepSkyAssetLoader) -- Nutzerwunsch 2026-08-19: "mehrere Katalognamen pro Objekt".
        if (dso.alternateDesignations.isNotEmpty()) {
            facts += RegionInfoFact(label("also_known_as", lang), dso.alternateDesignations.joinToString(", "))
        }

        // Wikipedia-Versuch für JEDES eingeblendete DSO, nicht nur die kuratierte "Popular"-Liste
        // (frühere Einschränkung, s. Git-Historie) -- Region-Info arbeitet ohnehin nur mit der
        // Handvoll gerade sichtbarer Objekte, nicht dem ganzen ~89.9k-Katalog, ein "kein Artikel
        // gefunden" kostet nur einen einzelnen, ungefährlichen Netzwerkversuch (durch das
        // Themen-Gate in WikipediaSummaryService ohnehin gegen Fehltreffer abgesichert). Konsistent
        // mit Sternen (buildStarEntry), die schon immer ungefiltert versucht werden.
        // dso.id (NGC/IC-Katalognummer, z.B. "NGC 6611") zuerst versuchen, NICHT dso.name (kurze
        // Messier-Form, z.B. "M 16"): kurze "M"+Zahl-Titel kollidieren auf Wikipedia real mit
        // fachfremden Themen (M16 -> Sturmgewehr statt Adlernebel als "primary topic", kein
        // Begriffsklärungs-Hinweis) -- die NGC/IC-Nummer ist dagegen praktisch nie mehrdeutig. Die
        // Messier-Form bleibt als Fallback. Katalog-Kennungen sind sprachunabhängig -- dieselben
        // Kandidaten gelten für alle 7 Sprachen.
        val candidates = listOf(dso.id, dso.name).distinct()

        // Populärname ersetzt die Katalogbezeichnung als Titel (properDisplayName) -- ohne diese
        // Ergänzung war die Katalogbezeichnung (z.B. "IC 1805") dann nirgends mehr in der Region-Info
        // sichtbar (Nutzerbefund 2026-08-18). dso.catalogDesignation (nicht dso.id) ist die dafür
        // relevante Bezeichnung: bei den 110 Fällen mit id != desig soll die Klammer die bevorzugte
        // Katalogform zeigen (z.B. "M 31", nicht "NGC 224") -- Nutzerwunsch 2026-08-19, s.
        // DeepSkyAssetLoader.preferredCatalogDesignation für die vollständige Rangliste (Messier vor
        // NGC vor IC vor allen anderen, bei SIMBAD selbst live verifiziert: main_id bevorzugt Messier
        // durchgängig).
        val displayName = dso.properDisplayName(lang)
        val title = if (displayName != dso.catalogDesignation) {
            "$displayName (${dso.catalogDesignation})"
        } else {
            displayName
        }

        return RegionInfoEntry(
            cacheKey = "dso:${dso.id}",
            title = title,
            subtitle = typeLabel(dso.type, lang),
            facts = facts,
            wikipediaTitleCandidates = candidates,
            attemptWikidataFacts = true,
            // Nur relevant, wenn der Titel ueberhaupt einen Populaernamen zeigt (nicht nur die
            // Katalogbezeichnung) -- sonst gibt es keine "Namensquelle" anzuzeigen.
            nameSource = if (dso.properName.isNotBlank()) dso.properNameSource else "",
        )
    }

    private fun buildStarEntry(star: CatalogStar, lang: String): RegionInfoEntry {
        val facts = mutableListOf(
            RegionInfoFact(label("magnitude", lang), "%.1f mag".format(star.magnitude)),
        )
        if (star.constellation.isNotBlank()) {
            facts += RegionInfoFact(label("constellation", lang), star.constellation)
        }
        // Nur Sterne mit echtem Eigennamen haben realistisch einen Wikipedia-Artikel -- eine bloße
        // Bayer-/Flamsteed-/HIP-Bezeichnung würde nur Fehlversuche produzieren.
        val properName = star.properDisplayName(lang)
        val hasProperName = properName.isNotBlank()
        return RegionInfoEntry(
            cacheKey = "star:${star.id}",
            title = star.displayName(lang),
            subtitle = label("star", lang),
            facts = facts,
            wikipediaTitleCandidates = if (hasProperName) listOf(properName) else emptyList(),
            attemptWikidataFacts = hasProperName,
        )
    }

    private fun buildConstellationEntry(pattern: ConstellationPattern, lang: String): RegionInfoEntry {
        val hemisphereLabel = when (pattern.hemisphere) {
            Hemisphere.North -> label("north_sky", lang)
            Hemisphere.South -> label("south_sky", lang)
            Hemisphere.Both -> label("both_hemispheres", lang)
        }
        val facts = listOf(
            RegionInfoFact(label("latin_name", lang), pattern.name),
            RegionInfoFact(label("hemisphere", lang), hemisphereLabel),
        )
        // Alle 88 IAU-Sternbilder haben verlässlich einen Wikipedia-Artikel -- immer versuchen.
        // "(Sternbild)"/"(constellation)" ist die übliche Klammerzusatz-Konvention für den
        // deutschen/englischen Artikeltitel, wenn der bloße Name mehrdeutig wäre. Für die 5 neuen
        // Sprachen (jetzt mit echtem lokalisiertem Namen aus Wikidata, s. ConstellationCatalog) ist die
        // Klammerzusatz-Konvention nicht bekannt -- der lokalisierte Name als erster Kandidat, der
        // lateinische Name als zweiter; sollte trotzdem die falsche Seite treffen, fängt das Themen-Gate
        // das ab, und die Wikidata-Sitelinks-Auflösung (WikipediaSummaryService) findet den echten Titel
        // über die beim EN/DE-Versuch bereits bekannte Q-ID.
        val localizedName = pattern.localizedNames[lang]
        val candidates = when (lang) {
            "de" -> listOf("${pattern.germanName} (Sternbild)", pattern.germanName)
            "en" -> listOf("${pattern.name} (constellation)", pattern.name)
            else -> listOfNotNull(localizedName, pattern.name).distinct()
        }
        return RegionInfoEntry(
            cacheKey = "const:${pattern.id}",
            title = when (lang) {
                "de" -> pattern.germanName
                else -> localizedName ?: pattern.name
            },
            subtitle = label("constellation", lang),
            facts = facts,
            wikipediaTitleCandidates = candidates,
        )
    }
}
