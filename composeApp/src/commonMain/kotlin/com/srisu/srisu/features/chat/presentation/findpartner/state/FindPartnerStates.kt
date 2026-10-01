package com.srisu.srisu.features.chat.presentation.findpartner.state

import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.utils.CountryModel

enum class PartnerSearchStatus { Idle, Loading, Found, NotFound, Error }

data class SentPartnerInvitation(
    val requestId: Long? = null,
    val name: String,
    val phoneNumber: String,
    val photoUrl: String? = null,
)

/** Search, outgoing invitation and received-request mutations load independently. */
data class FindPartnerState(
    val phoneNumber: String = "",
    val countryList: List<CountryModel> = emptyList(),
    val countryCode: String = "NP",
    val countryPrefix: String = "+977",
    val validationErrorMsg: String = "",
    val searchStatus: PartnerSearchStatus = PartnerSearchStatus.Idle,
    val searchError: String? = null,
    val partnerResponse: FindYourPartnerResponse? = null,
    val senderName: String = "You",
    val senderPhotoUrl: String? = null,
    val acceptedPartnerPhotoUrl: String? = null,
    val connectedSince: String? = null,
    val senderPhoneNumber: String = "",
    val isSendingInvitation: Boolean = false,
    val invitationError: String? = null,
    val sentInvitation: SentPartnerInvitation? = null,
    val navigateToInviteSent: Boolean = false,
    val isCheckingInvitation: Boolean = false,
    val invitationStatusChecked: Boolean = false,
    val invitationStatusError: String? = null,
    val isCancellingInvitation: Boolean = false,
    val handledRequestIds: Set<Long> = emptySet(),
    val updatingRequestIds: Set<Long> = emptySet(),
    val requestErrors: Map<Long, String> = emptyMap(),
    val acceptedPartnerName: String? = null,
)
