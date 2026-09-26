package com.srisu.srisu.features.chat.presentation.findpartner.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.cachedIn
import app.cash.paging.filter
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.data.paging.PartnerInvitationsPagingSource
import com.srisu.srisu.features.chat.presentation.findpartner.state.FindPartnerState
import com.srisu.srisu.features.chat.presentation.findpartner.state.PartnerSearchStatus
import com.srisu.srisu.features.chat.presentation.findpartner.state.SentPartnerInvitation
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.CoupleConnectionDTO
import com.srisu.srisu.features.home.connection.data.remote.mappers.toUser
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.utils.Constants
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.CountryModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.json.Json

class FindPartnerViewModel(
    private val connectionRepository: ConnectionRepository,
    private val sessionStorage: SessionStorage,
    private val countryProvider: () -> List<CountryModel> = { Country.getAllCountriesFromJson().orEmpty() },
) : ViewModel() {
    private val _findPartnerUIState = MutableStateFlow(FindPartnerState())
    val findPartnerUIState = _findPartnerUIState.asStateFlow()
    private var searchJob: Job? = null
    private var hasEntered = false

    // One cached Pager. Item access drives prefetch; recomposition never refreshes it.
    val loveRequests = combine(
        Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 3, enablePlaceholders = false),
            pagingSourceFactory = {
                PartnerInvitationsPagingSource { page ->
                    connectionRepository.getLoveRequests(pageSize = 20, page = page)
                }
            },
        ).flow.cachedIn(viewModelScope),
        _findPartnerUIState.map { it.handledRequestIds }.distinctUntilChanged(),
    ) { page, handled -> page.filter { it.id !in handled } }.cachedIn(viewModelScope)

    /** The shared NavHost creates this VM before authentication; defer API work until entry. */
    fun onScreenEntered() {
        val session = runCatching {
            sessionStorage.getSession(Constants.Auth.SESSION_KEY)?.let { Json.decodeFromString<Session>(it) }
        }.getOrNull()
        _findPartnerUIState.update { it.copy(senderPhoneNumber = session?.phoneNumber.orEmpty(),
            senderName = session?.fullName?.takeIf(String::isNotBlank) ?: "You", senderPhotoUrl = session?.profilePhoto) }
        if (hasEntered) return
        hasEntered = true
        _findPartnerUIState.update { it.copy(countryList = runCatching(countryProvider).getOrDefault(emptyList())) }
        sendHaveCoupleConnectionRequested()
    }

    fun updatePhoneNumber(phoneNumber: String) {
        if (_findPartnerUIState.value.isSendingInvitation) return
        val digits = phoneNumber.filter { it in '0'..'9' }.take(10)
        if (digits == _findPartnerUIState.value.phoneNumber) return
        cancelSearch()
        _findPartnerUIState.update { it.copy(phoneNumber = digits, validationErrorMsg = "") }
    }

    fun updateCountry(code: String, prefix: String) {
        if (_findPartnerUIState.value.isSendingInvitation) return
        cancelSearch()
        _findPartnerUIState.update { it.copy(countryCode = code, countryPrefix = prefix, validationErrorMsg = "") }
    }

    fun cancelSearch() {
        if (_findPartnerUIState.value.isSendingInvitation) return
        searchJob?.cancel()
        searchJob = null
        _findPartnerUIState.update {
            it.copy(searchStatus = PartnerSearchStatus.Idle, partnerResponse = null, searchError = null, invitationError = null)
        }
    }

    fun validatePhoneNumber(): Boolean {
        val state = _findPartnerUIState.value
        val error = when {
            state.phoneNumber.isBlank() -> "Enter your partner’s phone number."
            state.phoneNumber.length != 10 || state.phoneNumber.any { it !in '0'..'9' } ->
                "Enter a 10-digit phone number."
            state.countryPrefix + state.phoneNumber == state.senderPhoneNumber ->
                "Enter your partner’s number, not your own."
            else -> ""
        }
        _findPartnerUIState.update { it.copy(validationErrorMsg = error) }
        return error.isEmpty()
    }

    fun sendFindYourPartnerRequest() {
        val state = _findPartnerUIState.value
        if (state.searchStatus == PartnerSearchStatus.Loading || state.isSendingInvitation || !validatePhoneNumber()) return
        val phone = state.countryPrefix + state.phoneNumber
        _findPartnerUIState.update {
            it.copy(searchStatus = PartnerSearchStatus.Loading, searchError = null, partnerResponse = null, invitationError = null)
        }
        searchJob = viewModelScope.launch {
            when (val result = request { connectionRepository.sendFindYourPartnerRequest(phone) }) {
                is NetworkAPIResult.Success -> _findPartnerUIState.update {
                    val partner = result.response?.takeIf { person -> !person.phoneNumber.isNullOrBlank() }
                    it.copy(partnerResponse = partner, searchStatus = if (partner == null)
                        PartnerSearchStatus.NotFound else PartnerSearchStatus.Found)
                }
                is NetworkAPIResult.Error -> _findPartnerUIState.update {
                    it.copy(
                        searchStatus = if (result.errorType == NetworkAPIResult.ErrorType.NOT_FOUND)
                            PartnerSearchStatus.NotFound else PartnerSearchStatus.Error,
                        searchError = result.error ?: "We couldn’t search right now. Please try again.",
                    )
                }
            }
        }
    }

    fun sendCoupleConnectionRequest() {
        val state = _findPartnerUIState.value
        val partner = state.partnerResponse ?: return
        val receiver = partner.phoneNumber?.takeIf { it.isNotBlank() } ?: return
        if (state.isSendingInvitation || state.sentInvitation != null || state.acceptedPartnerName != null ||
            state.isCheckingInvitation || !state.invitationStatusChecked || state.updatingRequestIds.isNotEmpty()) return
        if (state.senderPhoneNumber.isBlank()) {
            _findPartnerUIState.update { it.copy(invitationError = "Your session has expired. Please sign in again.") }
            return
        }
        _findPartnerUIState.update { it.copy(isSendingInvitation = true, invitationError = null) }
        viewModelScope.launch {
            when (val result = request {
                connectionRepository.sendCoupleConnectionRequest(state.senderPhoneNumber, receiver)
            }) {
                is NetworkAPIResult.Success -> _findPartnerUIState.update {
                    it.copy(isSendingInvitation = false, navigateToInviteSent = true,
                        sentInvitation = SentPartnerInvitation(
                            requestId = result.response?.id?.toLong(),
                            name = partner.fullName?.takeIf(String::isNotBlank) ?: partner.username ?: "Your partner",
                            phoneNumber = receiver, photoUrl = partner.profilePhoto,
                        ))
                }
                is NetworkAPIResult.Error -> _findPartnerUIState.update {
                    it.copy(isSendingInvitation = false, invitationError = result.error ?: "Couldn’t send the invitation. Try again.")
                }
            }
            // Recover the request ID when a successful POST omitted its response body.
            if (_findPartnerUIState.value.sentInvitation?.requestId == null && _findPartnerUIState.value.sentInvitation != null) {
                sendHaveCoupleConnectionRequested()
            }
        }
    }

    fun onInviteSentNavigated() {
        _findPartnerUIState.update { it.copy(navigateToInviteSent = false) }
    }

    fun sendHaveCoupleConnectionRequested() {
        if (_findPartnerUIState.value.isCheckingInvitation || _findPartnerUIState.value.isCancellingInvitation) return
        _findPartnerUIState.update { it.copy(isCheckingInvitation = true, invitationStatusError = null) }
        viewModelScope.launch {
            when (val result = request { connectionRepository.sendHaveCoupleConnectionRequested() }) {
                is NetworkAPIResult.Success -> _findPartnerUIState.update {
                    val connection = result.response?.connection
                    val pending = if (result.response?.connectionRequested == true && connection != null && connection.connectionStatus != Constants.ConnectionStatus.ACCEPTED) {
                        SentPartnerInvitation(
                            requestId = connection.id?.toLong(),
                            name = connection.partner?.fullName?.takeIf(String::isNotBlank)
                                ?: connection.partner?.username ?: it.sentInvitation?.name ?: "Your partner",
                            phoneNumber = connection.receiverNumber ?: connection.partner?.phoneNumber.orEmpty(),
                            photoUrl = connection.partner?.profilePhoto ?: it.sentInvitation?.photoUrl,
                        )
                    } else null
                    it.copy(isCheckingInvitation = false, invitationStatusChecked = result.response != null,
                        invitationStatusError = if (result.response == null) "Couldn’t check your invitation. Please retry." else null,
                        sentInvitation = if (result.response != null) pending else it.sentInvitation,
                        acceptedPartnerName = if (connection?.connectionStatus == Constants.ConnectionStatus.ACCEPTED)
                            connection.partner?.fullName ?: it.sentInvitation?.name ?: "Your partner" else it.acceptedPartnerName,
                        acceptedPartnerPhotoUrl = if (connection?.connectionStatus == Constants.ConnectionStatus.ACCEPTED)
                            connection.partner?.profilePhoto ?: it.sentInvitation?.photoUrl else it.acceptedPartnerPhotoUrl,
                        connectedSince = if (connection?.connectionStatus == Constants.ConnectionStatus.ACCEPTED)
                            formatConnectionDate(connection.updatedAt) ?: it.connectedSince else it.connectedSince)
                }
                is NetworkAPIResult.Error -> _findPartnerUIState.update {
                    it.copy(isCheckingInvitation = false, invitationStatusError = result.error ?: "Couldn’t check your invitation. Please retry.")
                }
            }
        }
    }

    fun cancelSentInvitation() {
        val state = _findPartnerUIState.value
        val invitation = state.sentInvitation ?: return
        val requestId = invitation.requestId ?: run { sendHaveCoupleConnectionRequested(); return }
        if (state.isCancellingInvitation || state.isCheckingInvitation || state.acceptedPartnerName != null || state.updatingRequestIds.isNotEmpty()) return
        _findPartnerUIState.update { it.copy(isCancellingInvitation = true, invitationError = null) }
        viewModelScope.launch {
            when (val result = request {
                connectionRepository.updateLoveRequest(requestId, CoupleConnectionDTO(
                    senderNumber = state.senderPhoneNumber, receiverNumber = invitation.phoneNumber,
                    connectionStatus = Constants.ConnectionStatus.NOTHING,
                ))
            }) {
                is NetworkAPIResult.Success -> _findPartnerUIState.update {
                    it.copy(isCancellingInvitation = false, sentInvitation = null, navigateToInviteSent = false,
                        partnerResponse = null, searchStatus = PartnerSearchStatus.Idle)
                }
                is NetworkAPIResult.Error -> _findPartnerUIState.update {
                    it.copy(isCancellingInvitation = false, invitationError = result.error ?: "Couldn’t cancel the invitation. Try again.")
                }
            }
        }
    }

    fun updateLoveRequest(loveRequestId: Long?, senderNumber: String?, receiverNumber: String?, connectionStatus: String?, partner: CoupleConnectionRequestResponse.Result.Receiver? = null) {
        val id = loveRequestId ?: return
        val state = _findPartnerUIState.value
        if (id in state.updatingRequestIds || id in state.handledRequestIds || state.acceptedPartnerName != null ||
            state.updatingRequestIds.isNotEmpty() || state.isSendingInvitation || state.isCancellingInvitation) return
        if (connectionStatus != Constants.ConnectionStatus.ACCEPTED && connectionStatus != Constants.ConnectionStatus.REJECTED) return
        _findPartnerUIState.update { it.copy(updatingRequestIds = it.updatingRequestIds + id, requestErrors = it.requestErrors - id) }
        viewModelScope.launch {
            when (val result = request {
                connectionRepository.updateLoveRequest(id, CoupleConnectionDTO(senderNumber = senderNumber, receiverNumber = receiverNumber, connectionStatus = connectionStatus))
            }) {
                is NetworkAPIResult.Success -> _findPartnerUIState.update {
                    it.copy(updatingRequestIds = it.updatingRequestIds - id, handledRequestIds = it.handledRequestIds + id,
                        acceptedPartnerName = if (connectionStatus == Constants.ConnectionStatus.ACCEPTED)
                            partner?.fullName?.takeIf(String::isNotBlank) ?: partner?.username ?: "Your partner" else it.acceptedPartnerName,
                        acceptedPartnerPhotoUrl = if (connectionStatus == Constants.ConnectionStatus.ACCEPTED)
                            partner?.profilePhoto else it.acceptedPartnerPhotoUrl,
                        connectedSince = if (connectionStatus == Constants.ConnectionStatus.ACCEPTED)
                            formatConnectionDate(result.response?.updatedAt) else it.connectedSince)
                }
                is NetworkAPIResult.Error -> _findPartnerUIState.update {
                    it.copy(updatingRequestIds = it.updatingRequestIds - id,
                        requestErrors = it.requestErrors + (id to (result.error ?: "Couldn’t update this invitation. Please retry.")))
                }
            }
        }
    }

    fun getUserProfile(userProfile: CoupleConnectionRequestResponse.Result.Receiver?): String? =
        runCatching { Json.encodeToString(userProfile?.toUser()) }.getOrNull()

    private suspend fun <T> request(block: suspend () -> ResultHandler<T>): NetworkAPIResult<T> = try {
        block().result.also { currentCoroutineContext().ensureActive() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        NetworkAPIResult.Error(error.message ?: "Something went wrong. Please try again.")
    }
}

/** Only display a date supplied by the confirmed connection response. */
internal fun formatConnectionDate(timestamp: String?): String? = runCatching {
    val date = LocalDate.parse(timestamp?.take(10) ?: return null)
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    "${date.day} ${months[date.month.number - 1]} ${date.year}"
}.getOrNull()
