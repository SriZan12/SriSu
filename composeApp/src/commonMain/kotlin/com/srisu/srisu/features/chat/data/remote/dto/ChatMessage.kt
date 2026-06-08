package com.srisu.srisu.features.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class UploadState {
    UPLOADING, UPLOADED
}

@Serializable
data class ChatMessage(
    @SerialName("id")
    val id: Long? = null,

    @SerialName("chat_room_id")
    val chatRoomId: String? = null,

    @SerialName("sender_id")
    val senderId: Long? = null,

    @SerialName("receiver_id")
    val receiverId: Long? = null,

    @SerialName("message_type")
    val messageType: String? = null,

    @SerialName("text")
    val text: String? = null,

    @SerialName("media_url")
    val mediaUrl: String? = null,

    @SerialName("sticker_url")
    val stickerUrl: String? = null,

    @SerialName("medias")
    val medias: List<Media> = emptyList(),

    @SerialName("reply_to")
    val replyTo: ReplyMessage? = null,

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

    @SerialName("deleted_message")
    val deletedMessage: String? = null,

    @SerialName("delete_option")
    val deleteOption: String? = null,

    /**
     * Transitional compatibility field.
     * Keep this while backend/frontend are migrating away from old delete_for JSON logic.
     */
    @SerialName("delete_for")
    val deleteFor: Map<String, List<DeleteMessageAction>>? = null,

    /**
     * Transitional compatibility field for legacy reaction payloads.
     * Recommended backend shape: { "12": "❤️", "15": "🔥" }
     */
    @SerialName("reactions")
    val reactions: Map<String, String> = emptyMap(),

    @SerialName("timestamp")
    val timestamp: String? = null,

    // Local-only UI fields
    val uploadingPhotos: List<UploadingPhoto> = emptyList(),
    val isLocalOnly: Boolean = false,
) {

    @Serializable
    data class ReplyMessage(
        @SerialName("id")
        val id: Long? = null,

        @SerialName("text")
        val text: String? = null,

        @SerialName("sender_id")
        val senderId: Long? = null,

        @SerialName("message_type")
        val messageType: String? = null,

        @SerialName("message_owner_name")
        val messageOwnerName: String? = null,
    )

    @Serializable
    data class DeleteMessageAction(
        @SerialName("delete_option")
        val option: String? = null,

        @SerialName("user_id")
        val userId: Long? = null,

        @SerialName("delete_message")
        val deleteMessage: String? = null,
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
        val progress: Float,
        val state: UploadState,
    )
}