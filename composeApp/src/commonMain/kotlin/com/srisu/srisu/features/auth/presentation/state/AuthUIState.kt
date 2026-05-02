package com.srisu.srisu.features.auth.presentation.state

import androidx.compose.runtime.Stable
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.auth.presentation.components.CustomProfileSetupScreen
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.Gender
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.ZodiacUtils.ZodiacSign

@Stable
data class AuthUIStates(
    val phoneNumber: String = "",
    val fullName: String = "",
    val username: String = "",
    val dob: String = "",
    val age: String = "",
    val gender: Gender = Gender.NONE,
    val currentProgressStep: Int = 0,
    val countryCode: String = "NP",
    val countryPrefix: String = "+977",
    val progress: Float = 0f,
    val relationshipSituation: RelationshipSituation = RelationshipSituation.NOTHING,
    val optValues: List<String> = mutableListOf("", "", "", "", "", ""),
    val isPhoneNumberVerified: Boolean = false,
    val remainingOTPTimestamp: Long? = null,
    val zodiacSignList: List<ZodiacSign> = emptyList(),
    val zodiacSign: ZodiacSign? = null,
    val profilePictureUri: Uri? = null,
    val session: Session? = null,
    val currentScreen: CustomProfileSetupScreen = CustomProfileSetupScreen.SelectGenderScreen,
    val screenStack: ArrayDeque<CustomProfileSetupScreen> = ArrayDeque(),
    val baseUIState: BaseUIState = BaseUIState.Idle,
    val validationError: Validation = Validation(),
    val countryList: List<CountryModel> = emptyList()
)

data class Validation(
    val validationMessage: String = "",
    val isPhoneNumber: Boolean = false,
    val isOtp: Boolean = false,
    val isFullName: Boolean = false,
    val isUserName: Boolean = false,
    val isDOB: Boolean = false,
    val isGender: Boolean = false,
    val isRelationship: Boolean = false
)

enum class RelationshipSituation {
    SINGLE,
    COUPLE,
    NOTHING
}