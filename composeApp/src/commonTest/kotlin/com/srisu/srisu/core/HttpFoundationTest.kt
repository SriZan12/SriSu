package com.srisu.srisu.core

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class MemorySession : SessionStorage {
    var value: String? = null
    override fun getSession(sessionKey: String) = value
    override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
    override fun clearSession(): Boolean { value = null; return true }
    override fun clearOnReinstall(key: String) {}
}
fun session(account: Long = 1) = SessionCoordinator(MemorySession()).apply {
    saveSession(ApiJson.encodeToString(Session(id = account, access = "synthetic-$account")), SESSION_KEY)
}
val environment = ApiEnvironment("https://example.test/")

class HttpFoundationTest {
    @Test fun bodiesAndStatusMapping() = runTest {
        val responses = listOf(
            200 to """{"data":{"items":[]},"message":"ok","future":true}""",
            204 to "", 200 to "", 200 to "<html>broken</html>",
            400 to """{"error":{"code":"validation_failed","fields":{"name":["required"]},"retryable":false},"request_id":"abc"}""",
            401 to "", 403 to "", 404 to "", 409 to "", 429 to "", 503 to "<private server trace>",
        )
        var index = 0
        val client = HttpClientFactory.create(session(), environment, MockEngine { request ->
            assertEquals("Bearer synthetic-1", request.headers[HttpHeaders.Authorization])
            assertEquals("core-1", request.headers["X-SriSu-Contract"])
            assertNotNull(request.headers["X-Request-ID"])
            val (status, body) = responses[index++]
            respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val results = responses.map { client.safeRequest<JsonObject> { url("https://example.test/path") }.result }
        assertIs<NetworkAPIResult.Success<*>>(results[0])
        assertNull((results[1] as NetworkAPIResult.Success).response)
        assertFalse((results[1] as NetworkAPIResult.Success).metadata!!.hasBody)
        assertNull((results[2] as NetworkAPIResult.Success).response)
        assertEquals(NetworkAPIResult.ErrorType.SERIALIZATION, (results[3] as NetworkAPIResult.Error).errorType)
        assertEquals(listOf("required"), (results[4] as NetworkAPIResult.Error).failure.fields["name"])
        assertEquals(listOf(NetworkAPIResult.ErrorType.UNAUTHORIZED, NetworkAPIResult.ErrorType.FORBIDDEN, NetworkAPIResult.ErrorType.NOT_FOUND, NetworkAPIResult.ErrorType.CONFLICT, NetworkAPIResult.ErrorType.RATE_LIMITED, NetworkAPIResult.ErrorType.SERVER), results.drop(5).map { (it as NetworkAPIResult.Error).errorType })
        assertFalse((results.last() as NetworkAPIResult.Error).error.contains("private"))
        client.close()
    }

    @Test fun rawPaginationJsonWritesAndMultipartRemainDistinct() = runTest {
        val client = HttpClientFactory.create(session(), environment, MockEngine { request ->
            if (request.url.encodedPath == "/json") assertTrue(request.body.contentType.toString().startsWith("application/json"))
            if (request.url.encodedPath == "/media") assertTrue(request.body.contentType.toString().startsWith("multipart/form-data; boundary="))
            respond("""{"count":0,"next":null,"results":[]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val raw = client.safeRequest<JsonObject>(shape = ResponseShape.RAW) { url("https://example.test/json"); method = HttpMethod.Post; setBody(buildJsonObject { put("a", "b") }) }
        assertEquals(0, (raw.result as NetworkAPIResult.Success).response!!["count"]!!.jsonPrimitive.int)
        assertIs<NetworkAPIResult.Success<*>>(client.safeRequest<JsonObject>(shape = ResponseShape.RAW) { url("https://example.test/media"); method = HttpMethod.Post; setBody(MultiPartFormDataContent(formData { append("caption", "synthetic") })) }.result)
        client.close()
    }

    @Test fun retryBoundAndUnsafeWritesNeverReplay() = runTest {
        var calls = 0
        val client = HttpClientFactory.create(session(), environment, MockEngine { calls++; respond("", HttpStatusCode.ServiceUnavailable) })
        client.safeRequest<JsonObject>(readRetries = 2) { url("https://example.test/") }
        assertEquals(3, calls)
        client.safeRequest<JsonObject>(readRetries = 2) { url("https://example.test/"); method = HttpMethod.Post; setBody(buildJsonObject {}) }
        assertEquals(4, calls)
        client.close()
    }

    @Test fun logoutCancelsLateResponseWithoutRestoringCredentials() = runTest {
        val sessions = session()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClientFactory.create(sessions, environment, MockEngine { entered.complete(Unit); release.await(); respond("""{"data":{}}""") })
        val request = async { client.safeRequest<JsonObject> { url("https://example.test/") } }
        entered.await(); sessions.clearSession(); release.complete(Unit)
        assertFailsWith<SessionChangedException> { request.await() }
        assertNull(sessions.accessToken())
        client.close()
    }

    @Test fun accountChangeBetweenRetriesCannotReplayAsNewAccount() = runTest {
        val sessions = session(); var calls = 0
        val client = HttpClientFactory.create(sessions, environment, MockEngine {
            calls++; sessions.saveSession(ApiJson.encodeToString(Session(id = 2, access = "synthetic-2")), SESSION_KEY)
            throw kotlinx.io.IOException("offline")
        })
        assertFailsWith<SessionChangedException> { client.safeRequest<JsonObject>(readRetries = 2) { url("https://example.test/") } }
        assertEquals(1, calls)
        client.close()
    }

    @Test fun cancellationDoesNotBecomeAnErrorAndOfflineDoesNotLogout() = runTest {
        assertFailsWith<CancellationException> { handleException<Unit>(CancellationException()) }
        val sessions = session()
        val client = HttpClientFactory.create(sessions, environment, MockEngine { throw kotlinx.io.IOException("offline") })
        assertEquals(NetworkAPIResult.ErrorType.NETWORK, (client.safeRequest<JsonObject> { url("https://example.test/") }.result as NetworkAPIResult.Error).errorType)
        assertEquals(1L, sessions.stamp().accountId)
        val stamp = sessions.stamp(); sessions.clearSession()
        assertFalse(sessions.saveIfCurrent(ApiJson.encodeToString(Session(id = 1, access = "late")), stamp))
        client.close()
    }

    @Test fun externalOriginsAndRedirectsNeverReceiveCredentials() = runTest {
        var calls = 0
        val client = HttpClientFactory.create(session(), environment, MockEngine { request ->
            calls++
            if (request.url.host == "outside.test") assertNull(request.headers[HttpHeaders.Authorization])
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://outside.test/"))
        })
        client.safeRequest<Unit> { url("https://example.test/") }
        assertEquals(1, calls)
        client.safeRequest<Unit> { url("https://outside.test/"); headers.append(HttpHeaders.Authorization, "must-be-removed") }
        assertEquals(2, calls)
        client.close()
    }
}
