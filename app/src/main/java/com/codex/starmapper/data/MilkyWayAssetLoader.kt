package com.codex.starmapper.data

import android.content.Context
import com.codex.starmapper.domain.MilkyWayLayer
import com.codex.starmapper.domain.MilkyWayPolygon
import com.codex.starmapper.domain.SkyPoint
import org.json.JSONArray
import org.json.JSONObject

object MilkyWayAssetLoader {
    fun load(context: Context): List<MilkyWayLayer> {
        val text = context.assets.open("catalog/mw.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val features = root.getJSONArray("features")
        val layers = mutableListOf<MilkyWayLayer>()

        for (featureIndex in 0 until features.length()) {
            val feature = features.getJSONObject(featureIndex)
            val id = feature.optString("id", "ol${featureIndex + 1}")
            val level = id.removePrefix("ol").toIntOrNull() ?: (featureIndex + 1)
            val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val polygons = mutableListOf<MilkyWayPolygon>()

            for (polygonIndex in 0 until coordinates.length()) {
                val polygon = coordinates.getJSONArray(polygonIndex)
                val rings = mutableListOf<List<SkyPoint>>()
                for (ringIndex in 0 until polygon.length()) {
                    val ring = polygon.getJSONArray(ringIndex)
                    rings += parseRing(ring).sampleForInteractiveSphere()
                }
                if (rings.isNotEmpty()) polygons += MilkyWayPolygon(rings)
            }

            if (polygons.isNotEmpty()) layers += MilkyWayLayer(level = level, polygons = polygons)
        }

        return layers.sortedBy { it.level }
    }

    private fun parseRing(ring: JSONArray): List<SkyPoint> {
        val points = ArrayList<SkyPoint>(ring.length())
        for (pointIndex in 0 until ring.length()) {
            val coord = ring.getJSONArray(pointIndex)
            points += SkyPoint(
                raDegrees = coord.getDouble(0).toFloat(),
                decDegrees = coord.getDouble(1).toFloat(),
            )
        }
        return points
    }

    private fun List<SkyPoint>.sampleForInteractiveSphere(): List<SkyPoint> {
        if (size <= 180) return this
        val step = (size / 180).coerceIn(2, 6)
        return filterIndexed { index, _ -> index % step == 0 || index == lastIndex }
    }
}
