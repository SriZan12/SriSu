package com.srisu.srisu.features.moments.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.moments.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MomentNoteState(val note: MomentNote? = null, val loading: Boolean = false,
    val unavailable: Boolean = false, val error: String? = null, val draft: String = "", val sending: Boolean = false)

class MomentNoteViewModel(private val source: MomentSource, private val sessions: SessionCoordinator): ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow(MomentNoteState())
    val state = mutable.asStateFlow()
    private var id: Long? = null
    private var active = false
    private var work: Job? = null
    init { viewModelScope.launch { sessions.state.collect { if (it != stamp) {
        work?.cancel(); mutable.value = MomentNoteState(unavailable=true)
    } } } }
    fun open(value: Long) { if (id != value) { id=value; if(active) refresh() } }
    fun active(value: Boolean) {
        active=value
        if(value) refresh() else { work?.cancel(); mutable.value=MomentNoteState(draft=state.value.draft) }
    }
    fun draft(value: String) { mutable.update { it.copy(draft=value.take(1000)) } }
    fun refresh() {
        val key=id ?: return
        if(!active || sessions.stamp()!=stamp) return
        work?.cancel()
        work=viewModelScope.launch {
            mutable.update { it.copy(loading=true, note=null, error=null, unavailable=false, sending=false) }
            apply(source.noteDetail(key))
        }
    }
    private fun apply(result: MomentResult<MomentNote>) {
        if(!active || sessions.stamp()!=stamp) return
        val note=(result as? NetworkAPIResult.Success)?.response
        val unavailable=(result as? NetworkAPIResult.Error)?.failure?.status in listOf(401,403,404,410)
        mutable.update { it.copy(note=note, loading=false, sending=false, unavailable=unavailable,
            error=if(note==null && !unavailable) "Couldn’t load this appreciation. Try again." else null) }
    }
    fun reply() {
        val note=state.value.note ?: return
        val text=state.value.draft.trim()
        if(!active || sessions.stamp()!=stamp || !note.canReply || state.value.sending || text.isEmpty()) return
        work=viewModelScope.launch {
            mutable.update { it.copy(sending=true,error=null) }
            val result=source.noteReply(note.id,text)
            if(!active || sessions.stamp()!=stamp) return@launch
            if(result is NetworkAPIResult.Success) {
                mutable.update { it.copy(draft="") }; source.changed(); apply(source.noteDetail(note.id))
            } else {
                val denied=(result as? NetworkAPIResult.Error)?.failure?.status in listOf(401,403,404,410)
                mutable.update { it.copy(sending=false,note=if(denied) null else it.note,unavailable=denied,
                    error=if(denied) null else "Couldn’t confirm your reply. Reload the latest replies before sending again.") }
            }
        }
    }
}
