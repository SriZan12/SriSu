package com.srisu.srisu.features.chat.data.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TypingData(
    @SerialName("chat_room_id")
    val chatRoomId: String? = null,

    @SerialName("typing_users")
    val typingUsers: Map<String, Boolean> = emptyMap()
)