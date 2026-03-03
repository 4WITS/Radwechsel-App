package com.fourwheels.radwechsel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    /** null = noch am Laden */
    var startDestination by mutableStateOf<String?>(null)
        private set

    var initialWheelhotel by mutableStateOf<Wheelhotel?>(null)
        private set

    var initialUsername by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            if (authRepository.isTokenValid()) {
                val lastWH = authRepository.getLastWheelhotel()
                initialWheelhotel = lastWH
                initialUsername = authRepository.username.first() ?: ""
                startDestination = if (lastWH != null) Routes.RADWECHSEL else Routes.RH_AUSWAHL
            } else {
                // Wenn ein Username gespeichert war → Session als abgelaufen markieren (Username locken)
                val savedUsername = authRepository.username.first()
                if (!savedUsername.isNullOrBlank()) {
                    authRepository.markSessionExpired()
                }
                startDestination = Routes.LOGIN
            }
        }
    }
}
