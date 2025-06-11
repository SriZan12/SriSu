package com.srisu.srisu.core.data.response.suggestion


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoveRequestListResponse(
    @SerialName("count")
    val count: Int?,
    @SerialName("next")
    val next: String?,
    @SerialName("previous")
    val previous: String?,
    @SerialName("results")
    val results: List<Result?>?
) {
    @Serializable
    data class Result(
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
        val updatedAt: String?
    )
}