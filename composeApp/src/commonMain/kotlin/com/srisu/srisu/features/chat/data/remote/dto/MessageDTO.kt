package com.srisu.srisu.features.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable



@Serializable
data class FetchMessagesDto(
    @SerialName("chat_room_id")
    val chatRoomId: String,

    @SerialName("cursor")
    val cursor: Long? = null,

    @SerialName("limit")
    val limit: Int = 20,
)

@Serializable
data class SendMessageDto(
    @SerialName("chat_room_id")
    val chatRoomId: String,

    @SerialName("text")
    val text: String? = null,

    @SerialName("message_type")
    val messageType: String = "text",

    @SerialName("media_ids")
    val mediaIds: List<Long> = emptyList(),

    @SerialName("reply_to_id")
    val replyToId: Long? = null,

    @SerialName("media_url")
    val mediaUrl: String? = null,

    @SerialName("sticker_url")
    val stickerUrl: String? = null,
)

@Serializable
data class EditMessageDto(
    @SerialName("message_id")
    val messageId: Long,

    @SerialName("text")
    val text: String,
)

@Serializable
data class DeleteMessageDto(
    @SerialName("message_id")
    val messageId: Long,

    @SerialName("delete_option")
    val deleteOption: String,
)

@Serializable
data class MarkReadDto(
    @SerialName("chat_room_id")
    val chatRoomId: String,
)

@Serializable
data class MarkDeliveredDto(
    @SerialName("chat_room_id")
    val chatRoomId: String,
)

@Serializable
data class ReactionMessageDto(
    @SerialName("message_id")
    val messageId: Long,

    @SerialName("reaction")
    val reaction: String,
)

@Serializable
data class SetTypingDto(
    @SerialName("chat_room_id")
    val chatRoomId: String,

    @SerialName("is_typing")
    val isTyping: Boolean,
)
