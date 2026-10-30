package com.srisu.srisu.features.home.profile.presentation.vm
import androidx.lifecycle.ViewModel
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.profile.presentation.state.ProfileUIState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
class ProfileViewModel : ViewModel() {
    private val state = MutableStateFlow(ProfileUIState())
    val profileUIState = state.asStateFlow()
    fun setUserProfileData(user: User) { state.value = ProfileUIState(userProfileData = user) }
    fun idleScreen() = Unit
}
