package com.codex.starmapper.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FitsBinaryTableTest {

    private fun header(cards: Map<String, String>, dataOffset: Int = 0) =
        FitsBinaryTable.Header(cards, dataOffset)

    private fun baseCards(naxis1: Int, naxis2: Int, vararg columns: Pair<String, String>): Map<String, String> {
        val cards = linkedMapOf(
            "NAXIS1" to naxis1.toString(),
            "NAXIS2" to naxis2.toString(),
            "TFIELDS" to columns.size.toString(),
        )
        columns.forEachIndexed { i, (name, form) ->
            cards["TTYPE${i + 1}"] = name
            cards["TFORM${i + 1}"] = form
        }
        return cards
    }

    @Test
    fun readColumnsExtractsMultipleRows() {
        val cards = baseCards(naxis1 = 16, naxis2 = 3, "A" to "1D", "B" to "1D")
        val bytes = ByteBuffer.allocate(16 * 3).order(ByteOrder.BIG_ENDIAN).apply {
            putDouble(1.0); putDouble(2.0)
            putDouble(3.0); putDouble(4.0)
            putDouble(5.0); putDouble(6.0)
        }.array()

        val result = FitsBinaryTable.readColumns(bytes, header(cards), listOf("A", "B"))

        assertNotNull(result)
        assertEquals(listOf(1.0, 3.0, 5.0), result!!.getValue("A").toList())
        assertEquals(listOf(2.0, 4.0, 6.0), result.getValue("B").toList())
    }

    @Test
    fun readColumnsDecodesBigEndian() {
        // Big-endian 0x00000100 = 256; dieselben 4 Bytes als Little-Endian gelesen ergäben 0x00010000 =
        // 65536 -- ein falsch verdrahteter ByteOrder würde diesen Test zuverlässig zum Scheitern bringen.
        val cards = baseCards(naxis1 = 4, naxis2 = 1, "A" to "1J")
        val bytes = byteArrayOf(0x00, 0x00, 0x01, 0x00)

        val result = FitsBinaryTable.readColumns(bytes, header(cards), listOf("A"))

        assertNotNull(result)
        assertEquals(256.0, result!!.getValue("A")[0], 0.0)
    }

    @Test
    fun readColumnsReturnsNullForMissingColumn() {
        val cards = baseCards(naxis1 = 8, naxis2 = 1, "A" to "1D")
        val bytes = ByteArray(8)

        val result = FitsBinaryTable.readColumns(bytes, header(cards), listOf("A", "B"))

        assertNull(result)
    }

    @Test
    fun readColumnsReturnsNullOnRowWidthMismatch() {
        // NAXIS1 behauptet 20 Byte/Zeile, die beiden 1D-Spalten ergeben tatsächlich nur 16.
        val cards = baseCards(naxis1 = 20, naxis2 = 1, "A" to "1D", "B" to "1D")
        val bytes = ByteArray(20)

        val result = FitsBinaryTable.readColumns(bytes, header(cards), listOf("A"))

        assertNull(result)
    }

    @Test
    fun readColumnsReturnsNullForTruncatedFile() {
        // 5 Zeilen a 8 Byte angekündigt (40 Byte nötig), aber nur 16 Byte tatsächlich vorhanden.
        val cards = baseCards(naxis1 = 8, naxis2 = 5, "A" to "1D")
        val bytes = ByteArray(16)

        val result = FitsBinaryTable.readColumns(bytes, header(cards), listOf("A"))

        assertNull(result)
    }

    @Test
    fun readColumnsReturnsNullForUnsupportedTformType() {
        // "Z" ist kein bekannter FITS-TFORM-Typencode -- selbst wenn nur "A" angefragt wird, muss das
        // Layout wholesale fehlschlagen (Spaltenoffsets werden für ALLE Spalten in Deklarations-
        // reihenfolge berechnet, ein unbekannter Typ irgendwo darin macht alle nachfolgenden falsch).
        val cards = baseCards(naxis1 = 12, naxis2 = 1, "A" to "1D", "Z" to "1Z")
        val bytes = ByteArray(12)

        val result = FitsBinaryTable.readColumns(bytes, header(cards), listOf("A"))

        assertNull(result)
    }

    @Test
    fun readColumnsHonorsNonZeroDataOffset() {
        // Simuliert eine vorangehende Primär-HDU: die eigentlichen Zeilen beginnen erst ab Byte 100,
        // nicht bei 0 -- readColumns darf die führenden Bytes nicht mitlesen.
        val cards = baseCards(naxis1 = 8, naxis2 = 1, "A" to "1D")
        val junk = ByteArray(100) { 0x7F }
        val row = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(42.5).array()
        val bytes = junk + row

        val result = FitsBinaryTable.readColumns(bytes, header(cards, dataOffset = 100), listOf("A"))

        assertNotNull(result)
        assertEquals(42.5, result!!.getValue("A")[0], 0.0)
    }
}
