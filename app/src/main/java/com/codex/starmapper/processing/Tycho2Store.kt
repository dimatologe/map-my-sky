package com.codex.starmapper.processing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.codex.starmapper.domain.CatalogStar
import com.codex.starmapper.domain.SkyPoint
import java.io.File

/**
 * Ablage-Ort + Lesezugriff für den konvertierten Tycho-2-Tiefkatalog (2.539.913 Sterne, vollständig
 * bis ca. Mag. 11,5, s. Plan "Tycho-2 als optionaler Tiefen-Sternkatalog"). [Tycho2Downloader] lädt
 * die rohen CDS-Teile, [Tycho2Converter] wandelt sie einmalig in die hier verwaltete SQLite-Datenbank
 * um. Rein additiv zu stars.6/8.json: HIP-verknüpfte Zeilen wurden beim Konvertieren bereits
 * ausgeschlossen (s. Tycho2Converter), keine Duplikate mit dem bestehenden Hipparcos-Pfad.
 */
object Tycho2Store {
    fun baseDir(context: Context): File = File(context.filesDir, "tycho2")
    fun rawDir(context: Context): File = File(baseDir(context), "raw")
    fun dbFile(context: Context): File = File(baseDir(context), "tycho2.db")

    fun isInstalled(context: Context): Boolean = dbFile(context).let { it.isFile && it.length() > 0L }

    /** Löscht die gesamte Tycho-2-Ablage (Datenbank + etwaige rohe Teilreste) restlos. */
    fun delete(context: Context) {
        baseDir(context).deleteRecursively()
    }

    /**
     * Sterne im Bereich [box] bis [maxMagnitude]. Leere Liste, wenn nicht installiert oder die
     * Abfrage aus irgendeinem Grund fehlschlägt (z.B. beschädigte Datei) -- ruft NICHT crashend
     * durch, s. Aufrufstelle in StarMapperApp.syncStarLayer.
     */
    fun queryStars(context: Context, box: RaDecBox, maxMagnitude: Float): List<CatalogStar> {
        val file = dbFile(context)
        if (!file.isFile) return emptyList()
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            return emptyList()
        }
        return try {
            val args = mutableListOf(box.decMinDeg.toString(), box.decMaxDeg.toString())
            // RA-sicher gegen den 0°/360°-Sprung: raMinDeg/raMaxDeg sind NICHT auf 0 bis 360 Grad normiert
            // (s. RaDecBox-Kommentar) -- außerhalb dieses Bereichs liegende Ränder werden hier in
            // eine ODER-Bedingung über den Sprung hinweg aufgelöst. Deckt der Bereich bereits den
            // ganzen Himmel ab (>= 360° Spannweite, z.B. 360°-Panorama), entfällt der RA-Filter ganz.
            val span = box.raMaxDeg - box.raMinDeg
            val raClause = when {
                span >= 360.0 -> "1=1"
                box.raMinDeg < 0.0 -> {
                    args += (box.raMinDeg + 360.0).toString(); args += box.raMaxDeg.toString()
                    "(ra >= ? OR ra <= ?)"
                }
                box.raMaxDeg > 360.0 -> {
                    args += box.raMinDeg.toString(); args += (box.raMaxDeg - 360.0).toString()
                    "(ra >= ? OR ra <= ?)"
                }
                else -> {
                    args += box.raMinDeg.toString(); args += box.raMaxDeg.toString()
                    "ra BETWEEN ? AND ?"
                }
            }
            args += maxMagnitude.toString()
            val sql = "SELECT ra, dec, mag, bv, tyc FROM stars WHERE dec BETWEEN ? AND ? AND $raClause AND mag <= ?"
            val out = ArrayList<CatalogStar>()
            db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                val iRa = cursor.getColumnIndexOrThrow("ra")
                val iDec = cursor.getColumnIndexOrThrow("dec")
                val iMag = cursor.getColumnIndexOrThrow("mag")
                val iBv = cursor.getColumnIndexOrThrow("bv")
                val iTyc = cursor.getColumnIndexOrThrow("tyc")
                while (cursor.moveToNext()) {
                    val tyc = cursor.getString(iTyc)
                    out += CatalogStar(
                        id = "TYC $tyc",
                        point = SkyPoint(cursor.getDouble(iRa).toFloat(), cursor.getDouble(iDec).toFloat()),
                        magnitude = cursor.getDouble(iMag).toFloat(),
                        bv = if (cursor.isNull(iBv)) null else cursor.getDouble(iBv).toFloat(),
                        name = "TYC $tyc",
                    )
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        } finally {
            db.close()
        }
    }
}
