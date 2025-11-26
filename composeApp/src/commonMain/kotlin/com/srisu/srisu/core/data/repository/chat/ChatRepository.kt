package com.srisu.srisu.core.data.repository.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
import com.srisu.srisu.core.data.websocket.chat.ConnectionState
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient
) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Messages state
    private val _messages =
        MutableStateFlow<List<FetchMessageResponse.ChatMessage.Result?>>(emptyList())
    val messages: StateFlow<List<FetchMessageResponse.ChatMessage.Result?>> =
        _messages.asStateFlow()

    // Connection state exposed to UI
    val connectionState = webSocketClient.connectionState
        .stateIn(
            scope = repoScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.Disconnected
        )

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var isCollecting = false

    // -----------------------------
    // Lifecycle Management
    // -----------------------------

    /**
     * Start WebSocket connection and begin collecting messages
     */
    fun start() {
        if (isCollecting) {
            AppLogger.log("Repository already collecting messages")
            return
        }

        isCollecting = true
        webSocketClient.connect()
        startCollectingMessages()
    }

    /**
     * Stop WebSocket connection and message collection
     */
    fun stop() {
        isCollecting = false
        webSocketClient.disconnect()
    }

    // -----------------------------
    // Message Collection
    // -----------------------------

    private fun startCollectingMessages() {
        repoScope.launch {
            webSocketClient.chatMessages.collect { incomingMessage ->
                handleIncomingMessage(incomingMessage)
            }
        }
    }

    private fun handleIncomingMessage(message: List<FetchMessageResponse.ChatMessage.Result?>?) {
        val currentMessages = _messages.value.toMutableList()

        message?.filterNotNull()?.let { currentMessages.addAll(it) }

        _messages.value = currentMessages
    }


    // -----------------------------
    // Public Actions
    // -----------------------------

    /**
     * Send a text message through WebSocket
     */
    suspend fun sendMessage(senderId: Int, receiverId: Int, text: String) {
        if (text.isBlank()) {
            AppLogger.log("Cannot send empty message")
            return
        }

        try {
            val message = ChatMessage(
                id = null,  // Server generates ID
                senderId = senderId,
                receiverId = receiverId,
                text = text.trim(),
                timestamp = "",  // Server generates timestamp
                messageType = "TEXT",
                couple = 0
            )

            webSocketClient.sendMessage(message)
        } catch (e: Exception) {
            AppLogger.log("Error sending message: ${e.message}")
            throw e
        }
    }

    /**
     * Fetch message history
     */
    suspend fun fetchMessages(page: Int = 1, pageSize: Int = 20) {
        try {
            _isLoading.value = true
            webSocketClient.fetchMessages(page, pageSize)
        } catch (e: Exception) {
            AppLogger.log("Error fetching messages: ${e.message}")
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Clear local messages (useful for logout/room switch)
     */
    fun clearMessages() {
        _messages.value = emptyList()
        AppLogger.log("Messages cleared")
    }

    /**
     * Reconnect to WebSocket
     */
    fun reconnect() {
        stop()
        start()
    }
}