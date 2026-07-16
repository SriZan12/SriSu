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
import kotlinx.coroutines.flow.combine
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

    private val _activeChatRoomId = MutableStateFlow<String?>(null)
//    val activeChatRoomId: StateFlow<String?> = _activeChatRoomId.asStateFlow()

    /**
     * Per-room cache of messages.
     * Key = chatRoomId
     * Value = ordered message map for that room
     */
    private val roomMessageCache =
        MutableStateFlow<Map<String, LinkedHashMap<Long, ChatMessage>>>(emptyMap())

    /**
     * Visible messages for the currently active room only.
     */
    val messages: StateFlow<List<ChatMessage>> =
        combine(roomMessageCache, _activeChatRoomId) { cache, activeRoomId ->
            activeRoomId
                ?.let { cache[it]?.values?.toList() }
                .orEmpty()
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _chatRooms = MutableStateFlow<List<ChatRoomItemDto>>(emptyList())
    val chatRoomsList: StateFlow<List<ChatRoomItemDto>> = _chatRooms.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Message pagination is tracked for the active room only.
     */
    private val _messagePagination = MutableStateFlow(MessagePaginationState())
//    val messagePagination: StateFlow<MessagePaginationState> = _messagePagination.asStateFlow()

    private val _roomPagination = MutableStateFlow(ChatRoomPaginationState())
//    val roomPagination: StateFlow<ChatRoomPaginationState> = _roomPagination.asStateFlow()

    init {
        observeSocketEvents()
    }

    fun connect() {
        webSocketClient.connect()
    }

    fun disconnect(reason: String? = null) {
        webSocketClient.disconnect(reason)
    }

    fun clearError() {
        _error.value = null
    }

    fun setActiveChatRoom(chatRoomId: String?) {
        _activeChatRoomId.value = chatRoomId
        _messagePagination.value = MessagePaginationState()
    }

    fun clearActiveChatRoom() {
        _activeChatRoomId.value = null
        _messagePagination.value = MessagePaginationState()
    }

    suspend fun fetchInitialMessages(chatRoomId: String) {
        setActiveChatRoom(chatRoomId)
        clearMessagesForRoom(chatRoomId)

        webSocketClient.fetchMessages(
            chatRoomId = chatRoomId,
            cursor = null,
            limit = DEFAULT_MESSAGE_PAGE_SIZE,
        )
    }

    fun fetchOlderMessages(chatRoomId: String) {
        val activeRoomId = _activeChatRoomId.value
        if (activeRoomId != chatRoomId) return

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
        val activeRoomId = _activeChatRoomId.value

        webSocketClient.deleteMessage(
            messageId = messageId,
            deleteOption = deleteOption,
        )

        if (deleteOption == Constants.ChatConstants.DELETE_FOR_ME && activeRoomId != null) {
            removeMessageLocally(
                chatRoomId = activeRoomId,
                messageId = messageId,
            )
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

    fun addLocalMessage(message: ChatMessage) {
        val roomId = message.chatRoomId ?: return
        val id = message.id ?: return

        prependMessageToRoom(
            chatRoomId = roomId,
            messageId = id,
            message = message,
        )
    }

    fun clearMessagesForRoom(chatRoomId: String) {
        roomMessageCache.update { oldCache ->
            oldCache.toMutableMap().apply {
                remove(chatRoomId)
            }
        }
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
                        applyMessageEdit(
                            message = event.message,
                            chatRoom = event.chatRoom,
                        )
                    }

                    is ChatWebSocketEvent.MessageDeleted -> {
                        applyDeletedMessage(
                            message = event.message,
                            chatRoom = event.chatRoom,
                        )
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
//                        _error.value = event.throwable.message
                        _error.value = "Something went wrong!!"
                        AppLogger.log("Chat websocket error: ${event.throwable.message}")
                    }
                }
            }
        }
    }

    private fun updateRoomMessages(
        chatRoomId: String,
        transform: (LinkedHashMap<Long, ChatMessage>) -> LinkedHashMap<Long, ChatMessage>,
    ) {
        roomMessageCache.update { oldCache ->
            val currentMap = oldCache[chatRoomId] ?: linkedMapOf()
            oldCache.toMutableMap().apply {
                this[chatRoomId] = transform(currentMap)
            }
        }
    }

    private fun updateRoomMessagesInPlace(
        chatRoomId: String,
        transform: (LinkedHashMap<Long, ChatMessage>) -> Unit,
    ) {
        updateRoomMessages(chatRoomId) { oldMap ->
            LinkedHashMap(oldMap).apply {
                transform(this)
            }
        }
    }

    private fun prependMessageToRoom(
        chatRoomId: String,
        messageId: Long,
        message: ChatMessage,
    ) {
        updateRoomMessages(chatRoomId) { oldMap ->
            LinkedHashMap<Long, ChatMessage>(oldMap.size + 1).apply {
                put(messageId, message)
                oldMap.forEach { (existingId, existingMessage) ->
                    if (existingId != messageId) {
                        put(existingId, existingMessage)
                    }
                }
            }
        }
    }

    private fun upsertMessageInRoom(
        chatRoomId: String,
        message: ChatMessage,
    ) {
        val messageId = message.id ?: return

        updateRoomMessagesInPlace(chatRoomId) { map ->
            map[messageId] = message
        }
    }

    private fun removeMessageLocally(
        chatRoomId: String,
        messageId: Long,
    ) {
        updateRoomMessagesInPlace(chatRoomId) { map ->
            map.remove(messageId)
        }
    }

    private fun updateMessagesByIdsInRoom(
        chatRoomId: String,
        messageIds: List<Long>,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        if (messageIds.isEmpty()) return

        updateRoomMessagesInPlace(chatRoomId) { map ->
            messageIds.forEach { id ->
                val oldMessage = map[id] ?: return@forEach
                map[id] = transform(oldMessage)
            }
        }
    }

    private fun applyFetchedMessages(data: FetchMessagesData) {
        val roomId = data.chatRoomId ?: return

        if (_activeChatRoomId.value == roomId) {
            _messagePagination.value = MessagePaginationState(
                nextCursor = data.nextCursor,
                hasMore = data.hasMore,
            )
        }

        if (data.messages.isEmpty()) return

        updateRoomMessagesInPlace(roomId) { map ->
            data.messages.forEach { message ->
                val id = message.id ?: return@forEach
                map[id] = message
            }
        }
    }

    private fun handleIncomingMessageCreated(
        message: ChatMessage?,
        updatedChatRoom: ChatRoomItemDto?,
    ) {
        message ?: return
        val roomId = message.chatRoomId ?: return

        // Always update room preview / unread count globally.
        updatedChatRoom?.let(::upsertChatRoom)

        if (message.messageType == Constants.ChatConstants.IMAGE && !message.isLocalOnly) {
            replaceMatchingLocalMediaMessage(
                serverMessage = message,
                updatedChatRoom = null,
            )
        } else {
            val id = message.id ?: return
            prependMessageToRoom(
                chatRoomId = roomId,
                messageId = id,
                message = message,
            )
        }
    }

    private fun applyMessageEdit(
        message: ChatMessage?,
        chatRoom: ChatRoomItemDto?,
    ) {
        val roomId = message?.chatRoomId ?: return
        upsertMessageInRoom(chatRoomId = roomId, message)
        chatRoom?.let(block = ::upsertChatRoom)
    }

    private fun replaceMatchingLocalMediaMessage(
        serverMessage: ChatMessage,
        updatedChatRoom: ChatRoomItemDto?,
    ) {
        val roomId = serverMessage.chatRoomId ?: return
        val serverMessageId = serverMessage.id ?: return

        updateRoomMessages(roomId) { oldMap ->
            val mutable = LinkedHashMap(oldMap)

            val matchingLocalEntry = oldMap.entries.firstOrNull { (_, localMessage) ->
                localMessage.isLocalOnly &&
                        localMessage.messageType == serverMessage.messageType &&
                        localMessage.chatRoomId == roomId
            }

            matchingLocalEntry?.key?.let { localId ->
                mutable.remove(localId)
            }

            LinkedHashMap<Long, ChatMessage>(mutable.size + 1).apply {
                put(serverMessageId, serverMessage)
                mutable.forEach { (id, msg) ->
                    if (id != serverMessageId) {
                        put(id, msg)
                    }
                }
            }
        }

        updatedChatRoom?.let(::upsertChatRoom)
    }

    private fun applyFetchedChatRooms(data: ChatRoomsData) {
        _roomPagination.value = ChatRoomPaginationState(
            nextCursor = data.nextCursor,
            hasMore = !data.nextCursor.isNullOrBlank() ||
                    data.chatRooms.size >= DEFAULT_CHAT_ROOM_PAGE_SIZE,
        )

        if (data.chatRooms.isEmpty()) return

        _chatRooms.update { oldList ->
            if (oldList.isEmpty()) {
                data.chatRooms
            } else {
                (oldList + data.chatRooms).distinctBy { it.id }
            }
        }
    }

    private fun upsertChatRoom(chatRoom: ChatRoomItemDto) {
        if (chatRoom.otherUser?.id == currentUserId) {
            chatRoom.otherUser = chatRoom.user
        }
        _chatRooms.update { oldList ->
            listOf(chatRoom) + oldList.filterNot { it.id == chatRoom.id }
        }
    }

    private fun applyTypingUpdate(data: TypingData) {
        val chatRoomId = data.chatRoomId ?: return

        _chatRooms.update { oldList ->
            oldList.map { room ->
                if (room.id != chatRoomId) room
                else room.copy(isTyping = data.typingUsers)
            }
        }
    }

    private fun applyReadReceipt(data: MessageReadData) {
        val roomId = data.chatRoomId ?: return
        val me = currentUserId ?: return

        updateMessagesByIdsInRoom(roomId, data.messageIds) { message ->
            message.copy(
                isRead = true,
                isDelivered = true,
            )
        }

        _chatRooms.update { oldList ->
            oldList.map { room ->
                if (room.id != roomId) room
                else room.copy(
                    unreadCount = room.unreadCount + (me.toString() to 0)
                )
            }
        }
    }

    private fun applyDeliveredReceipt(data: MessageDeliveredData) {
        val roomId = data.chatRoomId ?: return

        updateMessagesByIdsInRoom(roomId, data.messageIds) { message ->
            message.copy(isDelivered = true)
        }
    }

    private fun applyReactionUpdate(data: ReactionData) {
        val messageId = data.messageId ?: return
        val userId = data.userId ?: return

        roomMessageCache.update { oldCache ->
            oldCache.toMutableMap().apply {
                entries.forEach { entry ->
                    val oldMessage = entry.value[messageId] ?: return@forEach
                    val updatedReactions = oldMessage.reactions.toMutableMap()

                    if (data.wasRemoved || data.reaction.isNullOrBlank()) {
                        updatedReactions.remove(userId.toString())
                    } else {
                        updatedReactions[userId.toString()] = data.reaction
                    }

                    entry.setValue(
                        LinkedHashMap(entry.value).apply {
                            this[messageId] = oldMessage.copy(
                                reactions = updatedReactions,
                            )
                        }
                    )
                }
            }
        }
    }

    private fun applyDeletedMessage(
        message: ChatMessage?,
        chatRoom: ChatRoomItemDto?,
    ) {
        message ?: return
        val roomId = message.chatRoomId ?: return

        val isDeletedForEveryone =
            message.isDeleted == true ||
                    message.deleteOption == Constants.ChatConstants.DELETE_FOR_EVERYONE

        if (isDeletedForEveryone) {
            upsertMessageInRoom(roomId, message)
            chatRoom?.let(::upsertChatRoom)
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