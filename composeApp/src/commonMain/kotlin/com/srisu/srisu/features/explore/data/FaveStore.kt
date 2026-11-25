package com.srisu.srisu.features.explore.data

import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FaveStatus(val confirmed: Boolean, val version: Long = -1, val desired: Boolean? = null,
    val pending: Boolean = false, val error: String? = null)

/** One account-scoped state and ordered writer for every Explore/profile Fave action. */
class FaveStore(private val source: ExploreSource, private val sessions: SessionCoordinator, lifetime: ApplicationLifetime) {
    private val scope=lifetime.scope
    private val mutable=MutableStateFlow<Map<Long,FaveStatus>>(emptyMap())
    val state=mutable.asStateFlow()
    private val change=MutableStateFlow(0L)
    val changes=change.asStateFlow()
    private val workers=mutableMapOf<Long,Job>()
    private val writes=Mutex()
    init { scope.launch { sessions.state.drop(1).collect {
        workers.values.forEach { it.cancel() }; workers.clear(); mutable.value=emptyMap(); change.value++
    } } }
    fun accept(id: Long, value: Boolean, version: Long) {
        val prior=mutable.value[id]
        if(prior==null || (!prior.pending && prior.desired==null && version>=prior.version))
            mutable.update { it+(id to FaveStatus(value,version)) }
    }
    fun set(id: Long, desired: Boolean) {
        if(sessions.stamp().accountId==null) return
        mutable.update { values->values+(id to (values[id] ?: FaveStatus(false)).copy(desired=desired,pending=true,error=null)) }
        if(workers[id]?.isActive==true) return
        val stamp=sessions.stamp()
        workers[id]=scope.launch {
            try { writes.withLock {
                var conflicts=0
                while(isActive && sessions.stamp()==stamp) {
                    val wanted=mutable.value[id]?.desired ?: break
                    val before=source.faveState(id)
                    sessions.ensureCurrent(stamp)
                    val snapshot=(before as? NetworkAPIResult.Success)?.response
                    if(snapshot==null) { fail(id,(before as? NetworkAPIResult.Error)?.failure?.message); break }
                    val write=source.setFave(id,wanted,snapshot.version)
                    sessions.ensureCurrent(stamp)
                    if(write is NetworkAPIResult.Error && write.failure.status==409 && conflicts++<3) continue
                    if(write is NetworkAPIResult.Error) {
                        if(mutable.value[id]?.desired!=wanted) continue
                        fail(id,write.failure.message); break
                    }
                    val check=source.faveState(id)
                    sessions.ensureCurrent(stamp)
                    val confirmed=(check as? NetworkAPIResult.Success)?.response
                    if(confirmed==null) { fail(id,"Couldn’t confirm your change. Retry to check and save it."); break }
                    mutable.update { values -> val current=values[id] ?: return@update values
                        val done=current.desired==wanted && confirmed.isFaved==wanted
                        values+(id to current.copy(confirmed=confirmed.isFaved,version=confirmed.version,
                            desired=if(done) null else current.desired,pending=!done,error=null)) }
                    change.value++
                    if(confirmed.isFaved!=wanted && conflicts++>=3) { fail(id,"Faves changed on another device. Please retry."); break }
                }
            } } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(sessions.stamp()==stamp) fail(id,"Couldn’t save your Fave. Please retry.") }
        }
    }
    fun retry(id: Long) { mutable.value[id]?.desired?.let { set(id,it) } }
    private fun fail(id: Long,message: String?) { mutable.update { values->
        val current=values[id] ?: return@update values
        values+(id to current.copy(pending=false,error=message ?: "Couldn’t save your Fave. Please retry.")) }
    }
}
