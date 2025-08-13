package com.srisu.srisu.features.profile.state

import androidx.compose.runtime.Stable
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.auth.InterestResponse
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.CountryModel

@Stable
data class EditProfileUIState(
    val fullName: String? = null,
    val userName: String? = null,
    val bio: String? = null,
    val country: CountryModel? = CountryModel(name = "Nepal", code = "NP", prefix = "+977"),
    val city: String? = null,
    val cities: List<String?>? = null,
    val profilePictureUri: Uri? = null,
    val currentInterests: List<User.UserInterest?>? = null,
    val interestList: List<InterestResponse.Interest?>? = null,
    val largePhotos: List<GalleyPhotoModel?>? =
        listOf(
            GalleyPhotoModel(photoUri = null, index = 0),
            GalleyPhotoModel(photoUri = null, index = 1),
        ),
    val smallPhotos: List<GalleyPhotoModel?>? =
        listOf(
            GalleyPhotoModel(photoUri = null, index = 0),
            GalleyPhotoModel(photoUri = null, index = 1),
            GalleyPhotoModel(photoUri = null, index = 2)
        ),
    val session: Session? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle
)

data class GalleyPhotoModel(
    val photoUri: Uri?,
    val index: Int
)

data class InterestCategoryUI(
    val category: String,
    val interests: List<InterestUI>
)

data class InterestUI(
    val interestName: String?,
    val interestId: Int?
)

