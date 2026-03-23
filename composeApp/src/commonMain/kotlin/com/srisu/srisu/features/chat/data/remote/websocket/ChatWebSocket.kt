package com.srisu.srisu.features.chat.data.remote.websocket

import com.srisu.srisu.features.chat.data.remote.dto.ChatRoomDTO
import com.srisu.srisu.features.chat.data.remote.dto.FetchMessageDTO
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomResponse
import com.srisu.srisu.features.chat.data.remote.response.FetchMessageResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageReadResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageResponse
import com.srisu.srisu.features.chat.data.remote.response.TypingResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_MESSAGE
import com.srisu.srisu.utils.Constants.ChatConstants.EDIT_MESSAGE
import com.srisu.srisu.utils.Constants.ChatConstants.FETCH_MESSAGES
import com.srisu.srisu.utils.Constants.ChatConstants.GET_CHAT_ROOMS
import com.srisu.srisu.utils.Constants.ChatConstants.MESSAGE_DELIVERED
import com.srisu.srisu.utils.Constants.ChatConstants.MESSAGE_READ
import com.srisu.srisu.utils.Constants.ChatConstants.REACT_TO_MESSAGE
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
    userToken: String?
) {

    private val _events = MutableSharedFlow<ChatWebSocketEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ChatWebSocketEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsUrl =
        "ws://$host:$port/ws/chat/?token=$userToken"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var isRunning = false

    @Volatile
    private var currentSession: DefaultClientWebSocketSession? = null


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

    private suspend fun connectionLoop() {
        var backoff = 1.seconds
        val maxBackoff = 30.seconds

        while (isRunning) {
            try {
                httpClient.webSocket(wsUrl) {
                    currentSession = this
                    backoff = 1.seconds

                    _events.emit(ChatWebSocketEvent.Connected)
                    AppLogger.log("WebSocket connected")

                    // Auto-fetch initial messages on connection
                    getChatRooms()

                    // Read incoming messages
                    readLoop()
                }
            } catch (e: Exception) {
                AppLogger.log("WebSocket error: ${e.message}")
            }

            if (!isRunning) break

            // Reconnect with exponential backoff
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
                        break
                    }

                    else -> {
                        // Ignore other frame types
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("Read loop error: ${e.message}")
        }
    }


    fun disconnect(reason: String? = null) {
        isRunning = false
        scope.launch {
            currentSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            currentSession = null
            _events.emit(ChatWebSocketEvent.Disconnected(reason))

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
                    _events.emit(value = ChatWebSocketEvent.FetchMessages(messages = response))
                }

                SEND_MESSAGE -> {
                    val response = json.decodeFromString(MessageResponse.serializer(), raw)
                    response.let { chatMessage ->
                        _events.emit(
                            value = ChatWebSocketEvent.SendMessage(
                                message = chatMessage.data,
                                updatedChatRoom = chatMessage.updatedChatRoom
                            )
                        )
                    }
                }

                EDIT_MESSAGE -> {
                    val response = json.decodeFromString(
                        MessageResponse.serializer(),
                        raw
                    )

                    response.data?.let { editedMessage ->
                        AppLogger.log("Message edited: $editedMessage")

                        _events.emit(value = ChatWebSocketEvent.MessageEdited(editedMessage))
                    }
                }

                DELETE_MESSAGE -> {

                    val response = json.decodeFromString(
                        MessageResponse.serializer(),
                        raw
                    )

                    response.data?.let { deletedMessage ->
                        _events.emit(value = ChatWebSocketEvent.MessageDeleted(message = deletedMessage))
                    }
                }

                TYPING -> {
                    val typingResponse = json.decodeFromString(TypingResponse.serializer(), raw)
                    _events.emit(ChatWebSocketEvent.MessageTyping(typingResponse = typingResponse))
                }

                MESSAGE_READ -> {
                    val messageReadResponse =
                        json.decodeFromString(MessageReadResponse.serializer(), raw)
                    _events.emit(ChatWebSocketEvent.MessageRead(messageReadResponse = messageReadResponse))
                }

                MESSAGE_DELIVERED -> {
                    val messageDeliveredResponse =
                        json.decodeFromString(MessageDeliveredResponse.serializer(), raw)
                    _events.emit(ChatWebSocketEvent.MessageDelivered(messageDeliveredResponse = messageDeliveredResponse))
                }

                REACT_TO_MESSAGE -> {
                    val reactToMessageResponse = json.decodeFromString(
                        MessageResponse.serializer(),
                        raw
                    )

                    _events.emit(ChatWebSocketEvent.ReactToMessage(reactToMessage = reactToMessageResponse.data))
                }

                GET_CHAT_ROOMS -> {
                    val chatRoomResponse = json.decodeFromString(
                        deserializer = ChatRoomResponse.serializer(),
                        string = raw
                    )

                    _events.emit(ChatWebSocketEvent.GetChatRooms(chatRoomResponse))
                }


                else -> {
                    AppLogger.log("Unknown action: $action")
                }
            }

        } catch (e: Exception) {
            AppLogger.log("Error handling incoming message: ${e.message}")
            _events.emit(ChatWebSocketEvent.Error(e))
        }
    }

    suspend fun fetchMessages(page: Int? = null, pageSize: Int = 20, chatRoomId: String?) {
        try {
            val fetchMessage = FetchMessageDTO(
                action = FETCH_MESSAGES,
                page = page?.toLong(),
                page_size = pageSize,
                chatRoomId = chatRoomId
            )
            val payload = Json.encodeToString(FetchMessageDTO.serializer(), fetchMessage)
            currentSession?.outgoing?.send(Frame.Text(payload))
        } catch (e: Exception) {
            AppLogger.log("Error fetching messages: ${e.message}")
            throw e
        }
    }

    private suspend fun getChatRooms(lastUpdatedAt: String? = null) {
        try {
            val chatRoomDTO = ChatRoomDTO(
                action = GET_CHAT_ROOMS,
                limit = 10,
                lastUpdated = lastUpdatedAt ?: ""
            )
            send(
                rawPayload = Json.encodeToString(value = chatRoomDTO),
            )
        } catch (exception: Exception) {
            AppLogger.log("Something went wrong = ${exception.message}")
        }


    }
}