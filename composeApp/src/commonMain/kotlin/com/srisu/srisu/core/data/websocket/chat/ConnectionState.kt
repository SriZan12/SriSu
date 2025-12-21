package com.srisu.srisu.core.data.websocket.chat

sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    object Reconnecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}