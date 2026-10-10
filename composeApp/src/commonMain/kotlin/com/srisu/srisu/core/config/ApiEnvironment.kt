package com.srisu.srisu.core.config

import io.ktor.http.URLProtocol
import io.ktor.http.Url

data class ApiEnvironment(val baseUrl: String, val development: Boolean = false) {
    private val origin = Url(baseUrl)
    init {
        require(origin.protocol == URLProtocol.HTTPS || (development && origin.protocol == URLProtocol.HTTP))
        require(origin.user == null && origin.password == null && origin.parameters.isEmpty() && origin.fragment.isEmpty())
        require(baseUrl.endsWith("/") && origin.encodedPath == "/") { "API base URL must end in /" }
    }
    val socketUrl: String = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws/chat/"
    fun owns(url: Url): Boolean = url.host == origin.host && url.port == origin.port &&
        (url.protocol == origin.protocol || url.protocol.name == if (origin.protocol == URLProtocol.HTTPS) "wss" else "ws")

    companion object {
        fun configured() = ApiEnvironment(BuildEnvironment.baseUrl, BuildEnvironment.development)
    }
}
