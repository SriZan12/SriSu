package com.srisu.srisu.core.data.websocket

import com.srisu.srisu.core.data.response.chat.ChatMessage
import com.srisu.srisu.core.data.response.chat.ChatResponse
import com.srisu.srisu.core.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

@Serializable
data class OutgoingChatMessage(
    val action: String = "send_message",
    val sender_id: Int,
    val receiver_id: Int?,
    val text: String?,
    val timestamp: String?,
    val message_type: String,
    val couple: Int? = null,
    val singles: Int? = null,
    val medias: List<Int> = emptyList(),
    val reply_to: Int? = null
)

@Serializable
data class WsResponse(
    @SerialName("action")
    val action: String,
    @SerialName("data")
    val data: List<ChatMessage>? = null,
    @SerialName("message")
    val message: String? = null
)

@Serializable
data class FetchMessages(
    @SerialName("action")
    val action: String?,
    @SerialName("page")
    val page: Int,
    @SerialName("page_size")
    val page_size: Int
)

class ChatWebSocketClient(
    private val httpClient: HttpClient
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val host = "192.168.1.74"
    val port = 8000
    val roomId = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4"
    val wsUrl = "ws://$host:$port/ws/chat/$roomId/"

    private val json = Json { ignoreUnknownKeys = true }

    private val _incomingRaw = MutableSharedFlow<String>()
    val incomingRaw = _incomingRaw.asSharedFlow()

    private val _parsedMessages = MutableSharedFlow<ChatResponse.Data?>()
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
        }
    }

    // -----------------------------
    // Outgoing messages
    // -----------------------------

    suspend fun sendJson(text: String) {
        currentSession?.outgoing?.send(Frame.Text(text))
    }

    suspend fun sendChatMessage(msg: ChatMessage) {
        val outgoing = OutgoingChatMessage(
            sender_id = msg.sender,
            receiver_id = msg.receiver ,
            text = msg.text,
            timestamp = msg.timestamp,
            message_type = msg.messageType,
            couple = msg.couple,
            singles = msg.singles,
            medias = msg.medias,
            reply_to = msg.replyTo
        )

        val payload = json.encodeToString(OutgoingChatMessage.serializer(), outgoing)
        sendJson(payload)
    }

    suspend fun fetchMessages(page: Int, pageSize: Int) {
        val fetchMessage = FetchMessages(
            action = "fetch_messages",
            page = page,
            page_size = pageSize

        )
        sendJson(Json.encodeToString(fetchMessage))
    }

    // -----------------------------
    // Connection Loop
    // -----------------------------
    private suspend fun connectionLoop() {
        var backoff = 1.seconds
        val maxBackoff = 30.seconds

        while (isRunning) {
            try {
                httpClient.webSocket(wsUrl) {
                    currentSession = this
                    backoff = 1.seconds

                    AppLogger.log("WebSocket connection established")

                    fetchMessages(1, 20)

                    val receiver = launch { readLoop() }
                    receiver.join()
                }
            } catch (e: Exception) {
                AppLogger.log("WebSocket connection error: ${e.message}")
            }

            if (!isRunning) break

            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(maxBackoff)
        }
    }

    // -----------------------------
    // Incoming messages
    // -----------------------------
    private suspend fun DefaultClientWebSocketSession.readLoop() {
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        _incomingRaw.emit(text)

                        handleWsResponse(text)
                    }

                    is Frame.Close -> {
                        val reason = frame.readReason()
                        AppLogger.log("WebSocket closed: ${reason?.message}")
                        break
                    }

                    else -> {}
                }
            }
        } catch (e: Exception) {
            AppLogger.log("WebSocket read error: ${e.message}")
        }
    }

    private suspend fun handleWsResponse(raw: String) {
        try {
            AppLogger.log("CHAT RESPONSE = $raw")
            val response = json.decodeFromString(ChatResponse.serializer(), raw)

            when (response.action) {
                "fetch_messages" -> {
                    response.data?.forEach { _parsedMessages.emit(it) }
                }


                else -> {
                    AppLogger.log("Unknown WS action: ${response.action}")
                }
            }

        } catch (e: Exception) {
            AppLogger.log("Parse error: ${e.message}")
        }
    }
}
