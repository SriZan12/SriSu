package com.srisu.srisu.utils

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.*
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.*
import platform.ImageIO.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/** ImageIO downsampling handles HEIC and orientation without a full camera-size bitmap. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun prepareProfilePhoto(path: String): MediaFile? = withContext(Dispatchers.Default) {
    try {
        val url = if (path.startsWith("file://")) NSURL.URLWithString(path) else NSURL.fileURLWithPath(path)
        val handle = NSFileHandle.fileHandleForReadingAtPath(url?.path ?: return@withContext null) ?: return@withContext null
        val data = try { handle.readDataOfLength((40 * 1024 * 1024 + 1).toULong()) } finally { handle.closeFile() }
        if (data.length == 0UL || data.length > (40 * 1024 * 1024).toULong()) return@withContext null
        val retainedData = CFBridgingRetain(data)
        val options = CFDictionaryCreateMutable(null, 4, null, null) ?: return@withContext null
        val maxPixels = CFBridgingRetain(1600)
        CFDictionaryAddValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
        CFDictionaryAddValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
        CFDictionaryAddValue(options, kCGImageSourceThumbnailMaxPixelSize, maxPixels)
        CFDictionaryAddValue(options, kCGImageSourceShouldCache, kCFBooleanFalse)
        try {
            val source = CGImageSourceCreateWithData(retainedData?.reinterpret(), null) ?: return@withContext null
            try {
                val thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0u, options?.reinterpret()) ?: return@withContext null
                try {
                    val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(thumbnail), .88) ?: return@withContext null
                    if (jpeg.length == 0UL || jpeg.length > (5 * 1024 * 1024).toULong()) return@withContext null
                    val bytes = ByteArray(jpeg.length.toInt())
                    bytes.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
                    MediaFile(id = null, fileName = "profile.jpg", mimeType = "image/jpeg", fileBytes = bytes,
                        fileSize = bytes.size.toLong(), fileType = MediaType.IMAGE_ONLY)
                } finally { CGImageRelease(thumbnail) }
            } finally { CFRelease(source) }
        } finally { CFRelease(options); if (maxPixels != null) CFRelease(maxPixels); if (retainedData != null) CFRelease(retainedData) }
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
