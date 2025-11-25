package com.srisu.srisu.core.data.repository.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.websocket.ChatWebSocketClient
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
    private val _messages = MutableStateFlow<List<ChatMessage?>>(emptyList())
    val messages: StateFlow<List<ChatMessage?>> = _messages.asStateFlow()

    // Connection state exposed to UI
    val connectionState = webSocketClient.connectionState
        .stateIn(
            scope = repoScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatWebSocketClient.ConnectionState.Disconnected
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
            webSocketClient.incomingMessages.collect { incomingMessage ->
                handleIncomingMessage(incomingMessage)
            }
        }
    }

    private fun handleIncomingMessage(message: ChatMessage?) {
        val currentMessages = _messages.value.toMutableList()

        // Check for duplicates based on message ID
        val isDuplicate = message?.id?.let { id ->
            currentMessages.any { it?.id == id }
        } ?: false

        if (!isDuplicate) {
            currentMessages.add(message)
            // Sort by timestamp (newest first)
            _messages.value = currentMessages.sortedByDescending { it?.timestamp }
            AppLogger.log("Message added to repository: ${message?.text}")
        } else {
            AppLogger.log("Duplicate message ignored: ${message.id}")
        }
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