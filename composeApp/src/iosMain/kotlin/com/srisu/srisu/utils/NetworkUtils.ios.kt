package com.srisu.srisu.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

actual class NetworkMonitor {
    actual fun isConnected(): Boolean {
        return false
    }
}

actual class ConnectivityObserver {
    private val _isConnected = MutableStateFlow(false)
    actual val isConnected: StateFlow<Boolean> = _isConnected

    init {
        val queue: dispatch_queue_t = dispatch_queue_create("network_monitor", null)
        val monitor = nw_path_monitor_create()

        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_set_update_handler(monitor) { path ->
            _isConnected.value = nw_path_get_status(path) == nw_path_status_satisfied
        }

        nw_path_monitor_start(monitor)
    }
}