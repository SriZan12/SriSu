package com.srisu.srisu.features.profile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.dto.profile.ProfileUpdateDTO
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.state.EditProfileUIState
import com.srisu.srisu.features.profile.state.GalleyPhotoModel
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.MediaFile
import com.srisu.srisu.utils.getMediaFileFromUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditProfileViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _editProfileUIState: MutableStateFlow<EditProfileUIState> =
        MutableStateFlow(EditProfileUIState())

    val editProfileUIState = _editProfileUIState.asStateFlow()

    init {
    }

    private fun <T> showSuccessMessage(data: T? = null, message: String) {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(
                baseUIState = BaseUIState.Success(
                    data = data,
                    message = message
                )
            )
    }

    private fun showErrorMessage(errorType: String?, message: String?) {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        this._editProfileUIState.value =
            _editProfileUIState.value.copy(baseUIState = BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

    private fun showLoading() {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(baseUIState = BaseUIState.Loading)
    }

    fun idleScreen() {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(baseUIState = BaseUIState.Idle)
    }

    //Events
    fun updateFullName(fullName: String) {
        _editProfileUIState.value = _editProfileUIState.value.copy(fullName = fullName)
    }

    fun updateUserName(userName: String) {
        _editProfileUIState.value = _editProfileUIState.value.copy(userName = userName)
    }

    fun updateBio(bio: String?) {
        _editProfileUIState.value = _editProfileUIState.value.copy(bio = bio)
    }

    fun updateCountry(country: CountryModel?) {
        _editProfileUIState.value = _editProfileUIState.value.copy(country = country)
        getCityList(country = country?.name?.lowercase())
    }

    fun updateCity(city: String?) {
        _editProfileUIState.value = _editProfileUIState.value.copy(city = city)
    }

    fun setCities(cities: List<String?>?) {
        _editProfileUIState.value = _editProfileUIState.value.copy(cities = cities)
    }

    fun updateInterests(interests: List<String?>?) {
        _editProfileUIState.value = _editProfileUIState.value.copy(interests = interests)
    }

    fun updateLargePhoto(photo: GalleyPhotoModel?) {
        if (photo == null) return

        val currentPhotos =
            _editProfileUIState.value.largePhotos?.toMutableList() ?: mutableListOf()

        if (photo.index in currentPhotos.indices) {
            currentPhotos[photo.index] = photo
        } else {
            if (photo.index >= 0) {
                while (currentPhotos.size <= photo.index) {
                    currentPhotos.add(null)
                }
                currentPhotos[photo.index] = photo
            }
        }

        _editProfileUIState.value = _editProfileUIState.value.copy(largePhotos = currentPhotos)

    }


    fun updateSmallPhotos(photo: GalleyPhotoModel?) {
        if (photo == null) return

        val currentPhotos =
            _editProfileUIState.value.smallPhotos?.toMutableList() ?: mutableListOf()

        if (photo.index in currentPhotos.indices) {
            currentPhotos[photo.index] = photo
        } else {
            if (photo.index >= 0) {
                while (currentPhotos.size <= photo.index) {
                    currentPhotos.add(null)
                }
                currentPhotos[photo.index] = photo
            }
        }

        _editProfileUIState.value = _editProfileUIState.value.copy(smallPhotos = currentPhotos)
    }

    fun updateProfilePictureUri(uri: Uri?) {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(profilePictureUri = uri)
    }

    fun updateSession(session: Session?) {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(session = session)

        setUserProfileData()
    }

    fun setUserProfileData() {
        val session = this._editProfileUIState.value.session

        updateFullName(fullName = session?.fullName ?: "")
        updateUserName(userName = session?.username ?: "")
        updateBio(bio = session?.bio)
        updateCountry(country = Country.getCountryModelFromName(country = session?.country))
        getCityList()
        updateCity(city = session?.city)
        updateInterests(interests = null)
        updateProfilePictureUri(uri = session?.profilePhoto?.toUri())
    }

    fun getCityList(country: String? = null, showLoading: Boolean = false) {

        if (showLoading) {
            showLoading()
        }

        val countryCity = country ?: _editProfileUIState.value.session?.country

        viewModelScope.launch {
            try {
                val cities = profileRepository.getCityList(country = countryCity)
                if (cities?.error == false) {
                    setCities(cities = cities.data)
                }
                idleScreen()
            } catch (exception: Exception) {
                showErrorMessage(message = exception.message, errorType = "ERROR")
            }


        }
    }

    fun updateProfile() {
        viewModelScope.launch {
            try {
                val profileUpdateDTO = ProfileUpdateDTO(
                    fullName = _editProfileUIState.value.fullName,
                    username = _editProfileUIState.value.userName,
                    bio = _editProfileUIState.value.bio,
                    country = _editProfileUIState.value.country?.name?.lowercase(),
                    city = _editProfileUIState.value.city,
                    userInterests = _editProfileUIState.value.interests,
                    profilePhoto = _editProfileUIState.value.profilePictureUri.toString(),
                )

                val profile = getMediaFileFromUri(uri = _editProfileUIState.value.profilePictureUri)
                val gallery: ArrayList<MediaFile?> = arrayListOf()
                _editProfileUIState.value.largePhotos?.forEach {
                    gallery.add(getMediaFileFromUri(uri = it?.photoUri))
                }
                _editProfileUIState.value.smallPhotos?.forEach {
                    gallery.add(getMediaFileFromUri(uri = it?.photoUri))
                }

                profileRepository.sendUpdateProfileRequest(
                    profileUpdateDTO = profileUpdateDTO,
                    userId = _editProfileUIState.value.session?.id ?: -1,
                    profilePhoto = profile,
                    gallery = gallery
                ).onSuccess { _, _ ->
                    AppLogger.log("Profile Updated Successfully")
                    idleScreen()
                }.onError { error, errorType ->
                    AppLogger.log("Profile Update Error = $error")
                    idleScreen()
                }
            } catch (exception: Exception) {
                AppLogger.log("Profile Update Error = ${exception.message}")
                showErrorMessage(message = exception.message, errorType = "Profile Update Error")
            }
        }
    }


}