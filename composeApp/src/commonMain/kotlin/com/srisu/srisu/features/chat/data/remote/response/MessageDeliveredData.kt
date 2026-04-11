package com.srisu.srisu.features.chat.data.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDeliveredData(
    @SerialName("chat_room_id")
    val chatRoomId: String? = null,

    @SerialName("delivered_to")
    val deliveredTo: Long? = null,

    @SerialName("message_ids")
    val messageIds: List<Long> = emptyList()
)