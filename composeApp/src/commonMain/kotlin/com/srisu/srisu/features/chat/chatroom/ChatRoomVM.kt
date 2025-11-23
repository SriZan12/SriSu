package com.srisu.srisu.features.chat.chatroom

// ChatViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.repository.chat.ChatRepository
import com.srisu.srisu.core.data.response.chat.ChatMessage
import com.srisu.srisu.core.data.response.chat.ChatResponse
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    val messages: StateFlow<List<ChatResponse.Data>> = repository.messages


    init {
        repository.start()
    }

    fun onSend(text: String) {
//        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(meId = 97, peerId = 95,text.trim())
        }
    }

    override fun onCleared() {
        repository.stop()
        super.onCleared()
    }
}
