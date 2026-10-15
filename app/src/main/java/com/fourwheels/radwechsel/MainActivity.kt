package com.fourwheels.radwechsel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fourwheels.radwechsel.scanner.M3Scanner
import com.fourwheels.radwechsel.ui.AppNavGraph
import com.fourwheels.radwechsel.ui.theme.RadwechselTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var scanner: M3Scanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RadwechselTheme {
                AppNavGraph()
            }
        }
    }

    // onResume statt onStart: kommt der Nutzer aus einer anderen Scanner-App
    // zurück, die den Ausgabemodus verstellt hat, wird er hier wieder gesetzt.
    override fun onResume() {
        super.onResume()
        scanner.start()
    }

    override fun onPause() {
        scanner.stop()
        super.onPause()
    }
}
