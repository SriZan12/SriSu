package com.srisu.srisu.core.data.response.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FetchMessageResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val chatMessage: ChatMessage? = null,
    @SerialName("message")
    val message: String? = null
) {
    @Serializable
    data class ChatMessage(
        @SerialName("count")
        val count: Int? = null,
        @SerialName("next")
        val next: Int? = null,
        @SerialName("previous")
        val previous: Int? = null,
        @SerialName("results")
        val results: List<Result?>? = null
    ) {
        @Serializable
        data class Result(
            @SerialName("id")
            val id: String? = null,

            // Chat info
            @SerialName("chat_room")
            val chatRoom: String? = null,

            @SerialName("couple")
            val couple: Int? = null,

            @SerialName("singles")
            val singles: Int? = null,

            // Sender / receiver
            @SerialName("sender_id")
            val senderId: Int? = null,

            @SerialName("receiver_id")
            val receiverId: Int? = null,

            // Message content
            @SerialName("message_type")
            val messageType: String? = null,

            @SerialName("text")
            val text: String? = null,

            @SerialName("media")
            val media: String? = null,

            @SerialName("media_url")
            val mediaUrl: String? = null,

            @SerialName("sticker_url")
            val stickerUrl: String? = null,

            @SerialName("medias")
            val medias: List<String> = emptyList(),

            // Reply to message (nested)
            @SerialName("reply_to")
            val replyTo: ReplyMessage? = null,

            // Message status
            @SerialName("is_deleted")
            val isDeleted: Boolean? = null,

            @SerialName("is_read")
            val isRead: Boolean? = null,

            @SerialName("is_delivered")
            val isDelivered: Boolean? = null,

            @SerialName("is_edited")
            val isEdited: Boolean? = null,

            // Delete fields
            @SerialName("deleted_message")
            val deletedMessage: String? = null,

            @SerialName("delete_option")
            val deleteOption: String? = null,

            @SerialName("delete_for")
            val deleteFor: Map<String, List<DeleteInfo>>? = null,

            @SerialName("message_deletion_dict")
            val messageDeletionDict: Map<String, String>? = null,

            // Reactions
            @SerialName("reactions")
            val reactions: Map<String, String>? = null,

            // Time
            @SerialName("timestamp")
            val timestamp: String? = null,
        ) {
            @Serializable
            data class ReplyMessage(
                @SerialName("id")
                val id: Int? = null,

                @SerialName("text")
                val text: String? = null,

                @SerialName("sender_id")
                val senderId: Int? = null
            )

            @Serializable
            data class DeleteInfo(
                @SerialName("user_id")
                val userId: Int? = null,

                @SerialName("delete_option")
                val deleteOption: String? = null
            )
        }
    }
}