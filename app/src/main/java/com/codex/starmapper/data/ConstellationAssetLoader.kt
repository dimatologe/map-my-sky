package com.codex.starmapper.data

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.codex.starmapper.domain.ConstellationPattern
import com.codex.starmapper.domain.Hemisphere
import com.codex.starmapper.domain.StarNode
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object ConstellationAssetLoader {
    fun load(context: Context): List<ConstellationPattern> {
        val text = context.assets.open("catalog/constellations.lines.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val starNamesByCoordinate = loadStarNamesByCoordinate(context)
        val grouped = linkedMapOf<String, MutableList<JSONArray>>()
        val features = root.getJSONArray("features")

        for (featureIndex in 0 until features.length()) {
            val feature = features.getJSONObject(featureIndex)
            val id = feature.getString("id")
            val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
            grouped.getOrPut(id) { mutableListOf() } += coordinates
        }

        return grouped.map { (id, multiLineGroups) ->
            val rawPoints = mutableListOf<SkyCoord>()
            val pointIndex = linkedMapOf<String, Int>()
            val edges = linkedSetOf<Pair<Int, Int>>()

            fun indexFor(coord: JSONArray): Int {
                val ra = coord.getDouble(0).toFloat()
                val dec = coord.getDouble(1).toFloat()
                val key = coordinateKey(ra, dec)
                return pointIndex.getOrPut(key) {
                    val normalizedRa = normalizeDegrees(ra)
                    rawPoints += SkyCoord(
                        ra = normalizedRa,
                        dec = dec,
                        name = starNamesByCoordinate[key] ?: starNamesByCoordinate[coordinateKey(normalizedRa, dec)].orEmpty(),
                    )
                    rawPoints.lastIndex
                }
            }

            multiLineGroups.forEach { lines ->
                for (lineIndex in 0 until lines.length()) {
                    val line = lines.getJSONArray(lineIndex)
                    var previous: Int? = null
                    for (pointIndexInLine in 0 until line.length()) {
                        val current = indexFor(line.getJSONArray(pointIndexInLine))
                        previous?.let { previousIndex ->
                            if (previousIndex != current) edges += previousIndex to current
                        }
                        previous = current
                    }
                }
            }

            val unwrappedRa = unwrapRightAscension(rawPoints.map { it.ra })
            val minRa = unwrappedRa.minOrNull() ?: 0f
            val maxRa = unwrappedRa.maxOrNull() ?: 1f
            val minDec = rawPoints.minOfOrNull { it.dec } ?: -1f
            val maxDec = rawPoints.maxOfOrNull { it.dec } ?: 1f
            val midRa = (minRa + maxRa) / 2f
            val midDec = (minDec + maxDec) / 2f
            val halfSpan = max(maxRa - minRa, maxDec - minDec).coerceAtLeast(1f) / 2f

            val metadata = ConstellationCatalog.metadataByIau[id]
                ?: ConstellationCatalog.Metadata(latinName = id, germanName = id)
            val stars = rawPoints.mapIndexed { index, coord ->
                StarNode(
                    name = coord.name.ifBlank { "${id}_${index + 1}" },
                    x = ((unwrappedRa.getOrElse(index) { coord.ra } - midRa) / halfSpan).coerceIn(-1.15f, 1.15f),
                    y = (-(coord.dec - midDec) / halfSpan).coerceIn(-1.15f, 1.15f),
                    raHours = coord.ra / 15f,
                    decDegrees = coord.dec,
                )
            }

            ConstellationPattern(
                id = id,
                name = metadata.latinName,
                germanName = metadata.germanName,
                localizedNames = metadata.localizedNames,
                hemisphere = hemisphereFor(minDec, maxDec),
                stars = stars,
                edges = edges.toList(),
                mythStrokes = mythStrokesFor(id),
            )
        }.sortedBy { it.germanName }
    }

    private fun loadStarNamesByCoordinate(context: Context): Map<String, String> {
        return runCatching {
            val namesRoot = JSONObject(context.assets.open("catalog/starnames.json").bufferedReader().use { it.readText() })
            val starsRoot = JSONObject(context.assets.open("catalog/stars.6.json").bufferedReader().use { it.readText() })
            val features = starsRoot.getJSONArray("features")
            buildMap(features.length()) {
                for (featureIndex in 0 until features.length()) {
                    val feature = features.getJSONObject(featureIndex)
                    val id = feature.opt("id")?.toString() ?: continue
                    val label = namesRoot.optJSONObject(id)?.starDisplayLabel()?.ifBlank { "HIP $id" } ?: "HIP $id"
                    if (label.isBlank()) continue
                    val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                    val ra = coordinates.getDouble(0).toFloat()
                    val dec = coordinates.getDouble(1).toFloat()
                    put(coordinateKey(ra, dec), label)
                    put(coordinateKey(normalizeDegrees(ra), dec), label)
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun JSONObject.starDisplayLabel(): String {
        val properName = optString("name", "").trim()
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

    private fun hemisphereFor(minDec: Float, maxDec: Float): Hemisphere = when {
        minDec >= 0f -> Hemisphere.North
        maxDec <= 0f -> Hemisphere.South
        else -> Hemisphere.Both
    }

    private fun mythStrokesFor(id: String): List<List<Offset>> = when (id) {
        "Ori" -> listOf(
            listOf(Offset(-0.62f, -0.86f), Offset(-0.78f, -0.42f), Offset(-0.58f, 0.14f), Offset(-0.56f, 0.68f)),
            listOf(Offset(0.46f, -0.80f), Offset(0.72f, -0.38f), Offset(0.58f, 0.18f), Offset(0.62f, 0.76f)),
            listOf(Offset(-0.42f, -0.50f), Offset(-0.18f, -0.05f), Offset(0.05f, 0.00f), Offset(0.32f, -0.36f)),
            listOf(Offset(-0.04f, 0.10f), Offset(-0.02f, 0.34f), Offset(0.06f, 0.55f)),
            listOf(Offset(-0.70f, -0.36f), Offset(-1.02f, -0.18f), Offset(-0.88f, 0.08f)),
            listOf(Offset(0.62f, -0.34f), Offset(1.00f, -0.14f), Offset(0.82f, 0.12f)),
        )
        "Cyg" -> listOf(
            listOf(Offset(0.00f, -1.06f), Offset(0.00f, -0.58f), Offset(0.00f, -0.10f), Offset(0.02f, 0.88f)),
            listOf(Offset(-0.78f, 0.02f), Offset(-0.36f, -0.16f), Offset(0.00f, -0.10f), Offset(0.42f, -0.12f), Offset(0.76f, -0.02f)),
            listOf(Offset(-0.68f, 0.18f), Offset(-0.30f, 0.10f), Offset(0.00f, -0.10f), Offset(0.34f, 0.12f), Offset(0.70f, 0.18f)),
            listOf(Offset(-0.10f, -1.00f), Offset(0.08f, -1.10f), Offset(0.18f, -0.94f)),
            listOf(Offset(-0.10f, 0.72f), Offset(0.02f, 0.98f), Offset(0.14f, 0.72f)),
        )
        else -> emptyList()
    }

    private fun unwrapRightAscension(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        var sumSin = 0.0
        var sumCos = 0.0
        values.forEach { value ->
            val radians = value / 180f * PI
            sumSin += sin(radians)
            sumCos += cos(radians)
        }
        val mean = normalizeDegrees((atan2(sumSin, sumCos) * 180.0 / PI).toFloat())
        return values.map { value -> mean + signedAngleDelta(value, mean) }
    }

    private fun signedAngleDelta(value: Float, origin: Float): Float {
        var delta = normalizeDegrees(value) - normalizeDegrees(origin)
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun normalizeDegrees(value: Float): Float {
        var normalized = value % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private fun coordinateKey(ra: Float, dec: Float): String {
        return "${(ra * 10000f).roundToInt()}:${(dec * 10000f).roundToInt()}"
    }
}

private data class SkyCoord(
    val ra: Float,
    val dec: Float,
    val name: String,
)
