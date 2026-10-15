package com.fourwheels.radwechsel.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ton und Vibration als Rückmeldung auf Scans und Fehler.
 *
 * In der Lagerhalle ist das Display oft nicht im Blick und laut ist es auch –
 * deshalb gibt es beides. Die Muster sind absichtlich identisch zur
 * Räderhotel-App, damit dieselben Mitarbeiter nicht zwei Signalsprachen lernen müssen.
 */
@Singleton
class ScanFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Ein ToneGenerator belegt eine Audio-Ressource. Auf dem SL20 wird im
    // Sekundentakt gescannt, deshalb eine Instanz für die ganze App statt einer
    // pro Piep. Der Konstruktor wirft, wenn das Gerät keine mehr hergibt – dann
    // bleibt es eben still, ein fehlender Piep darf keinen Radwechsel abbrechen.
    private val toneGenerator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    }

    /** Erkannter Barcode: kurzer hoher Piep (1200 Hz) + einmal 200 ms vibrieren. */
    fun success() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MS)
        vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Fehler: 941-Hz-Doppelpiep + dreimal 100 ms vibrieren mit Pausen. */
    fun error() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, TONE_MS)
        vibrator?.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(100, 100, 100, 100, 100),
                intArrayOf(AMP, PAUSE, AMP, PAUSE, AMP),
                NO_REPEAT
            )
        )
    }

    private companion object {
        const val VOLUME    = 100   // ToneGenerator-Skala 0–100
        const val TONE_MS   = 200
        const val AMP       = VibrationEffect.DEFAULT_AMPLITUDE   // -1 = Gerätestandard
        const val PAUSE     = 0
        const val NO_REPEAT = -1
    }
}
