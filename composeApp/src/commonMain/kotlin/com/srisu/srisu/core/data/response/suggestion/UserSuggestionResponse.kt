package com.srisu.srisu.core.data.response.suggestion


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class UserSuggestionResponse(
    @SerialName("count")
    val count: Int? = null,
    @SerialName("next")
    val next: String? = null,
    @SerialName("previous")
    val previous: String? = null,
    @SerialName("results")
    val results: List<Result?>? = null
) {
    @Serializable
    data class Result(
        @SerialName("bio")
        val bio: String? = null,
        @SerialName("city")
        val city: String? = null,
        @SerialName("country")
        val country: String? = null,
        @SerialName("crushed")
        var crushed: Boolean? = null,
        @SerialName("dob")
        val dob: String? = null,
        @SerialName("full_name")
        val fullName: String? = null,
        @SerialName("gender")
        val gender: String? = null,
        @SerialName("id")
        val id: Int? = null,
        @SerialName("mood")
        val mood: String? = null,
        @SerialName("phone_number")
        val phoneNumber: String? = null,
        @SerialName("profile_photo")
        val profilePhoto: String? = null,
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
            val user: Int? = null
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
}
