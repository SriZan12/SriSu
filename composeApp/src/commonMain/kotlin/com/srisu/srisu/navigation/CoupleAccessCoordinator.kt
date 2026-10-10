package com.srisu.srisu.navigation

import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import com.srisu.srisu.features.auth.domain.*
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface CoupleAccess {
    data object Resolving : CoupleAccess
    data class Ready(val stamp: SessionStamp, val coupleId: Long?, val members: List<Long>, val warning: String? = null) : CoupleAccess
    data class Recovery(val message: String) : CoupleAccess
}
/** Projects existing authoritative membership; owns no credentials or domain cache. */
class CoupleAccessCoordinator(
    private val sessions: SessionCoordinator,
    private val startup: StartupCoordinator,
    private val repository: CoupleProfileRepository,
    private val lifetime: ApplicationLifetime,
) {
    private val mutable = MutableStateFlow<CoupleAccess>(CoupleAccess.Resolving)
    val state = mutable.asStateFlow()
    private val refresh = MutableStateFlow(0)
    fun retry() { refresh.value += 1 }
    init {
        lifetime.scope.launch {
            combine(startup.state, lifetime.foreground, refresh) { access, foreground, _ -> access to foreground }
                .collectLatest { (access, foreground) ->
                    val allowed = access as? StartupState.Available
                    if (allowed?.destination != AccessDestination.MAIN) {
                        mutable.value = CoupleAccess.Resolving
                        return@collectLatest
                    }
                    val stamp = allowed.stamp
                    if ((mutable.value as? CoupleAccess.Ready)?.stamp != stamp) mutable.value = CoupleAccess.Resolving
                    do {
                        val result = repository.load(null)
                        sessions.ensureCurrent(stamp)
                        when (result) {
                            is NetworkAPIResult.Success -> {
                                val profile = result.response
                                if (profile == null || profile.viewer != "member") {
                                    mutable.value = CoupleAccess.Recovery("Unable to verify current membership.")
                                    return@collectLatest
                                }
                                mutable.value = CoupleAccess.Ready(stamp, profile.id, profile.members.orEmpty().map { it.id }.sorted())
                            }
                            is NetworkAPIResult.Error -> {
                                if (result.failure.status == 404) mutable.value = CoupleAccess.Ready(stamp, null, emptyList())
                                else if (result.failure.status in listOf(401, 403)) mutable.value = CoupleAccess.Recovery("Access could not be verified. Retry to continue.")
                                else {
                                    val previous = (mutable.value as? CoupleAccess.Ready)?.takeIf { it.stamp == stamp }
                                    mutable.value = previous?.copy(warning = result.failure.message) ?: CoupleAccess.Recovery(result.failure.message)
                                }
                            }
                        }
                        if (!foreground) break
                        delay(30_000)
                    } while (true)
                }
        }
    }
}
