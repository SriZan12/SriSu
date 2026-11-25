package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use

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

internal actual fun preparePrivateThumbnail(bytes: ByteArray): ByteArray? = try {
    Image.makeFromEncoded(bytes).use { image ->
        if (image.width !in 1..4096 || image.height !in 1..4096) null
        else if (bytes.size <= 256 * 1024 && maxOf(image.width, image.height) <= 1024) bytes
        else {
            var result: ByteArray? = null
            for (edge in listOf(1024, 800, 640)) {
                val ratio = minOf(1f, edge.toFloat() / maxOf(image.width, image.height))
                org.jetbrains.skia.Surface.makeRasterN32Premul(maxOf(1, (image.width * ratio).toInt()), maxOf(1, (image.height * ratio).toInt())).use { surface ->
                    surface.canvas.drawImageRect(image, 0f, 0f, image.width.toFloat(), image.height.toFloat(),
                        0f, 0f, surface.width.toFloat(), surface.height.toFloat(), org.jetbrains.skia.SamplingMode.LINEAR, null, true)
                    surface.makeImageSnapshot().use { preview ->
                        for (quality in listOf(85, 78, 70)) {
                            val encoded = preview.encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, quality)?.use { it.bytes }
                            if (encoded != null && encoded.size <= 256 * 1024) { result = encoded; break }
                        }
                    }
                }
                if (result != null) break
            }
            result
        }
    }
} catch (_: Exception) { null }
