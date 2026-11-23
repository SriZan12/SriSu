package com.srisu.srisu.notifications

import com.srisu.srisu.core.session
import com.srisu.srisu.core.environment
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.features.chat.ChatFixtureRepository
import com.srisu.srisu.features.chat.ChatFixtures
import com.srisu.srisu.features.chat.matrix.MatrixChatRepository
import com.srisu.srisu.features.notifications.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPreviewReaderTest {
    private val delivery = "11111111-1111-4111-8111-111111111111"
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private val chat = """{"data":{"event_kind":"matrix.message","title":"Synthetic partner","body":"Open your chat to read the message.","room_id":"${ChatFixtures.ROOM_ID}","matrix_event_id":"${'$'}synthetic-event"}}"""

    @Test fun decryptsExactEventAndRechecksAuthorizationWithoutSendingOrMarkingRead() = runTest {
        val sessions = session()
        var calls = 0
        val client = HttpClientFactory.create(sessions, environment, MockEngine {
            assertEquals("/api/notifications/v1/deliveries/$delivery/preview/", it.url.encodedPath)
            calls++
            respond(chat, headers = headers)
        })
        val base = ChatFixtureRepository(ChatFixtures.empty())
        val matrix = object : MatrixChatRepository by base {
            override suspend fun notificationPreview(roomId: String, eventId: String): String {
                assertEquals(ChatFixtures.ROOM_ID, roomId)
                assertEquals("\$synthetic-event", eventId)
                return "Synthetic picnic at six?"
            }
            override suspend fun markRead(roomId: String, messageId: String): Result<Unit> = error("Preview must not mark read")
        }
        try {
            val result = assertIs<PreviewResult.Ready>(NotificationPreviewReader(NotificationApi(client, environment), sessions, matrix).load(delivery))
            assertEquals("Synthetic picnic at six?", result.value.body)
            assertTrue(result.complete)
            assertEquals(2, calls)
            assertTrue(base.sent.isEmpty())
        } finally { client.close() }
    }

    @Test fun lostAuthorizationDuringDecryptionNeverReturnsPlaintext() = runTest {
        val sessions = session()
        var calls = 0
        val client = HttpClientFactory.create(sessions, environment, MockEngine {
            if (++calls == 1) respond(chat, headers = headers) else respond("{}", HttpStatusCode.NotFound, headers)
        })
        val matrix = object : MatrixChatRepository by ChatFixtureRepository(ChatFixtures.empty()) {
            override suspend fun notificationPreview(roomId: String, eventId: String) = "Synthetic secret"
        }
        try {
            assertEquals(PreviewResult.Unavailable, NotificationPreviewReader(NotificationApi(client, environment), sessions, matrix).load(delivery))
        } finally { client.close() }
    }

    @Test fun unavailableKeysKeepTypedFallbackAndAllowBoundedWorkerRetry() = runTest {
        val sessions = session()
        val client = HttpClientFactory.create(sessions, environment, MockEngine { respond(chat, headers = headers) })
        try {
            val result = assertIs<PreviewResult.Ready>(NotificationPreviewReader(NotificationApi(client, environment), sessions,
                ChatFixtureRepository(ChatFixtures.empty())).load(delivery))
            assertFalse(result.complete)
            assertEquals("Open your chat to read the message.", result.value.body)
        } finally { client.close() }
    }

    @Test fun accountChangeDuringDecryptionCancelsPrivateResult() = runTest {
        val sessions = session()
        val client = HttpClientFactory.create(sessions, environment, MockEngine { respond(chat, headers = headers) })
        val matrix = object : MatrixChatRepository by ChatFixtureRepository(ChatFixtures.empty()) {
            override suspend fun notificationPreview(roomId: String, eventId: String): String {
                sessions.clearSession()
                return "Synthetic previous account text"
            }
        }
        try {
            assertFailsWith<CancellationException> { NotificationPreviewReader(NotificationApi(client, environment), sessions, matrix).load(delivery) }
        } finally { client.close() }
    }

    @Test fun planPreviewNeverInitializesMatrixAndInvalidRoutingNeverRequestsNetwork() = runTest {
        val sessions = session()
        var calls = 0
        val client = HttpClientFactory.create(sessions, environment, MockEngine {
            calls++
            respond("""{"data":{"event_kind":"plan.created","title":"Synthetic partner invited you to a plan","body":"Picnic · Saturday 16:00 · Garden"}}""", headers = headers)
        })
        val matrix = object : MatrixChatRepository by ChatFixtureRepository(ChatFixtures.empty()) {
            override suspend fun notificationPreview(roomId: String, eventId: String): String = error("Plans do not use Matrix")
        }
        val reader = NotificationPreviewReader(NotificationApi(client, environment), sessions, matrix)
        try {
            assertEquals(PreviewResult.Unavailable, reader.load("../../private"))
            assertEquals(0, calls)
            assertTrue(assertIs<PreviewResult.Ready>(reader.load(delivery)).value.body.contains("Picnic"))
            sessions.clearSession()
            assertEquals(PreviewResult.Unavailable, reader.load(delivery))
            assertEquals(1, calls)
        } finally { client.close() }
    }
}
