package com.srisu.srisu.features.chat.data.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReactionData(
    @SerialName("message_id")
    val messageId: Long? = null,

    @SerialName("user_id")
    val userId: Long? = null,

    @SerialName("reaction")
    val reaction: String? = null,

    @SerialName("was_removed")
    val wasRemoved: Boolean = false
)