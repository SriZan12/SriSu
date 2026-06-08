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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

abstract class BaseWebSocketClient(
    private val httpClient: HttpClient,
    private val wsUrl: String,
) {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    protected var isRunning = false

    @Volatile
    protected var currentSession: DefaultClientWebSocketSession? = null

    fun connect() {
        if (isRunning) {
            AppLogger.log("WebSocket already running")
            return
        }

        isRunning = true
        scope.launch {
            connectionLoop()
        }
    }

    fun disconnect(reason: String? = null) {
        isRunning = false
        scope.launch {
            try {
                currentSession?.close(
                    CloseReason(
                        CloseReason.Codes.NORMAL,
                        reason ?: "Client disconnected"
                    )
                )
            } catch (e: Exception) {
                AppLogger.log("Error during disconnect: ${e.message}")
            } finally {
                currentSession = null
                onDisconnected(reason)
            }
        }
    }

    suspend fun send(rawPayload: String) {
        try {
            currentSession?.outgoing?.send(Frame.Text(rawPayload))
        } catch (e: Exception) {
            AppLogger.log("Error sending message: ${e.message}")
            onError(e)
            throw e
        }
    }

    private suspend fun connectionLoop() {
        var backoff = 1.seconds
        val maxBackoff = 30.seconds

        while (isRunning) {
            try {
                httpClient.webSocket(urlString = wsUrl) {
                    currentSession = this
                    backoff = 1.seconds

                    onConnected()
                    AppLogger.log("WebSocket connected")

                    onSessionStarted(this)
                    readLoop()
                }
            } catch (e: Exception) {
                AppLogger.log("WebSocket error: ${e.message}")
                onError(e)
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
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val raw = frame.readText()
                        onIncoming(raw)
                    }

                    is Frame.Close -> {
                        val reason = frame.readReason()
                        AppLogger.log("WebSocket closed: ${reason?.message}")
                        onDisconnected(reason?.message)
                        break
                    }

                    else -> {
                        // Ignore unsupported frame types for now
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("Read loop error: ${e.message}")
            onError(e)
        }
    }

    /**
     * Called once the websocket session is connected.
     */
    protected open suspend fun onConnected() {}

    /**
     * Called after a session is established and before reading starts.
     * Useful for subscriptions / initial fetches.
     */
    protected open suspend fun onSessionStarted(session: DefaultClientWebSocketSession) {}

    /**
     * Called when text payload is received.
     */
    protected abstract suspend fun onIncoming(raw: String)

    /**
     * Called when websocket disconnects or closes.
     */
    protected open suspend fun onDisconnected(reason: String?) {}

    /**
     * Called on connection/read/send errors.
     */
    protected open suspend fun onError(error: Throwable) {}
}