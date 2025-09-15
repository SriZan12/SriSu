package com.srisu.srisu.core.data.response.suggestion


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserPreferenceResponse(
    @SerialName("city")
    val city: String? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("id")
    val id: Int? = null,
    @SerialName("max_age")
    val maxAge: Int? = null,
    @SerialName("min_age")
    val minAge: Int? = null,
    @SerialName("radius_km")
    val radiusKm: Int? = null,
    @SerialName("zodiac_sign")
    val zodiac_sign: String? = null
)