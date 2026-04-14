package com.srisu.srisu.features.chat.data.remote.api

import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.chat.data.remote.response.ChatMediaResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketEvent
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.core.session.SessionUtils
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomItemDto
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomsData
import com.srisu.srisu.features.chat.data.remote.response.FetchMessagesData
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredData
import com.srisu.srisu.features.chat.data.remote.response.MessageReadData
import com.srisu.srisu.features.chat.data.remote.response.ReactionData
import com.srisu.srisu.features.chat.data.remote.response.TypingData
import com.srisu.srisu.utils.Constants
import com.srisu.srisu.utils.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient,
    private val chatApiService: ChatApiService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val currentUserId: Long? = SessionUtils().getCurrentUserId()

    private val messageMap = MutableStateFlow(LinkedHashMap<Long, ChatMessage>())
    val messages: StateFlow<List<ChatMessage>> =
        messageMap
            .map { it.values.toList() }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private val _chatRooms = MutableStateFlow<List<ChatRoomItemDto>>(emptyList())
    val chatRoomsList: StateFlow<List<ChatRoomItemDto>> = _chatRooms.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _messagePagination = MutableStateFlow(MessagePaginationState())
    val messagePagination: StateFlow<MessagePaginationState> = _messagePagination.asStateFlow()

    private val _roomPagination = MutableStateFlow(ChatRoomPaginationState())
    val roomPagination: StateFlow<ChatRoomPaginationState> = _roomPagination.asStateFlow()

    init {
        observeSocketEvents()
    }

    fun connect() {
        webSocketClient.connect()
    }

    fun disconnect(reason: String? = null) {
        webSocketClient.disconnect(reason)
    }

    private fun observeSocketEvents() {
        scope.launch {
            webSocketClient.events.collect { event ->
                when (event) {
                    is ChatWebSocketEvent.Connected -> {
                        AppLogger.log("Chat websocket connected")
                    }

                    is ChatWebSocketEvent.Disconnected -> {
                        AppLogger.log("Chat websocket disconnected: ${event.reason}")
                    }

                    is ChatWebSocketEvent.FetchMessages -> {
                        applyFetchedMessages(event.data)
                    }

                    is ChatWebSocketEvent.SendMessage -> {
                        handleIncomingMessageCreated(
                            message = event.message,
                            updatedChatRoom = event.updatedChatRoom,
                        )
                    }

                    is ChatWebSocketEvent.MessageEdited -> {
                        upsertMessage(event.message)
                    }

                    is ChatWebSocketEvent.MessageDeleted -> {
                        applyDeletedMessage(event.message)
                    }

                    is ChatWebSocketEvent.MessageTyping -> {
                        applyTypingUpdate(event.data)
                    }

                    is ChatWebSocketEvent.MessageRead -> {
                        applyReadReceipt(event.data)
                    }

                    is ChatWebSocketEvent.MessageDelivered -> {
                        applyDeliveredReceipt(event.data)
                    }

                    is ChatWebSocketEvent.ReactToMessage -> {
                        applyReactionUpdate(event.data)
                    }

                    is ChatWebSocketEvent.GetChatRooms -> {
                        applyFetchedChatRooms(event.data)
                    }

                    is ChatWebSocketEvent.ChatRoomUpdated -> {
                        upsertChatRoom(event.chatRoom)
                    }

                    is ChatWebSocketEvent.Error -> {
                        _error.value = event.throwable.message
                    }
                }
            }
        }
    }

    suspend fun fetchInitialMessages(chatRoomId: String) {
        clearMessages()
        _messagePagination.value = MessagePaginationState()

        webSocketClient.fetchMessages(
            chatRoomId = chatRoomId,
            cursor = null,
            limit = DEFAULT_MESSAGE_PAGE_SIZE,
        )
    }

    fun fetchOlderMessages(chatRoomId: String) {
        val pagination = _messagePagination.value
        if (!pagination.hasMore) return

        scope.launch {
            webSocketClient.fetchMessages(
                chatRoomId = chatRoomId,
                cursor = pagination.nextCursor,
                limit = DEFAULT_MESSAGE_PAGE_SIZE,
            )
        }
    }

    suspend fun fetchInitialChatRooms() {
        _chatRooms.value = emptyList()
        _roomPagination.value = ChatRoomPaginationState()

        webSocketClient.getChatRooms(
            limit = DEFAULT_CHAT_ROOM_PAGE_SIZE,
            lastUpdatedAt = null,
        )
    }

    fun fetchOlderChatRooms() {
        val pagination = _roomPagination.value
        if (!pagination.hasMore) return

        scope.launch {
            webSocketClient.getChatRooms(
                limit = DEFAULT_CHAT_ROOM_PAGE_SIZE,
                lastUpdatedAt = pagination.nextCursor,
            )
        }
    }

    suspend fun sendMessage(
        chatRoomId: String,
        text: String? = null,
        messageType: String = Constants.ChatConstants.TEXT,
        mediaIds: List<Long> = emptyList(),
        replyToId: Long? = null,
        mediaUrl: String? = null,
        stickerUrl: String? = null,
    ) {
        webSocketClient.sendMessage(
            chatRoomId = chatRoomId,
            text = text,
            messageType = messageType,
            mediaIds = mediaIds,
            replyToId = replyToId,
            mediaUrl = mediaUrl,
            stickerUrl = stickerUrl,
        )
    }

    suspend fun editMessage(
        messageId: Long,
        text: String,
    ) {
        webSocketClient.editMessage(
            messageId = messageId,
            text = text,
        )
    }

    suspend fun deleteMessage(
        messageId: Long,
        deleteOption: String,
    ) {
        webSocketClient.deleteMessage(
            messageId = messageId,
            deleteOption = deleteOption,
        )

        if (deleteOption == Constants.ChatConstants.DELETE_FOR_ME) {
            removeMessageLocally(messageId)
        }
    }

    suspend fun markRead(chatRoomId: String) {
        webSocketClient.markRead(chatRoomId)
    }

    suspend fun markDelivered(chatRoomId: String) {
        webSocketClient.markDelivered(chatRoomId)
    }

    suspend fun reactToMessage(
        messageId: Long,
        reaction: String,
    ) {
        webSocketClient.reactToMessage(
            messageId = messageId,
            reaction = reaction,
        )
    }

    suspend fun setTyping(
        chatRoomId: String,
        isTyping: Boolean,
    ) {
        webSocketClient.setTyping(
            chatRoomId = chatRoomId,
            isTyping = isTyping,
        )
    }

    suspend fun uploadMedias(
        medias: List<MediaFile?>?,
    ): ResultHandler<ChatMediaResponse?> {
        return chatApiService.uploadMedias(medias = medias)
    }

    fun clearMessages() {
        messageMap.value = linkedMapOf()
    }

    fun clearError() {
        _error.value = null
    }

    private fun applyFetchedMessages(data: FetchMessagesData) {
        val fetchedMessages = data.messages

        _messagePagination.value = MessagePaginationState(
            nextCursor = data.nextCursor,
            hasMore = data.hasMore,
        )

        messageMap.update { oldMap ->
            val merged = LinkedHashMap(oldMap)
            fetchedMessages.forEach { message ->
                val id = message.id ?: return@forEach
                merged[id] = message
            }
            merged
        }
    }

    private fun handleIncomingMessageCreated(
        message: ChatMessage?,
        updatedChatRoom: ChatRoomItemDto?,
    ) {
        message ?: return

        if (message.messageType == Constants.ChatConstants.IMAGE && !message.isLocalOnly) {
            replaceMatchingLocalMediaMessage(
                serverMessage = message,
                updatedChatRoom = updatedChatRoom,
            )
        } else {
            prependMessage(
                message = message,
                updatedChatRoom = updatedChatRoom,
            )
        }
    }

    private fun prependMessage(
        message: ChatMessage,
        updatedChatRoom: ChatRoomItemDto?,
    ) {
        val id = message.id ?: return

        messageMap.update { oldMap ->
            LinkedHashMap<Long, ChatMessage>().apply {
                put(id, message)
                oldMap.forEach { (existingId, existingMessage) ->
                    if (existingId != id) {
                        put(existingId, existingMessage)
                    }
                }
            }
        }

        updatedChatRoom?.let { upsertChatRoom(it) }
    }

    private fun upsertMessage(message: ChatMessage?) {
        val id = message?.id ?: return

        messageMap.update { oldMap ->
            LinkedHashMap(oldMap).apply {
                this[id] = message
            }
        }
    }

    private fun replaceMatchingLocalMediaMessage(
        serverMessage: ChatMessage,
        updatedChatRoom: ChatRoomItemDto?,
    ) {
        val serverMessageId = serverMessage.id ?: return

        messageMap.update { oldMap ->
            val mutable = LinkedHashMap(oldMap)

            val matchingLocalEntry = oldMap.entries.firstOrNull { (_, localMessage) ->
                localMessage.isLocalOnly &&
                        localMessage.messageType == serverMessage.messageType &&
                        localMessage.chatRoomId == serverMessage.chatRoomId
            }

            matchingLocalEntry?.key?.let { localId ->
                mutable.remove(localId)
            }

            LinkedHashMap<Long, ChatMessage>().apply {
                put(serverMessageId, serverMessage)
                mutable.forEach { (id, msg) ->
                    if (id != serverMessageId) {
                        put(id, msg)
                    }
                }
            }
        }

        updatedChatRoom?.let { upsertChatRoom(it) }
    }

    fun addLocalMessage(message: ChatMessage) {
        val id = message.id ?: return

        messageMap.update { oldMap ->
            LinkedHashMap<Long, ChatMessage>().apply {
                put(id, message)
                oldMap.forEach { (existingId, existingMessage) ->
                    if (existingId != id) {
                        put(existingId, existingMessage)
                    }
                }
            }
        }
    }

    private fun applyFetchedChatRooms(data: ChatRoomsData) {
        val fetchedRooms = data.chatRooms
        val nextCursor = data.nextCursor

        _roomPagination.value = ChatRoomPaginationState(
            nextCursor = nextCursor,
            hasMore = !nextCursor.isNullOrBlank() || fetchedRooms.size >= DEFAULT_CHAT_ROOM_PAGE_SIZE,
        )

        _chatRooms.update { oldList ->
            if (oldList.isEmpty()) {
                fetchedRooms
            } else {
                (oldList + fetchedRooms).distinctBy { it.id }
            }
        }
    }

    private fun upsertChatRoom(chatRoom: ChatRoomItemDto) {
        AppLogger.log("Chat Room updated!!!")
        _chatRooms.update { oldList ->
            listOf(chatRoom) + oldList.filterNot { it.id == chatRoom.id }
        }
    }

    private fun applyTypingUpdate(data: TypingData) {
        val chatRoomId = data.chatRoomId ?: return

        _chatRooms.update { oldList ->
            oldList.map { room ->
                if (room.id != chatRoomId) {
                    room
                } else {
                    room.copy(isTyping = data.typingUsers)
                }
            }
        }
    }

    private fun applyReadReceipt(data: MessageReadData) {
        val roomId = data.chatRoomId ?: return
        val readIds = data.messageIds

        messageMap.update { oldMap ->
            LinkedHashMap(oldMap).apply {
                readIds.forEach { id ->
                    val oldMessage = this[id] ?: return@forEach
                    this[id] = oldMessage.copy(
                        isRead = true,
                        isDelivered = true,
                    )
                }
            }
        }

        val me = currentUserId ?: return
        _chatRooms.update { oldList ->
            oldList.map { room ->
                if (room.id != roomId) {
                    room
                } else {
                    room.copy(
                        unreadCount = room.unreadCount + (me.toString() to 0)
                    )
                }
            }
        }
    }

    private fun applyDeliveredReceipt(data: MessageDeliveredData) {
        val deliveredIds = data.messageIds

        messageMap.update { oldMap ->
            LinkedHashMap(oldMap).apply {
                deliveredIds.forEach { id ->
                    val oldMessage = this[id] ?: return@forEach
                    this[id] = oldMessage.copy(isDelivered = true)
                }
            }
        }
    }

    private fun applyReactionUpdate(data: ReactionData) {
        val messageId = data.messageId ?: return
        val userId = data.userId ?: return

        messageMap.update { oldMap ->
            val oldMessage = oldMap[messageId] ?: return@update oldMap

            val updatedReactions = oldMessage.reactions.toMutableMap()
            if (data.wasRemoved || data.reaction.isNullOrBlank()) {
                updatedReactions.remove(userId.toString())
            } else {
                updatedReactions[userId.toString()] = data.reaction
            }

            LinkedHashMap(oldMap).apply {
                this[messageId] = oldMessage.copy(
                    reactions = updatedReactions
                )
            }
        }
    }

    private fun applyDeletedMessage(message: ChatMessage) {
        AppLogger.log("Inside applyDeletedMessage")
       message.id ?: return

        AppLogger.log("Chat message after deletion = $message")

        val isDeletedForEveryone =
            message.isDeleted == true ||
                    message.deleteOption == Constants.ChatConstants.DELETE_FOR_EVERYONE

        if (isDeletedForEveryone) {
            AppLogger.log("Delete for everyone -> updating message in list")
            upsertMessage(message)
            return
        }
    }

    private fun removeMessageLocally(messageId: Long) {
        messageMap.update { oldMap ->
            LinkedHashMap(oldMap).apply {
                remove(messageId)
            }
        }
    }

    companion object {
        private const val DEFAULT_MESSAGE_PAGE_SIZE = 20
        private const val DEFAULT_CHAT_ROOM_PAGE_SIZE = 10
    }
}

data class MessagePaginationState(
    val nextCursor: Long? = null,
    val hasMore: Boolean = true,
)

data class ChatRoomPaginationState(
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
)