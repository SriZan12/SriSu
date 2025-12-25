package com.srisu.srisu.core.data.dto.chatdto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val isTyping: Map<String, Boolean>? = emptyMap(),

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
