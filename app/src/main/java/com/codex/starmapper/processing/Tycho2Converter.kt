package com.codex.starmapper.processing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

/**
 * Wandelt die 20 rohen tyc2.dat.NN.gz-Teile (s. [Tycho2Downloader]) EINMALIG in die von
 * [Tycho2Store] verwaltete SQLite-Datenbank um. Streaming-Parse (kein Volltext im Speicher --
 * ~499 MB unkomprimiert wären ein Heap-Risiko, App-Limit lt. Diagnose-Dump 512 MB).
 *
 * Byte-Spalten-Layout (1-indiziert, aus dem echten ReadMe UND gegen eine echte heruntergeladene
 * Zeile verifiziert -- HIP-769-Stichprobe deckungsgleich mit stars.8.json: RA 2.36680804° vs.
 * 2.3668°, Dec 1.24404346° vs. 1.244°, s. Session-Historie -- NICHT geschätzt):
 *   TYC1(1-4) TYC2(6-10) TYC3(12) pflag(14, 'X'=keine Mittelposition) RAmdeg(16-27) DEmdeg(29-40)
 *   BTmag(111-116) VTmag(124-129) HIP(143-148, optional).
 * pflag='X' UND Zeilen mit belegter HIP-Nummer werden verworfen (Letztere bereits über stars.8.json
 * abgedeckt) -- Tycho-2 bleibt dadurch rein ADDITIV, keine Duplikate.
 */
class Tycho2Converter(private val context: Context) {

    suspend fun convert(onProgress: suspend (Float) -> Unit) = withContext(Dispatchers.IO) {
        val dbFile = Tycho2Store.dbFile(context)
        dbFile.delete()
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL("CREATE TABLE stars (ra REAL, dec REAL, mag REAL, bv REAL, tyc TEXT)")
            db.beginTransaction()
            try {
                val insert = db.compileStatement(
                    "INSERT INTO stars (ra, dec, mag, bv, tyc) VALUES (?, ?, ?, ?, ?)",
                )
                for (part in 0 until Tycho2Downloader.PART_COUNT) {
                    coroutineContext.ensureActive()
                    val file = File(Tycho2Store.rawDir(context), Tycho2Downloader.partFileName(part))
                    if (file.isFile) {
                        BufferedReader(
                            InputStreamReader(GZIPInputStream(file.inputStream()), Charsets.ISO_8859_1),
                        ).use { reader ->
                            reader.forEachLine { line -> insertLine(insert, line) }
                        }
                    }
                    onProgress(((part + 1).toFloat() / Tycho2Downloader.PART_COUNT).coerceIn(0f, 1f))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL("CREATE INDEX idx_stars_dec ON stars (dec)")
        } finally {
            db.close()
        }
        onProgress(1f)
    }

    private fun insertLine(insert: android.database.sqlite.SQLiteStatement, line: String) {
        if (line.length < 148) return
        if (line[13] == 'X') return // pflag: keine Mittelposition vorhanden
        if (line.substring(142, 148).isNotBlank()) return // HIP belegt -> schon in stars.8.json
        val ra = line.substring(15, 27).trim().toDoubleOrNull() ?: return
        val dec = line.substring(28, 40).trim().toDoubleOrNull() ?: return
        val bt = line.substring(110, 116).trim().toDoubleOrNull()
        val vt = line.substring(123, 129).trim().toDoubleOrNull()
        val mag = vt ?: bt ?: return
        val tyc = "${line.substring(0, 4).trim()}-${line.substring(5, 10).trim()}-${line.substring(11, 12).trim()}"
        insert.clearBindings()
        insert.bindDouble(1, ra)
        insert.bindDouble(2, dec)
        insert.bindDouble(3, mag)
        if (bt != null && vt != null) insert.bindDouble(4, bt - vt) else insert.bindNull(4)
        insert.bindString(5, tyc)
        insert.executeInsert()
    }
}
