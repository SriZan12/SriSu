package com.srisu.srisu.features.home.profile.presentation.state

import androidx.compose.runtime.Stable
import com.srisu.srisu.features.auth.data.remote.response.User

@Stable
data class ProfileUIState(
    val userProfileData: User? = null,
    val isLoading: Boolean = true,
    val error: com.srisu.srisu.core.data.remote.ApiError? = null,
    val detailsVisible: Boolean = false,
    val connectionState: String = "unavailable",
    val requestId: Long? = null,
)
