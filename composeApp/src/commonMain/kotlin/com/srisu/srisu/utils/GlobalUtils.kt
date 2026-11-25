package com.srisu.srisu.utils



expect object AppContext

expect object PlatformInfo {
    val osVersion: Int
    val appVersionName: String
    val appVersionCode: String
}

expect fun formatTime(seconds: Long): String




