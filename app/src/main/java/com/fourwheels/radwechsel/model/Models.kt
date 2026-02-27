package com.fourwheels.radwechsel.model

import com.google.gson.annotations.SerializedName

// ─── Auth ────────────────────────────────────────────────────────────────────

data class TokenResponse(
    @SerializedName("token_type")    val tokenType: String,
    @SerializedName("expires_in")    val expiresIn: Int,
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

// ─── Wheelhotel ──────────────────────────────────────────────────────────────

data class Wheelhotel(
    @SerializedName("id")             val id: String,           // "10100" – ist die Kostenstelle
    @SerializedName("name")           val name: String,
    @SerializedName("address")        val address: WheelhotelAddress?,
    @SerializedName("branchManager")  val branchManager: BranchManager?
) {
    /** Lesbare Kurzbezeichnung für die UI – filtert leere Namen heraus */
    val displayName: String get() = name.ifBlank { "Standort $id" }

    /** Stadt für die Untertitelzeile */
    val city: String get() = address?.city?.ifBlank { "–" } ?: "–"
}

data class WheelhotelAddress(
    @SerializedName("address")  val address: String?,
    @SerializedName("address2") val address2: String?,
    @SerializedName("city")     val city: String?,
    @SerializedName("zip")      val zip: String?,
    @SerializedName("phone")    val phone: String?,
    @SerializedName("email")    val email: String?
)

data class BranchManager(
    @SerializedName("id")   val id: String,
    @SerializedName("name") val name: String
)

// ─── Wheel Change Request ─────────────────────────────────────────────────────

data class WheelChangeRequest(
    @SerializedName("wheelhotel")    val wheelhotel: String,      // id des Wheelhotels
    @SerializedName("username")      val username: String,
    @SerializedName("licensePlate")  val licensePlate: String,    // Kennzeichen / Auftragsnummer
    @SerializedName("torque")        val torque: String,           // Drehmoment in Nm als String
    @SerializedName("startedAt")     val startedAt: String,        // ISO 8601 UTC
    @SerializedName("finishedAt")    val finishedAt: String         // ISO 8601 UTC
)
