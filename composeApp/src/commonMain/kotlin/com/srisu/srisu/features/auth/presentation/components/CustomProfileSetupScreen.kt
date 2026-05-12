package com.srisu.srisu.features.auth.presentation.components

import kotlinx.serialization.Serializable

sealed class CustomProfileSetupScreen(val title: String) : Comparable<CustomProfileSetupScreen> {
    data object AddFullNameScreen : CustomProfileSetupScreen("Full name")
    data object AddDOBScreen : CustomProfileSetupScreen("Date of birth")
    data object ZodiacScreen : CustomProfileSetupScreen("Zodiac Sign")
    data object SelectGenderScreen : CustomProfileSetupScreen("Gender")
    data object SelectRelationshipScreen : CustomProfileSetupScreen("SelectRelationshipScreen")
    data object SetProfilePictureScreen : CustomProfileSetupScreen("Set Profile Picture")

    override fun compareTo(other: CustomProfileSetupScreen): Int {
        return screenOrder.indexOf(this) - screenOrder.indexOf(other)
    }

    override fun toString(): String {
        return this.title
    }

    companion object {
        val screenOrder: ArrayDeque<CustomProfileSetupScreen> by lazy {
            ArrayDeque(
                listOf(
                    AddFullNameScreen,
                    AddDOBScreen,
                    ZodiacScreen,
                    SelectGenderScreen,
                    SelectRelationshipScreen,
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
