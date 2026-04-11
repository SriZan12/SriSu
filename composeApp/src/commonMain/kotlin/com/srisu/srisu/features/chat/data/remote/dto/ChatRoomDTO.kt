package com.srisu.srisu.features.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ChatRoomDTO(
    @SerialName("limit")
    val limit: Int = 10,

    @SerialName("last_updated")
    val lastUpdated: String? = null,
)