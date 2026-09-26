package com.srisu.srisu.core

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.chat.data.remote.response.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.url
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** These strings are generated from the pinned backend-owned fixtures, never hand-maintained. */
class CoreContractTest {
    @Test fun backendFixturesParseAsClientContracts() = runTest {
        val bodies = ArrayDeque(listOf(CoreContractFixtures.interests, CoreContractFixtures.empty_interests, CoreContractFixtures.history, CoreContractFixtures.empty_history, CoreContractFixtures.rooms))
        val client = HttpClientFactory.create(session(), environment, MockEngine { respond(bodies.removeFirst()) })
        val interests = client.safeRequest<InterestResponse> { url("https://example.test/") }.result as NetworkAPIResult.Success
        assertEquals("Hiking", interests.response?.interests?.single()?.name)
        assertEquals(emptyList(), (client.safeRequest<InterestResponse> { url("https://example.test/") }.result as NetworkAPIResult.Success).response?.interests)
        val history = (client.safeRequest<ChatHistoryPage> { url("https://example.test/") }.result as NetworkAPIResult.Success).response!!
        assertEquals(history.chatRoomId, history.messages.single().chatRoomId)
        assertFalse(history.hasMore)
        assertTrue((client.safeRequest<ChatHistoryPage> { url("https://example.test/") }.result as NetworkAPIResult.Success).response!!.messages.isEmpty())
        assertNull((client.safeRequest<ChatRoomPage> { url("https://example.test/") }.result as NetworkAPIResult.Success).response!!.nextCursor)
        val error = decodeApiError(400, CoreContractFixtures.validation_error)
        assertEquals("validation_failed", error.code); assertEquals(listOf("max_value"), error.fields["limit"])
        val socket = ApiJson.decodeFromString<SocketEnvelope<MessageMutationData>>(CoreContractFixtures.socket_message)
        assertEquals(history.messages.single(), socket.data?.message)
        client.close()
    }
}
