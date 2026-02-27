package com.fourwheels.radwechsel.api

import com.fourwheels.radwechsel.model.TokenResponse
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.model.WheelChangeRequest
import retrofit2.Response
import retrofit2.http.*

// ─── Auth (login-test.4wheels.de) ────────────────────────────────────────────

interface AuthApi {

    /**
     * OAuth2 Password Flow.
     * Content-Type: application/x-www-form-urlencoded  (Retrofit @FormUrlEncoded)
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
}

// ─── 4Wheels API (api-test.4wheels.de) ───────────────────────────────────────

interface FourWheelsApi {

    @GET("api/4wheels/wheelhotels")
    suspend fun getWheelhotels(
        @Header("Authorization") bearer: String
    ): Response<List<Wheelhotel>>

    /**
     * Endpunkt existiert noch nicht – Struktur abgestimmt auf WheelChangeRequest.
     * Wird in der Offline-Queue gespeichert und gesendet sobald der Endpunkt live ist.
     */
    @POST("api/4wheels/wheel-change")
    suspend fun postWheelChange(
        @Header("Authorization") bearer: String,
        @Body body: WheelChangeRequest
    ): Response<Unit>
}
