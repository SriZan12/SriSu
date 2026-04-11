package com.srisu.srisu.features.chat.data.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomsData(
    @SerialName("chat_rooms")
    val chatRooms: List<ChatRoomItemDto> = emptyList(),

    @SerialName("next_cursor")
    val nextCursor: String? = null,

    @SerialName("limit")
    val limit: Int? = null
)