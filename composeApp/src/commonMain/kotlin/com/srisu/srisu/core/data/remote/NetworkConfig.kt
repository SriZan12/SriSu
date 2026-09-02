package com.srisu.srisu.core.data.remote

/**
 * Environment-specific network settings. Bind a different instance in DI for staging/production.
 */
class NetworkConfig(
    apiBaseUrl: String,
    val webSocketHost: String,
    val webSocketPort: Int,
    val useSecureWebSocket: Boolean,
    val enableNetworkLogging: Boolean = false,
) {
    val apiBaseUrl: String = apiBaseUrl.trimEnd('/') + "/"

    init {
        require(this.apiBaseUrl.startsWith("http://") || this.apiBaseUrl.startsWith("https://")) {
            "apiBaseUrl must use HTTP or HTTPS"
        }
        require(webSocketHost.isNotBlank()) { "webSocketHost must not be blank" }
        require(webSocketPort in 1..65535) { "webSocketPort must be valid" }
    }

    fun apiUrl(path: String): String = apiBaseUrl + path.trimStart('/')

    fun webSocketUrl(accessToken: String?): String {
        val scheme = if (useSecureWebSocket) "wss" else "ws"
        val tokenQuery = accessToken?.takeIf(String::isNotBlank)?.let { "?token=$it" }.orEmpty()
        return "$scheme://$webSocketHost:$webSocketPort/ws/chat/$tokenQuery"
    }

    companion object {
        fun localDevelopment(host: String = "192.168.1.72") = NetworkConfig(
            apiBaseUrl = "http://$host:8000/",
            webSocketHost = host,
            webSocketPort = 8000,
            useSecureWebSocket = false,
            enableNetworkLogging = false,
        )
    }
}
