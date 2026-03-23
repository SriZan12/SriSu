package com.srisu.srisu.features.chat.domain.repository

import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.features.chat.data.remote.response.ChatMediaResponse
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomResponse
import com.srisu.srisu.features.chat.data.remote.response.FetchMessageResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageReadResponse
import com.srisu.srisu.features.chat.data.remote.response.TypingResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.data.remote.api.ChatApiService
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.dto.ChatRoomDTO
import com.srisu.srisu.features.chat.data.remote.dto.FetchMessageDTO
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketEvent
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.session.SessionUtils
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
import kotlinx.serialization.json.Json

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient,
    private val chatApiService: ChatApiService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messageMap = MutableStateFlow<LinkedHashMap<Long, ChatMessage>>(linkedMapOf())
    val messages: StateFlow<List<ChatMessage>> =
        messageMap.map { it.values.toList() }
            .stateIn(scope, SharingStarted.Companion.WhileSubscribed(5000), emptyList())

    private val _chatRooms =
        MutableStateFlow<List<ChatRoomResponse.Data.ChatRoom>>(emptyList())

    val chatRoomsList = _chatRooms.asStateFlow()

    private var hasMoreChatRooms: Boolean = true


    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var nextCursor: Long? = null
    private var hasMore: Boolean = true
    private var chatRoomLastUpdateAt: String? = null
    private var chatRoomLastCursor: String? = null
    private var currentUserId: Long? = null


    init {
        currentUserId = SessionUtils().getCurrentUserId()
        webSocketClient.connect()
        scope.launch {
            webSocketClient.events.collect { event ->
                when (event) {
                    is ChatWebSocketEvent.FetchMessages -> applyFetchMessages(messages = event.messages)
                    is ChatWebSocketEvent.SendMessage -> {
                        if (event.message?.messageType == Constants.ChatConstants.IMAGE) {
                            replaceLocalMediaMessage(event.message, event.updatedChatRoom)
                        } else {
                            prependMessage(event.message, event.updatedChatRoom)
                        }
                    }

                    is ChatWebSocketEvent.MessageEdited -> updateMessage(event.message)
                    is ChatWebSocketEvent.MessageDeleted -> deleteMessage(message = event.message)
                    is ChatWebSocketEvent.MessageTyping -> updateTyping(typingResponse = event.typingResponse)
                    is ChatWebSocketEvent.MessageRead -> {
                        updateMessageRead(messageReadResponse = event.messageReadResponse)
                        updateMessageReadInChatRoom(messageReadResponse = event.messageReadResponse)
                    }

                    is ChatWebSocketEvent.MessageDelivered -> updateMessageDelivered(
                        messageDeliveredResponse = event.messageDeliveredResponse
                    )

                    is ChatWebSocketEvent.ReactToMessage -> updateMessage(event.reactToMessage)
                    is ChatWebSocketEvent.Error -> _error.value = event.throwable.message
                    is ChatWebSocketEvent.GetChatRooms -> applyOrAppendChatRooms(event.chatRoomResponse)
                    else -> Unit
                }
            }
        }
    }

    suspend fun sendRequest(payload: String?) {
        payload ?: return
        webSocketClient.send(rawPayload = payload)
    }

    suspend fun fetchInitialMessages(chatRoomId: String?) {
        webSocketClient.fetchMessages(chatRoomId = chatRoomId)
    }


    fun fetchOlderMessages(chatRoomId: String?) {
        if (!hasMore) return
        scope.launch {
            val fetchPayload = FetchMessageDTO(
                action = Constants.ChatConstants.FETCH_MESSAGES,
                page = nextCursor,
                page_size = 20,
                chatRoomId = chatRoomId
            )
            webSocketClient.send(rawPayload = Json.Default.encodeToString(fetchPayload))
        }
    }

    fun fetchNewChatRooms() {
        scope.launch {
            val chatRoomDTO = ChatRoomDTO(
                action = Constants.ChatConstants.GET_CHAT_ROOMS,
                limit = 10,
                lastUpdated = chatRoomLastUpdateAt ?: ""
            )
            webSocketClient.send(rawPayload = Json.Default.encodeToString(chatRoomDTO))
        }
    }


    suspend fun uploadMedias(
        medias: List<MediaFile?>?
    ): ResultHandler<ChatMediaResponse?> {
        return chatApiService.uploadMedias(
            medias = medias
        )
    }

    private fun applyChatRoomsList(chatRooms: List<ChatRoomResponse.Data.ChatRoom>) {
        _chatRooms.value = chatRooms
        hasMoreChatRooms = chatRooms.size >= 10
    }

    private fun appendChatRoomsList(chatRooms: List<ChatRoomResponse.Data.ChatRoom>) {
        _chatRooms.update { oldList ->
            (oldList.plus(chatRooms)).distinctBy { it.chatRoom?.id }
        }
        hasMoreChatRooms = chatRooms.size >= 10
    }

    private fun applyOrAppendChatRooms(chatRoomResponse: ChatRoomResponse?) {
        chatRoomResponse ?: return
        val newRooms = chatRoomResponse.data?.chatRooms?.filterNotNull() ?: return
        chatRoomLastCursor = chatRoomResponse.data.nextCursor

        if (_chatRooms.value.isEmpty()) {
            applyChatRoomsList(newRooms)
        } else {
            appendChatRoomsList(newRooms)
        }
    }

    private fun updateChatRoomOnMessage(
        updatedChatRoom: ChatRoomResponse.Data.ChatRoom.ChatRoom?
    ) {
        updatedChatRoom ?: return

        _chatRooms.update { oldList ->

            val existingRoom = oldList.firstOrNull {
                it.chatRoom?.id == updatedChatRoom.id
            }

            val updatedItem = ChatRoomResponse.Data.ChatRoom(
                chatRoom = updatedChatRoom,
                otherUser = existingRoom?.otherUser
            )

            listOf(updatedItem) + oldList.filter {
                it.chatRoom?.id != updatedChatRoom.id
            }
        }
    }

    private fun updateMessageReadInChatRoom(
        messageReadResponse: MessageReadResponse?
    ) {
        val chatRoomId = messageReadResponse?.data?.chatRoomId ?: return
        val currentUserId = currentUserId ?: return

        _chatRooms.update { chatRooms ->
            chatRooms.map { item ->
                val chatRoom = item.chatRoom

                if (chatRoom?.id != chatRoomId) return@map item

                item.copy(
                    chatRoom = chatRoom.copy(
                        unreadCount = chatRoom.unreadCount + (currentUserId.toString() to 0)
                    )
                )
            }
        }
    }

    fun updateTyping(typingResponse: TypingResponse) {
        val chatRoomId = typingResponse.typingData?.chatRoomId ?: return

        _chatRooms.update { chatRooms ->
            chatRooms.map { item ->
                val room = item.chatRoom

                if (room?.id == chatRoomId) {
                    item.copy(chatRoom = room.copy(isTyping = typingResponse))
                } else item
            }
        }
    }


    private fun applyFetchMessages(messages: FetchMessageResponse?) {
        val messageList = messages?.chatMessage?.results ?: return

        nextCursor = messageList.lastOrNull()?.id
        hasMore = messageList.size >= 20


        try {
            messageMap.update { oldMap ->
                // Create updates, ensuring IDs are not null
                val updates = messageList.filterNotNull()
                    .associateBy { it.id!! } // !! is safe here because of filterNotNull()

                // Merge and explicitly cast to LinkedHashMap
                (oldMap + updates).toMutableMap() as LinkedHashMap<Long, ChatMessage>
            }

        } catch (exception: Exception) {
            AppLogger.log("Exception: ${exception.message}")
        }

    }


    fun prependMessage(
        message: ChatMessage?,
        updatedChatRoom: ChatRoomResponse.Data.ChatRoom.ChatRoom?
    ) {
        val id = message?.id ?: return

        messageMap.update { oldMap ->
            // Put the new message first, then the old ones
            (mapOf(id to message) + oldMap).toMutableMap() as LinkedHashMap<Long, ChatMessage>
        }

        updateChatRoomOnMessage(updatedChatRoom = updatedChatRoom)

    }


    private fun updateMessage(message: ChatMessage?) {
        val id = message?.id ?: return
        messageMap.update { oldMap ->
            // Returns a NEW map instance
            (oldMap + (id to message)) as LinkedHashMap<Long, ChatMessage>
        }
    }


    private fun replaceLocalMediaMessage(
        message: ChatMessage,
        updatedChatRoom: ChatRoomResponse.Data.ChatRoom.ChatRoom?
    ) {
        val id = message.id ?: return

        messageMap.update { oldMap ->

            val mutableMap = LinkedHashMap(oldMap)

            // remove all local messages
            oldMap.values
                .filter { it.isLocalOnly }
                .forEach { local ->
                    local.id?.let { mutableMap.remove(it) }
                }

            // prepend server message
            LinkedHashMap<Long, ChatMessage>().apply {
                put(id, message)
                putAll(mutableMap)
            }
        }

        updateChatRoomOnMessage(updatedChatRoom)
    }

    private fun deleteMessage(message: ChatMessage?) {
        message ?: return

        val messageId = message.id ?: return

        val deleteForMap = message.deleteFor
        if (deleteForMap != null) {
            // If "me" (or current user) is already in the delete_for list → fully remove from local map
            val hasBeenDeletedForMe = deleteForMap.values
                .flatten()
                .any { action -> action.option == Constants.ChatConstants.DELETE_FOR_ME && action.user_id == currentUserId }

            if (hasBeenDeletedForMe) {
                messageMap.update { oldMap ->
                    (oldMap - messageId) as LinkedHashMap<Long, ChatMessage>
                }
            } else {
                updateMessage(message = message)
            }
        }

    }


    private fun updateMessageRead(messageReadResponse: MessageReadResponse?) {
        messageReadResponse?.let { response ->
            val messageReadIds = response.data?.messageIds ?: return

            messageMap.update { oldMap ->
                val updates: Map<Long, ChatMessage> =
                    messageReadIds.mapNotNull { id ->
                        id?.let { nonNullId ->
                            oldMap[nonNullId]?.let { message ->
                                nonNullId to message.copy(isRead = true)
                            }
                        }
                    }.toMap()

                LinkedHashMap<Long, ChatMessage>().apply {
                    putAll(oldMap)
                    putAll(updates) // overwrite only read messages
                }
            }
        }
    }


    private fun updateMessageDelivered(messageDeliveredResponse: MessageDeliveredResponse?) {
        messageDeliveredResponse?.let { response ->
            val messageDeliveredIds = response.data?.messageIds ?: return

            messageMap.update { oldMap ->
                val updates: Map<Long, ChatMessage> =
                    messageDeliveredIds.mapNotNull { id ->
                        id?.let { nonNullId ->
                            oldMap[nonNullId]?.let { message ->
                                nonNullId to message.copy(isDelivered = true)
                            }
                        }
                    }.toMap()

                LinkedHashMap<Long, ChatMessage>().apply {
                    putAll(oldMap)
                    putAll(updates) // overwrite only read messages
                }
            }
        }
    }


    fun clearError() {
        _error.value = null
    }
}