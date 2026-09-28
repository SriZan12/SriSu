package com.srisu.srisu.core.session

import com.srisu.srisu.core.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

@Serializable data class SessionTokens(val access: String, val refresh: String)
@Serializable data class TokenResponse(val tokens: SessionTokens)

/** JWT payload inspection is a scheduling hint only. The server verifies authority. */
fun accessExpiry(token: String?): Long? = try {
    val payload = token?.split('.')?.getOrNull(1)
    payload?.let { ApiJson.parseToJsonElement(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(it).decodeToString()).jsonObject["exp"]?.jsonPrimitive?.longOrNull }
} catch (_: Exception) { null }

fun accessHasDeviceSession(token: String?): Boolean = try {
    val payload = requireNotNull(token).split('.')[1]
    ApiJson.parseToJsonElement(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()).jsonObject.containsKey("sid")
} catch (_: Exception) { false }

suspend fun HttpClient.refreshSessionIfNeeded(force: Boolean = false, rejectedAccess: String? = null): ApiError? {
    val sessions = attributes.getOrNull(SessionCoordinatorKey) ?: return null
    val baseUrl = attributes[ApiEnvironmentKey].baseUrl
    val observed = sessions.stamp()
    return sessions.refreshMutex.withLock {
        sessions.ensureCurrent(observed)
        val current = sessions.currentSession() ?: return@withLock null
        if (force && rejectedAccess != null && current.access != rejectedAccess) return@withLock null
        val expiry = accessExpiry(current.access)
        // Upgrade legacy sessions when a refresh proof exists. Access-only sessions
        // remain usable until expiry or an authoritative rejection by the server.
        if (!force && (expiry == null || (expiry > sessions.epochSeconds() + 30 &&
                    (accessHasDeviceSession(current.access) || current.refresh == null)))) return@withLock null
        val refresh = current.refresh ?: return@withLock ApiError(NetworkAPIResult.ErrorType.UNAUTHORIZED, "unauthenticated", "Sign in to continue.").also { sessions.clearIfCurrent(observed) }
        // Public request: no recursion, no bearer access header, no automatic write replay.
        val requestId = sessions.refreshRequestId(observed)
        val result = safeRequest<TokenResponse> {
            url("${baseUrl}api/auth/refresh/")
            method = HttpMethod.Post
            attributes.put(PublicAuthRequestKey, true)
            setBody(mapOf("refresh" to refresh, "request_id" to requestId))
        }.result
        sessions.ensureCurrent(observed)
        when (result) {
            is NetworkAPIResult.Success -> {
                val tokens = result.response?.tokens
                if (tokens == null) ApiError(NetworkAPIResult.ErrorType.SERIALIZATION, "serialization", "Please retry signing in.")
                else { sessions.rotateIfCurrent(tokens.access, tokens.refresh, observed); null }
            }
            is NetworkAPIResult.Error -> {
                if (result.failure.status == 401) sessions.clearIfCurrent(observed)
                result.failure
            }
        }
    }
}
