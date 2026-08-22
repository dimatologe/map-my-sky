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
 * Lädt den rohen Tycho-2-Katalog direkt vom CDS-Archiv (cdsarc.cds.unistra.fr) -- 20 gzip-Teile
 * á ~7,7-8,2 MB (zusammen ~160 MB), Größen/Format am echten Server verifiziert (s. Plan/Memory
 * "Tycho-2 als optionaler Tiefen-Sternkatalog"). Nach vollständigem Download wandelt
 * [Tycho2Converter] die Teile EINMALIG in die von [Tycho2Store] verwaltete SQLite-Datenbank um.
 * Mirror von [AstrometryIndexDownloader]s Download-Schleife (Fortsetzbarkeit, .part-Datei, Fortschritt).
 */
class Tycho2Downloader(private val context: Context) {

    private fun rawDir(): File = Tycho2Store.rawDir(context).apply { mkdirs() }

    fun isDownloaded(): Boolean {
        val dir = rawDir()
        return (0 until PART_COUNT).all { n -> File(dir, partFileName(n)).let { it.isFile && it.length() > 0L } }
    }

    fun installedBytes(): Long {
        val dir = rawDir()
        return (0 until PART_COUNT).sumOf { n -> File(dir, partFileName(n)).let { if (it.isFile) it.length() else 0L } }
    }

    /** Lädt alle (noch fehlenden) 20 Teile sequenziell. [onProgress] 0..1 über alle Teile kombiniert.
     *  Abbruchbar (coroutine cancel); bereits vorhandene Teile werden übersprungen. */
    suspend fun download(onProgress: suspend (Float) -> Unit) = withContext(Dispatchers.IO) {
        val dir = rawDir()
        for (i in 0 until PART_COUNT) {
            coroutineContext.ensureActive()
            val name = partFileName(i)
            val dest = File(dir, name)
            if (dest.isFile && dest.length() > 0L) {
                onProgress(((i + 1).toFloat() / PART_COUNT).coerceIn(0f, 1f))
                continue
            }
            val part = File(dir, "$name.part")
            part.delete()
            val conn = (URL("$BASE_URL$name").openConnection() as HttpURLConnection).apply {
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
                            onProgress(((i + fileFrac) / PART_COUNT).toFloat().coerceIn(0f, 1f))
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

    /** Löscht alle rohen .gz-Teile (nach erfolgreicher Konvertierung, oder bei Abbruch/Neustart). */
    fun deleteRaw() {
        val dir = rawDir()
        for (n in 0 until PART_COUNT) {
            File(dir, partFileName(n)).delete()
            File(dir, "${partFileName(n)}.part").delete()
        }
    }

    companion object {
        private const val BASE_URL = "https://cdsarc.cds.unistra.fr/ftp/I/259/"
        const val PART_COUNT = 20
        fun partFileName(n: Int): String = "tyc2.dat.%02d.gz".format(n)
    }
}
