package com.srisu.srisu.features.chat.data.remote.websocket

import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomItemDto
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomResponse
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomsData
import com.srisu.srisu.features.chat.data.remote.response.FetchMessageResponse
import com.srisu.srisu.features.chat.data.remote.response.FetchMessagesData
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredData
import com.srisu.srisu.features.chat.data.remote.response.MessageDeliveredResponse
import com.srisu.srisu.features.chat.data.remote.response.MessageReadData
import com.srisu.srisu.features.chat.data.remote.response.MessageReadResponse
import com.srisu.srisu.features.chat.data.remote.response.ReactionData
import com.srisu.srisu.features.chat.data.remote.response.TypingData
import com.srisu.srisu.features.chat.data.remote.response.TypingResponse

sealed interface ChatWebSocketEvent {
    data object Connected : ChatWebSocketEvent
    data class Disconnected(val reason: String?) : ChatWebSocketEvent
    data class FetchMessages(val data: FetchMessagesData) : ChatWebSocketEvent
    data class SendMessage(val message: ChatMessage?, val updatedChatRoom: ChatRoomItemDto?) : ChatWebSocketEvent
    data class MessageEdited(val message: ChatMessage) : ChatWebSocketEvent
    data class MessageDeleted(val message: ChatMessage) : ChatWebSocketEvent
    data class MessageTyping(val data: TypingData) : ChatWebSocketEvent
    data class MessageRead(val data: MessageReadData) : ChatWebSocketEvent
    data class MessageDelivered(val data: MessageDeliveredData) : ChatWebSocketEvent
    data class ReactToMessage(val data: ReactionData) : ChatWebSocketEvent
    data class GetChatRooms(val data: ChatRoomsData) : ChatWebSocketEvent
    data class ChatRoomUpdated(val chatRoom: ChatRoomItemDto) : ChatWebSocketEvent
    data class Error(val throwable: Throwable) : ChatWebSocketEvent
}