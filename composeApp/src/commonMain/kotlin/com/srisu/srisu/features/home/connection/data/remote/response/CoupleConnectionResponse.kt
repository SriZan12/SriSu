package com.srisu.srisu.features.home.connection.data.remote.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoupleConnectionResponse(
    @SerialName("breakup_reason")
    val breakupReason: String? = null,
    @SerialName("connection_status")
    val connectionStatus: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("id")
    val id: Long? = null,
    @SerialName("receiver_number")
    val receiverNumber: String? = null,
    @SerialName("sender_number")
    val senderNumber: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("chat_room_id")
    val chatRoomId: String? = null,
    @SerialName("couple_id")
    val coupleId: Long? = null,
    val replayed: Boolean? = null,
)
