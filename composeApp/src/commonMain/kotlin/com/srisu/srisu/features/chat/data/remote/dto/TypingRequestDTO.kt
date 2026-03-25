package com.srisu.srisu.features.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TypingRequest(
    @SerialName("action")
    val action: String,
    @SerialName("is_typing")
    val isTyping: Boolean,
    @SerialName("user_id")
    val userId: Long,
    @SerialName("chat_room_id")
    val chat_room_id: String?
)
