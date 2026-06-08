package com.srisu.srisu.features.chat.data.remote.response

import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FetchMessagesData(
    @SerialName("chat_room_id")
    val chatRoomId: String? = null,

    @SerialName("messages")
    val messages: List<ChatMessage> = emptyList(),

    @SerialName("has_more")
    val hasMore: Boolean = false,

    @SerialName("next_cursor")
    val nextCursor: Long? = null
)