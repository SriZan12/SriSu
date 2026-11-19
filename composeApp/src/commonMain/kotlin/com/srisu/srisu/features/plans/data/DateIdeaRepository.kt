package com.srisu.srisu.features.plans.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable data class DateIdea(val id: String, val couple: Long, val creator: Long, val preset: String,
    @SerialName("interested_by") val interestedBy: List<Long> = emptyList(), val state: String,
    val revision: Long, @SerialName("created_at") val createdAt: String,
    @SerialName("plan_id") val planId: Long? = null) {
    val title get() = when (preset) { "coffee" -> "Coffee together"; "walk" -> "An evening walk"; else -> "Movie night" }
}
@Serializable data class DateIdeas(val couple: Long, val items: List<DateIdea> = emptyList())
interface DateIdeaSource {
    suspend fun list(couple: Long): NetworkAPIResult<DateIdeas?>
    suspend fun get(id: String): NetworkAPIResult<DateIdea?>
    suspend fun create(couple: Long, preset: String, requestId: String): NetworkAPIResult<DateIdea?>
    suspend fun act(id: String, revision: Long, requestId: String, action: String,
        values: JsonObject = buildJsonObject {}): NetworkAPIResult<DateIdea?>
}
class DateIdeaRepository(private val client: HttpClient, private val environment: ApiEnvironment): DateIdeaSource {
    private fun path(value: String) = environment.baseUrl + "api/social/date-ideas/" + value
    override suspend fun list(couple: Long) = client.safeRequest<DateIdeas> { url(path("")); parameter("couple", couple) }.result
    override suspend fun get(id: String) = client.safeRequest<DateIdea> { url(path("$id/")) }.result
    override suspend fun create(couple: Long, preset: String, requestId: String) = client.safeRequest<DateIdea> {
        url(path("")); method = HttpMethod.Post
        setBody(buildJsonObject { put("couple", couple); put("preset", preset); put("request_id", requestId) })
    }.result
    override suspend fun act(id: String, revision: Long, requestId: String, action: String, values: JsonObject) = client.safeRequest<DateIdea> {
        url(path("$id/actions/")); method = HttpMethod.Post
        setBody(buildJsonObject { put("request_id", requestId); put("expected_revision", revision); put("action", action); put("values", values) })
    }.result
}
