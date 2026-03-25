package com.srisu.srisu.features.chat.data.remote.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageReadResponse(
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
        @SerialName("message_ids")
        val messageIds: List<Long?>? = null,
        @SerialName("read_by")
        val readBy: Long? = null
    )
}