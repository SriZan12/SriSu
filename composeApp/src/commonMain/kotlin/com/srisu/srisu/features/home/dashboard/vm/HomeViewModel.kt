package com.srisu.srisu.features.home.dashboard.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.challenges.data.ChallengeSource
import com.srisu.srisu.features.challenges.presentation.challengeValue
import com.srisu.srisu.features.home.dashboard.state.HomeData
import com.srisu.srisu.features.home.dashboard.state.HomeUiState
import com.srisu.srisu.features.home.dashboard.state.toUiState
import com.srisu.srisu.features.moments.data.MomentHome
import com.srisu.srisu.features.moments.data.MomentSource
import com.srisu.srisu.features.plans.data.PlanSource
import com.srisu.srisu.features.plans.data.planValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Aggregates the existing Moments, Challenges and Plans Home sources into one [HomeUiState]. */
@OptIn(kotlin.time.ExperimentalTime::class)
class HomeViewModel(
    private val moments: MomentSource,
    private val challenges: ChallengeSource,
    private val plans: PlanSource,
    private val sessions: SessionCoordinator,
    private val clock: Clock = Clock.System,
    private val zone: () -> TimeZone = TimeZone::currentSystemDefault,
) : ViewModel() {
    private val stamp = sessions.stamp()
    private val data = MutableStateFlow(HomeData(name = sessions.currentSession()?.fullName.orEmpty()))
    val state: StateFlow<HomeUiState> = data.map { it.toUiState(clock.now(), zone()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), data.value.toUiState(clock.now(), zone()))

    private var momentJob: Job? = null
    private var challengeJob: Job? = null
    private var planJob: Job? = null
    private var expiry: Job? = null
    private var poll: Job? = null
    private var visible = false
    private var momentsDirty = false

    init {
        viewModelScope.launch { moments.changes.drop(1).collect { momentsDirty = true; if (visible) loadMoments() } }
        viewModelScope.launch { challenges.changes.drop(1).collect { if (visible) loadChallenge() } }
        viewModelScope.launch { plans.changes.drop(1).collect { if (visible) loadPlan() } }
        viewModelScope.launch {
            sessions.state.collect {
                if (it != stamp) {
                    listOf(momentJob, challengeJob, planJob, expiry, poll).forEach { job -> job?.cancel() }
                    data.value = HomeData(momentsLoading = false, challengeLoading = false, planLoading = false)
                }
            }
        }
    }

    fun visible(value: Boolean) {
        visible = value
        poll?.cancel()
        if (!value) return
        refresh()
        poll = viewModelScope.launch {
            while (isActive) { delay(POLL_INTERVAL); loadChallenge(); loadPlan() }
        }
    }

    fun refresh() { loadMoments(); loadChallenge(); loadPlan() }

    suspend fun image(url: String): ByteArray? = moments.image(url)

    private fun loadMoments() {
        if (sessions.stamp() != stamp || momentJob?.isActive == true) return
        momentJob = viewModelScope.launch {
            do {
                momentsDirty = false
                data.update { it.copy(momentsLoading = true) }
                val startedAt = TimeSource.Monotonic.markNow()
                when (val result = moments.home()) {
                    is NetworkAPIResult.Success -> {
                        sessions.ensureCurrent(stamp)
                        val home = result.response
                        if (home == null) data.update { it.copy(momentsLoading = false, momentsFailed = true, momentsFresh = false) }
                        else {
                            data.update { it.copy(moments = home, momentsLoading = false, momentsFailed = false, momentsFresh = true) }
                            scheduleExpiry(home, startedAt.elapsedNow().inWholeMilliseconds)
                        }
                    }
                    is NetworkAPIResult.Error -> {
                        sessions.ensureCurrent(stamp)
                        val revoked = result.failure.status in REVOKED
                        data.update { it.copy(momentsLoading = false, momentsFailed = true, momentsFresh = false,
                            moments = if (revoked) null else it.moments) }
                    }
                }
            } while (momentsDirty && visible)
        }
    }

    /** Moments expire server-side; mark the row stale and reload when the earliest one lapses. */
    private fun scheduleExpiry(home: MomentHome, elapsed: Long) {
        expiry?.cancel()
        val remaining = home.couple?.members?.mapNotNull { it.nextExpiry }?.mapNotNull {
            runCatching { (Instant.parse(it) - Instant.parse(home.serverTime)).inWholeMilliseconds - elapsed }.getOrNull()
        }?.minOrNull() ?: return
        expiry = viewModelScope.launch {
            delay(remaining.coerceAtLeast(1))
            data.update { it.copy(momentsFresh = false) }
            momentsDirty = true
            if (visible) loadMoments()
        }
    }

    private fun loadChallenge() {
        if (sessions.stamp() != stamp || challengeJob?.isActive == true) return
        challengeJob = viewModelScope.launch {
            try {
                val home = challenges.home().challengeValue()
                sessions.ensureCurrent(stamp)
                data.update { it.copy(challenge = home, challengeLoading = false, challengeFailed = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { data.update { it.copy(challengeLoading = false, challengeFailed = true) } }
        }
    }

    private fun loadPlan() {
        if (sessions.stamp() != stamp || planJob?.isActive == true) return
        planJob = viewModelScope.launch {
            try {
                val home = plans.home().planValue()
                sessions.ensureCurrent(stamp)
                data.update { it.copy(plan = home, planLoading = false, planFailed = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { data.update { it.copy(planLoading = false, planFailed = true) } }
        }
    }

    private companion object {
        const val POLL_INTERVAL = 15_000L
        const val STOP_TIMEOUT = 5_000L
        val REVOKED = listOf(401, 403, 404)
    }
}
