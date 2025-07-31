package com.srisu.srisu.features.profile.state

import androidx.compose.runtime.Stable
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.CountryModel

@Stable
data class EditProfileUIState(
    val fullName: String? = null,
    val userName: String? = null,
    val bio: String? = null,
    val country: CountryModel? = CountryModel(name = "Nepal", code = "NP", prefix = "+977"),
    val city: String? = null,
    val profilePictureUri: Uri? = null,
    val interests: List<String?>? = null,
    val photos: List<String>? = null,
    val session: Session? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle
)