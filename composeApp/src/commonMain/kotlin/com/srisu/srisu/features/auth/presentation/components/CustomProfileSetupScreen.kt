package com.srisu.srisu.features.auth.presentation.components

import kotlinx.serialization.Serializable

sealed class CustomProfileSetupScreen(val title: String) : Comparable<CustomProfileSetupScreen> {
    data object AddFullNameScreen : CustomProfileSetupScreen("Full name")
    data object SelectGenderScreen : CustomProfileSetupScreen("Gender")
    data object SetProfilePictureScreen : CustomProfileSetupScreen("Set Profile Picture")

    override fun compareTo(other: CustomProfileSetupScreen): Int {
        return registrationOrder.indexOf(this) - registrationOrder.indexOf(other)
    }

    override fun toString(): String {
        return this.title
    }

    companion object {
        val registrationOrder by lazy { listOf(AddFullNameScreen, SelectGenderScreen, SetProfilePictureScreen) }

    }
}


@Serializable
data class OTPScreenMetadata(
    val countryCode: String,
    val countryPrefix: String,
    val phoneNumber: String,
    val saveTime: Long,
    val totalTime: Long,
    val challengeId: String? = null,
    val expiresAt: Long = 0
)
