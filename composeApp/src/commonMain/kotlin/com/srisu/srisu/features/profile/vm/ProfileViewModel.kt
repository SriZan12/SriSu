package com.srisu.srisu.features.profile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.state.ProfileUIState
import com.srisu.srisu.session.SessionUtils
import com.srisu.srisu.utils.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ProfileViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _profileUIState: MutableStateFlow<ProfileUIState> =
        MutableStateFlow(ProfileUIState())

    val profileUIState = _profileUIState.asStateFlow()

    private fun <T> showSuccessMessage(data: T? = null, message: String) {
        this._profileUIState.value =
            this._profileUIState.value.copy(
                baseUIState = BaseUIState.Success(
                    data = data,
                    message = message
                )
            )
    }

    private fun showErrorMessage(errorType: String?, message: String?) {
        this._profileUIState.value =
            this._profileUIState.value.copy(
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
    }

    private fun showLoading() {
        this._profileUIState.value =
            this._profileUIState.value.copy(baseUIState = BaseUIState.Loading)
    }

    fun idleScreen() {
        this._profileUIState.value = this._profileUIState.value.copy(baseUIState = BaseUIState.Idle)
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        this._profileUIState.value =
            this._profileUIState.value.copy(baseUIState = BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

    private fun isInternetAvailable(): Boolean {
        return connectivityObserver.isConnected.value
    }

    fun updateUserProfileData(userProfileData: String?) {
        userProfileData?.let {
            val profileData = Json.decodeFromString<UserSuggestionResponse.Result?>(
                userProfileData
            )
            _profileUIState.value = _profileUIState.value.copy(userProfileData = profileData)
        }
    }

    private fun updateIsRequestSent(isRequestSent: Boolean) {
        _profileUIState.value =
            _profileUIState.value.copy(isRequestSentSuccessfully = isRequestSent)
    }

    fun sendSingleConnectionRequest() {

        showLoading()
        viewModelScope.launch {
            val myPhoneNumber = SessionUtils().getPhoneNumber()
            val receiverNumber = profileUIState.value.userProfileData?.phoneNumber

            profileRepository.sendSingleConnectionRequest(
                senderNumber = myPhoneNumber,
                receiverNumber = receiverNumber
            ).onSuccess { response, message ->
                showSuccessMessage(data = response, message ?: "Request sent successfully")
                updateIsRequestSent(isRequestSent = true)
            }.onError { error, errorType ->
                showErrorMessage(
                    errorType = errorType.name,
                    message = error
                )
            }
        }
    }


}