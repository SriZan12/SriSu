package com.srisu.srisu.core.data.websocket

import com.srisu.srisu.core.data.response.chat.ChatMessage
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

class ChatWebSocketClient(
    private val httpClient: HttpClient
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val host = "192.168.1.77"
    val port = 8000
    val roomId = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4"

    val wsUrl = "ws://$host:$port/ws/chat/$roomId/"
    private val json: Json = Json { ignoreUnknownKeys = true }

    // Raw incoming JSON strings from server
    private val _incomingMessages = MutableSharedFlow<String>(replay = 0)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val _parsedMessages = MutableSharedFlow<ChatMessage>(replay = 0)
    val parsedMessages = _parsedMessages.asSharedFlow()

    @Volatile
    private var isRunning = false

    private var currentSession: DefaultClientWebSocketSession? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            connectionLoop()
        }
    }

    fun stop() {
        isRunning = false
        scope.launch {
            currentSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client stopped"))
            currentSession = null
            // Don't close httpClient here if it's shared/injected!
            // Only close if you created it internally
        }
        // Cancel scope only if you're sure no other code uses it
        // scope.cancel() // Usually NOT recommended unless this client is fully isolated
    }

    suspend fun sendJson(rawJson: String) {
        if (!isRunning) return
        currentSession?.outgoing?.send(Frame.Text(rawJson))
    }

    suspend fun sendChatMessage(message: ChatMessage) {
        val payload = json.encodeToString(
            mapOf(
                "action" to "send_message",
                "sender_id" to message.sender,
                "receiver_id" to message.receiver,
                "text" to message.text,
                "timestamp" to message.timestamp,
                "message_type" to message.messageType,
                "couple" to message.couple,
                "singles" to message.singles,
                "medias" to message.medias,
                "reply_to" to message.replyTo
            )
        )
        sendJson(payload)
    }

    private suspend fun connectionLoop() {
        var backoff = 1.seconds
        val maxBackoff = 30.seconds

        while (isRunning) {
            try {
                httpClient.webSocket(wsUrl) {
                    currentSession = this
                    backoff = 1.seconds // reset backoff on successful connection

                    // Launch sender and receiver coroutines
                    val senderJob = launch { outgoingLoop() }
                    val receiverJob = launch { readLoop() }

                    // Wait for either to fail or connection close
                    senderJob.join()
                    receiverJob.join()
                }
            } catch (e: Exception) {
                // Log if needed: e.printStackTrace()
                if (!isRunning) break
            } finally {
                currentSession = null
            }

            if (!isRunning) break

            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(maxBackoff)
        }
    }


    private suspend fun DefaultClientWebSocketSession.outgoingLoop() {
        try {
            //pass
        } catch (exception: Exception) {
            AppLogger.log(exception.message.toString())
        }
    }

    // Reads incoming frames and emits raw JSON
    private suspend fun DefaultClientWebSocketSession.readLoop() {
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        AppLogger.log("Raw WS Message: $text")

                        // Parse the action first
                        try {
                            val jsonElement = json.parseToJsonElement(text)
                            val action = jsonElement.jsonObject["action"]?.jsonPrimitive?.content

                            when (action) {
                                "fetch_messages" -> {
                                    // Handle message history
                                    val dataArray = jsonElement.jsonObject["data"]?.jsonArray
                                    dataArray?.forEach { element ->
                                        val msg = json.decodeFromJsonElement<ChatMessage>(element)
                                        // You need a way to expose this to repository
                                        // Let's assume you have a callback or flow for parsed messages
                                        _parsedMessages.emit(msg) // You'll add this flow
                                    }
                                }

                                "new_message" -> {
                                    // Handle real-time new message
                                    val messageJson = jsonElement.jsonObject["message"]?.jsonObject
                                        ?: jsonElement.jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
                                    if (messageJson != null) {
                                        val msg =
                                            json.decodeFromJsonElement<ChatMessage>(messageJson)
                                        _parsedMessages.emit(msg)
                                    }
                                }

                                "message_sent" -> {
                                    // Optional: confirmation
                                    AppLogger.log("Message sent confirmation")
                                }

                                else -> {
                                    // Forward raw for debugging or other actions
                                    _incomingMessages.emit(text)
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.log("Failed to parse WS message: ${e.message}")
                            e.printStackTrace()
                            _incomingMessages.emit(text) // fallback
                        }
                    }

                    is Frame.Close -> {
                        val reason = frame.readReason()
                        AppLogger.log("WebSocket closed: ${reason?.message}")
                        break
                    }

                    else -> { /* ignore */
                    }
                }
            }
        } catch (e: Throwable) {
            AppLogger.log("WebSocket read error: ${e.message}")
            e.printStackTrace()
        } finally {
            if (isRunning) {
                // Trigger reconnect
            }
        }
    }
}