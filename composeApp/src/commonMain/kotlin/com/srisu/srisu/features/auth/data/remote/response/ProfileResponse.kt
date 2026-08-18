package com.srisu.srisu.features.auth.data.remote.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    @SerialName("user")
    val user: User? = null
) {
    @Serializable
    data class User(
        @SerialName("id")
        val id: Long? = null,
        @SerialName("bio")
        val bio: String? = null,
        @SerialName("city")
        val city: String? = null,
        @SerialName("country")
        val country: String? = null,
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
        @SerialName("user_interests")
        val userInterests: List<com.srisu.srisu.features.auth.data.remote.response.User.UserInterest?>? = null,
        @SerialName("user_photos")
        val userPhotos: List<com.srisu.srisu.features.auth.data.remote.response.User.UserPhoto?>? = null,
        @SerialName("username")
        val username: String? = null,
        @SerialName("zodiac_sign")
        val zodiacSign: String? = null,
        @SerialName("is_engaged")
        val isEngaged: Boolean? = null
    )
}
