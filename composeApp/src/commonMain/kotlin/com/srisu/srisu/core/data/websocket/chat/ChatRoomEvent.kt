package com.srisu.srisu.core.data.websocket.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.response.chat.ChatRoomResponse
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.response.chat.MessageDeliveredResponse
import com.srisu.srisu.core.data.response.chat.MessageReadResponse
import com.srisu.srisu.core.data.response.chat.TypingResponse

sealed interface ChatRoomEvent {
    data object Connected : ChatRoomEvent
    data class Disconnected(val reason: String?) : ChatRoomEvent
    data class FetchMessages(val messages: FetchMessageResponse?) : ChatRoomEvent
    data class SendMessage(
        val message: ChatMessage?,
        val updatedChatRoom: ChatRoomResponse.Data.ChatRoom.ChatRoom?
    ) : ChatRoomEvent

    data class MessageEdited(val message: ChatMessage) : ChatRoomEvent
    data class MessageDeleted(val message: ChatMessage?) : ChatRoomEvent
    data class MessageTyping(val typingResponse: TypingResponse) : ChatRoomEvent
    data class MessageRead(val messageReadResponse: MessageReadResponse?) : ChatRoomEvent
    data class MessageDelivered(val messageDeliveredResponse: MessageDeliveredResponse?) :
        ChatRoomEvent

    data class ReactToMessage(val reactToMessage: ChatMessage?) : ChatRoomEvent

    data class GetChatRooms(val chatRoomResponse: ChatRoomResponse) : ChatRoomEvent

    data class Error(val throwable: Throwable) : ChatRoomEvent
}