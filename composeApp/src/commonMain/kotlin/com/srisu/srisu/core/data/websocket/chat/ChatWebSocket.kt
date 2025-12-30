package com.srisu.srisu.core.data.websocket.chat

import com.srisu.srisu.core.data.dto.chatdto.FetchMessageDTO
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.response.chat.MessageDeliveredResponse
import com.srisu.srisu.core.data.response.chat.MessageReadResponse
import com.srisu.srisu.core.data.response.chat.SendMessageResponse
import com.srisu.srisu.core.data.response.chat.TypingResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_MESSAGE
import com.srisu.srisu.utils.Constants.ChatConstants.EDIT_MESSAGE
import com.srisu.srisu.utils.Constants.ChatConstants.FETCH_MESSAGES
import com.srisu.srisu.utils.Constants.ChatConstants.MESSAGE_DELIVERED
import com.srisu.srisu.utils.Constants.ChatConstants.MESSAGE_READ
import com.srisu.srisu.utils.Constants.ChatConstants.SEND_MESSAGE
import com.srisu.srisu.utils.Constants.ChatConstants.TYPING
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

    private val _events = MutableSharedFlow<ChatEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val roomId = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4"

    //    private val roomId = "e579dc98-5dbd-4aab-8a48-10985346d7fa"
    private val wsUrl =
        "ws://$host:$port/ws/chat/$roomId/?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoyMDczODI2MDg3LCJpYXQiOjE3NTg0NjYwODcsImp0aSI6IjQ5MTVmOWUxMzI3OTQ0NTJhMWU5MWRmYjFiNzhjZjhjIiwidXNlcl9pZCI6OTd9.Ja2EA1CgeDoM76PwHmYdhB6HGGM1m4sJ0k_d6mbLk7w"
    private val json = Json { ignoreUnknownKeys = true }

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    @Volatile
    private var isRunning = false

    @Volatile
    private var currentSession: DefaultClientWebSocketSession? = null

    fun connect(roomId: String) {
        if (isRunning) {
            AppLogger.log("WebSocket already running")
            return
        }
        isRunning = true

        scope.launch {
            _connectionState.emit(ConnectionState.Connecting)
            connectionLoop(roomId = roomId)
        }

    }

    private suspend fun connectionLoop(roomId: String) {
        var backoff = 1.seconds
        val maxBackoff = 30.seconds

        while (isRunning) {
            try {
                httpClient.webSocket(wsUrl) {
                    currentSession = this
                    backoff = 1.seconds

                    _connectionState.emit(ConnectionState.Connected)
                    _events.emit(ChatEvent.Connected(roomId))
                    AppLogger.log("WebSocket connected")


                    // Auto-fetch initial messages on connection
                    fetchMessages(page = null, pageSize = 50)

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

    private suspend fun DefaultClientWebSocketSession.readLoop() {
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val webSocketText = frame.readText()
                        onIncoming(raw = webSocketText)
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


    fun disconnect(reason: String? = null) {
        isRunning = false
        scope.launch {
            currentSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            currentSession = null
            _connectionState.emit(ConnectionState.Disconnected)
            _events.emit(ChatEvent.Disconnected(reason))

        }
    }

    suspend fun send(rawPayload: String) {
        try {
            currentSession?.outgoing?.send(Frame.Text(rawPayload))
        } catch (e: Exception) {
            AppLogger.log("Error sending message: ${e.message}")
            throw e
        }
    }

    suspend fun onIncoming(raw: String) {
        try {
            val action = Json.parseToJsonElement(raw).jsonObject["action"]?.jsonPrimitive?.content
            AppLogger.log("Action received: $action")

            when (action) {

                FETCH_MESSAGES -> {
                    val response = json.decodeFromString(FetchMessageResponse.serializer(), raw)
                    _events.emit(value = ChatEvent.FetchMessages(messages = response))
                }

                SEND_MESSAGE -> {
                    val response = json.decodeFromString(SendMessageResponse.serializer(), raw)
                    response.data?.let { chatMessage ->
                        _events.emit(value = ChatEvent.SendMessage(chatMessage))
                    }
                }

                EDIT_MESSAGE -> {
                    val response = json.decodeFromString(
                        SendMessageResponse.serializer(),
                        raw
                    )

                    response.data?.let { editedMessage ->
                        AppLogger.log("Message edited: $editedMessage")

                        _events.emit(value = ChatEvent.MessageEdited(editedMessage))
                    }
                }

                DELETE_MESSAGE -> {

                    val response = json.decodeFromString(
                        SendMessageResponse.serializer(),
                        raw
                    )

                    response.data?.let { deletedMessage ->
                        _events.emit(value = ChatEvent.MessageDeleted(message = deletedMessage))
                    }
                }

                TYPING -> {
                    val typingResponse = json.decodeFromString(TypingResponse.serializer(), raw)
                    _events.emit(ChatEvent.MessageTyping(typingResponse = typingResponse))
                }

                MESSAGE_READ -> {
                    val messageReadResponse =
                        json.decodeFromString(MessageReadResponse.serializer(), raw)
                    _events.emit(ChatEvent.MessageRead(messageReadResponse = messageReadResponse))
                }

                MESSAGE_DELIVERED -> {
                    val messageDeliveredResponse =
                        json.decodeFromString(MessageDeliveredResponse.serializer(), raw)
                    _events.emit(ChatEvent.MessageDelivered(messageDeliveredResponse = messageDeliveredResponse))
                }

                else -> {
                    AppLogger.log("Unknown action: $action")
                }
            }

        } catch (e: Exception) {
            AppLogger.log("Error handling incoming message: ${e.message}")
            _events.emit(ChatEvent.Error(e))
        }
    }

    suspend fun fetchMessages(page: Int? = null, pageSize: Int = 50) {
        try {
            val fetchMessage = FetchMessageDTO(
                action = FETCH_MESSAGES,
                page = page?.toLong(),
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
}