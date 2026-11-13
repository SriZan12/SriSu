package com.srisu.srisu.features.moments.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import com.srisu.srisu.features.couplechat.presentation.NativePhotoBatch
import com.srisu.srisu.features.moments.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.uuid.Uuid

data class LocalMomentPhoto(val id: String, val bytes: ByteArray? = null, val remote: StagedPhoto? = null)
data class MomentComposerState(val record: MomentDraftRecord? = null, val draft: MomentDraft? = null,
    val photos: List<LocalMomentPhoto> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val progress: Float? = null, val stage: String = "", val error: String? = null,
    val publishedId: Long? = null, val discarded: Boolean = false)

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class MomentComposerViewModel(private val source: MomentSource, private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp=sessions.stamp()
    private val json=Json { ignoreUnknownKeys=true }
    private val mutable=MutableStateFlow(MomentComposerState())
    val state=mutable.asStateFlow()
    private var couple: Long?=null
    private var work: Job?=null
    private val attemptedUploads=mutableSetOf<String>()
    private val key get()="moment-draft:${stamp.accountId}:$couple"
    init { viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
        work?.cancel(); mutable.value=MomentComposerState(loading=false)
    } } } }
    fun open(coupleId: Long) {
        if(couple!=null || sessions.stamp()!=stamp) return
        couple=coupleId
        val saved=runCatching { sessions.getSession(key)?.takeIf(String::isNotBlank)?.let { json.decodeFromString<MomentDraftRecord>(it) } }.getOrNull()
        val record=saved?.takeIf { it.couple==coupleId } ?: MomentDraftRecord(Uuid.random().toString(),coupleId)
        mutable.update { it.copy(record=record) }; persist(); restore()
    }
    private fun persist(): Boolean = try {
        sessions.ensureCurrent(stamp)
        sessions.saveSession(state.value.record?.let { json.encodeToString(it) }.orEmpty(),key); true
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(_: Exception) { mutable.update { it.copy(error="Your draft could not be saved on this device. Try again before publishing.") }; false }
    fun restore() {
        val record=state.value.record ?: return
        if(work?.isActive==true) return
        work=viewModelScope.launch {
            mutable.update { it.copy(loading=true,error=null) }
            when(val result=source.createDraft(record.id,record.couple)) {
                is NetworkAPIResult.Success -> {
                    sessions.ensureCurrent(stamp)
                    val draft=result.response
                    if(draft==null) fail("The draft could not be loaded.")
                    else if(draft.momentId!=null) complete(draft.momentId)
                    else if(draft.publishedAt!=null) fail("This Moment is no longer available. Discard this draft to start again.")
                    else {
                        val local=state.value.photos.associateBy { it.id }
                        val remote=draft.photos.associateBy { it.id }
                        val order=(record.order+draft.photos.map { it.id }).distinct()
                        val photos=order.mapNotNull { id -> remote[id]?.let { LocalMomentPhoto(id,local[id]?.bytes,it) } ?: local[id] }
                        val missing=record.order.any { it !in remote && it !in local }
                        mutable.update { it.copy(draft=draft,photos=photos,loading=false,
                            error=if(missing) "Some photos were not uploaded. Please select them again." else null) }
                    }
                }
                is NetworkAPIResult.Error -> { sessions.ensureCurrent(stamp); fail(result.failure.message) }
            }
        }
    }
    fun caption(value: String) { updateRecord { it.copy(caption=value.take(1000)) } }
    fun audience(value: String) { if(value in setOf("private","public")) updateRecord { it.copy(visibility=value) } }
    private fun updateRecord(change: (MomentDraftRecord)->MomentDraftRecord) {
        if(state.value.busy || state.value.record?.publication!=null) return
        mutable.update { it.copy(record=it.record?.let(change)) }; persist()
    }
    fun pickerStamp(): SessionStamp = stamp
    fun remainingPhotoSlots(): Int = (5-state.value.photos.size).coerceAtLeast(0)
    fun pickedPhotos(batch: NativePhotoBatch, expected: SessionStamp) {
        if(expected!=sessions.stamp() || expected!=stamp || state.value.busy || state.value.record?.publication!=null) return
        if(batch.photos.isEmpty() && batch.failedCount==0) return
        val valid=batch.photos.filter { it.isNotEmpty() && it.size<=10*1024*1024 }
        val added=valid.take(remainingPhotoSlots()).map { LocalMomentPhoto(Uuid.random().toString(),it) }
        val unreadable=batch.failedCount.coerceAtLeast(0)+batch.photos.count { it.isEmpty() }
        val oversized=batch.photos.count { it.size>10*1024*1024 }
        val errors=buildList {
            if(unreadable>0) add("$unreadable ${if(unreadable==1) "photo could" else "photos could"} not be opened. Try selecting ${if(unreadable==1) "it" else "them"} again.")
            if(oversized>0) add("$oversized ${if(oversized==1) "photo exceeds" else "photos exceed"} 10 MiB.")
            if(valid.size>added.size) add("You can add up to five photos.")
        }
        mutable.update { s ->
            val photos=s.photos+added
            s.copy(photos=photos,error=errors.takeIf { it.isNotEmpty() }?.joinToString(" "),
                record=s.record?.copy(order=photos.map { it.id }))
        }
        if(added.isNotEmpty()) persist()
    }
    fun picked(raw: String, bytes: ByteArray?, expected: SessionStamp) {
        if(expected!=sessions.stamp() || expected!=stamp || state.value.busy || state.value.record?.publication!=null) return
        try {
            val data=json.parseToJsonElement(raw).jsonObject
            when(data["event"]?.jsonPrimitive?.content) {
                "ready" -> {
                    if(data["kind"]?.jsonPrimitive?.content!="photo") {
                        fail("Choose a photo for your Moment."); return
                    }
                    if(bytes==null || bytes.isEmpty()) {
                        fail("This photo could not be opened. Try another photo."); return
                    }
                    if(bytes.size>10*1024*1024) {
                        fail("Choose a photo no larger than 10 MiB."); return
                    }
                    if(state.value.photos.size>=5) { fail("You can add up to five photos."); return }
                    val photo=LocalMomentPhoto(Uuid.random().toString(),bytes)
                    mutable.update { it.copy(photos=it.photos+photo,error=null,record=it.record?.copy(order=it.photos.map { p->p.id }+photo.id)) }; persist()
                }
                "error" -> fail(if(data["code"]?.jsonPrimitive?.content=="permission") "Camera access is off. Allow it in Settings, or choose a photo." else "This photo could not be opened. Try another photo.")
            }
        } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { fail("This photo could not be opened.") }
    }
    fun reorder(id: String, offset: Int) {
        if(state.value.busy || state.value.record?.publication!=null) return
        val photos=state.value.photos.toMutableList(); val from=photos.indexOfFirst { it.id==id }; val to=from+offset
        if(from<0 || to !in photos.indices) return
        photos.add(to,photos.removeAt(from))
        mutable.update { it.copy(photos=photos,record=it.record?.copy(order=photos.map { p->p.id })) }; persist()
    }
    fun remove(id: String) {
        if(state.value.busy || work?.isActive==true || state.value.record?.publication!=null) return
        val photo=state.value.photos.firstOrNull { it.id==id } ?: return
        fun removed(draft: MomentDraft?) {
            mutable.update { s -> val photos=s.photos.filterNot { it.id==id }; s.copy(photos=photos,draft=draft ?: s.draft,busy=false,record=s.record?.copy(order=photos.map { it.id })) }; persist()
        }
        if(photo.remote==null && id !in attemptedUploads) { removed(null); return }
        work=viewModelScope.launch {
            mutable.update { it.copy(busy=true,error=null) }
            when(val result=source.removePhoto(state.value.record!!.id,id)) {
                is NetworkAPIResult.Success -> { sessions.ensureCurrent(stamp); removed(result.response) }
                is NetworkAPIResult.Error -> { sessions.ensureCurrent(stamp); fail(result.failure.message) }
            }
        }
    }
    fun share() {
        val record=state.value.record ?: return
        if(work?.isActive==true || state.value.busy || state.value.loading || state.value.photos.isEmpty()) return
        if(!persist()) return
        work=viewModelScope.launch {
            mutable.update { it.copy(busy=true,error=null,stage="Preparing your Moment",progress=null) }
            val fetched=source.createDraft(record.id,record.couple)
            sessions.ensureCurrent(stamp)
            val current=(fetched as? NetworkAPIResult.Success)?.response
            if(current==null) { fail((fetched as? NetworkAPIResult.Error)?.failure?.message ?: "Your draft could not be loaded."); return@launch }
            if(current.momentId!=null) { complete(current.momentId); return@launch }
            var draft: MomentDraft=current
            val photos=state.value.photos.toList()
            for((index,photo) in photos.withIndex()) {
                if(draft.photos.any { it.id==photo.id }) continue
                val bytes=photo.bytes
                if(bytes==null) { fail("Please select the missing photo again."); return@launch }
                mutable.update { it.copy(stage="Uploading photo ${index+1} of ${photos.size}",progress=index.toFloat()/photos.size) }
                // A timeout may have stored the file; removal must reconcile this UUID remotely.
                attemptedUploads+=photo.id
                val result=source.upload(record.id,photo.id,bytes) { progress ->
                    if(sessions.stamp()==stamp) mutable.update { it.copy(progress=(index+progress)/photos.size) }
                }
                sessions.ensureCurrent(stamp)
                draft=(result as? NetworkAPIResult.Success)?.response ?: run {
                    fail((result as? NetworkAPIResult.Error)?.failure?.message ?: "Upload failed. Your photos are still here."); return@launch
                }
                mutable.update { s -> s.copy(draft=draft,photos=s.photos.map { p -> p.copy(remote=draft.photos.firstOrNull { it.id==p.id } ?: p.remote) }) }
            }
            val intent=record.publication ?: PublishMoment(draft.revision,record.caption,record.visibility,photos.map { it.id })
            mutable.update { it.copy(record=it.record?.copy(publication=intent),stage="Publishing your Moment",progress=null) }
            if(!persist()) { mutable.update { it.copy(busy=false) }; return@launch }
            when(val result=source.publish(record.id,intent)) {
                is NetworkAPIResult.Success -> { sessions.ensureCurrent(stamp); result.response?.let { complete(it.id) } ?: fail("Publication could not be confirmed. Retry safely.") }
                is NetworkAPIResult.Error -> { sessions.ensureCurrent(stamp); fail(result.failure.message+" Your draft is kept; retry to check publication.") }
            }
        }
    }
    fun discard() {
        if(state.value.busy || work?.isActive==true) return
        val record=state.value.record ?: return
        work=viewModelScope.launch {
            mutable.update { it.copy(busy=true) }
            val result=source.discard(record.id); sessions.ensureCurrent(stamp)
            if(result is NetworkAPIResult.Success || (result is NetworkAPIResult.Error && result.failure.status in listOf(404,410))) {
                sessions.saveSession("",key); mutable.value=MomentComposerState(loading=false,discarded=true)
            } else fail((result as? NetworkAPIResult.Error)?.failure?.message ?: "Draft could not be discarded.")
        }
    }
    private fun complete(id: Long) {
        // Publication is confirmed remotely. A failed local cleanup must not republish.
        runCatching { sessions.saveSession("",key) }; source.changed()
        mutable.value=MomentComposerState(loading=false,publishedId=id)
    }
    private fun fail(message: String) { mutable.update { it.copy(loading=false,busy=false,progress=null,error=message) } }
    suspend fun image(url: String) = source.image(url)
}
