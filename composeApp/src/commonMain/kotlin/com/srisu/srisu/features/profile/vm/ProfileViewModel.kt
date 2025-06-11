package com.srisu.srisu.features.profile.vm

import androidx.lifecycle.ViewModel
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.features.profile.state.ProfileUIState
import com.srisu.srisu.utils.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class ProfileViewModel(private val connectivityObserver: ConnectivityObserver) : ViewModel() {

    private val _profileUIState: MutableStateFlow<ProfileUIState> =
        MutableStateFlow(ProfileUIState())

    val profileUIState = _profileUIState.asStateFlow()

    fun updateUserProfileData(userProfileData: String?) {
        userProfileData?.let {
            val profileData = Json.decodeFromString<UserSuggestionResponse.Result?>(
                userProfileData
            )
            _profileUIState.value = _profileUIState.value.copy(userProfileData = profileData)
        }
    }


}