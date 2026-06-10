package com.srisu.srisu.features.chat.presentation.findpartner.state

import androidx.compose.runtime.Stable
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
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
    val showPartnerProfile: Boolean = false,
    val partnerResponse: FindYourPartnerResponse? = null,
    val senderPhoneNumber: String = "",
    val isConnectionRequestSent: Boolean = false,
    val handledRequestIds: Set<Long> = emptySet(),
    )