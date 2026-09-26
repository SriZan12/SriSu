package com.srisu.srisu.core.data.remote

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val ApiJson = Json { ignoreUnknownKeys = true; explicitNulls = true }
val SessionCoordinatorKey = AttributeKey<SessionCoordinator>("SriSuSessionCoordinator")
val RequestScopeKey = AttributeKey<RequestScope>("SriSuRequestScope")
data class RequestScope(val coordinator: SessionCoordinator, val stamp: SessionStamp) {
    fun ensureCurrent() = coordinator.ensureCurrent(stamp)
}

object HttpClientFactory {
    @OptIn(ExperimentalUuidApi::class)
    fun create(sessions: SessionCoordinator, environment: ApiEnvironment, engine: HttpClientEngine): HttpClient {
        val sessionPlugin = createClientPlugin("SriSuSessionBoundary") {
            onRequest { request, body ->
                val scope = request.attributes.getOrNull(RequestScopeKey) ?: RequestScope(sessions, sessions.stamp())
                scope.ensureCurrent()
                request.attributes.put(RequestScopeKey, scope)
                if (body !is io.ktor.http.content.OutgoingContent && request.method !in listOf(io.ktor.http.HttpMethod.Get, io.ktor.http.HttpMethod.Head)) {
                    request.headers[HttpHeaders.ContentType] = ContentType.Application.Json.toString()
                }
                request.headers.remove(HttpHeaders.Authorization)
                if (environment.owns(request.url.build())) {
                    sessions.authorization(scope.stamp)?.let { request.headers.append(HttpHeaders.Authorization, "Bearer $it") }
                    request.headers["X-SriSu-Contract"] = "core-1"
                    request.headers["X-Request-ID"] = Uuid.random().toString()
                }
            }
            onResponse { response ->
                response.call.request.attributes.getOrNull(RequestScopeKey)?.ensureCurrent()
                if (response.call.request.attributes.getOrNull(SocketHandshakeKey) == true && response.status.value in listOf(401, 403)) {
                    throw SocketAccessDenied(response.status.value)
                }
            }
        }
        return HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
            install(ContentNegotiation) { json(ApiJson) }
            // OkHttp 3.2.3 rejects the plugin's maxFrameSize setter. Enforce our
            // decode bound in KtorSocketConnector; native engine owns wire buffering.
            install(WebSockets) { pingIntervalMillis = 25_000 }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(sessionPlugin)
            defaultRequest { accept(ContentType.Application.Json) }
        }.also { it.attributes.put(SessionCoordinatorKey, sessions) }
    }
}
