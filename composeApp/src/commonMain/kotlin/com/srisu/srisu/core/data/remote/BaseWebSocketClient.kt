package com.srisu.srisu.core.data.remote

import com.srisu.srisu.core.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

abstract class BaseWebSocketClient(
    private val httpClient: HttpClient,
    private val externalScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val wsUrlProvider: () -> String,
) {
    private var connectionJob: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var currentSession: DefaultClientWebSocketSession? = null

    fun connect() {
        if (connectionJob?.isActive == true) {
            AppLogger.log("WebSocket already running")
            return
        }

        isRunning = true
        connectionJob = externalScope.launch(dispatcher) {
            connectionLoop()
        }
    }

    fun disconnect(reason: String? = null) {
        isRunning = false
        connectionJob?.cancel(CancellationException(reason ?: "Client disconnected"))
        connectionJob = null

        val session = currentSession
        currentSession = null

        externalScope.launch(dispatcher) {
            try {
                session?.close(
                    CloseReason(
                        CloseReason.Codes.NORMAL,
                        reason ?: "Client disconnected",
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                AppLogger.log("Error during disconnect: ${exception.message}")
            } finally {
                onDisconnected(reason)
            }
        }
    }

    suspend fun send(rawPayload: String) {
        val session = currentSession
            ?: throw IllegalStateException("Cannot send while the WebSocket is disconnected")

        try {
            session.outgoing.send(Frame.Text(rawPayload))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            AppLogger.log("Error sending message: ${exception.message}")
            onError(exception)
            throw exception
        }
    }

    private suspend fun connectionLoop() {
        var backoff = 1.seconds
        val maxBackoff = 30.seconds

        while (isRunning) {
            try {
                httpClient.webSocket(urlString = wsUrlProvider()) {
                    currentSession = this
                    backoff = 1.seconds

                    onConnected()
                    AppLogger.log("WebSocket connected")
                    onSessionStarted(this)
                    readLoop()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                AppLogger.log("WebSocket error: ${exception.message}")
                onError(exception)
            } finally {
                currentSession = null
            }

            if (!isRunning) break

            AppLogger.log("Reconnecting in $backoff...")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(maxBackoff)
        }
    }

    private suspend fun DefaultClientWebSocketSession.readLoop() {
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> onIncoming(frame.readText())
                is Frame.Close -> {
                    val reason = frame.readReason()
                    AppLogger.log("WebSocket closed: ${reason?.message}")
                    onDisconnected(reason?.message)
                    break
                }
                else -> Unit
            }
        }
    }

    protected open suspend fun onConnected() = Unit

    protected open suspend fun onSessionStarted(session: DefaultClientWebSocketSession) = Unit

    protected abstract suspend fun onIncoming(raw: String)

    protected open suspend fun onDisconnected(reason: String?) = Unit

    protected open suspend fun onError(error: Throwable) = Unit
}
