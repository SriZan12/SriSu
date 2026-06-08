package com.srisu.srisu.features.chat.data.remote.response

import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageMutationData(
    @SerialName("message")
    val message: ChatMessage? = null,

    @SerialName("chat_room")
    val chatRoom: ChatRoomItemDto? = null
)