package com.srisu.srisu.features.sparks.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

interface SparkSource {
    val changes: StateFlow<Long>
    fun changed()
    suspend fun home(): NetworkAPIResult<SparkHome?>
    suspend fun history(after: String? = null): NetworkAPIResult<SparkHistoryPage?> = NetworkAPIResult.Success(SparkHistoryPage())
    suspend fun get(id: String): NetworkAPIResult<SparkSession?>
    suspend fun start(intent: SparkStartIntent): NetworkAPIResult<SparkSession?>
    suspend fun answer(id: String, envelope: JsonObject): NetworkAPIResult<SparkSession?>
    suspend fun skip(id: String): NetworkAPIResult<SparkSession?>
    suspend fun card(id: String, eventId: String): NetworkAPIResult<SparkSession?>
}
class SparkRepository(private val client: HttpClient, private val environment: ApiEnvironment) : SparkSource {
    private val revision = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = revision
    override fun changed() { revision.value += 1 }
    private fun path(value: String) = environment.baseUrl + "api/social/sparks/" + value
    override suspend fun home() = client.safeRequest<SparkHome> { url(path("home/")) }.result
    override suspend fun history(after: String?) = client.safeRequest<SparkHistoryPage> {
        url(path("history/")); after?.let { parameter("after", it) }
    }.result
    override suspend fun get(id: String) = client.safeRequest<SparkSession> { url(path("$id/")) }.result
    override suspend fun start(intent: SparkStartIntent) = client.safeRequest<SparkSession> {
        url(path("")); method = HttpMethod.Post
        setBody(buildJsonObject { put("request_id", intent.id); put("couple", intent.couple); put("question_id", intent.question) })
    }.result
    override suspend fun answer(id: String, envelope: JsonObject) = client.safeRequest<SparkSession> {
        url(path("$id/answer/")); method = HttpMethod.Put; setBody(buildJsonObject { put("envelope", envelope) })
    }.result
    override suspend fun skip(id: String) = client.safeRequest<SparkSession> {
        url(path("$id/skip/")); method = HttpMethod.Post; setBody(buildJsonObject {})
    }.result
    override suspend fun card(id: String, eventId: String) = client.safeRequest<SparkSession> {
        url(path("$id/card/")); method = HttpMethod.Put; setBody(buildJsonObject { put("event_id", eventId) })
    }.result
}
