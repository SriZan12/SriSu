package com.srisu.srisu.features.coupleprofile.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable data class CoupleStatus(val author: Long, val text: String,
    @SerialName("expires_at") val expiresAt: String? = null, val revision: Long)
@Serializable data class CoupleStatuses(val couple: Long, val items: List<CoupleStatus> = emptyList(),
    @SerialName("server_time") val serverTime: String)
interface CoupleStatusSource {
    suspend fun get(couple: Long): NetworkAPIResult<CoupleStatuses?>
    suspend fun save(couple: Long, text: String, revision: Long, requestId: String): NetworkAPIResult<CoupleStatus?>
}
class CoupleStatusRepository(private val client: HttpClient, private val environment: ApiEnvironment): CoupleStatusSource {
    private fun path(value: String) = environment.baseUrl + "api/social/couple-status/" + value
    override suspend fun get(couple: Long) = client.safeRequest<CoupleStatuses> { url(path("")); parameter("couple", couple) }.result
    override suspend fun save(couple: Long, text: String, revision: Long, requestId: String) = client.safeRequest<CoupleStatus> {
        url(path("mine/")); method = HttpMethod.Put
        setBody(buildJsonObject { put("couple", couple); put("text", text); put("expected_revision", revision); put("request_id", requestId) })
    }.result
}
