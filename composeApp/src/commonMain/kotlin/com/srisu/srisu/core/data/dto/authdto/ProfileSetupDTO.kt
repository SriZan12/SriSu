package com.srisu.srisu.core.data.dto.authdto


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ProfileSetupDTO(
    @SerialName("dob")
    val dob: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("gender")
    val gender: String? = null,
    @SerialName("mood")
    val mood: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("profile_photo")
    val profilePhoto: String? = null,
    @SerialName("zodiac_sign")
    val zodiacSign: String? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("city")
    val city: String? = null
)
