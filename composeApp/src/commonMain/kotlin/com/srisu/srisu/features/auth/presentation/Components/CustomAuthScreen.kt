package com.srisu.srisu.features.auth.presentation.Components

import kotlinx.serialization.Serializable

sealed class CustomAuthScreen(val title: String) : Comparable<CustomAuthScreen> {
    data object AddPhoneNumberScreen : CustomAuthScreen("Phone number")
    data object PhoneNumberVerificationScreen : CustomAuthScreen("Number verification")
    data object AddFullNameScreen : CustomAuthScreen("Full name")
    data object AddDOBScreen : CustomAuthScreen("Date of birth")
    data object ZodiacScreen : CustomAuthScreen("Zodiac Sign")
    data object SelectGenderScreen : CustomAuthScreen("Gender")
    data object SetProfilePictureScreen : CustomAuthScreen("Set Profile Picture")

    override fun compareTo(other: CustomAuthScreen): Int {
        return screenOrder.indexOf(this) - screenOrder.indexOf(other)
    }

    override fun toString(): String {
        return this.title
    }

    companion object {
        val screenOrder: ArrayDeque<CustomAuthScreen> by lazy {
            ArrayDeque(
                listOf(
                    AddPhoneNumberScreen,
                    PhoneNumberVerificationScreen,
                    AddFullNameScreen,
                    AddDOBScreen,
                    ZodiacScreen,
                    SelectGenderScreen,
                    SetProfilePictureScreen
                )
            )
        }
    }
}


@Serializable
data class OTPScreenMetadata(
    val countryCode: String,
    val countryPrefix: String,
    val phoneNumber: String,
    val saveTime: Long,
    val totalTime: Long
)
