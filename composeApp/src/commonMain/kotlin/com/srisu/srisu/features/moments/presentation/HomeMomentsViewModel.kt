package com.srisu.srisu.features.moments.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.moments.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource
import kotlin.time.Instant

data class HomeMomentsState(val name: String = "", val home: MomentHome? = null,
    val loading: Boolean = false, val error: String? = null, val fresh: Boolean = false)

@OptIn(kotlin.time.ExperimentalTime::class)
class HomeMomentsViewModel(private val source: MomentSource, private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow(HomeMomentsState(name=sessions.currentSession()?.fullName.orEmpty()))
    val state = mutable.asStateFlow()
    private var load: Job? = null
    private var expiry: Job? = null
    private var visible = false
    private var dirty = false
    init {
        viewModelScope.launch { source.changes.drop(1).collect { dirty=true; if(visible) refresh() } }
        viewModelScope.launch { sessions.state.collect { if(it != stamp) {
            load?.cancel(); expiry?.cancel(); mutable.value=HomeMomentsState()
        } } }
    }
    fun visible(value: Boolean) { visible=value; if(value) refresh() }
    fun refresh() {
        if(sessions.stamp()!=stamp || load?.isActive==true) return
        load=viewModelScope.launch {
            do {
                dirty=false
                mutable.update { it.copy(loading=true,error=null) }
                val startedAt=TimeSource.Monotonic.markNow()
                when(val result=source.home()) {
                    is NetworkAPIResult.Success -> {
                        sessions.ensureCurrent(stamp)
                        val home=result.response
                        if(home==null) mutable.update { it.copy(loading=false,error="Home could not be loaded.",fresh=false) }
                        else {
                            mutable.value=HomeMomentsState(home.user.name,home,fresh=true)
                            expiry?.cancel()
                            val remaining=home.couple?.members?.mapNotNull { it.nextExpiry }?.mapNotNull {
                                runCatching { (Instant.parse(it)-Instant.parse(home.serverTime)).inWholeMilliseconds-startedAt.elapsedNow().inWholeMilliseconds }.getOrNull()
                            }?.minOrNull()
                            if(remaining!=null) expiry=viewModelScope.launch {
                                delay(remaining.coerceAtLeast(1))
                                mutable.update { it.copy(fresh=false) }; dirty=true
                                if(visible) refresh()
                            }
                        }
                    }
                    is NetworkAPIResult.Error -> {
                        sessions.ensureCurrent(stamp)
                        mutable.update { it.copy(loading=false,fresh=false,error=result.failure.message,
                            home=if(result.failure.status in listOf(401,403,404)) null else it.home) }
                    }
                }
            } while(dirty && visible)
        }
    }
    suspend fun image(url: String) = source.image(url)
}
