package com.srisu.srisu.features.chat.data.remote.response

import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomItemDto(
    @SerialName("id")
    val id: String? = null,

    @SerialName("chat_type")
    val chatType: String? = null,

    @SerialName("user_one_id")
    val userOneId: Long? = null,

    @SerialName("user_two_id")
    val userTwoId: Long? = null,

    @SerialName("other_user")
    val otherUser: User? = null,

    @SerialName("last_message")
    val lastMessage: ChatMessage? = null,

    @SerialName("unread_count")
    val unreadCount: Map<String, Int> = emptyMap(),

    @SerialName("is_typing")
    val isTyping: Map<String, Boolean> = emptyMap(),

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)