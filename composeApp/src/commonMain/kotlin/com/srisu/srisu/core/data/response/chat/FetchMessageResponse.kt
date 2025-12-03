package com.srisu.srisu.core.data.response.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FetchMessageResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val chatMessage: Message? = null,
    @SerialName("message")
    val message: String? = null
) {
    @Serializable
    data class Message(
        @SerialName("count")
        val count: Int? = null,
        @SerialName("next")
        val next: Int? = null,
        @SerialName("previous")
        val previous: Int? = null,
        @SerialName("results")
        val results: List<ChatMessage?>? = null
    )
}