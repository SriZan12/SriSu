package com.srisu.srisu.features.moments.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MOMENT_VIDEO_MAX_BYTES = 100L * 1024 * 1024
const val MOMENT_VIDEO_MAX_DURATION = 60_000L

@Serializable data class LocalMomentVideo(val path: String, val size: Long, val durationMs: Long,
    val width: Int, val height: Int)

@Serializable data class MomentVideoInfo(val id: String, val state: String,
    @SerialName("source_bytes") val sourceBytes: Long = 0,
    @SerialName("received_bytes") val receivedBytes: Long = 0,
    @SerialName("chunk_bytes") val chunkBytes: Int = 2 * 1024 * 1024,
    @SerialName("duration_ms") val durationMs: Long = 0,
    val width: Int = 0, val height: Int = 0,
    @SerialName("error_code") val errorCode: String = "")

@Serializable data class MomentPlayback(val url: String,
    @SerialName("cookie_name") val cookieName: String = "",
    @SerialName("cookie_value") val cookieValue: String = "",
    @SerialName("cookie_path") val cookiePath: String = "",
    @SerialName("expires_in") val expiresIn: Int = 120,
    @SerialName("duration_ms") val durationMs: Long = 0)

data class MomentVideoSelection(val video: LocalMomentVideo? = null, val poster: ByteArray? = null,
    val error: String? = null)
data class MomentPlaybackEvent(val state: String, val positionMs: Long = 0, val durationMs: Long = 0,
    val firstFrame: Boolean = false)

interface MomentVideoPicker {
    fun pick(result: (MomentVideoSelection) -> Unit)
    fun cancel()
    fun close()
}

object MomentVideoPickerRuntime {
    private var factory: (() -> MomentVideoPicker)? = null
    fun install(value: () -> MomentVideoPicker) { factory = value }
    fun create(): MomentVideoPicker = checkNotNull(factory) { "Video picker unavailable" }.invoke()
}

@Composable expect fun rememberMomentVideoPicker(): MomentVideoPicker
/** Only private app-owned scratch paths are accepted; reads are bounded to one chunk. */
expect suspend fun readMomentVideoChunk(video: LocalMomentVideo, offset: Long, count: Int): ByteArray
expect fun removeMomentVideoFile(video: LocalMomentVideo)

@Composable expect fun MomentVideoPlayer(source: MomentPlayback, playing: Boolean, muted: Boolean,
    modifier: Modifier, embedded: Boolean = false, onEvent: (MomentPlaybackEvent) -> Unit)
