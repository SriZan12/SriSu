package com.srisu.srisu.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skia.Image
import org.jetbrains.skia.makeFromEncoded
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.stringWithFormat
import platform.UIKit.UIDevice
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation


actual fun getCountryFlagFromAssets(countryCode: String): ImageBitmap? {
    val resourceName = countryCode.lowercase()
    return try {
        val image = UIImage.imageNamed(resourceName)
        AppLogger.log("IMAGE ios = $image")
        image?.toImageBitmap()
    } catch (exception: Exception) {
        AppLogger.log("EXCEPTION = ${exception.message}")
        null
    }

}

fun UIImage.toImageBitmap(): ImageBitmap? {
    val imageData = UIImagePNGRepresentation(this)
    imageData ?: return null

    // Decode PNG data to Skia Image and convert to Compose ImageBitmap
    val skiaImage = Image.makeFromEncoded(imageData)
    return skiaImage.toComposeImageBitmap()
}


actual object AppContext


@OptIn(ExperimentalForeignApi::class)
actual fun readJsonFromAssets(fileName: String): String? {
    return try {
        val path = NSBundle.mainBundle.pathForResource(name = fileName, ofType = null)

        AppLogger.log("path ios = ${path}")

        if (path != null) {
            val fileContent = NSString.stringWithContentsOfFile(
                path,
                encoding = NSUTF8StringEncoding,
                error = null
            )
            fileContent
        } else {
            null
        }
    } catch (e: Exception) {
        println("Error reading JSON file: ${e.message}")
        null
    }
}

actual object PlatformInfo {

    actual val osVersion: Int
        get() = UIDevice.currentDevice.systemVersion.toInt()

    actual val appVersionName: String
        get() = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
            ?: "Unknown"

    actual val appVersionCode: String
        get() = NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String ?: "Unknown"
}

actual fun formatTime(seconds: Long): String {
    return NSString.stringWithFormat("%02d:%02d", seconds / 60, seconds % 60)

}