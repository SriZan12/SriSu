package com.srisu.srisu.features.chat.data.remote.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMediaResponse(
    @SerialName("media")
    val media: List<Media?>? = null,
    @SerialName("message")
    val message: String? = null
) {
    @Serializable
    data class Media(
        @SerialName("file")
        val `file`: String? = null,
        @SerialName("id")
        val id: Long? = null,
        @SerialName("uploaded_at")
        val uploadedAt: String? = null
    )
}