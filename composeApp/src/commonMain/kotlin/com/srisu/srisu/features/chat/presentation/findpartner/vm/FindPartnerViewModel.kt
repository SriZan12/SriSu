package com.srisu.srisu.features.chat.presentation.findpartner.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.cash.paging.PagingData
import app.cash.paging.filter
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.remote.BasePagingSource
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.connection.coupleconnection.domain.repository.ConnectionRepository
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.presentation.findpartner.state.FindPartnerState
import com.srisu.srisu.features.chat.data.remote.response.FindYourPartnerResponse
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.CoupleConnectionDTO
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.data.remote.mappers.toUser
import com.srisu.srisu.utils.Constants
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import com.srisu.srisu.utils.Constants.ConnectionStatus.REJECTED
import com.srisu.srisu.utils.Country
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.collections.contains

class FindPartnerViewModel(
    val connectionRepository: ConnectionRepository,
    private val sessionStorage: SessionStorage,
) : ViewModel() {

    private val _findPartnerUIState: MutableStateFlow<FindPartnerState> =
        MutableStateFlow(FindPartnerState())
    val findPartnerUIState = this._findPartnerUIState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5000
        ),
        initialValue = FindPartnerState()
    )

    private val receivedLoveRequestTrigger = MutableStateFlow(0)


    init {
        loadAllCountries()
        setSenderPhoneNumber()
    }

    private fun showSuccessMessage(message: String) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(
                baseUIState = BaseUIState.Success(
                    data = null,
                    message = message
                )
            )
    }

    private fun showLoading() {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(baseUIState = BaseUIState.Loading)
    }

    fun idleScreen() {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(baseUIState = BaseUIState.Idle)
    }

    fun showErrorMessage(errorType: String, message: String) {
        this._findPartnerUIState.value = this._findPartnerUIState.value.copy(
            baseUIState =
                BaseUIState.Error(errorType = errorType, message = message)
        )
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(
                baseUIState = BaseUIState.NoInternetConnection(
                    isOffline = isOffline
                )
            )
    }

//    private fun isInternetAvailable(): Boolean {
//        return connectivityObserver.isConnected.value
//    }

    fun setSenderPhoneNumber() {
        val sessionData = sessionStorage.getSession(sessionKey = Constants.Auth.SESSION_KEY)
        var session: Session? = null
        if (sessionData != null) {
            session = Json.decodeFromString<Session>(sessionData)
        }
        updateSenderPhoneNumber(phoneNumber = session?.phoneNumber)
    }

    fun updatePhoneNumber(phoneNumber: String) {
        if (phoneNumber.length <= 10) {
            this._findPartnerUIState.value =
                this._findPartnerUIState.value.copy(phoneNumber = phoneNumber)
        }
    }

    fun updateSenderPhoneNumber(phoneNumber: String?) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(senderPhoneNumber = phoneNumber ?: "")
    }

    fun updateCountry(code: String, prefix: String) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(countryCode = code, countryPrefix = prefix)
    }

    fun updateShowPartnerProfile(showPartnerProfile: Boolean) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(showPartnerProfile = showPartnerProfile)
    }

    fun refreshLoveRequests() {
        receivedLoveRequestTrigger.update { it + 1 }
    }

    fun validatePhoneNumber(): Boolean {
        val phone = _findPartnerUIState.value.phoneNumber

        return when {
            phone.isEmpty() -> {
                updateValidationError("Phone number cannot be empty")
                false
            }

            phone.length != 10 -> {
                updateValidationError("Phone number must be 10 digits")
                false
            }

            !phone.all { it.isDigit() } -> {
                updateValidationError("Phone number must contain digits only")
                false
            }

            else -> {
                updateValidationError("")  // clear error
                true
            }
        }
    }

    private fun updateValidationError(msg: String) {
        _findPartnerUIState.value = _findPartnerUIState.value.copy(
            validationErrorMsg = msg
        )
    }

    private fun updatePartnerResponse(partnerResponse: FindYourPartnerResponse?) {
        _findPartnerUIState.value =
            _findPartnerUIState.value.copy(partnerResponse = partnerResponse)
    }


    private fun loadAllCountries() {
        viewModelScope.launch {
            val countries = Country.getAllCountriesFromJson() ?: emptyList()
            _findPartnerUIState.value = _findPartnerUIState.value.copy(countryList = countries)
        }
    }

    //Paging flows
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loveRequestPagingFlow =
        receivedLoveRequestTrigger
            .flatMapLatest {
                createPagingFlow { page ->
                    connectionRepository.getLoveRequests(pageSize = 20, page = page)
                }
            }
            .cachedIn(viewModelScope)

    val loveRequests: Flow<PagingData<CoupleConnectionRequestResponse.Result>> =
        combine(
            loveRequestPagingFlow,
            _findPartnerUIState.map { it.handledRequestIds }
        ) { pagingData, handledIds ->
            pagingData.filter { it.id?.toLong() !in handledIds }
        }.cachedIn(viewModelScope)

    fun updateLoveRequest(
        loveRequestId: Int?,
        senderNumber: String?,
        receiverNumber: String?,
        connectionStatus: String?
    ) {
        val requestId = loveRequestId?.toLong() ?: return
        val status = connectionStatus.orEmpty()

        applyOptimisticUpdate(requestId, status)

        viewModelScope.launch {
            runCatching {
                connectionRepository.updateLoveRequest(
                    loveRequestId = loveRequestId,
                    coupleConnectionDTO = CoupleConnectionDTO(
                        senderNumber = senderNumber,
                        receiverNumber = receiverNumber,
                        connectionStatus = connectionStatus
                    )
                )
            }.onSuccess { result ->
                result.onSuccess { _, _ ->
//                    showSuccess("Request updated successfully")

                    // refresh after action

                }.onError { error, errorType ->
                    rollback(requestId, status, error.toString(), errorType.toString())
                }
            }.onFailure {
                rollback(requestId, status, it.message ?: "Unknown error", "Exception")
            }
        }
    }

    private fun applyOptimisticUpdate(requestId: Long, status: String) {
        when (status) {
            ACCEPTED, REJECTED -> {
                _findPartnerUIState.update {
                    it.copy(
                        handledRequestIds = it.handledRequestIds + requestId
                    )
                }
            }

            else -> {
                _findPartnerUIState.update {
                    it.copy(
                        handledRequestIds = it.handledRequestIds + requestId
                    )
                }
            }
        }
    }

    private fun rollback(
        requestId: Long,
        status: String,
        message: String,
        errorType: String
    ) {
        when (status) {
            ACCEPTED, REJECTED -> {
                _findPartnerUIState.update {
                    it.copy(
                        handledRequestIds = it.handledRequestIds - requestId,
                        baseUIState = BaseUIState.Error(errorType, message)
                    )
                }
            }

            else -> {
                _findPartnerUIState.update {
                    it.copy(
                        handledRequestIds = it.handledRequestIds - requestId,
                        baseUIState = BaseUIState.Error(errorType, message)
                    )
                }
            }
        }
    }

    private fun createPagingFlow(
        fetch: suspend (page: Int) -> ResultHandler<CoupleConnectionRequestResponse?>
    ): Flow<PagingData<CoupleConnectionRequestResponse.Result>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    var items: List<CoupleConnectionRequestResponse.Result?> = emptyList()

                    fetch(page)
                        .onSuccess { response, _ ->
                            AppLogger.log("API Response: $response")
                            items = response?.results ?: emptyList()
                        }
                        .onError { error, errorType ->
                            throw Exception("API Error: $error, Type: $errorType")
                        }

                    items
                }
            }
        ).flow
    }

    fun sendFindYourPartnerRequest() {
        viewModelScope.launch {
            showLoading()

            val phoneNumber =
                "${_findPartnerUIState.value.countryPrefix}${_findPartnerUIState.value.phoneNumber}"

            connectionRepository.sendFindYourPartnerRequest(partnerNumber = phoneNumber)
                .onSuccess { response, _ ->
                    idleScreen()
                    updatePartnerResponse(partnerResponse = response)
                    updateShowPartnerProfile(showPartnerProfile = true)
                }
                .onError { errorMessage, errorType ->
                    showErrorMessage(
                        errorType = errorType.name,
                        message = errorMessage.toString()
                    )
                }

        }
    }

    fun sendCoupleConnectionRequest() {
        viewModelScope.launch {
            showLoading()

            val receiverNumber = _findPartnerUIState.value.partnerResponse?.phoneNumber
            val senderPhoneNumber = _findPartnerUIState.value.senderPhoneNumber


            connectionRepository.sendCoupleConnectionRequest(
                senderNumber = senderPhoneNumber,
                receiverNumber = receiverNumber
            )
                .onSuccess { _, message ->
                    AppLogger.log("MESSAGE = $message")
                    showSuccessMessage(
                        message = message ?: "Love request sent successfully"
                    )
                }
                .onError { errorMessage, errorType ->
                    showErrorMessage(
                        errorType = errorType.name,
                        message = errorMessage.toString()
                    )
                }

        }
    }

    fun getUserProfile(userProfile: CoupleConnectionRequestResponse.Result.Receiver?): String? {
        return runCatching {
            Json.encodeToString(userProfile?.toUser())
        }.getOrNull()
    }

}
