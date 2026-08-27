package com.fourwheels.radwechsel.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimaler XLSX-Writer ohne externe Bibliothek. Erzeugt eine einzelne Tabelle
 * (OOXML/SpreadsheetML) als ZIP im Speicher – reicht für einfache Reports aus
 * Kopfzeile + Datenzeilen. Alle Zellen werden als Text geschrieben.
 */
object XlsxWriter {

    fun build(headers: List<String>, rows: List<List<String>>): ByteArray {
        val sheet = buildSheetXml(headers, rows)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putEntry("[Content_Types].xml", CONTENT_TYPES)
            zip.putEntry("_rels/.rels", RELS)
            zip.putEntry("xl/workbook.xml", WORKBOOK)
            zip.putEntry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putEntry("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun buildSheetXml(headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        var r = 1
        appendRow(sb, r++, headers)
        for (row in rows) appendRow(sb, r++, row)
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun appendRow(sb: StringBuilder, rowNum: Int, cells: List<String>) {
        sb.append("<row r=\"").append(rowNum).append("\">")
        for ((i, value) in cells.withIndex()) {
            val ref = colRef(i) + rowNum
            sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                .append(escape(value)).append("</t></is></c>")
        }
        sb.append("</row>")
    }

    /** 0 -> A, 25 -> Z, 26 -> AA, ... */
    private fun colRef(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') append(' ') else append(c)
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Radwechsel" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
}
