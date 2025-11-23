package com.srisu.srisu.core.data.repository.chat

import com.srisu.srisu.core.data.response.chat.ChatMessage
import com.srisu.srisu.core.data.response.chat.ChatResponse
import com.srisu.srisu.core.data.websocket.ChatWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient
) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _messages = MutableStateFlow<List<ChatResponse.Data>>(emptyList())
    val messages: StateFlow<List<ChatResponse.Data>> = _messages

    private val messageList = mutableListOf<ChatResponse.Data>()

    init {
        observeWebSocketMessages()
    }

    /**
     * Collect parsed messages from WebSocketClient.
     * Handles both history (list) and real-time messages.
     */
    private fun observeWebSocketMessages() {
        repoScope.launch {
            webSocketClient.parsedMessages.collectLatest { incomingMsg ->
                val exists = messageList.any { it.id != null && it.id == incomingMsg?.id }

                if (!exists) {
                    messageList.add(incomingMsg ?: ChatResponse.Data())
                    _messages.value = messageList.sortedByDescending { it.timestamp }
                }
            }
        }
    }

    // ------------------------
    // Public control functions
    // ------------------------

    fun start() = webSocketClient.start()

    fun stop() = webSocketClient.stop()

    /**
     * Send a chat message via websocket using the refactored sendChatMessage()
     */
    suspend fun sendMessage(meId: Int, peerId: Int, text: String) {
        val newMessage = ChatMessage(
            id = null,                     // server generates
            sender = meId,
            receiver = peerId,
            text = text,
            timestamp = "",                // server-generated timestamp
            messageType = "TEXT",          // adjust if your server uses lowercase
            couple = 0,
        )

//        webSocketClient.fetchMessages(page = 1, pageSize = 20)
    }
}
