package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import coil3.Uri
import kotlinx.serialization.Required
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
    @Required val id: Int?,
    @Required val removed: Boolean? = false,
    @Required val fileName: String? = null,
    @Required val mimeType: String? = null,
    @Required val fileSize: Long? = null,
    @Required val fileBytes: ByteArray? = null,
    @Required val fileType: MediaType? = null,
    @Required val url: String? = null
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
    mediaType: MediaType?,
    isMultiple: Boolean
): GalleryManager


expect class GalleryManager(
    onLaunch: () -> Unit
) {
    fun launch()
}

expect class FileManager() {
    suspend fun createMediaFileFromPath(path: String?, id: Int?, removed: Boolean?): MediaFile?
}

suspend fun getMediaFileFromUri(uri: Uri?, id: Int?,removed: Boolean?): MediaFile? {
    val fileManager = FileManager()
    return fileManager.createMediaFileFromPath(path = uri.toString(), id = id, removed = removed)
}
