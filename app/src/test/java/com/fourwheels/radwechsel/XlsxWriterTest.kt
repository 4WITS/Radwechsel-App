package com.fourwheels.radwechsel

import com.fourwheels.radwechsel.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private fun entries(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return result
    }

    @Test
    fun `enthaelt alle OOXML-Teile`() {
        val bytes = XlsxWriter.build(listOf("A", "B"), listOf(listOf("1", "2")))
        val names = entries(bytes).keys
        assertTrue(names.contains("[Content_Types].xml"))
        assertTrue(names.contains("_rels/.rels"))
        assertTrue(names.contains("xl/workbook.xml"))
        assertTrue(names.contains("xl/_rels/workbook.xml.rels"))
        assertTrue(names.contains("xl/worksheets/sheet1.xml"))
    }

    @Test
    fun `sheet enthaelt kopf und daten mit korrekten zellbezuegen`() {
        val bytes = XlsxWriter.build(
            headers = listOf("Kennzeichen", "Radsatznummer"),
            rows = listOf(listOf("MÜ-AB 123", "9999707"))
        )
        val sheet = entries(bytes).getValue("xl/worksheets/sheet1.xml")
        // Kopfzeile in Zeile 1, Daten in Zeile 2
        assertTrue(sheet.contains("r=\"A1\""))
        assertTrue(sheet.contains("r=\"B1\""))
        assertTrue(sheet.contains("r=\"A2\""))
        assertTrue(sheet.contains("Kennzeichen"))
        assertTrue(sheet.contains("9999707"))
    }

    @Test
    fun `escaped sonderzeichen`() {
        val bytes = XlsxWriter.build(listOf("H"), listOf(listOf("a & b < c > d")))
        val sheet = entries(bytes).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("a &amp; b &lt; c &gt; d"))
    }

    @Test
    fun `spaltenbezug jenseits Z`() {
        // 28 Spalten -> letzte Spalte muss AB sein
        val headers = (0 until 28).map { "H$it" }
        val bytes = XlsxWriter.build(headers, emptyList())
        val sheet = entries(bytes).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("r=\"AA1\""))
        assertTrue(sheet.contains("r=\"AB1\""))
    }
}
