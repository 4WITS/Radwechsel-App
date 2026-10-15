package com.fourwheels.radwechsel.logging

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.fourwheels.radwechsel.BuildConfig
import com.fourwheels.radwechsel.auth.TokenStore
import com.fourwheels.radwechsel.ui.radwechsel.QueueItem
import com.fourwheels.radwechsel.ui.radwechsel.QueueManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Empfänger des Diagnoseprotokolls. */
private const val SUPPORT_MAIL = "it@4wheels.de"

/**
 * So viel Protokoll wird angehängt – neueste Tage zuerst. An Tagen mit vielen
 * Radwechseln kommt einiges zusammen, gleichzeitig muss die Mail noch durch die
 * Größenbeschränkung des Mailservers passen.
 */
private const val MAX_RAW_BYTES = 20L * 1024 * 1024

/**
 * Packt Diagnosekopf und die letzten Protokolltage in *eine* Datei und baut
 * daraus eine fertige Mail an die IT.
 *
 * Der Kopf ist der eigentliche Wert: er beantwortet die Fragen, die bei "Daten
 * gehen nicht durch" als Erstes kommen – wer war angemeldet, in welchem
 * Räderhotel, welche App-Version, was hängt in der Warteschlange fest und mit
 * welchem Fehler.
 */
@Singleton
class LogExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLog: AppLog,
    private val deviceInfo: DeviceInfo,
    private val tokenStore: TokenStore,
    private val queueManager: QueueManager
) {
    /**
     * Baut die Mail. Wirft nicht – schlägt das Schreiben fehl, kommt null zurück
     * und der Aufrufer zeigt einen Hinweis.
     */
    fun buildMailIntent(): Intent? = runCatching {
        val now = ZonedDateTime.now()
        val user = tokenStore.username?.uppercase().orEmpty().ifEmpty { "unbekannt" }
        val file = writeExportFile(now, user)

        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val subject = "Radwechsel Protokoll – $user – ${SUBJECT_TIME.format(now)}"

        Intent(Intent.ACTION_SEND).apply {
            // message/rfc822 zielt auf Mail-Apps statt auf jeden Messenger
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_MAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, mailBody(user))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    private fun mailBody(user: String) = buildString {
        appendLine("Diagnoseprotokoll der Radwechsel-App.")
        appendLine()
        appendLine("Benutzer:   $user")
        appendLine("Räderhotel: ${wheelhotelLabel()}")
        appendLine("Gerät:      ${deviceInfo.name}")
        appendLine("App:        ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine()
        appendLine("Was ist passiert?")
        appendLine()
        appendLine()
        appendLine("Details siehe Anhang.")
    }

    /**
     * Schreibt Kopf + Protokolle in eine Textdatei im Cache. Der Dateiname trägt
     * Benutzer und Zeitstempel, damit er im Postfach der IT für sich steht.
     */
    private fun writeExportFile(now: ZonedDateTime, user: String): File {
        val outDir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        // Alte Exporte aufräumen, sonst sammeln sie sich im Cache
        outDir.listFiles()?.forEach { it.delete() }

        val safeUser = user.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "unbekannt" }
        val out = File(outDir, "radwechsel-log_${safeUser}_${FILE_TIME.format(now)}.log")

        val logFiles = selectLogFiles()
        BufferedOutputStream(out.outputStream()).use { sink ->
            fun write(text: String) = sink.write(text.toByteArray(Charsets.UTF_8))
            write(header(now, logFiles))
            // Älteste zuerst, damit sich die Datei von oben nach unten liest.
            // Gestreamt statt eingelesen: ein voller Tag kann groß werden.
            logFiles.reversed().forEach { log ->
                write("\n───── ${log.name} ─────\n")
                runCatching { log.inputStream().use { it.copyTo(sink) } }
                    .onFailure { write("<nicht lesbar: ${it.message}>\n") }
            }
        }
        return out
    }

    /**
     * Neueste Tage zuerst einsammeln, solange sie in [MAX_RAW_BYTES] passen.
     * Der neueste Tag kommt immer mit – ein einzelner großer Tag darf nicht dazu
     * führen, dass gar nichts im Anhang landet.
     */
    private fun selectLogFiles(): List<File> {
        var budget = MAX_RAW_BYTES
        val selected = mutableListOf<File>()
        for (file in appLog.files()) {
            if (selected.isNotEmpty() && file.length() > budget) break
            selected += file
            budget -= file.length()
        }
        return selected
    }

    private fun header(now: ZonedDateTime, logFiles: List<File>): String = buildString {
        appendLine("═══ Radwechsel · Diagnoseprotokoll ═══")
        appendLine("Erstellt:       ${AppLog.TIMESTAMP.format(now)}")
        appendLine("App:            ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
            "${BuildConfig.BUILD_TYPE}")
        appendLine("Gerätename:     ${deviceInfo.name}")
        appendLine("Hardware:       ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android:        ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Benutzer:       ${tokenStore.username?.uppercase() ?: "–"}" +
            if (tokenStore.usernameLocked) " (gesperrt)" else "")
        appendLine("Räderhotel:     ${wheelhotelLabel()}")
        appendLine("Sitzung:        ${sessionLabel()}")
        appendLine()

        val pending = queueManager.pendingItems.value
        val failed  = queueManager.failedItems.value
        appendLine("Warteschlange:  ${pending.size} ausstehend, ${failed.size} fehlgeschlagen")
        failed.forEach { appendLine("  FEHLGESCHLAGEN ${describe(it)}") }
        pending.forEach { appendLine("  AUSSTEHEND     ${describe(it)}") }
        appendLine()

        val raw = logFiles.sumOf { it.length() }
        val onDevice = appLog.files()
        appendLine("Protokolle:     ${logFiles.size} von ${onDevice.size} Tage(n), " +
            "${raw / 1024} KB roh, Aufbewahrung ${AppLog.RETENTION_DAYS} Tage")
        if (logFiles.isNotEmpty()) {
            appendLine("                ${logFiles.last().name} … ${logFiles.first().name}")
        }
        if (logFiles.size < onDevice.size) {
            appendLine("                ACHTUNG: ältere Tage fehlen (Grenze ${MAX_RAW_BYTES / 1024 / 1024} MB)")
        }
        appendLine("Hinweis:        Auth-Header sind geschwärzt, Bodys werden nicht protokolliert.")
        appendLine("═══════════════════════════════════════")
    }

    private fun describe(item: QueueItem): String = buildString {
        append(item.licensePlate)
        append(" · KST ${item.wheelhotelId ?: "–"}")
        append(" · ${item.torque} Nm")
        append(" · ${item.startedAt} – ${item.finishedAt} von ${item.username ?: "–"}")
        if (item.attemptCount > 0) append(" · ${item.attemptCount} Versuch(e)")
        item.errorMessage?.let { append(" · Fehler: $it") }
    }

    private fun wheelhotelLabel(): String {
        val name = tokenStore.lastWheelhotelName
        val id = tokenStore.lastWheelhotelId
        return when {
            name != null && id != null -> "$name ($id)"
            name != null -> name
            id != null -> id
            else -> "–"
        }
    }

    private fun sessionLabel(): String = when {
        tokenStore.accessToken == null -> "kein Token (abgemeldet oder abgelaufen)"
        tokenStore.isTokenValid ->
            "gültig bis ${AppLog.TIMESTAMP.format(
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(tokenStore.tokenExpiry), ZoneId.systemDefault())
            )}"
        else -> "Token abgelaufen, Refresh ${if (tokenStore.refreshToken != null) "vorhanden" else "fehlt"}"
    }

    private companion object {
        const val SHARE_DIR = "shared-logs"
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        val SUBJECT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }
}
