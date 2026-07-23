package com.fourwheels.radwechsel.api

import com.fourwheels.radwechsel.model.TokenResponse
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.model.WheelChangeRequest
import retrofit2.Response
import retrofit2.http.*

// ─── Auth (login-test.4wheels.de) ────────────────────────────────────────────

interface AuthApi {

    /**
     * OAuth2 Password Flow – Erst-Login mit Credentials.
     * Content-Type: application/x-www-form-urlencoded (Retrofit @FormUrlEncoded)
     */
    @FormUrlEncoded
    @POST("internal/token")
    suspend fun login(
        @Field("grant_type") grantType: String = "password",
        @Field("client_id")  clientId: String,
        @Field("scope")      scope: String = "meta:read wheelhotel:read wheelhotel:write",
        @Field("username")   username: String,
        @Field("password")   password: String
    ): Response<TokenResponse>

    /**
     * OAuth2 Refresh Flow – tauscht den Refresh-Token gegen ein frisches
     * Access-Token. Wird vom [com.fourwheels.radwechsel.auth.TokenAuthenticator]
     * bei HTTP 401 aufgerufen.
     *
     * Gibt TokenResponse DIREKT zurück (kein Response-Wrapper): bei Fehler wirft
     * Retrofit eine Exception, die der Authenticator via runCatching abfängt.
     */
    @FormUrlEncoded
    @POST("internal/token")
    suspend fun refresh(
        @Field("grant_type")    grantType: String = "refresh_token",
        @Field("client_id")     clientId: String,
        @Field("refresh_token") refreshToken: String
    ): TokenResponse
}

// ─── 4Wheels API (api-test.4wheels.de) ───────────────────────────────────────
// Der Authorization-Header wird vom AuthInterceptor automatisch gesetzt,
// der 401-Refresh vom TokenAuthenticator – daher KEIN @Header-Parameter mehr.

interface FourWheelsApi {

    @GET("api/4wheels/wheelhotels")
    suspend fun getWheelhotels(): Response<List<Wheelhotel>>

    /**
     * Endpunkt existiert noch nicht – Struktur abgestimmt auf WheelChangeRequest.
     * Wird in der Offline-Queue gespeichert und gesendet sobald der Endpunkt live ist.
     */
    @POST("api/4wheels/wheel-change")
    suspend fun postWheelChange(
        @Body body: WheelChangeRequest
    ): Response<Unit>
}
