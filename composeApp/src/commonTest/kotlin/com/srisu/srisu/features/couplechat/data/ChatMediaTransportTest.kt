package com.srisu.srisu.features.couplechat.data

import com.srisu.srisu.core.environment
import com.srisu.srisu.core.session
import com.srisu.srisu.core.data.remote.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ChatMediaTransportTest {
    @Test fun uploadUsesOpaqueBytesAndOwnedAuthorization() = runTest {
        val bytes=ByteArray(256) {it.toByte()}
        val client=HttpClientFactory.create(session(),environment,MockEngine {request ->
            assertEquals(HttpMethod.Put,request.method)
            assertEquals("Bearer synthetic-1",request.headers[HttpHeaders.Authorization])
            assertEquals("device",request.headers["X-Chat-Device"])
            assertEquals(ContentType.Application.OctetStream,request.body.contentType)
            // The upload progress plugin may wrap outgoing content in a channel.
            assertContentEquals(bytes,request.body.toByteArray())
            respond("""{"data":{"id":"attachment","state":"uploaded"}}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {assertIs<NetworkAPIResult.Success<*>>(CoupleChatApi(client,environment).upload("room","attachment","device",bytes) {}.result)}
        finally {client.close()}
    }
    @Test fun downloadsRequireAnExactBoundAndKeepCapabilitiesOutOfUrls() = runTest {
        var length=64
        val client=HttpClientFactory.create(session(),environment,MockEngine {request ->
            assertTrue(request.url.parameters.isEmpty())
            assertEquals("synthetic-capability",request.headers["X-Chat-Media-Capability"])
            assertEquals("Bearer synthetic-1",request.headers[HttpHeaders.Authorization])
            respond(ByteArray(length),headers=headersOf(HttpHeaders.ContentType,"application/octet-stream"))
        })
        try {
            assertIs<NetworkAPIResult.Success<*>>(client.readBoundedChatCiphertext("https://example.test/media","synthetic-capability",64).result)
            for(size in listOf(63,65)) {
                length=size
                val error=assertIs<NetworkAPIResult.Error<*>>(client.readBoundedChatCiphertext("https://example.test/media","synthetic-capability",64).result)
                assertEquals("attachment_integrity",error.failure.code)
            }
        } finally {client.close()}
    }
    @Test fun anOldAccountDownloadCannotPublishAfterSignOut() = runTest {
        val sessions=session();val began=CompletableDeferred<Unit>();val resume=CompletableDeferred<Unit>()
        val client=HttpClientFactory.create(sessions,environment,MockEngine {
            began.complete(Unit);resume.await();respond(ByteArray(64),headers=headersOf(HttpHeaders.ContentType,"application/octet-stream"))
        })
        try {
            val download=async {client.readBoundedChatCiphertext("https://example.test/media","synthetic-capability",64)}
            began.await();sessions.clearSession();resume.complete(Unit)
            try {assertIs<NetworkAPIResult.Error<*>>(download.await().result)} catch(_: CancellationException) { /* session boundary cancels stale requests */ }
        } finally {client.close()}
    }
}
