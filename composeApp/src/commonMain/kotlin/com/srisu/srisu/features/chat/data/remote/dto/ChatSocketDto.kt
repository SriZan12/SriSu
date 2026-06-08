package com.srisu.srisu.features.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatSocketDto<T>(
    @SerialName("action")
    val action: String,

    @SerialName("request_id")
    val requestId: String? = null,

    @SerialName("payload")
    val payload: T,
)