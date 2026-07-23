package com.fourwheels.radwechsel.auth

import com.fourwheels.radwechsel.BuildConfig
import com.fourwheels.radwechsel.api.AuthApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Stiller Token-Refresh bei HTTP 401.
 *
 * Der Access-Token läuft nach ~1h ab. Statt den Nutzer neu einloggen zu lassen,
 * tauscht dieser Authenticator den Refresh-Token gegen ein frisches Token und
 * wiederholt den fehlgeschlagenen Request automatisch.
 *
 * - Der [Mutex] verhindert, dass mehrere parallele 401-Requests gleichzeitig
 *   refreshen.
 * - War ein anderer Thread schneller, wird der Request einfach mit dem bereits
 *   erneuerten Token wiederholt.
 * - Schlägt der Refresh fehl (kein/abgelaufener Refresh-Token), wird die Session
 *   als abgelaufen markiert und der ursprüngliche 401 propagiert zurück an das
 *   Repository (-> "SESSION_EXPIRED" -> Re-Login).
 *
 * [AuthApi] wird über [Provider] injiziert, um jeden potenziellen Init-Zyklus
 * zwischen API-Client und Authenticator zu vermeiden. Der Refresh läuft über den
 * Login-Client (ohne diesen Authenticator) -> keine Endlosschleife.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: Provider<AuthApi>,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Schon einmal (erfolglos) probiert -> aufgeben, sonst Endlosschleife.
        if (responseCount(response) >= 2) return null

        val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        return runBlocking {
            mutex.withLock {
                val current = tokenStore.accessToken

                // Ein anderer Thread hat bereits erneuert -> nur mit neuem Token wiederholen.
                if (current != null && current != staleToken) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $current")
                        .build()
                }

                val refresh = tokenStore.refreshToken ?: run {
                    tokenStore.markSessionExpired()
                    return@withLock null
                }

                val fresh = runCatching {
                    authApi.get().refresh(
                        clientId = BuildConfig.CLIENT_ID,
                        refreshToken = refresh,
                    )
                }.getOrElse {
                    tokenStore.markSessionExpired()
                    return@withLock null
                }

                tokenStore.saveTokens(fresh)

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${fresh.accessToken}")
                    .build()
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
