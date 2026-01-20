package com.srisu.srisu.core.data.repository.chat

import com.srisu.srisu.core.data.apiservice.chat.ChatApiService
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.dto.chatdto.ChatRoom
import com.srisu.srisu.core.data.dto.chatdto.FetchMessageDTO
import com.srisu.srisu.core.data.network.ResultHandler
import com.srisu.srisu.core.data.response.chat.ChatMediaResponse
import com.srisu.srisu.core.data.response.chat.ChatRoomResponse
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.response.chat.MessageDeliveredResponse
import com.srisu.srisu.core.data.response.chat.MessageReadResponse
import com.srisu.srisu.core.data.response.chat.TypingResponse
import com.srisu.srisu.core.data.websocket.chat.ChatRoomEvent
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.session.SessionUtils
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_ME
import com.srisu.srisu.utils.Constants.ChatConstants.FETCH_MESSAGES
import com.srisu.srisu.utils.Constants.ChatConstants.IMAGE
import com.srisu.srisu.utils.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.let

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient,
    private val chatApiService: ChatApiService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messageMap = MutableStateFlow<LinkedHashMap<Long, ChatMessage>>(linkedMapOf())
    val messages: StateFlow<List<ChatMessage>> =
        messageMap.map { it.values.toList() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatRoom = MutableStateFlow<ChatRoom?>(ChatRoom())
    val chatRoom = _chatRoom.asStateFlow()

    private val _chatRoomsList =
        MutableStateFlow<List<ChatRoomResponse.Data.ChatRoom?>?>(emptyList())
    val chatRoomsList = _chatRoomsList.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var nextCursor: Long? = null
    private var hasMore: Boolean = true

    init {
        webSocketClient.connect(roomId = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4")
        scope.launch {
            webSocketClient.events.collect { event ->
                when (event) {
                    is ChatRoomEvent.FetchMessages -> applyFetchMessages(messages = event.messages)
                    is ChatRoomEvent.SendMessage -> {
                        if (event.message.messageType == IMAGE) {
                            replaceLocalMediaMessage(event.message)
                        } else {
                            prependMessage(event.message)
                        }
                    }

                    is ChatRoomEvent.MessageEdited -> updateMessage(event.message)
                    is ChatRoomEvent.MessageDeleted -> deleteMessage(message = event.message)
                    is ChatRoomEvent.MessageTyping -> updateTyping(typingResponse = event.typingResponse)
                    is ChatRoomEvent.MessageRead -> updateMessageRead(messageReadResponse = event.messageReadResponse)
                    is ChatRoomEvent.MessageDelivered -> updateMessageDelivered(
                        messageDeliveredResponse = event.messageDeliveredResponse
                    )

                    is ChatRoomEvent.ReactToMessage -> updateMessage(event.reactToMessage)
                    is ChatRoomEvent.Error -> _error.value = event.throwable.message
                    is ChatRoomEvent.GetChatRooms -> updateChatRooms(chatRoomResponse = event.chatRoomResponse)
                    else -> Unit
                }
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

            AppLogger.log("Inside applyFetchMessages")
        } catch (exception: Exception) {
            AppLogger.log("Exception: ${exception.message}")
        }

    }


    fun prependMessage(message: ChatMessage) {
        val id = message.id ?: return

        messageMap.update { oldMap ->
            // Put the new message first, then the old ones
            (mapOf(id to message) + oldMap).toMutableMap() as LinkedHashMap<Long, ChatMessage>
        }

    }


    private fun updateMessage(message: ChatMessage?) {
        val id = message?.id ?: return
        messageMap.update { oldMap ->
            // Returns a NEW map instance
            (oldMap + (id to message)) as LinkedHashMap<Long, ChatMessage>
        }
    }


    private fun replaceLocalMediaMessage(message: ChatMessage) {
        message.id ?: return
        messageMap.update { oldMap ->
            // Find and remove the local photo message
            val localPhotoMessage = oldMap.values.find { it.isLocalOnly }
            val mutableMap = LinkedHashMap(oldMap)

            if (localPhotoMessage != null) {
                mutableMap.remove(key = localPhotoMessage.id)
            }

            mutableMap
        }

        prependMessage(message)
    }

    private fun deleteMessage(message: ChatMessage?) {
        message ?: return

        val messageId = message.id ?: return

        val deleteForMap = message.deleteFor
        if (deleteForMap != null) {
            // If "me" (or current user) is already in the delete_for list → fully remove from local map
            val currentUserId = SessionUtils().getCurrentUserId()
            val hasBeenDeletedForMe = deleteForMap.values
                .flatten()
                .any { action -> action.option == DELETE_FOR_ME && action.user_id == currentUserId }

            if (hasBeenDeletedForMe) {
                messageMap.update { oldMap ->
                    (oldMap - messageId) as LinkedHashMap<Long, ChatMessage>
                }
            } else {
                updateMessage(message = message)
            }
        }

    }

    private fun updateTyping(typingResponse: TypingResponse) {
        AppLogger.log("Inside repo updateTyping: $typingResponse")
        _chatRoom.update { currentRoom ->
            AppLogger.log("Current room before update: $currentRoom")
            currentRoom?.copy(
                isTyping = typingResponse
            )
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


    fun fetchOlderMessages() {
        if (!hasMore) return
        scope.launch {
            val fetchPayload = FetchMessageDTO(
                action = FETCH_MESSAGES,
                page = nextCursor,
                page_size = 50
            )
            webSocketClient.send(Json.encodeToString(fetchPayload))
        }
    }

    fun updateChatRooms(chatRoomResponse: ChatRoomResponse?) {
        chatRoomResponse ?: return
        _chatRoomsList.value = chatRoomResponse.data?.chatRooms
    }

    suspend fun sendRequest(payload: String?) {
        payload ?: return
        webSocketClient.send(payload)
    }

    suspend fun uploadMedias(
        medias: List<MediaFile?>?
    ): ResultHandler<ChatMediaResponse?> {
        return chatApiService.uploadMedias(
            medias = medias
        )
    }


    fun clearError() {
        _error.value = null
    }
}

