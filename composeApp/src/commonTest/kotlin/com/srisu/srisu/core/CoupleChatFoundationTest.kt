@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.core

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.couplechat.data.*
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class CoupleChatFoundationTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun after() { Dispatchers.resetMain() }
    private val snapshot = """{"data":{"contract":"couple-chat-1","room":{"id":"00000000-0000-4000-8000-000000000001","relationship_id":7,"participant_ids":[1,2],"sequence":1,"security_state":"setup_required","can_send":false}}}"""

    @Test fun roomContractUsesNewEndpointAndCannotEnableUnimplementedEncryption() = runTest(dispatcher) {
        val sessions = session(); val lifetime = ApplicationLifetime()
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply {
            dispatcher = this@CoupleChatFoundationTest.dispatcher
            addHandler { request ->
                assertEquals("/api/couple-chat/v1/state/", request.url.encodedPath)
                respond(snapshot.replace("\"can_send\":false", "\"can_send\":true"))
            }
        }))
        val repository = CoupleChatRepository(CoupleChatApi(client, environment), SocketConnector { throw SocketUnavailable() }, sessions, lifetime)
        lifetime.setForeground(true); repository.enter(); runCurrent()
        val state = assertIs<CoupleChatState.SetupRequired>(repository.state.value)
        assertEquals(7, state.room.relationshipId)
        sessions.clearSession(); runCurrent()
        assertEquals(CoupleChatState.Loading, repository.state.value)
        repository.close(); lifetime.close(); client.close()
    }

    @Test fun accountSwitchCancelsStaleRoomAndBackgroundStopsAccess() = runTest(dispatcher) {
        val sessions = session(); val lifetime = ApplicationLifetime()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply {
            dispatcher = this@CoupleChatFoundationTest.dispatcher
            addHandler { started.complete(Unit); release.await(); respond(snapshot) }
        }))
        val repository = CoupleChatRepository(CoupleChatApi(client, environment), SocketConnector { throw SocketUnavailable() }, sessions, lifetime)
        lifetime.setForeground(true); repository.enter(); runCurrent(); started.await()
        sessions.clearSession(); release.complete(Unit); runCurrent()
        assertEquals(CoupleChatState.Loading, repository.state.value)
        lifetime.setForeground(false); runCurrent()
        repository.close(); lifetime.close(); client.close()
    }

    @Test fun contradictoryParticipantListIsNotDisplayed() = runTest(dispatcher) {
        val sessions = session(); val lifetime = ApplicationLifetime()
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply {
            dispatcher = this@CoupleChatFoundationTest.dispatcher
            addHandler { respond(snapshot.replace("[1,2]", "[3,4]")) }
        }))
        val repository = CoupleChatRepository(CoupleChatApi(client, environment), SocketConnector { throw SocketUnavailable() }, sessions, lifetime)
        lifetime.setForeground(true); repository.enter(); runCurrent()
        assertIs<CoupleChatState.Unavailable>(repository.state.value)
        repository.close(); lifetime.close(); client.close()
    }
}
