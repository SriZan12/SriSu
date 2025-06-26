package com.srisu.srisu.core.data.network

import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.Constants.SESSION_KEY
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    fun create(engine: HttpClientEngine, sessionStorage: SessionStorage): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    json = Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                        explicitNulls = true
                    }
                )
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 120_000L
                requestTimeoutMillis = 120_000L
                connectTimeoutMillis = 120_000L
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        AppLogger.log(message)
                    }
                }
//                level = LogLevel.ALL
                level = LogLevel.BODY
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

    private fun getBearerToken(session: String?): String? {
        if (session != null) {
            val sessionData = Json.decodeFromString<Session>(session)
            AppLogger.log("token is ${sessionData.access}")
            return sessionData.access
        }

        return null
    }
}