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
    val playback: MomentPlayback? = null, val videoReady: Boolean = false,
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
    private var sequenceStarted=false
    private var sequenceWork: Job?=null
    private var work: Job?=null
    private var expiry: Job?=null
    private var publicAccess: Job?=null
    private var notesWork: Job?=null
    private var notesEpoch=0
    private var notePage=1
    private var active=false
    private var started=false
    private val viewed=mutableSetOf<Long>()
    private var viewRequest: Long?=null
    init { viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
        generation++; work?.cancel();sequenceWork?.cancel();publicAccess?.cancel(); expiry?.cancel();cancelNoteLoad(); mutable.value=MomentViewerState(loading=false,unavailable="unavailable")
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
        else if(!value) { generation++; work?.cancel();sequenceWork?.cancel();publicAccess?.cancel();closeNotes(); mutable.update { it.copy(bytes=null,playback=null,videoReady=false,fresh=false,imageLoading=false,busy=false) } }
    }
    private fun current(own: Int): Boolean = generation==own && sessions.stamp()==stamp && active
    fun refresh() {
        if(!active || sessions.stamp()!=stamp) return
        work?.cancel();sequenceWork?.cancel();publicAccess?.cancel(); val own=++generation
        closeNotes()
        work=viewModelScope.launch {
            mutable.update { it.copy(loading=true,busy=false,error=null,unavailable=null,fresh=false,bytes=null,playback=null,videoReady=false) }
            if(currentId==null && couple!=null) {
                val result=source.sequence(couple!!,creator,null)
                if(!current(own)) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) { failure(result as? NetworkAPIResult.Error); return@launch }
                sequence=page.results.map { it.id }.distinct(); cursor=momentCursor(page.next);sequenceStarted=true
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
                hasNext=photo<moment.photos.lastIndex || sequence.indexOf(id) in 0 until sequence.lastIndex || (id in sequence && cursor!=null)) }
            expiry?.cancel(); expiry=viewModelScope.launch { delay(remaining); if(currentId==id && sessions.stamp()==stamp) expired() }
            // Photo navigation can cancel this media job. Authorization must
            // already be running independently before that can happen.
            watchPublicAccess(moment)
            loadImage(moment,photo,own)
            if(current(own)) loadSelectedSequence(id,own)
        }
    }
    /** Public access can be withdrawn while a visitor keeps the viewer open. Check
     * every 30 seconds without recording a view or restarting unchanged playback. */
    private fun watchPublicAccess(moment:Moment) {
        publicAccess?.cancel()
        if(moment.visibility!="public" || moment.canEdit || moment.canReply) return
        publicAccess=viewModelScope.launch {
            while(isActive && active && sessions.stamp()==stamp && currentId==moment.id && state.value.fresh) {
                delay(30_000)
                val result=source.get(moment.id)
                currentCoroutineContext().ensureActive()
                if(!active || sessions.stamp()!=stamp || currentId!=moment.id) return@launch
                val fresh=(result as? NetworkAPIResult.Success)?.response?.takeIf {it.id==moment.id && it.couple==moment.couple}
                if(fresh==null) {
                    generation++;work?.cancel();sequenceWork?.cancel();expiry?.cancel()
                    mutable.update {it.copy(notes=null,notesLoading=false,noteReplyId=null,noteReplyDraft="")}
                    failure(result as? NetworkAPIResult.Error)
                    return@launch
                }
                if(runCatching {Instant.parse(fresh.expiresAt)<=Instant.parse(fresh.serverTime)}.getOrDefault(true)) {expired();return@launch}
                val prior=state.value.moment ?: return@launch
                if(prior.revision!=fresh.revision || prior.photos!=fresh.photos) {refresh();return@launch}
                mutable.update {it.copy(moment=fresh)}
                if(fresh.visibility!="public" || fresh.canEdit || fresh.canReply) return@launch
            }
        }
    }
    /** A selected discovery thumbnail stays visible while we locate its place in the
     * existing chronological sequence. Sequence metadata never records a view. */
    private fun loadSelectedSequence(id:Long,own:Int) {
        val coupleId=couple ?: return
        if(id in sequence || (sequenceStarted && cursor==null)) return
        sequenceWork=viewModelScope.launch {
            val visited=mutableSetOf<String?>()
            repeat(20) {
                val requestCursor=if(sequenceStarted) cursor else null
                if(!visited.add(requestCursor)) {
                    if(current(own)) mutable.update {it.copy(error="Couldn’t load nearby Moments. Please retry.")}
                    return@launch
                }
                val result=source.sequence(coupleId,creator,requestCursor)
                currentCoroutineContext().ensureActive();if(!current(own)) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) {
                    val error=(result as? NetworkAPIResult.Error)?.failure
                    if(error?.status in listOf(401,403,404,410)) failure(result as? NetworkAPIResult.Error)
                    else mutable.update {it.copy(error=error?.message ?: "Couldn’t load nearby Moments. Please retry.")}
                    return@launch
                }
                sequence=(sequence+page.results.filter {it.couple==coupleId && (creator==null || it.createdBy==creator)}.map {it.id}).distinct()
                sequenceStarted=true;cursor=momentCursor(page.next)
                if(id in sequence) {
                    mutable.update {state->state.copy(hasPrevious=state.photo>0 || sequence.indexOf(id)>0,
                        hasNext=state.photo<(state.moment?.photos?.lastIndex ?: -1) || sequence.indexOf(id)<sequence.lastIndex || cursor!=null)}
                    return@launch
                }
                if(cursor==null) {
                    // A direct, authorized Moment may not belong to this sequence any
                    // more. Do not silently jump to a different couple/date's content.
                    sequence=listOf(id)
                    return@launch
                }
            }
            if(current(own)) mutable.update {it.copy(error="More Moments are still loading. Retry to continue.")}
        }
    }
    private suspend fun loadImage(moment: Moment, index: Int, own: Int) {
        val photo=moment.photos.getOrNull(index)
        if(photo==null) { mutable.update { it.copy(imageLoading=false) }; return }
        coroutineScope {
            val image=async {source.image(photo.image)}
            val access=async {photo.video?.let {source.playback(it.id)}}
            val bytes=image.await();val result=access.await()
            if(!current(own)) return@coroutineScope
            val playback=(result as? NetworkAPIResult.Success)?.response
            mutable.update { it.copy(bytes=bytes,playback=playback,videoReady=false,imageLoading=false,
                error=if(photo.video!=null && playback==null) (result as? NetworkAPIResult.Error)?.failure?.message ?: "Video unavailable. Retry to reload."
                else if(bytes==null && photo.video==null) "Photo unavailable. Retry to check access and reload." else null) }
        }
    }
    private fun expired() {
        generation++; work?.cancel();sequenceWork?.cancel();publicAccess?.cancel();closeNotes(); mutable.update { it.copy(bytes=null,playback=null,videoReady=false,moment=null,loading=false,imageLoading=false,fresh=false,unavailable="expired",busy=false) }
        source.changed()
    }
    private fun failure(error: NetworkAPIResult.Error<*>?) {
        val status=error?.failure?.status
        // A denied/failed current Moment invalidates every outstanding reader,
        // including a photo started before an appreciation received a denial.
        generation++;work?.cancel();sequenceWork?.cancel();publicAccess?.cancel();expiry?.cancel()
        closeNotes()
        mutable.update { it.copy(loading=false,busy=false,imageLoading=false,bytes=null,playback=null,videoReady=false,moment=null,fresh=false,
            unavailable=when(status) { 410->"expired";401,403,404->"unavailable";else->null },
            error=if(status in listOf(401,403,404,410)) null else error?.failure?.message ?: "This Moment could not be loaded.") }
    }
    fun move(direction: Int) {
        if(state.value.loading || !state.value.fresh || state.value.busy) return
        val moment=state.value.moment ?: return
        val index=state.value.photo+direction
        if(index in moment.photos.indices) {
            work?.cancel();sequenceWork?.cancel(); val own=++generation
            mutable.update { it.copy(photo=index,bytes=null,playback=null,videoReady=false,imageLoading=true,error=null,
                hasPrevious=index>0 || sequence.indexOf(moment.id)>0,
                hasNext=index<moment.photos.lastIndex || sequence.indexOf(moment.id) in 0 until sequence.lastIndex || (moment.id in sequence && cursor!=null)) }
            work=viewModelScope.launch { loadImage(moment,index,own);if(current(own)) loadSelectedSequence(moment.id,own) }; return
        }
        val currentIndex=sequence.indexOf(moment.id)
        if(currentIndex<0) return
        val next=currentIndex+direction
        if(next in sequence.indices) selectMoment(sequence[next])
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
                if(extra.isNotEmpty()) selectMoment(extra.first())
                else mutable.update { it.copy(hasNext=cursor!=null) }
            }
        }
    }
    private fun selectMoment(id: Long) {
        currentId=id
        cancelNoteLoad()
        notePage=1
        // Drafts, replies and sent feedback belong to one Moment, including when
        // navigation crosses a sequence-page boundary.
        mutable.value=MomentViewerState()
        refresh()
    }
    /** Called only after pixels (or a legacy text-only Moment) are actually presented. */
    fun videoPresented(photoId:Long) {
        if(state.value.moment?.photos?.getOrNull(state.value.photo)?.let {it.id==photoId && it.video!=null}!=true) return
        mutable.update {it.copy(videoReady=true)};presented()
    }
    fun presented() {
        val moment=state.value.moment ?: return
        if(!active || !state.value.fresh || state.value.imageLoading || (moment.photos.getOrNull(state.value.photo)?.video?.let {!state.value.videoReady} ?: (moment.photos.isNotEmpty() && state.value.bytes==null)) ||
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
    private fun cancelNoteLoad() {
        notesEpoch++;notesWork?.cancel();notesWork=null
    }
    fun closeNotes() {
        cancelNoteLoad();notePage=1
        mutable.update { it.copy(notes=null,notesLoading=false,notesNext=false,noteReplyId=null,noteReplyDraft="") }
    }
    private fun recheckNoteAccess(error: NetworkAPIResult.Error<*>?): Boolean {
        return when(error?.failure?.status) {
            401 -> { failure(error);true }
            403,404,410 -> {
                // A deleted note or an emptied continuation page can return 404
                // while its Moment remains available. Close the private notes and
                // reauthorize the parent instead of treating the note as the Moment.
                refresh();true
            }
            else -> false
        }
    }
    fun loadNotes(more: Boolean=false) {
        val moment=state.value.moment ?: return
        if(!state.value.fresh || state.value.notesLoading || more && !state.value.notesNext) return
        val own=generation
        if(!more) notePage=1
        val pageNumber=notePage
        mutable.update { it.copy(notes=if(more) it.notes else emptyList(),notesNext=more && it.notesNext,notesLoading=true,error=null) }
        notesWork=viewModelScope.launch {
            val result=source.notes(moment.id,pageNumber)
            currentCoroutineContext().ensureActive()
            if(!current(own)) return@launch
            if(recheckNoteAccess(result as? NetworkAPIResult.Error)) return@launch
            val page=(result as? NetworkAPIResult.Success)?.response
            mutable.update { it.copy(notesLoading=false,notes=if(page!=null) ((if(more) it.notes.orEmpty() else emptyList())+page.results).distinctBy {it.id} else it.notes,
                notesNext=page?.let {it.next!=null} ?: it.notesNext,error=(result as? NetworkAPIResult.Error)?.failure?.message) }
            if(page?.next!=null) notePage=pageNumber+1
        }
    }
    fun replyToNote(id: Long?, text: String="") { mutable.update {it.copy(noteReplyId=id,noteReplyDraft=text.take(1000))} }
    fun sendNoteReply() {
        val moment=state.value.moment ?: return
        val id=state.value.noteReplyId ?: return
        val text=state.value.noteReplyDraft.trim()
        if((!moment.canEdit && !moment.canReply) || !state.value.fresh || state.value.busy || text.isBlank()) return
        val own=generation
        val ownNotes=notesEpoch
        mutable.update {it.copy(busy=true,error=null)}
        viewModelScope.launch {
            val result=source.noteReply(id,text)
            if(!current(own)) return@launch
            if(result is NetworkAPIResult.Success) {
                mutable.update {it.copy(busy=false,noteReplyId=null,noteReplyDraft="")}
                if(ownNotes==notesEpoch && state.value.notes!=null) loadNotes()
            } else if(!recheckNoteAccess(result as? NetworkAPIResult.Error)) {
                mutable.update {it.copy(busy=false,error=(result as? NetworkAPIResult.Error)?.failure?.message)}
            }
        }
    }
    suspend fun image(url: String) = source.image(url)
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
            else if(result is NetworkAPIResult.Error && result.failure.status in listOf(401,403,404,410)) failure(result)
            else mutable.update { it.copy(busy=false,error=(result as? NetworkAPIResult.Error)?.failure?.message) }
        }
    }
}
