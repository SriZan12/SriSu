package com.srisu.srisu.features.chat.data.remote.websocket

import com.srisu.srisu.core.coroutines.AppCoroutineDispatchers
import com.srisu.srisu.core.coroutines.ApplicationCoroutineScope
import com.srisu.srisu.core.data.remote.BaseWebSocketClient
import com.srisu.srisu.core.data.remote.NetworkConfig
import com.srisu.srisu.core.session.SessionUtils
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomItemDto
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomsData
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomsSocketResponse
import com.srisu.srisu.features.chat.data.remote.response.FetchMessagesData
import com.srisu.srisu.features.chat.data.remote.response.FetchMessagesSocketResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredData
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredSocketResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageMutationData
import com.srisu.srisu.features.chat.data.remote.response.MessageMutationSocketResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageReadData
import com.srisu.srisu.features.chat.data.remote.response.MessageReadSocketResponse
import com.srisu.srisu.features.chat.data.remote.response.ReactionData
import com.srisu.srisu.features.chat.data.remote.response.ReactionSocketResponse
import com.srisu.srisu.features.chat.data.remote.response.SocketEnvelope
import com.srisu.srisu.features.chat.data.remote.response.SocketErrorEnvelope
import com.srisu.srisu.features.chat.data.remote.response.TypingData
import com.srisu.srisu.features.chat.data.remote.response.TypingSocketResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChatWebSocketClient(
    httpClient: HttpClient,
    networkConfig: NetworkConfig,
    sessionUtils: SessionUtils,
    applicationScope: ApplicationCoroutineScope,
    dispatchers: AppCoroutineDispatchers,
) : BaseWebSocketClient(
    httpClient = httpClient,
    externalScope = applicationScope,
    dispatcher = dispatchers.io,
    wsUrlProvider = { networkConfig.webSocketUrl(sessionUtils.getSession()?.access) },
) {

    private val _events = MutableSharedFlow<ChatWebSocketEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ChatWebSocketEvent> = _events.asSharedFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun onConnected() {
        _events.emit(ChatWebSocketEvent.Connected)
    }

    override suspend fun onSessionStarted(session: DefaultClientWebSocketSession) {
        getChatRooms()
    }

    override suspend fun onDisconnected(reason: String?) {
        _events.emit(ChatWebSocketEvent.Disconnected(reason))
    }

    override suspend fun onError(error: Throwable) {
        _events.emit(ChatWebSocketEvent.Error(error))
    }

    override suspend fun onIncoming(raw: String) {
        try {
            val root = json.parseToJsonElement(raw).jsonObject
            val type = root["type"]?.jsonPrimitive?.content
            val action = root["action"]?.jsonPrimitive?.content

            AppLogger.log("Socket frame received. type=$type, action=$action")

            if (type == SocketFrameTypes.ERROR) {
                val errorEnvelope = json.decodeFromString(
                    deserializer = SocketErrorEnvelope.serializer(),
                    string = raw,
                )

                _events.emit(
                    ChatWebSocketEvent.Error(
                        throwable = IllegalStateException(
                            errorEnvelope.message ?: "Unknown websocket error"
                        )
                    )
                )
                return
            }

            when (action) {
                ChatSocketActions.FETCH_MESSAGES -> {
                    val response = json.decodeFromString(
                        deserializer = FetchMessagesSocketResponse.serializer(
                            typeSerial0 = FetchMessagesData.serializer()
                        ),
                        string = raw,
                    )

                    _events.emit(
                        ChatWebSocketEvent.FetchMessages(
                            data = response.data ?: FetchMessagesData()
                        )
                    )
                }

                ChatSocketActions.SEND_MESSAGE,
                ChatSocketEvents.MESSAGE_CREATED -> {
                    val response = json.decodeFromString(
                        deserializer = MessageMutationSocketResponse.serializer(
                            typeSerial0 = MessageMutationData.serializer()
                        ),
                        string = raw,
                    )

                    _events.emit(
                        ChatWebSocketEvent.SendMessage(
                            message = response.data?.message,
                            updatedChatRoom = response.data?.chatRoom,
                        )
                    )
                }

                ChatSocketActions.EDIT_MESSAGE,
                ChatSocketEvents.MESSAGE_UPDATED -> {
                    val response = json.decodeFromString(
                        deserializer = MessageMutationSocketResponse.serializer(
                            typeSerial0 = MessageMutationData.serializer()
                        ),
                        string = raw,
                    )

                    response.data?.let { editedMessage ->
                        _events.emit(
                            ChatWebSocketEvent.MessageEdited(
                                editedMessage.message,
                                editedMessage.chatRoom
                            )
                        )

                    }
                }

                ChatSocketActions.DELETE_MESSAGE,
                ChatSocketEvents.MESSAGE_DELETED -> {
                    val response = json.decodeFromString(
                        deserializer = MessageMutationSocketResponse.serializer(
                            typeSerial0 = MessageMutationData.serializer()
                        ),
                        string = raw,
                    )

                    response.data?.let { deletedMessage ->
                        _events.emit(ChatWebSocketEvent.MessageDeleted(message = deletedMessage.message, chatRoom = deletedMessage.chatRoom))
                    }
                }

                ChatSocketActions.SET_TYPING,
                ChatSocketEvents.TYPING_UPDATED -> {
                    val response = json.decodeFromString(
                        deserializer = TypingSocketResponse.serializer(
                            typeSerial0 = TypingData.serializer()
                        ),
                        string = raw,
                    )

                    response.data?.let { typingData ->
                        _events.emit(ChatWebSocketEvent.MessageTyping(typingData))
                    }
                }

                ChatSocketActions.MARK_READ,
                ChatSocketEvents.MESSAGE_READ -> {
                    val response = json.decodeFromString(
                        deserializer = MessageReadSocketResponse.serializer(
                            typeSerial0 = MessageReadData.serializer()
                        ),
                        string = raw,
                    )

                    response.data?.let { readData ->
                        _events.emit(ChatWebSocketEvent.MessageRead(readData))
                    }
                }

                ChatSocketActions.MARK_DELIVERED,
                ChatSocketEvents.MESSAGE_DELIVERED -> {
                    val response = json.decodeFromString(
                        deserializer = MessageDeliveredSocketResponse.serializer(
                            typeSerial0 = MessageDeliveredData.serializer()
                        ),
                        string = raw,
                    )

                    response.data?.let { deliveredData ->
                        _events.emit(ChatWebSocketEvent.MessageDelivered(deliveredData))
                    }
                }

                ChatSocketActions.REACT_TO_MESSAGE,
                ChatSocketEvents.MESSAGE_REACTED -> {
                    val response = json.decodeFromString(
                        deserializer = ReactionSocketResponse.serializer(
                            typeSerial0 = ReactionData.serializer()
                        ),
                        string = raw,
                    )

                    response.data?.let { reactionData ->
                        _events.emit(ChatWebSocketEvent.ReactToMessage(reactionData))
                    }
                }

                ChatSocketActions.GET_CHAT_ROOMS,
                ChatSocketEvents.CHAT_ROOMS_FETCHED -> {
                    val response = json.decodeFromString(
                        deserializer = ChatRoomsSocketResponse.serializer(
                            typeSerial0 = ChatRoomsData.serializer()
                        ),
                        string = raw,
                    )

                    AppLogger.log("Fetched chat rooms: ${response.data}")

                    _events.emit(
                        ChatWebSocketEvent.GetChatRooms(
                            data = response.data ?: ChatRoomsData()
                        )
                    )
                }

                ChatSocketEvents.CHAT_ROOM_UPDATED -> {
                    val response = json.decodeFromString(
                        deserializer = SocketEnvelope.serializer(ChatRoomItemDto.serializer()),
                        string = raw,
                    )

                    response.data?.let { updatedRoom ->
                        _events.emit(ChatWebSocketEvent.ChatRoomUpdated(updatedRoom))
                    }
                }

                else -> {
                    AppLogger.log("Unhandled websocket action: $action")
                }


            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            AppLogger.log("Error handling incoming websocket message: ${exception.message}")
            _events.emit(ChatWebSocketEvent.Error(exception))
        }
    }

    suspend fun fetchMessages(
        chatRoomId: String,
        cursor: Long? = null,
        limit: Int = DEFAULT_MESSAGE_PAGE_SIZE,
        requestId: String? = null,
    ) {
        try {
            send(
                rawPayload = ChatSocketRequests.fetchMessages(
                    chatRoomId = chatRoomId,
                    cursor = cursor,
                    limit = limit,
                    requestId = requestId,
                )
            )
        } catch (e: Exception) {
            AppLogger.log("Error fetching messages: ${e.message}")
            throw e
        }
    }

    suspend fun getChatRooms(
        limit: Int = DEFAULT_CHAT_ROOM_PAGE_SIZE,
        lastUpdatedAt: String? = null,
        requestId: String? = null,
    ) {
        try {
            send(
                rawPayload = ChatSocketRequests.getChatRooms(
                    limit = limit,
                    lastUpdated = lastUpdatedAt,
                    requestId = requestId,
                )
            )
        } catch (e: Exception) {
            AppLogger.log("Error fetching chat rooms: ${e.message}")
            throw e
        }
    }

    suspend fun sendMessage(
        chatRoomId: String,
        text: String? = null,
        messageType: String = "text",
        mediaIds: List<Long> = emptyList(),
        replyToId: Long? = null,
        mediaUrl: String? = null,
        stickerUrl: String? = null,
        requestId: String? = null,
    ) {
        send(
            rawPayload = ChatSocketRequests.sendMessage(
                chatRoomId = chatRoomId,
                text = text,
                messageType = messageType,
                mediaIds = mediaIds,
                replyToId = replyToId,
                mediaUrl = mediaUrl,
                stickerUrl = stickerUrl,
                requestId = requestId,
            )
        )
    }

    suspend fun editMessage(
        messageId: Long,
        text: String,
        requestId: String? = null,
    ) {
        send(
            rawPayload = ChatSocketRequests.editMessage(
                messageId = messageId,
                text = text,
                requestId = requestId,
            )
        )
    }

    suspend fun deleteMessage(
        messageId: Long,
        deleteOption: String,
        requestId: String? = null,
    ) {
        send(
            rawPayload = ChatSocketRequests.deleteMessage(
                messageId = messageId,
                deleteOption = deleteOption,
                requestId = requestId,
            )
        )
    }

    suspend fun markRead(
        chatRoomId: String,
        requestId: String? = null,
    ) {
        send(
            rawPayload = ChatSocketRequests.markRead(
                chatRoomId = chatRoomId,
                requestId = requestId,
            )
        )
    }

    suspend fun markDelivered(
        chatRoomId: String,
        requestId: String? = null,
    ) {
        send(
            rawPayload = ChatSocketRequests.markDelivered(
                chatRoomId = chatRoomId,
                requestId = requestId,
            )
        )
    }

    suspend fun reactToMessage(
        messageId: Long,
        reaction: String,
        requestId: String? = null,
    ) {
        send(
            rawPayload = ChatSocketRequests.reactToMessage(
                messageId = messageId,
                reaction = reaction,
                requestId = requestId,
            )
        )
    }

    suspend fun setTyping(
        chatRoomId: String,
        isTyping: Boolean,
        requestId: String? = null,
    ) {
        send(
            rawPayload = ChatSocketRequests.setTyping(
                chatRoomId = chatRoomId,
                isTyping = isTyping,
                requestId = requestId,
            )
        )
    }

    companion object {
        private const val DEFAULT_MESSAGE_PAGE_SIZE = 20
        private const val DEFAULT_CHAT_ROOM_PAGE_SIZE = 10
    }
}

object ChatSocketActions {
    const val SEND_MESSAGE = "send_message"
    const val FETCH_MESSAGES = "fetch_messages"
    const val EDIT_MESSAGE = "edit_message"
    const val DELETE_MESSAGE = "delete_message"
    const val MARK_READ = "mark_read"
    const val MARK_DELIVERED = "mark_delivered"
    const val REACT_TO_MESSAGE = "react_to_message"
    const val SET_TYPING = "set_typing"
    const val GET_CHAT_ROOMS = "get_chat_rooms"
}

object ChatSocketEvents {
    const val MESSAGE_CREATED = "message_created"
    const val MESSAGE_UPDATED = "message_updated"
    const val MESSAGE_DELETED = "message_deleted"
    const val MESSAGE_READ = "message_read"
    const val MESSAGE_DELIVERED = "message_delivered"
    const val MESSAGE_REACTED = "message_reacted"
    const val TYPING_UPDATED = "typing_updated"
    const val CHAT_ROOMS_FETCHED = "chat_rooms_fetched"
    const val CHAT_ROOM_UPDATED = "chat_room_updated"
}

object SocketFrameTypes {
    const val SUCCESS = "success"
    const val EVENT = "event"
    const val ERROR = "error"
}