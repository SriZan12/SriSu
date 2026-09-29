package com.srisu.srisu.features.auth.domain

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.features.auth.data.local.datastore.IntroductionStep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class AccessDestination { ONBOARDING, SPACE, GUEST, PHONE, NAME, GENDER, PHOTO, MAIN }
sealed interface StartupState {
    data object Loading : StartupState
    data class Available(val destination: AccessDestination, val stamp: SessionStamp, val session: Session?) : StartupState
    data class Recovery(val message: String) : StartupState
}

/** One application-owned bootstrap; cached profile flags never open the protected graph. */
class StartupCoordinator(
    private val sessions: SessionCoordinator,
    private val repository: AuthRepository,
    private val scope: CoroutineScope,
    private val preferences: AuthDataStore,
) {
    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state = _state.asStateFlow()
    private var attempt: Job? = null
    private var attemptId = 0L
    private var pendingIntroduction: IntroductionStep? = null
    private var phoneReturn = IntroductionStep.WELCOME

    init {
        sessions.onLocalLogout = { refresh -> scope.launch { repository.revoke(refresh) } }
        scope.launch { sessions.state.collect { retry() } }
    }

    fun retry() {
        attempt?.cancel()
        val id = ++attemptId
        val stamp = sessions.stamp()
        _state.value = StartupState.Loading
        attempt = scope.launch {
            try {
                if (sessions.currentSession()?.access.isNullOrBlank()) {
                    val step = withTimeout(5_000) {
                        (pendingIntroduction?.also { preferences.saveIntroductionStep(it, phoneReturn) }
                            ?: preferences.introductionStep()).also {
                            if (it == IntroductionStep.PHONE) phoneReturn = preferences.phoneReturnStep()
                        }
                    }
                    sessions.ensureCurrent(stamp)
                    if (id != attemptId) return@launch
                    pendingIntroduction = null
                    val destination = when (step) {
                        IntroductionStep.WELCOME -> AccessDestination.ONBOARDING
                        IntroductionStep.SPACE -> AccessDestination.SPACE
                        IntroductionStep.PHONE -> AccessDestination.PHONE
                        IntroductionStep.GUEST -> AccessDestination.GUEST
                    }
                    _state.value = StartupState.Available(destination, stamp, null)
                    return@launch
                }
                // Existing accounts have already passed the introduction. This preference
                // only controls the next signed-out entry; the server still decides access.
                pendingIntroduction = null
                withTimeout(5_000) { preferences.saveIntroductionStep(IntroductionStep.PHONE, IntroductionStep.WELCOME) }
                val result = withTimeout(35_000) { repository.getProfile() }.result
                sessions.ensureCurrent(stamp)
                if (id != attemptId) return@launch
                when (result) {
                    is NetworkAPIResult.Success -> accept(requireNotNull(result.response), stamp)
                    is NetworkAPIResult.Error -> _state.value = StartupState.Recovery(result.failure.message)
                }
            } catch (_: TimeoutCancellationException) {
                if (id == attemptId) _state.value = StartupState.Recovery("Connection timed out. Please retry.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (id == attemptId) _state.value = StartupState.Recovery("Unable to open your saved progress. Please retry.")
            }
        }
    }

    fun showSpace() = introduce(IntroductionStep.SPACE)
    fun showWelcome() = introduce(IntroductionStep.WELCOME)
    fun enterGuest() = introduce(IntroductionStep.GUEST)
    fun beginAuthentication() {
        if (_state.value !is StartupState.Available) return
        phoneReturn = when ((_state.value as? StartupState.Available)?.destination) {
            AccessDestination.GUEST -> IntroductionStep.GUEST
            AccessDestination.SPACE -> IntroductionStep.SPACE
            else -> IntroductionStep.WELCOME
        }
        introduce(IntroductionStep.PHONE)
    }
    fun leaveAuthentication() = introduce(phoneReturn)

    private fun introduce(step: IntroductionStep) {
        // Repeated taps and stale guest callbacks cannot interrupt authenticated bootstrap.
        if (!sessions.currentSession()?.access.isNullOrBlank() || _state.value !is StartupState.Available) return
        pendingIntroduction = step
        retry()
    }

    fun accept(profile: ProfileResponse, stamp: SessionStamp) {
        sessions.ensureCurrent(stamp)
        val user = requireNotNull(profile.user)
        val progress = requireNotNull(profile.progress)
        require(user.id == stamp.accountId && progress.phoneVerified)
        val current = requireNotNull(sessions.currentSession())
        sessions.saveIfCurrent(setUserWholeCredentials(current.access, current.refresh, user), stamp)
        val destination = when {
            progress.profileComplete && progress.nextStep == "complete" -> AccessDestination.MAIN
            !progress.profileComplete && progress.nextStep == "name" -> AccessDestination.NAME
            // Gender is a mobile onboarding step within the existing server photo
            // phase. Keep the auth-1 wire contract and completed accounts compatible.
            !progress.profileComplete && progress.nextStep == "photo" ->
                if (user.gender in setOf("MALE", "FEMALE")) AccessDestination.PHOTO else AccessDestination.GENDER
            else -> error("Unsupported profile state")
        }
        // Existing Home supports both linked users and its unlinked Find Partner action.
        _state.value = StartupState.Available(destination, stamp, sessions.currentSession())
    }
}
