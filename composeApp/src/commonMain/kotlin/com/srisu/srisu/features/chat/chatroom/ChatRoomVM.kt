package com.srisu.srisu.features.chat.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.repository.chat.ChatRepository
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
import com.srisu.srisu.core.data.websocket.chat.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
) : ViewModel() {


    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        repository.start()
        updateChatMessages()
    }

    // -----------------------------
    // User Actions
    // -----------------------------

    fun onMessageInputChanged(text: String) {
        _chatState.update { it ->
            it.copy(messageInput = text)
        }
    }

    fun updateChatMessages() {
        viewModelScope.launch {
            repository.messages.collect { messages ->
                _chatState.update {
                    it.copy(chatMessages = messages)
                }
            }
        }
    }

    /**
     * Send message and clear input
     */
    fun sendMessage() {
        val text = chatState.value.messageInput.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    ChatMessage(
                        action = "send_message",
                        text = text,
                        senderId = 3,
                        receiverId = 2,
                        couple = 1,
                        reactions = null,
                        deleteFor = null,
                        messageDeletionDict = null,
                        chatRoom = "e579dc98-5dbd-4aab-8a48-10985346d7fa",
                        messageType = "text"
                    )
                )
                onMessageInputChanged("")
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to send message: ${e.message}"
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