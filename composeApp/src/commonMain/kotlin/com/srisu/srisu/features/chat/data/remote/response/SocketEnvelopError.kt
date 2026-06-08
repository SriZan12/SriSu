package com.srisu.srisu.features.chat.data.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocketErrorEnvelope(
    @SerialName("type")
    val type: String? = null,

    @SerialName("action")
    val action: String? = null,

    @SerialName("request_id")
    val requestId: String? = null,

    @SerialName("message")
    val message: String? = null,

    @SerialName("errors")
    val errors: Map<String, List<String>>? = null
)