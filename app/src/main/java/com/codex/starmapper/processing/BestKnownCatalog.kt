package com.codex.starmapper.processing

import com.codex.starmapper.domain.DeepSkyObject

/**
 * Best Known V1 (Nutzer-Vorgabe 2026-09-01): eine feste, kuratierte Menge von 250 Ziel-Objekten
 * (110 Messier + 109 Caldwell + 31 zusätzliche Astrofotografie-Ziele) -- KEIN zweiter Objektkatalog,
 * KEINE eigene Geometrie/RA/Dec. Jeder [BestKnownEntry] ist ausschließlich eine Zuordnung von
 * (bereits im bestehenden [DeepSkyObject]-Katalog vorhandenen) Bezeichnungen zu einem optionalen,
 * besser bekannten Anzeigenamen (DE/international) -- reine Identitäts-/Namensauflösung, s.
 * [AstapOverlayMapper.createDeepSkyOverlays] für die tatsächliche Anwendung (Best-Known-Filter +
 * Namensauflösung).
 *
 * [matchIds]: EINE oder MEHRERE (bei getrennt zu haltenden Mehrfachobjekten, s. u.) rohe
 * Katalog-Bezeichnungen, die denselben physischen Ziel-Eintrag identifizieren -- werden gegen
 * [DeepSkyObject.name]/[DeepSkyObject.id]/[DeepSkyObject.alternateDesignations] normalisiert
 * verglichen ([normalizeId], identische Normalisierung wie das bestehende
 * `DeepSkyAssetLoader.normalizeDesignation`: Leerzeichen/Bindestriche/Unterstriche entfernt,
 * Großschreibung). EIN [BestKnownEntry] = EIN physisches Zielobjekt -- Mehrfachobjekte (Doppelhaufen,
 * Antennen-Galaxien) bekommen bewusst MEHRERE, getrennte [BestKnownEntry]-Zeilen statt eines
 * zusammengeführten Eintrags (s. Kommentare an den jeweiligen Einträgen unten).
 *
 * [nameIntl]/[nameDe]: `null` = KEIN wirklich etablierter Eigenname bekannt -- dann bleibt (s.
 * [resolveDisplayName]) der bisherige Katalogname unverändert stehen. KEINE künstlichen
 * Übersetzungen/Rückfallnamen -- nur tatsächlich gebräuchliche Namen wurden hier eingetragen.
 *
 * WICHTIG (ehrlicher Stand, s. auch Abschlussbericht dieser Runde): die Messier-Nummern selbst sind
 * eine reine, mechanisch korrekte 1:1-Bezeichnung (M1..M110 SIND bereits die Katalog-Anzeigeform,
 * keine externe Übersetzungstabelle nötig). Die Caldwell-Zuordnung (C1..C109 -> NGC/IC/sonstige
 * Bezeichnung) ist dagegen externes astronomisches Referenzwissen (der `dsos.20.json`-Katalog selbst
 * enthält NACHWEISLICH keine "C "-Bezeichnungen, s. Grep-Beleg im Abschlussbericht) -- aus
 * Trainingswissen befüllt, in dieser Sitzung NICHT gegen eine externe Quelle nachgeprüft. Empfehlung:
 * vor Produktiveinsatz stichprobenartig gegen eine Referenztabelle (z. B. Wikipedia „Caldwell
 * catalogue") abgleichen, besonders die weniger geläufigen Nummern.
 */
data class BestKnownEntry(
    val matchIds: List<String>,
    val preferredCanonicalId: String,
    val nameIntl: String? = null,
    val nameDe: String? = null,
) {
    constructor(matchId: String, nameIntl: String? = null, nameDe: String? = null) :
        this(listOf(matchId), matchId, nameIntl, nameDe)
}

object BestKnownCatalog {

    // Performance-Fix 2026-09-02: vorher wurde Regex("[\\s\\-_]") bei JEDEM normalizeId()-Aufruf neu
    // kompiliert -- match() ruft das bis zu 4x pro Objekt auf, bei aktivem Best-Known-Schalter also bis
    // zu ~365.000x pro einziger createDeepSkyOverlays()-Neuberechnung (91.281 Katalogobjekte). Einmal
    // kompiliertes Pattern statt Neubau pro Aufruf -- identisches Verhalten, nur ohne die wiederholte
    // Kompilierungskosten.
    private val NORMALIZE_ID_PATTERN = Regex("[\\s\\-_]")

    fun normalizeId(raw: String): String = raw.trim().uppercase().replace(NORMALIZE_ID_PATTERN, "")

    /** DE, falls vorhanden, sonst international, sonst (null) unveränderter Katalogname -- exakt
     *  Abschnitt C/T der Nutzer-Vorgabe. [lang] wie überall in dieser App die aktive Annotation-/
     *  Export-Sprache ([com.codex.starmapper.ui.AppLocale.resolvedLanguageTag]), NICHT die reine
     *  Android-Systemsprache. */
    fun resolveDisplayName(entry: BestKnownEntry, lang: String): String? =
        if (lang == "de") entry.nameDe ?: entry.nameIntl else entry.nameIntl

    private val byNormalizedId: Map<String, BestKnownEntry> by lazy {
        val map = HashMap<String, BestKnownEntry>(entries.size * 2)
        for (entry in entries) {
            for (id in entry.matchIds) {
                val key = normalizeId(id)
                // Absichtlich KEIN Fehler bei Kollision -- der ersteintragende Eintrag gewinnt, eine
                // Kollision würde nur bei einem Tippfehler in dieser Tabelle selbst auftreten (die
                // 250 Ziele sind per Konstruktion disjunkt) und soll den Katalog nicht zum Absturz bringen.
                if (key !in map) map[key] = entry
            }
        }
        map
    }

    /** Prüft [deepSky] gegen [name]/[id]/jede [DeepSkyObject.alternateDesignations]-Zeile, normalisiert
     *  -- deckt damit automatisch beide Speicherrichtungen ab, die `DeepSkyAssetLoader` für dieselbe
     *  physische Quelle wählen kann (z. B. M31 als `id="NGC 224"`/`name="M 31"` ODER umgekehrt), ohne
     *  dass diese Tabelle beide Schreibweisen selbst auflisten müsste (s. `preferredCatalogDesignation`
     *  in `DeepSkyAssetLoader.kt` -- diese Funktion wird hier bewusst NICHT dupliziert, nur die
     *  bereits vom Loader befüllten Felder werden gelesen). Kein Abstands-/Positionsabgleich -- reine
     *  Bezeichnungs-Übereinstimmung, s. Abschnitt F der Nutzer-Vorgabe ("keine heuristische
     *  Positionsverschmelzung"). */
    fun match(deepSky: DeepSkyObject): BestKnownEntry? {
        byNormalizedId[normalizeId(deepSky.name)]?.let { return it }
        byNormalizedId[normalizeId(deepSky.id)]?.let { return it }
        for (alt in deepSky.alternateDesignations) {
            byNormalizedId[normalizeId(alt)]?.let { return it }
        }
        return null
    }

    /**
     * 110 Messier-Objekte. `matchIds = ["M$n"]` ist eine reine, mechanisch korrekte 1:1-Form (Messier-
     * Nummern SIND bereits die Katalog-Anzeigeform in `dsos.20.json`, s. Klassenkommentar) -- keine
     * externe Übersetzung nötig, dieser Teil ist strukturell nicht fehleranfällig. Namen NUR für
     * wirklich etablierte Eigennamen eingetragen (deutlich weniger als 110 -- die meisten Messier-
     * Objekte haben KEINEN gebräuchlichen Eigennamen, bleiben also beim Katalognamen, s. Abschnitt C).
     */
    private val messier: List<BestKnownEntry> = buildList {
        val names: Map<Int, Pair<String, String>> = mapOf(
            1 to ("Crab Nebula" to "Krebsnebel"),
            8 to ("Lagoon Nebula" to "Lagunennebel"),
            13 to ("Great Globular Cluster in Hercules" to "Herkuleshaufen"),
            16 to ("Eagle Nebula" to "Adlernebel"),
            17 to ("Omega Nebula" to "Omeganebel"),
            20 to ("Trifid Nebula" to "Trifidnebel"),
            27 to ("Dumbbell Nebula" to "Hantelnebel"),
            31 to ("Andromeda Galaxy" to "Andromedagalaxie"),
            // 32 (Begleitgalaxie von M31) hat keinen durchgängig etablierten Eigennamen.
            33 to ("Triangulum Galaxy" to "Dreiecksgalaxie"),
            42 to ("Orion Nebula" to "Orionnebel"),
            43 to ("De Mairan's Nebula" to "De-Mairans-Nebel"),
            44 to ("Beehive Cluster" to "Praesepe"),
            45 to ("Pleiades" to "Plejaden"),
            51 to ("Whirlpool Galaxy" to "Strudelgalaxie"),
            57 to ("Ring Nebula" to "Ringnebel"),
            64 to ("Black Eye Galaxy" to "Black-Eye-Galaxie"),
            // 65/66 (zwei Drittel des Leo-Tripletts, s. NGC 3628/"Hamburger Galaxy" in den 31
            // Zusatzzielen für das dritte Mitglied) haben KEINEN etablierten Einzel-Eigennamen --
            // bewusst kein künstlicher "Leo Triplet (M65)"-Name erfunden.
            74 to ("Phantom Galaxy" to "Phantomgalaxie"),
            // 77 (Seyfert-Galaxie in Cetus) bewusst NICHT eingetragen -- "Cetus A" ist kein
            // durchgängig etablierter Eigenname (Unsicherheit selbst erkannt), bleibt beim Katalognamen.
            81 to ("Bode's Galaxy" to "Bodes Galaxie"),
            82 to ("Cigar Galaxy" to "Zigarrengalaxie"),
            83 to ("Southern Pinwheel Galaxy" to "Südliche Feuerradgalaxie"),
            97 to ("Owl Nebula" to "Eulennebel"),
            101 to ("Pinwheel Galaxy" to "Feuerradgalaxie"),
            104 to ("Sombrero Galaxy" to "Sombrerogalaxie"),
            // 106/108/109/110 bewusst NICHT eingetragen -- kein etablierter Eigenname, bleiben beim
            // Katalognamen (Abschnitt C: "kein sinnvoller Proper/Common Name vorhanden -> Katalognamen
            // unverändert benutzen").
        )
        for (n in 1..110) {
            val entry = names[n]
            // M102: umstrittene Identität, breit akzeptierte Gleichsetzung mit NGC 5866 (dasselbe
            // physische Objekt, keine Nachbar-/Teilstruktur wie bei den in Abschnitt B gewarnten
            // Fällen) -- s. Abschlussbericht, per echtem Katalog-Abgleich verifiziert.
            // M40 (Winnecke 4) ist ein DOPPELSTERN, kein Deep-Sky-Objekt -- er hat im
            // Deep-Sky-Katalog bewusst keine Entsprechung und bleibt daher ohne Treffer (Prüfung
            // 2026-09-04). Der naheliegendste Katalogeintrag, NGC 4290 (RA 12,347h / Dec +58,09;
            // 0,21° = 12,5' entfernt), ist eine ECHTE, ANDERE Spiralgalaxie (type=s, mag 12,7) und
            // wäre eine Falschzuordnung -- ebenso NGC 4284 (0,29°). Bewusst kein Alias, s.
            // bestknown_catalog_audit.
            val matchIds = if (n == 102) listOf("M$n", "NGC 5866") else listOf("M$n")
            add(BestKnownEntry(matchIds, "M$n", nameIntl = entry?.first, nameDe = entry?.second))
        }
    }

    /**
     * 109 Caldwell-Objekte (Patrick Moore, 1995) -> reale NGC/IC/sonstige Bezeichnung im bestehenden
     * Katalog. EXTERNES Referenzwissen, s. Klassenkommentar für den ehrlichen Vorbehalt. C14 (Double
     * Cluster) und C60/C61 (Antennae) sind laut Nutzer-Vorgabe (Abschnitt G) explizit als ZWEI reale
     * Einzelobjekte zu behandeln -- hier als zwei separate [BestKnownEntry]-Zeilen umgesetzt statt
     * einer künstlich verschmolzenen Gruppe.
     */
    private val caldwell: List<BestKnownEntry> = listOf(
        BestKnownEntry(listOf("NGC 188"), "C1"),
        BestKnownEntry(listOf("NGC 40"), "C2", nameIntl = "Bow-Tie Nebula"),
        BestKnownEntry(listOf("NGC 4236"), "C3"),
        BestKnownEntry(listOf("NGC 7023"), "C4", nameIntl = "Iris Nebula", nameDe = "Irisnebel"),
        BestKnownEntry(listOf("IC 342"), "C5"),
        BestKnownEntry(listOf("NGC 6543"), "C6", nameIntl = "Cat's Eye Nebula", nameDe = "Katzenaugennebel"),
        BestKnownEntry(listOf("NGC 2403"), "C7"),
        BestKnownEntry(listOf("NGC 559"), "C8"),
        BestKnownEntry(listOf("Sh2-155"), "C9", nameIntl = "Cave Nebula"),
        BestKnownEntry(listOf("NGC 663"), "C10"),
        BestKnownEntry(listOf("NGC 7635"), "C11", nameIntl = "Bubble Nebula", nameDe = "Blasennebel"),
        BestKnownEntry(listOf("NGC 6946"), "C12", nameIntl = "Fireworks Galaxy"),
        BestKnownEntry(listOf("NGC 457"), "C13", nameIntl = "Owl Cluster"),
        // C14 Double Cluster -- ZWEI reale Sternhaufen, s. Klassenkommentar. Bewusst KEIN gemeinsamer
        // Name (kein Group-Label-Mechanismus vorhanden, s. Abschnitt G) -- beide behalten ihren Katalognamen.
        BestKnownEntry(listOf("NGC 869"), "C14"),
        BestKnownEntry(listOf("NGC 884"), "C14"),
        BestKnownEntry(listOf("NGC 6826"), "C15", nameIntl = "Blinking Planetary"),
        BestKnownEntry(listOf("NGC 7243"), "C16"),
        BestKnownEntry(listOf("NGC 147"), "C17"),
        BestKnownEntry(listOf("NGC 185"), "C18"),
        BestKnownEntry(listOf("IC 5146"), "C19", nameIntl = "Cocoon Nebula", nameDe = "Kokonnebel"),
        BestKnownEntry(listOf("NGC 7000"), "C20", nameIntl = "North America Nebula", nameDe = "Nordamerikanebel"),
        BestKnownEntry(listOf("NGC 4449"), "C21"),
        BestKnownEntry(listOf("NGC 7662"), "C22", nameIntl = "Blue Snowball"),
        BestKnownEntry(listOf("NGC 891"), "C23"),
        BestKnownEntry(listOf("NGC 1275"), "C24", nameIntl = "Perseus A"),
        BestKnownEntry(listOf("NGC 2419"), "C25", nameIntl = "Intergalactic Wanderer"),
        BestKnownEntry(listOf("NGC 4244"), "C26", nameIntl = "Silver Needle Galaxy"),
        BestKnownEntry(listOf("NGC 6888"), "C27", nameIntl = "Crescent Nebula", nameDe = "Sichelnebel"),
        BestKnownEntry(listOf("NGC 752"), "C28"),
        BestKnownEntry(listOf("NGC 5005"), "C29"),
        BestKnownEntry(listOf("NGC 7331"), "C30"),
        BestKnownEntry(listOf("IC 405"), "C31", nameIntl = "Flaming Star Nebula"),
        BestKnownEntry(listOf("NGC 4631"), "C32", nameIntl = "Whale Galaxy"),
        BestKnownEntry(listOf("NGC 6992"), "C33", nameIntl = "Eastern Veil Nebula"),
        BestKnownEntry(listOf("NGC 6960"), "C34", nameIntl = "Western Veil Nebula"),
        BestKnownEntry(listOf("NGC 4889"), "C35"),
        BestKnownEntry(listOf("NGC 4559"), "C36"),
        BestKnownEntry(listOf("NGC 6885"), "C37"),
        BestKnownEntry(listOf("NGC 4565"), "C38", nameIntl = "Needle Galaxy"),
        BestKnownEntry(listOf("NGC 2392"), "C39", nameIntl = "Eskimo Nebula"),
        BestKnownEntry(listOf("NGC 3626"), "C40"),
        // "Melotte 25" allein matcht im Katalog nichts -- die dort tatsächlich vorkommende Schreibweise
        // ist die Abkürzung "Mel 25" (per echtem Katalog-Abgleich verifiziert), hier zusätzlich behalten.
        BestKnownEntry(listOf("Mel 25", "Melotte 25"), "C41", nameIntl = "Hyades", nameDe = "Hyaden"),
        BestKnownEntry(listOf("NGC 7006"), "C42"),
        BestKnownEntry(listOf("NGC 7814"), "C43"),
        BestKnownEntry(listOf("NGC 7479"), "C44"),
        BestKnownEntry(listOf("NGC 5248"), "C45"),
        BestKnownEntry(listOf("NGC 2261"), "C46", nameIntl = "Hubble's Variable Nebula"),
        BestKnownEntry(listOf("NGC 6934"), "C47"),
        BestKnownEntry(listOf("NGC 2775"), "C48"),
        BestKnownEntry(listOf("NGC 2237"), "C49", nameIntl = "Rosette Nebula", nameDe = "Rosettennebel"),
        BestKnownEntry(listOf("NGC 2244"), "C50", nameIntl = "Rosette Cluster"),
        BestKnownEntry(listOf("IC 1613"), "C51"),
        BestKnownEntry(listOf("NGC 4697"), "C52"),
        BestKnownEntry(listOf("NGC 3115"), "C53", nameIntl = "Spindle Galaxy"),
        BestKnownEntry(listOf("NGC 2506"), "C54"),
        BestKnownEntry(listOf("NGC 7009"), "C55", nameIntl = "Saturn Nebula"),
        BestKnownEntry(listOf("NGC 246"), "C56", nameIntl = "Skull Nebula"),
        BestKnownEntry(listOf("NGC 6822"), "C57", nameIntl = "Barnard's Galaxy"),
        BestKnownEntry(listOf("NGC 2360"), "C58"),
        BestKnownEntry(listOf("NGC 3242"), "C59", nameIntl = "Ghost of Jupiter"),
        // C60/C61 Antennae -- ZWEI reale Galaxien, s. Klassenkommentar. Ebenfalls kein Group-Label.
        BestKnownEntry(listOf("NGC 4038"), "C60"),
        BestKnownEntry(listOf("NGC 4039"), "C61"),
        BestKnownEntry(listOf("NGC 247"), "C62"),
        BestKnownEntry(listOf("NGC 7293"), "C63", nameIntl = "Helix Nebula", nameDe = "Helixnebel"),
        BestKnownEntry(listOf("NGC 2362"), "C64"),
        BestKnownEntry(listOf("NGC 253"), "C65", nameIntl = "Sculptor Galaxy"),
        BestKnownEntry(listOf("NGC 5694"), "C66"),
        BestKnownEntry(listOf("NGC 1097"), "C67"),
        BestKnownEntry(listOf("NGC 6729"), "C68"),
        BestKnownEntry(listOf("NGC 6302"), "C69", nameIntl = "Bug Nebula"),
        BestKnownEntry(listOf("NGC 300"), "C70"),
        BestKnownEntry(listOf("NGC 2477"), "C71"),
        BestKnownEntry(listOf("NGC 55"), "C72"),
        BestKnownEntry(listOf("NGC 1851"), "C73"),
        BestKnownEntry(listOf("NGC 3132"), "C74", nameIntl = "Eight-Burst Nebula"),
        BestKnownEntry(listOf("NGC 6124"), "C75"),
        BestKnownEntry(listOf("NGC 6231"), "C76"),
        BestKnownEntry(listOf("NGC 5128"), "C77", nameIntl = "Centaurus A"),
        BestKnownEntry(listOf("NGC 6541"), "C78"),
        BestKnownEntry(listOf("NGC 3201"), "C79"),
        BestKnownEntry(listOf("NGC 5139"), "C80", nameIntl = "Omega Centauri"),
        BestKnownEntry(listOf("NGC 6352"), "C81"),
        BestKnownEntry(listOf("NGC 6193"), "C82"),
        BestKnownEntry(listOf("NGC 4945"), "C83"),
        BestKnownEntry(listOf("NGC 5286"), "C84"),
        BestKnownEntry(listOf("IC 2391"), "C85"),
        BestKnownEntry(listOf("NGC 6397"), "C86"),
        BestKnownEntry(listOf("NGC 1261"), "C87"),
        BestKnownEntry(listOf("NGC 5823"), "C88"),
        BestKnownEntry(listOf("NGC 6087"), "C89"),
        BestKnownEntry(listOf("NGC 2867"), "C90"),
        BestKnownEntry(listOf("NGC 3532"), "C91"),
        BestKnownEntry(listOf("NGC 3372"), "C92", nameIntl = "Carina Nebula", nameDe = "Eta-Carinae-Nebel"),
        BestKnownEntry(listOf("NGC 6752"), "C93"),
        BestKnownEntry(listOf("NGC 4755"), "C94", nameIntl = "Jewel Box"),
        BestKnownEntry(listOf("NGC 6025"), "C95"),
        BestKnownEntry(listOf("NGC 2516"), "C96"),
        BestKnownEntry(listOf("NGC 3766"), "C97"),
        BestKnownEntry(listOf("NGC 4609"), "C98"),
        // C99 = Coalsack. Der frühere Platzhalter `__UNRESOLVED_C99_COALSACK__` ist überholt: der
        // Katalog enthält den Kohlensack inzwischen unter ZWEI Bezeichnungen, beide per Koordinaten
        // bestätigt (Sollposition RA 12,88h / Dec -63,0):
        //   "C 99"     -> RA 12,833h / Dec -62,50, type=dn, 430x300'
        //   "Coalsack" -> RA 12,883h / Dec -63,00, type=dn, 400x300'
        // Beide bezeichnen dieselbe Dunkelwolke (0,6° auseinander, bei 400'+ Ausdehnung dieselbe
        // Region) -- deshalb EIN Eintrag mit beiden matchIds, nicht zwei.
        // Unverändert gültig: KEINE Verknüpfung mit NGC 3372 (das ist C92/Carina, RA 10,75h/Dec -59,9 --
        // ein anderes, unabhängiges Objekt; eine solche Verknüpfung wäre eine falsche Identität).
        BestKnownEntry(listOf("C 99", "Coalsack"), "C99", nameIntl = "Coalsack Nebula", nameDe = "Kohlensack"),
        BestKnownEntry(listOf("IC 2944"), "C100", nameIntl = "Lambda Centauri Nebula"),
        BestKnownEntry(listOf("NGC 6744"), "C101"),
        BestKnownEntry(listOf("IC 2602"), "C102", nameIntl = "Southern Pleiades"),
        BestKnownEntry(listOf("NGC 2070"), "C103", nameIntl = "Tarantula Nebula", nameDe = "Tarantelnebel"),
        BestKnownEntry(listOf("NGC 362"), "C104"),
        BestKnownEntry(listOf("NGC 4833"), "C105"),
        BestKnownEntry(listOf("NGC 104"), "C106", nameIntl = "47 Tucanae"),
        BestKnownEntry(listOf("NGC 6101"), "C107"),
        BestKnownEntry(listOf("NGC 4372"), "C108"),
        BestKnownEntry(listOf("NGC 3195"), "C109"),
    )

    /**
     * 31 zusätzliche Astrofotografie-Ziele, exakt wie vom Nutzer 2026-09-01 vorgegeben (Abschnitt B).
     * Absichtlich KEINE Zusammenführung der explizit gewarnten Nachbar-/Teilobjekte (B33/IC434,
     * NGC1973+1975/NGC1977, IC1396A/IC1396, DWB111+DWB119/Simeis57, IC1805/IC1848 vs. deren
     * eingebettete Cluster) -- jeder Eintrag matcht NUR seine eigene, explizit genannte Bezeichnung.
     */
    private val extraTargets: List<BestKnownEntry> = listOf(
        BestKnownEntry("B 33", nameIntl = "Horsehead Nebula", nameDe = "Pferdekopfnebel"),
        BestKnownEntry("NGC 2024", nameIntl = "Flame Nebula", nameDe = "Flammennebel"),
        BestKnownEntry("NGC 1977", nameIntl = "Running Man Nebula", nameDe = "Running-Man-Nebel"),
        // Alias-Fix 2026-09-03 (Gerätebefund "5 fehlende Best-Known-Objekte"): "IC 2118" existiert im
        // verwendeten Katalog NACHWEISLICH NICHT (weder als id noch als desig) -- derselbe Nebel steht
        // dort als "NGC 1909" (type=rn, dim=180x60', RA 5,033h/Dec -7,90°, per Koordinaten- und
        // Größenabgleich als der Hexenkopf bestätigt). Der bisher einzige matchId lief deshalb immer ins
        // Leere. Kein neues Objekt, kein Duplikat -- nur die zweite Bezeichnung DESSELBEN Eintrags.
        // (Der ebenfalls geprüfte Kandidat "vdB 38" liegt bei RA 5,367h/Dec +8,30° -- völlig andere
        // Himmelsposition, 3x3', also NICHT der Hexenkopf; bewusst nicht aufgenommen.)
        BestKnownEntry(listOf("IC 2118", "NGC 1909"), "IC 2118", nameIntl = "Witch Head Nebula", nameDe = "Hexenkopfnebel"),
        BestKnownEntry("Sh2-276", nameIntl = "Barnard's Loop", nameDe = "Barnards Schleife"),
        BestKnownEntry("NGC 2264", nameIntl = "Cone Nebula / Christmas Tree Cluster", nameDe = "Konusnebel / Weihnachtsbaum-Sternhaufen"),
        // Sh2-190 = dieselbe physische Nebel-Region wie IC 1805 (NICHT der eingebettete Sternhaufen
        // Melotte 15 -- dessen Nichtgleichsetzung ist der eigentliche Warnhinweis aus Abschnitt B, hier
        // unberührt); "Sh2-190" allein matcht im Katalog nichts, "IC 1805" per Abgleich verifiziert.
        BestKnownEntry(listOf("Sh2-190", "IC 1805"), "Sh2-190", nameIntl = "Heart Nebula", nameDe = "Herznebel"),
        // Analog zu Sh2-190/Heart Nebula oben: Sh2-199 = dieselbe Region wie IC 1848 (nicht der
        // eingebettete Sternhaufen), "IC 1848" per Abgleich verifiziert vorhanden.
        BestKnownEntry(listOf("Sh2-199", "IC 1848"), "Sh2-199", nameIntl = "Soul Nebula", nameDe = "Seelennebel"),
        BestKnownEntry("IC 5070", nameIntl = "Pelican Nebula", nameDe = "Pelikannebel"),
        BestKnownEntry("IC 410", nameIntl = "Tadpoles Nebula", nameDe = "Kaulquappennebel"),
        BestKnownEntry("IC 443", nameIntl = "Jellyfish Nebula", nameDe = "Quallennebel"),
        BestKnownEntry("NGC 2174", nameIntl = "Monkey Head Nebula", nameDe = "Affenkopfnebel"),
        BestKnownEntry(listOf("Sh2-240", "Simeis 147"), "Sh2-240", nameIntl = "Spaghetti Nebula", nameDe = "Spaghettinebel"),
        BestKnownEntry("NGC 2359", nameIntl = "Thor's Helmet", nameDe = "Thors Helm"),
        BestKnownEntry("IC 2177", nameIntl = "Seagull Nebula", nameDe = "Möwennebel"),
        // "Sh2-142" fehlt im Katalog; derselbe Komplex steht dort als "NGC 7380" -- per Koordinaten
        // bestätigt (RA 22,789h / Dec +58,132 gegen die Sollposition RA 22,783h / Dec +58,1, also 0,05°
        // Abweichung). Entscheidend für die Eignung: der Katalogeintrag hat type=**sfr**
        // (Sternentstehungsregion, 25x20'), bezeichnet also die NEBELREGION und nicht nur den
        // eingebetteten Sternhaufen -- exakt dieselbe Konstellation wie bei Sh2-190/IC 1805 und
        // Sh2-199/IC 1848 weiter oben, die aus demselben Grund bereits so geführt werden.
        BestKnownEntry(listOf("Sh2-142", "NGC 7380"), "Sh2-142", nameIntl = "Wizard Nebula", nameDe = "Zauberernebel"),
        // Alias-Fix 2026-09-03 (s. IC 2118 oben): "IC 1396A" existiert im Katalog NICHT. Der
        // Elefantenrüssel selbst steht dort als "vdB 142" (type=rn, RA 21,611h/Dec +57,50° -- deckt sich
        // mit der bekannten Position des Rüssels). BEWUSST NICHT "IC 1396" als Alias aufgenommen: das ist
        // die UMGEBENDE Sternentstehungsregion (type=sfr, mag 3,5) und damit ein anderes, größeres
        // Objekt -- genau die Nachbar-/Teilobjekt-Verwechslung, vor der die Nutzer-Vorgabe aus Abschnitt B
        // (s. Kommentar über dieser Liste) für exakt dieses Paar ausdrücklich warnt.
        BestKnownEntry(listOf("IC 1396A", "vdB 142"), "IC 1396A", nameIntl = "Elephant's Trunk Nebula", nameDe = "Elefantenrüsselnebel"),
        BestKnownEntry("IC 1318", nameIntl = "Gamma Cygni Nebula", nameDe = "Gamma-Cygni-Nebel"),
        BestKnownEntry("Sh2-101", nameIntl = "Tulip Nebula", nameDe = "Tulpennebel"),
        BestKnownEntry("Sh2-136", nameIntl = "Ghost Nebula", nameDe = "Geisternebel"),
        BestKnownEntry("IC 63", nameIntl = "Ghost of Cassiopeia", nameDe = null),
        BestKnownEntry("IC 4592", nameIntl = "Blue Horsehead Nebula", nameDe = "Blauer Pferdekopfnebel"),
        BestKnownEntry("IC 4604", nameIntl = "Rho Ophiuchi Nebula", nameDe = "Rho-Ophiuchi-Nebel"),
        BestKnownEntry("NGC 6334", nameIntl = "Cat's Paw Nebula", nameDe = "Katzenpfotennebel"),
        BestKnownEntry("NGC 6357", nameIntl = "Lobster Nebula", nameDe = "Hummernebel"),
        BestKnownEntry(listOf("IC 4628", "Gum 56"), "IC 4628", nameIntl = "Prawn Nebula", nameDe = "Garnelennebel"),
        BestKnownEntry("NGC 3576", nameIntl = "Statue of Liberty Nebula", nameDe = null),
        BestKnownEntry("NGC 3628", nameIntl = "Hamburger Galaxy", nameDe = "Hamburger-Galaxie"),
        // WEITERHIN NICHT KATALOGAUFLÖSBAR (Prüfung 2026-09-04, bewusst nicht erzwungen):
        // "Simeis 57"/"DWB 111"/"DWB 119" fehlen alle drei im Katalog. Der vorgeschlagene Kandidat
        // "LBN 251" wurde per Koordinatenabgleich VERWORFEN: er liegt bei RA 20,287h / Dec +41,90,
        // die Sollposition des Propellernebels ist RA ~20,27h / Dec +43,68 -- rund **1,8° Abstand**
        // bei einer Objektgröße von nur 45x25'. Das ist mehr als das Doppelte der eigenen Ausdehnung
        // und damit keine belastbare Identität. Nächstes Katalogobjekt zur Sollposition ist "LBN 278"
        // (0,70°, 30x20') -- ebenfalls zu weit und ohne bestätigte Kreuzidentifikation. Die
        // LBN-Vermessung segmentiert diese Region anders als die Simeis/DWB-Listen; ohne externe
        // Cross-ID-Quelle waere jede Zuordnung geraten. Bleibt bewusst ohne Treffer, s.
        // bestknown_catalog_audit.
        BestKnownEntry(listOf("Simeis 57", "DWB 111", "DWB 119"), "Simeis 57", nameIntl = "Propeller Nebula", nameDe = "Propellernebel"),
        BestKnownEntry("NGC 1788", nameIntl = "Fox Face Nebula", nameDe = null),
        BestKnownEntry("Sh2-129", nameIntl = "Flying Bat Nebula", nameDe = "Fledermausnebel"),
        // Drei Nachträge 2026-09-03 (Gerätebefund): diese Objekte fehlten in der Liste ganz -- sie sind
        // im Katalog vorhanden (per id/desig + Koordinaten geprüft), waren aber nie als Best-Known-Ziel
        // erfasst. KEINE Alias-/Normalisierungsfrage, echte Ergänzungen. Damit wächst die kuratierte
        // Menge von 250 auf 253 Einträge.
        // RA 0,019h / Dec +67,42°, type=bn -- kein etablierter deutscher/internationaler Eigenname
        // (die geläufige Bezeichnung IST "NGC 7822"), daher bewusst KEIN Anzeigename: der Katalogname
        // bleibt unverändert stehen (s. resolveDisplayName/Abschnitt C). Das benachbarte "Ced 214" ist
        // ein eigenes Objekt und wird NICHT mit eingemischt.
        BestKnownEntry("NGC 7822"),
        // RA 0,874h / Dec +56,57°, type=bn, 35x30'.
        BestKnownEntry("NGC 281", nameIntl = "Pac-Man Nebula", nameDe = "Pac-Man-Nebel"),
        // RA 4,055h / Dec +36,42°, type=bn, 160x40'.
        BestKnownEntry("NGC 1499", nameIntl = "California Nebula", nameDe = "Californianebel"),
    )

    val entries: List<BestKnownEntry> = messier + caldwell + extraTargets
}
