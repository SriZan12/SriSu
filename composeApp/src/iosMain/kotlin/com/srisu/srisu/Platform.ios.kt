package com.srisu.srisu

import io.github.aakira.napier.DebugAntilog
import platform.UIKit.UIDevice
import io.github.aakira.napier.Napier

class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

fun debugBuild() {
    Napier.base(
        DebugAntilog()
    )
}
