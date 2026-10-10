package com.srisu.srisu.features.chat.data.remote.response

import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Required core-1 HTTP fields, separate from the tolerant legacy socket DTOs. */
@Serializable
data class ChatHistoryPage(
    @SerialName("chat_room_id") val chatRoomId: String,
    val messages: List<ChatMessage>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_cursor") val nextCursor: Long?,
)

@Serializable
data class ChatRoomPage(
    @SerialName("chat_rooms") val chatRooms: List<ChatRoomItemDto>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_cursor") val nextCursor: String?,
    val limit: Int,
)
