package com.fourwheels.radwechsel.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        private val KEY_ACCESS_TOKEN  = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USERNAME      = stringPreferencesKey("username")
    }

    // ─── Token lesen ─────────────────────────────────────────────────────────

    val accessToken: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val username: Flow<String?>    = dataStore.data.map { it[KEY_USERNAME] }

    fun bearerHeader(token: String) = "Bearer $token"

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

                // Token sicher im DataStore persistieren
                dataStore.edit { prefs ->
                    prefs[KEY_ACCESS_TOKEN]  = body.accessToken
                    prefs[KEY_REFRESH_TOKEN] = body.refreshToken
                    prefs[KEY_USERNAME]      = username
                }
                AuthResult.Success(Unit)
            } else {
                // 401, 403 etc. → immer gleiche Fehlermeldung (kein Leak von Server-Details)
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
                // Einträge ohne Namen und ohne Stadt herausfiltern (wie id="2110")
                val filtered = list.filter { it.name.isNotBlank() }
                AuthResult.Success(filtered)
            } else {
                AuthResult.Error("Wheelhotels konnten nicht geladen werden")
            }
        } catch (e: Exception) {
            AuthResult.Error("Keine Verbindung zum Server")
        }
    }

    // ─── Logout ──────────────────────────────────────────────────────────────

    suspend fun logout() {
        dataStore.edit { it.clear() }
    }
}
