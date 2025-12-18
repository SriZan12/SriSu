package com.srisu.srisu.features.chat.chatroom

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage

@Stable
data class ChatState(
    val messageInput: TextFieldValue = TextFieldValue(),
    val chatMessages: List<ChatMessage?>? = null,
    val longClickedMessage: ChatMessage? = null,
    val isEditMessage: Boolean = false
)