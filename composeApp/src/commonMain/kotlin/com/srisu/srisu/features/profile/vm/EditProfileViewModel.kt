package com.srisu.srisu.features.profile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.dto.profile.ProfileUpdateDTO
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.core.data.response.auth.InterestResponse
import com.srisu.srisu.core.data.response.auth.ProfileResponse
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.state.EditProfileUIState
import com.srisu.srisu.features.profile.state.GalleyPhotoModel
import com.srisu.srisu.features.profile.state.UserProfileUIModel
import com.srisu.srisu.features.profile.state.toUIModel
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.session.setUserWholeCredentials
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.MediaFile
import com.srisu.srisu.utils.getMediaFileFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        viewModelScope.launch {
            val session = loadSession()
            val countries = loadCountries()
            val interests = loadInterest()
            val profile = loadProfile()

            val mergedState = buildInitialState(
                session = session,
                profile = profile,
                countries = countries,
                interests = interests
            )

            _editProfileUIState.value = mergedState

        }
    }


    private fun loadSession(): Session? {
        return try {
            sessionStorage.getSession(SESSION_KEY)
                ?.let { Json.decodeFromString<Session>(it) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadCountries(): List<CountryModel> =
        withContext(Dispatchers.IO) {
            getAllCountriesFromJson() ?: emptyList()
        }

    private fun buildInitialState(
        session: Session?,
        profile: ProfileResponse?,
        countries: List<CountryModel>,
        interests: List<InterestResponse.Interest?>?
    ): EditProfileUIState {

        val profileData = profile?.toUIModel()
            ?: session?.toUIModel()
            ?: UserProfileUIModel()

        val largePhotos = MutableList(2) { index ->
            profileData.userPhotos?.getOrNull(index)?.let { photo ->
                GalleyPhotoModel(
                    id = photo.id,
                    photoUri = photo.photo?.toUri(),
                    index = index,
                    removed = photo.removed ?: false
                )
            } ?: GalleyPhotoModel(photoUri = null, index = index)
        }

        val smallPhotos = MutableList(3) { index ->
            profileData.userPhotos?.getOrNull(index + 2)?.let { photo ->
                GalleyPhotoModel(
                    id = photo.id,
                    photoUri = photo.photo?.toUri(),
                    index = index,
                    removed = photo.removed ?: false
                )
            } ?: GalleyPhotoModel(photoUri = null, index = index)
        }

        return EditProfileUIState(
            session = session,
            profileResponse = profile,
            countryList = countries,
            interestList = interests,
            fullName = profileData.fullName.orEmpty(),
            userName = profileData.username.orEmpty(),
            bio = profileData.bio,
            country = Country.getCountryModelFromName(profileData.country),
            city = profileData.city,
            currentInterests = profileData.userInterests,
            profilePictureUri = profileData.profilePhoto?.toUri(),
            largePhotos = largePhotos,
            smallPhotos = smallPhotos,
            baseUIState = BaseUIState.Idle
        )
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

    private fun isInternetAvailable(): Boolean {
        return connectivityObserver.isConnected.value
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

    fun updateCurrentInterests(interests: List<User.UserInterest?>?) {
        _editProfileUIState.value = _editProfileUIState.value.copy(currentInterests = interests)
    }

    fun updateInterestList(interestList: List<InterestResponse.Interest?>?) {
        _editProfileUIState.value = _editProfileUIState.value.copy(interestList = interestList)
    }

    private fun loadAllCountries() {
        viewModelScope.launch {
            val countries = getAllCountriesFromJson() ?: emptyList()
            _editProfileUIState.value = _editProfileUIState.value.copy(countryList = countries)
        }
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
                    currentPhotos.add(photo)
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

    }

    fun updateProfileResponse(profileResponse: ProfileResponse?) {
        this._editProfileUIState.value =
            this._editProfileUIState.value.copy(profileResponse = profileResponse)
    }

    fun resetGalleryPhotos() {
        val largePhotos = listOf(
            GalleyPhotoModel(photoUri = null, index = 0),
            GalleyPhotoModel(photoUri = null, index = 1)
        )

        val smallPhotos = listOf(
            GalleyPhotoModel(photoUri = null, index = 0),
            GalleyPhotoModel(photoUri = null, index = 1),
            GalleyPhotoModel(photoUri = null, index = 2)
        )

        this._editProfileUIState.value = _editProfileUIState.value.copy(
            largePhotos = largePhotos,
            smallPhotos = smallPhotos
        )
    }


    private suspend fun loadProfile(): ProfileResponse? {
        return try {
            var result: ProfileResponse? = null

            profileRepository.getProfile()
                .onSuccess { profileResponse, _ ->
                    result = profileResponse
                }
                .onError { error, errorType ->
                    showErrorMessage(errorType.name, error)
                }

            result
        } catch (e: Exception) {
            showErrorMessage("Exception", e.message)
            null
        }
    }

    fun setUserProfileData() {
        val profileData: UserProfileUIModel =
            _editProfileUIState.value.profileResponse?.toUIModel()
                ?: _editProfileUIState.value.session?.toUIModel()
                ?: UserProfileUIModel() // fallback empty

        updateFullName(profileData.fullName.orEmpty())
        updateUserName(profileData.username.orEmpty())
        updateBio(profileData.bio)
        updateCountry(Country.getCountryModelFromName(profileData.country))
        getCityList()
        updateCity(profileData.city)
        updateCurrentInterests(profileData.userInterests)
        updateProfilePictureUri(profileData.profilePhoto?.toUri())

        if (!profileData.userPhotos.isNullOrEmpty()) {

            resetGalleryPhotos()

            // Large photos
            profileData.userPhotos.take(2).forEachIndexed { index, photo ->
                updateLargePhoto(
                    GalleyPhotoModel(
                        id = photo?.id,
                        photoUri = photo?.photo?.toUri(),
                        index = index,
                        removed = photo?.removed ?: false
                    )
                )
            }

            // Small photos
            profileData.userPhotos.drop(2).forEachIndexed { index, photo ->
                updateSmallPhotos(
                    GalleyPhotoModel(
                        id = photo?.id,
                        photoUri = photo?.photo?.toUri(),
                        index = index,
                        removed = photo?.removed ?: false
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

    private suspend fun loadInterest(): List<InterestResponse.Interest?>? {
        return try {
            var result: List<InterestResponse.Interest?>? = null

            profileRepository.getInterestList()
                .onSuccess { interestResponse, _ ->
                    result = interestResponse?.interests
                }
                .onError { error, errorType ->
                    showErrorMessage(errorType.name, error)
                }

            result
        } catch (e: Exception) {
            showErrorMessage("Exception", e.message)
            null
        }
    }

    fun updateProfile() {
        showLoading()
        viewModelScope.launch {
            try {
                val dto = buildProfileUpdateDTO()
                val profile = buildProfileMedia()
                val gallery = buildGalleryMedia()

                profileRepository.sendUpdateProfileRequest(
                    profileUpdateDTO = dto,
                    userId = _editProfileUIState.value.session?.id,
                    profilePhoto = profile,
                    gallery = gallery
                ).onSuccess { profileResponse, _ ->
                    AppLogger.log("Profile Updated Successfully")
                    updateProfileResponse(profileResponse = profileResponse)
                    updateSessionWithCredentials(
                        access = _editProfileUIState.value.session?.access,
                        refresh = _editProfileUIState.value.session?.refresh,
                        userInfo = profileResponse?.user
                    )
                }.onError { error, _ ->
                    AppLogger.log("Profile Update Error = $error")
                    showErrorMessage(message = error, errorType = "Profile Update Error")
                }
            } catch (exception: Exception) {
                AppLogger.log("Exception = ${exception.message}")
                showErrorMessage(message = exception.message, errorType = "Exception")
            }
        }
    }

    private fun buildProfileUpdateDTO() = ProfileUpdateDTO(
        phoneNumber = _editProfileUIState.value.session?.phoneNumber,
        gender = _editProfileUIState.value.session?.gender,
        dob = _editProfileUIState.value.session?.dob,
        zodiacSign = _editProfileUIState.value.session?.zodiacSign,
        fullName = _editProfileUIState.value.fullName,
        username = _editProfileUIState.value.userName,
        bio = _editProfileUIState.value.bio,
        country = _editProfileUIState.value.country?.name,
        city = _editProfileUIState.value.city,
        userInterests = _editProfileUIState.value.currentInterests,
    )

    private suspend fun buildProfileMedia() =
        getMediaFileFromUri(
            uri = _editProfileUIState.value.profilePictureUri,
            id = null,
            removed = false
        )

    private suspend fun buildGalleryMedia(): ArrayList<MediaFile?> {
        val gallery = arrayListOf<MediaFile?>()
        _editProfileUIState.value.largePhotos?.forEach {
            gallery.add(getMediaFileFromUri(it?.photoUri, it?.id, it?.removed ?: false))
        }
        _editProfileUIState.value.smallPhotos?.forEach {
            gallery.add(getMediaFileFromUri(it?.photoUri, it?.id, it?.removed ?: false))
        }
        return gallery
    }


    private fun updateSessionWithCredentials(
        access: String?,
        refresh: String?,
        userInfo: ProfileResponse.User?,
    ) {
        val credentials = setUserWholeCredentials(access, refresh, userInfo)
        saveSession(credentials)
        setSession()
        setUserProfileData()
    }


    private fun saveSession(credentials: String) {
        sessionStorage.saveSession(credentials, SESSION_KEY)
    }

    fun setSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sessionData = sessionStorage.getSession(SESSION_KEY)
                var session: Session? = null
                if (sessionData != null) {
                    session = Json.decodeFromString<Session>(sessionData)
                }
                updateSession(session)

            } catch (exception: Exception) {
                AppLogger.log("Exception = ${exception.message}")
            }
        }


    }
}