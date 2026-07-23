package com.fourwheels.radwechsel.repository

import com.fourwheels.radwechsel.api.AuthApi
import com.fourwheels.radwechsel.api.FourWheelsApi
import com.fourwheels.radwechsel.auth.TokenStore
import com.fourwheels.radwechsel.model.Wheelhotel
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

// ─── Ergebnis-Wrapper ────────────────────────────────────────────────────────

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

// ─── Repository ──────────────────────────────────────────────────────────────
//
// Token-Handling liegt jetzt im synchronen, verschlüsselten TokenStore.
// Bearer-Header + automatischer 401-Refresh passieren transparent im
// OkHttp-Layer (AuthInterceptor + TokenAuthenticator) – das Repository muss
// den Token nicht mehr selbst anhängen.

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val fourWheelsApi: FourWheelsApi,
    private val tokenStore: TokenStore,
    @Named("clientId") private val clientId: String,
    @Named("scope")    private val scope: String
) {

    // ─── Zustand (synchron aus dem verschlüsselten Store) ─────────────────────

    val username: String?         get() = tokenStore.username
    val isUsernameLocked: Boolean get() = tokenStore.usernameLocked

    fun isTokenValid(): Boolean = tokenStore.isTokenValid

    // ─── Letztes Wheelhotel ───────────────────────────────────────────────────

    fun getLastWheelhotel(): Wheelhotel? {
        val id   = tokenStore.lastWheelhotelId   ?: return null
        val name = tokenStore.lastWheelhotelName ?: return null
        return Wheelhotel(id = id, name = name, address = null, branchManager = null)
    }

    fun saveLastWheelhotel(wh: Wheelhotel) {
        tokenStore.lastWheelhotelId   = wh.id
        tokenStore.lastWheelhotelName = wh.name
    }

    // ─── Session / Logout ─────────────────────────────────────────────────────

    /** Tokens weg, Username merken + sperren (für Re-Login). */
    fun markSessionExpired() = tokenStore.markSessionExpired()

    fun logout(lockUsername: Boolean = false) {
        if (lockUsername) tokenStore.markSessionExpired() else tokenStore.clear()
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

                tokenStore.saveLogin(username, body)
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error("Login fehlgeschlagen")
            }
        } catch (e: Exception) {
            AuthResult.Error("Keine Verbindung zum Server")
        }
    }

    // ─── Wheelhotels laden ───────────────────────────────────────────────────
    // Bearer setzt der AuthInterceptor, ein abgelaufener Token wird vom
    // TokenAuthenticator still erneuert. Ein 401 landet hier nur noch, wenn
    // AUCH der Refresh fehlgeschlagen ist -> echte Session-Expiry.

    suspend fun getWheelhotels(): AuthResult<List<Wheelhotel>> {
        if (tokenStore.accessToken == null) return AuthResult.Error("Nicht eingeloggt")

        return try {
            val response = fourWheelsApi.getWheelhotels()

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
}
