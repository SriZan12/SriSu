package com.srisu.srisu.core.data.response.auth


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ProfileSetupResponse(
    @SerialName("user")
    val user: User? = null
) {
    @Serializable
    data class User(
        @SerialName("dob")
        val dob: String? = null,
        @SerialName("full_name")
        val fullName: String? = null,
        @SerialName("gender")
        val gender: String? = null,
        @SerialName("is_phone_verified")
        val isPhoneVerified: Boolean? = null,
        @SerialName("is_profile_complete")
        val isProfileComplete: Boolean? = null,
        @SerialName("mood")
        val mood: String? = null,
        @SerialName("phone_number")
        val phoneNumber: String? = null,
        @SerialName("profile_photo")
        val profilePhoto: String? = null,
        @SerialName("zodiac_sign")
        val zodiacSign: String? = null
    )
}
