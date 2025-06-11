package com.srisu.srisu.session

import com.srisu.srisu.core.data.response.auth.ProfileSetupResponse
import com.srisu.srisu.core.data.response.auth.User
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
    val zodiacSign: String? = null
)

fun User.toSession(access: String?, refresh: String?): Session {
    return Session(
        access = access,
        refresh = refresh,
        createdDate = this.createdDate,
        dateJoined = this.dateJoined,
        dob = this.dob,
        email = this.email,
        firstName = this.firstName,
        fullName = this.fullName,
        gender = this.gender,
        id = this.id,
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
        zodiacSign = this.zodiacSign
    )
}


fun setUserWholeCredentials(
    access: String?,
    refresh: String?,
    userInfo: ProfileSetupResponse.User?
): String {
    val credentials = Session(
        access = access,
        refresh = refresh,
        dob = userInfo?.dob,
        fullName = userInfo?.fullName,
        gender = userInfo?.gender,
        isPhoneVerified = userInfo?.isPhoneVerified,
        isProfileComplete = userInfo?.isProfileComplete,
        mood = userInfo?.mood,
        phoneNumber = userInfo?.phoneNumber,
        profilePhoto = userInfo?.profilePhoto,
        zodiacSign = userInfo?.zodiacSign
    )

    return Json.encodeToString(credentials)

}
