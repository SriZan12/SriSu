package com.srisu.srisu.features.chat

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.presentation.findpartner.state.PartnerSearchStatus
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.utils.Constants
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FindPartnerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var client: HttpClient
    private lateinit var vm: FindPartnerViewModel
    private val calls = mutableListOf<Pair<String, String>>()
    private var searchStatus = HttpStatusCode.OK
    private var searchData = """{"id":2,"full_name":"Aayush","phone_number":"+9779812345678"}"""
    private var sendStatus = HttpStatusCode.OK
    private var updateStatus = HttpStatusCode.OK
    private var statusStatus = HttpStatusCode.OK
    private var statusData = """{"connection_requested":false}"""
    private var lastUpdateBody = ""
    private var latency = 0L

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = this@FindPartnerViewModelTest.dispatcher
            addHandler { request ->
            val path = request.url.encodedPath
            calls += path to request.url.parameters["phone_number"].orEmpty()
            delay(latency)
            val (status, data) = when {
                path.endsWith("find-partner/") -> searchStatus to searchData
                path.endsWith("have-couple-connection-requested/") -> statusStatus to statusData
                path.endsWith("connect-couple/") -> sendStatus to """{"id":7,"breakup_reason":null,"connection_status":null,"created_at":null,"receiver_number":null,"sender_number":null,"updated_at":null}"""
                else -> {
                    lastUpdateBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    updateStatus to "null"
                }
            }
            respond(if (status == HttpStatusCode.OK) """{"data":$data,"message":"Done"}"""
                else """{"message":"Please try again"}""", status,
                headersOf("Content-Type", "application/json"))
            }
        })) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; coerceInputValues = true }) }
            // Mirrors production's JSON request content type.
            install(io.ktor.client.plugins.DefaultRequest) {
                headers.append("Content-Type", "application/json")
            }
        }
        vm = FindPartnerViewModel(ConnectionRepository(ConnectionApiService(client)), object : SessionStorage {
            override fun getSession(sessionKey: String) = Json.encodeToString(Session(phoneNumber = "+9779800000000"))
            override fun saveSession(credentials: String, sessionKey: String) = Unit
            override fun clearSession() = true
            override fun clearOnReinstall(key: String) = Unit
        }, countryProvider = { emptyList() })
    }

    @AfterTest
    fun teardown() {
        vm.viewModelScope.cancel()
        client.close()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.findPartner() {
        vm.onScreenEntered()
        advanceUntilIdle()
        vm.updatePhoneNumber("9812345678")
        vm.sendFindYourPartnerRequest()
        advanceUntilIdle()
        assertEquals(PartnerSearchStatus.Found, vm.findPartnerUIState.value.searchStatus)
    }

    @Test fun defersNetworkUntilEntryAndChecksStatusOnlyOnce() = runTest(dispatcher) {
        advanceUntilIdle()
        assertTrue(calls.isEmpty())
        vm.onScreenEntered()
        vm.onScreenEntered()
        advanceUntilIdle()
        assertEquals(1, calls.size)
    }

    @Test fun validatesNumberAndRejectsOwnNumberWithoutSearching() = runTest(dispatcher) {
        vm.onScreenEntered()
        advanceUntilIdle()
        vm.sendFindYourPartnerRequest()
        assertTrue(vm.findPartnerUIState.value.validationErrorMsg.isNotEmpty())
        vm.updatePhoneNumber("9800000000")
        vm.sendFindYourPartnerRequest()
        advanceUntilIdle()
        assertTrue(vm.findPartnerUIState.value.validationErrorMsg.contains("own"))
        assertEquals(1, calls.size)
    }

    @Test fun normalizesInputAndUsesPhoneQueryWithSelectedCountry() = runTest(dispatcher) {
        vm.updateCountry("IN", "+91")
        vm.updatePhoneNumber("981 234-5678")
        vm.sendFindYourPartnerRequest()
        advanceUntilIdle()
        assertEquals("+919812345678", calls.single().second)
        assertEquals(PartnerSearchStatus.Found, vm.findPartnerUIState.value.searchStatus)
    }

    @Test fun suppressesDuplicateSearchAndDropsCancelledResults() = runTest(dispatcher) {
        latency = 1000
        vm.updatePhoneNumber("9812345678")
        vm.sendFindYourPartnerRequest()
        vm.sendFindYourPartnerRequest()
        runCurrent()
        assertEquals(1, calls.size)
        vm.cancelSearch()
        advanceUntilIdle()
        assertEquals(PartnerSearchStatus.Idle, vm.findPartnerUIState.value.searchStatus)
        assertNull(vm.findPartnerUIState.value.partnerResponse)
    }

    @Test fun editingNumberInvalidatesPriorResult() = runTest(dispatcher) {
        findPartner()
        vm.updatePhoneNumber("9812345679")
        assertNull(vm.findPartnerUIState.value.partnerResponse)
        assertEquals(PartnerSearchStatus.Idle, vm.findPartnerUIState.value.searchStatus)
    }

    @Test fun notFoundAndNetworkErrorsHaveDifferentStatesAndCanRetry() = runTest(dispatcher) {
        vm.updatePhoneNumber("9812345678")
        searchStatus = HttpStatusCode.NotFound
        vm.sendFindYourPartnerRequest()
        advanceUntilIdle()
        assertEquals(PartnerSearchStatus.NotFound, vm.findPartnerUIState.value.searchStatus)
        searchStatus = HttpStatusCode.ServiceUnavailable
        vm.sendFindYourPartnerRequest()
        advanceUntilIdle()
        assertEquals(PartnerSearchStatus.Error, vm.findPartnerUIState.value.searchStatus)
        searchStatus = HttpStatusCode.OK
        vm.sendFindYourPartnerRequest()
        advanceUntilIdle()
        assertEquals(PartnerSearchStatus.Found, vm.findPartnerUIState.value.searchStatus)
    }

    @Test fun emptySearchResponseIsNotFound() = runTest(dispatcher) {
        searchData = "null"
        vm.updatePhoneNumber("9812345678")
        vm.sendFindYourPartnerRequest()
        advanceUntilIdle()
        assertEquals(PartnerSearchStatus.NotFound, vm.findPartnerUIState.value.searchStatus)
    }

    @Test fun sendsOnceAndConsumesSuccessNavigation() = runTest(dispatcher) {
        findPartner()
        latency = 1000
        vm.sendCoupleConnectionRequest()
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        assertEquals(1, calls.count { it.first.endsWith("connect-couple/") })
        assertTrue(vm.findPartnerUIState.value.navigateToInviteSent)
        assertEquals("Aayush", vm.findPartnerUIState.value.sentInvitation?.name)
        vm.onInviteSentNavigated()
        assertFalse(vm.findPartnerUIState.value.navigateToInviteSent)
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        assertEquals(1, calls.count { it.first.endsWith("connect-couple/") })
    }

    @Test fun failedSendPreservesPartnerForRetry() = runTest(dispatcher) {
        findPartner()
        sendStatus = HttpStatusCode.ServiceUnavailable
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        assertNotNull(vm.findPartnerUIState.value.invitationError)
        assertFalse(vm.findPartnerUIState.value.navigateToInviteSent)
        assertNotNull(vm.findPartnerUIState.value.partnerResponse)
        sendStatus = HttpStatusCode.OK
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        assertNotNull(vm.findPartnerUIState.value.sentInvitation)
    }

    @Test fun cancelInvitationRetriesAndClearsSuccessState() = runTest(dispatcher) {
        findPartner()
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        updateStatus = HttpStatusCode.ServiceUnavailable
        vm.cancelSentInvitation()
        advanceUntilIdle()
        assertNotNull(vm.findPartnerUIState.value.sentInvitation)
        updateStatus = HttpStatusCode.OK
        vm.cancelSentInvitation()
        vm.cancelSentInvitation()
        advanceUntilIdle()
        assertNull(vm.findPartnerUIState.value.sentInvitation)
        assertEquals(PartnerSearchStatus.Idle, vm.findPartnerUIState.value.searchStatus)
        assertEquals("NOTHING", Json.parseToJsonElement(lastUpdateBody).jsonObject["connection_status"]?.jsonPrimitive?.content)
    }

    @Test fun failedInvitationActionKeepsRowAndSuccessfulRetryRemovesIt() = runTest(dispatcher) {
        updateStatus = HttpStatusCode.ServiceUnavailable
        vm.updateLoveRequest(42, "+9779812345678", "+9779800000000", Constants.ConnectionStatus.REJECTED)
        vm.updateLoveRequest(42, "+9779812345678", "+9779800000000", Constants.ConnectionStatus.REJECTED)
        advanceUntilIdle()
        assertEquals(1, calls.size)
        assertTrue(vm.findPartnerUIState.value.handledRequestIds.isEmpty())
        assertNotNull(vm.findPartnerUIState.value.requestErrors[42])
        updateStatus = HttpStatusCode.OK
        vm.updateLoveRequest(42, "+9779812345678", "+9779800000000", Constants.ConnectionStatus.REJECTED)
        advanceUntilIdle()
        assertTrue(42L in vm.findPartnerUIState.value.handledRequestIds)
        assertEquals("REJECTED", Json.parseToJsonElement(lastUpdateBody).jsonObject["connection_status"]?.jsonPrimitive?.content)
    }
    @Test fun pendingInvitationRecoveryPreventsAnotherSendAndDetectsAcceptance() = runTest(dispatcher) {
        statusData = """{"connection_requested":true,"connection":{"id":7,"receiver_number":"+9779812345678","partner":{"full_name":"Aayush"}}}"""
        findPartner()
        assertEquals(7L, vm.findPartnerUIState.value.sentInvitation?.requestId)
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        assertEquals(0, calls.count { it.first.endsWith("connect-couple/") })
        statusData = """{"connection_requested":false,"connection":{"id":7,"connection_status":"ACCEPTED","partner":{"full_name":"Aayush"}}}"""
        vm.sendHaveCoupleConnectionRequested()
        advanceUntilIdle()
        assertEquals("Aayush", vm.findPartnerUIState.value.acceptedPartnerName)
        assertNull(vm.findPartnerUIState.value.sentInvitation)
    }

    @Test fun statusFailureBlocksSendingUntilSuccessfulRetry() = runTest(dispatcher) {
        statusStatus = HttpStatusCode.ServiceUnavailable
        findPartner()
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        assertEquals(0, calls.count { it.first.endsWith("connect-couple/") })
        assertNotNull(vm.findPartnerUIState.value.invitationStatusError)
        statusStatus = HttpStatusCode.OK
        vm.sendHaveCoupleConnectionRequested()
        advanceUntilIdle()
        vm.sendCoupleConnectionRequest()
        advanceUntilIdle()
        assertNotNull(vm.findPartnerUIState.value.sentInvitation)
    }

    @Test fun acceptsOnlyOnePartnerAndSendsCorrectRequestFields() = runTest(dispatcher) {
        vm.updateLoveRequest(42, "+9779812345678", "+9779800000000", Constants.ConnectionStatus.ACCEPTED)
        vm.updateLoveRequest(43, "+9779812345679", "+9779800000000", Constants.ConnectionStatus.ACCEPTED)
        advanceUntilIdle()
        assertEquals(1, calls.size)
        assertNotNull(vm.findPartnerUIState.value.acceptedPartnerName)
        val body = Json.parseToJsonElement(lastUpdateBody).jsonObject
        assertEquals("ACCEPTED", body["connection_status"]?.jsonPrimitive?.content)
        assertEquals("+9779812345678", body["sender_number"]?.jsonPrimitive?.content)
        assertEquals("+9779800000000", body["receiver_number"]?.jsonPrimitive?.content)
        vm.updateLoveRequest(43, "+9779812345679", "+9779800000000", Constants.ConnectionStatus.ACCEPTED)
        advanceUntilIdle()
        assertEquals(1, calls.size)
    }

    @Test fun acceptedInvitationRetainsPartnerIdentityForConnectedScreen() = runTest(dispatcher) {
        val partner = com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse.Result.Receiver(
            fullName = "Aatithi", profilePhoto = "https://example.test/avatar.jpg")
        vm.updateLoveRequest(42, "+9779812345678", "+9779800000000", Constants.ConnectionStatus.ACCEPTED, partner)
        advanceUntilIdle()
        assertEquals("Aatithi", vm.findPartnerUIState.value.acceptedPartnerName)
        assertEquals(partner.profilePhoto, vm.findPartnerUIState.value.acceptedPartnerPhotoUrl)
        assertNull(vm.findPartnerUIState.value.connectedSince)
    }

    @Test fun connectionDateUsesServerValueAndRejectsInvalidDates() {
        assertEquals("10 Jan 2026", com.srisu.srisu.features.chat.presentation.findpartner.vm.formatConnectionDate("2026-01-10T12:34:56Z"))
        assertNull(com.srisu.srisu.features.chat.presentation.findpartner.vm.formatConnectionDate(null))
        assertNull(com.srisu.srisu.features.chat.presentation.findpartner.vm.formatConnectionDate("invalid"))
    }

}
