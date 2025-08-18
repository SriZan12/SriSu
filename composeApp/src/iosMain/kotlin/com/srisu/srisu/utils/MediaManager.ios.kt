package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import platform.posix.memcpy

actual class GalleryManager actual constructor(private val onLaunch: () -> Unit) {
    actual fun launch() {
        onLaunch()
    }
}

@Composable
actual fun rememberGalleryManager(
    onResult: (List<String?>?) -> Unit,
    mediaType: MediaType?
): GalleryManager {
    val viewController = rememberUIViewController()

    val pickerDelegate = remember {
        PickerDelegate { selectedUris ->
            onResult(selectedUris.map { it })
        }
    }

    val picker = UIImagePickerController().apply {
        delegate = pickerDelegate
        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary

        mediaTypes = when (mediaType) {
            MediaType.IMAGE_ONLY -> listOf("public.image")
            MediaType.VIDEO_ONLY -> listOf("public.movie")
            MediaType.IMAGE_AND_VIDEO -> listOf("public.image", "public.movie")
            MediaType.MIME_TYPE, null -> listOf("public.image", "public.movie") // default both
            MediaType.NOTHING -> listOf("public.image")
        }
    }

    return remember {
        GalleryManager(onLaunch = {
            viewController.presentViewController(picker, animated = true, completion = null)
        })
    }
}

//class PickerDelegate(
//    private val onResult: (List<NSURL>) -> Unit
//) : NSObject(), UIImagePickerControllerDelegateProtocol,
//    UINavigationControllerDelegateProtocol {
//
//    override fun imagePickerController(
//        picker: UIImagePickerController,
//        didFinishPickingImage: UIImage,
//        editingInfo: Map<Any?, *>?
//    ) {
//
//        AppLogger.log("image converted ${didFinishPickingImage.toImageBitmap()} $editingInfo")
//
//        val uri = editingInfo?.get(UIImagePickerControllerImageURL) as? NSURL
//        picker.dismissViewControllerAnimated(true, completion = null)
//
//        if (uri != null) {
//            onResult(listOf(uri))
//        } else {
//            onResult(emptyList())
//        }
//    }
//
//    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
//        picker.dismissViewControllerAnimated(true, completion = null)
//        onResult(emptyList())
//    }
//}


class PickerDelegate(
    private val onResult: (List<String>) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val uri = didFinishPickingMediaWithInfo[UIImagePickerControllerImageURL] as? NSURL

        if (uri != null) {
            val uriString = uri.absoluteString ?: ""
            AppLogger.log("Image picked with URI: $uriString")
            onResult(listOf(uriString))
        } else {
            AppLogger.log("No image URL found in picker result")
            onResult(emptyList())
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        AppLogger.log("Image picker cancelled")
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

    /*actual suspend fun createMediaFileFromPath(path: String): MediaFile? {
        val fileUrl = NSURL.fileURLWithPath(path)

        AppLogger.log("FILE URL = $fileUrl")

        val fileName = fileUrl.lastPathComponent ?: return null
        val mimeType = getMimeType(fileUrl)
        val fileType = determineFileType(mimeType)

        val fileData = NSData.dataWithContentsOfURL(fileUrl) ?: return null
        val fileBytes = fileData.toByteArray()
        val fileSize = fileBytes.size.toLong()


        val mediaFile =  MediaFile(
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            fileBytes = fileBytes,
            fileType = fileType
        )

        AppLogger.log("MEDIA FILE ios = ${Json.encodeToString(mediaFile)}")


        return mediaFile
    }*/

    actual suspend fun createMediaFileFromPath(path: String?, id: Int?,removed: Boolean?): MediaFile? {

        path?.let {
            AppLogger.log("FILE PATH = $path")

            // Ensure the path is a valid file URL
            val fixedPath = if (path.startsWith("file://")) path else "file://$path"
            val fileUrl = NSURL.URLWithString(fixedPath) ?: return null

            AppLogger.log("FILE URL = $fileUrl")

            val fileName = fileUrl.lastPathComponent ?: return null
            val mimeType = getMimeType(fileUrl)
            val fileType = determineFileType(mimeType)

            val fileData = NSData.dataWithContentsOfURL(fileUrl) ?: return null
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

        return MediaFile(
            id = id,
            url = path,
            removed = removed
        )
    }


    private fun getMimeType(url: NSURL): String {
        val pathExtension = url.pathExtension ?: return "application/octet-stream"
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
}

//actual fun saveImageToCache(bytes: ByteArray): File {
//    val file = File.createTempFile("image_", ".jpg", AppContext.get().cacheDir)
//    file.writeBytes(bytes)
//    return file
//}

