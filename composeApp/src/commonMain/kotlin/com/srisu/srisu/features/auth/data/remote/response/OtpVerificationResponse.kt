package com.srisu.srisu.features.auth.data.remote.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class OtpVerificationResponse(
    @SerialName("tokens")
    val tokens: Tokens? = null,
    @SerialName("user")
    val user: User? = null
) {
    @Serializable
    data class Tokens(
        @SerialName("access")
        val access: String? = null,
        @SerialName("refresh")
        val refresh: String? = null
    )
}
