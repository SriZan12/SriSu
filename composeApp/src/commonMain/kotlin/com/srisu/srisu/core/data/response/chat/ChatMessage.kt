package com.srisu.srisu.core.data.response.chat

data class ChatMessage(
    val id: Int? = null,

    // Chat associates
    val chatRoom: Int? = null,                 // ForeignKey → use ID
    val couple: Int,                           // required
    val singles: Int? = null,

    // Sender and Receiver
    val sender: Int,
    val receiver: Int? = null,

    // Message contents
    val messageType: String = "text",          // MessageType.TEXT
    val text: String? = null,
    val media: String? = null,                 // File URL returned by API
    val mediaUrl: String? = null,
    val stickerUrl: String? = null,
    val medias: List<Int> = emptyList(),        // ManyToMany → list of media IDs

    // Reply to message
    val replyTo: Int? = null,

    // Message status
    val isDeleted: Boolean = false,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,

    // Message actions
    val deletedMessage: String? = null,
    val deleteOption: String = "NOT_DELETED",
    val messageDeletionDict: Map<String, String>? = null,
    val isEdited: Boolean = false,

    val deleteFor: Map<String, String> = emptyMap(),

    // Reactions
    val reactions: Map<String, String> = emptyMap(),

    // Timestamps
    val timestamp: String? = null
)