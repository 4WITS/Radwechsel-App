package com.fourwheels.radwechsel.ui.radwechsel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fourwheels.radwechsel.model.Wheelhotel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class RadwechselPhase { EINGABE, LAUFEND, ERFOLG }

data class RadwechselUiState(
    val phase: RadwechselPhase = RadwechselPhase.EINGABE,
    val kennzeichen: String = "",
    val torque: String = "110",
    val timerSeconds: Int = 0,
    val startedAt: String = "",
    val finishedAt: String = "",
    val error: String? = null
)

@HiltViewModel
class RadwechselViewModel @Inject constructor(
    private val queueManager: QueueManager,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    var uiState by mutableStateOf(RadwechselUiState())
        private set

    private var timerJob: Job? = null
    private var startInstant: Instant? = null

    var wheelhotel: Wheelhotel? = null

    val pendingItems = queueManager.pendingItems
    val failedItems = queueManager.failedItems

    fun onKennzeichenChange(v: String) {
        uiState = uiState.copy(kennzeichen = v.uppercase(), error = null)
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
        val torqueInt = uiState.torque.toIntOrNull()
        if (torqueInt == null || torqueInt < 50 || torqueInt > 500) {
            uiState = uiState.copy(error = "Drehmoment muss zwischen 50 und 500 Nm liegen")
            return
        }

        startInstant = Instant.now()
        uiState = uiState.copy(
            phase = RadwechselPhase.LAUFEND,
            startedAt = DateTimeFormatter.ISO_INSTANT.format(startInstant),
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
        timerJob?.cancel()
        val finishedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        uiState = uiState.copy(phase = RadwechselPhase.ERFOLG, finishedAt = finishedAt)

        viewModelScope.launch {
            val username = dataStore.data
                .map { it[stringPreferencesKey("username")] ?: "" }
                .first()

            queueManager.enqueue(
                QueueItem(
                    wheelhotelId   = wheelhotel?.id ?: "",
                    wheelhotelName = wheelhotel?.displayName ?: "",
                    username       = username,
                    licensePlate   = uiState.kennzeichen,
                    torque         = uiState.torque.toInt(),
                    startedAt      = uiState.startedAt,
                    finishedAt     = finishedAt
                )
            )
        }
    }

    fun neuerWechsel() {
        uiState = RadwechselUiState()
    }

    fun retryAll() = queueManager.retryFailed()
    fun retryItem(id: String) = queueManager.retryItem(id)

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
