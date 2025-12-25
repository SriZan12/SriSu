package com.srisu.srisu.core.data.response.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TypingResponse(
    @SerialName("typing_map")
    val typingMap: Map<String, Boolean> = emptyMap(),

    @SerialName("current_user_id")
    val currentUserId: Int?
) {
    /** Users typing except myself */
    fun otherUsersTyping(): List<String> =
        typingMap
            .filter { (userId, isTyping) ->
                isTyping && userId != currentUserId?.toString()
            }
            .keys
            .toList()

    fun isAnyoneTyping(): Boolean =
        otherUsersTyping().isNotEmpty()
}

