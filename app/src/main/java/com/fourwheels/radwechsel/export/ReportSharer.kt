package com.fourwheels.radwechsel.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Teilt den Excel-Report an eine Mail-App – bevorzugt Outlook.
 *
 * Bewusst KEINE eigenen SMTP-Zugangsdaten: die App erzeugt nur die Datei und
 * übergibt sie per content://-URI an Outlook (auf den M3-Geräten per Scalefusion
 * mit radwechsel@4wheels.de eingerichtet). Outlook verschickt aus dem Konto
 * heraus (moderne OAuth-Anmeldung) – ein Tipp auf „Senden" genügt.
 */
object ReportSharer {

    private const val OUTLOOK_PACKAGE = "com.microsoft.office.outlook"
    private const val XLSX_MIME =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** @return true, wenn eine App zum Senden geöffnet wurde. */
    fun shareToOutlook(
        context: Context,
        xlsx: ByteArray,
        fileName: String,
        recipient: String,
        subject: String,
        body: String
    ): Boolean {
        val uri = try {
            writeToCache(context, xlsx, fileName)
        } catch (e: Exception) {
            Toast.makeText(context, "Report konnte nicht erstellt werden", Toast.LENGTH_LONG).show()
            return false
        }

        val base = Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME
            if (recipient.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 1. Direkt Outlook, falls installiert.
        val outlookInstalled = context.packageManager
            .getLaunchIntentForPackage(OUTLOOK_PACKAGE) != null
        if (outlookInstalled) {
            val outlook = Intent(base).setPackage(OUTLOOK_PACKAGE)
            try {
                context.startActivity(outlook.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                // Fällt unten auf den Auswahldialog zurück.
            }
        }

        // 2. Sonst System-Auswahldialog (irgendeine Mail-App).
        return try {
            val chooser = Intent.createChooser(base, "Report senden mit …")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Keine Mail-App gefunden", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun writeToCache(context: Context, bytes: ByteArray, fileName: String): Uri {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
