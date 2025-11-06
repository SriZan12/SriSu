package com.srisu.srisu.features.chat.findpartner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.apiservice.connection.ConnectionApiService
import com.srisu.srisu.core.data.repository.chat.ChatRepository
import com.srisu.srisu.core.data.repository.connection.ConnectionRepository
import com.srisu.srisu.core.data.response.chat.FindYourPartnerResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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

    init {
        loadAllCountries()
        setSenderPhoneNumber()
    }

    private fun showSuccessMessage(message: String) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(baseUIState = BaseUIState.Success(data = null, message = message))
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
        val sessionData = sessionStorage.getSession(sessionKey = SESSION_KEY)
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
                return true
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
            val countries = getAllCountriesFromJson() ?: emptyList()
            _findPartnerUIState.value = _findPartnerUIState.value.copy(countryList = countries)
        }
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
                    AppLogger.log("MESSAGE = ${message}")
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

}