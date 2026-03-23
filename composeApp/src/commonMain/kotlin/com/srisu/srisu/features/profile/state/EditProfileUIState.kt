package com.srisu.srisu.features.profile.state

import androidx.compose.runtime.Stable
import coil3.Uri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.features.auth.data.remote.response.User
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
    val profileResponse: ProfileResponse? = null,
    val countryList: List<CountryModel> = emptyList(),
    val baseUIState: BaseUIState = BaseUIState.Idle
)

@Stable
data class GalleyPhotoModel(
    val id: Int? = null,
    val photoUri: Uri?,
    val removed: Boolean = false,
    val index: Int
)

@Stable
data class InterestCategoryUI(
    val category: String,
    val interests: List<InterestUI>
)

@Stable
data class InterestUI(
    val interestName: String?,
    val id: Int? = null,
    val user: Int? = null,
    val category: Int? = null,
)

@Stable
data class UserProfileUIModel(
    val fullName: String? = null,
    val username: String? = null,
    val bio: String? = null,
    val country: String? = null,
    val city: String? = null,
    val userInterests: List<User.UserInterest?>? = null,
    val profilePhoto: String? = null,
    val userPhotos: List<User.UserPhoto?>? = emptyList()
)

fun ProfileResponse?.toUIModel() = UserProfileUIModel(
    fullName = this?.user?.fullName,
    username = this?.user?.username,
    bio = this?.user?.bio,
    country = this?.user?.country,
    city = this?.user?.city,
    userInterests = this?.user?.userInterests,
    profilePhoto = this?.user?.profilePhoto,
    userPhotos = this?.user?.userPhotos.orEmpty()
)

fun Session?.toUIModel() = UserProfileUIModel(
    fullName = this?.fullName,
    username = this?.username,
    bio = this?.bio,
    country = this?.country,
    city = this?.city,
    userInterests = this?.userInterests,
    profilePhoto = this?.profilePhoto,
    userPhotos = this?.userPhotos.orEmpty()
)
