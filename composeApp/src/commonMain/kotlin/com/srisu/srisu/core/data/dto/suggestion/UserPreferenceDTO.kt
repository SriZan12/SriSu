package com.srisu.srisu.core.data.dto.suggestion


import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserPreferenceDTO(
    @Required
    @SerialName("city")
    val city: String? = null,
    @Required
    @SerialName("country")
    val country: String? = null,
    @Required
    @SerialName("zodiac_sign")
    val zodiacSign: String? = null,
    @SerialName("max_age")
    val maxAge: Int? = null,
    @SerialName("min_age")
    val minAge: Int? = null,
    @Required
    @SerialName("radius_km")
    val radiusKm: Int? = null,
    @SerialName("user")
    val user: Long? = null
)