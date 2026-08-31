package com.srisu.srisu.features.home.couple.presentation.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.home.couple.data.remote.dto.CoupleProfileWriteRequest
import com.srisu.srisu.features.home.couple.data.remote.mapper.toUiState
import com.srisu.srisu.features.home.couple.data.remote.response.CoupleProfileData
import com.srisu.srisu.features.home.couple.domain.repository.CoupleProfileRepository
import com.srisu.srisu.features.home.couple.presentation.state.CoupleProfileScreenState
import com.srisu.srisu.features.home.couple.presentation.state.CoupleProfileUiState
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.FileManager
import com.srisu.srisu.utils.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CoupleProfileViewModel(
    private val repository: CoupleProfileRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow(CoupleProfileScreenState())
    val state = _state.asStateFlow()

    private var hasLoaded = false

    fun loadProfile(forceRefresh: Boolean = false) {
        if (hasLoaded && !forceRefresh) return
        if (!connectivityObserver.isConnected.value) {
            showError("Offline", "Check your internet connection and try again.")
            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                isMissing = false,
                errorTitle = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            repository.getProfile()
                .onSuccess { data, _ ->
                    hasLoaded = true
                    applyProfile(data, isSaving = false)
                }
                .onError { error, errorType ->
                    hasLoaded = false
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isMissing = errorType == NetworkAPIResult.ErrorType.NOT_FOUND,
                            errorTitle = if (errorType == NetworkAPIResult.ErrorType.NOT_FOUND) null else errorType.name,
                            errorMessage = if (errorType == NetworkAPIResult.ErrorType.NOT_FOUND) null else error,
                        )
                    }
                }
        }
    }

    fun saveProfile(
        profile: CoupleProfileUiState,
        coverPhotoPath: String?,
        onSuccess: () -> Unit,
    ) {
        if (!connectivityObserver.isConnected.value) {
            showError("Offline", "Check your internet connection and try again.")
            return
        }

        val request = profile.toWriteRequest()
        if (!profile.profileComplete && request.partnerId == null) {
            showError("Partner unavailable", "An accepted partner is required to create the profile.")
            return
        }

        _state.update {
            it.copy(
                isSaving = true,
                errorTitle = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            val mediaFile = coverPhotoPath?.let { path ->
                FileManager().createMediaFileFromPath(
                    path = path,
                    id = null,
                    removed = false,
                )
            }

            val saveResult = if (profile.profileComplete) {
                repository.updateProfile(request.copy(partnerId = null))
            } else {
                repository.createProfile(request)
            }

            saveResult
                .onSuccess { data, _ ->
                    val savedProfile = data?.coupleProfile?.toUiState()
                    if (savedProfile == null) {
                        showError("Invalid response", "The server did not return the couple profile.")
                        return@onSuccess
                    }

                    hasLoaded = true
                    if (mediaFile?.fileBytes != null) {
                        uploadCoverPhoto(
                            mediaFile = mediaFile,
                            fallbackProfile = savedProfile,
                            onSuccess = onSuccess,
                        )
                    } else {
                        _state.value = CoupleProfileScreenState(profile = savedProfile)
                        onSuccess()
                    }
                }
                .onError { error, errorType ->
                    showError(errorType.name, error ?: "Unable to save the couple profile.")
                }
        }
    }

    private suspend fun uploadCoverPhoto(
        mediaFile: MediaFile,
        fallbackProfile: CoupleProfileUiState,
        onSuccess: () -> Unit,
    ) {
        repository.uploadCoverPhoto(mediaFile)
            .onSuccess { data, _ ->
                val updatedProfile = data?.coupleProfile?.toUiState() ?: fallbackProfile
                _state.value = CoupleProfileScreenState(profile = updatedProfile)
                onSuccess()
            }
            .onError { error, errorType ->
                _state.update {
                    it.copy(
                        profile = fallbackProfile,
                        isSaving = false,
                        errorTitle = errorType.name,
                        errorMessage = error ?: "Profile saved, but the cover photo could not be uploaded.",
                    )
                }
            }
    }

    fun clearError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }

    private fun applyProfile(data: CoupleProfileData?, isSaving: Boolean) {
        val profile = data?.coupleProfile?.toUiState()
        if (profile == null) {
            showError("Invalid response", "The server did not return the couple profile.")
            return
        }

        _state.value = CoupleProfileScreenState(
            profile = profile,
            isSaving = isSaving,
        )
    }

    private fun showError(title: String?, message: String?) {
        _state.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                errorTitle = title,
                errorMessage = message,
            )
        }
    }
}

private fun CoupleProfileUiState.toWriteRequest() = CoupleProfileWriteRequest(
    partnerId = partnerId,
    title = coupleTitle.trim(),
    anniversaryDate = anniversaryDate?.trim()?.takeIf(String::isNotEmpty),
    sharedDreams = sharedDreams,
    sharedInterests = sharedInterests,
    relationshipTagline = tagline.trim(),
    journeyStory = journeyStory.trim(),
    relationshipStrength = relationshipStrength.coerceIn(0, 100),
)
