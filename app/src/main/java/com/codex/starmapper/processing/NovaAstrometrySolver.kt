package com.codex.starmapper.processing

import android.content.Context
import android.graphics.Bitmap
import com.codex.starmapper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class NovaSolveResult(
    val wcs: WcsSolution,
    val jobId: Long,
    val log: String,
)

class NovaSolveException(
    message: String,
    cause: Throwable? = null,
    // Vorübergehender Netz-/Serverfehler (z.B. kurzer Verbindungsabriss beim Wechsel
    // in den Hintergrund) -> während des Pollings tolerieren statt sofort abzubrechen.
    val transient: Boolean = false,
    // Server-Timeout (Warteschlange/Lösen zu lange). Genutzt, um bei De-Warp den teuren
    // Roh-Crop-Fallback zu überspringen (derselbe langsame Server würde erneut timeouten).
    val timeout: Boolean = false,
) : IllegalStateException(message, cause)

/**
 * Online-Solver über die nova.astrometry.net-API. Ablauf: login -> upload ->
 * Submission/Job pollen -> WCS-Header laden. Die zurückgegebene WCS ist auf die
 * Pixelkoordinaten des übergebenen Bitmaps umgerechnet (Upload wird verkleinert).
 */
class NovaAstrometrySolver(private val context: Context) {
    suspend fun solve(
        bitmap: Bitmap,
        apiKey: String,
        fovWidthLowerDeg: Float?,
        fovWidthUpperDeg: Float?,
        // Optionaler Positions-Hinweis (RA/Dec in Grad + Suchradius Grad): Nova sucht nur nahe
        // dieser Stelle statt am ganzen Himmel. Unabhängig von der (blinden) Skala.
        centerRaDeg: Double? = null,
        centerDecDeg: Double? = null,
        radiusDeg: Double? = null,
        onStatus: suspend (String) -> Unit,
    ): NovaSolveResult = withContext(Dispatchers.IO) {
        check(apiKey.isNotBlank()) { context.getString(R.string.nova_error_no_api_key) }
        val log = StringBuilder()

        onStatus(context.getString(R.string.nova_status_signing_in))
        val session = retryTransient(onStatus) { login(apiKey) }
        log.appendLine("login ok")

        onStatus(context.getString(R.string.nova_status_uploading))
        val uploadImage = prepareUpload(bitmap)
        val subId = retryTransient(onStatus) {
            submitUpload(session, uploadImage, fovWidthLowerDeg, fovWidthUpperDeg, centerRaDeg, centerDecDeg, radiusDeg)
        }
        log.appendLine("upload ok subid=$subId scale=${uploadImage.scale}")

        onStatus(context.getString(R.string.nova_status_in_queue_subid, subId))
        val jobId = awaitJobId(subId, onStatus)
        log.appendLine("job=$jobId")

        onStatus(context.getString(R.string.nova_status_solving_job, jobId))
        awaitJobSuccess(jobId, onStatus)
        log.appendLine("job solved")

        val wcsBytes = httpGetBytes("$BASE_URL/wcs_file/$jobId")
        val cards = WcsSolutionParser.parseCards(wcsBytes)
        val uploadedWcs = WcsSolutionParser.fromCards(
            cards = cards,
            requirePlateSolved = false,
            // astrometry.net behält bei Rasterbildern die Zeilenreihenfolge bei;
            // die WCS-Y-Achse zählt von oben (kein FITS-Flip wie bei ASTAP).
            flipY = false,
        )
        log.appendLine(
            "wcs center ra=${uploadedWcs.crVal1Degrees} dec=${uploadedWcs.crVal2Degrees}",
        )
        NovaSolveResult(
            wcs = rescaleToDisplay(uploadedWcs, uploadImage.scale),
            jobId = jobId,
            log = log.toString(),
        )
    }

    private class UploadImage(
        val jpegBytes: ByteArray,
        val scale: Float, // uploadPx / displayPx
    )

    private fun prepareUpload(bitmap: Bitmap): UploadImage {
        val maxDimension = max(bitmap.width, bitmap.height)
        val scale = if (maxDimension > UPLOAD_MAX_DIMENSION_PX) {
            UPLOAD_MAX_DIMENSION_PX.toFloat() / maxDimension
        } else {
            1f
        }
        val uploadBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val output = ByteArrayOutputStream()
        check(uploadBitmap.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, output)) {
            context.getString(R.string.nova_error_upload_prep_failed)
        }
        if (uploadBitmap !== bitmap) uploadBitmap.recycle()
        return UploadImage(jpegBytes = output.toByteArray(), scale = scale)
    }

    /**
     * Die WCS bezieht sich auf das verkleinerte Upload-Bild; für die Anzeige wird sie
     * auf das Display-Bitmap zurückgerechnet. SIP-Verzeichnung wird dabei KORREKT mitskaliert:
     * ein SIP-Term der Ordnung n=p+q skaliert mit s^(n-1) (n=1 -> unverändert, n=2 -> s, n=3 -> s²).
     * Früher wurde SIP verworfen -> bei Weitwinkel/Fisheye-Crops standen Anker zum Rand hin daneben.
     */
    private fun rescaleToDisplay(wcs: WcsSolution, uploadScale: Float): WcsSolution {
        if (uploadScale >= 1f) return wcs
        val s = uploadScale.toDouble()
        return wcs.copy(
            crPix1 = (wcs.crPix1 - 0.5) / s + 0.5,
            crPix2 = (wcs.crPix2 - 0.5) / s + 0.5,
            cd11 = wcs.cd11 * s,
            cd12 = wcs.cd12 * s,
            cd21 = wcs.cd21 * s,
            cd22 = wcs.cd22 * s,
            inverseSipX = wcs.inverseSipX.mapValues { (k, v) -> v * s.pow(k.first + k.second - 1) },
            inverseSipY = wcs.inverseSipY.mapValues { (k, v) -> v * s.pow(k.first + k.second - 1) },
            forwardSipX = wcs.forwardSipX.mapValues { (k, v) -> v * s.pow(k.first + k.second - 1) },
            forwardSipY = wcs.forwardSipY.mapValues { (k, v) -> v * s.pow(k.first + k.second - 1) },
        )
    }

    private fun login(apiKey: String): String {
        val response = postForm(
            url = "$BASE_URL/api/login",
            requestJson = JSONObject().put("apikey", apiKey),
        )
        if (response.optString("status") != "success") {
            val errorMessage = response.optString("errormessage")
                .ifBlank { context.getString(R.string.nova_error_unknown) }
            throw NovaSolveException(context.getString(R.string.nova_error_login_failed, errorMessage))
        }
        return response.optString("session")
            .ifBlank {
                throw NovaSolveException(context.getString(R.string.nova_error_login_no_session))
            }
    }

    private fun submitUpload(
        session: String,
        image: UploadImage,
        fovWidthLowerDeg: Float?,
        fovWidthUpperDeg: Float?,
        centerRaDeg: Double? = null,
        centerDecDeg: Double? = null,
        radiusDeg: Double? = null,
    ): Long {
        val request = JSONObject()
            .put("session", session)
            .put("publicly_visible", "n")
            .put("allow_modifications", "d")
            .put("allow_commercial_use", "d")
        if (fovWidthLowerDeg != null && fovWidthUpperDeg != null) {
            request
                .put("scale_units", "degwidth")
                .put("scale_type", "ul")
                .put("scale_lower", String.format(Locale.US, "%.3f", fovWidthLowerDeg).toDouble())
                .put("scale_upper", String.format(Locale.US, "%.3f", fovWidthUpperDeg).toDouble())
        }
        // Positions-Hinweis: enge Suche um RA/Dec (Grad). Skala bleibt davon unberührt (blind ok).
        if (centerRaDeg != null && centerDecDeg != null && radiusDeg != null) {
            request
                .put("center_ra", String.format(Locale.US, "%.5f", centerRaDeg).toDouble())
                .put("center_dec", String.format(Locale.US, "%.5f", centerDecDeg).toDouble())
                .put("radius", String.format(Locale.US, "%.3f", radiusDeg).toDouble())
        }
        val response = postMultipart(
            url = "$BASE_URL/api/upload",
            requestJson = request,
            fileName = "image.jpg",
            fileBytes = image.jpegBytes,
        )
        if (response.optString("status") != "success") {
            val errorMessage = response.optString("errormessage")
                .ifBlank { context.getString(R.string.nova_error_unknown) }
            throw NovaSolveException(context.getString(R.string.nova_error_upload_failed, errorMessage))
        }
        return response.optLong("subid", -1L)
            .takeIf { it > 0 } ?: throw NovaSolveException(
            context.getString(R.string.nova_error_upload_no_subid),
        )
    }

    private suspend fun awaitJobId(subId: Long, onStatus: suspend (String) -> Unit): Long {
        val deadline = System.currentTimeMillis() + QUEUE_TIMEOUT_MS
        var polls = 0
        var transientErrors = 0
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val submission = try {
                httpGetJson("$BASE_URL/api/submissions/$subId")
            } catch (error: NovaSolveException) {
                transientErrors = handlePollError(error, transientErrors, onStatus)
                delay(POLL_INTERVAL_MS)
                continue
            }
            transientErrors = 0
            val jobs = submission.optJSONArray("jobs")
            if (jobs != null) {
                for (index in 0 until jobs.length()) {
                    val jobId = jobs.optLong(index, 0L)
                    if (jobId > 0) return jobId
                }
            }
            polls++
            if (polls % 6 == 0) {
                onStatus(
                    context.getString(R.string.nova_status_in_queue_seconds, polls * POLL_INTERVAL_MS / 1000),
                )
            }
            delay(POLL_INTERVAL_MS)
        }
        throw NovaSolveException(
            context.getString(R.string.nova_error_queue_timeout),
            timeout = true,
        )
    }

    private suspend fun awaitJobSuccess(jobId: Long, onStatus: suspend (String) -> Unit) {
        val deadline = System.currentTimeMillis() + SOLVE_TIMEOUT_MS
        var polls = 0
        var transientErrors = 0
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val job = try {
                httpGetJson("$BASE_URL/api/jobs/$jobId")
            } catch (error: NovaSolveException) {
                transientErrors = handlePollError(error, transientErrors, onStatus)
                delay(POLL_INTERVAL_MS)
                continue
            }
            transientErrors = 0
            when (job.optString("status")) {
                "success" -> return
                "failure" -> throw NovaSolveException(context.getString(R.string.nova_error_solve_failed_server))
            }
            polls++
            if (polls % 6 == 0) {
                onStatus(context.getString(R.string.nova_status_solving_seconds, polls * POLL_INTERVAL_MS / 1000))
            }
            delay(POLL_INTERVAL_MS)
        }
        throw NovaSolveException(
            context.getString(R.string.nova_error_solve_timeout),
            timeout = true,
        )
    }

    /**
     * Der Job läuft serverseitig weiter, auch wenn das Handy kurz die Verbindung verliert
     * (z.B. App im Hintergrund). Vorübergehende Fehler werden daher beim Pollen toleriert;
     * erst nach mehreren aufeinanderfolgenden Aussetzern wird abgebrochen.
     */
    private suspend fun handlePollError(
        error: NovaSolveException,
        consecutive: Int,
        onStatus: suspend (String) -> Unit,
    ): Int {
        if (!error.transient) throw error
        val next = consecutive + 1
        if (next > MAX_CONSECUTIVE_POLL_ERRORS) throw error
        onStatus(context.getString(R.string.nova_status_retrying, next))
        return next
    }

    private suspend fun <T> retryTransient(
        onStatus: suspend (String) -> Unit,
        block: () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: NovaSolveException) {
                attempt++
                if (!error.transient || attempt >= LOGIN_UPLOAD_RETRIES) throw error
                onStatus(context.getString(R.string.nova_status_retrying, attempt))
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun postForm(url: String, requestJson: JSONObject): JSONObject {
        val body = "request-json=" + URLEncoder.encode(requestJson.toString(), "UTF-8")
        return withConnection(url) { connection ->
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            readJson(connection)
        }
    }

    private fun postMultipart(
        url: String,
        requestJson: JSONObject,
        fileName: String,
        fileBytes: ByteArray,
    ): JSONObject {
        val boundary = "----sternbildmapper${System.currentTimeMillis()}"
        return withConnection(url) { connection ->
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.buffered().use { output ->
                fun writeLine(text: String) = output.write("$text\r\n".toByteArray(Charsets.UTF_8))
                writeLine("--$boundary")
                writeLine("Content-Type: text/plain")
                writeLine("MIME-Version: 1.0")
                writeLine("Content-disposition: form-data; name=\"request-json\"")
                writeLine("")
                writeLine(requestJson.toString())
                writeLine("--$boundary")
                writeLine("Content-Type: application/octet-stream")
                writeLine("MIME-Version: 1.0")
                writeLine("Content-disposition: form-data; name=\"file\"; filename=\"$fileName\"")
                writeLine("")
                output.write(fileBytes)
                writeLine("")
                writeLine("--$boundary--")
            }
            readJson(connection)
        }
    }

    private fun httpGetJson(url: String): JSONObject = withConnection(url) { connection ->
        connection.requestMethod = "GET"
        readJson(connection)
    }

    private fun httpGetBytes(url: String): ByteArray = withConnection(url) { connection ->
        connection.requestMethod = "GET"
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw httpError(code)
        }
        connection.inputStream.use { it.readBytes() }
    }

    private fun <T> withConnection(url: String, block: (HttpURLConnection) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        return try {
            block(connection)
        } catch (error: NovaSolveException) {
            throw error
        } catch (error: java.net.UnknownHostException) {
            throw NovaSolveException(
                context.getString(R.string.nova_error_no_internet),
                error,
                transient = true,
            )
        } catch (error: java.net.SocketTimeoutException) {
            throw NovaSolveException(
                context.getString(R.string.nova_error_not_responding),
                error,
                transient = true,
            )
        } catch (error: java.io.IOException) {
            // z.B. "Software caused connection abort" beim Hintergrundwechsel.
            throw NovaSolveException(
                context.getString(R.string.nova_error_network, error.message ?: error.javaClass.simpleName),
                error,
                transient = true,
            )
        } catch (error: Exception) {
            throw NovaSolveException(
                context.getString(R.string.nova_error_network, error.message ?: error.javaClass.simpleName),
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        if (code !in 200..299) {
            throw httpError(code)
        }
        return runCatching { JSONObject(text) }.getOrElse {
            throw NovaSolveException(context.getString(R.string.nova_error_unexpected_response))
        }
    }

    private fun httpError(code: Int): NovaSolveException = if (code in 500..599) {
        NovaSolveException(
            context.getString(R.string.nova_error_overloaded, code),
            transient = true,
        )
    } else {
        NovaSolveException(context.getString(R.string.nova_error_http_response, code))
    }

    private companion object {
        const val BASE_URL = "https://nova.astrometry.net"
        const val UPLOAD_MAX_DIMENSION_PX = 3000
        const val UPLOAD_JPEG_QUALITY = 92
        const val POLL_INTERVAL_MS = 5_000L
        const val QUEUE_TIMEOUT_MS = 6 * 60_000L
        const val SOLVE_TIMEOUT_MS = 8 * 60_000L
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 30_000
        // Bis zu ~1 Minute Verbindungsaussetzer während des Pollings tolerieren.
        const val MAX_CONSECUTIVE_POLL_ERRORS = 12
        const val LOGIN_UPLOAD_RETRIES = 3
    }
}
