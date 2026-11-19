package com.srisu.srisu.core.logger

import io.github.aakira.napier.Napier

object AppLogger {
    // Temporary no-op adapter: old callers pass arbitrary payloads and exception
    // text. New diagnostics must use the allowlisted structured methods below.
    fun log(@Suppress("UNUSED_PARAMETER") message: String) = Unit
    fun debug(@Suppress("UNUSED_PARAMETER") message: String, @Suppress("UNUSED_PARAMETER") tag: String) = Unit
    fun error(@Suppress("UNUSED_PARAMETER") error: String, @Suppress("UNUSED_PARAMETER") tag: String) = Unit

    fun http(status: Int, durationMillis: Long, requestId: String?) {
        val safeId = requestId?.takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) } ?: "absent"
        Napier.i("http_complete status=$status duration_ms=$durationMillis request_id=$safeId", tag = "SriSuTransport")
    }
    fun socket(event: SocketDiagnostic) { Napier.i(event.name, tag = "SriSuSocket") }
}

enum class SocketDiagnostic { CONNECTED, DISCONNECTED, MALFORMED_FRAME, UNKNOWN_EVENT, DUPLICATE_EVENT, RECONNECTING, TERMINAL_FAILURE }
