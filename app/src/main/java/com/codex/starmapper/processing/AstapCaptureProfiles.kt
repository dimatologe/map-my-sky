package com.codex.starmapper.processing

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.codex.starmapper.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class AstapCaptureType {
    Single,
    Fisheye,
}

enum class AstapFovSource {
    AutomaticExif,
    EquipmentProfile,
    Manual,
}

enum class AstapTileOrientation {
    Landscape,
    Portrait,
}

data class CameraSensorProfile(
    val id: String,
    val nameResId: Int,
    val sensorWidthMm: Double,
    val sensorHeightMm: Double,
    val pixelWidth: Int,
    val pixelHeight: Int,
)

data class OpticalProfile(
    val id: String,
    val name: String,
    val focalLengthMm: Double,
)

data class AstapCaptureSettings(
    val captureType: AstapCaptureType = AstapCaptureType.Single,
    val fovSource: AstapFovSource = AstapFovSource.AutomaticExif,
    val cameraProfileId: String = AstapEquipmentCatalog.FULL_FRAME_24_ID,
    // Brennweite in mm – frei eingebbar (statt festem Objektivkatalog), deckt jedes Objektiv ab.
    val focalLengthMm: Float = 40f,
    // Sensor-Auflösung in Megapixeln -> Pixelpitch (für die pixelbasierte FOV-Hypothese/Crops).
    val sensorMegapixels: Float = 24f,
    val tileOrientation: AstapTileOrientation = AstapTileOrientation.Landscape,
    val manualVerticalFovDegrees: Float = 60f,
)

data class AstapFieldOfView(
    val horizontalDegrees: Float,
    val verticalDegrees: Float,
)

data class AstapFovCandidates(
    val primaryDegrees: Float,
    val alternativeDegrees: Float?,
)

data class AstapExifFieldOfView(
    val focalLength35mm: Int,
    val fieldOfView: AstapFieldOfView,
)

object AstapEquipmentCatalog {
    const val FULL_FRAME_24_ID = "full-frame-24mp"
    const val SIGMA_40_ID = "sigma-40-art"
    const val ASKAR_FRA_300_ID = "askar-fra-300"

    // Sensor-GRÖSSEN (mm). Pixelanzahl kommt separat als Megapixel-Eingabe (Pixelpitch),
    // die pixelWidth/pixelHeight hier sind nur nominelle Defaults und werden nicht direkt genutzt.
    val cameras = listOf(
        CameraSensorProfile(
            id = FULL_FRAME_24_ID,
            nameResId = R.string.camera_sensor_full_frame,
            sensorWidthMm = 36.0,
            sensorHeightMm = 24.0,
            pixelWidth = 6000,
            pixelHeight = 4000,
        ),
        CameraSensorProfile(
            id = "aps-c",
            nameResId = R.string.camera_sensor_apsc,
            sensorWidthMm = 23.5,
            sensorHeightMm = 15.6,
            pixelWidth = 6000,
            pixelHeight = 4000,
        ),
        CameraSensorProfile(
            id = "micro-four-thirds",
            nameResId = R.string.camera_sensor_m43,
            sensorWidthMm = 17.3,
            sensorHeightMm = 13.0,
            pixelWidth = 5184,
            pixelHeight = 3888,
        ),
        CameraSensorProfile(
            id = "one-inch",
            nameResId = R.string.camera_sensor_one_inch,
            sensorWidthMm = 13.2,
            sensorHeightMm = 8.8,
            pixelWidth = 5472,
            pixelHeight = 3648,
        ),
        CameraSensorProfile(
            id = "medium-format",
            nameResId = R.string.camera_sensor_medium_format,
            sensorWidthMm = 44.0,
            sensorHeightMm = 33.0,
            pixelWidth = 8256,
            pixelHeight = 6192,
        ),
    )

    val optics = listOf(
        OpticalProfile(
            id = SIGMA_40_ID,
            name = "Sigma 40 mm F1.4 DG HSM Art",
            focalLengthMm = 40.0,
        ),
        OpticalProfile(
            id = ASKAR_FRA_300_ID,
            name = "Askar FRA 300",
            focalLengthMm = 300.0,
        ),
    )

    fun camera(id: String): CameraSensorProfile =
        cameras.firstOrNull { it.id == id } ?: cameras.first()

    fun optics(id: String): OpticalProfile =
        optics.firstOrNull { it.id == id } ?: optics.first()
}

object AstapFieldOfViewCalculator {
    fun calculate(settings: AstapCaptureSettings): AstapFieldOfView {
        val camera = AstapEquipmentCatalog.camera(settings.cameraProfileId)
        val focalLengthMm = settings.focalLengthMm.toDouble()
        var tileWidth = camera.sensorWidthMm
        var tileHeight = camera.sensorHeightMm
        if (settings.tileOrientation == AstapTileOrientation.Portrait) {
            val oldWidth = tileWidth
            tileWidth = tileHeight
            tileHeight = oldWidth
        }

        val tileHorizontalFov = angleDegrees(tileWidth, focalLengthMm)
        val tileVerticalFov = angleDegrees(tileHeight, focalLengthMm)

        return AstapFieldOfView(
            horizontalDegrees = tileHorizontalFov,
            verticalDegrees = tileVerticalFov,
        )
    }

    /**
     * [imageHeightPx] muss die Bildhöhe in nativen Sensorpixeln sein (Originalaufnahme),
     * nicht die Höhe eines für die Anzeige herunterskalierten Bitmaps — der Pixelpitch
     * des Kameraprofils gilt nur für native Pixel.
     */
    fun calculateFromImageHeight(imageHeightPx: Int, settings: AstapCaptureSettings): Float {
        val camera = AstapEquipmentCatalog.camera(settings.cameraProfileId)
        val scale = pixelScaleArcsec(camera, settings.focalLengthMm.toDouble(), settings.sensorMegapixels)
        return (imageHeightPx * scale / 3600.0).toFloat().coerceIn(0.05f, 179f)
    }

    /**
     * Two FOV hypotheses for an image of unknown provenance:
     * - pixel-based (image height x native pixel scale): exact for crops kept at native scale
     * - sensor-based (sensor mm geometry, matched to image orientation): exact for resized full frames
     * If the image aspect ratio matches the sensor, a resize is more likely and the
     * sensor-based value becomes the primary candidate.
     */
    fun candidates(
        imageWidthPx: Int,
        imageHeightPx: Int,
        settings: AstapCaptureSettings,
    ): AstapFovCandidates {
        val pixelBased = calculateFromImageHeight(imageHeightPx, settings)
        val sensorBased = sensorVerticalFovForImage(imageWidthPx, imageHeightPx, settings)
        val camera = AstapEquipmentCatalog.camera(settings.cameraProfileId)
        val imageAspect = aspectRatio(imageWidthPx.toDouble(), imageHeightPx.toDouble())
        val sensorAspect = aspectRatio(camera.sensorWidthMm, camera.sensorHeightMm)
        val looksResized = abs(imageAspect - sensorAspect) / sensorAspect <= ASPECT_MATCH_TOLERANCE
        val primary = if (looksResized) sensorBased else pixelBased
        val alternative = if (looksResized) pixelBased else sensorBased
        return AstapFovCandidates(
            primaryDegrees = primary,
            alternativeDegrees = alternative.takeIf { abs(it - primary) > 0.01f },
        )
    }

    fun sensorVerticalFovForImage(
        imageWidthPx: Int,
        imageHeightPx: Int,
        settings: AstapCaptureSettings,
    ): Float {
        val camera = AstapEquipmentCatalog.camera(settings.cameraProfileId)
        val shortSideMm = min(camera.sensorWidthMm, camera.sensorHeightMm)
        val longSideMm = max(camera.sensorWidthMm, camera.sensorHeightMm)
        val verticalExtentMm = if (imageHeightPx > imageWidthPx) longSideMm else shortSideMm
        return angleDegrees(verticalExtentMm, settings.focalLengthMm.toDouble())
    }

    fun pixelScaleArcsec(settings: AstapCaptureSettings): Double {
        val camera = AstapEquipmentCatalog.camera(settings.cameraProfileId)
        return pixelScaleArcsec(camera, settings.focalLengthMm.toDouble(), settings.sensorMegapixels)
    }

    private fun pixelScaleArcsec(
        camera: CameraSensorProfile,
        focalLengthMm: Double,
        megapixels: Float,
    ): Double {
        val pixelPitchUm = camera.sensorWidthMm / nativePixelWidth(camera, megapixels) * 1000.0
        return 206.265 * pixelPitchUm / focalLengthMm.coerceAtLeast(0.1)
    }

    // Native Pixelbreite aus Megapixeln + Sensor-Seitenverhältnis (Pixelpitch-Basis).
    private fun nativePixelWidth(camera: CameraSensorProfile, megapixels: Float): Double {
        val total = megapixels.toDouble().coerceAtLeast(0.1) * 1_000_000.0
        val aspect = camera.sensorWidthMm / camera.sensorHeightMm.coerceAtLeast(0.1)
        return sqrt(total * aspect).coerceAtLeast(1.0)
    }

    private fun angleDegrees(sensorExtentMm: Double, focalLengthMm: Double): Float {
        val angle = 2.0 * atan(sensorExtentMm / (2.0 * focalLengthMm.coerceAtLeast(0.1)))
        return (angle * 180.0 / PI).toFloat().coerceIn(0.05f, 179f)
    }

    private fun aspectRatio(a: Double, b: Double): Double =
        max(a, b) / min(a, b).coerceAtLeast(1e-6)

    private const val ASPECT_MATCH_TOLERANCE = 0.02
}

// Aus AstapSolver.kt hierher verschoben (2026-08-20, ASTAP-Entfernung): wird UNBEDINGT für jeden
// Bildimport aufgerufen, unabhängig von der Solver-Wahl (StarMapperApp.kt, LaunchedEffect(imageUri,
// loadedImage)) -- speist den Positions-Suchradius für SOWOHL LocalAstrometry ALS AUCH NovaOnline.
// Trotz Namens nie ASTAP-exklusiv gewesen, nur zufällig in derselben Datei untergebracht.
object AstapFovEstimator {
    fun estimateDetailed(
        context: Context,
        uri: Uri,
        imageWidth: Int,
        imageHeight: Int,
    ): AstapExifFieldOfView? = runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            val exif = ExifInterface(checkNotNull(input))
            val focalLength35mm = exif.getAttributeInt(
                ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                0,
            )
            if (focalLength35mm <= 0) return@runCatching null
            val diagonalHalfAngle = atan(43.2666 / (2.0 * focalLength35mm))
            val diagonalPixels = hypot(imageWidth.toDouble(), imageHeight.toDouble())
            val horizontalSensorFraction = imageWidth / diagonalPixels
            val verticalSensorFraction = imageHeight / diagonalPixels
            AstapExifFieldOfView(
                focalLength35mm = focalLength35mm,
                fieldOfView = AstapFieldOfView(
                    horizontalDegrees = (
                        2.0 * atan(kotlin.math.tan(diagonalHalfAngle) * horizontalSensorFraction) *
                            180.0 / PI
                        ).toFloat().coerceIn(0.05f, 179f),
                    verticalDegrees = (
                        2.0 * atan(kotlin.math.tan(diagonalHalfAngle) * verticalSensorFraction) *
                            180.0 / PI
                        ).toFloat().coerceIn(0.05f, 179f),
                ),
            )
        }
    }.getOrNull()
}
