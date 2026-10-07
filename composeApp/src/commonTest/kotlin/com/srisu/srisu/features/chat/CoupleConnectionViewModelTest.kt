package com.srisu.srisu.features.chat

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiEnvironmentKey
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm.CoupleConnectionViewModel
import com.srisu.srisu.utils.Constants
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CoupleConnectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var client: HttpClient
    private lateinit var viewModel: CoupleConnectionViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = this@CoupleConnectionViewModelTest.dispatcher
            addHandler {
                respond(
                    content = """{"data":{"id":42,"connection_status":"ACCEPTED","updated_at":"2026-10-07T01:02:03Z","chat_room_id":"123e4567-e89b-12d3-a456-426614174000","couple_id":9},"message":"Done"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        })) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; coerceInputValues = true }) }
            install(DefaultRequest) { headers.append("Content-Type", "application/json") }
        }
        client.attributes.put(ApiEnvironmentKey, ApiEnvironment("https://example.test/"))
        viewModel = CoupleConnectionViewModel(ConnectionRepository(ConnectionApiService(client)))
    }

    @AfterTest
    fun teardown() {
        viewModel.viewModelScope.cancel()
        client.close()
        Dispatchers.resetMain()
    }

    @Test
    fun confirmedAcceptanceExposesRoomOnceForSuccessNavigation() = runTest(dispatcher) {
        val partner = CoupleConnectionRequestResponse.Result.Receiver(
            fullName = "Aayush",
            profilePhoto = "https://example.test/avatar.jpg",
        )

        viewModel.updateLoveRequest(
            loveRequestId = 42,
            senderNumber = "+9779812345678",
            receiverNumber = "+9779800000000",
            connectionStatus = Constants.ConnectionStatus.ACCEPTED,
            partner = partner,
        )
        advanceUntilIdle()

        val accepted = viewModel.coupleConnectionUiState.value.acceptedConnection
        assertEquals(42L, accepted?.requestId)
        assertEquals("Aayush", accepted?.partnerName)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", accepted?.roomId)
        assertEquals(9L, accepted?.coupleId)

        viewModel.consumeAcceptedConnection(42)
        assertNull(viewModel.coupleConnectionUiState.value.acceptedConnection)
    }
}
