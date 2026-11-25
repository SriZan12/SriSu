package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.*
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class MatrixNotificationCardTest {
    @Test fun opaqueMarkerSurvivesActualMatrixSendSerializerAndOldClientCanDecryptEnvelope() = runTest {
        val key="11111111-1111-4111-8111-111111111111"
        val original=MegolmEncryptedMessageEventContent(MegolmMessageValue("synthetic-ciphertext"),sessionId="synthetic-session")
        val json=createMatrixEventJson(matrixEventContentSerializerMappings)
        var wire: JsonObject?=null
        val api=MatrixClientServerApiClientImpl(Url("https://matrix.invalid"),
            eventContentSerializerMappings=matrixEventContentSerializerMappings,json=json,coroutineContext=coroutineContext,
            httpClientEngine=MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("/send/m.room.encrypted/srisu-plan-12"))
                wire=json.parseToJsonElement((request.body as TextContent).text).jsonObject
                respond("""{"event_id":"synthetic-event"}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
            })
        try {
            api.room.sendMessageEvent(RoomId("!synthetic:test"),original.notificationCard(json,key),txnId="srisu-plan-12").getOrThrow()
            assertEquals(key,wire!!["org.srisu.notification_card"]!!.jsonPrimitive.content)
            assertEquals("m.megolm.v1.aes-sha2",wire!!["algorithm"]!!.jsonPrimitive.content)
            val legacy=createMatrixEventJson().decodeFromJsonElement(EncryptedMessageEventContent.Serializer,wire!!)
            assertEquals(original,legacy)
            assertEquals(setOf("algorithm","ciphertext","session_id","org.srisu.notification_card"),wire!!.keys)
            assertFailsWith<IllegalArgumentException> { original.notificationCard(json,"https://example.invalid") }
        } finally { api.closeSuspending() }
    }
}
