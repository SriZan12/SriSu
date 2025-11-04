package com.srisu.srisu.features.chat.findpartner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FindPartnerViewModel : ViewModel() {

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
    }

    private fun showSuccessMessage(message: String) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(baseUIState = BaseUIState.Success(message))
    }

    private fun showLoading() {
        this._findPartnerUIState.value = this._findPartnerUIState.value.copy(baseUIState = BaseUIState.Loading)
    }

    fun idleScreen() {
        this._findPartnerUIState.value = this._findPartnerUIState.value.copy(baseUIState = BaseUIState.Idle)
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(baseUIState = BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

//    private fun isInternetAvailable(): Boolean {
//        return connectivityObserver.isConnected.value
//    }

    fun updatePhoneNumber(phoneNumber: String) {
        if (phoneNumber.length <= 10) {
            this._findPartnerUIState.value =
                this._findPartnerUIState.value.copy(phoneNumber = phoneNumber)
        }
    }

    fun updateCountry(code: String, prefix: String) {
        this._findPartnerUIState.value =
            this._findPartnerUIState.value.copy(countryCode = code, countryPrefix = prefix)
    }

    fun validatePhoneNumber() {
        val phone = _findPartnerUIState.value.phoneNumber

        when {
            phone.isEmpty() -> {
                updateValidationError("Phone number cannot be empty")
            }

            phone.length != 10 -> {
                updateValidationError("Phone number must be 10 digits")
            }

            !phone.all { it.isDigit() } -> {
                updateValidationError("Phone number must contain digits only")
            }

            else -> {
                updateValidationError("")  // clear error
            }
        }
    }

    private fun updateValidationError(msg: String) {
        _findPartnerUIState.value = _findPartnerUIState.value.copy(
            validationErrorMsg = msg
        )
    }


    private fun loadAllCountries() {
        viewModelScope.launch {
            val countries = getAllCountriesFromJson() ?: emptyList()
            _findPartnerUIState.value = _findPartnerUIState.value.copy(countryList = countries)
        }
    }

}