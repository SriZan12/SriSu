package com.srisu.srisu.core.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkConfigTest {

    @Test
    fun normalizesBaseUrlAndResolvesPaths() {
        val config = NetworkConfig(
            apiBaseUrl = "https://api.example.com",
            webSocketHost = "socket.example.com",
            webSocketPort = 443,
            useSecureWebSocket = true,
        )

        assertEquals("https://api.example.com/", config.apiBaseUrl)
        assertEquals("https://api.example.com/users", config.apiUrl("/users"))
    }

    @Test
    fun buildsSecureWebSocketUrlWithTheLatestToken() {
        val config = NetworkConfig(
            apiBaseUrl = "https://api.example.com/",
            webSocketHost = "socket.example.com",
            webSocketPort = 443,
            useSecureWebSocket = true,
        )

        assertEquals(
            "wss://socket.example.com:443/ws/chat/?token=new-token",
            config.webSocketUrl("new-token"),
        )
    }

    @Test
    fun rejectsInvalidPorts() {
        assertFailsWith<IllegalArgumentException> {
            NetworkConfig(
                apiBaseUrl = "https://api.example.com",
                webSocketHost = "socket.example.com",
                webSocketPort = 0,
                useSecureWebSocket = true,
            )
        }
    }
}
