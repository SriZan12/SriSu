package com.srisu.srisu.features.home.profile.presentation.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.ApiError
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.profile.presentation.state.ProfileUIState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: ConnectionRepository) : ViewModel() {
    private val state = MutableStateFlow(ProfileUIState())
    val profileUIState = state.asStateFlow()
    private var selection: Pair<Long, String?>? = null
    private var job: Job? = null

    fun loadProfile(userId: Long, accessToken: String?, refresh: Boolean = false) {
        val next = userId to accessToken
        if (selection == next && !refresh) return
        selection = next
        retry()
    }

    fun retry() {
        val (id, token) = selection ?: return
        job?.cancel()
        // Never retain another person's details, or private data after a revoked read.
        state.value = ProfileUIState(isLoading = id > 0)
        if (id <= 0) return
        job = viewModelScope.launch {
            try {
                when (val result = repository.getPartnerProfile(id, token).result) {
                    is NetworkAPIResult.Success -> {
                        ensureActive()
                        val profile = result.response?.takeIf { it.user.id == id }
                        state.value = if (profile == null) ProfileUIState(isLoading = false)
                        else ProfileUIState(
                            userProfileData = profile.user.copy(
                                bio = profile.user.bio.takeIf { profile.detailsVisible },
                                userInterests = profile.user.userInterests.takeIf { profile.detailsVisible },
                                userPhotos = null, city = null, country = null,
                            ),
                            isLoading = false, detailsVisible = profile.detailsVisible,
                            connectionState = profile.connectionState, requestId = profile.requestId,
                        )
                    }
                    is NetworkAPIResult.Error -> {
                        ensureActive()
                        state.value = ProfileUIState(isLoading = false, error = result.failure)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                state.value = ProfileUIState(isLoading = false, error = ApiError(
                    NetworkAPIResult.ErrorType.NETWORK, "network", "Couldn’t load this profile. Try again.", retryable = true))
            }
        }
    }
}
