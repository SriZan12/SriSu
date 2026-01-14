package com.srisu.srisu.core.data.response.chat


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMediaResponse(
    @SerialName("data")
    val `data`: List<Data?>? = null,
    @SerialName("message")
    val message: String? = null
) {
    @Serializable
    data class Data(
        @SerialName("file")
        val `file`: String? = null,
        @SerialName("id")
        val id: Int? = null,
        @SerialName("uploaded_at")
        val uploadedAt: String? = null
    )
}