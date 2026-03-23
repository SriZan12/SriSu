package com.srisu.srisu.features.chat.data.remote.response


import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val `data`: ChatMessage? = null,
    @SerialName("updated_chat_room")
    val updatedChatRoom: ChatRoomResponse.Data.ChatRoom.ChatRoom? = null,
    @SerialName("message")
    val message: String? = null
)