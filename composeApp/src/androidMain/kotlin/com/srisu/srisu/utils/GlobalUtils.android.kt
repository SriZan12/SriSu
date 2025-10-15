package com.srisu.srisu.utils

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException

actual suspend fun getCountryFlagFromAssets(countryCode: String): ImageBitmap? {
    val context = AppContext.get()
    val resourceName = countryCode.lowercase()
    val assetPath = "Countries/${resourceName}.imageset/${resourceName}.png"

    return try {
        val inputStream = context.assets.open(assetPath)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        bitmap?.asImageBitmap()
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

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

actual fun readJsonFromAssets(fileName: String): String? {
    return try {
        val context = AppContext.get()
        context.assets.open(fileName).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        e.printStackTrace()
        null
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