package com.srisu.srisu.core.data.repository.chat


import androidx.compose.ui.text.input.TextFieldValue
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.dto.chatdto.ChatRoom
import com.srisu.srisu.core.data.dto.chatdto.FetchMessageDTO
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.response.chat.TypingResponse
import com.srisu.srisu.core.data.websocket.chat.ChatEvent
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.session.SessionUtils
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_ME
import com.srisu.srisu.utils.Constants.ChatConstants.FETCH_MESSAGES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messageMap = MutableStateFlow<LinkedHashMap<Long, ChatMessage>>(linkedMapOf())
    val messages: StateFlow<List<ChatMessage>> =
        messageMap.map { it.values.toList() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatRoom = MutableStateFlow<ChatRoom?>(null)
    val chatRoom = _chatRoom.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var nextCursor: Long? = null
    private var hasMore: Boolean = true

    init {
        webSocketClient.connect(roomId = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4")
        scope.launch {
            webSocketClient.events.collect { event ->
                when (event) {
                    is ChatEvent.FetchMessages -> applyFetchMessages(messages = event.messages)
                    is ChatEvent.SendMessage -> prependMessage(event.message)
                    is ChatEvent.MessageEdited -> updateMessage(event.message)
                    is ChatEvent.MessageDeleted -> deleteMessage(message = event.message)
                    is ChatEvent.MessageTyping -> updateTyping(typingResponse = event.typingResponse)
                    is ChatEvent.Error -> _error.value = event.throwable.message
                    else -> Unit
                }
            }
        }
    }

    private fun applyFetchMessages(messages: FetchMessageResponse?) {
        val messageList = messages?.chatMessage?.results ?: return

        nextCursor = messageList.lastOrNull()?.id
        hasMore = messageList.size >= 20

        AppLogger.log("Apply Fetch Messages = ${messageList.size}")

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


    private fun prependMessage(message: ChatMessage) {
        val id = message.id ?: return

        messageMap.update { oldMap ->
            // Put the new message first, then the old ones
            (mapOf(id to message) + oldMap).toMutableMap() as LinkedHashMap<Long, ChatMessage>
        }
    }


    private fun updateMessage(message: ChatMessage) {
        val id = message.id ?: return
        messageMap.update { oldMap ->
            // Returns a NEW map instance
            (oldMap + (id to message)) as LinkedHashMap<Long, ChatMessage>
        }
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
        val typingUsers = typingResponse.typing_users

        _chatRoom.update { currentRoom ->
            currentRoom?.copy(
                isTyping = typingUsers?.associateWith { true } // All users typing -> true
            )
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

    suspend fun sendRequest(chatMessage: ChatMessage?) {
        chatMessage ?: return
        webSocketClient.send(Json.encodeToString(chatMessage))
    }

    suspend fun sendTypingRequest(payload: String) {
        webSocketClient.send(payload)
    }


    fun clearError() {
        _error.value = null
    }
}

