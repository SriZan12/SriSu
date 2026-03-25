package com.srisu.srisu.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

expect class NetworkMonitor {
    fun isConnected(): Boolean
}

expect class ConnectivityObserver() {
    val isConnected: StateFlow<Boolean>
}

@Composable
fun isInternetAvailable(
    connectivityObserver: ConnectivityObserver = koinInject(),
    chatWebSocket: ChatWebSocketClient = koinInject()
): Boolean {
    val isConnected by connectivityObserver.isConnected.collectAsState()
//    if (isConnected){
//        chatWebSocket.send()
//    }
    return isConnected
}
