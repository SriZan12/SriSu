package com.srisu.srisu.features.home.profile.presentation.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO
import com.srisu.srisu.features.home.profile.domain.repository.ProfileRepository
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.profile.presentation.state.toUIModel
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.core.session.setUserWholeCredentials
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.Country.getAllCountriesFromJson
import com.srisu.srisu.utils.CountryModel
import com.srisu.srisu.utils.MediaFile
import com.srisu.srisu.utils.getMediaFileFromUri
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class EditProfileViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val sessionStorage: SessionStorage,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _editProfileUIState: MutableStateFlow<com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState> =
        MutableStateFlow(_root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState())

    val editProfileUIState = _editProfileUIState.asStateFlow()
    private val initialized = MutableStateFlow(false)
    val isInitialized = initialized.asStateFlow()
    private var initializationJob: Job? = null
    private var saveJob: Job? = null
    private var cityJob: Job? = null
    private val initialError = MutableStateFlow<String?>(null)
    val initializationError = initialError.asStateFlow()
    fun signOut() { sessionStorage.clearSession() }
    private var savedFields: List<Any?>? = null
    private fun editableFields(state: com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState) = listOf(
        state.fullName, state.userName, state.bio.orEmpty(), state.sharePartnerDetails, state.country?.name, state.city.orEmpty(),
        state.currentInterests, state.profilePictureUri, state.largePhotos, state.smallPhotos)
    fun hasUnsavedChanges() = initialized.value && savedFields != editableFields(_editProfileUIState.value)


    private val catalogue = com.srisu.srisu.features.home.profile.presentation.state.InterestCatalogueStateHolder(viewModelScope) { force -> profileRepository.loadCatalogue(force) }
    val catalogueState = catalogue.state
    fun refreshCatalogue() = catalogue.refresh(force = true)

    init {
        catalogue.refresh()
        viewModelScope.launch { catalogue.state.collect { value ->
            _editProfileUIState.update { it.copy(interestList = value.items) }
        } }
        retryInitialization()
    }

    fun retryInitialization() {
        if (initialized.value || initializationJob?.isActive == true) return
        initialError.value = null
        val coordinator = sessionStorage as? com.srisu.srisu.core.session.SessionCoordinator
        val stamp = coordinator?.stamp()
        initializationJob = viewModelScope.launch {
            try {
                val session = loadSession()
                val countries = loadCountries()
                val profile = loadProfile()
                if (stamp != null) coordinator.ensureCurrent(stamp)
                if (profile?.user == null) {
                    initialError.value = "Your profile couldn’t be loaded. Try again to edit it."
                    idleScreen()
                    return@launch
                }
                _editProfileUIState.value = buildInitialState(session, profile, countries, catalogue.state.value.items)
                savedFields = editableFields(_editProfileUIState.value)
                initialized.value = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) {
                initialError.value = "Your profile couldn’t be loaded. Try again to edit it."
                idleScreen()
            }
        }
    }


    private fun loadSession(): Session? {
        return try {
            sessionStorage.getSession(SESSION_KEY)
                ?.let { Json.decodeFromString<Session>(it) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
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
    ): com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState {

        val profileData = profile?.toUIModel()
            ?: session?.toUIModel()
            ?: _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.UserProfileUIModel()

        val largePhotos = MutableList(2) { index ->
            profileData.userPhotos?.getOrNull(index)?.let { photo ->
                _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                    id = photo.id,
                    photoUri = photo.photo?.toUri(),
                    index = index,
                    removed = photo.removed ?: false
                )
            } ?: _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                photoUri = null,
                index = index
            )
        }

        val smallPhotos = MutableList(3) { index ->
            profileData.userPhotos?.getOrNull(index + 2)?.let { photo ->
                _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                    id = photo.id,
                    photoUri = photo.photo?.toUri(),
                    index = index,
                    removed = photo.removed ?: false
                )
            } ?: _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                photoUri = null,
                index = index
            )
        }

        return _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState(
            session = session,
            profileResponse = profile,
            countryList = countries,
            interestList = interests,
            fullName = profileData.fullName.orEmpty(),
            userName = profileData.username.orEmpty(),
            bio = profileData.bio,
            sharePartnerDetails = profileData.sharePartnerDetails,
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
        if (saveJob?.isActive == true) return
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

    fun updatePartnerDetailsSharing(share: Boolean) {
        _editProfileUIState.update { it.copy(sharePartnerDetails = share) }
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

    fun updateLargePhoto(photo: com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel?) {
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


    fun updateSmallPhotos(photo: com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel?) {
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
            _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                photoUri = null,
                index = 0
            ),
            _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                photoUri = null,
                index = 1
            )
        )

        val smallPhotos = listOf(
            _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                photoUri = null,
                index = 0
            ),
            _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                photoUri = null,
                index = 1
            ),
            _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
                photoUri = null,
                index = 2
            )
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
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (e: Exception) {
            showErrorMessage("Exception", e.message)
            null
        }
    }

    fun setUserProfileData() {
        val profileData: com.srisu.srisu.features.home.profile.presentation.state.UserProfileUIModel =
            _editProfileUIState.value.profileResponse?.toUIModel()
                ?: _editProfileUIState.value.session?.toUIModel()
                ?: _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.UserProfileUIModel() // fallback empty

        updateFullName(profileData.fullName.orEmpty())
        updateUserName(profileData.username.orEmpty())
        updateBio(profileData.bio)
        _editProfileUIState.update { it.copy(sharePartnerDetails = profileData.sharePartnerDetails) }
        updateCountry(Country.getCountryModelFromName(profileData.country))
        getCityList()
        updateCity(profileData.city)
        updateCurrentInterests(profileData.userInterests)
        updateProfilePictureUri(profileData.profilePhoto?.toUri())

        resetGalleryPhotos()
        if (!profileData.userPhotos.isNullOrEmpty()) {

            // Large photos
            profileData.userPhotos.take(2).forEachIndexed { index, photo ->
                updateLargePhoto(
                    _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
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
                    _root_ide_package_.com.srisu.srisu.features.home.profile.presentation.state.GalleyPhotoModel(
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
        val requestedCountry = country ?: _editProfileUIState.value.country?.name
        cityJob?.cancel()
        cityJob = viewModelScope.launch {
            try {
                val cities = profileRepository.getCityList(country = requestedCountry)
                if (cities?.error == false && _editProfileUIState.value.country?.name?.equals(requestedCountry, ignoreCase = true) == true) {
                    setCities(cities.data)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) {
                // This optional lookup must not dismiss a save or clear a user's chosen city.
            }
        }
    }

    fun updateProfile() {
        if (!initialized.value || saveJob?.isActive == true) return
        val snapshot = _editProfileUIState.value
        if(snapshot.fullName.isNullOrBlank()) { showErrorMessage("Validation","Enter your full name."); return }
        if(snapshot.userName.isNullOrBlank()) { showErrorMessage("Validation","Enter a username."); return }
        showLoading()
        val stamp = (sessionStorage as? com.srisu.srisu.core.session.SessionCoordinator)?.stamp()
        saveJob = viewModelScope.launch {
            try {
                val dto = buildProfileUpdateDTO(snapshot)
                val profile = buildProfileMedia(snapshot)
                val gallery = buildGalleryMedia(snapshot)

                profileRepository.sendUpdateProfileRequest(
                    profileUpdateDTO = dto,
                    userId = snapshot.session?.id,
                    profilePhoto = profile,
                    gallery = gallery
                ).onSuccess { profileResponse, _ ->
                    val user = profileResponse?.user ?: error("The server returned an empty profile.")
                    val coordinator = sessionStorage as? com.srisu.srisu.core.session.SessionCoordinator
                    if (stamp != null) {
                        if (!coordinator!!.updateProfileIfCurrent(user, stamp)) throw com.srisu.srisu.core.session.SessionChangedException()
                    } else {
                        val current = loadSession()
                        check(current?.id == user.id) { "The account changed. Reopen your profile." }
                        sessionStorage.saveSession(setUserWholeCredentials(current?.access, current?.refresh, user), SESSION_KEY)
                    }
                    updateProfileResponse(profileResponse)
                    updateSession(loadSession())
                    setUserProfileData()
                    savedFields = editableFields(_editProfileUIState.value)
                    showSuccessMessage<Unit>(message = "Profile updated")
                }.onFailure { failure ->
                    showErrorMessage(message = profileUpdateError(failure), errorType = "Profile Update Error")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (exception: Exception) {
                showErrorMessage(message = if(exception is InvalidProfileImage) exception.message else "Your profile couldn’t be saved. Please try again.", errorType = "Profile Update Error")
            }
        }
    }

    private fun buildProfileUpdateDTO(snapshot: com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState) = ProfileUpdateDTO(
        fullName = snapshot.fullName?.trim(),
        username = snapshot.userName?.trim(),
        bio = snapshot.bio,
        sharePartnerDetails = snapshot.sharePartnerDetails,
        country = snapshot.country?.name,
        city = snapshot.city,
        userInterests = snapshot.currentInterests,
    )

    private class InvalidProfileImage: Exception("Choose a JPEG, PNG or WebP image under 5 MB and 20 megapixels, then try again.")
    private suspend fun selectedImage(uri: Uri?, id: Int? = null): MediaFile? {
        val path=uri?.toString()?.takeIf {it.isNotBlank()} ?: return null
        // Server images are already saved. Only user-selected local images are uploads.
        if(path.startsWith("https://",ignoreCase=true) || path.startsWith("http://",ignoreCase=true)) return null
        return com.srisu.srisu.utils.FileManager().createProfilePhotoFromPath(path)?.copy(id=id,removed=false)
            ?: throw InvalidProfileImage()
    }
    private suspend fun buildProfileMedia(snapshot: com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState) =
        selectedImage(snapshot.profilePictureUri)

    private suspend fun buildGalleryMedia(snapshot: com.srisu.srisu.features.home.profile.presentation.state.EditProfileUIState): ArrayList<MediaFile?> {
        val gallery = arrayListOf<MediaFile?>()
        (snapshot.largePhotos.orEmpty()+snapshot.smallPhotos.orEmpty()).filterNotNull().forEach {
            if(it.removed && it.id!=null) gallery.add(MediaFile(id=it.id,removed=true))
            else if(!it.removed) selectedImage(it.photoUri,it.id)?.let(gallery::add)
        }
        return gallery
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

            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (exception: Exception) {
                AppLogger.log("Exception = ${exception.message}")
            }
        }


    }
}

internal fun profileUpdateError(error: com.srisu.srisu.core.data.remote.ApiError): String = when {
    error.fields["username"]?.contains("unique") == true -> "This username is taken. Choose another."
    error.fields.containsKey("username") -> "Enter a valid username, up to 150 characters."
    error.fields.containsKey("full_name") -> "Enter your full name, up to 100 characters."
    error.fields.keys.any {it.startsWith("profile_photo") || it.startsWith("user_photos")} -> "Choose a JPEG, PNG or WebP image under 5 MB and 20 megapixels."
    error.fields.keys.any {it.startsWith("user_interests")} -> "Some interests are no longer available. Reopen Interests and choose them again."
    error.fields.containsKey("country") || error.fields.containsKey("city") -> "Check your country and city, then try again."
    error.fields.containsKey("bio") -> "Check your bio and try again."
    else -> error.message
}
