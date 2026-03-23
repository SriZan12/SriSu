package com.srisu.srisu.features.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FetchMessageDTO(
    @SerialName("action")
    val action: String,
    @SerialName("page")
    val page: Long?,
    @SerialName("page_size")
    val page_size: Int?,
    @SerialName("chat_room_id")
    val chatRoomId: String? = null
)
