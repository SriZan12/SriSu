package com.srisu.srisu.core.data.response.chat


import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.response.auth.User
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
                @SerialName("chat_type")
                val chatType: String? = null,
                @SerialName("couple")
                val couple: Int? = null,
                @SerialName("created_at")
                val createdAt: String? = null,
                @SerialName("id")
                val id: String? = null,
//                @SerialName("is_typing")
//                val isTyping: IsTyping? = null,
                @SerialName("last_message")
                val lastMessage: ChatMessage? = null,
                @SerialName("messages")
                val messages: List<ChatMessage?>? = null,
//                @SerialName("pinned_messages")
//                val pinnedMessages: List<Any?>? = null,
//                @SerialName("settings")
//                val settings: Settings? = null,
                @SerialName("singles")
                val singles: Int? = null,
//                @SerialName("unread_count")s
//                val unreadCount: UnreadCount? = null,
                @SerialName("updated_at")
                val updatedAt: String? = null,
                @SerialName("user_one")
                val userOne: Int? = null,
                @SerialName("user_two")
                val userTwo: Int? = null
            ) {
//                @Serializable
//                class IsTyping
//
//                @Serializable
//                class Settings
//
//                @Serializable
//                class UnreadCount
            }

        }
    }
}