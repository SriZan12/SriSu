package com.srisu.srisu.utils

import platform.Foundation.NSString
import platform.Foundation.NSBundle
import platform.Foundation.stringWithFormat
import platform.UIKit.UIDevice


actual object AppContext


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