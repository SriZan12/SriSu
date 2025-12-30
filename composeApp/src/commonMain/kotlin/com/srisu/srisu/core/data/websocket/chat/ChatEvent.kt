package com.srisu.srisu.core.data.websocket.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.response.chat.MessageDeliveredResponse
import com.srisu.srisu.core.data.response.chat.MessageReadResponse
import com.srisu.srisu.core.data.response.chat.TypingResponse

sealed interface ChatEvent {
    data class Connected(val roomId: String) : ChatEvent
    data class Disconnected(val reason: String?) : ChatEvent

    data class FetchMessages(val messages: FetchMessageResponse?) : ChatEvent
    data class SendMessage(val message: ChatMessage) : ChatEvent
    data class MessageEdited(val message: ChatMessage) : ChatEvent
    data class MessageDeleted(val message: ChatMessage?) : ChatEvent
    data class MessageTyping(val typingResponse: TypingResponse) : ChatEvent
    data class MessageRead(val messageReadResponse: MessageReadResponse?) : ChatEvent
    data class MessageDelivered(val messageDeliveredResponse: MessageDeliveredResponse?) : ChatEvent

    data class Error(val throwable: Throwable) : ChatEvent
}