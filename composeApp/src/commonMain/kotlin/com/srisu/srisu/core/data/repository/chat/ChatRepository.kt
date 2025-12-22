package com.srisu.srisu.core.data.repository.chat


import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.dto.chatdto.FetchMessageDTO
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.websocket.chat.ChatEvent
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.let

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messageMap = MutableStateFlow<LinkedHashMap<Int, ChatMessage>>(linkedMapOf())
    val messages: StateFlow<List<ChatMessage>> =
        messageMap.map { it.values.toList() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                    is ChatEvent.MessageDeleted -> deleteMessage(event.messageId)
                    is ChatEvent.Error -> _error.value = event.throwable.message
                    else -> Unit
                }
            }
        }
    }

    private fun applyFetchMessages(messages: FetchMessageResponse?) {
        val messageList = messages?.chatMessage?.results ?: return

        // Process metadata
        nextCursor = messageList.lastOrNull()?.id?.toLong()
        hasMore = messageList.size >= 20
        AppLogger.log("Next Cursor = $nextCursor")

        messageMap.update { oldMap ->
            // Create updates, ensuring IDs are not null
            val updates = messageList.filterNotNull()
                .associateBy { it.id!! } // !! is safe here because of filterNotNull()

            // Merge and explicitly cast to LinkedHashMap
            (oldMap + updates).toMutableMap() as LinkedHashMap<Int, ChatMessage>
        }
    }


    private fun prependMessage(message: ChatMessage) {
        val id = message.id ?: return

        messageMap.update { oldMap ->
            // Put the new message first, then the old ones
            (mapOf(id to message) + oldMap).toMutableMap() as LinkedHashMap<Int, ChatMessage>
        }
    }


    private fun updateMessage(message: ChatMessage) {
        message.id?.let { id ->
            messageMap.update { oldMap ->
                oldMap.apply { put(id, message) }
            }
        }
    }

    private fun deleteMessage(messageId: Int?) {
        messageId ?: return
        messageMap.update { oldMap ->
            oldMap.apply { remove(key = messageId) }
        }
    }

    fun fetchOlderMessages() {
        if (!hasMore) return
        scope.launch {
            val fetchPayload = FetchMessageDTO(
                action = "fetch_messages",
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

    fun clearError() {
        _error.value = null
    }
}

