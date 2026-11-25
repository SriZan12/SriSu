package com.srisu.srisu.features.settings

import com.srisu.srisu.core.environment
import com.srisu.srisu.core.session
import com.srisu.srisu.core.data.remote.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class PartnerNicknameRequestTest {
    @Test fun disclosedEditorExplicitlyOptsIntoPartnerAlertWithoutChangingRevisionGuards() = runTest {
        val client = HttpClientFactory.create(session(), environment, MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertTrue(request.url.encodedPath.endsWith("/settings/partner-nickname/"))
            val body = ApiJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(JsonPrimitive(true), body["notify_partner"])
            assertEquals(JsonPrimitive("Sunshine ☀️"), body["nickname"])
            assertEquals(JsonPrimitive(3), body["connection_id"])
            assertEquals(JsonPrimitive(2), body["expected_revision"])
            assertEquals(JsonPrimitive(7), body["expected_nickname_revision"])
            respond("""{"data":{"contract":"settings-1","partner":null}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            assertIs<NetworkAPIResult.Success<SettingsContext?>>(SettingsRepository(client, environment)
                .saveNickname(samplePartner.copy(nicknameRevision = 7), "Sunshine ☀️"))
        } finally { client.close() }
    }
}
