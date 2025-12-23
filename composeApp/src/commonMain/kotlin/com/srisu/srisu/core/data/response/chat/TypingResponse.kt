package com.srisu.srisu.core.data.response.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TypingResponse(
    @SerialName("typing_users")
    val typing_users: List<String>??,
    @SerialName("user_id")
    val user_id: Int?
)
