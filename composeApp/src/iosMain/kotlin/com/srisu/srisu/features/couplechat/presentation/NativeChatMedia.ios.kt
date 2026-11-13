package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

@Composable internal actual fun rememberNativeChatMedia(): NativeChatMedia = remember {
    NativeChatMediaRuntime.create()
}
internal actual fun decodePrivatePhoto(bytes: ByteArray): ImageBitmap? = try {
    val image=Image.makeFromEncoded(bytes)
    if(image.width in 1..4096 && image.height in 1..4096) image.toComposeImageBitmap() else null
} catch (_: Exception) { null }

/** Swift supplies a child AVPlayerViewController; navigation stays in Compose. */
object NativeChatVideoRuntime {
    private var factory: ((NativeChatMedia) -> platform.UIKit.UIViewController)? = null
    fun install(value: (NativeChatMedia) -> platform.UIKit.UIViewController) { factory=value }
    internal fun create(native: NativeChatMedia) = checkNotNull(factory)(native)
}
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable internal actual fun NativeChatVideo(native: NativeChatMedia, modifier: androidx.compose.ui.Modifier) {
    androidx.compose.ui.viewinterop.UIKitViewController(factory={NativeChatVideoRuntime.create(native)},modifier=modifier)
}
