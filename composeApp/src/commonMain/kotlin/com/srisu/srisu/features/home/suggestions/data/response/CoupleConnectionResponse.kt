package com.srisu.srisu.features.home.suggestions.data.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoupleConnectionResponse(
    @SerialName("breakup_reason")
    val breakupReason: String?,
    @SerialName("connection_status")
    val connectionStatus: String?,
    @SerialName("created_at")
    val createdAt: String?,
    @SerialName("id")
    val id: Int?,
    @SerialName("receiver_number")
    val receiverNumber: String?,
    @SerialName("sender_number")
    val senderNumber: String?,
    @SerialName("updated_at")
    val updatedAt: String?,
    @SerialName("couple_chat")
    val coupleChat: com.srisu.srisu.features.couplechat.data.CoupleChatRoom? = null,
)
