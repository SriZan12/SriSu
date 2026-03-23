package com.srisu.srisu.features.chat.data.remote.dto

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