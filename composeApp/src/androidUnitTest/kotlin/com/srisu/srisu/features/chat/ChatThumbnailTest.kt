package com.srisu.srisu.features.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.srisu.srisu.features.couplechat.presentation.AndroidChatMedia
import com.srisu.srisu.features.couplechat.presentation.preparePrivateThumbnail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatThumbnailTest {
    @Test fun newlyPreparedPhotosKeepSharpBoundedPreviewsAndLegacyOriginalsCanBeDownsampled() {
        val bitmap = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(2048 * 1536) { i ->
            // Deliberately detailed synthetic image, rather than an easily compressed solid color.
            val x = i % 2048; val y = i / 2048
            android.graphics.Color.rgb((x * 11 + y) % 256, (y * 7 + x) % 256, (x + y * 3) % 256) // theme-audit: image pixels
        }
        bitmap.setPixels(pixels, 0, 2048, 0, 0, 2048, 1536)
        try {
            val (metadata, original) = AndroidChatMedia.photo(bitmap)
            val thumbnail = android.util.Base64.decode(metadata.getString("thumbnail"), android.util.Base64.NO_WRAP)
            assertTrue(thumbnail.size <= 256 * 1024)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.size, bounds)
            assertTrue(bounds.outWidth >= 640)
            assertTrue(bounds.outWidth <= 1024)
            assertEquals(2048, metadata.getInt("width"))
            val legacyPreview = assertNotNull(preparePrivateThumbnail(original))
            assertTrue(legacyPreview.size <= 256 * 1024)
            BitmapFactory.decodeByteArray(legacyPreview, 0, legacyPreview.size, bounds)
            assertTrue(bounds.outWidth in 640..1024)
            assertNull(preparePrivateThumbnail(byteArrayOf(1, 2, 3)))
        } finally { bitmap.recycle() }
    }
}
