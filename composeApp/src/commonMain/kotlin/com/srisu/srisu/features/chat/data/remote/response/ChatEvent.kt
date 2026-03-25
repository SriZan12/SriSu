package com.srisu.srisu.features.chat.data.remote.response

data class ChatEvent<T>(
    val action: String,
    val message: String,
    val data: T
)
