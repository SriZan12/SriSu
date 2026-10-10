package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

@Composable internal actual fun rememberNativeChatMedia(): NativeChatMedia = remember {
    checkNotNull(NativeChatMediaRuntime.driver) { "Native chat media must be installed before starting SriSu" }
}
internal actual fun decodePrivatePhoto(bytes: ByteArray): ImageBitmap? = try {
    val image=Image.makeFromEncoded(bytes)
    if(image.width in 1..4096 && image.height in 1..4096) image.toComposeImageBitmap() else null
} catch (_: Exception) { null }
