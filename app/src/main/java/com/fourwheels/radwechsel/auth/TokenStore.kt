package com.fourwheels.radwechsel.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fourwheels.radwechsel.model.TokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verschlüsselter, SYNCHRONER Token-Speicher (Android Keystore).
 *
 * Bewusst synchron (nicht DataStore): der [AuthInterceptor] und der
 * [TokenAuthenticator] laufen auf beliebigen OkHttp-Threads und dürfen NICHT
 * suspendieren – deshalb SharedPreferences statt DataStore.
 *
 * Hält neben Access-/Refresh-Token auch Username-Lock und letztes Wheelhotel,
 * damit der Re-Login nach Ablauf nur das Passwort verlangt und die RH-Auswahl
 * übersprungen bleibt.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(v) = prefs.edit().putString(KEY_ACCESS, v).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(v) = prefs.edit().putString(KEY_REFRESH, v).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    var usernameLocked: Boolean
        get() = prefs.getBoolean(KEY_USERNAME_LOCKED, false)
        set(v) = prefs.edit().putBoolean(KEY_USERNAME_LOCKED, v).apply()

    /** Ablaufzeitpunkt des Access-Tokens in Epoch-Millis. */
    var tokenExpiry: Long
        get() = prefs.getLong(KEY_EXPIRY, 0L)
        set(v) = prefs.edit().putLong(KEY_EXPIRY, v).apply()

    var lastWheelhotelId: String?
        get() = prefs.getString(KEY_WH_ID, null)
        set(v) = prefs.edit().putString(KEY_WH_ID, v).apply()

    var lastWheelhotelName: String?
        get() = prefs.getString(KEY_WH_NAME, null)
        set(v) = prefs.edit().putString(KEY_WH_NAME, v).apply()

    /**
     * Lokaler Ablauf-Check. Ein abgelaufener Token wird zusätzlich beim ersten
     * 401-Response vom [TokenAuthenticator] still erneuert.
     */
    val isTokenValid: Boolean
        get() = accessToken != null && System.currentTimeMillis() < tokenExpiry

    /** Login: Username + alle Token-Felder setzen, Sperre lösen. */
    fun saveLogin(username: String, token: TokenResponse) {
        this.username = username
        usernameLocked = false
        saveTokens(token)
    }

    /** Refresh: Access + Ablauf (und Refresh-Token nur, falls der Server einen neuen liefert). */
    fun saveTokens(token: TokenResponse) {
        accessToken = token.accessToken
        token.refreshToken?.let { refreshToken = it }
        tokenExpiry = System.currentTimeMillis() + token.expiresIn * 1000L
    }

    /**
     * Session abgelaufen: Tokens löschen, aber Username merken + sperren.
     * Letztes Wheelhotel bleibt erhalten, damit die RH-Auswahl übersprungen wird.
     */
    fun markSessionExpired() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRY)
            .putBoolean(KEY_USERNAME_LOCKED, true)
            .apply()
    }

    /** Vollständiger Logout: alles weg. */
    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS          = "access_token"
        const val KEY_REFRESH         = "refresh_token"
        const val KEY_USERNAME        = "username"
        const val KEY_USERNAME_LOCKED = "username_locked"
        const val KEY_EXPIRY          = "token_expiry"
        const val KEY_WH_ID           = "last_wh_id"
        const val KEY_WH_NAME         = "last_wh_name"
    }
}
