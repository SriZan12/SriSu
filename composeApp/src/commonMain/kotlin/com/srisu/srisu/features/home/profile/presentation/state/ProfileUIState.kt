package com.srisu.srisu.features.home.profile.presentation.state

import androidx.compose.runtime.Stable
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.auth.data.remote.response.User

@Stable
data class ProfileUIState(
    val userProfileData: User? = null,
    val isRequestSentSuccessfully: Boolean = false,
    val baseUIState: BaseUIState = BaseUIState.Idle
)