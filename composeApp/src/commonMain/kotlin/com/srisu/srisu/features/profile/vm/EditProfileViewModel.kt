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
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.session.setUserWholeCredentials
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.FileManager
import com.srisu.srisu.utils.MediaFile
import com.srisu.srisu.utils.getMediaFileFromUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EditProfileViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val sessionStorage: SessionStorage,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _editProfileUIState: MutableStateFlow<EditProfileUIState> =
        MutableStateFlow(EditProfileUIState())

    val editProfileUIState = _editProfileUIState.asStateFlow()

    init {
        val sessionData = getSession()
        if (sessionData != null) {
            AppLogger.log("SESSION DATA = ${sessionData}")
            val session = Json.decodeFromString<Session>(sessionData)
            updateSession(session)
        }
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

        session?.userPhotos?.forEachIndexed { index, photo ->
            if (index < 2) {

                updateLargePhoto(
                    GalleyPhotoModel(
                        photoUri = photo?.photo?.toUri(),
                        index = index
                    )
                )
            } else {
                updateSmallPhotos(
                    GalleyPhotoModel(
                        photoUri = photo?.photo?.toUri(),
                        index = index
                    )
                )
            }
        }


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
        showLoading()
        viewModelScope.launch {
            try {
                val profileUpdateDTO = ProfileUpdateDTO(
                    phoneNumber = _editProfileUIState.value.session?.phoneNumber,
                    gender = _editProfileUIState.value.session?.gender,
                    dob = _editProfileUIState.value.session?.dob,
                    zodiacSign = _editProfileUIState.value.session?.zodiacSign,
                    fullName = _editProfileUIState.value.fullName,
                    username = _editProfileUIState.value.userName,
                    bio = _editProfileUIState.value.bio,
                    country = _editProfileUIState.value.country?.name,
                    city = _editProfileUIState.value.city,
                    userInterests = _editProfileUIState.value.interests,
                )

                val profile =  getMediaFileFromUri(uri = _editProfileUIState.value.profilePictureUri)
                AppLogger.log("Profile URI = ${_editProfileUIState.value.profilePictureUri}")
                val gallery: ArrayList<MediaFile?> = arrayListOf()
                _editProfileUIState.value.largePhotos?.forEach {
                    gallery.add(getMediaFileFromUri(uri = it?.photoUri))
                }
                _editProfileUIState.value.smallPhotos?.forEach {
                    gallery.add(getMediaFileFromUri(uri = it?.photoUri))
                }

                AppLogger.log("GALLERY SIZE  = ${Json.encodeToString(gallery)}")

                profileRepository.sendUpdateProfileRequest(
                    profileUpdateDTO = profileUpdateDTO,
                    userId = _editProfileUIState.value.session?.id,
                    profilePhoto = profile,
                    gallery = gallery
                ).onSuccess { profileResponse, _ ->
                    AppLogger.log("Profile Updated Successfully")
                    val credentials = setUserWholeCredentials(
                        access = _editProfileUIState.value.session?.access,
                        refresh = _editProfileUIState.value.session?.refresh,
                        userInfo = profileResponse?.user
                    )

                    saveSession(credentials = credentials)
                    showSuccessMessage(data = null, message = "Profile Updated")
                    getSession()
                }.onError { error, errorType ->
                    AppLogger.log("Profile Update Error = $error")
                    showErrorMessage(message = error, errorType = "Profile Update Error")
                }
            } catch (exception: Exception) {
                showErrorMessage(message = exception.message, errorType = "Exception")
                AppLogger.log("Exception = ${exception.message}")
            }
        }


    }

    private fun saveSession(credentials: String) {
        sessionStorage.saveSession(credentials, SESSION_KEY)
    }

    private fun getSession(): String? {
        return try {
            sessionStorage.getSession(SESSION_KEY)
        } catch (_: Exception) {
            null
        }

    }
}