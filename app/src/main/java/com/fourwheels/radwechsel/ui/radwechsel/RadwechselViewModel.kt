package com.fourwheels.radwechsel.ui.radwechsel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val ISO_MS = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    .withZone(ZoneOffset.UTC)

enum class RadwechselPhase { EINGABE, LAUFEND, ERFOLG }

data class RadwechselUiState(
    val phase: RadwechselPhase = RadwechselPhase.EINGABE,
    val kennzeichen: String = "",
    val torque: String = "110",
    val username: String = "",
    val timerSeconds: Int = 0,
    val startedAt: String = "",
    val finishedAt: String = "",
    val error: String? = null
)

@HiltViewModel
class RadwechselViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val queueManager: QueueManager
) : ViewModel() {

    var uiState by mutableStateOf(RadwechselUiState())
        private set

    private var timerJob: Job? = null
    private var startInstant: Instant? = null

    var wheelhotel: Wheelhotel? = null

    val pendingItems = queueManager.pendingItems
    val failedItems  = queueManager.failedItems

    init {
        viewModelScope.launch {
            val savedUsername = authRepository.username.first() ?: ""
            uiState = uiState.copy(username = savedUsername)
        }
    }

    fun setUsername(u: String) {
        if (u.isNotEmpty()) uiState = uiState.copy(username = u)
    }

    fun onKennzeichenChange(v: String) {
        if (v.length <= 20) {
            uiState = uiState.copy(kennzeichen = v.uppercase(), error = null)
        }
    }

    fun onTorqueChange(v: String) {
        if (v.all { it.isDigit() } && v.length <= 5) {
            uiState = uiState.copy(torque = v, error = null)
        }
    }

    fun startRadwechsel() {
        if (uiState.kennzeichen.isBlank()) {
            uiState = uiState.copy(error = "Bitte Kennzeichen / Auftragsnummer eingeben")
            return
        }

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
        uiState = RadwechselUiState(username = uiState.username)
    }

    fun abschliessen() {
        val torqueInt = uiState.torque.toIntOrNull()
        if (torqueInt == null || torqueInt < 50 || torqueInt > 500) {
            uiState = uiState.copy(error = "Drehmoment muss zwischen 50 und 500 Nm liegen")
            return
        }

        timerJob?.cancel()
        val finishedAt = ISO_MS.format(Instant.now())
        uiState = uiState.copy(phase = RadwechselPhase.ERFOLG, finishedAt = finishedAt, error = null)

        viewModelScope.launch {
            queueManager.enqueue(
                QueueItem(
                    wheelhotelId   = wheelhotel?.id ?: "",
                    wheelhotelName = wheelhotel?.displayName ?: "",
                    username       = uiState.username,
                    licensePlate   = uiState.kennzeichen,
                    torque         = uiState.torque.toInt(),
                    startedAt      = uiState.startedAt,
                    finishedAt     = finishedAt
                )
            )
        }
    }

    fun neuerWechsel() {
        uiState = RadwechselUiState(username = uiState.username)
    }

    fun logout(lockUsername: Boolean, onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout(lockUsername)
            onLoggedOut()
        }
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
