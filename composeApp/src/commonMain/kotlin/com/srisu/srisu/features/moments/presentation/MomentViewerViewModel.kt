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

data class MomentViewerState(val moment: Moment? = null, val photo: Int = 0, val bytes: ByteArray? = null,
    val loading: Boolean = true, val imageLoading: Boolean = false, val fresh: Boolean = false,
    val error: String? = null, val unavailable: String? = null, val busy: Boolean = false,
    val note: String = "", val noteSent: Boolean = false, val edited: Boolean = false,
    val notes: List<MomentNote>? = null, val notesLoading: Boolean = false, val notesNext: Boolean = false,
    val noteReplyId: Long? = null, val noteReplyDraft: String = "",
    val captionDraft: String? = null, val hasPrevious: Boolean = false, val hasNext: Boolean = false)

@OptIn(kotlin.time.ExperimentalTime::class)
class MomentViewerViewModel(private val source: MomentSource, private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp=sessions.stamp()
    private val mutable=MutableStateFlow(MomentViewerState())
    val state=mutable.asStateFlow()
    private var generation=0
    private var currentId: Long?=null
    private var couple: Long?=null
    private var creator: Long?=null
    private var sequence=emptyList<Long>()
    private var cursor: String?=null
    private var work: Job?=null
    private var expiry: Job?=null
    private var notePage=1
    private var active=false
    private var started=false
    private val viewed=mutableSetOf<Long>()
    private var viewRequest: Long?=null
    init { viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
        generation++; work?.cancel(); expiry?.cancel(); mutable.value=MomentViewerState(loading=false,unavailable="unavailable")
    } } } }
    fun open(momentId: Long?, coupleId: Long?, creatorId: Long?) {
        if(started) return
        started=true; currentId=momentId; couple=coupleId; creator=creatorId
        if(active) refresh()
    }
    fun active(value: Boolean) {
        if(active==value) return
        active=value
        if(value && started) refresh()
        else if(!value) { generation++; work?.cancel(); mutable.update { it.copy(bytes=null,fresh=false,imageLoading=false,notes=null,notesLoading=false,busy=false) } }
    }
    private fun current(own: Int): Boolean = generation==own && sessions.stamp()==stamp && active
    fun refresh() {
        if(!active || sessions.stamp()!=stamp) return
        work?.cancel(); val own=++generation
        work=viewModelScope.launch {
            mutable.update { it.copy(loading=true,busy=false,error=null,unavailable=null,fresh=false,bytes=null) }
            if(currentId==null && couple!=null) {
                val result=source.sequence(couple!!,creator,null)
                if(!current(own)) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) { failure(result as? NetworkAPIResult.Error); return@launch }
                sequence=page.results.map { it.id }.distinct(); cursor=momentCursor(page.next)
                currentId=page.results.firstOrNull { !it.hasOpened }?.id ?: sequence.firstOrNull()
            }
            val id=currentId
            if(id==null) { mutable.update { it.copy(loading=false,unavailable="empty") }; return@launch }
            val startedAt=TimeSource.Monotonic.markNow()
            val result=source.get(id)
            if(!current(own)) return@launch
            val moment=(result as? NetworkAPIResult.Success)?.response
            if(moment==null) { failure(result as? NetworkAPIResult.Error); return@launch }
            if(couple!=null && couple!=moment.couple) { mutable.update { it.copy(loading=false,unavailable="unavailable") }; return@launch }
            val remaining=runCatching { (Instant.parse(moment.expiresAt)-Instant.parse(moment.serverTime)).inWholeMilliseconds-startedAt.elapsedNow().inWholeMilliseconds }.getOrDefault(0)
            if(remaining<=0) { expired(); return@launch }
            val photo=if(state.value.moment?.id==id) state.value.photo.coerceAtMost((moment.photos.size-1).coerceAtLeast(0)) else 0
            mutable.update { it.copy(moment=moment,photo=photo,loading=false,fresh=true,imageLoading=true,
                hasPrevious=photo>0 || sequence.indexOf(id)>0,
                hasNext=photo<moment.photos.lastIndex || sequence.indexOf(id) in 0 until sequence.lastIndex || cursor!=null) }
            expiry?.cancel(); expiry=viewModelScope.launch { delay(remaining); if(currentId==id && sessions.stamp()==stamp) expired() }
            loadImage(moment,photo,own)
        }
    }
    private suspend fun loadImage(moment: Moment, index: Int, own: Int) {
        val photo=moment.photos.getOrNull(index)
        if(photo==null) { mutable.update { it.copy(imageLoading=false) }; return }
        val bytes=source.image(photo.image)
        if(!current(own)) return
        mutable.update { it.copy(bytes=bytes,imageLoading=false,error=if(bytes==null) "Photo unavailable. Retry to check access and reload." else null) }
    }
    private fun expired() {
        generation++; work?.cancel(); mutable.update { it.copy(bytes=null,moment=null,notes=null,loading=false,imageLoading=false,fresh=false,unavailable="expired",busy=false) }
        source.changed()
    }
    private fun failure(error: NetworkAPIResult.Error<*>?) {
        val status=error?.failure?.status
        mutable.update { it.copy(loading=false,busy=false,imageLoading=false,bytes=null,moment=null,fresh=false,
            unavailable=when(status) { 410->"expired";401,403,404->"unavailable";else->null },
            error=if(status in listOf(401,403,404,410)) null else error?.failure?.message ?: "This Moment could not be loaded.") }
    }
    fun move(direction: Int) {
        if(state.value.loading || !state.value.fresh || state.value.busy) return
        val moment=state.value.moment ?: return
        val index=state.value.photo+direction
        if(index in moment.photos.indices) {
            work?.cancel(); val own=++generation
            mutable.update { it.copy(photo=index,bytes=null,imageLoading=true,error=null,
                hasPrevious=index>0 || sequence.indexOf(moment.id)>0,
                hasNext=index<moment.photos.lastIndex || sequence.indexOf(moment.id) in 0 until sequence.lastIndex || cursor!=null) }
            work=viewModelScope.launch { loadImage(moment,index,own) }; return
        }
        val next=sequence.indexOf(moment.id)+direction
        if(next in sequence.indices) { currentId=sequence[next]; mutable.update { it.copy(photo=0,captionDraft=null,note="",noteSent=false,notes=null) }; refresh() }
        else if(direction>0 && cursor!=null && couple!=null) {
            work=viewModelScope.launch {
                val own=generation
                mutable.update { it.copy(loading=true) }
                val result=source.sequence(couple!!,creator,cursor)
                if(!current(own)) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) { failure(result as? NetworkAPIResult.Error); return@launch }
                val extra=page.results.map { it.id }.filterNot { it in sequence }
                sequence=(sequence+extra).distinct(); cursor=momentCursor(page.next)
                mutable.update { it.copy(loading=false) }
                if(extra.isNotEmpty()) { currentId=extra.first(); refresh() }
                else mutable.update { it.copy(hasNext=cursor!=null) }
            }
        }
    }
    /** Called only after pixels (or a legacy text-only Moment) are actually presented. */
    fun presented() {
        val moment=state.value.moment ?: return
        if(!active || !state.value.fresh || state.value.imageLoading || (moment.photos.isNotEmpty() && state.value.bytes==null) ||
            moment.id in viewed || viewRequest==moment.id) return
        viewRequest=moment.id
        viewModelScope.launch {
            val result=source.viewed(moment.id)
            if(sessions.stamp()!=stamp) return@launch
            viewRequest=null
            if(result is NetworkAPIResult.Success) {
                viewed+=moment.id; source.changed()
                if(state.value.moment?.id==moment.id && state.value.fresh) mutable.update { it.copy(moment=result.response ?: it.moment) }
            } else if(result is NetworkAPIResult.Error && result.failure.status in listOf(401,403,404,410) && currentId==moment.id) failure(result)
        }
    }
    fun react() {
        val moment=state.value.moment ?: return
        if(!moment.canReply || state.value.busy || !state.value.fresh) return
        action { source.react(moment.id,!moment.reacted) }
    }
    fun editCaption(value: String?) { mutable.update { it.copy(captionDraft=value?.take(1000),edited=false) } }
    fun saveCaption() {
        val moment=state.value.moment ?: return
        val caption=state.value.captionDraft ?: return
        if(!moment.canEdit || state.value.busy || !state.value.fresh) return
        action(edit=true) { source.edit(moment.id,moment.revision,caption) }
    }
    private fun action(edit: Boolean=false, call: suspend ()->MomentResult<Moment>) {
        val id=currentId; val own=generation
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch {
            val result=call()
            if(!current(own) || currentId!=id) return@launch
            when(result) {
                is NetworkAPIResult.Success -> { source.changed(); mutable.update { it.copy(busy=false,moment=result.response ?: it.moment,
                    captionDraft=if(edit) null else it.captionDraft,edited=edit) } }
                is NetworkAPIResult.Error -> if(result.failure.status in listOf(401,403,404,410)) failure(result) else mutable.update { it.copy(busy=false,error=result.failure.message) }
            }
        }
    }
    fun delete() {
        val moment=state.value.moment ?: return
        if(!moment.canDelete || state.value.busy) return
        mutable.update { it.copy(busy=true) }
        viewModelScope.launch {
            val result=source.delete(moment.id); sessions.ensureCurrent(stamp)
            if(result is NetworkAPIResult.Success || (result is NetworkAPIResult.Error && result.failure.status in listOf(404,410))) {
                expiry?.cancel(); generation++; source.changed(); mutable.value=MomentViewerState(loading=false,unavailable="deleted")
            } else mutable.update { it.copy(busy=false,error=(result as? NetworkAPIResult.Error)?.failure?.message) }
        }
    }
    fun closeNotes() { mutable.update { it.copy(notes=null,noteReplyId=null,noteReplyDraft="") } }
    fun loadNotes(more: Boolean=false) {
        val moment=state.value.moment ?: return
        if(!state.value.fresh || state.value.notesLoading) return
        val own=generation
        if(!more) notePage=1
        mutable.update { it.copy(notes=if(more) it.notes else emptyList(),notesLoading=true,error=null) }
        viewModelScope.launch {
            val result=source.notes(moment.id,notePage)
            if(!current(own)) return@launch
            val page=(result as? NetworkAPIResult.Success)?.response
            mutable.update { it.copy(notesLoading=false,notes=if(page!=null) ((if(more) it.notes.orEmpty() else emptyList())+page.results).distinctBy {it.id} else it.notes,
                notesNext=page?.next!=null,error=(result as? NetworkAPIResult.Error)?.failure?.message) }
            if(page?.next!=null) notePage++
        }
    }
    fun replyToNote(id: Long?, text: String="") { mutable.update {it.copy(noteReplyId=id,noteReplyDraft=text.take(1000))} }
    fun sendNoteReply() {
        val moment=state.value.moment ?: return
        val id=state.value.noteReplyId ?: return
        val text=state.value.noteReplyDraft.trim()
        if((!moment.canEdit && !moment.canReply) || !state.value.fresh || state.value.busy || text.isBlank()) return
        val own=generation
        mutable.update {it.copy(busy=true,error=null)}
        viewModelScope.launch {
            val result=source.noteReply(id,text)
            if(!current(own)) return@launch
            if(result is NetworkAPIResult.Success) {
                mutable.update {it.copy(busy=false,noteReplyId=null,noteReplyDraft="")}; loadNotes()
            } else mutable.update {it.copy(busy=false,error=(result as? NetworkAPIResult.Error)?.failure?.message)}
        }
    }
    fun note(value: String) { mutable.update { it.copy(note=value.take(1000),noteSent=false) } }
    fun sendNote() {
        val moment=state.value.moment ?: return
        val text=state.value.note.trim()
        if(moment.canReply || moment.canEdit || moment.visibility!="public" || text.isBlank() || state.value.busy || !state.value.fresh) return
        val own=generation
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch {
            val result=source.note(moment.id,text)
            if(!current(own)) return@launch
            if(result is NetworkAPIResult.Success) mutable.update { it.copy(busy=false,note="",noteSent=true) }
            else mutable.update { it.copy(busy=false,error=(result as? NetworkAPIResult.Error)?.failure?.message) }
        }
    }
}
