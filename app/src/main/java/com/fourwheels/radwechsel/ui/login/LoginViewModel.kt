package com.fourwheels.radwechsel.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.repository.AuthRepository
import com.fourwheels.radwechsel.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String          = "",
    val password: String          = "",
    val usernameIsLocked: Boolean = false,
    val isLoading: Boolean        = false,
    val error: String?            = null,
    val loginSuccess: Boolean     = false,
    val lastWheelhotel: Wheelhotel? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    init {
        viewModelScope.launch {
            val locked   = authRepository.isUsernameLocked.first()
            val username = if (locked) authRepository.username.first() ?: "" else ""
            uiState = uiState.copy(username = username, usernameIsLocked = locked)
        }
    }

    fun onUsernameChange(value: String) {
        if (!uiState.usernameIsLocked) {
            uiState = uiState.copy(username = value, error = null)
        }
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun login() {
        val username = uiState.username.trim()
        val password = uiState.password

        if (username.isBlank() || password.isBlank()) {
            uiState = uiState.copy(error = "Bitte Username und Passwort eingeben")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)

            when (val result = authRepository.login(username, password)) {
                is AuthResult.Success -> {
                    val lastWH = authRepository.getLastWheelhotel()
                    uiState = uiState.copy(isLoading = false, loginSuccess = true, lastWheelhotel = lastWH)
                }
                is AuthResult.Error -> {
                    uiState = uiState.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
