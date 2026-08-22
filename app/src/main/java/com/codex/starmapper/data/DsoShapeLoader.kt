package com.codex.starmapper.data

import android.content.Context
import com.codex.starmapper.domain.DsoShape
import org.json.JSONObject

/**
 * Lädt app-eigene Form-Fakten (assets/catalog/dso_shapes.json): pro Bezeichnung
 * `{ "maj": Bogenmin, "min": Bogenmin?, "pa": Grad? }`. Mit `min`+`pa` -> orientierte Ellipse,
 * sonst Kreis in korrigierter Größe. Winkelgrößen/PA sind gemeinfreie Messfakten.
 *
 * Key wird NORMALISIERT (Großbuchstaben, ohne Leerzeichen/Bindestriche/Unterstriche). Nicht-Objekt-
 * Werte (z.B. der "_comment"-String) werden ignoriert. Fehlt die Datei, wird eine leere Map
 * geliefert -> keine Ellipsen/Korrekturen (Kreis-Fallback = Altverhalten).
 */
object DsoShapeLoader {
    fun load(context: Context): Map<String, DsoShape> {
        val text = runCatching {
            context.assets.open("catalog/dso_shapes.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyMap()

        val result = HashMap<String, DsoShape>()
        runCatching {
            val root = JSONObject(text)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue // "_comment" etc. überspringen
                val maj = obj.optDouble("maj", Double.NaN)
                if (maj.isNaN() || maj <= 0.0) continue
                val min = obj.optDouble("min", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
                val pa = obj.optDouble("pa", Double.NaN).takeIf { !it.isNaN() }
                result[normalize(key)] = DsoShape(
                    majArcmin = maj.toFloat(),
                    minArcmin = min?.toFloat(),
                    posAngleDeg = pa?.toFloat(),
                )
            }
        }
        return result
    }

    /** Normalisierung für den Bezeichnungs-Vergleich (identisch zur Mapper-Seite). */
    private fun normalize(designation: String): String =
        designation.trim().uppercase().replace(Regex("[\\s\\-_]"), "")
}
