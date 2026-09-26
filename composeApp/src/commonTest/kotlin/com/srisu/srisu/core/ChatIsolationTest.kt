@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.core

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.chat.data.remote.api.*
import com.srisu.srisu.features.chat.data.remote.websocket.*
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ChatIsolationTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun after() { Dispatchers.resetMain() }
    private val roomA = "00000000-0000-4000-8000-000000000001"
    private val roomB = "00000000-0000-4000-8000-000000000002"
    private fun history(room: String, id: Int) = """{"data":{"chat_room_id":"$room","messages":[{"id":$id,"chat_room_id":"$room","text":"Synthetic"}],"has_more":false,"next_cursor":null}}"""

    @Test fun switchingRoomCancelsOldReadAndLogoutClearsProjection() = runTest(dispatcher) {
        val sessions = session(); val lifetime = ApplicationLifetime()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply { dispatcher = this@ChatIsolationTest.dispatcher; addHandler { request ->
            if (request.url.encodedPath.contains(roomA)) { entered.complete(Unit); release.await(); respond(history(roomA, 1)) }
            else respond(history(roomB, 2))
        } }) )
        val socket = ChatWebSocketClient(SocketConnector { throw SocketUnavailable() }, sessions, lifetime)
        val repo = ChatRepository(socket, ChatApiService(client), sessions, lifetime)
        runCurrent()
        repo.fetchInitialMessages(roomA); runCurrent(); entered.await()
        repo.fetchInitialMessages(roomB); runCurrent()
        release.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(roomB), repo.messages.value.map { it.chatRoomId })
        sessions.clearSession(); runCurrent()
        assertTrue(repo.messages.value.isEmpty()); assertTrue(repo.chatRoomsList.value.isEmpty())
        repo.close(); socket.close(); lifetime.close(); client.close()
    }

    @Test fun mismatchedServerRoomNeverEntersVisibleState() = runTest(dispatcher) {
        val sessions = session(); val lifetime = ApplicationLifetime()
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply { dispatcher = this@ChatIsolationTest.dispatcher; addHandler { respond(history(roomA, 1)) } }))
        val socket = ChatWebSocketClient(SocketConnector { throw SocketUnavailable() }, sessions, lifetime)
        val repo = ChatRepository(socket, ChatApiService(client), sessions, lifetime)
        runCurrent(); repo.fetchInitialMessages(roomB); advanceUntilIdle()
        assertTrue(repo.messages.value.isEmpty()); assertNotNull(repo.error.value)
        repo.close(); socket.close(); lifetime.close(); client.close()
    }
}
