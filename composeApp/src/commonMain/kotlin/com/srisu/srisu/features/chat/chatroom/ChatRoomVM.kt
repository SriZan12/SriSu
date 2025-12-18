package com.srisu.srisu.features.chat.chatroom

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

    fun onMessageInputChanged(value: TextFieldValue) {
        _chatState.update { it.copy(messageInput = value) }
    }

    fun setMessageInputText(text: String) {
        _chatState.update {
            it.copy(
                messageInput = TextFieldValue(
                    text = text,
                    selection = TextRange(text.length) // cursor at end
                )
            )
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

    fun updateLongClickedMessage(chatMessage: ChatMessage) {
        _chatState.update {
            it.copy(longClickedMessage = chatMessage)
        }
    }

    fun updateIsEditMessage(isEditMessage: Boolean) {
        _chatState.update {
            it.copy(isEditMessage = isEditMessage)
        }
    }

    /**
     * Send message and clear input
     */
    fun sendMessage() {
        val text = chatState.value.messageInput.text.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    ChatMessage(
                        action = "send_message",
                        text = text,
                        senderId = 97,
                        receiverId = 95,
                        couple = 2,
                        reactions = null,
                        deleteFor = null,
                        messageDeletionDict = null,
                        chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4",
                        messageType = "text"
                    )
                )
                onMessageInputChanged(TextFieldValue())
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to send message: ${e.message}"
            }
        }
    }

    fun editMessage(
        messageId: Int?
    ) {
        val text = chatState.value.messageInput.text.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repository.editMessage(
                    ChatMessage(
                        action = "edit_message",
                        id = messageId,
                        text = text,
                        senderId = 97,
                        receiverId = 95,
                        couple = 2,
                        chatRoom = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4",
                        messageType = "text"
                    )
                )
                onMessageInputChanged(TextFieldValue())
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