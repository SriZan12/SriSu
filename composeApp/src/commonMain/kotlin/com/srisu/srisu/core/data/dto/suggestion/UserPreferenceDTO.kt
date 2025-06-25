package com.srisu.srisu.core.data.dto.suggestion


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserPreferenceDTO(
    @SerialName("city")
    val city: String? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("zodiac_sign")
    val zodiacSign: String? = null,
    @SerialName("max_age")
    val maxAge: Int? = null,
    @SerialName("min_age")
    val minAge: Int? = null,
    @SerialName("radius_km")
    val radiusKm: Int? = null,
    @SerialName("user")
    val user: Int? = null
)