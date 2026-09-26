package com.srisu.srisu.features.chat.data.remote.websocket

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import com.srisu.srisu.features.chat.data.remote.response.*
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatCommandFailure(val failure: ApiError) : Exception(failure.message)
class ChatAcknowledgmentUnknown : Exception("Delivery is unconfirmed. Refresh the conversation before sending again.")
data class ScopedChatEvent(val session: SessionStamp, val event: ChatWebSocketEvent)

class ChatWebSocketClient(
    connector: SocketConnector,
    sessions: SessionCoordinator,
    lifetime: ApplicationLifetime,
) : BaseWebSocketClient(connector, sessions, lifetime) {
    private val _events = MutableSharedFlow<ScopedChatEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()
    private data class Pending(val action: String, val acknowledgment: CompletableDeferred<Unit>)
    private val pending = mutableMapOf<String, Pending>()
    private val seen = LinkedHashSet<String>()

    override suspend fun onConnected(stamp: SessionStamp) {
        seen.clear()
        emit(stamp, ChatWebSocketEvent.Connected)
    }
    override suspend fun onDisconnected(stamp: SessionStamp) {
        pending.values.forEach { it.acknowledgment.completeExceptionally(ChatAcknowledgmentUnknown()) }
        pending.clear()
        emit(stamp, ChatWebSocketEvent.Disconnected(null))
    }
    private suspend fun emit(stamp: SessionStamp, event: ChatWebSocketEvent) {
        if (sessions.stamp() == stamp) _events.emit(ScopedChatEvent(stamp, event))
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun sendAcknowledged(rawPayload: String) = withContext(scope.coroutineContext.minusKey(Job)) {
        val root = ApiJson.parseToJsonElement(rawPayload).jsonObject
        val id = (root["request_id"] as? JsonPrimitive)?.contentOrNull ?: Uuid.random().toString()
        if (pending.size >= 32) throw SocketUnavailable()
        require(id !in pending) { "Duplicate in-flight request identifier" }
        val acknowledgment = CompletableDeferred<Unit>()
        pending[id] = Pending(root["action"]!!.jsonPrimitive.content, acknowledgment)
        try {
            try { send(JsonObject(root + ("request_id" to JsonPrimitive(id))).toString()) }
            catch (_: TimeoutCancellationException) { throw ChatAcknowledgmentUnknown() }
            try { withTimeout(10_000) { acknowledgment.await() } }
            catch (_: TimeoutCancellationException) { throw ChatAcknowledgmentUnknown() }
        } finally { pending.remove(id) }
    }

    override suspend fun onIncoming(raw: String, stamp: SessionStamp) {
        try {
            val root = ApiJson.parseToJsonElement(raw).jsonObject
            val type = (root["type"] as? JsonPrimitive)?.contentOrNull
            val action = (root["action"] as? JsonPrimitive)?.contentOrNull
            val id = (root["request_id"] as? JsonPrimitive)?.contentOrNull
            val version = (root["protocol_version"] as? JsonPrimitive)?.intOrNull
            if (version != null && version != 1) { emit(stamp, ChatWebSocketEvent.Resync); return }
            if (type == "error") {
                val code = ((root["error"] as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
                val status = when(code) { "unauthenticated" -> 401; "forbidden" -> 403; "not_found" -> 404; "rate_limited" -> 429; "server_error" -> 500; else -> 400 }
                val failure = decodeApiError(status, raw)
                if (id != null) pending[id]?.takeIf { it.action == action }?.acknowledgment?.completeExceptionally(ChatCommandFailure(failure))
                emit(stamp, ChatWebSocketEvent.Error(ChatCommandFailure(failure)))
                return
            }
            if (type !in listOf("success", "event")) { emit(stamp, ChatWebSocketEvent.Resync); return }
            val eventId = (root["event_id"] as? JsonPrimitive)?.contentOrNull
            if (type == "event" && eventId != null) {
                if (!seen.add(eventId)) return
                if (seen.size > 256) seen.remove(seen.first())
            }
            if (action == "unsubscribe_room") { if (type == "success" && id != null) pending[id]?.takeIf { it.action == action }?.acknowledgment?.complete(Unit); return }
            val data = root["data"] ?: throw SerializationException("Missing socket data")
            val event = when(action) {
                "fetch_messages" -> ChatWebSocketEvent.FetchMessages(ApiJson.decodeFromJsonElement<FetchMessagesData>(data))
                "get_chat_rooms", "chat_rooms_fetched" -> ChatWebSocketEvent.GetChatRooms(ApiJson.decodeFromJsonElement<ChatRoomsData>(data))
                "send_message", "message_created" -> ApiJson.decodeFromJsonElement<MessageMutationData>(data).let { ChatWebSocketEvent.SendMessage(it.message, it.chatRoom) }
                "edit_message", "message_updated" -> ApiJson.decodeFromJsonElement<MessageMutationData>(data).let { ChatWebSocketEvent.MessageEdited(it.message, it.chatRoom) }
                "delete_message", "message_deleted" -> ApiJson.decodeFromJsonElement<MessageMutationData>(data).let { ChatWebSocketEvent.MessageDeleted(it.message, it.chatRoom) }
                "set_typing", "typing_updated" -> ChatWebSocketEvent.MessageTyping(ApiJson.decodeFromJsonElement<TypingData>(data))
                "mark_read", "message_read" -> ChatWebSocketEvent.MessageRead(ApiJson.decodeFromJsonElement<MessageReadData>(data))
                "mark_delivered", "message_delivered" -> ChatWebSocketEvent.MessageDelivered(ApiJson.decodeFromJsonElement<MessageDeliveredData>(data))
                "react_to_message", "message_reacted" -> ChatWebSocketEvent.ReactToMessage(ApiJson.decodeFromJsonElement<ReactionData>(data))
                "chat_room_updated" -> ChatWebSocketEvent.ChatRoomUpdated(ApiJson.decodeFromJsonElement<ChatRoomItemDto>(data))
                "access_revoked" -> ChatWebSocketEvent.AccessRevoked((data.jsonObject["chat_room_id"] as? JsonPrimitive)?.contentOrNull ?: throw SerializationException("Missing room"))
                else -> ChatWebSocketEvent.Resync
            }
            val mutation = when (event) {
                is ChatWebSocketEvent.SendMessage -> event.message
                is ChatWebSocketEvent.MessageEdited -> event.message
                is ChatWebSocketEvent.MessageDeleted -> event.message
                else -> null
            }
            if (event is ChatWebSocketEvent.SendMessage || event is ChatWebSocketEvent.MessageEdited || event is ChatWebSocketEvent.MessageDeleted) {
                if (mutation?.id == null || mutation.id <= 0 || mutation.chatRoomId.isNullOrBlank()) throw SerializationException("Invalid message identity")
            }
            if (type == "success" && id != null && event != ChatWebSocketEvent.Resync) pending[id]?.takeIf { it.action == action }?.acknowledgment?.complete(Unit)
            emit(stamp, event)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SerializationException) { emit(stamp, ChatWebSocketEvent.Resync) }
        catch (_: IllegalArgumentException) { emit(stamp, ChatWebSocketEvent.Resync) }
    }

    suspend fun unsubscribe(roomId: String) = sendAcknowledged(
        buildJsonObject { put("action", "unsubscribe_room"); put("payload", buildJsonObject { put("chat_room_id", roomId) }) }.toString()
    )

    suspend fun fetchMessages(
        chatRoomId: String,
        cursor: Long? = null,
        limit: Int = DEFAULT_MESSAGE_PAGE_SIZE,
        requestId: String? = null,
    ) {
        try {
            sendAcknowledged(
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
            sendAcknowledged(
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
        sendAcknowledged(
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
        sendAcknowledged(
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
        sendAcknowledged(
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
        sendAcknowledged(
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
        sendAcknowledged(
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
        sendAcknowledged(
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
        sendAcknowledged(
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