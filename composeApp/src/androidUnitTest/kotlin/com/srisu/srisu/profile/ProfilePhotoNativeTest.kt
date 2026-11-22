package com.srisu.srisu.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.srisu.srisu.utils.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilePhotoNativeTest {
    @Test fun cameraOriginalIsDownsampledAndOrientationApplied() = runBlocking {
        AppContext.setUp(RuntimeEnvironment.getApplication())
        val file = File.createTempFile("synthetic", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
        try {
            val image = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.rgb(128, 90, 180)) // theme-audit: image pixels
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 95, it) }; image.recycle()
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
            val prepared = assertNotNull(prepareProfilePhoto(file.absolutePath))
            assertEquals("image/jpeg", prepared.mimeType)
            val bytes = assertNotNull(prepared.fileBytes)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            assertEquals(1200, bounds.outWidth); assertEquals(1600, bounds.outHeight)
            assertTrue(bytes.size < 5 * 1024 * 1024)
            file.writeText("not an image")
            assertNull(prepareProfilePhoto(file.absolutePath))
        } finally { file.delete() }
    }
}
