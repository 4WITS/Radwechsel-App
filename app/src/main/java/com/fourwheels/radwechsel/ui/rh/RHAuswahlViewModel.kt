package com.fourwheels.radwechsel.ui.rh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.repository.AuthRepository
import com.fourwheels.radwechsel.repository.AuthResult
import com.fourwheels.radwechsel.ui.radwechsel.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RHAuswahlUiState(
    val wheelhotels: List<Wheelhotel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessionExpired: Boolean = false
)

@HiltViewModel
class RHAuswahlViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val queueManager: QueueManager
) : ViewModel() {

    var uiState by mutableStateOf(RHAuswahlUiState())
        private set

    val pendingItems = queueManager.pendingItems
    val failedItems  = queueManager.failedItems

    init { loadWheelhotels() }

    private fun loadWheelhotels() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = authRepository.getWheelhotels()) {
                is AuthResult.Success -> uiState = uiState.copy(isLoading = false, wheelhotels = result.data)
                is AuthResult.Error   -> {
                    if (result.message == "SESSION_EXPIRED") {
                        authRepository.markSessionExpired()
                        uiState = uiState.copy(isLoading = false, sessionExpired = true)
                    } else {
                        uiState = uiState.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun retry() = loadWheelhotels()

    fun selectWheelhotel(hotel: Wheelhotel, onSelected: (Wheelhotel) -> Unit) {
        viewModelScope.launch {
            authRepository.saveLastWheelhotel(hotel)
            onSelected(hotel)
        }
    }

    fun logout(lockUsername: Boolean, onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout(lockUsername)
            onLoggedOut()
        }
    }
}
