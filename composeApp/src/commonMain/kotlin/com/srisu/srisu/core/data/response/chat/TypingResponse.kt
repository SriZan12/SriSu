package com.srisu.srisu.core.data.response.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TypingResponse(
    @SerialName("action")
    val action: String = "typing",
    @SerialName("message")
    val message: String? = null,
    @SerialName("data")
    val typingData: TypingData? = null

) {
    @Serializable
    data class TypingData(
        @SerialName("typing_users")
        val typingUsers: Map<String, Boolean>,
        @SerialName("chat_room_id")
        val chatRoomId: String? = null
    )
}

