package com.fourwheels.radwechsel.ui.radwechsel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fourwheels.radwechsel.export.XlsxWriter
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.repository.AuthRepository
import com.fourwheels.radwechsel.session.CompletedRadwechsel
import com.fourwheels.radwechsel.session.SessionLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val ISO_MS = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    .withZone(ZoneOffset.UTC)

private val REPORT_DATETIME = DateTimeFormatter
    .ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

private val REPORT_DATE = DateTimeFormatter
    .ofPattern("dd.MM.yyyy")
    .withZone(ZoneId.systemDefault())

private val REPORT_TIME = DateTimeFormatter
    .ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private val FILE_STAMP = DateTimeFormatter
    .ofPattern("yyyy-MM-dd_HH-mm")
    .withZone(ZoneId.systemDefault())

/** Report geht immer von radwechsel@4wheels.de an radwechsel@4wheels.de (fix). */
private const val REPORT_RECIPIENT = "radwechsel@4wheels.de"

/**
 * Radsatz-Barcode-Format: [optional $] PRÄFIX(11/70/71) NUMMER SUFFIX([optional %]1/3/7/9
 * oder A–D) [optional Endzeichen]. Präfix + Suffix werden verworfen – übrig bleibt die
 * eigentliche Radsatznummer (Gruppe 1). Reine manuelle Zifferneingabe matcht nicht
 * (kein Suffix) und bleibt unverändert.
 */
private val WHEELSET_REGEX = Regex("^\\$?(?:11|70|71)(\\d+)(?:%[1379]|[A-D]).?$")

enum class RadwechselPhase { EINGABE, LAUFEND, ERFOLG }

data class RadwechselUiState(
    val phase: RadwechselPhase = RadwechselPhase.EINGABE,
    val kennzeichen: String = "",
    val radsatznummer: String = "",
    val torque: String = "110",
    val timerSeconds: Int = 0,
    val startedAt: String = "",
    val finishedAt: String = "",
    val error: String? = null
)

/** Alles, was zum Teilen des Reports an Outlook gebraucht wird. */
data class ReportPayload(
    val xlsx: ByteArray,
    val fileName: String,
    val recipient: String,
    val subject: String,
    val body: String
)

@HiltViewModel
class RadwechselViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val queueManager: QueueManager,
    private val sessionLog: SessionLog
) : ViewModel() {

    var uiState by mutableStateOf(RadwechselUiState())
        private set

    private var timerJob: Job? = null
    private var startInstant: Instant? = null

    var wheelhotel: Wheelhotel? = null
    var username: String = ""

    /** Wird true, sobald ein vollständiger Radsatz-Barcode erkannt wurde – die UI
     *  blendet dann die Tastatur aus. Danach über [consumeRadsatzScanCompleted] quittieren. */
    var radsatzScanCompleted by mutableStateOf(false)
        private set

    fun consumeRadsatzScanCompleted() { radsatzScanCompleted = false }

    val pendingItems = queueManager.pendingItems
    val failedItems  = queueManager.failedItems
    val sessionItems = sessionLog.items

    // ── Eingabe ─────────────────────────────────────────────────────────────

    fun onKennzeichenChange(v: String) {
        if (v.length <= 20) {
            uiState = uiState.copy(kennzeichen = v.uppercase(), error = null)
        }
    }

    fun onRadsatznummerChange(v: String) {
        // Erlaubte Zeichen behalten (Ziffern, A–D, $, %), damit ein voller Scan
        // erkannt werden kann; passt der Barcode aufs Muster, bleibt nur die
        // eigentliche Nummer (Präfix 11/70/71 + Suffix werden verworfen).
        val cleaned = v.trim().uppercase()
            .filter { it.isDigit() || it in 'A'..'D' || it == '$' || it == '%' }
            .take(24)
        // Nach Präfix/Suffix-Abschnitt (bzw. bei manueller Eingabe) auf 10 Zeichen begrenzen.
        val match = WHEELSET_REGEX.find(cleaned)
        val normalized = (match?.groupValues?.get(1) ?: cleaned).take(10)
        uiState = uiState.copy(radsatznummer = normalized, error = null)
        // Vollständiger Barcode erkannt -> Tastatur ausblenden lassen.
        if (match != null) radsatzScanCompleted = true
    }

    fun onTorqueChange(v: String) {
        if (v.all { it.isDigit() } && v.length <= 4) {
            uiState = uiState.copy(torque = v, error = null)
        }
    }

    fun startRadwechsel() {
        if (uiState.kennzeichen.isBlank()) {
            uiState = uiState.copy(error = "Bitte Kennzeichen / Auftragsnummer eingeben")
            return
        }
        // Radsatznummer ist optional – kein Zwang zum Scannen.

        startInstant = Instant.now()
        uiState = uiState.copy(
            phase = RadwechselPhase.LAUFEND,
            startedAt = ISO_MS.format(startInstant),
            timerSeconds = 0,
            error = null
        )

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                uiState = uiState.copy(timerSeconds = uiState.timerSeconds + 1)
            }
        }
    }

    fun abbrechen() {
        timerJob?.cancel()
        uiState = RadwechselUiState()
    }

    fun abschliessen() {
        val torqueInt = uiState.torque.toIntOrNull()
        if (torqueInt == null || torqueInt < 50 || torqueInt > 1500) {
            uiState = uiState.copy(error = "Drehmoment muss zwischen 50 und 1500 Nm liegen")
            return
        }

        timerJob?.cancel()
        val finishedAt = ISO_MS.format(Instant.now())
        val duration = uiState.timerSeconds
        uiState = uiState.copy(phase = RadwechselPhase.ERFOLG, finishedAt = finishedAt, error = null)

        // Session-Log (inkl. Radsatznummer – nur für Gegencheck/Excel, nicht für API).
        sessionLog.add(
            CompletedRadwechsel(
                kennzeichen    = uiState.kennzeichen,
                radsatznummer  = uiState.radsatznummer,
                torque         = torqueInt,
                kostenstelle   = wheelhotel?.id ?: "",
                standortName   = wheelhotel?.displayName ?: "",
                benutzer       = username,
                startedAtIso   = uiState.startedAt,
                finishedAtIso  = finishedAt,
                durationSeconds = duration
            )
        )

        // API-Übertragung wie gehabt – ohne Radsatznummer.
        viewModelScope.launch {
            queueManager.enqueue(
                QueueItem(
                    wheelhotelName = wheelhotel?.displayName ?: "",
                    licensePlate   = uiState.kennzeichen,
                    torque         = torqueInt,
                    startedAt      = uiState.startedAt,
                    finishedAt     = finishedAt
                )
            )
        }
    }

    fun neuerWechsel() {
        uiState = RadwechselUiState()
    }

    fun logout(lockUsername: Boolean, onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout(lockUsername)
            // Session-Liste erst zurücksetzen, wenn ALLE Requests übertragen sind
            // (keine offenen/fehlgeschlagenen mehr). Sonst über den Logout hinaus behalten.
            if (!queueManager.hasPendingOrFailed) {
                sessionLog.clear()
            }
            onLoggedOut()
        }
    }

    fun retryAll() = queueManager.retryFailed()
    fun retryItem(id: String) = queueManager.retryItem(id)

    /** Session lebt, solange Access-Token gültig ODER per Refresh erneuerbar ist. */
    fun sessionAlive(): Boolean = authRepository.hasSession()

    // ── Excel-Report an Outlook teilen ────────────────────────────────────────

    /**
     * Baut die Excel + Metadaten zum Teilen. Gibt null zurück, wenn keine
     * Radwechsel vorliegen. Empfänger/Absender sind fix radwechsel@4wheels.de;
     * der Versand läuft über Outlook (ReportSharer) – keine Creds in der App.
     */
    fun buildReportPayload(): ReportPayload? {
        val items = sessionLog.items.value
        if (items.isEmpty()) return null

        val standort = wheelhotel?.id ?: "–"
        val stamp = FILE_STAMP.format(Instant.now())
        return ReportPayload(
            xlsx      = buildReportXlsx(items),
            fileName  = "radwechsel_${standort}_$stamp.xlsx",
            recipient = REPORT_RECIPIENT,
            subject   = "Radwechsel-Report $standort – ${items.size} Wechsel",
            body      = buildString {
                append("Report der Radwechsel-App.\n\n")
                append("Kostenstelle: $standort\n")
                append("Standort: ${wheelhotel?.displayName ?: "–"}\n")
                append("Anzahl Radwechsel: ${items.size}\n")
                append("Erstellt: ${REPORT_DATETIME.format(Instant.now())}\n")
            }
        )
    }

    private fun buildReportXlsx(items: List<CompletedRadwechsel>): ByteArray {
        val headers = listOf(
            "Kostenstelle", "Standort", "Benutzer", "Kennzeichen / Auftragsnummer",
            "Radsatznummer", "Drehmoment (Nm)", "Datum", "Von", "Bis"
        )
        val rows = items.map {
            listOf(
                it.kostenstelle,
                it.standortName,
                it.benutzer,
                it.kennzeichen,
                it.radsatznummer,
                it.torque.toString(),
                formatDate(it.finishedAtIso),
                formatTime(it.startedAtIso),
                formatTime(it.finishedAtIso)
            )
        }
        return XlsxWriter.build(headers, rows)
    }

    private fun formatDate(iso: String): String = try {
        REPORT_DATE.format(Instant.parse(iso))
    } catch (e: Exception) { iso }

    private fun formatTime(iso: String): String = try {
        REPORT_TIME.format(Instant.parse(iso))
    } catch (e: Exception) { iso }

    // ── Formatierung ──────────────────────────────────────────────────────────

    fun formatTimer(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    fun formatStartzeit(iso: String): String {
        return try {
            val instant = Instant.parse(iso)
            val local = instant.atZone(ZoneOffset.systemDefault())
            "%02d:%02d Uhr".format(local.hour, local.minute)
        } catch (e: Exception) { iso }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
