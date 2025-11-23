package com.srisu.srisu.core.data.response.chat


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val `data`: List<Data?>? = null,
    @SerialName("message")
    val message: String? = null
) {
    @Serializable
    data class Data(
        @SerialName("chat_room_id")
        val chatRoomId: String? = null,
        @SerialName("delete_for")
        val deleteFor: String? = null,
        @SerialName("id")
        val id: String? = null,
        @SerialName("is_delivered")
        val isDelivered: Boolean? = null,
        @SerialName("is_read")
        val isRead: Boolean? = null,
        @SerialName("medias")
        val medias: List<String?>? = null,
        @SerialName("reaction")
        val reaction: String? = null,
        @SerialName("reply_to")
        val replyTo: String? = null,
        @SerialName("sender_id")
        val senderId: Int? = null,
        @SerialName("text")
        val text: String? = null,
        @SerialName("timestamp")
        val timestamp: String? = null
    )
}