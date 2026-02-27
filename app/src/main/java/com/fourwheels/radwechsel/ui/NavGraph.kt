package com.fourwheels.radwechsel.ui

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.ui.login.LoginScreen
import com.fourwheels.radwechsel.ui.rh.RHAuswahlScreen

// Routen-Konstanten – kein Tippfehler-Risiko durch Magic Strings
object Routes {
    const val LOGIN      = "login"
    const val RH_AUSWAHL = "rh_auswahl"
    const val RADWECHSEL = "radwechsel"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    // Ausgewähltes Wheelhotel zwischen Screens teilen
    // (In größeren Apps: in SharedViewModel oder SavedStateHandle)
    var selectedHotel by remember { mutableStateOf<Wheelhotel?>(null) }

    NavHost(
        navController   = navController,
        startDestination = Routes.LOGIN
    ) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.RH_AUSWAHL) {
                        // Login nicht im Backstack – kein Zurück-Button nach Login
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.RH_AUSWAHL) {
            RHAuswahlScreen(
                onWheelhotelSelected = { hotel ->
                    selectedHotel = hotel
                    navController.navigate(Routes.RADWECHSEL)
                },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.RADWECHSEL) {
            // RadwechselScreen kommt im nächsten Schritt
            // selectedHotel ist hier verfügbar
        }
    }
}
