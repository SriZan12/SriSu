package com.srisu.srisu.features.profile.state

import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse

data class ProfileUIState(
    val userProfileData: UserSuggestionResponse.Result? = null
)
