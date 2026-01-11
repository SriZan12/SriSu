package com.srisu.srisu.core.data.response.chat


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDeliveredResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val `data`: Data? = null,
    @SerialName("message")
    val message: String? = null
) {
    @Serializable
    data class Data(
        @SerialName("action")
        val action: String? = null,
        @SerialName("chat_room_id")
        val chatRoomId: String? = null,
        @SerialName("delivered_to")
        val deliveredTo: Long? = null,
        @SerialName("message_ids")
        val messageIds: List<Long?>? = null
    )
}