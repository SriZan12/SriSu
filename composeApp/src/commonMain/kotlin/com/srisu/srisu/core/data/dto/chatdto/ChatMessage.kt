package com.srisu.srisu.core.data.dto.chatdto

import coil3.Uri
import com.srisu.srisu.core.data.response.chat.ChatRoomResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class UploadState {
    UPLOADING,
    UPLOADED,
    FAILED
}


@Serializable
data class ChatMessage(

    @SerialName("action")
    val action: String = "",

    @SerialName("id")
    val id: Long? = null,
    @SerialName("user_id")
    val user_id: Int? = null,

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
    val medias: List<Media?>? = emptyList(),
    // for local only
    val uploadingPhotos: List<UploadingPhoto>? = null,
    val isLocalOnly: Boolean = false,


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

    @SerialName("is_sent")
    val isSent: Boolean? = null,

    @SerialName("is_edited")
    val isEdited: Boolean? = null,

    // Delete fields
    @SerialName("deleted_message")
    val deletedMessage: String? = null,

    @SerialName("delete_option")
    val deleteOption: String? = null,

    @SerialName("delete_for")
    val deleteFor: Map<String, List<DeleteMessageAction>>? = null,

    @SerialName("message_deletion_dict")
    val messageDeletionDict: Map<String, String>? = null,

    // Reactions
    @SerialName("reactions")
    val reactions: Reaction? = null,

    // Time
    @SerialName("timestamp")
    val timestamp: String? = null,

) {
    @Serializable
    data class ReplyMessage(
        @SerialName("id")
        val id: Long? = null,
        @SerialName("text")
        val text: String? = null,
        @SerialName("sender_id")
        val senderId: Int? = null,
        @SerialName("message_type")
        val messageType: String? = null

    )

    @Serializable
    data class DeleteMessageAction(
        @SerialName("delete_option")
        val option: String? = null,
        @SerialName("user_id")
        val user_id: Int? = null,
        @SerialName("delete_message")
        val delete_message: String? = null
    )

    @Serializable
    data class Reaction(
        @SerialName("reaction")
        val reaction: String? = null,
    )

    @Serializable
    data class Media(
        @SerialName("id")
        val id: Long? = null,
        @SerialName("media_url")
        val mediaUrl: String? = null,
        @SerialName("uploaded_at")
        val uploadedAt: String? = null,
    )

    @Serializable
    data class UploadingPhoto(
        val localUri: String,
        val progress: Float,   // 0f → 1f
        val state: UploadState
    )

}