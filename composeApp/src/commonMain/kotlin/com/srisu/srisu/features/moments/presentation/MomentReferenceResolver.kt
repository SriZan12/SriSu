package com.srisu.srisu.features.moments.presentation

import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.moments.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource
import kotlin.time.Instant

/** A live eligibility label, never a persistent copy of a Moment's photo or caption. */
enum class MomentReferenceState(val label: String) {
    CHECKING("Checking Moment…"), AVAILABLE("Open Moment"), EXPIRED("Moment expired"),
    UNAVAILABLE("Moment unavailable"), RETRY("Couldn't check Moment · Retry")
}

@OptIn(kotlin.time.ExperimentalTime::class)
class MomentReferenceResolver(private val source: MomentSource?, private val sessions: SessionCoordinator?, private val scope: CoroutineScope) {
    private data class Entry(val state: MutableStateFlow<MomentReferenceState> = MutableStateFlow(MomentReferenceState.CHECKING), var owners: Int = 0, var job: Job? = null)
    private val entries = mutableMapOf<MomentReference, Entry>()
    private var active = false
    private var generation = 0
    init {
        scope.launch { source?.changes?.collect { if(active) refreshVisible() } }
        scope.launch { sessions?.state?.collect { clear() } }
    }
    fun observe(reference: MomentReference): StateFlow<MomentReferenceState> = entries.getOrPut(reference) { Entry() }.state
    fun retain(reference: MomentReference) {
        val entry = entries.getOrPut(reference) { Entry() }; entry.owners++
        if(active && entry.owners==1) refresh(reference)
    }
    fun release(reference: MomentReference) {
        val entry=entries[reference] ?: return
        entry.owners--
        if(entry.owners<=0) { entry.job?.cancel(); entries.remove(reference) }
    }
    fun active(value: Boolean) {
        if(active==value) return
        active=value; generation++
        entries.values.forEach { it.job?.cancel(); it.state.value=MomentReferenceState.CHECKING }
        if(value) refreshVisible()
    }
    private fun refreshVisible() { entries.filterValues { it.owners>0 }.keys.toList().forEach(::refresh) }
    fun clear() {
        generation++
        entries.values.forEach { it.job?.cancel(); it.state.value=MomentReferenceState.UNAVAILABLE }
    }
    fun refresh(reference: MomentReference) {
        val entry=entries[reference] ?: return
        val source=source ?: return
        if(!active) return
        entry.job?.cancel(); val own=generation; val stamp=sessions?.stamp()
        entry.state.value=MomentReferenceState.CHECKING
        entry.job=scope.launch {
            val startedAt=TimeSource.Monotonic.markNow()
            val result=source.get(reference.id)
            if(!active || own!=generation || sessions?.stamp()!=stamp) return@launch
            val moment=(result as? NetworkAPIResult.Success)?.response
            val remaining=moment?.let { runCatching { (Instant.parse(it.expiresAt)-Instant.parse(it.serverTime)).inWholeMilliseconds-startedAt.elapsedNow().inWholeMilliseconds }.getOrDefault(0) } ?: 0
            entry.state.value=when {
                moment!=null && (moment.couple!=reference.couple || (!moment.canReply && !moment.canEdit))->MomentReferenceState.UNAVAILABLE
                moment!=null && remaining<=0->MomentReferenceState.EXPIRED
                moment!=null->MomentReferenceState.AVAILABLE
                result is NetworkAPIResult.Error && result.failure.status==410->MomentReferenceState.EXPIRED
                result is NetworkAPIResult.Error && result.failure.status in listOf(401,403,404)->MomentReferenceState.UNAVAILABLE
                else->MomentReferenceState.RETRY
            }
            if(entry.state.value==MomentReferenceState.AVAILABLE) {
                delay(remaining)
                if(active && own==generation && sessions?.stamp()==stamp) entry.state.value=MomentReferenceState.EXPIRED
            }
        }
    }
}
