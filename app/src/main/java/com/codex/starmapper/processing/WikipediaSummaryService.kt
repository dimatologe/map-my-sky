package com.codex.starmapper.processing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Kurzbeschreibung von Wikipedias REST-Summary-API. [pageUrl] optional für einen "Mehr erfahren"-Link.
 * [wikibaseItem] ist die Wikidata-Q-ID des Artikels (falls vorhanden) -- Grundlage für die
 * Online-Fakten aus [WikidataFactsService] UND für die sprachübergreifende Titel-Auflösung hier.
 */
data class WikipediaSummary(val extract: String, val pageUrl: String?, val wikibaseItem: String? = null)

/**
 * Markiert einen Netzwerk-/Server-Aussetzer (Timeout, kein Internet, 5xx, ...) -- im Unterschied zu
 * einem inhaltlich geprüften "kein Artikel" (404, Begriffsklärung, Themen-Gate). Nur Letzteres darf
 * dauerhaft in [WikipediaSummaryService.results] als `null` gecacht werden; ein Aussetzer muss beim
 * nächsten Aufruf erneut versucht werden können, sonst bleibt ein einmaliger Blackout für den Rest der
 * Bildsitzung an der falschen Sprache hängen (live beobachtet: M18 zeigte nach einem Aussetzer beim
 * deutschen Abruf dauerhaft Englisch, obwohl der deutsche Artikel nachweislich existiert).
 */
private class TransientFetchFailure(cause: Throwable) : Exception(cause)

/**
 * Lädt kurze Objekt-Zusammenfassungen von Wikipedias öffentlicher REST-Summary-API (kein API-Key,
 * kein Vertrag nötig) -- rein für die Region-Info-Funktion. Es gehen ausschließlich astronomische
 * Bezeichnungen raus, keine Bild-/Standortdaten. In-Memory-Sitzungscache (Compose-State, damit
 * offene Dialog-Zeilen automatisch nachrecomposen, sobald ein Ergebnis eintrifft); kein
 * Diskspeicher -- [clear] wird bei Bildwechsel aufgerufen.
 *
 * Cache-Schlüssel ist `"$cacheKey|$lang"` (NICHT nur `cacheKey`) -- ein Objekt kann in mehreren
 * Sprachen unterschiedliche Kurztexte haben, und nach einem Sprachwechsel muss der alte Treffer nicht
 * länger als "schon versucht" gelten, sonst bleibt z.B. ein englischer Kurztext nach Umschalten auf
 * Deutsch fälschlich stehen.
 */
object WikipediaSummaryService {
    // null = "kein Artikel gefunden" (fester Cache-Treffer, kein erneuter Versuch). Fehlt der
    // Schlüssel komplett, wurde noch nie versucht.
    val results = mutableStateMapOf<String, WikipediaSummary?>()
    private val inFlight = HashSet<String>()
    // Objekte (cacheKey, NICHT cacheKey|lang -- der Zustand ist pro Objekt, nicht pro Sprache), bei
    // denen [ensureFetchedChain] alle Versuche ausgeschoepft hat, OHNE fuer jede Sprache ein
    // definitives Ergebnis (Treffer oder bestaetigt "kein Artikel") zu bekommen -- UI zeigt dafuer
    // "Info laden" (antippen loest EINEN gezielten Neuversuch fuer genau dieses Objekt aus) statt
    // entweder endlos "wird geladen" oder faelschlich dauerhaft leer zu bleiben (Nutzerwunsch
    // 2026-08-18, ersetzt die vorherige Loesung "nach Ausschoepfen als kein Treffer cachen").
    private val gaveUp = mutableStateSetOf<String>()

    private fun key(cacheKey: String, lang: String) = "$cacheKey|$lang"

    /** Bester bisher gefundener Treffer über [langsInOrder] hinweg -- für die Anzeige. */
    fun bestResult(cacheKey: String, langsInOrder: List<String>): WikipediaSummary? {
        for (lang in langsInOrder.distinct()) {
            results[key(cacheKey, lang)]?.let { return it }
        }
        return null
    }

    /** true, wenn [ensureFetchedChain] fuer [cacheKey] alle Versuche ausgeschoepft hat, ohne fertig zu
     *  werden -- UI bietet dafuer einen manuellen "Info laden"-Neuversuch an (s. [gaveUp]). */
    fun hasGivenUp(cacheKey: String): Boolean = cacheKey in gaveUp

    /** true, sobald ein Treffer feststeht ODER alle Sprachen der Kette erfolglos versucht wurden. */
    fun chainComplete(cacheKey: String, langsInOrder: List<String>): Boolean {
        val langs = langsInOrder.distinct()
        if (bestResult(cacheKey, langs) != null) return true
        return langs.all { results.containsKey(key(cacheKey, it)) }
    }

    /**
     * Versucht [titleCandidates] der Reihe nach in [langsInOrder] (App-Sprache -> eingestellte
     * Fallback-Sprache -> Englisch, dedupliziert von der Aufrufseite) -- bricht beim ersten Treffer ab.
     * Für eine Sprache, die über die geratenen Kandidaten NICHTS findet, aber für die bereits eine
     * Wikidata-Q-ID aus einem früheren Treffer bekannt ist, wird zusätzlich der ECHTE Artikeltitel dieser
     * Sprache über Wikidata-Sitelinks nachgeschlagen (live verifiziert nötig: "Sirius" in lateinischer
     * Schreibweise landet auf Russisch auf einer Begriffsklärungsseite, der echte Artikel heißt
     * "Сириус" -- geratene lateinische Titel scheitern systematisch bei anderen Schriftsystemen).
     *
     * Prüft VOR jedem Netzwerkversuch, ob [context] überhaupt eine Internetverbindung meldet (s.
     * [hasNetworkConnection]) -- ohne diese Absicherung durchläuft ein Gerät ohne Netz (z.B. beim
     * Astrofoto-Test unter freiem Himmel ohne Empfang) erst den kompletten, aus mehreren Sprachen/
     * Titel-Kandidaten/internen Wiederholungen bestehenden Zeitplan (mehrere Minuten) BEVOR die Zeile
     * "Info laden" zeigt -- ein Antippen von "Info laden" wiederholt exakt denselben aussichtslosen,
     * minutenlangen Ablauf, was sich für den Nutzer wie ein kompletter Ausfall anfühlt (Nutzerbefund
     * 2026-08-19: "klickt man auf Laden, passiert aber wiederum nichts"). Bei fehlendem Netz wird
     * [cacheKey] sofort ohne jeden Verbindungsversuch als [gaveUp] markiert.
     *
     * NUR EIN Durchlauf durch [attemptFetchChain] pro Aufruf (frühere Fassung wiederholte den GESAMTEN
     * Kettendurchlauf zusätzlich noch dreifach automatisch -- zusammen mit dem internen Retry pro
     * Sprache/Titel in [fetchSummaryWithRetry] ergab das eine mehrfach verschachtelte, sich
     * multiplizierende Wiederholung mit einer Worst-Case-Dauer von ~20 Minuten pro Objekt, s.
     * Commit-Historie). Der externe, EINMALIGE Retry-Mechanismus IST bereits das Antippen von
     * "Info laden" selbst -- ein weiterer interner Automatismus obendrauf war unnötig und die
     * eigentliche Ursache der gefühlten Blockade. Nach Ausschoepfen des einen Durchlaufs OHNE fertig
     * zu werden landet [cacheKey] in [gaveUp] -- die UI bietet dafuer "Info laden" an, ein erneuter
     * Aufruf DIESER Funktion (z.B. per Antippen) versucht wieder von vorn (s. [hasGivenUp]). Bewusst
     * NICHT als "kein Treffer" (null) fest gecacht (s. [TransientFetchFailure]) -- das waere fuer
     * einen reinen Netzwerk-Aussetzer dauerhaft falsch und liesse keinen gezielten Neuversuch mehr zu.
     */
    suspend fun ensureFetchedChain(context: Context, cacheKey: String, titleCandidates: List<String>, langsInOrder: List<String>) {
        val langs = langsInOrder.distinct()
        if (titleCandidates.isEmpty()) return
        gaveUp.remove(cacheKey)
        if (!hasNetworkConnection(context)) {
            gaveUp += cacheKey
            return
        }
        if (!chainComplete(cacheKey, langs)) {
            attemptFetchChain(cacheKey, titleCandidates, langs)
        }
        if (!chainComplete(cacheKey, langs)) {
            gaveUp += cacheKey
        }
    }

    /** Grobe, aber schnelle Vorabprüfung -- meldet nur, ob das System überhaupt ein Netz mit
     *  Internet-Fähigkeit als aktiv führt (kein tatsächlicher Erreichbarkeits-Ping, s. [ensureFetchedChain]). */
    private fun hasNetworkConnection(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Ein einzelner Kettendurchlauf -- s. [ensureFetchedChain] fuer die aeussere Wiederholung darum. */
    private suspend fun attemptFetchChain(cacheKey: String, titleCandidates: List<String>, langs: List<String>) {
        var qid = langs.firstNotNullOfOrNull { results[key(cacheKey, it)]?.wikibaseItem }
        for (lang in langs) {
            val k = key(cacheKey, lang)
            if (results.containsKey(k)) continue
            var found = fetchForLang(cacheKey, titleCandidates, lang)
            if (found == null && qid != null) {
                val nativeTitle = resolveNativeTitle(qid, lang)
                if (nativeTitle != null && nativeTitle !in titleCandidates) {
                    try {
                        found = fetchSummaryWithRetry(lang, nativeTitle)
                        results[k] = found
                    } catch (e: TransientFetchFailure) {
                        // Aussetzer: nicht cachen, s. fetchForLang unten -- unentschieden lassen.
                    }
                }
            }
            // Letzter Versuch, wenn KEIN exakter Titel existiert: manche Katalogobjekte haben keinen
            // eigenen Artikel, werden aber ausführlich in einem anders benannten Übersichtsartikel
            // behandelt (live verifiziert 2026-08-20: Sh2-109 hat keinen eigenen Artikel, wird aber im
            // Artikel "Cygnus Molecular Nebula Complex" als dessen markanteste Struktur beschrieben).
            // fetchForLang hat [k] an dieser Stelle bereits auf null gesetzt (Klassenkommentar oben,
            // "fester Cache-Treffer") -- wird hier gezielt überschrieben, sobald ein besserer,
            // Snippet-bestätigter Treffer feststeht.
            if (found == null) {
                found = titleCandidates.firstNotNullOfOrNull { candidate ->
                    try {
                        searchFallback(lang, candidate)
                    } catch (e: TransientFetchFailure) {
                        null
                    }
                }
                if (found != null) results[k] = found
            }
            qid = qid ?: found?.wikibaseItem
            if (found != null) break
        }
    }

    /** Einzelsprachiger Abruf mit In-Flight-Sperre; liefert (und cached) das Ergebnis für `cacheKey|lang`. */
    private suspend fun fetchForLang(cacheKey: String, titleCandidates: List<String>, lang: String): WikipediaSummary? {
        val k = key(cacheKey, lang)
        if (results.containsKey(k)) return results[k]
        synchronized(inFlight) {
            if (!inFlight.add(k)) return results[k]
        }
        try {
            var found: WikipediaSummary? = null
            for (title in titleCandidates) {
                found = try {
                    fetchSummaryWithRetry(lang, title)
                } catch (e: TransientFetchFailure) {
                    // Aussetzer: NICHT als "kein Artikel" cachen (s. Klassenkommentar oben) -- diese
                    // Sprache bleibt unentschieden, der nächste Aufruf versucht sie erneut.
                    return null
                }
                if (found != null) break
            }
            results[k] = found
            return found
        } finally {
            synchronized(inFlight) { inFlight.remove(k) }
        }
    }

    /**
     * Letzter Versuch, wenn KEIN Titel aus dem exakten Abgleich existiert -- nutzt Wikipedias eigene
     * Volltextsuche statt exaktem Titel-Abgleich. WICHTIG (live verifiziert 2026-08-20): die
     * Trefferliste selbst ist NICHT vertrauenswürdig -- eine Suche nach "Sh2 109" lieferte u.a. auch
     * Treffer zu Sh2-113/Sh2-142/Sh2-277/Sh2-184 (alles ANDERE Sharpless-Nummern, nur lose textlich
     * ähnlich). Einzige verlässliche Bestätigung: Wikipedias eigenes Such-SNIPPET muss die gesuchte
     * Bezeichnung SELBST (normalisiert) enthalten -- das ist die Stelle, an der die Suche den Treffer
     * fand, und war für Sh2-109 vorhanden, obwohl die REST-Kurzbeschreibung (extract) die Bezeichnung
     * GAR NICHT erwähnte (ebenfalls live geprüft) -- der Abgleich muss also gegen das Snippet laufen,
     * nicht gegen [WikipediaSummary.extract]. Für den ersten bestätigten Treffer läuft
     * [fetchSummary] (EIN Versuch, KEIN [fetchSummaryWithRetry]) -- damit gilt automatisch weiterhin
     * das astronomische Themen-Gate ([looksAstronomical]) UND die Begriffsklärungs-Prüfung aus
     * [fetchSummary], ohne Code-Verdopplung. Bewusst OHNE Wiederholungsversuche und auf höchstens
     * [SEARCH_FALLBACK_MAX_ATTEMPTS] geprüfte Treffer begrenzt (Nutzerbefund 2026-08-20: dieser
     * ohnehin schon letzte Rückfallversuch darf die Gesamtwartezeit eines EINZELNEN, gezielten
     * "Info laden"-Tastendrucks nicht durch mehrere gestaffelte Wiederholungsversuche unnötig
     * aufblähen -- ein einzelner Fehlversuch bedeutet hier einfach "nächster Kandidat", kein erneutes
     * Warten auf denselben).
     */
    private suspend fun searchFallback(lang: String, designation: String): WikipediaSummary? {
        if (designation.isBlank()) return null
        val hits = runCatching { searchTitles(lang, designation) }.getOrDefault(emptyList())
        var attempts = 0
        for (hit in hits) {
            if (!snippetMentionsDesignation(hit.snippet, designation)) continue
            if (attempts >= SEARCH_FALLBACK_MAX_ATTEMPTS) break
            attempts++
            val summary = try {
                fetchSummary(lang, hit.title)
            } catch (e: TransientFetchFailure) {
                null
            }
            if (summary != null) return summary
        }
        return null
    }

    private data class SearchHit(val title: String, val snippet: String)

    private suspend fun searchTitles(lang: String, query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json" +
                "&srlimit=3&srsearch=$encodedQuery",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "MapMySky-Android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val hits = JSONObject(body).optJSONObject("query")?.optJSONArray("search")
                ?: return@withContext emptyList()
            (0 until hits.length()).map { i ->
                val hit = hits.getJSONObject(i)
                SearchHit(hit.optString("title"), hit.optString("snippet"))
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    /** Entfernt Wikipedias eigene Fundstellen-Hervorhebung (z.B. <span class="searchmatch">) aus dem
     *  Snippet, für einen sauberen Text-Abgleich darunter. */
    private fun stripSearchMarkup(snippet: String): String = snippet.replace(Regex("<[^>]+>"), "")

    private fun normalizeForSnippetMatch(s: String): String = s.uppercase().replace(Regex("[\\s\\-]"), "")

    /** true, wenn [snippet] (Wikipedia-Suchtreffer) die gesuchte [designation] selbst enthält -- s.
     *  [searchFallback]-Kommentar, warum das die einzig verlässliche Bestätigung ist. Grenzprüfung:
     *  keine direkt anschließende weitere Ziffer, damit "NGC 6960" nicht fälschlich "NGC 69601" träfe. */
    private fun snippetMentionsDesignation(snippet: String, designation: String): Boolean {
        val haystack = normalizeForSnippetMatch(stripSearchMarkup(snippet))
        val target = normalizeForSnippetMatch(designation)
        if (target.isBlank()) return false
        val index = haystack.indexOf(target)
        if (index < 0) return false
        val after = index + target.length
        return after >= haystack.length || !haystack[after].isDigit()
    }

    /** Bildwechsel -> Cache verwerfen (Ergebnisse hängen am jeweils gelösten Bildfeld). */
    fun clear() {
        results.clear()
        gaveUp.clear()
        synchronized(inFlight) { inFlight.clear() }
    }

    /**
     * [fetchSummary] mit kurzen Wiederholungsversuchen bei einem Aussetzer (s. [TransientFetchFailure]) --
     * OHNE das gäbe es innerhalb EINER Dialog-Sitzung keinen Auslöser für den in [fetchForLang] versprochenen
     * "nächsten Aufruf" (der Aufrufer [RegionInfoDialog] startet den Abruf nur einmal pro geöffnetem Dialog,
     * s. dortiges `LaunchedEffect(entries, langChain)`) -- ein einzelner Aussetzer ließ die Kurztext-Zeile
     * sonst bis zum Schließen/Neuöffnen des Dialogs dauerhaft bei "wird geladen" hängen (Nutzerbefund
     * 2026-08-18, bei einem dicht mit Objekten belegten Feld -- mehrere gleichzeitige Abrufe treffen dort
     * öfter auf einen kurzen Netzwerk-/Server-Aussetzer als bei einem einzelnen Objekt). Die meisten
     * Aussetzer sind Sekundenbruchteile später bereits vorbei.
     */
    private suspend fun fetchSummaryWithRetry(lang: String, title: String): WikipediaSummary? {
        var lastFailure: TransientFetchFailure? = null
        repeat(RETRY_ATTEMPTS) { attempt ->
            try {
                return fetchSummary(lang, title)
            } catch (e: TransientFetchFailure) {
                lastFailure = e
                if (attempt < RETRY_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }
        throw lastFailure!!
    }

    private suspend fun fetchSummary(lang: String, title: String): WikipediaSummary? = withContext(Dispatchers.IO) {
        val encodedTitle = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        val url = URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedTitle")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "MapMySky-Android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.connect()
            // 404 (kein Artikel) ist der HÄUFIGSTE, erwartete Fall hier -- eindeutig kein Fehlerzustand,
            // sicher dauerhaft cachebar. Jeder ANDERE Nicht-2xx-Code (5xx, 429, ...) ist dagegen ein
            // Server-/Netzwerkproblem, kein Beleg für "Artikel existiert nicht" -- zählt als Aussetzer.
            if (connection.responseCode == 404) return@withContext null
            if (connection.responseCode !in 200..299) {
                throw TransientFetchFailure(java.io.IOException("HTTP ${connection.responseCode}"))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("type") == "disambiguation") return@withContext null
            val extract = json.optString("extract", "").trim()
            if (extract.isBlank()) return@withContext null
            val description = json.optString("description", "")
            // Themen-Absicherung: manche kurzen Katalog-Titel (z.B. "M16") sind auf Wikipedia KEINE
            // Begriffsklärungsseite, sondern ein direkter Redirect auf ein fachfremdes "primary topic"
            // (M16 -> Sturmgewehr statt Adlernebel) -- die obige disambiguation-Prüfung greift dann
            // nicht, der Treffer sieht wie ein normaler Artikel aus. Deshalb zusätzlich Positivliste:
            // nur akzeptieren, wenn Kurzbeschreibung (oder ersatzweise der Textanfang) erkennbar
            // astronomisches Vokabular enthält. Gilt für DSOs, Sterne UND Sternbilder gleichermaßen,
            // da dies der einzige gemeinsame Abrufpfad für alle drei ist -- und für alle 7 Sprachen.
            if (!looksAstronomical(lang, description, extract)) return@withContext null
            val pageUrl = json.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page")
                ?.takeIf { it.isNotBlank() }
            val wikibaseItem = json.optString("wikibase_item", "").takeIf { it.isNotBlank() }
            WikipediaSummary(extract, pageUrl, wikibaseItem)
        } catch (e: TransientFetchFailure) {
            throw e
        } catch (e: Exception) {
            // Verbindungs-/Parse-Störung (kein Internet, Timeout, o.ä.) -- ebenfalls ein Aussetzer,
            // kein "kein Artikel"-Befund (s. TransientFetchFailure-Klassenkommentar).
            throw TransientFetchFailure(e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Löst über die Wikidata-Q-ID [qid] den ECHTEN Wikipedia-Artikeltitel für [lang] auf (z.B. "Сириус"
     * für Russisch statt der geratenen lateinischen Schreibweise "Sirius") -- gegen die echte API
     * verifiziert (`wbgetentities&props=sitelinks&sitefilter={lang}wiki`, Antwortform
     * `entities.{qid}.sitelinks.{lang}wiki.title` bestätigt an Vega/Sirius).
     */
    private suspend fun resolveNativeTitle(qid: String, lang: String): String? = withContext(Dispatchers.IO) {
        val url = URL(
            "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$qid&props=sitelinks" +
                "&sitefilter=${lang}wiki&format=json",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "MapMySky-Android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optJSONObject("entities")?.optJSONObject(qid)
                ?.optJSONObject("sitelinks")?.optJSONObject("${lang}wiki")
                ?.optString("title", "")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Bewusst eine Positivliste (nicht z.B. ein hartcodiertes "M16 != Gewehr"-Ausschluss-Wörterbuch):
     * deckt so auch unbekannte künftige Titel-Kollisionen ab, nicht nur bekannte Einzelfälle. [haystack]
     * ist die kurze Wikidata-Kurzbeschreibung (z.B. "emission nebula in the constellation Serpens"),
     * ersatzweise der komplette Extract-Absatz, falls keine Kurzbeschreibung vorliegt. Fehlt eine
     * Sprache in der Wortliste (sollte nicht vorkommen, s.u.), wird sicherheitshalber Englisch genutzt.
     */
    private fun looksAstronomical(lang: String, description: String, extract: String): Boolean {
        val keywords = ASTRONOMY_KEYWORDS[lang] ?: ASTRONOMY_KEYWORDS_EN
        val haystack = description.ifBlank { extract }.lowercase()
        return keywords.any { haystack.contains(it) }
    }

    private val ASTRONOMY_KEYWORDS_EN = listOf(
        "star", "stars", "galaxy", "galaxies", "nebula", "nebulae", "cluster", "constellation",
        "supernova", "astronomical", "astronomy", "celestial", "cosmic", "interstellar", "nova",
        "pulsar", "quasar", "messier", "caldwell", "catalogue", "catalog", "deep-sky", "deep sky",
        "light-year", "light year", "parsec", "globular", "molecular cloud", "star-forming",
        "dwarf galaxy", "spiral galaxy", "elliptical galaxy", "lenticular", "irregular galaxy",
        "remnant", "milky way", "solar system", "observatory", "telescope", "right ascension",
        "declination", "night sky", "outer space", "universe", "astrophysical", "sharpless", "barnard",
    )

    private val ASTRONOMY_KEYWORDS_DE = listOf(
        "stern", "sterne", "galaxie", "galaxien", "nebel", "sternhaufen", "sternbild", "supernova",
        "astronomisch", "astronomie", "himmelsobjekt", "himmelskörper", "kosmisch", "interstellar",
        "nova", "pulsar", "quasar", "messier", "caldwell", "katalog", "kugelsternhaufen",
        "offener sternhaufen", "spiralgalaxie", "elliptische galaxie", "linsenförmige galaxie",
        "irreguläre galaxie", "zwerggalaxie", "planetarischer nebel", "reflexionsnebel", "dunkelnebel",
        "emissionsnebel", "sternentstehung", "milchstraße", "sonnensystem", "sternwarte", "teleskop",
        "rektaszension", "deklination", "nachthimmel", "weltraum", "universum", "lichtjahr", "parsec",
        "bogenminute",
    )

    private val ASTRONOMY_KEYWORDS_ZH = listOf(
        "恒星", "星系", "星云", "星团", "星座", "超新星", "天文", "天体", "宇宙", "星际", "新星", "脉冲星",
        "类星体", "梅西耶", "卡德维尔", "星表", "目录", "银河系", "太阳系", "天文台", "望远镜", "赤经", "赤纬",
        "夜空", "外太空", "天体物理", "光年", "秒差距", "球状星团", "疏散星团", "行星状星云", "反射星云",
        "暗星云", "发射星云", "矮星系", "螺旋星系", "椭圆星系", "透镜星系", "不规则星系", "遗迹", "恒星形成",
        "角分",
    )

    private val ASTRONOMY_KEYWORDS_ES = listOf(
        "estrella", "estrellas", "galaxia", "galaxias", "nebulosa", "cúmulo", "cúmulos", "constelación",
        "supernova", "astronómico", "astronomía", "celeste", "cósmico", "interestelar", "nova", "púlsar",
        "cuásar", "messier", "caldwell", "catálogo", "vía láctea", "sistema solar", "observatorio",
        "telescopio", "ascensión recta", "declinación", "cielo nocturno", "espacio exterior", "universo",
        "astrofísico", "año luz", "pársec", "cúmulo globular", "cúmulo abierto", "nebulosa planetaria",
        "nebulosa de reflexión", "nebulosa oscura", "nebulosa de emisión", "galaxia enana",
        "galaxia espiral", "galaxia elíptica", "lenticular", "galaxia irregular", "remanente",
        "formación estelar", "minuto de arco",
    )

    private val ASTRONOMY_KEYWORDS_RU = listOf(
        "звезда", "звёзды", "звезды", "галактика", "галактики", "туманность", "скопление", "созвездие",
        "сверхновая", "астрономический", "астрономия", "небесный", "космический", "межзвёздный",
        "пульсар", "квазар", "мессье", "колдуэлл", "каталог", "млечный путь", "солнечная система",
        "обсерватория", "телескоп", "прямое восхождение", "склонение", "ночное небо", "космос",
        "вселенная", "астрофизический", "световой год", "парсек", "шаровое скопление",
        "рассеянное скопление", "планетарная туманность", "отражательная туманность", "тёмная туманность",
        "эмиссионная туманность", "карликовая галактика", "спиральная галактика", "эллиптическая галактика",
        "линзовидная", "неправильная галактика", "остаток", "звездообразование", "угловая минута",
    )

    private val ASTRONOMY_KEYWORDS_AR = listOf(
        "نجم", "نجوم", "مجرة", "مجرات", "سديم", "سدم", "عنقود", "كوكبة", "مستعر أعظم", "فلكي",
        "علم الفلك", "سماوي", "كوني", "بين النجوم", "نجم متجدد", "نوفا", "نجم نابض", "كوازار", "ميسييه",
        "كالدويل", "فهرس", "كتالوج", "درب التبانة", "المجموعة الشمسية", "مرصد", "تلسكوب",
        "المطلع المستقيم", "الميل", "سماء الليل", "الفضاء الخارجي", "الكون", "فيزياء فلكية", "سنة ضوئية",
        "فرسخ فلكي", "عنقود كروي", "عنقود مفتوح", "سديم كوكبي", "سديم انعكاسي", "سديم مظلم",
        "سديم انبعاثي", "مجرة قزمة", "مجرة حلزونية", "مجرة إهليلجية", "عدسي", "مجرة غير منتظمة", "بقايا",
        "تشكل النجوم",
    )

    private val ASTRONOMY_KEYWORDS_JA = listOf(
        "恒星", "銀河", "星雲", "星団", "星座", "超新星", "天文学", "天体", "宇宙", "星間", "新星", "パルサー",
        "クエーサー", "メシエ", "コールドウェル", "カタログ", "目録", "天の川", "太陽系", "天文台", "望遠鏡",
        "赤経", "赤緯", "夜空", "宇宙空間", "天体物理学", "光年", "パーセク", "球状星団", "散開星団",
        "惑星状星雲", "反射星雲", "暗黒星雲", "輝線星雲", "矮小銀河", "渦巻銀河", "楕円銀河", "レンズ状銀河",
        "不規則銀河", "残骸", "星形成", "分角",
    )

    private val ASTRONOMY_KEYWORDS = mapOf(
        "en" to ASTRONOMY_KEYWORDS_EN,
        "de" to ASTRONOMY_KEYWORDS_DE,
        "zh" to ASTRONOMY_KEYWORDS_ZH,
        "es" to ASTRONOMY_KEYWORDS_ES,
        "ru" to ASTRONOMY_KEYWORDS_RU,
        "ar" to ASTRONOMY_KEYWORDS_AR,
        "ja" to ASTRONOMY_KEYWORDS_JA,
    )

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 6_000
    private const val RETRY_ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 900L
    private const val SEARCH_FALLBACK_MAX_ATTEMPTS = 2
}
