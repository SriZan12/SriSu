package com.srisu.srisu.features.chat.chatroom

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.response.chat.TypingResponse
import com.srisu.srisu.session.Session

@Stable
data class ChatState(
    val session: Session? = null,
    val messageInput: TextFieldValue = TextFieldValue(),
    val chatMessages: List<ChatMessage?>? = null,
    val selectedMessageForAction: ChatMessage? = null,
    val selectedMessageIdForActions: Long? = null,
    val isEditMessage: Boolean = false,
    var lastAnimatedMessageId: Long? = null,
    var lastSentMessageId: Long? = null,
    val typingResponse: TypingResponse? = null
)