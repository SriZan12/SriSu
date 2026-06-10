package com.srisu.srisu.features.home.connection.data.remote.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class HaveCoupleConnectionResponse(
    @SerialName("connection")
    val connection: Connection? = null,
    @SerialName("connection_requested")
    val connectionRequested: Boolean? = null
) {
    @Serializable
    data class Connection(
        @SerialName("breakup_reason")
        val breakupReason: String? = null,
        @SerialName("connection_status")
        val connectionStatus: String? = null,
        @SerialName("created_at")
        val createdAt: String? = null,
        @SerialName("id")
        val id: Int? = null,
        @SerialName("partner")
        val partner: CoupleConnectionRequestResponse.Result.Receiver? = null,
        @SerialName("receiver_number")
        val receiverNumber: String? = null,
        @SerialName("sender_number")
        val senderNumber: String? = null,
        @SerialName("updated_at")
        val updatedAt: String? = null
    )
}
