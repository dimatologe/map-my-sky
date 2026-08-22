package com.codex.starmapper.data

import android.content.Context
import com.codex.starmapper.domain.CatalogStar
import com.codex.starmapper.domain.SkyPoint
import org.json.JSONObject

object StarCatalogAssetLoader {
    // Sprachkürzel, für die starnames.json eigene Felder führt (identisch zu AppLocale, ohne "en" --
    // das ist die internationale Standard-Form in "name", kein eigenes Feld nötig).
    private val LOCALIZED_TAGS = listOf("de", "zh", "es", "ru", "ar", "ja")

    fun load(context: Context, assetPath: String = "catalog/stars.6.json"): List<CatalogStar> {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val namesRoot = runCatching {
            JSONObject(context.assets.open("catalog/starnames.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        val features = root.getJSONArray("features")
        val stars = ArrayList<CatalogStar>(features.length())

        for (featureIndex in 0 until features.length()) {
            val feature = features.getJSONObject(featureIndex)
            val properties = feature.getJSONObject("properties")
            val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val id = feature.opt("id")?.toString() ?: featureIndex.toString()
            val nameEntry = namesRoot?.optJSONObject(id)
            stars += CatalogStar(
                id = id,
                point = SkyPoint(
                    raDegrees = coordinates.getDouble(0).toFloat(),
                    decDegrees = coordinates.getDouble(1).toFloat(),
                ),
                magnitude = properties.optDouble("mag", 6.0).toFloat(),
                bv = properties.optString("bv", "").toFloatOrNull(),
                // Fallback auf die Hipparcos-Nummer (id -- verifiziert gegen bekannte Sterne, z.B.
                // id=32349 liegt exakt auf Sirius): starnames.json deckt nur ~12% der 41411 Sterne in
                // stars.8.json ab, der Rest blieb bisher ganz unbeschriftet -- entgegen der Absicht in
                // createStarOverlays ("JEDER Stern hier bekommt eine Beschriftung"). Ohne DIESEN
                // Fallback wirkte "Alle bis Mag." dadurch fast leer (nur ~12% der Punkte sichtbar
                // beschriftet, der Rest als winzige, kaum wahrnehmbare unbeschriftete Punkte).
                name = nameEntry?.starDisplayLabel("en")?.takeIf { it.isNotBlank() } ?: "HIP $id",
                localizedNames = LOCALIZED_TAGS
                    .associateWith { nameEntry?.starDisplayLabel(it).orEmpty() }
                    .filterValues { it.isNotBlank() },
                properName = nameEntry?.optString("name", "")?.trim().orEmpty(),
                localizedProperNames = LOCALIZED_TAGS
                    .associateWith { nameEntry?.optString(it, "")?.trim().orEmpty() }
                    .filterValues { it.isNotBlank() },
                constellation = nameEntry?.optString("c", "")?.trim().orEmpty(),
            )
        }

        return stars.sortedBy { it.magnitude }
    }

    /** Volles Anzeige-Label für [lang]: Eigenname (lokalisiert, sonst international) -> Katalogbezeichnung. */
    private fun JSONObject?.starDisplayLabel(lang: String): String {
        if (this == null) return ""
        val properName = localizedProperName(lang)
        if (properName.isNotBlank()) return properName
        val constellation = optString("c", "").trim()
        val designation = listOf(
            optString("bayer", ""),
            optString("flam", ""),
            optString("var", ""),
            optString("desig", ""),
        ).firstOrNull { it.trim().isNotBlank() }.orEmpty().trim()
        if (designation.isNotBlank()) {
            return if (constellation.isNotBlank() && !designation.endsWith(constellation)) {
                "$designation $constellation"
            } else {
                designation
            }
        }
        return optString("hip", "").trim()
    }

    private fun JSONObject.localizedProperName(lang: String): String {
        if (lang == "en") return optString("name", "").trim()
        return optString(lang, "").trim().ifBlank { optString("name", "").trim() }
    }
}
