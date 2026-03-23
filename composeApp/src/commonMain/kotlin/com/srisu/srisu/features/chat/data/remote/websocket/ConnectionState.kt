package com.srisu.srisu.features.chat.data.remote.websocket

sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    object Reconnecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}