package com.srisu.srisu.core

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.chat.matrix.MatrixSessionDto
import io.ktor.client.engine.mock.*
import io.ktor.client.request.url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

/** These strings are generated from the pinned backend-owned fixtures, never hand-maintained. */
class CoreContractTest {
    @Test fun backendFixturesParseAsClientContracts() = runTest {
        val bodies = ArrayDeque(listOf(CoreContractFixtures.INTEREST, CoreContractFixtures.EMPTY_INTEREST))
        val client = HttpClientFactory.create(session(), environment, MockEngine { respond(bodies.removeFirst()) })
        val interests = client.safeRequest<InterestResponse> { url("https://example.test/") }.result as NetworkAPIResult.Success
        assertEquals("Hiking", interests.response?.interests?.single()?.name)
        assertEquals(emptyList(), (client.safeRequest<InterestResponse> { url("https://example.test/") }.result as NetworkAPIResult.Success).response?.interests)
        val error = decodeApiError(400, CoreContractFixtures.VALIDATION_ERROR)
        assertEquals("validation_failed", error.code); assertEquals(listOf("max_value"), error.fields["limit"])
        val matrix = ApiJson.decodeFromJsonElement<MatrixSessionDto>(
            ApiJson.parseToJsonElement(CoreContractFixtures.MATRIX_SESSION).jsonObject.getValue("data")
        )
        assertEquals("matrix-e2ee-v1", matrix.protocolId)
        assertEquals("org.matrix.login.jwt", matrix.loginType)
        client.close()
    }
}
