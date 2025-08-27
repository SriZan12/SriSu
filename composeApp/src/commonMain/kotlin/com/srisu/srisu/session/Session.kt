package com.srisu.srisu.session

import com.srisu.srisu.core.data.response.auth.ProfileResponse
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.data.response.auth.User.UserInterest
import com.srisu.srisu.core.data.response.auth.User.UserPhoto
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Session(
    @SerialName("access")
    val access: String? = null,
    @SerialName("refresh")
    val refresh: String? = null,
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
    @SerialName("username")
    val username: String? = null,
    @SerialName("zodiac_sign")
    val zodiacSign: String? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("city")
    val city: String? = null,
    @SerialName("user_interests")
    val userInterests: List<UserInterest?>? = null,
    @SerialName("user_photos")
    val userPhotos: List<UserPhoto?>? = null,
    @SerialName("bio")
    val bio: String? = null,
)

fun User.toSession(access: String?, refresh: String?,id: Int?): Session {
    return Session(
        access = access,
        refresh = refresh,
        createdDate = this.createdDate,
        dateJoined = this.dateJoined,
        dob = this.dob,
        bio = this.bio,
        email = this.email,
        firstName = this.firstName,
        fullName = this.fullName,
        gender = this.gender,
        id = id,
        isActive = this.isActive,
        isPhoneVerified = this.isPhoneVerified,
        isProfileComplete = this.isProfileComplete,
        isStaff = this.isStaff,
        isSuperuser = this.isSuperuser,
        lastName = this.lastName,
        mood = this.mood,
        phoneNumber = this.phoneNumber,
        profilePhoto = this.profilePhoto,
        updatedDate = this.updatedDate,
        username = this.username,
        zodiacSign = this.zodiacSign,
        country = this.country,
        city = this.city,
        userInterests = this.userInterests,
        userPhotos = this.userPhotos
    )
}


fun setUserWholeCredentials(
    access: String?,
    refresh: String?,
    userInfo: ProfileResponse.User?
): String {
    val credentials = Session(
        access = access,
        refresh = refresh,
        id = userInfo?.id,
        dob = userInfo?.dob,
        bio = userInfo?.bio,
        fullName = userInfo?.fullName,
        gender = userInfo?.gender,
        isPhoneVerified = userInfo?.isPhoneVerified,
        isProfileComplete = userInfo?.isProfileComplete,
        mood = userInfo?.mood,
        phoneNumber = userInfo?.phoneNumber,
        profilePhoto = userInfo?.profilePhoto,
        username = userInfo?.username,
        zodiacSign = userInfo?.zodiacSign,
        country = userInfo?.country,
        city = userInfo?.city,
        userInterests = userInfo?.userInterests,
        userPhotos = userInfo?.userPhotos
    )

    return Json.encodeToString(credentials)

}
