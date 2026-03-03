package com.fourwheels.radwechsel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.ui.login.LoginScreen
import com.fourwheels.radwechsel.ui.login.Red4Wheels
import com.fourwheels.radwechsel.ui.radwechsel.RadwechselScreen
import com.fourwheels.radwechsel.ui.rh.RHAuswahlScreen

object Routes {
    const val LOGIN      = "login"
    const val RH_AUSWAHL = "rh_auswahl"
    const val RADWECHSEL = "radwechsel"
}

@Composable
fun AppNavGraph(
    appViewModel: AppViewModel = hiltViewModel()
) {
    val startDest = appViewModel.startDestination

    if (startDest == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Red4Wheels)
        }
        return
    }

    val navController = rememberNavController()
    var selectedHotel    by remember { mutableStateOf(appViewModel.initialWheelhotel) }
    var selectedUsername by remember { mutableStateOf(appViewModel.initialUsername) }

    NavHost(navController = navController, startDestination = startDest) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { lastWH, username ->
                    selectedUsername = username
                    if (lastWH != null) {
                        // Letztes RH bekannt → direkt zu Radwechsel, RH-Auswahl überspringen
                        selectedHotel = lastWH
                        navController.navigate(Routes.RADWECHSEL) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.RH_AUSWAHL) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.RH_AUSWAHL) {
            RHAuswahlScreen(
                onWheelhotelSelected = { hotel ->
                    selectedHotel = hotel
                    // Back-Stack leeren: von Radwechsel-Maske gibt es kein Zurück zur Auswahl
                    navController.navigate(Routes.RADWECHSEL) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSessionExpired = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.RADWECHSEL) {
            RadwechselScreen(
                wheelhotel = selectedHotel,
                username   = selectedUsername,
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
