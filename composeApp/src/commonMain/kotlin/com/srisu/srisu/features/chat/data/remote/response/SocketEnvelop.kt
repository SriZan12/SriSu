package com.srisu.srisu.features.chat.data.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocketEnvelope<T>(
    @SerialName("type")
    val type: String? = null,

    @SerialName("action")
    val action: String? = null,

    @SerialName("request_id")
    val requestId: String? = null,

    @SerialName("message")
    val message: String? = null,

    @SerialName("data")
    val data: T? = null
)

typealias ChatRoomsSocketResponse = SocketEnvelope<ChatRoomsData>
typealias FetchMessagesSocketResponse = SocketEnvelope<FetchMessagesData>
typealias MessageMutationSocketResponse = SocketEnvelope<MessageMutationData>
typealias MessageReadSocketResponse = SocketEnvelope<MessageReadData>
typealias MessageDeliveredSocketResponse = SocketEnvelope<MessageDeliveredData>
typealias TypingSocketResponse = SocketEnvelope<TypingData>
typealias ReactionSocketResponse = SocketEnvelope<ReactionData>