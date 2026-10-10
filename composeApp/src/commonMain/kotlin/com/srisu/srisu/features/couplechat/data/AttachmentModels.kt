package com.srisu.srisu.features.couplechat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** This descriptor, including its fresh file key, stays in the E2EE body. */
@Serializable
data class AttachmentDescriptor(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("message_id") val messageId: String,
    val algorithm: String,
    val key: String,
    @SerialName("ciphertext_size") val ciphertextSize: Int,
    val sha256: String,
)

@Serializable
data class ChatMedia(
    val descriptor: AttachmentDescriptor,
    val kind: String,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val thumbnail: String? = null,
    val waveform: List<Int> = emptyList(),
)

@Serializable
data class AttachmentReservation(
    @SerialName("device_id") val deviceId: String,
    @SerialName("attachment_id") val attachmentId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("ciphertext_size") val ciphertextSize: Int,
    val sha256: String,
)
@Serializable
data class AttachmentDevice(@SerialName("device_id") val deviceId: String)
@Serializable
data class AttachmentState(val id: String, val state: String)
@Serializable
data class MediaCapability(val capability: String, @SerialName("expires_in") val expiresIn: Int)

/** Held in memory while the platform validates and compresses selected media. */
data class PreparedChatMedia(val bytes: ByteArray, val kind: String, val mime: String, val width: Int = 0,
    val height: Int = 0, val durationMs: Long = 0, val thumbnail: String? = null, val waveform: List<Int> = emptyList())
