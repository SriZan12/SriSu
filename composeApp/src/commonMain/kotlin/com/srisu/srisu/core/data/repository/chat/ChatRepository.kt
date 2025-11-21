package com.srisu.srisu.core.data.repository.chat


// ChatRepository.kt
import com.srisu.srisu.core.data.response.chat.ChatMessage
import com.srisu.srisu.core.data.websocket.ChatWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val messageList = mutableListOf<ChatMessage>()

    init {
        startListening()
    }

    private fun startListening() {
        CoroutineScope(Dispatchers.IO).launch {
            webSocketClient.parsedMessages.collect { message ->
                if (!messageList.any { it.id == message.id }) {
                    messageList.add(0, message) // newest first? or sort later
                    _messages.emit(messageList.sortedByDescending { it.timestamp })
                }
            }
        }
    }

    fun start() = webSocketClient.start()
    fun stop() = webSocketClient.stop()

    suspend fun sendMessage(meId: Int, peerId: Int, text: String) {
        val chatMessage = ChatMessage(
            id = null,
            sender = meId,
            receiver = peerId,
            text = text,
            messageType = "TEXT",
            couple = 8,
            timestamp = "", // will be set by server
            // fill others as needed
        )
        webSocketClient.sendChatMessage(chatMessage)
    }
}
