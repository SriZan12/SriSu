package com.srisu.srisu.core.data.websocket.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse

sealed interface ChatEvent {
    data class Connected(val roomId: String) : ChatEvent
    data class Disconnected(val reason: String?) : ChatEvent

    data class FetchMessages(val messages: FetchMessageResponse?) : ChatEvent
    data class SendMessage(val message: ChatMessage) : ChatEvent
    data class MessageEdited(val message: ChatMessage) : ChatEvent
    data class MessageDeleted(val message: ChatMessage?) : ChatEvent

    data class Error(val throwable: Throwable) : ChatEvent
}