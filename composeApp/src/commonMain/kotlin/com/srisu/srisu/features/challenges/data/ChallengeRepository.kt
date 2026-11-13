package com.srisu.srisu.features.challenges.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

interface ChallengeSource {
    val changes: StateFlow<Long>
    fun changed()
    suspend fun home(): NetworkAPIResult<ChallengeHome?>
    suspend fun history(before: String? = null): NetworkAPIResult<ChallengeHistory?>
    suspend fun get(id: String): NetworkAPIResult<ChallengeSession?>
    suspend fun submit(intent: ChallengeIntent): NetworkAPIResult<ChallengeSession?>
    suspend fun card(id: String, eventId: String): NetworkAPIResult<ChallengeSession?>
}
class ChallengeRepository(private val client: HttpClient, private val environment: ApiEnvironment) : ChallengeSource {
    private val revision = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = revision
    override fun changed() { revision.value += 1 }
    private fun path(value: String) = environment.baseUrl + "api/social/challenges/" + value
    override suspend fun home() = client.safeRequest<ChallengeHome> { url(path("home/")) }.result
    override suspend fun history(before: String?) = client.safeRequest<ChallengeHistory> {
        url(path("history/")); before?.let { parameter("before", it) }
    }.result
    override suspend fun get(id: String) = client.safeRequest<ChallengeSession> { url(path("$id/")) }.result
    override suspend fun submit(intent: ChallengeIntent) = client.safeRequest<ChallengeSession> {
        method = HttpMethod.Post
        url(path(intent.sessionId?.let { "$it/actions/" } ?: ""))
        setBody(buildJsonObject {
            put("request_id", intent.requestId)
            if (intent.sessionId == null) { put("couple", intent.couple); put("template_id", requireNotNull(intent.templateId)) }
            else { put("action", requireNotNull(intent.action)); put("expected_revision", requireNotNull(intent.revision)) }
        })
    }.result
    override suspend fun card(id: String, eventId: String) = client.safeRequest<ChallengeSession> {
        url(path("$id/card/")); method = HttpMethod.Put; setBody(buildJsonObject { put("event_id", eventId) })
    }.result
}
