package com.srisu.srisu.core.data.dto.chatdto

import com.srisu.srisu.utils.Constants.ChatConstants.GET_CHAT_ROOMS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomDTO(
    @SerialName("action")
    val action: String,
    @SerialName("limit")
    val limit: Int,
    @SerialName("last_updated")
    val lastUpdated: String
)