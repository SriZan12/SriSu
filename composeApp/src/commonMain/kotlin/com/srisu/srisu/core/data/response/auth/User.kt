package com.srisu.srisu.core.data.response.auth


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class User(
    @SerialName("bio")
    val bio: String? = null,
    @SerialName("city")
    val city: String? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("created_date")
    val createdDate: String? = null,
    @SerialName("date_joined")
    val dateJoined: String? = null,
    @SerialName("dob")
    val dob: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("gender")
    val gender: String? = null,
    @SerialName("id")
    val id: Int? = null,
    @SerialName("is_active")
    val isActive: Boolean? = null,
    @SerialName("is_phone_verified")
    val isPhoneVerified: Boolean? = null,
    @SerialName("is_profile_complete")
    val isProfileComplete: Boolean? = null,
    @SerialName("is_staff")
    val isStaff: Boolean? = null,
    @SerialName("is_superuser")
    val isSuperuser: Boolean? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("mood")
    val mood: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("profile_photo")
    val profilePhoto: String? = null,
    @SerialName("updated_date")
    val updatedDate: String? = null,
    @SerialName("user_interests")
    val userInterests: List<UserInterest?>? = null,
    @SerialName("user_photos")
    val userPhotos: List<UserPhoto?>? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("zodiac_sign")
    val zodiacSign: String? = null
) {
    @Serializable
    data class UserInterest(
        @SerialName("id")
        val id: Int? = null,
        @SerialName("name")
        val name: String? = null,
        @SerialName("user")
        val user: Int? = null,
        @SerialName("interest") //This is the interest id from the InterestModel
        val interest: Int? = null,
        @SerialName("removed")
        val removed: Boolean? = null,
    )

    @Serializable
    data class UserPhoto(
        @SerialName("created_date")
        val createdDate: String? = null,
        @SerialName("id")
        val id: Int? = null,
        @SerialName("photo")
        val photo: String? = null,
        @SerialName("updated_date")
        val updatedDate: String? = null,
        @SerialName("user")
        val user: Int? = null
    )
}

