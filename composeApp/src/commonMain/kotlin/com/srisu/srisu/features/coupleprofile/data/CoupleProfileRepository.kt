package com.srisu.srisu.features.coupleprofile.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.utils.MediaFile
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** Uses the shared client/session scope. Private profile snapshots are owned by
 * the route's ViewModel; no disk cache or global profile singleton is introduced. */
class CoupleProfileRepository(val client: HttpClient, val environment: ApiEnvironment, private val sessions: SessionCoordinator) {
    private fun path(id: Long?) = "api/social/profiles/${id ?: "me"}/"
    suspend fun discovery(cursor: String? = null) = client.safeRequest<DiscoveryPage> {
        url(environment.baseUrl + "api/social/couple-feed/"); parameter("section","global"); parameter("page_size",20); cursor?.let { parameter("cursor",it) }
    }.result
    suspend fun load(id: Long?) = client.safeRequest<CoupleProfile>(readRetries = 1) { url(environment.baseUrl + path(id)); method = HttpMethod.Get }.result
    suspend fun save(id: Long, section: String, body: JsonObject) = client.safeRequest<CoupleProfile> {
        url(environment.baseUrl + path(id) + "sections/$section/"); method = HttpMethod.Patch; setBody(body)
    }.result
    suspend fun cover(id: Long, revision: String, focalY: Float, photo: MediaFile?, source: Long?, remove: Boolean = false) = client.safeRequest<CoupleProfile> {
        url(environment.baseUrl + path(id) + "cover/"); method = HttpMethod.Patch
        if (photo?.fileBytes != null) setBody(MultiPartFormDataContent(formData {
            append("expected_revision", revision); append("focal_y", focalY.toString())
            append("photo", photo.fileBytes, Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=cover.jpg")
                append(HttpHeaders.ContentType, photo.mimeType ?: "image/jpeg")
            })
        })) else setBody(buildJsonObject {
            put("expected_revision", revision); put("focal_y", focalY); put("remove", remove)
            source?.let { put("moment_photo_id", it) }
        })
    }.result
    suspend fun history(id: Long, before: Long? = null) = client.safeRequest<HistoryPage> { url(environment.baseUrl + path(id) + "history/"); before?.let { parameter("before", it) } }.result
    suspend fun covers(id: Long, before: Long? = null) = client.safeRequest<CoverPage> { url(environment.baseUrl + path(id) + "cover-choices/"); before?.let { parameter("before", it) } }.result
    suspend fun plans(id: Long, past: Boolean, before: Long? = null) = client.safeRequest<PlansPage> {
        url(environment.baseUrl + path(id) + "plans/"); parameter("mode", if (past) "past" else "upcoming"); before?.let { parameter("before", it) }
    }.result
    suspend fun plan(id: Long, planId: Long) = client.safeRequest<CouplePlan> { url(environment.baseUrl + path(id) + "plans/$planId/") }.result
    suspend fun invite(id: Long, prompt: String, requestId: String) = client.safeRequest<ProfileInviteResult> {
        url(environment.baseUrl + path(id) + "story-invites/"); method = HttpMethod.Post
        setBody(buildJsonObject { put("prompt",prompt); put("request_id",requestId) })
    }.result
    suspend fun createPlan(id: Long, body: JsonObject) = client.safeRequest<CouplePlan> { url(environment.baseUrl + path(id) + "plans/"); method = HttpMethod.Post; setBody(body) }.result
    suspend fun respond(id: Long, plan: Long, body: JsonObject) = client.safeRequest<CouplePlan> { url(environment.baseUrl + path(id) + "plans/$plan/"); method = HttpMethod.Patch; setBody(body) }.result
    suspend fun fave(id: Long, value: Boolean) = client.safeRequest<JsonObject> { url(environment.baseUrl + "api/social/couple-faves/$id/"); method = if (value) HttpMethod.Put else HttpMethod.Delete }.result

    /** Only first-party guarded media, bounded bytes, current session, no disk cache.
     * Rendering is byte-based so a generic image loader never receives credentials. */
    suspend fun image(url: String): ByteArray? {
        val stamp = sessions.stamp()
        return try {
            val target = Url(url)
            val base = Url(environment.baseUrl)
            if (target.protocol != base.protocol || target.host != base.host || target.port != base.port || !target.encodedPath.startsWith("/api/social/")) return null
            val response = client.get(url)
            if (response.status != HttpStatusCode.OK) return null
            val channel = response.bodyAsChannel()
            val chunks = mutableListOf<ByteArray>()
            var size = 0
            while (!channel.isClosedForRead) {
                val buffer = ByteArray(8192)
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                size += count
                if (size > 5 * 1024 * 1024) { channel.cancel(); return null }
                if (count > 0) chunks += buffer.copyOf(count)
            }
            sessions.ensureCurrent(stamp)
            ByteArray(size).also { result -> var offset = 0; chunks.forEach { it.copyInto(result, offset); offset += it.size } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
}
