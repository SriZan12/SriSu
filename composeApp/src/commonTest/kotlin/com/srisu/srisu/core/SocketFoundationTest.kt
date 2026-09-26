@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.core

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.chat.data.remote.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

class FakeSocket : SocketConnection {
    val incoming = Channel<String>(64)
    val sent = Channel<String>(64)
    var code: Int? = null
    var closed = false
    override suspend fun receive() = incoming.receiveCatching().getOrNull()
    override suspend fun send(text: String) { sent.send(text) }
    override suspend fun close() { closed = true; incoming.close() }
    override suspend fun closeCode() = code
}

class SocketFoundationTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun after() { Dispatchers.resetMain() }

    @Test fun foregroundAndLogoutOwnConnectionLifetime() = runTest(dispatcher) {
        val lifetime = ApplicationLifetime(); val sessions = session(); val socket = FakeSocket(); var opens = 0
        val client = ChatWebSocketClient(SocketConnector { opens++; socket }, sessions, lifetime)
        client.connect(); runCurrent(); assertEquals(0, opens)
        lifetime.setForeground(true); runCurrent(); assertEquals(SocketState.Connected, client.connectionState.value)
        sessions.clearSession(); runCurrent()
        assertTrue(socket.closed); assertEquals(SocketState.Disconnected, client.connectionState.value)
        advanceTimeBy(100_000); runCurrent(); assertEquals(1, opens)
        client.close(); lifetime.close()
    }

    @Test fun reconnectIsBoundedAndTerminalDenialDoesNotReconnect() = runTest(dispatcher) {
        val lifetime = ApplicationLifetime(); lifetime.setForeground(true); var opens = 0
        val client = ChatWebSocketClient(SocketConnector { opens++; throw SocketUnavailable() }, session(), lifetime)
        client.connect(); advanceUntilIdle()
        assertEquals(6, opens); assertEquals(SocketState.Terminal("reconnect_exhausted"), client.connectionState.value)
        client.close(); lifetime.close()
        val terminalLifetime = ApplicationLifetime(); terminalLifetime.setForeground(true)
        val socket = FakeSocket().apply { code = 4401; incoming.close() }; opens = 0
        val terminal = ChatWebSocketClient(SocketConnector { opens++; socket }, session(), terminalLifetime)
        terminal.connect(); advanceUntilIdle()
        assertEquals(1, opens); assertEquals(SocketState.Terminal("unauthenticated"), terminal.connectionState.value)
        terminal.close(); terminalLifetime.close()
    }

    @Test fun writeWaitsForAckAndLossDoesNotReplay() = runTest(dispatcher) {
        val lifetime = ApplicationLifetime(); lifetime.setForeground(true); val socket = FakeSocket()
        val client = ChatWebSocketClient(SocketConnector { socket }, session(), lifetime)
        client.connect(); runCurrent()
        val send = async { client.sendMessage("00000000-0000-0000-0000-000000000001", text = "synthetic") }
        runCurrent(); val command = ApiJson.parseToJsonElement(socket.sent.receive()).jsonObject
        assertFalse(send.isCompleted)
        val requestId = command["request_id"]!!.jsonPrimitive.content
        socket.incoming.send("""{"type":"success","action":"send_message","request_id":"$requestId","data":{"message":{"id":1,"chat_room_id":"00000000-0000-0000-0000-000000000001"}}}""")
        runCurrent(); send.await()
        supervisorScope {
            val unknown = async { client.sendMessage("00000000-0000-0000-0000-000000000001", text = "synthetic") }
            runCurrent(); socket.sent.receive(); advanceTimeBy(10_001); runCurrent()
            assertFailsWith<ChatAcknowledgmentUnknown> { unknown.await() }
            assertTrue(socket.sent.tryReceive().isFailure)
        }
        client.close(); lifetime.close()
    }

    @Test fun duplicateMalformedAndUnknownFramesStayAtProtocolBoundary() = runTest(dispatcher) {
        val lifetime = ApplicationLifetime(); lifetime.setForeground(true); val socket = FakeSocket()
        val client = ChatWebSocketClient(SocketConnector { socket }, session(), lifetime)
        val events = mutableListOf<ChatWebSocketEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { client.events.collect { events += it.event } }
        client.connect(); runCurrent()
        val frame = """{"type":"event","protocol_version":1,"event_id":"fixed-id","action":"message_created","data":{"message":{"id":1,"chat_room_id":"room-a"}}}"""
        socket.incoming.send(frame); socket.incoming.send(frame)
        socket.incoming.send("{"); socket.incoming.send("""{"type":"event","action":"future_event","data":{}}""")
        runCurrent()
        assertEquals(1, events.filterIsInstance<ChatWebSocketEvent.SendMessage>().size)
        assertEquals("room-a", events.filterIsInstance<ChatWebSocketEvent.SendMessage>().single().message?.chatRoomId)
        assertEquals(2, events.count { it == ChatWebSocketEvent.Resync })
        client.close(); lifetime.close()
    }
}
