package com.srisu.srisu.core.data.dto.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileUpdateDTO(
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("gender")
    val gender: String? = null,
    @SerialName("zodiac_sign")
    val zodiacSign: String? = null,
    @SerialName("dob")
    val dob: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("mood")
    val mood: String? = null,
    @SerialName("profile_photo")
    val profilePhoto: String? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("city")
    val city: String? = null,
    @SerialName("bio")
    val bio: String? = null,
    @SerialName("user_interests")
    val userInterests: List<String?>? = null,
    @SerialName("user_photos")
    val userPhotos: List<String>? = null
)