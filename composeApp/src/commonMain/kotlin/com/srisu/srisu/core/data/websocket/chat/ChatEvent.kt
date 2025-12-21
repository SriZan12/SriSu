package com.srisu.srisu.core.data.websocket.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage

sealed interface ChatEvent {
    data class Connected(val roomId: String) : ChatEvent
    data class Disconnected(val reason: String?) : ChatEvent

    data class FetchMessages(val messages: List<ChatMessage?>?) : ChatEvent
    data class SendMessage(val message: ChatMessage) : ChatEvent
    data class MessageEdited(val message: ChatMessage) : ChatEvent
    data class MessageDeleted(val messageId: Int?) : ChatEvent

    data class Error(val throwable: Throwable) : ChatEvent
}