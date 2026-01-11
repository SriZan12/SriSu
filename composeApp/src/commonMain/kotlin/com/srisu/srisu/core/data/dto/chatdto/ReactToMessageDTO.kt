package com.srisu.srisu.core.data.dto.chatdto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReactToMessageDTO(
    @SerialName("action")
    val action: String,
    @SerialName("message_id")
    val messageId: Long?,
    @SerialName("reaction")
    val reaction: String,
    @SerialName("user_id")
    val userId: Long?
)