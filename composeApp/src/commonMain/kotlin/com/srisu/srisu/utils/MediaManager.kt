package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import coil3.Uri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable

enum class MediaType {
    IMAGE_ONLY,
    VIDEO_ONLY,
    IMAGE_AND_VIDEO,
    MIME_TYPE,
    NOTHING
}

@Serializable
data class MediaFile(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val fileBytes: ByteArray,
    val fileType: MediaType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MediaFile

        if (fileSize != other.fileSize) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (!fileBytes.contentEquals(other.fileBytes)) return false
        if (fileType != other.fileType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileSize.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileBytes.contentHashCode()
        result = 31 * result + fileType.hashCode()
        return result
    }
}

@Composable
expect fun rememberGalleryManager(
    onResult: (List<String?>?) -> Unit,
    mediaType: MediaType?
): GalleryManager

expect class GalleryManager(
    onLaunch: () -> Unit
) {
    fun launch()
}

expect class FileManager() {
    suspend fun createMediaFileFromPath(path: String?): MediaFile?
}

suspend fun getMediaFileFromUri(uri: Uri?): MediaFile? {
    if (uri == null) return null
    val fileManager = FileManager()
    return fileManager.createMediaFileFromPath(uri.toString())
}
/**
 * Downloads an image from the given URL and saves it to a temporary file
 * in the device's cache directory.
 *
 * Flow:
 *
 * [Your App]
 *     |
 *     |   URL: http://yourserver.com/media/profiles/22.jpg
 *     v
 * Open network connection (HttpURLConnection)
 *     |
 *     v
 * Read image bytes from server (InputStream)
 *     |
 *     v
 * Write image bytes to temporary file (OutputStream → File)
 *     |
 *     v
 * Return the File (stored in cache dir, usable for upload)
 */
suspend fun downloadImageBytes(imageUrl: String, httpClient: HttpClient): ByteArray? {
    return try {
        val response: HttpResponse = httpClient.get(imageUrl)
        if (response.status.value in 200..299) {
            response.bodyAsChannel().toByteArray()
        } else {
            null
        }
    } catch (e: Exception) {
        println("Image download failed: ${e.message}")
        null
    }
}

//expect fun saveImageToCache(bytes: ByteArray): Any

