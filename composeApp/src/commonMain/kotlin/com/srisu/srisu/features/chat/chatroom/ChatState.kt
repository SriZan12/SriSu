package com.srisu.srisu.features.chat.chatroom

import androidx.compose.runtime.Stable
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage

@Stable
data class ChatState(
    val messageInput: String = "",
    val chatMessages: List<ChatMessage?>? = null,
)