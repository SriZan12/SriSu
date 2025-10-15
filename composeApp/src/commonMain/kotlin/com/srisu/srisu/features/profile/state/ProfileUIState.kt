package com.srisu.srisu.features.profile.state

import androidx.compose.runtime.Stable
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse

@Stable
data class ProfileUIState(
    val userProfileData: User? = null,
    val isRequestSentSuccessfully: Boolean = false,
    val baseUIState: BaseUIState = BaseUIState.Idle
)