package com.codex.starmapper.processing

import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Fixture mit den echten Werten einer nova.astrometry.net-Lösung (wcs.fits des
 * 5772x3744-Summenbilds): kein PLTSOLVD-Key, Y-Achse zählt von oben (flipY=false).
 */
class NovaWcsParsingTest {

    private fun novaCards(): Map<String, String> = WcsSolutionParser.parseCards(novaHeaderBytes())

    @Test
    fun parsesNovaHeaderWithoutPlateSolvedKey() {
        val solution = WcsSolutionParser.fromCards(
            cards = novaCards(),
            requirePlateSolved = false,
            flipY = false,
        )

        assertEquals(41.1404818536, solution.crVal1Degrees, 1e-9)
        assertEquals(60.6632272997, solution.crVal2Degrees, 1e-9)
        assertEquals(2467.13907878, solution.crPix1, 1e-6)
        assertEquals(1924.6492513, solution.crPix2, 1e-6)
        assertEquals(-0.000802371969102, solution.cd11, 1e-15)
        assertEquals(-0.000808278848831, solution.cd12, 1e-15)
        assertEquals(0.000807734137906, solution.cd21, 1e-15)
        assertEquals(-0.000803480523085, solution.cd22, 1e-15)
        assertEquals(-0.00020038360779, solution.inverseSipX[0 to 0]!!, 1e-15)
        assertEquals(0.000152508492301, solution.inverseSipY[0 to 0]!!, 1e-15)
    }

    @Test
    fun missingPlateSolvedKeyFailsWhenRequired() {
        assertThrows(IllegalStateException::class.java) {
            WcsSolutionParser.fromCards(
                cards = novaCards(),
                requirePlateSolved = true,
                flipY = true,
            )
        }
    }

    @Test
    fun referenceSkyPointMapsToReferencePixelWithoutVerticalFlip() {
        val solution = WcsSolutionParser.fromCards(
            cards = novaCards(),
            requirePlateSolved = false,
            flipY = false,
        ).copy(inverseSipX = emptyMap(), inverseSipY = emptyMap())

        val point = solution.skyToImage(
            SkyPoint(41.1404818536f, 60.6632272997f),
            imageHeight = 3744,
        )

        assertNotNull(point)
        // FITS ist 1-basiert: Referenzpixel (CRPIX) - 1; Y unverändert von oben gezählt.
        assertEquals(2466.139f, point!!.x, 0.01f)
        assertEquals(1923.649f, point.y, 0.01f)
    }

    private fun novaHeaderBytes(): ByteArray {
        val cards = listOf(
            "CRVAL1" to "41.1404818536",
            "CRVAL2" to "60.6632272997",
            "CRPIX1" to "2467.13907878",
            "CRPIX2" to "1924.6492513",
            "CD1_1" to "-0.000802371969102",
            "CD1_2" to "-0.000808278848831",
            "CD2_1" to "0.000807734137906",
            "CD2_2" to "-0.000803480523085",
            "AP_0_0" to "-0.00020038360779",
            "BP_0_0" to "0.000152508492301",
        ).map { (key, value) ->
            (key.padEnd(8) + "= " + value).padEnd(80).take(80)
        } + "END".padEnd(80)
        return cards.joinToString(separator = "").toByteArray(Charsets.US_ASCII)
    }
}
