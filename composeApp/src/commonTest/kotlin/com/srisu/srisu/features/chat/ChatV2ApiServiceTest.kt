package com.srisu.srisu.features.chat

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiEnvironmentKey
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2ApiService
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2Routes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatV2ApiServiceTest {
    @Test fun readRoutesPinContractHeaderAndClampBackendLimits() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            val data = if (request.url.encodedPath.endsWith("/messages/"))
                """{"room_id":"$ROOM","high_watermark":0,"messages":[],"has_more":false,"next_before_sequence":null}"""
            else """{"room_id":"$ROOM","high_watermark":0,"changes":[],"has_more":false,"next_after_sequence":0}"""
            respond("""{"data":$data}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        client.attributes.put(ApiEnvironmentKey, ApiEnvironment("https://example.test/"))
        val api = ChatV2ApiService(client)
        api.messages(ROOM, beforeSequence = 9, limit = 500)
        api.changes(ROOM, afterSequence = 7, limit = 500)

        assertEquals("/api/chat/v2/rooms/$ROOM/messages/", requests[0].url.encodedPath)
        assertEquals("50", requests[0].url.parameters["limit"])
        assertEquals("9", requests[0].url.parameters["before_sequence"])
        assertEquals("/api/chat/v2/rooms/$ROOM/changes/", requests[1].url.encodedPath)
        assertEquals("100", requests[1].url.parameters["limit"])
        assertEquals("7", requests[1].url.parameters["after_sequence"])
        requests.forEach { assertEquals(ChatV2Routes.CONTRACT_VERSION, it.headers[ChatV2Routes.CONTRACT_HEADER]) }
        client.close()
    }

    companion object {
        private const val ROOM = "123e4567-e89b-12d3-a456-426614174000"
    }
}
