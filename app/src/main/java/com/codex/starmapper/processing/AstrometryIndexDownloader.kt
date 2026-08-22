package com.codex.starmapper.processing

import android.content.Context
import com.codex.starmapper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Herunterladbare astrometry.net-Index-Pakete für SCHMALERE Felder, als das gebündelte 4110–4119
 * (~1°–33°) abdeckt. Die Dateien landen im selben Index-Ordner ([AstrometryIndexManager.indexDir]),
 * den die backend.cfg per `add_path` + `autoindex` automatisch mitnutzt — kein cfg-Eingriff nötig.
 *
 * Server: data.astrometry.net (HTTPS). Größen/Dateistruktur am 2026-07-11 am Server verifiziert.
 * Die /4100/-Skalen sind Einzeldateien; die schmalen /4200/-Skalen sind HEALPix-aufgeteilt (12 Dateien).
 */
enum class AstrometryIndexPackage(
    val id: String,
    val titleResId: Int,
    val coverageResId: Int,
    /** Ungefähre Downloadgröße in Bytes (für den Größen-Hinweis in der UI). */
    val approxBytes: Long,
    /** Relative Serverpfade unter data.astrometry.net/ (Basename = Zieldatei im Index-Ordner). */
    val paths: List<String>,
) {
    DeepSkyTele(
        id = "deepsky-tele",
        titleResId = R.string.index_pkg_deepsky_tele_title,
        coverageResId = R.string.index_pkg_deepsky_tele_coverage,
        approxBytes = 309_317_760L,
        paths = listOf("4100/index-4107.fits", "4100/index-4108.fits", "4100/index-4109.fits"),
    ),
    DeepSkyNarrow(
        id = "deepsky-narrow",
        titleResId = R.string.index_pkg_deepsky_narrow_title,
        coverageResId = R.string.index_pkg_deepsky_narrow_coverage,
        approxBytes = 987_344_640L,
        paths = (0..11).map { "4200/index-4205-%02d.fits".format(it) } +
            (0..11).map { "4200/index-4206-%02d.fits".format(it) },
    ),
    ;

    /** Zieldateinamen im Index-Ordner (Basename der Serverpfade). */
    fun fileNames(): List<String> = paths.map { it.substringAfterLast('/') }
}

class AstrometryIndexDownloader(private val context: Context) {

    private fun indexDir(): File = AstrometryIndexManager.indexDir(context).apply { mkdirs() }

    /** Alle Dateien des Pakets vorhanden (und nicht leer)? */
    fun isInstalled(pkg: AstrometryIndexPackage): Boolean {
        val dir = indexDir()
        return pkg.fileNames().all { File(dir, it).let { f -> f.isFile && f.length() > 0L } }
    }

    /** Bereits auf dem Gerät liegende Bytes des Pakets (für Teil-Downloads / Anzeige). */
    fun installedBytes(pkg: AstrometryIndexPackage): Long {
        val dir = indexDir()
        return pkg.fileNames().sumOf { File(dir, it).let { f -> if (f.isFile) f.length() else 0L } }
    }

    /**
     * Lädt alle (noch fehlenden) Dateien des Pakets sequentiell. [onProgress] 0..1 kombiniert über
     * alle Dateien. Abbruchbar (coroutine cancel). Bereits vorhandene Dateien werden übersprungen.
     */
    suspend fun download(pkg: AstrometryIndexPackage, onProgress: suspend (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val dir = indexDir()
            val total = pkg.paths.size
            pkg.paths.forEachIndexed { i, path ->
                coroutineContext.ensureActive()
                val name = path.substringAfterLast('/')
                val dest = File(dir, name)
                if (dest.isFile && dest.length() > 0L) {
                    onProgress(((i + 1).toFloat() / total).coerceIn(0f, 1f))
                    return@forEachIndexed
                }
                val part = File(dir, "$name.part")
                part.delete()
                val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "SternbildMapper")
                }
                try {
                    conn.connect()
                    check(conn.responseCode in 200..299) {
                        context.getString(R.string.index_download_failed, conn.responseCode, name)
                    }
                    val fileTotal = conn.contentLengthLong.coerceAtLeast(1L)
                    conn.inputStream.use { input ->
                        FileOutputStream(part).use { out ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                copied += n
                                val fileFrac = (copied.toDouble() / fileTotal).coerceIn(0.0, 1.0)
                                onProgress(((i + fileFrac) / total).toFloat().coerceIn(0f, 1f))
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
            }
            onProgress(1f)
        }

    /** Löscht alle Dateien des Pakets aus dem Index-Ordner. */
    suspend fun remove(pkg: AstrometryIndexPackage) = withContext(Dispatchers.IO) {
        val dir = indexDir()
        pkg.fileNames().forEach { File(dir, it).delete() }
        // Etwaige Reste eines abgebrochenen Downloads mitnehmen.
        pkg.fileNames().forEach { File(dir, "$it.part").delete() }
    }

    companion object {
        private const val BASE_URL = "https://data.astrometry.net/"
    }
}
