package com.srisu.srisu.core.data.response.chat


import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FetchMessageResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val chatMessages: List<ChatMessage?>? = null,
    @SerialName("message")
    val message: String? = null
)