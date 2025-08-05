package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import coil3.Uri
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
)

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
