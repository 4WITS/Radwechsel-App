package com.fourwheels.radwechsel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    /** null = noch am Laden */
    var startDestination by mutableStateOf<String?>(null)
        private set

    var initialUsername by mutableStateOf("")
        private set

    var initialWheelhotel by mutableStateOf<Wheelhotel?>(null)
        private set

    init {
        // TokenStore liest synchron -> keine Coroutine nötig.
        // Abgelaufener Access-Token allein ist kein Grund für einen Re-Login: mit
        // Refresh-Token erneuert der TokenAuthenticator still beim ersten Request.
        if (authRepository.canResumeSession()) {
            val lastWH = authRepository.getLastWheelhotel()
            initialUsername   = authRepository.username ?: ""
            initialWheelhotel = lastWH
            // Ohne gemerktes Räderhotel (z.B. Absturz direkt nach dem Login) erst
            // auswählen lassen – sonst ginge ein Radwechsel ohne Wheelhotel raus.
            startDestination  = if (lastWH != null) Routes.RADWECHSEL else Routes.RH_AUSWAHL
        } else {
            val savedUsername = authRepository.username
            if (!savedUsername.isNullOrBlank()) {
                authRepository.markSessionExpired()
            }
            startDestination = Routes.LOGIN
        }
    }
}
