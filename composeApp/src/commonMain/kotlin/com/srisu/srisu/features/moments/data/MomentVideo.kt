package com.srisu.srisu.features.moments.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MOMENT_VIDEO_MAX_BYTES = 100L * 1024 * 1024
const val MOMENT_VIDEO_MAX_DURATION = 60_000L

internal const val MOMENT_VIDEO_SIZE_ERROR = "This video is too large. Choose one under 100 MiB (about 105 MB)."

/** Keep duration, unreadable files and size failures distinct in both clients. */
internal fun momentVideoSelectionError(size: Long, durationMs: Long): String? = when {
    size <= 0 -> "This video file is empty or could not be read. Select it again."
    size > MOMENT_VIDEO_MAX_BYTES -> MOMENT_VIDEO_SIZE_ERROR
    durationMs < 100 -> "This video's duration could not be read. Choose another video."
    durationMs > MOMENT_VIDEO_MAX_DURATION -> "This video is longer than 60 seconds. Choose a shorter clip."
    else -> null
}

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
    /** Capture privately in the app, then return through the same preparation pipeline. */
    fun record(result: (MomentVideoSelection) -> Unit)
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
