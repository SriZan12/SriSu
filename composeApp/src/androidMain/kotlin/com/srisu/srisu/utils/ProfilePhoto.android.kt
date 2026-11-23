package com.srisu.srisu.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Decode a bounded thumbnail, normalize orientation and upload only the prepared JPEG. */
internal suspend fun prepareProfilePhoto(path: String): MediaFile? = withContext(Dispatchers.IO) {
    try {
        val resolver = AppContext.get().contentResolver
        val uri = if (path.startsWith('/')) java.io.File(path).toUri() else path.toUri()
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > 40 * 1024 * 1024) return@withContext null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 3200) inSampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
        val output = ByteArrayOutputStream()
        try {
            val orientation = runCatching { bytes.inputStream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
                val ratio = minOf(1f, 1600f / maxOf(decoded.width, decoded.height))
                postScale(ratio, ratio)
            }
            val prepared = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try { if (!prepared.compress(Bitmap.CompressFormat.JPEG, 88, output)) return@withContext null }
            finally { if (prepared !== decoded) prepared.recycle() }
        } finally { decoded.recycle() }
        val jpeg = output.toByteArray()
        if (!profileImageDimensionsValid(jpeg)) return@withContext null
        MediaFile(id = null, fileName = "profile.jpg", mimeType = "image/jpeg", fileBytes = jpeg,
            fileSize = jpeg.size.toLong(), fileType = MediaType.IMAGE_ONLY)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
