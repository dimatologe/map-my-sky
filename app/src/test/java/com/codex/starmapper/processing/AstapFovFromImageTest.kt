package com.codex.starmapper.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AstapFovFromImageTest {

    private fun askarSettings() = AstapCaptureSettings(
        cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
        focalLengthMm = 300f,
    )

    @Test
    fun pixelScaleArcsecFullFrameAskar300() {
        // 36mm / 6000px * 1000 = 6.0 µm pitch; 206.265 * 6.0 / 300 = 4.1253 arcsec/px
        val scale = AstapFieldOfViewCalculator.pixelScaleArcsec(
            AstapCaptureSettings(
                cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = 300f,
            ),
        )
        assertEquals(4.1253, scale, 0.001)
    }

    @Test
    fun fovFromImageHeightNativeResolution() {
        // Native 6000x4000 sensor: 4000px * 4.1253"/px / 3600 = 4.584°
        val fov = AstapFieldOfViewCalculator.calculateFromImageHeight(
            imageHeightPx = 4000,
            settings = AstapCaptureSettings(
                cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = 300f,
            ),
        )
        assertEquals(4.584f, fov, 0.02f)
    }

    @Test
    fun fovFromImageHeightCroppedImage() {
        // Cropped to 4998px height: 4998 * 4.1253 / 3600 = 5.727°
        val fov = AstapFieldOfViewCalculator.calculateFromImageHeight(
            imageHeightPx = 4998,
            settings = AstapCaptureSettings(
                cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = 300f,
            ),
        )
        assertEquals(5.727f, fov, 0.02f)
    }

    @Test
    fun fovFromImageSmallerThanSensorHeightGivesSmallerFov() {
        val fovNative = AstapFieldOfViewCalculator.calculateFromImageHeight(
            imageHeightPx = 4000,
            settings = AstapCaptureSettings(
                cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = 300f,
            ),
        )
        val fovCropped = AstapFieldOfViewCalculator.calculateFromImageHeight(
            imageHeightPx = 2000,
            settings = AstapCaptureSettings(
                cameraProfileId = AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = 300f,
            ),
        )
        assertEquals(fovNative / 2f, fovCropped, 0.02f)
    }

    @Test
    fun candidatesPreferSensorFovForResizedFullFrame() {
        // RAW preview: 1616x1080 = full 3:2 frame downscaled 3.7x
        val candidates = AstapFieldOfViewCalculator.candidates(
            imageWidthPx = 1616,
            imageHeightPx = 1080,
            settings = askarSettings(),
        )
        assertEquals(4.5812f, candidates.primaryDegrees, 0.001f)
        assertEquals(1.2376f, candidates.alternativeDegrees!!, 0.001f)
    }

    @Test
    fun candidatesPreferPixelFovForCroppedImage() {
        // Stitched/cropped image: aspect 1.242 does not match the 3:2 sensor
        val candidates = AstapFieldOfViewCalculator.candidates(
            imageWidthPx = 6208,
            imageHeightPx = 4998,
            settings = askarSettings(),
        )
        assertEquals(5.7273f, candidates.primaryDegrees, 0.002f)
        assertEquals(4.5812f, candidates.alternativeDegrees!!, 0.001f)
    }

    @Test
    fun candidatesCollapseForNativeResolution() {
        val landscape = AstapFieldOfViewCalculator.candidates(
            imageWidthPx = 6000,
            imageHeightPx = 4000,
            settings = askarSettings(),
        )
        assertEquals(4.5812f, landscape.primaryDegrees, 0.001f)
        assertNull(landscape.alternativeDegrees)

        val portrait = AstapFieldOfViewCalculator.candidates(
            imageWidthPx = 4000,
            imageHeightPx = 6000,
            settings = askarSettings(),
        )
        assertEquals(6.8673f, portrait.primaryDegrees, 0.001f)
        assertNull(portrait.alternativeDegrees)
    }

    @Test
    fun sensorFovMatchesImageOrientation() {
        assertEquals(
            4.5812f,
            AstapFieldOfViewCalculator.sensorVerticalFovForImage(1616, 1080, askarSettings()),
            0.001f,
        )
        assertEquals(
            6.8673f,
            AstapFieldOfViewCalculator.sensorVerticalFovForImage(1080, 1616, askarSettings()),
            0.001f,
        )
    }
}
