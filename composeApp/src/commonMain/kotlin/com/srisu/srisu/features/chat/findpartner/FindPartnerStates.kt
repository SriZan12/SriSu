package com.srisu.srisu.features.chat.findpartner

import androidx.compose.runtime.Stable
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.utils.CountryModel

@Stable
data class FindPartnerState(
    val phoneNumber: String = "",
    val countryList: List<CountryModel> = emptyList(),
    val countryCode: String = "NP",
    val countryPrefix: String = "+977",
    val isPhoneNumberInvalid: Boolean = false,
    val validationErrorMsg: String = "",
    val baseUIState: BaseUIState = BaseUIState.Idle,
    )