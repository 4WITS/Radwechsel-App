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
    val error: String? = null
)

@HiltViewModel
class RHAuswahlViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val queueManager: QueueManager
) : ViewModel() {

    var uiState by mutableStateOf(RHAuswahlUiState())
        private set

    // Queue-States direkt durchreichen für den Screen
    val pendingItems = queueManager.pendingItems
    val failedItems = queueManager.failedItems

    init { loadWheelhotels() }

    private fun loadWheelhotels() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = authRepository.getWheelhotels()) {
                is AuthResult.Success -> uiState = uiState.copy(isLoading = false, wheelhotels = result.data)
                is AuthResult.Error   -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun retry() = loadWheelhotels()

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}
