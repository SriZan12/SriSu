package com.srisu.srisu.features.profile.state

import androidx.compose.runtime.Stable
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse

@Stable
data class ProfileUIState(
    val userProfileData: UserSuggestionResponse.Result? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle
)
