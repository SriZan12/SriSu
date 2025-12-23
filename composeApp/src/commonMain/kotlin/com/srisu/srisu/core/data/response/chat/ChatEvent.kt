package com.srisu.srisu.core.data.response.chat

data class ChatEvent<T>(
    val action: String,
    val message: String,
    val data: T
)
