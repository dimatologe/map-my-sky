package com.codex.starmapper.processing

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FITS-Mehr-HDU-Scanner für solve-fields optionale `.corr`-Ausgabedatei (echte, von astrometry.net
 * selbst verifizierte Sterntreffer je Kachel-Solve). Schema PER GERÄTETEST BESTÄTIGT (13 Spalten,
 * immer in dieser Reihenfolge): field_x/field_y/field_ra/field_dec/index_x/index_y/index_ra/
 * index_dec (je 1D), index_id/field_id (je 1J), match_weight (1D), FLUX/BACKGROUND (je 1E).
 * [readFirstExtensionHeader]/[columns] lesen den Header (Spaltennamen/-typen/-anzahl/Zeilenzahl),
 * [readColumns] liest die eigentlichen Datenzeilen -- Spalten-Byte-Offsets werden dabei IMMER
 * dynamisch aus TTYPEn/TFORMn ermittelt, nichts ist hartkodiert (s. dortige KDoc).
 *
 * FITS-Grundform (Standard, nicht projektspezifisch): 80-Byte-ASCII-Karten, auf 2880 Byte
 * aufgefüllt, "END"-Karte schließt eine Header-Sequenz ab. Die Primär-HDU (SIMPLE/NAXIS) hat bei
 * solve-fields .corr üblicherweise keine Bilddaten (NAXIS=0) -- direkt danach (auf 2880 Byte
 * ausgerichtet) folgt die BINTABLE-Extension mit den eigentlichen Trefferzeilen. Binärtabellen-
 * Daten sind IMMER big-endian (FITS-Standard).
 */
object FitsBinaryTable {
    data class Header(
        val cards: Map<String, String>,
        // Byte-Position UNMITTELBAR NACH dieser (auf 2880 Byte aufgefüllten) Header-Karten-Sequenz
        // -- Start der zugehörigen Datenzeilen (falls NAXIS>0) bzw. der nächsten HDU.
        val dataOffset: Int,
    )

    /**
     * Liest EINE Header-Karten-Sequenz (80-Byte-Karten bis "END", auf 2880 Byte aufgefüllt) ab
     * [startOffset]. null, wenn dort keine "END"-Karte innerhalb der Bytegrenzen gefunden wird
     * (kaputte/zu kurze Datei).
     */
    private fun readHeader(bytes: ByteArray, startOffset: Int): Header? {
        val cards = linkedMapOf<String, String>()
        var offset = startOffset
        var endFound = false
        while (offset + 80 <= bytes.size) {
            val card = bytes.copyOfRange(offset, offset + 80).toString(Charsets.US_ASCII)
            offset += 80
            val key = card.take(8).trim()
            if (key == "END") {
                endFound = true
                break
            }
            if (key.isNotBlank() && card.getOrNull(8) == '=') {
                cards[key] = card.substring(10).substringBefore('/').trim().trim('\'').trim()
            }
        }
        if (!endFound) return null
        val headerLen = offset - startOffset
        val padded = headerLen + ((2880 - headerLen % 2880) % 2880)
        return Header(cards, startOffset + padded)
    }

    /**
     * Größe des Datenblocks (Bytes, VOR 2880-Auffüllung) einer HDU aus ihren Header-Karten --
     * für die Primär-HDU meist 0 (keine Bilddaten), bei einer BINTABLE-Extension
     * NAXIS1 (Bytes pro Zeile) * NAXIS2 (Zeilenzahl).
     */
    private fun dataSize(cards: Map<String, String>): Long {
        val naxis = cards["NAXIS"]?.toIntOrNull() ?: return 0L
        if (naxis <= 0) return 0L
        val naxis1 = cards["NAXIS1"]?.toLongOrNull() ?: return 0L
        if (naxis == 1) return naxis1
        val naxis2 = cards["NAXIS2"]?.toLongOrNull() ?: return 0L
        return naxis1 * naxis2
    }

    /**
     * Springt über die Primär-HDU (Header + ggf. Datenblock, beides auf 2880 Byte aufgefüllt) zur
     * ERSTEN Extension (z. B. der BINTABLE einer .corr-Datei) und liefert deren Header-Karten.
     * null, wenn keine Extension existiert oder die Datei kein gültiges Mehr-HDU-FITS ist.
     */
    fun readFirstExtensionHeader(bytes: ByteArray): Header? {
        val primary = readHeader(bytes, 0) ?: return null
        val dataBytes = dataSize(primary.cards)
        val paddedData = dataBytes + ((2880 - dataBytes % 2880) % 2880)
        val extensionStart = primary.dataOffset + paddedData
        if (extensionStart >= bytes.size) return null
        return readHeader(bytes, extensionStart.toInt())
    }

    /** Spaltennamen (TTYPEn) + -formate (TFORMn) in Deklarationsreihenfolge, für Diagnose-Zwecke. */
    fun columns(header: Header): List<Pair<String, String>> {
        val n = header.cards["TFIELDS"]?.toIntOrNull() ?: return emptyList()
        return (1..n).mapNotNull { i ->
            val name = header.cards["TTYPE$i"] ?: return@mapNotNull null
            val form = header.cards["TFORM$i"] ?: "?"
            name to form
        }
    }

    /** Byte-Position + -Größe + FITS-Typencode (letzter Buchstabe von TFORMn) einer Spalte in der Zeile. */
    private data class ColumnLayout(val offset: Int, val size: Int, val typeChar: Char)

    // TFORMn: optionale Wiederholungszahl + genau ein Typencode-Buchstabe, z.B. "1D"/"1J"/"1E" (die
    // drei in .corr real vorkommenden), auch "24A" o.ä. ist syntaktisch gültig.
    private val TFORM_PATTERN = Regex("""^(\d*)([A-Za-z])$""")

    /**
     * Byte-Größe EINES Elements (vor Wiederholungszahl-Multiplikation) für den FITS-Binärtabellen-
     * Typencode [typeChar] -- deckt die FITS-Standardtypen mit FESTER Breite ab (auch solche, die
     * in .corr nicht vorkommen, aber harmlos vor/nach den benötigten Spalten stehen könnten).
     * Bewusst OHNE 'P'/'Q' (variable-length Array-Deskriptoren): deren Elementgröße hängt NICHT vom
     * TFORM-Wiederholungspräfix ab (Deskriptor fester Größe, echte Daten liegen im Heap-Bereich der
     * Tabelle) -- käme so etwas vor, wäre die Zeilenbreiten-Annahme dieses einfachen Decoders
     * ohnehin hinfällig, also lieber sauber abbrechen (null) als falsch rechnen.
     */
    private fun fieldByteSize(typeChar: Char): Int? = when (typeChar) {
        'L' -> 1  // Logical ('T'/'F' als ASCII)
        'B' -> 1  // UInt8
        'A' -> 1  // ASCII-Zeichen (je Element)
        'I' -> 2  // Int16 (big-endian)
        'J' -> 4  // Int32 (big-endian) -- index_id/field_id
        'E' -> 4  // Float32 (big-endian IEEE754) -- FLUX/BACKGROUND
        'C' -> 8  // Complex (2x Float32)
        'K' -> 8  // Int64 (big-endian)
        'D' -> 8  // Float64 (big-endian IEEE754) -- field_x/field_y/*_ra/*_dec/match_weight
        'M' -> 16 // Double komplex (2x Float64)
        else -> null // z.B. 'P'/'Q' (variable-length) oder unbekannter Code -> Breite nicht bestimmbar
    }

    /**
     * Double-Wert eines Feldes vom Typ [typeChar] an [offset] in [buffer] -- nur für Typen, deren
     * Wert sich sinnvoll als Double ausdrücken lässt (numerische Skalartypen). null für Text/
     * Logical/Komplex -- der Aufrufer verwirft dann die GANZE Spaltenanfrage (s. [readColumns]).
     */
    private fun numericValue(buffer: ByteBuffer, offset: Int, typeChar: Char): Double? = when (typeChar) {
        'D' -> buffer.getDouble(offset)
        'E' -> buffer.getFloat(offset).toDouble()
        'J' -> buffer.getInt(offset).toDouble()
        'K' -> buffer.getLong(offset).toDouble()
        'I' -> buffer.getShort(offset).toDouble()
        'B' -> (buffer.get(offset).toInt() and 0xFF).toDouble()
        else -> null // 'A'/'L'/'C'/'M' -- keine sinnvolle Double-Interpretation
    }

    /**
     * Baut die Byte-Layouts ALLER Spalten aus TTYPEn/TFORMn (Deklarationsreihenfolge = Byte-
     * Reihenfolge in der FITS-Zeile, FITS-Standard) -- dynamisch, nichts ist hartkodiert. null wenn
     * TFIELDS nicht zur tatsächlichen Anzahl gefundener TTYPEn/TFORMn-Paare passt (Lücke in der
     * Nummerierung würde die kumulative Offset-Berechnung ab der Lücke verfälschen) oder irgendeine
     * Spalte (auch eine, die niemand anfordert!) einen TFORM-Typencode mit unbekannter Breite hat --
     * ohne dessen Größe lässt sich der Offset aller nachfolgenden Spalten nicht mehr bestimmen.
     */
    private fun columnLayouts(header: Header): Map<String, ColumnLayout>? {
        val declaredFieldCount = header.cards["TFIELDS"]?.toIntOrNull() ?: return null
        val cols = columns(header)
        if (cols.size != declaredFieldCount) return null
        var offset = 0
        val layouts = LinkedHashMap<String, ColumnLayout>()
        for ((name, form) in cols) {
            val match = TFORM_PATTERN.matchEntire(form.trim()) ?: return null
            val repeat = match.groupValues[1].toIntOrNull() ?: 1
            val typeChar = match.groupValues[2].uppercase().first()
            val unitSize = fieldByteSize(typeChar) ?: return null
            val size = unitSize * repeat
            layouts[name] = ColumnLayout(offset, size, typeChar)
            offset += size
        }
        return layouts
    }

    /**
     * Liest die NAXIS2 Datenzeilen der Extension [header] aus [bytes] und liefert für jede
     * angeforderte Spalte in [columnNames] eine [DoubleArray] der Länge NAXIS2 (eine Zeile pro
     * Element, in Datei-Reihenfolge). Spalten-Byte-Offsets werden IMMER dynamisch aus TTYPEn/TFORMn
     * ermittelt (nichts ist hartkodiert) -- ändert eine künftige Portierung die Spaltenreihenfolge
     * oder fügt Spalten hinzu/entfernt sie, passt sich dieser Decoder automatisch an, SOLANGE die
     * angeforderten Spalten mit einem unterstützten Zahlentyp weiter existieren. FITS-Binärtabellen
     * sind IMMER big-endian.
     *
     * Defensiv: liefert null (NIE eine Exception, NIE falsch interpretierte Werte), wenn
     *  - NAXIS1/NAXIS2 fehlen, nicht parsebar oder <= 0 sind,
     *  - [columnLayouts] scheitert (Spaltenlücke oder unbekannter TFORM-Typ irgendwo in der Tabelle),
     *  - eine der Spalten aus [columnNames] im Schema fehlt,
     *  - eine angeforderte Spalte einen Typ hat, der sich nicht sinnvoll als Double lesen lässt,
     *  - die aus TFORMn aufsummierte Zeilenbreite NICHT exakt NAXIS1 ergibt (zweite, unabhängige
     *    Absicherung derselben Schema-Annahme -- z.B. falls TFIELDS zufällig zur Spaltenzahl passt,
     *    aber falsche Spalten meint),
     *  - [bytes] zu kurz für NAXIS2 vollständige Zeilen ist (abgeschnittene/kaputte Datei).
     */
    fun readColumns(bytes: ByteArray, header: Header, columnNames: List<String>): Map<String, DoubleArray>? {
        val naxis1 = header.cards["NAXIS1"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val naxis2 = header.cards["NAXIS2"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val layouts = columnLayouts(header) ?: return null
        val rowWidth = layouts.values.sumOf { it.size }
        if (rowWidth != naxis1) return null
        val requested = columnNames.map { name -> layouts[name] ?: return null }
        val requiredBytes = header.dataOffset.toLong() + rowWidth.toLong() * naxis2.toLong()
        if (requiredBytes > bytes.size.toLong()) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN) // FITS ist IMMER big-endian
        val out = columnNames.associateWith { DoubleArray(naxis2) }
        for (row in 0 until naxis2) {
            val rowOffset = header.dataOffset + row * rowWidth
            for (i in columnNames.indices) {
                val col = requested[i]
                val value = numericValue(buffer, rowOffset + col.offset, col.typeChar) ?: return null
                out.getValue(columnNames[i])[row] = value
            }
        }
        return out
    }
}
