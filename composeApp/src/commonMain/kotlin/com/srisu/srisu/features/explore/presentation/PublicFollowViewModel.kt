package com.srisu.srisu.features.explore.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.explore.data.ExploreSource
import com.srisu.srisu.features.explore.data.PublicFollowSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** A visitor-profile scope: checking public following never loads the discovery feed. */
class PublicFollowViewModel(private val source: ExploreSource,private val sessions: SessionCoordinator):ViewModel() {
    private val stamp=sessions.stamp()
    private val mutable=MutableStateFlow(PublicFollowState())
    val state=mutable.asStateFlow()
    private var target:Long?=null
    private var foreground=false
    private var work:Job?=null
    init {viewModelScope.launch {sessions.state.collect {if(it!=stamp) {
        work?.cancel();target=null;foreground=false;mutable.value=PublicFollowState()
    }}}}
    fun enter(id:Long) {
        if(target==id) return
        work?.cancel();target=id.takeIf {it>0};mutable.value=PublicFollowState()
        if(foreground) refresh()
    }
    fun active(value:Boolean) {
        if(foreground==value) return
        foreground=value
        if(value) refresh() else {work?.cancel();mutable.value=PublicFollowState()}
    }
    fun refresh() {
        val id=target ?: return
        if(!foreground || sessions.stamp()!=stamp) return
        work?.cancel();mutable.value=PublicFollowState(loading=true)
        work=viewModelScope.launch {
            val result=source.followState(id)
            currentCoroutineContext().ensureActive();sessions.ensureCurrent(stamp)
            if(!foreground || target!=id) return@launch
            mutable.value=publicFollowResult(id,result)
        }
    }
    fun follow(desired:Boolean,shareIdentity:Boolean=false) {
        val id=target ?: return
        if(!foreground || sessions.stamp()!=stamp || work?.isActive==true) return
        if(desired && !shareIdentity) {
            mutable.update {it.copy(error="Confirm sharing your identity before following.")};return
        }
        mutable.update {it.copy(saving=true,error=null)}
        work=viewModelScope.launch {
            val result=changePublicFollow(source,id,desired,shareIdentity) {sessions.ensureCurrent(stamp)}
            if(foreground && target==id) mutable.value=result
        }
    }
}

internal fun publicFollowResult(id:Long,result:NetworkAPIResult<PublicFollowSnapshot?>):PublicFollowState {
    val snapshot=(result as? NetworkAPIResult.Success)?.response?.takeIf {it.coupleId==id}
    return PublicFollowState(snapshot=snapshot,error=if(snapshot==null)
        (result as? NetworkAPIResult.Error)?.failure?.message ?: "Couldn’t check following. Please retry." else null)
}

/** One explicit-consent write path shared by the Explore menu and visitor profile. */
internal suspend fun changePublicFollow(source:ExploreSource,id:Long,desired:Boolean,shareIdentity:Boolean,
    ensureCurrent:()->Unit):PublicFollowState {
    if(desired && !shareIdentity) return PublicFollowState(error="Confirm sharing your identity before following.")
    val before=source.followState(id)
    currentCoroutineContext().ensureActive();ensureCurrent()
    val checked=publicFollowResult(id,before)
    val snapshot=checked.snapshot ?: return checked
    if(desired && !snapshot.canFollow) return PublicFollowState(snapshot=snapshot,
        error="Your public identity must be available before following this couple.")
    val result=source.setFollow(id,desired,snapshot.version,shareIdentity)
    currentCoroutineContext().ensureActive();ensureCurrent()
    val saved=(result as? NetworkAPIResult.Success)?.response?.takeIf {it.coupleId==id}
    return PublicFollowState(snapshot=saved ?: snapshot,error=if(saved==null)
        (result as? NetworkAPIResult.Error)?.failure?.message ?: "Couldn’t confirm following. Retry to check and save it." else null)
}
