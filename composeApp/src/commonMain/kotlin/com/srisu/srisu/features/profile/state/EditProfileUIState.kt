package com.srisu.srisu.features.profile.state

import androidx.compose.runtime.Immutable
import com.srisu.srisu.baseframework.BaseUIState

@Immutable
data class EditProfileUIState(
    val fullName: String? = null,
    val userName: String? = null,
    val bio: String? = null,
    val country: String? = null,
    val city: String? = null,
    val interests: List<String>? = null,
    val photos: List<String>? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle
)