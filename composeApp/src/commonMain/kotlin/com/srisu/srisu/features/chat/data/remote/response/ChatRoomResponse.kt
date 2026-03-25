package com.srisu.srisu.features.chat.data.remote.response


import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.auth.data.remote.response.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("data")
    val `data`: Data? = null
) {
    @Serializable
    data class Data(
        @SerialName("chat_rooms")
        val chatRooms: List<ChatRoom?>? = null,
        @SerialName("limit")
        val limit: Int? = null,
        @SerialName("next_cursor")
        val nextCursor: String? = null
    ) {
        @Serializable
        data class ChatRoom(
            @SerialName("chat_room")
            val chatRoom: ChatRoom? = null,
            @SerialName("other_user")
            val otherUser: User? = null
        ) {
            @Serializable
            data class ChatRoom(
                @SerialName("id")
                val id: String? = null,

                @SerialName("chat_type")
                val chatType: String? = null,

                @SerialName("couple")
                val couple: Int? = null,

                @SerialName("singles")
                val singles: Int? = null,

                // Messages in this chat room
                @SerialName("messages")
                val messages: List<ChatMessage> = emptyList(),

                // Last message
                @SerialName("last_message")
                val lastMessage: ChatMessage? = null,

                // Unread counts per user
                @SerialName("unread_count")
                val unreadCount: Map<String, Int> = emptyMap(),

                // Typing status
                @SerialName("is_typing")
                val isTyping: TypingResponse? = null,

                // Pinned messages
                @SerialName("pinned_messages")
                val pinnedMessages: List<ChatMessage> = emptyList(),

                // Chat settings (muted, archived, etc.)
                @SerialName("settings")
                val settings: Map<String, Boolean> = emptyMap(),

                @SerialName("created_at")
                val createdAt: String? = null, // ISO8601 timestamp

                @SerialName("updated_at")
                val updatedAt: String? = null
            )

        }
    }
}