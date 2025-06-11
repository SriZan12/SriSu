package com.srisu.srisu.core.data.response.suggestion


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class UserSuggestionResponse(
    @SerialName("count")
    val count: Int?,
    @SerialName("next")
    val next: String?,
    @SerialName("previous")
    val previous: String?,
    @SerialName("results")
    val results: List<Result?>?
) {
    @Serializable
    data class Result(
        @SerialName("bio")
        val bio: String?,
        @SerialName("city")
        val city: String?,
        @SerialName("country")
        val country: String?,
        @SerialName("created_date")
        val createdDate: String?,
        @SerialName("date_joined")
        val dateJoined: String?,
        @SerialName("dob")
        val dob: String?,
        @SerialName("email")
        val email: String?,
        @SerialName("first_name")
        val firstName: String?,
        @SerialName("full_name")
        val fullName: String?,
        @SerialName("gender")
        val gender: String?,
        @SerialName("id")
        val id: Int?,
        @SerialName("is_active")
        val isActive: Boolean?,
        @SerialName("is_phone_verified")
        val isPhoneVerified: Boolean?,
        @SerialName("is_profile_complete")
        val isProfileComplete: Boolean?,
        @SerialName("is_staff")
        val isStaff: Boolean?,
        @SerialName("is_superuser")
        val isSuperuser: Boolean?,
        @SerialName("last_name")
        val lastName: String?,
        @SerialName("mood")
        val mood: String?,
        @SerialName("phone_number")
        val phoneNumber: String?,
        @SerialName("profile_photo")
        val profilePhoto: String?,
        @SerialName("updated_date")
        val updatedDate: String?,
        @SerialName("user_interests")
        val userInterests: List<UserInterest?>?,
        @SerialName("user_photos")
        val userPhotos: List<UserPhoto?>?,
        @SerialName("username")
        val username: String?,
        @SerialName("zodiac_sign")
        val zodiacSign: String?
    ) {
        @Serializable
        data class UserInterest(
            @SerialName("id")
            val id: Int?,
            @SerialName("name")
            val name: String?,
            @SerialName("user")
            val user: Int?
        )

        @Serializable
        data class UserPhoto(
            @SerialName("created_date")
            val createdDate: String?,
            @SerialName("id")
            val id: Int?,
            @SerialName("photo")
            val photo: String?,
            @SerialName("updated_date")
            val updatedDate: String?,
            @SerialName("user")
            val user: Int?
        )
    }
}