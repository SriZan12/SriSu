package com.srisu.srisu.features.chat.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.repository.chat.ChatRepository
import com.srisu.srisu.core.data.websocket.ChatWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val currentUserId: Int = 97,  // Inject this via DI in production
    private val otherUserId: Int = 95     // Inject this via DI in production
) : ViewModel() {

    // Messages from repository
    val messages: StateFlow<List<ChatMessage?>> = repository.messages

    // Connection state from repository
    val connectionState: StateFlow<ChatWebSocketClient.ConnectionState> =
        repository.connectionState

    // Loading state
    val isLoading: StateFlow<Boolean> = repository.isLoading

    // UI state for input field
    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Start WebSocket connection when ViewModel is created
        repository.start()
    }

    // -----------------------------
    // User Actions
    // -----------------------------

    /**
     * Update message input text
     */
    fun onMessageInputChanged(text: String) {
        _messageInput.value = text
    }

    /**
     * Send message and clear input
     */
    fun onSendMessage() {
        val text = _messageInput.value.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    senderId = currentUserId,
                    receiverId = otherUserId,
                    text = text
                )
                _messageInput.value = "" // Clear input on success
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to send message: ${e.message}"
            }
        }
    }

    /**
     * Load more messages (for pagination)
     */
    fun loadMoreMessages(page: Int, pageSize: Int = 20) {
        viewModelScope.launch {
            try {
                repository.fetchMessages(page, pageSize)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load messages: ${e.message}"
            }
        }
    }

    /**
     * Retry connection
     */
    fun retryConnection() {
        viewModelScope.launch {
            repository.reconnect()
            _error.value = null
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    // -----------------------------
    // Lifecycle
    // -----------------------------

    override fun onCleared() {
        super.onCleared()
        repository.stop()
    }
}