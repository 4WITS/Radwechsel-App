package com.fourwheels.radwechsel.logging

import android.content.Context
import android.content.RestrictionsManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identifiziert das Gerät fürs Protokoll.
 *
 * Gesucht ist der Name aus Scalefusion (z.B. "MDE-IT-Test-0003 (HO)"). Der steht
 * *nicht* auf dem Gerät: der Android-Gerätename der SL20 ist schlicht "M3SL20",
 * und das Asset-Label einer fremden MDM-App lässt sich nicht auslesen.
 *
 * Der unterstützte Weg ist die Managed Configuration: Scalefusion ist auf den
 * Geräten Device Owner und kann der App darüber Werte durchreichen. Trägt die IT
 * dort den Schlüssel `device_name` ein (siehe app_restrictions.xml), steht der
 * echte Name im Protokoll. Ohne diesen Eintrag bleibt es bei der Hardware-
 * bezeichnung – die übrigen Quellen stehen zur Kontrolle mit im Protokoll.
 */
@Singleton
class DeviceInfo @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Alle Kandidaten mit Herkunft – als Klartext fürs Protokoll. */
    fun candidates(): Map<String, String> = linkedMapOf(
        "Managed Config device_name"     to managedName(),
        "Geräte-ID (ANDROID_ID)"         to androidId(),
        "Settings.Global.device_name"    to global(Settings.Global.DEVICE_NAME),
        "Settings.Secure.bluetooth_name" to secure("bluetooth_name"),
        "Build.MODEL"                    to Build.MODEL.orEmpty(),
        "Build.MANUFACTURER"             to Build.MANUFACTURER.orEmpty()
    ).mapValues { it.value.ifBlank { "–" } }

    /**
     * Kennung des Geräts fürs Protokoll.
     *
     * Die Hardware-Seriennummer wäre das Naheliegende, ist einer normalen App
     * seit Android 10 aber verschlossen: `Build.getSerial()` ist dem Device Owner
     * und Carrier-Apps vorbehalten – Device Owner ist hier Scalefusion, nicht wir.
     *
     * Ersatz ist die ANDROID_ID: pro App und Gerät eindeutig, stabil bis zum
     * Zurücksetzen auf Werkseinstellungen und ohne Berechtigung lesbar. Zum
     * Wiedererkennen eines Geräts in den Protokollen reicht das. Trägt die IT in
     * Scalefusion zusätzlich den Gerätenamen ein (siehe app_restrictions.xml),
     * steht der davor.
     */
    val name: String
        get() = listOf(
            managedName(),
            global(Settings.Global.DEVICE_NAME).takeIf { it != Build.MODEL }.orEmpty()
        ).firstOrNull { it.isNotBlank() }
            ?.let { "$it · ${androidId()}" }
            ?: "${Build.MANUFACTURER} ${Build.MODEL} · ${androidId()}"

    /** Pro App und Gerät eindeutig, ohne Berechtigung lesbar. */
    private fun androidId() = secure(Settings.Secure.ANDROID_ID)

    /** Von der Geräteverwaltung durchgereichter Name (siehe app_restrictions.xml). */
    private fun managedName() = read {
        context.getSystemService(RestrictionsManager::class.java)
            ?.applicationRestrictions?.getString(KEY_DEVICE_NAME)
    }

    // Settings-Zugriffe können je nach Android-Version/Hersteller werfen oder
    // null liefern – ein fehlender Name darf nichts kaputtmachen.
    private fun global(key: String) = read { Settings.Global.getString(context.contentResolver, key) }
    private fun secure(key: String) = read { Settings.Secure.getString(context.contentResolver, key) }
    private fun system(key: String) = read { Settings.System.getString(context.contentResolver, key) }

    private inline fun read(block: () -> String?) = runCatching(block).getOrNull().orEmpty().trim()

    private companion object {
        /** Schlüssel in app_restrictions.xml – muss in Scalefusion genauso heißen. */
        const val KEY_DEVICE_NAME = "device_name"
    }
}
