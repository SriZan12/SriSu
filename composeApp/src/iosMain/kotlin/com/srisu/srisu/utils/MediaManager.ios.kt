package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerImageURL
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.Foundation.*
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_notify
import platform.posix.memcpy

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

    val viewController = rememberUIViewController()

    val multiPickerDelegate = remember {
        MultiPickerDelegate { uris ->
            onResult(uris)
        }
    }

    val singlePickerDelegate = remember {
        PickerDelegate { uris ->
            onResult(uris)
        }
    }

    return remember {
        GalleryManager(onLaunch = {

            if (isMultiple) {
                val config = PHPickerConfiguration().apply {
                    selectionLimit = 5
                    filter = when (mediaType) {
                        MediaType.IMAGE_ONLY -> PHPickerFilter.imagesFilter()
                        MediaType.VIDEO_ONLY -> PHPickerFilter.videosFilter()
                        MediaType.IMAGE_AND_VIDEO,
                        MediaType.MIME_TYPE,
                        null -> null

                        MediaType.NOTHING -> PHPickerFilter.imagesFilter()
                    }
                }

                val picker = PHPickerViewController(config).apply {
                    delegate = multiPickerDelegate
                }

                viewController.presentViewController(
                    picker,
                    animated = true,
                    completion = null
                )

            } else {
                val picker = UIImagePickerController().apply {
                    delegate = singlePickerDelegate
                    sourceType =
                        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary

                    mediaTypes = when (mediaType) {
                        MediaType.IMAGE_ONLY -> listOf("public.image")
                        MediaType.VIDEO_ONLY -> listOf("public.movie")
                        MediaType.IMAGE_AND_VIDEO,
                        MediaType.MIME_TYPE,
                        null -> listOf("public.image", "public.movie")

                        MediaType.NOTHING -> listOf("public.image")
                    }
                }

                viewController.presentViewController(
                    picker,
                    animated = true,
                    completion = null
                )
            }
        })
    }
}


class MultiPickerDelegate(
    private val onResult: (List<String>) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>
    ) {

        val didFinishPickingPHPickerResult = didFinishPicking.filterIsInstance<PHPickerResult>()
        picker.dismissViewControllerAnimated(true, completion = null)


        if (didFinishPicking.isEmpty()) {
            onResult(emptyList())
            return
        }

        val uris = mutableListOf<String>()
        val group = dispatch_group_create()

        didFinishPickingPHPickerResult.forEach { result ->
            val provider = result.itemProvider

            if (provider.hasItemConformingToTypeIdentifier(typeIdentifier = "public.image")) {
                dispatch_group_enter(group)

                provider.loadFileRepresentationForTypeIdentifier(typeIdentifier = "public.image") { url, _ ->
                    url?.let {
                        val copiedPath = FileManager().copyToAppCache(url = it)
                        copiedPath?.let { safePath ->
                            uris.add(safePath)
                        }
                    }
                    dispatch_group_leave(group)
                }

            }
        }

        dispatch_group_notify(group, dispatch_get_main_queue()) {
            picker.dismissViewControllerAnimated(flag = true, completion = null)
            onResult(uris)
        }
    }


}


class PickerDelegate(
    private val onResult: (List<String>) -> Unit
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val uri =
            didFinishPickingMediaWithInfo[UIImagePickerControllerImageURL] as? NSURL

        if (uri != null) {
            onResult(listOf(uri.absoluteString ?: ""))
        } else {
            onResult(emptyList())
        }
    }

    override fun imagePickerControllerDidCancel(
        picker: UIImagePickerController
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(emptyList())
    }
}


@Composable
fun rememberUIViewController(): UIViewController {
    return remember {
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: error("Unable to find root UIViewController")
    }
}


actual class FileManager {

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun createMediaFileFromPath(
        path: String?,
        id: Int?,
        removed: Boolean?
    ): MediaFile? {

        val defaultMediaFile = defaultMediaFile(id, path ?: "", removed)

        try {

            path?.let {
                // Ensure the path is a valid file URL
                val fixedPath = if (path.startsWith("file://")) path else "file://$path"
                val fileUrl = NSURL.URLWithString(fixedPath)

                val fileName = fileUrl?.lastPathComponent ?: return defaultMediaFile
                val mimeType = getMimeType(fileUrl)
                val fileType = determineFileType(mimeType)

                val fileData = NSData.dataWithContentsOfURL(fileUrl) ?: return defaultMediaFile
                val fileBytes = fileData.toByteArray()
                val fileSize = fileBytes.size.toLong()

                val mediaFile = MediaFile(
                    id = id,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    fileBytes = fileBytes,
                    fileType = fileType,
                    removed = removed
                )

                return mediaFile
            }
        } catch (exception: Exception) {
            AppLogger.log("INSIDE EXCEPTION = Error creating media file from path: ${exception.message}")
        }

        return defaultMediaFile
    }

    fun defaultMediaFile(id: Int?, path: String, removed: Boolean?): MediaFile {
        return MediaFile(
            id = id,
            url = path,
            removed = removed
        )
    }


    private fun getMimeType(url: NSURL): String {
        AppLogger.log("Path extension = ${url.pathExtension}")
        AppLogger.log("Path extension() = ${url.pathExtension()}")
        val pathExtension =
            url.pathExtension ?: url.pathExtension() ?: return "application/octet-stream"
        return when (pathExtension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun determineFileType(mimeType: String): MediaType {
        return when {
            mimeType.startsWith("image/") -> MediaType.IMAGE_ONLY
            mimeType.startsWith("video/") -> MediaType.VIDEO_ONLY
//            mimeType.startsWith("audio/") -> MediaType.AUDIO // TODO: WILL IMPLEMENT AS NEEDED IN FUTURE.
//            mimeType.startsWith("application/") -> MediaType.DOCUMENT
            else -> MediaType.NOTHING
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        return ByteArray(this.length.toInt()).apply {
            usePinned {
                memcpy(it.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
            }
        }
    }


    @OptIn(ExperimentalForeignApi::class)
    fun copyToAppCache(url: NSURL): String? {
        val fileManager = NSFileManager.defaultManager
        val cacheDir =
            fileManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
                .firstOrNull() as? NSURL ?: return null

        val destination =
            cacheDir.URLByAppendingPathComponent(url.lastPathComponent ?: return null)

        try {
            destination?.let {
                fileManager.removeItemAtURL(destination, null) // overwrite safety
                fileManager.copyItemAtURL(url, destination, null)
                return destination.absoluteString
            }
        } catch (e: Exception) {
            AppLogger.log("File copy failed: ${e.message}")
            return null
        }

        return null
    }

}

//actual fun saveImageToCache(bytes: ByteArray): File {
//    val file = File.createTempFile("image_", ".jpg", AppContext.get().cacheDir)
//    file.writeBytes(bytes)
//    return file
//}

