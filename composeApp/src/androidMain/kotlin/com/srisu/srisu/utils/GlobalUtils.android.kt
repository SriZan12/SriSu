package com.srisu.srisu.utils

import android.app.Application
import android.content.Context
import android.os.Build

actual object AppContext {
    private lateinit var application: Application

    fun setUp(context: Context) {
        application = context as Application
    }

    fun get(): Context {
        if (AppContext::application.isInitialized.not()) throw Exception("Application not initialized.")
        return application.applicationContext
    }
}

actual object PlatformInfo {

    actual val osVersion: Int
        get() = Build.VERSION.SDK_INT

    actual val appVersionName: String
        get() = AppContext.get().packageManager.getPackageInfo(
            AppContext.get().packageName,
            0
        ).versionName ?: "Unknown"

    actual val appVersionCode: String
        get() = ""

}

actual fun formatTime(seconds: Long): String {
    return String.format("%02d:%02d", seconds / 60, seconds % 60)
}