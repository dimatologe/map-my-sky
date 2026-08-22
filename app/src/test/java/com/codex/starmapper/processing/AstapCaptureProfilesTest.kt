package com.codex.starmapper.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class AstapCaptureProfilesTest {
    @Test
    fun fullFrameSigma40MatchesExpectedFieldOfView() {
        val result = AstapFieldOfViewCalculator.calculate(
            AstapCaptureSettings(
                cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = 40f,
            ),
        )

        assertEquals(48.46f, result.horizontalDegrees, 0.02f)
        assertEquals(33.40f, result.verticalDegrees, 0.02f)
    }

    @Test
    fun fullFrameAskar300MatchesExpectedFieldOfView() {
        val result = AstapFieldOfViewCalculator.calculate(
            AstapCaptureSettings(
                cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = 300f,
            ),
        )

        assertEquals(6.87f, result.horizontalDegrees, 0.02f)
        assertEquals(4.58f, result.verticalDegrees, 0.02f)
    }

    @Test
    fun portraitOrientationSwapsSingleFrameAxes() {
        val result = AstapFieldOfViewCalculator.calculate(
            AstapCaptureSettings(
                focalLengthMm = 40f,
                tileOrientation = AstapTileOrientation.Portrait,
            ),
        )

        assertEquals(33.40f, result.horizontalDegrees, 0.02f)
        assertEquals(48.46f, result.verticalDegrees, 0.02f)
    }
}
