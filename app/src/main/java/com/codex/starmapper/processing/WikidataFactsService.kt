package com.codex.starmapper.processing

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Online-Zusatzfakten zu einem Objekt (Wikidata) -- rein additiv, jedes Feld einzeln optional. */
data class WikidataFacts(val distanceLightYears: Double?, val spectralType: String?)

/**
 * Markiert einen Netzwerk-/Server-Aussetzer, im Unterschied zu inhaltlich geprüften "keine Fakten"
 * (Property existiert nicht für dieses Objekt). Nur Letzteres darf dauerhaft gecacht werden -- s.
 * ausführliche Begründung bei der analogen Klasse TransientFetchFailure in WikipediaSummaryService.kt
 * (bewusst NICHT derselbe Name: beide sind datei-private Top-Level-Klassen im selben Package --
 * Kotlin/JVM erlaubt gleiche Top-Level-Klassennamen dort nicht, auch nicht bei "private").
 */
private class WikidataFetchFailure(cause: Throwable) : Exception(cause)

/**
 * Lädt einzelne, gezielte Fakten (Entfernung, Spektraltyp) von Wikidatas öffentlicher Claims-API --
 * nur für Objekte, für die [WikipediaSummaryService] bereits einen inhaltlich geprüften Treffer (samt
 * Wikidata-Q-ID) gefunden hat, s. `RegionInfoEntry.attemptWikidataFacts`. Bewusst je Property ein
 * eigener, gezielter wbgetclaims-Abruf statt eines Vollentitäts-Abrufs (wbgetentities/props=claims):
 * Letzterer liefert für ein einzelnes Objekt leicht >200 KB (Dutzende Fach-Properties, Referenzen,
 * Qualifikatoren, am Stern Wega selbst getestet) -- für ein paar Fakten unverhältnismäßig. Gezielte
 * Einzel-Property-Abrufe bleiben im Bereich weniger hundert Byte. In-Memory-Sitzungscache wie
 * [WikipediaSummaryService], geleert bei Bildwechsel via [clear].
 */
object WikidataFactsService {
    // null = "keine Fakten gefunden" (fester Cache-Treffer). Fehlt der Schlüssel, wurde noch nie versucht.
    val results = mutableStateMapOf<String, WikidataFacts?>()
    private val inFlight = HashSet<String>()

    suspend fun ensureFetched(qid: String) {
        if (qid.isBlank() || results.containsKey(qid)) return
        synchronized(inFlight) {
            if (!inFlight.add(qid)) return
        }
        try {
            val distance = fetchDistanceLightYears(qid)
            val spectralType = fetchSpectralType(qid)
            results[qid] = if (distance != null || spectralType != null) {
                WikidataFacts(distance, spectralType)
            } else {
                null
            }
        } catch (e: WikidataFetchFailure) {
            // Aussetzer: NICHT als "keine Fakten" cachen -- unentschieden lassen, nächster Aufruf
            // versucht es erneut (analog WikipediaSummaryService).
        } finally {
            synchronized(inFlight) { inFlight.remove(qid) }
        }
    }

    /** Bildwechsel -> Cache verwerfen (Ergebnisse hängen am jeweils gelösten Bildfeld). */
    fun clear() {
        results.clear()
        synchronized(inFlight) { inFlight.clear() }
    }

    /** P2583 "Entfernung von der Erde" -- Einheit steckt als Wikidata-Q-ID im `unit`-Feld, nicht als Text. */
    private suspend fun fetchDistanceLightYears(qid: String): Double? {
        val mainsnak = fetchBestClaim(qid, "P2583") ?: return null
        val value = mainsnak.optJSONObject("datavalue")?.optJSONObject("value") ?: return null
        val amount = value.optString("amount", "").removePrefix("+").toDoubleOrNull() ?: return null
        val unitId = value.optString("unit", "").substringAfterLast('/')
        // Nur real beobachtete, gegen die Wikidata-Label-API geprüfte Einheiten -- eine unbekannte
        // Einheit zählt bewusst als "kein Fakt" statt zu raten (eine falsche Umrechnung wäre schlimmer
        // als gar keine Angabe).
        val factor = LIGHT_YEAR_FACTORS[unitId] ?: return null
        return amount * factor
    }

    /** P215 "Spektraltyp" -- reiner String-Wert (z.B. "A0V"), gleiche rank-Filterung wie die Entfernung. */
    private suspend fun fetchSpectralType(qid: String): String? {
        val mainsnak = fetchBestClaim(qid, "P215") ?: return null
        val value = mainsnak.optJSONObject("datavalue")?.opt("value")
        return (value as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Holt alle Claims für [property] und wählt den besten Rang (preferred > normal, deprecated
     * ausgeschlossen). An echten Objekten beobachtet (Adlernebel-Entfernung, Andromeda-Entfernung,
     * Vega-Spektraltyp): mehrere konkurrierende/veraltete Werte pro Property sind der Normalfall, nicht
     * die Ausnahme -- ungefiltert würde man leicht einen überholten Wert erwischen.
     */
    private suspend fun fetchBestClaim(qid: String, property: String): JSONObject? = withContext(Dispatchers.IO) {
        val url = URL(
            "https://www.wikidata.org/w/api.php?action=wbgetclaims&entity=$qid&property=$property&format=json",
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
            // Anders als bei Wikipedia gibt es hier keinen "404 = eindeutig kein Fakt"-Fall (die Q-ID
            // stammt immer aus einem bereits bestätigten Wikipedia-Treffer, ist also gültig) -- jeder
            // Nicht-2xx-Code zählt daher als Aussetzer, nicht als bestätigtes "kein Fakt".
            if (connection.responseCode !in 200..299) {
                throw WikidataFetchFailure(java.io.IOException("HTTP ${connection.responseCode}"))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val claims = JSONObject(body).optJSONObject("claims")?.optJSONArray(property)
                ?: return@withContext null
            var preferred: JSONObject? = null
            var normal: JSONObject? = null
            for (i in 0 until claims.length()) {
                val claim = claims.optJSONObject(i) ?: continue
                val mainsnak = claim.optJSONObject("mainsnak") ?: continue
                if (mainsnak.optString("snaktype") != "value") continue
                when (claim.optString("rank")) {
                    "preferred" -> if (preferred == null) preferred = mainsnak
                    "normal" -> if (normal == null) normal = mainsnak
                    // "deprecated" bewusst übersprungen -- durch neuere Messungen überholte Werte.
                }
            }
            preferred ?: normal
        } catch (e: WikidataFetchFailure) {
            throw e
        } catch (e: Exception) {
            // Verbindungs-/Parse-Störung -- ebenfalls ein Aussetzer, s. WikidataFetchFailure oben.
            throw WikidataFetchFailure(e)
        } finally {
            connection.disconnect()
        }
    }

    // Wikidata-Q-ID der Maßeinheit -> Umrechnungsfaktor auf Lichtjahre. Jede einzelne Zeile gegen die
    // echte Wikidata-Label-API geprüft (nicht angenommen) -- u.a. dabei eine eigene Falschannahme
    // korrigiert (Lichtjahr/Parsec waren zunächst vertauscht).
    private val LIGHT_YEAR_FACTORS = mapOf(
        "Q531" to 1.0, // Lichtjahr
        "Q12129" to 3.26156, // Parsec
        "Q11929860" to 3261.56, // Kiloparsec
        "Q3773454" to 3_261_560.0, // Megaparsec
    )

    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 8_000
}
