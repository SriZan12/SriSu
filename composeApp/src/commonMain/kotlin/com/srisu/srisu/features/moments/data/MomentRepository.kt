package com.srisu.srisu.features.moments.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

typealias MomentResult<T> = NetworkAPIResult<T?>

interface MomentSource {
    val changes: StateFlow<Long>
    fun changed()
    suspend fun home(): MomentResult<MomentHome>
    suspend fun sequence(couple: Long, creator: Long?, cursor: String?): MomentResult<MomentPage>
    suspend fun get(id: Long): MomentResult<Moment>
    suspend fun image(url: String): ByteArray?
    suspend fun viewed(id: Long): MomentResult<Moment>
    suspend fun react(id: Long, value: Boolean): MomentResult<Moment>
    suspend fun edit(id: Long, revision: Long, caption: String): MomentResult<Moment>
    suspend fun delete(id: Long): MomentResult<JsonObject>
    suspend fun notes(id: Long, page: Int): MomentResult<MomentNotePage>
    suspend fun noteReply(id: Long, text: String): MomentResult<MomentNoteReply>
    suspend fun note(id: Long, text: String): MomentResult<MomentNote>
    suspend fun createDraft(id: String, couple: Long): MomentResult<MomentDraft>
    suspend fun draft(id: String): MomentResult<MomentDraft>
    suspend fun upload(id: String, photo: String, bytes: ByteArray, progress: (Float) -> Unit): MomentResult<MomentDraft>
    suspend fun removePhoto(id: String, photo: String): MomentResult<MomentDraft>
    suspend fun discard(id: String): MomentResult<JsonObject>
    suspend fun publish(id: String, body: PublishMoment): MomentResult<Moment>
    suspend fun discover(section: String, cursor: String?): MomentResult<MomentDiscoveryPage>
    suspend fun fave(couple: Long, value: Boolean): MomentResult<JsonObject>
}

class MomentRepository(private val client: HttpClient, private val environment: ApiEnvironment,
    private val media: CoupleProfileRepository) : MomentSource {
    private val revision = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = revision
    override fun changed() { revision.value += 1 }
    private fun path(suffix: String) = environment.baseUrl + "api/social/" + suffix
    override suspend fun home() = client.safeRequest<MomentHome>(readRetries = 1) { url(path("couple-moments/home/")) }.result
    override suspend fun sequence(couple: Long, creator: Long?, cursor: String?) = client.safeRequest<MomentPage> {
        url(path("couple-moments/sequence/"))
        if(cursor == null) { parameter("couple",couple); parameter("mode","all"); parameter("page_size",10); creator?.let { parameter("creator",it) } }
        else parameter("cursor",cursor)
    }.result
    override suspend fun get(id: Long) = client.safeRequest<Moment>(shape = ResponseShape.RAW) {
        url(path("couple-moments/$id/")); parameter("record_view","false")
    }.result
    override suspend fun image(url: String) = media.image(url, maxBytes = 10 * 1024 * 1024)
    override suspend fun viewed(id: Long) = client.safeRequest<Moment> {
        url(path("couple-moments/$id/viewed/")); method=HttpMethod.Post; setBody(buildJsonObject {})
    }.result
    override suspend fun react(id: Long, value: Boolean) = client.safeRequest<Moment> {
        url(path("couple-moments/$id/reaction/")); method=HttpMethod.Put; setBody(buildJsonObject { put("reacted",value) })
    }.result
    override suspend fun edit(id: Long, revision: Long, caption: String) = client.safeRequest<Moment> {
        url(path("couple-moments/$id/caption/")); method=HttpMethod.Patch
        setBody(buildJsonObject { put("caption",caption); put("expected_revision",revision) })
    }.result
    override suspend fun delete(id: Long) = client.safeRequest<JsonObject>(shape=ResponseShape.EMPTY) {
        url(path("couple-moments/$id/")); method=HttpMethod.Delete
    }.result
    override suspend fun notes(id: Long, page: Int) = client.safeRequest<MomentNotePage> {
        url(path("moment-notes/")); parameter("moment",id); parameter("page",page); parameter("page_size",10)
    }.result
    override suspend fun noteReply(id: Long, text: String) = client.safeRequest<MomentNoteReply> {
        url(path("moment-notes/$id/replies/")); method=HttpMethod.Post; setBody(buildJsonObject { put("message",text) })
    }.result
    override suspend fun note(id: Long, text: String) = client.safeRequest<MomentNote> {
        url(path("couple-moments/$id/notes/")); method=HttpMethod.Post; setBody(buildJsonObject { put("message",text) })
    }.result
    override suspend fun createDraft(id: String, couple: Long) = client.safeRequest<MomentDraft> {
        url(path("moment-drafts/")); method=HttpMethod.Post; setBody(buildJsonObject { put("request_id",id); put("couple",couple) })
    }.result
    override suspend fun draft(id: String) = client.safeRequest<MomentDraft> { url(path("moment-drafts/$id/")) }.result
    override suspend fun upload(id: String, photo: String, bytes: ByteArray, progress: (Float)->Unit) = client.safeRequest<MomentDraft> {
        url(path("moment-drafts/$id/photos/$photo/")); method=HttpMethod.Put
        setBody(MultiPartFormDataContent(formData { append("photo",bytes,Headers.build {
            append(HttpHeaders.ContentDisposition,"filename=moment.jpg"); append(HttpHeaders.ContentType,"image/jpeg")
        }) }))
        onUpload { sent,total -> if(total != null && total>0) progress((sent.toFloat()/total).coerceIn(0f,1f)) }
    }.result
    override suspend fun removePhoto(id: String, photo: String) = client.safeRequest<MomentDraft> {
        url(path("moment-drafts/$id/photos/$photo/")); method=HttpMethod.Delete
    }.result
    override suspend fun discard(id: String) = client.safeRequest<JsonObject>(shape=ResponseShape.EMPTY) {
        url(path("moment-drafts/$id/")); method=HttpMethod.Delete
    }.result
    override suspend fun publish(id: String, body: PublishMoment) = client.safeRequest<Moment> {
        url(path("moment-drafts/$id/publish/")); method=HttpMethod.Post; setBody(body)
    }.result
    override suspend fun discover(section: String, cursor: String?) = client.safeRequest<MomentDiscoveryPage> {
        url(path("couple-feed/")); if(cursor == null) { parameter("section",section); parameter("page_size",10) }
        else parameter("cursor",cursor)
    }.result
    override suspend fun fave(couple: Long, value: Boolean) = media.fave(couple,value)
}

/** Never follow a server-provided next URL; use only its bounded cursor on our own API. */
fun momentCursor(next: String?): String? = next?.let { runCatching { Url(it).parameters["cursor"]?.takeIf { v -> v.length <= 4096 } }.getOrNull() }
