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

    private fun showSuccessMessage(message: String) {
        this._profileUIState.value =
            this._profileUIState.value.copy(baseUIState = BaseUIState.Success(message))
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

    fun sendSingleConnectionRequest() {

        viewModelScope.launch {

            showLoading()

            val myPhoneNumber = SessionUtils().getPhoneNumber()
            val receiverNumber = profileUIState.value.userProfileData?.phoneNumber

            profileRepository.sendSingleConnectionRequest(
                senderNumber = myPhoneNumber,
                receiverNumber = receiverNumber
            ).onSuccess { response, _ ->

                AppLogger.log("SINGLE CONNECTION SUCCESS = $response")
                idleScreen()
            }.onError { error, errorType ->
                idleScreen()
                AppLogger.log("SINGLE CONNECTION ERROR = $error")
                AppLogger.log("SINGLE CONNECTION ERROR TYPE = ${errorType.name}")
            }
        }
    }


}