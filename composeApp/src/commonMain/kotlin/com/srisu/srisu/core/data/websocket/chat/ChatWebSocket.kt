package com.srisu.srisu.core.data.websocket.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.dto.chatdto.FetchMessageDTO
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.response.chat.SendMessageResponse
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

class ChatWebSocketClient(
    private val httpClient: HttpClient,
    host: String,
    port: Int,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//    private val roomId = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4"
    private val roomId = "e579dc98-5dbd-4aab-8a48-10985346d7fa"
    private val wsUrl = "ws://$host:$port/ws/chat/$roomId/"
    private val json = Json { ignoreUnknownKeys = true }

    // Expose incoming messages as a SharedFlow
    private val _chatMessages = MutableSharedFlow<List<ChatMessage?>?>()
    val chatMessages = _chatMessages.asSharedFlow()

    // Connection state
    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    @Volatile
    private var isRunning = false

    @Volatile
    private var currentSession: DefaultClientWebSocketSession? = null

    // -----------------------------
    // Lifecycle Management
    // -----------------------------

    fun connect() {
        if (isRunning) {
            AppLogger.log("WebSocket already running")
            return
        }
        isRunning = true

        scope.launch {
            _connectionState.emit(ConnectionState.Connecting)
            connectionLoop()
        }
    }

    fun disconnect() {
        isRunning = false
        scope.launch {
            currentSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            currentSession = null
            _connectionState.emit(ConnectionState.Disconnected)
        }
    }

    // -----------------------------
    // Outgoing Messages
    // -----------------------------

    suspend fun sendMessage(message: ChatMessage) {
        try {
            val payload = json.encodeToString(ChatMessage.serializer(), message)
            currentSession?.outgoing?.send(Frame.Text(payload))
            AppLogger.log("Message sent: ${message.text}")
        } catch (e: Exception) {
            AppLogger.log("Error sending message: ${e.message}")
            throw e
        }
    }

    suspend fun fetchMessages(page: Int = 1, pageSize: Int = 20) {
        try {
            val fetchMessage = FetchMessageDTO(
                action = "fetch_messages",
                page = page,
                page_size = pageSize
            )
            val payload = Json.encodeToString(FetchMessageDTO.serializer(), fetchMessage)
            currentSession?.outgoing?.send(Frame.Text(payload))
            AppLogger.log("Fetch messages request sent (page: $page, size: $pageSize)")
        } catch (e: Exception) {
            AppLogger.log("Error fetching messages: ${e.message}")
            throw e
        }
    }

    // --------------------------------
    // Connection Loop with Auto-Reconnect
    // ---------------------------------

    private suspend fun connectionLoop() {
        var backoff = 1.seconds
        val maxBackoff = 30.seconds

        while (isRunning) {
            try {
                httpClient.webSocket(wsUrl) {
                    currentSession = this
                    backoff = 1.seconds

                    _connectionState.emit(ConnectionState.Connected)
                    AppLogger.log("WebSocket connected")

                    // Auto-fetch initial messages on connection
                    fetchMessages(page = 1, pageSize = 100)

                    // Read incoming messages
                    readLoop()
                }
            } catch (e: Exception) {
                AppLogger.log("WebSocket error: ${e.message}")
                _connectionState.emit(ConnectionState.Error(e.message ?: "Unknown error"))
            }

            if (!isRunning) break

            // Reconnect with exponential backoff
            _connectionState.emit(ConnectionState.Reconnecting)
            AppLogger.log("Reconnecting in $backoff...")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(maxBackoff)
        }
    }

    // -----------------------------
    // Incoming Message Handler
    // -----------------------------

    private suspend fun DefaultClientWebSocketSession.readLoop() {
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val webSocketText = frame.readText()
                        handleIncomingMessage(raw = webSocketText)
                    }

                    is Frame.Close -> {
                        val reason = frame.readReason()
                        AppLogger.log("WebSocket closed: ${reason?.message}")
                        _connectionState.emit(ConnectionState.Disconnected)
                        break
                    }

                    else -> {
                        // Ignore other frame types
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("Read loop error: ${e.message}")
            _connectionState.emit(ConnectionState.Error(e.message ?: "Read error"))
        }
    }

    private suspend fun handleIncomingMessage(raw: String) {
        try {
            // Parse the action first
            val action = Json.parseToJsonElement(raw).jsonObject["action"]?.jsonPrimitive?.content
            AppLogger.log("Action received: $action")

            when (action) {
                "fetch_messages" -> {
                    val response = json.decodeFromString(FetchMessageResponse.serializer(), raw)
                    val messageList = response.chatMessage?.results
                    AppLogger.log("Fetched messages emitted, count: ${messageList?.size ?: 0}")
                    _chatMessages.emit(messageList)
                }

                "send_message" -> {
                    val response = json.decodeFromString(SendMessageResponse.serializer(), raw)
                    response.data?.let { chatMessage ->
                        AppLogger.log("New message emitted: ${chatMessage.text}")

                        val currentList = _chatMessages.replayCache.lastOrNull() ?: emptyList()
                        _chatMessages.emit(currentList + listOf(chatMessage)) // append at the end

                    }
                }

                else -> {
                    AppLogger.log("Unknown action received: $action")
                }
            }
        } catch (e: Exception) {
            AppLogger.log("Parse error in handleIncomingMessage: ${e.message}\nRaw payload: $raw")
        }
    }


}