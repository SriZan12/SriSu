package com.srisu.srisu.core.data.remote

import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    fun create(
        sessionStorage: SessionStorage,
        enableNetworkLogging: Boolean = false,
    ): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(
                    json = Json {
                        classDiscriminator = "type"
                        prettyPrint = true
                        ignoreUnknownKeys = true
                        explicitNulls = true
                    }
                )
            }
            install(WebSockets){
                pingIntervalMillis = 30_000L
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 120_000L
                requestTimeoutMillis = 120_000L
                connectTimeoutMillis = 120_000L
            }
            if (enableNetworkLogging) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            AppLogger.log(message)
                        }
                    }
                    // BODY may contain credentials, private messages, and profile data.
                    level = LogLevel.HEADERS
                    sanitizeHeader { header -> header == HttpHeaders.Authorization }
                }
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)

                try {
                    val session = sessionStorage.getSession(SESSION_KEY)
                    val bearerToken = getBearerToken(session)
                    if (bearerToken != null) {
                        bearerAuth(token = bearerToken)
                    }
                } catch (ex: Exception) {
                    AppLogger.log("token exception ${ex.message}")
                }
            }
        }
    }

    fun getBearerToken(session: String?): String? {
        if (session != null) {
            val sessionData = Json.decodeFromString<Session>(session)
            return sessionData.access
        }

        return null
    }
}
