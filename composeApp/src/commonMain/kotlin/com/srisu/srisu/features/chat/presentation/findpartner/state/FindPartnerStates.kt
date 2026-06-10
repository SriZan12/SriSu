package com.srisu.srisu.features.chat.presentation.findpartner.state

import androidx.compose.runtime.Stable
import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.data.remote.response.HaveCoupleConnectionResponse
import com.srisu.srisu.utils.CountryModel
import kotlinx.coroutines.flow.Flow

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
    val haveCoupleConnectionRequestedResponse: HaveCoupleConnectionResponse? = null,
    val senderPhoneNumber: String = "",
    val handledRequestIds: Set<Long> = emptySet(),
    var loveRequests: Flow<PagingData<CoupleConnectionRequestResponse.Result>>? = null
)