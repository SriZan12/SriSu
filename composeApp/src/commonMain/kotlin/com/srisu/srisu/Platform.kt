package com.srisu.srisu

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform