package com.srisu.srisu.utils

import androidx.compose.ui.graphics.ImageBitmap


expect object AppContext

expect fun getCountryFlagFromAssets(countryCode: String): ImageBitmap?

expect fun readJsonFromAssets(fileName: String): String?

expect object PlatformInfo {
    val osVersion: Int
    val appVersionName: String
    val appVersionCode: String
}

expect fun formatTime(seconds: Long): String




