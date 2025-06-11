package com.srisu.srisu.core.data.dto.couple

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoupleConnectionDTO(
    @SerialName("connection_status")
    val connectionStatus: String? = null,
    @SerialName("sender_number")
    val senderNumber: String? = null,
    @SerialName("receiver_number")
    val receiverNumber: String? = null
)