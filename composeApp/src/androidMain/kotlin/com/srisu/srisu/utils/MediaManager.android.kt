package com.srisu.srisu.utils

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.srisu.srisu.core.logger.AppLogger
import java.io.File
import java.net.URLConnection
import androidx.core.net.toUri
import kotlin.contracts.contract

actual class GalleryManager actual constructor(private val onLaunch: () -> Unit) {

    actual fun launch() {
        onLaunch()
    }
}


@Composable
actual fun rememberGalleryManager(
    onResult: (List<String?>?) -> Unit,
    mediaType: MediaType?,
    isMultiple: Boolean
): GalleryManager {

    if (!isMultiple) {
        val pickMedia =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                onResult(uri?.let { listOf(it.toString()) })
            }

        return remember {
            GalleryManager(onLaunch = {
                val request = PickVisualMediaRequest(
                    mediaType = when (mediaType) {
                        MediaType.IMAGE_ONLY ->
                            ActivityResultContracts.PickVisualMedia.ImageOnly

                        MediaType.VIDEO_ONLY ->
                            ActivityResultContracts.PickVisualMedia.VideoOnly

                        MediaType.IMAGE_AND_VIDEO,
                        MediaType.MIME_TYPE,
                        null ->
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo

                        MediaType.NOTHING ->
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                    }
                )
                pickMedia.launch(request)
            })
        }

    } else {
        val pickMultipleMedia =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickMultipleVisualMedia(
                    maxItems = 5
                ),
            ) { uris ->
                onResult(
                    uris.takeIf { it.isNotEmpty() }?.map { it.toString() }
                )
            }

        return remember {
            GalleryManager(onLaunch = {
                val request = PickVisualMediaRequest(
                    mediaType = when (mediaType) {
                        MediaType.IMAGE_ONLY ->
                            ActivityResultContracts.PickVisualMedia.ImageOnly

                        MediaType.VIDEO_ONLY ->
                            ActivityResultContracts.PickVisualMedia.VideoOnly

                        MediaType.IMAGE_AND_VIDEO,
                        MediaType.MIME_TYPE,
                        null ->
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo

                        MediaType.NOTHING ->
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                    },
                )
                pickMultipleMedia.launch(request)
            })
        }
    }
}


actual class FileManager {

    private val context = AppContext.get()

    actual suspend fun createMediaFileFromPath(
        path: String?,
        id: Int?,
        removed: Boolean?
    ): MediaFile? {
        try {
            path?.let {
                val uri = path.toUri()

                val contentResolver = context.contentResolver
                val fileName = getFileName(uri) ?: return null
                val mimeType =
                    contentResolver.getType(uri) ?: URLConnection.guessContentTypeFromName(fileName)
                    ?: "application/octet-stream"
                val fileType = determineFileType(mimeType)

                val inputStream = contentResolver.openInputStream(uri) ?: return null
                val fileBytes = inputStream.use { it.readBytes() }
                val fileSize = fileBytes.size.toLong()

                return MediaFile(
                    id = id,
                    removed = removed,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    fileBytes = fileBytes,
                    fileType = fileType
                )
            }
        } catch (exception: Exception) {
            AppLogger.log("INSIDE EXCEPTION = Error creating media file from path: ${exception.message}")
            return MediaFile(
                id = id,
                url = path,
                removed = removed
            )
        }


        return MediaFile(
            id = id,
            url = path,
            removed = removed
        )

    }

    /*    private fun getFileName(uri: Uri): String? {
            return uri.path?.let { "${File(it).name}.${File(it).extension}" }
        }*/

    private fun getFileName(uri: Uri): String? {
        val contentResolver = context.contentResolver

        // First, try to query content resolver for the actual file name
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }


    private fun determineFileType(mimeType: String): MediaType {
        return when {
            mimeType.startsWith("image/") -> MediaType.IMAGE_ONLY
            mimeType.startsWith("video/") -> MediaType.VIDEO_ONLY
//            mimeType.startsWith("audio/") -> MediaType.
//            mimeType.startsWith("application/") -> MediaType.DOCUMENT
//            else -> MediaType.UNKNOWN

            else -> {
                MediaType.NOTHING
            }
        }
    }
}


// In androidMain
//actual fun saveImageToCache(bytes: ByteArray): File {
//    val file = File.createTempFile("image_", ".jpg", AppContext.get().cacheDir)
//    file.writeBytes(bytes)
//    return file
//}
