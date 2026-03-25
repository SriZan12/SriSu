package com.srisu.srisu.features.chat.data.remote.response

import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FetchMessageResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val chatMessage: Message? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean? = null,
    @SerialName("next_cursor")
    val nextCursor: Long? = null
) {
    @Serializable
    data class Message(
        @SerialName("messages")
        val results: List<ChatMessage?>? = null
    )
}