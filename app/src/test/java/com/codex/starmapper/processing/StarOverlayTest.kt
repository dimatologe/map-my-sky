package com.codex.starmapper.processing

import com.codex.starmapper.domain.CatalogStar
import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarOverlayTest {

    private fun solution() = WcsSolution(
        crPix1 = 501.0,
        crPix2 = 400.0,
        crVal1Degrees = 0.0,
        crVal2Degrees = 0.0,
        cd11 = 0.01,
        cd12 = 0.0,
        cd21 = 0.0,
        cd22 = 0.01,
    )

    private fun star(
        id: String,
        properName: String = "",
        magnitude: Float = 4f,
        raDegrees: Float = 0f,
        decDegrees: Float = 0f,
    ) = CatalogStar(
        id = id,
        point = SkyPoint(raDegrees, decDegrees),
        magnitude = magnitude,
        bv = null,
        name = properName.ifBlank { id },
        properName = properName,
    )

    @Test
    fun namedStarsOnlyIncludesStarsWithProperNames() {
        val overlays = AstapOverlayMapper.createStarOverlays(
            catalogStars = listOf(
                star("HIP 24436", properName = "Rigel", raDegrees = 0.1f, decDegrees = 0.1f),
                star("HIP 11767", magnitude = 2f, raDegrees = -0.1f, decDegrees = -0.1f),
            ),
            constellationPatterns = emptyList(),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            includeNamed = true,
            includeConstellation = false,
            allToMagnitude = null,
        )

        assertEquals(listOf("Rigel"), overlays.map { it.text })
    }

    @Test
    fun allToMagnitudeIncludesUnnamedStarsUpToLimit() {
        val overlays = AstapOverlayMapper.createStarOverlays(
            catalogStars = listOf(
                star("a", magnitude = 3f, raDegrees = 0.1f),
                star("b", magnitude = 7f, raDegrees = 0.2f),
            ),
            constellationPatterns = emptyList(),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            includeNamed = false,
            includeConstellation = false,
            allToMagnitude = 5f,
        )

        assertEquals(1, overlays.size)
        assertTrue(overlays.all { !it.showName })
    }

    @Test
    fun disabledTogglesProduceNoOverlays() {
        val overlays = AstapOverlayMapper.createStarOverlays(
            catalogStars = listOf(star("a", properName = "Rigel")),
            constellationPatterns = emptyList(),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            includeNamed = false,
            includeConstellation = false,
            allToMagnitude = null,
        )

        assertTrue(overlays.isEmpty())
    }
}
