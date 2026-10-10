package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** One foreground media owner per conversation. Bytes are never navigation arguments.
 * Native callbacks contain bounded metadata or a fixed error code, never file paths.
 */
interface NativeChatMedia {
    fun safetyQr(text: String): ByteArray?
    fun pick(source: String, result: (String, ByteArray?) -> Unit)
    fun startRecording(result: (String, ByteArray?) -> Unit)
    fun stopRecording(discard: Boolean)
    fun play(bytes: ByteArray, mime: String, result: (String) -> Unit)
    fun playback(action: String, value: Double)
    fun export(bytes: ByteArray, mime: String, result: (String) -> Unit)
    fun interrupt()
    fun close()
}

object NativeChatMediaRuntime {
    var driver: NativeChatMedia? = null
        private set
    fun install(value: NativeChatMedia) { driver = value }
}

@Composable internal expect fun rememberNativeChatMedia(): NativeChatMedia
internal expect fun decodePrivatePhoto(bytes: ByteArray): ImageBitmap?
