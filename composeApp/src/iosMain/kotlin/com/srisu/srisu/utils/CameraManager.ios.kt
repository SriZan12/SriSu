package com.srisu.srisu.utils

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Composable actual fun rememberCameraManager(onResult: (MediaFile) -> Unit, onError: () -> Unit): CameraManager {
    val controller = rememberUIViewController()
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    val delegate = remember { CameraDelegate({ result(it) }, { error() }) }
    val available = UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
    return CameraManager(available) {
        if (!available) error() else AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { allowed ->
            dispatch_async(dispatch_get_main_queue()) {
                if (!allowed) error() else {
                    val picker = UIImagePickerController()
                    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    picker.delegate = delegate
                    controller.presentViewController(picker, animated = true, completion = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CameraDelegate(val result: (MediaFile) -> Unit, val error: () -> Unit) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return error()
        val (width, height) = image.size.useContents { width to height }
        val ratio = minOf(1.0, 1600.0 / maxOf(width, height))
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width * ratio, height * ratio), true, 1.0)
        val data: NSData?
        try { image.drawInRect(CGRectMake(0.0, 0.0, width * ratio, height * ratio)); data = UIGraphicsGetImageFromCurrentImageContext()?.let { UIImageJPEGRepresentation(it, .88) } }
        finally { UIGraphicsEndImageContext() }
        if (data == null || data.length > 5uL * 1024uL * 1024uL) return error()
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        result(MediaFile(id = null, fileName = "cover.jpg", mimeType = "image/jpeg", fileBytes = bytes, fileSize = bytes.size.toLong(), fileType = MediaType.IMAGE_ONLY))
    }
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) { picker.dismissViewControllerAnimated(true, completion = null) }
}
