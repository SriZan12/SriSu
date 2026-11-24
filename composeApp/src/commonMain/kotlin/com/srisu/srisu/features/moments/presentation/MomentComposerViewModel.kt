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

data class LocalMomentPhoto(val id: String, val bytes: ByteArray? = null, val remote: StagedPhoto? = null, val video: LocalMomentVideo? = null)
data class MomentComposerState(val record: MomentDraftRecord? = null, val draft: MomentDraft? = null,
    val photos: List<LocalMomentPhoto> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val progress: Float? = null, val stage: String = "", val error: String? = null,
    val publishedId: Long? = null, val discarded: Boolean = false)

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class MomentComposerViewModel(private val source: MomentSource, private val sessions: SessionCoordinator,
    private val readVideoChunk: suspend (LocalMomentVideo,Long,Int)->ByteArray = ::readMomentVideoChunk) : ViewModel() {
    private val stamp=sessions.stamp()
    private val json=Json { ignoreUnknownKeys=true }
    private val mutable=MutableStateFlow(MomentComposerState())
    val state=mutable.asStateFlow()
    private var couple: Long?=null
    private var work: Job?=null
    private val attemptedUploads=mutableSetOf<String>()
    private val key get()="moment-draft:${stamp.accountId}:$couple"
    init { viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
        work?.cancel(); state.value.photos.mapNotNull { it.video }.forEach(::removeMomentVideoFile); mutable.value=MomentComposerState(loading=false)
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
                        val photos=order.mapNotNull { id -> remote[id]?.let { LocalMomentPhoto(id,local[id]?.bytes,it,record.videos[id] ?: local[id]?.video) } ?: local[id] ?: record.videos[id]?.let { LocalMomentPhoto(id,video=it) } }
                        val missing=record.order.any { it !in remote && it !in local && it !in record.videos }
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
    fun crop(id: String, crop: MomentCrop?) {
        if (sessions.stamp()!=stamp || state.value.loading || state.value.photos.none { it.id==id && it.video==null && it.remote?.video==null } || crop?.valid==false) return
        updateRecord { record -> record.copy(crops=if(crop==null || crop.full) record.crops-id else record.crops+(id to crop)) }
    }
    private fun updateRecord(change: (MomentDraftRecord)->MomentDraftRecord) {
        if(state.value.busy || state.value.record?.publication!=null) return
        mutable.update { it.copy(record=it.record?.let(change)) }; persist()
    }
    fun pickedVideo(selection: MomentVideoSelection, expected: SessionStamp) {
        val video = selection.video
        if (expected != stamp || sessions.stamp() != stamp || state.value.busy || remainingPhotoSlots() == 0 || state.value.record?.publication != null) {
            video?.let(::removeMomentVideoFile); return
        }
        if (selection.error != null) { fail(selection.error); return }
        if (video == null) return
        if (video.durationMs !in 100..MOMENT_VIDEO_MAX_DURATION || video.size !in 1..MOMENT_VIDEO_MAX_BYTES) {
            removeMomentVideoFile(video); fail("Choose a video up to 60 seconds and 100 MiB."); return
        }
        val id = Uuid.random().toString()
        val photo = LocalMomentPhoto(id,bytes=selection.poster,video=video)
        mutable.update { it.copy(photos=it.photos+photo,error=null,
            record=it.record?.copy(order=it.record.order+id,videos=it.record.videos+(id to video))) }
        persist()
    }
    fun cancelUpload() {
        work?.cancel(); work=null
        mutable.update { it.copy(busy=false,loading=false,progress=null,stage="",error="Upload paused. Your draft is kept; tap Share to resume.") }
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
            if(valid.size>added.size) add("You can add up to five photos or videos.")
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
                    if(state.value.photos.size>=5) { fail("You can add up to five photos or videos."); return }
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
            mutable.update { s -> val photos=s.photos.filterNot { it.id==id }; s.copy(photos=photos,draft=draft ?: s.draft,busy=false,record=s.record?.copy(order=photos.map { it.id },crops=s.record.crops-id,videos=s.record.videos-id)) }; photo.video?.let(::removeMomentVideoFile); persist()
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
                if(photo.video!=null || photo.remote?.video!=null) {
                    if(!uploadVideo(record,photo,index,photos.size,awaitReady=false)) return@launch
                    val latest=source.draft(record.id)
                    sessions.ensureCurrent(stamp)
                    draft=(latest as? NetworkAPIResult.Success)?.response ?: run { fail("Your video is kept. Retry to reload the draft."); return@launch }
                    mutable.update { s -> s.copy(draft=draft,photos=s.photos.map { p -> p.copy(remote=draft.photos.firstOrNull { it.id==p.id } ?: p.remote) }) }
                    continue
                }
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
            // Queue every source first; encoding overlaps the remaining uploads.
            for ((index, photo) in photos.withIndex()) {
                if (photo.video != null || photo.remote?.video != null) {
                    if (!uploadVideo(record, photo, index, photos.size, awaitReady=true)) return@launch
                }
            }
            if (photos.any { it.video != null || it.remote?.video != null }) {
                sessions.ensureCurrent(stamp)
                draft = (source.draft(record.id) as? NetworkAPIResult.Success)?.response
                    ?: run { fail("Your videos are kept. Retry to reload the draft."); return@launch }
                sessions.ensureCurrent(stamp)
            }
            val intent=record.publication ?: PublishMoment(draft.revision,record.caption,record.visibility,photos.map { it.id },
                record.crops.filterKeys { id -> photos.any { it.id==id } })
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
                sessions.saveSession("",key); state.value.photos.mapNotNull { it.video }.forEach(::removeMomentVideoFile); mutable.value=MomentComposerState(loading=false,discarded=true)
            } else fail((result as? NetworkAPIResult.Error)?.failure?.message ?: "Draft could not be discarded.")
        }
    }
    private suspend fun uploadVideo(record: MomentDraftRecord, photo: LocalMomentPhoto, index: Int, count: Int, awaitReady: Boolean): Boolean {
        suspend fun result(call: MomentResult<MomentVideoInfo>): MomentVideoInfo? {
            sessions.ensureCurrent(stamp)
            val value=(call as? NetworkAPIResult.Success)?.response
            if(value==null) fail((call as? NetworkAPIResult.Error)?.failure?.message ?: "Video upload failed. Tap Share to resume.")
            return value
        }
        attemptedUploads+=photo.id
        var video=result(if(photo.video!=null && !awaitReady) source.beginVideo(record.id,photo.id,photo.video.size)
            else source.videoStatus(record.id,photo.id)) ?: return false
        if(video.state=="uploading") {
            val local=photo.video ?: run {fail("This video upload is incomplete. Remove it and choose the video again.");return false}
            while(video.receivedBytes<video.sourceBytes) {
                ensureActiveVideoSession()
                val chunkSize=video.chunkBytes.coerceIn(1,2*1024*1024)
                val offset=video.receivedBytes
                val bytes=try {readVideoChunk(local,offset,minOf(chunkSize.toLong(),video.sourceBytes-offset).toInt())}
                    catch(cancelled: CancellationException) {throw cancelled}
                    catch(_: Exception) {fail("This local video is no longer available. Remove it and select it again.");return false}
                mutable.update {it.copy(stage="Uploading video ${index+1} of $count",progress=(index+offset.toFloat()/video.sourceBytes)/count)}
                video=result(source.videoChunk(record.id,photo.id,offset,bytes)) ?: return false
                if(video.receivedBytes<=offset) {fail("The upload did not advance. Tap Share to retry.");return false}
            }
            video=result(source.completeVideo(record.id,photo.id)) ?: return false
        }
        if(video.state=="failed" && video.errorCode=="processing") video=result(source.retryVideo(record.id,photo.id)) ?: return false
        if (!awaitReady && video.state in setOf("queued", "processing", "ready")) return true
        var polls=0
        while(video.state in setOf("queued","processing") && polls++<240) {
            mutable.update {it.copy(stage="Preparing video ${index+1} of $count",progress=null)}
            delay(2000)
            video=result(source.videoStatus(record.id,photo.id)) ?: return false
        }
        if(video.state!="ready") {
            fail(if(video.errorCode=="invalid_video") "This video could not be processed. Choose a valid video up to 60 seconds."
                else "Your video is not ready yet. Your draft is kept; try Share again shortly.")
            return false
        }
        return true
    }
    private suspend fun ensureActiveVideoSession() {
        currentCoroutineContext().ensureActive(); sessions.ensureCurrent(stamp)
    }
    suspend fun playback(video: String) = source.playback(video)
    private fun complete(id: Long) {
        // Publication is confirmed remotely. A failed local cleanup must not republish.
        runCatching { sessions.saveSession("",key) }; state.value.photos.mapNotNull { it.video }.forEach(::removeMomentVideoFile); source.changed()
        mutable.value=MomentComposerState(loading=false,publishedId=id)
    }
    private fun fail(message: String) { mutable.update { it.copy(loading=false,busy=false,progress=null,error=message) } }
    suspend fun image(url: String) = source.image(url)
}
