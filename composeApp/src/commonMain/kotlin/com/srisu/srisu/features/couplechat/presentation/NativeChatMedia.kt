package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Ordered, prepared photos. Empty photos with no failures means the picker was cancelled. */
data class NativePhotoBatch(val photos: List<ByteArray>, val failedCount: Int = 0)

/** One foreground media owner per conversation. Bytes are never navigation arguments.
 * Native callbacks contain bounded metadata or a fixed error code, never file paths.
 */
interface NativeChatMedia {
    fun safetyQr(text: String): ByteArray?
    fun pick(source: String, result: (String, ByteArray?) -> Unit)
    /** Select up to [limit] photos, prepare serially off the UI thread, then deliver once. */
    fun pickPhotos(limit: Int, result: (NativePhotoBatch) -> Unit)
    fun startRecording(result: (String, ByteArray?) -> Unit)
    fun stopRecording(discard: Boolean)
    fun play(bytes: ByteArray, mime: String, result: (String) -> Unit)
    fun playback(action: String, value: Double)
    fun export(bytes: ByteArray, mime: String, result: (String) -> Unit)
    fun interrupt()
    fun close()
}

object NativeChatMediaRuntime {
    private var factory: (() -> NativeChatMedia)? = null
    fun installFactory(value: () -> NativeChatMedia) { factory=value }
    fun create(): NativeChatMedia = factory?.invoke() ?: checkNotNull(driver)
    var driver: NativeChatMedia? = null
        private set
    fun install(value: NativeChatMedia) { driver = value }
}

@Composable internal expect fun rememberNativeChatMedia(): NativeChatMedia
internal expect fun decodePrivatePhoto(bytes: ByteArray): ImageBitmap?

@Composable internal expect fun NativeChatVideo(native: NativeChatMedia, modifier: androidx.compose.ui.Modifier)
