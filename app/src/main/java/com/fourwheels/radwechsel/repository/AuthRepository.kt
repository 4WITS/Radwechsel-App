package com.fourwheels.radwechsel.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fourwheels.radwechsel.api.AuthApi
import com.fourwheels.radwechsel.api.FourWheelsApi
import com.fourwheels.radwechsel.model.Wheelhotel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

// ─── Ergebnis-Wrapper ────────────────────────────────────────────────────────

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

// ─── Repository ──────────────────────────────────────────────────────────────

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val fourWheelsApi: FourWheelsApi,
    private val dataStore: DataStore<Preferences>,
    @Named("clientId") private val clientId: String,
    @Named("scope")    private val scope: String
) {

    companion object {
        private val KEY_ACCESS_TOKEN   = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN  = stringPreferencesKey("refresh_token")
        private val KEY_USERNAME       = stringPreferencesKey("username")
        private val KEY_TOKEN_EXPIRY   = longPreferencesKey("token_expiry")
        private val KEY_LAST_WH_ID     = stringPreferencesKey("last_wh_id")
        private val KEY_LAST_WH_NAME   = stringPreferencesKey("last_wh_name")
        private val KEY_USERNAME_LOCKED = booleanPreferencesKey("username_locked")
    }

    // ─── Token / User lesen ──────────────────────────────────────────────────

    val accessToken: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val username: Flow<String?>    = dataStore.data.map { it[KEY_USERNAME] }
    val isUsernameLocked: Flow<Boolean> = dataStore.data.map { it[KEY_USERNAME_LOCKED] ?: false }

    fun bearerHeader(token: String) = "Bearer $token"

    suspend fun isTokenValid(): Boolean {
        val expiry = dataStore.data.first()[KEY_TOKEN_EXPIRY] ?: return false
        return System.currentTimeMillis() < expiry
    }

    // ─── Letztes Wheelhotel ──────────────────────────────────────────────────

    suspend fun getLastWheelhotel(): Wheelhotel? {
        val prefs = dataStore.data.first()
        val id   = prefs[KEY_LAST_WH_ID]   ?: return null
        val name = prefs[KEY_LAST_WH_NAME] ?: return null
        return Wheelhotel(id = id, name = name, address = null, branchManager = null)
    }

    suspend fun saveLastWheelhotel(wh: Wheelhotel) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_WH_ID]   = wh.id
            prefs[KEY_LAST_WH_NAME] = wh.name
        }
    }

    // ─── Session abgelaufen ──────────────────────────────────────────────────

    suspend fun markSessionExpired() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRY)
            // Letztes WH behalten, damit nach Re-Login die Auswahl übersprungen wird
            prefs[KEY_USERNAME_LOCKED] = true
        }
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    suspend fun login(username: String, password: String): AuthResult<Unit> {
        return try {
            val response = authApi.login(
                clientId = clientId,
                scope    = scope,
                username = username,
                password = password
            )

            if (response.isSuccessful) {
                val body = response.body()
                    ?: return AuthResult.Error("Login fehlgeschlagen")

                dataStore.edit { prefs ->
                    prefs[KEY_ACCESS_TOKEN]    = body.accessToken
                    prefs[KEY_REFRESH_TOKEN]   = body.refreshToken
                    prefs[KEY_USERNAME]        = username
                    prefs[KEY_TOKEN_EXPIRY]    = System.currentTimeMillis() + body.expiresIn * 1000L
                    prefs[KEY_USERNAME_LOCKED] = false
                }
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error("Login fehlgeschlagen")
            }
        } catch (e: Exception) {
            AuthResult.Error("Keine Verbindung zum Server")
        }
    }

    // ─── Wheelhotels laden ───────────────────────────────────────────────────

    suspend fun getWheelhotels(): AuthResult<List<Wheelhotel>> {
        val token = dataStore.data.first()[KEY_ACCESS_TOKEN]
            ?: return AuthResult.Error("Nicht eingeloggt")

        return try {
            val response = fourWheelsApi.getWheelhotels(bearerHeader(token))

            if (response.isSuccessful) {
                val list = response.body() ?: emptyList()
                val filtered = list.filter { it.name.isNotBlank() }
                AuthResult.Success(filtered)
            } else {
                if (response.code() == 401) AuthResult.Error("SESSION_EXPIRED")
                else AuthResult.Error("Wheelhotels konnten nicht geladen werden")
            }
        } catch (e: Exception) {
            AuthResult.Error("Keine Verbindung zum Server")
        }
    }

    // ─── Logout ──────────────────────────────────────────────────────────────

    suspend fun logout(lockUsername: Boolean = false) {
        dataStore.edit { prefs ->
            if (lockUsername) {
                prefs.remove(KEY_ACCESS_TOKEN)
                prefs.remove(KEY_REFRESH_TOKEN)
                prefs.remove(KEY_TOKEN_EXPIRY)
                // Letztes WH behalten, damit nach Re-Login die Auswahl übersprungen wird
                prefs[KEY_USERNAME_LOCKED] = true
            } else {
                prefs.clear()
            }
        }
    }
}
