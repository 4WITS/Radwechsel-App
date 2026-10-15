package com.fourwheels.radwechsel.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anbindung an den eingebauten Scanner der M3-Geräte (SL20, Dienst "ScanEmul").
 *
 * ScanEmul liefert Scans je nach Ausgabemodus als Einfügen in ein Textfeld, als
 * Tastendrücke oder als Broadcast. Die Modi sind geräteweit – die Räderhotel-App
 * stellt z.B. auf Broadcast um, danach kommt in einem Textfeld schlicht nichts
 * mehr an. Deshalb stellt diese App beim Start selbst auf Broadcast um und hört
 * nur noch darauf, statt sich auf irgendeinen Vorzustand zu verlassen.
 *
 * Aktiv nur solange die App im Vordergrund ist ([start]/[stop] aus der Activity).
 */
@Singleton
class M3Scanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Dekodierte Barcodes, bereits getrimmt. */
    val scans: SharedFlow<String> = _scans.asSharedFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // ScanEmul hängt je nach Einstellung ein Endzeichen (Enter/Tab) an
            val data = intent.getStringExtra(EXTRA_DATA)?.trim().orEmpty()
            Log.i("M3Scanner", "Scan empfangen (${data.length} Zeichen)")
            if (data.isNotEmpty()) _scans.tryEmit(data)
        }
    }

    fun start() {
        if (!registered) {
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(ACTION_SCAN), ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        }
        // Auf Nicht-M3-Geräten verpuffen diese Broadcasts folgenlos
        context.sendBroadcast(
            Intent(ACTION_SETTING_CHANGE)
                .putExtra("setting", "output_mode")
                .putExtra("output_mode_value", OUTPUT_MODE_INTENT)
        )
        context.sendBroadcast(Intent(ACTION_ON_OFF).putExtra("scanneronoff", 1))
        Log.i("M3Scanner", "Scanner auf Broadcast-Ausgabe gestellt")
    }

    fun stop() {
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
    }

    private companion object {
        const val ACTION_SCAN           = "com.android.server.scannerservice.broadcast"
        const val ACTION_SETTING_CHANGE = "com.android.server.scannerservice.settingchange"
        const val ACTION_ON_OFF         = "com.android.server.scannerservice.m3onoff"
        const val EXTRA_DATA            = "m3scannerdata"
        const val OUTPUT_MODE_INTENT    = 2   // 0 = Einfügen, 1 = Tastendrücke, 2 = Broadcast
    }
}
