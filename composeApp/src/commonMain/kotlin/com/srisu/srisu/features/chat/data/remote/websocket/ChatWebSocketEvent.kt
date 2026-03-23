package com.srisu.srisu.features.chat.data.remote.websocket

import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomResponse
import com.srisu.srisu.features.chat.data.remote.response.FetchMessageResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageReadResponse
import com.srisu.srisu.features.chat.data.remote.response.TypingResponse

sealed interface ChatWebSocketEvent {
    data object Connected : ChatWebSocketEvent
    data class Disconnected(val reason: String?) : ChatWebSocketEvent
    data class FetchMessages(val messages: FetchMessageResponse?) : ChatWebSocketEvent
    data class SendMessage(
        val message: ChatMessage?,
        val updatedChatRoom: ChatRoomResponse.Data.ChatRoom.ChatRoom?
    ) : ChatWebSocketEvent

    data class MessageEdited(val message: ChatMessage) : ChatWebSocketEvent
    data class MessageDeleted(val message: ChatMessage?) : ChatWebSocketEvent
    data class MessageTyping(val typingResponse: TypingResponse) : ChatWebSocketEvent
    data class MessageRead(val messageReadResponse: MessageReadResponse?) : ChatWebSocketEvent
    data class MessageDelivered(val messageDeliveredResponse: MessageDeliveredResponse?) :
        ChatWebSocketEvent

    data class ReactToMessage(val reactToMessage: ChatMessage?) : ChatWebSocketEvent

    data class GetChatRooms(val chatRoomResponse: ChatRoomResponse) : ChatWebSocketEvent

    data class Error(val throwable: Throwable) : ChatWebSocketEvent
}