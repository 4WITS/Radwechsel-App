package com.fourwheels.radwechsel.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.fourwheels.radwechsel.BuildConfig
import com.fourwheels.radwechsel.auth.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentrales Dateiprotokoll für die Fehlersuche im Feld.
 *
 * Wenn ein Radwechsel nicht durchgeht, hilft der Blick ins Logcat nicht weiter –
 * das Gerät steht in der Halle. Deshalb landet alles Wichtige hier: HTTP-Verkehr
 * (siehe [HttpFileLogger]), Queue-Ereignisse und Sitzungswechsel. Über
 * [LogExporter] geht das Ganze per Mail an die IT.
 *
 * Pro Tag eine Datei, älter als [RETENTION_DAYS] Tage wird beim ersten Schreiben
 * des neuen Tages gelöscht. Fehler beim Schreiben werden geschluckt: ein volles
 * Dateisystem darf keinen Radwechsel verhindern.
 */
@Singleton
class AppLog @Inject constructor(
    @ApplicationContext context: Context,
    private val tokenStore: TokenStore,
    private val deviceInfo: DeviceInfo
) {
    // Externer App-Ordner, damit die Logs zur Not auch per USB abgeholt werden
    // können. Der Ordner gehört der App und verschwindet mit ihr.
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME)

    private val lock = Any()
    private var lastPrunedDay: LocalDate? = null
    private var bannerWritten = false

    /**
     * Eine Zeile (oder einen mehrzeiligen Block) ins Protokoll schreiben. Jede
     * Zeile bekommt Zeitstempel und den gerade angemeldeten Benutzer, damit sich
     * bei einem Gerät, das durch mehrere Hände geht, alles zuordnen lässt.
     */
    fun log(tag: String, message: String) {
        val user = runCatching { tokenStore.username }.getOrNull()?.uppercase().orEmpty().ifEmpty { "-" }
        synchronized(lock) {
            runCatching {
                val now = ZonedDateTime.now()
                val today = now.toLocalDate()
                if (lastPrunedDay != today) {
                    dir.mkdirs()
                    prune(today)
                    lastPrunedDay = today
                }
                val file = File(dir, "$FILE_PREFIX$today$FILE_SUFFIX")
                // Die Gerätekennung kommt einmal pro App-Start und einmal in jede
                // neue Tagesdatei. Nur "wenn Datei neu" reichte nicht: nach einem
                // Update mitten am Tag stand sie dann nirgends.
                if (!bannerWritten || !file.exists()) {
                    file.appendText(deviceBanner(now))
                    bannerWritten = true
                }

                val prefix = "${TIMESTAMP.format(now)} $user [$tag]"
                val body = message.trimEnd().lines().joinToString("\n") { "$prefix $it" }
                file.appendText(body + "\n")
            }.onFailure { Log.w(TAG, "Log konnte nicht geschrieben werden: ${it.message}") }
        }
    }

    /**
     * Kopfzeilen einer neuen Tagesdatei. Alle Namensquellen stehen drin, weil
     * noch nicht feststeht, in welche davon Scalefusion seinen Gerätenamen
     * schreibt – siehe [DeviceInfo].
     */
    private fun deviceBanner(now: ZonedDateTime): String = buildString {
        val stamp = TIMESTAMP.format(now)
        appendLine("$stamp - [GERÄT] Gerätename: ${deviceInfo.name}")
        deviceInfo.candidates().forEach { (quelle, wert) ->
            appendLine("$stamp - [GERÄT]     $quelle = $wert")
        }
        appendLine("$stamp - [GERÄT] Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · " +
            "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
    }

    /** Vorhandene Protokolldateien, neueste zuerst. */
    fun files(): List<File> =
        dir.listFiles().orEmpty()
            .filter { FILE_NAME.matches(it.name) }
            .sortedByDescending { it.name }

    /** Alles löschen, was älter als [RETENTION_DAYS] Tage ist. */
    private fun prune(today: LocalDate) {
        val cutoff = today.minusDays(RETENTION_DAYS)
        dir.listFiles()?.forEach { file ->
            val day = FILE_NAME.matchEntire(file.name)?.groupValues?.get(1)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            // Unbekannte Dateien bleiben liegen – hier wird nur aufgeräumt, was
            // diese Klasse selbst angelegt hat.
            if (day != null && day.isBefore(cutoff)) file.delete()
        }
    }

    companion object {
        const val RETENTION_DAYS = 30L
        private const val TAG = "AppLog"
        private const val DIR_NAME = "logs"
        private const val FILE_PREFIX = "radwechsel-"
        private const val FILE_SUFFIX = ".log"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        private val FILE_NAME = Regex("""\Q$FILE_PREFIX\E(\d{4}-\d{2}-\d{2})\Q$FILE_SUFFIX\E""")
    }
}
