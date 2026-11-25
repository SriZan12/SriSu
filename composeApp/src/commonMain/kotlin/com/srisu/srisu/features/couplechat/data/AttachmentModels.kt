package com.srisu.srisu.features.couplechat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Presentation identity only; encrypted attachment metadata stays in the Matrix repository. */
@Serializable
data class AttachmentDescriptor(val id: String, val roomId: String, val messageId: String)

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

/** Held in memory while the platform validates and compresses selected media. */
data class PreparedChatMedia(val bytes: ByteArray, val kind: String, val mime: String, val width: Int = 0,
    val height: Int = 0, val durationMs: Long = 0, val thumbnail: String? = null, val waveform: List<Int> = emptyList())
