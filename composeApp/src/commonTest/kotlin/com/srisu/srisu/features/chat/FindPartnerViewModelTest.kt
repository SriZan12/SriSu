package com.srisu.srisu.features.chat

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiEnvironmentKey
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.presentation.findpartner.state.PartnerSearchStatus
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionResponse
import com.srisu.srisu.features.home.connection.data.remote.response.HaveCoupleConnectionResponse
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
    private lateinit var connectionRepository: ConnectionRepository
    private lateinit var vm: FindPartnerViewModel
    private lateinit var sessions: SessionCoordinator
    private lateinit var lifetime: ApplicationLifetime
    private val calls = mutableListOf<Pair<String, String>>()
    private var searchStatus = HttpStatusCode.OK
    private var searchData = """{"id":2,"full_name":"Aayush","phone_number":"+9779812345678"}"""
    private var sendStatus = HttpStatusCode.OK
    private var updateStatus = HttpStatusCode.OK
    private var updateData = "null"
    private var statusStatus = HttpStatusCode.OK
    private var statusData = """{"connection_requested":false}"""
    private var lastUpdateBody = ""
    private val idempotencyKeys = mutableListOf<String?>()
    private var latency = 0L

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = this@FindPartnerViewModelTest.dispatcher
            addHandler { request ->
            val path = request.url.encodedPath
            calls += path to request.url.parameters["phone_number"].orEmpty()
            if (request.method != io.ktor.http.HttpMethod.Get) idempotencyKeys += request.headers["Idempotency-Key"]
            val statusDataAtRequestStart = statusData
            delay(latency)
            val (status, data) = when {
                path.endsWith("find-partner/") -> searchStatus to searchData
                path.endsWith("have-couple-connection-requested/") -> statusStatus to statusDataAtRequestStart
                path.endsWith("connect-couple/") -> sendStatus to """{"id":7,"breakup_reason":null,"connection_status":null,"created_at":null,"receiver_number":null,"sender_number":null,"updated_at":null}"""
                else -> {
                    lastUpdateBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    updateStatus to updateData
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
        client.attributes.put(ApiEnvironmentKey, ApiEnvironment("https://example.test/"))
        var storedSession: String? = Json.encodeToString(Session(id = 1, access = "synthetic", phoneNumber = "+9779800000000"))
        val storage = object : SessionStorage {
            override fun getSession(sessionKey: String) = storedSession
            override fun saveSession(credentials: String, sessionKey: String) { storedSession = credentials }
            override fun clearSession(): Boolean { storedSession = null; return true }
            override fun clearOnReinstall(key: String) = Unit
        }
        sessions = SessionCoordinator(storage)
        lifetime = ApplicationLifetime()
        connectionRepository = ConnectionRepository(ConnectionApiService(client))
        vm = FindPartnerViewModel(
            connectionRepository,
            sessions,
            lifetime,
            countryProvider = { emptyList() },
        )
    }

    @AfterTest
    fun teardown() {
        vm.viewModelScope.cancel()
        lifetime.close()
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

    @Test fun manualStatusRecoveryDiscoversAcceptedRoomAfterPartnerAccepts() = runTest(dispatcher) {
        statusData = """{"connection_requested":true,"connection":{"id":7,"connection_status":"PENDING","receiver_number":"+9779812345678","partner":{"full_name":"Aayush"}}}"""
        vm.onScreenEntered()
        advanceUntilIdle()
        assertEquals(7L, vm.findPartnerUIState.value.sentInvitation?.requestId)

        statusData = """{"connection_requested":false,"connection":{"id":7,"connection_status":"ACCEPTED","updated_at":"2026-10-07T01:02:03Z","chat_room_id":"123e4567-e89b-12d3-a456-426614174000","couple_id":9,"partner":{"full_name":"Aayush"}}}"""
        vm.sendHaveCoupleConnectionRequested()
        advanceUntilIdle()

        assertNull(vm.findPartnerUIState.value.sentInvitation)
        assertEquals("Aayush", vm.findPartnerUIState.value.acceptedPartnerName)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", vm.findPartnerUIState.value.acceptedRoomId)
        assertEquals(9L, vm.findPartnerUIState.value.acceptedCoupleId)
        assertEquals("7 Oct 2026", vm.findPartnerUIState.value.connectedSince)
        assertTrue(vm.findPartnerUIState.value.acceptedNavigationPending)

        vm.onAcceptedNavigated()
        assertFalse(vm.findPartnerUIState.value.acceptedNavigationPending)
        vm.sendHaveCoupleConnectionRequested()
        advanceUntilIdle()
        assertFalse(vm.findPartnerUIState.value.acceptedNavigationPending)
    }

    @Test fun screenReentryRecoversAcceptanceWithoutChatSocket() = runTest(dispatcher) {
        statusData = """{"connection_requested":true,"connection":{"id":7,"connection_status":"PENDING","receiver_number":"+9779812345678","partner":{"full_name":"Aayush"}}}"""
        vm.onScreenEntered()
        advanceUntilIdle()
        val initialStatusReads = calls.count { it.first.endsWith("have-couple-connection-requested/") }

        statusData = """{"connection_requested":false,"connection":{"id":7,"connection_status":"ACCEPTED","chat_room_id":"123e4567-e89b-12d3-a456-426614174000","couple_id":9,"partner":{"full_name":"Aayush"}}}"""
        vm.onScreenEntered()
        advanceUntilIdle()

        assertEquals(initialStatusReads + 1, calls.count { it.first.endsWith("have-couple-connection-requested/") })
        assertEquals("Aayush", vm.findPartnerUIState.value.acceptedPartnerName)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", vm.findPartnerUIState.value.acceptedRoomId)
    }

    @Test fun authoritativeRevokedSnapshotClearsPreviouslyAcceptedIdentityAndRoom() = runTest(dispatcher) {
        statusData = """{"connection_requested":false,"connection":{"id":7,"connection_status":"ACCEPTED","updated_at":"2026-10-07T01:02:03Z","chat_room_id":"123e4567-e89b-12d3-a456-426614174000","couple_id":9,"partner":{"full_name":"Aayush","profile_photo":"https://example.test/avatar.jpg"}}}"""
        vm.onScreenEntered()
        advanceUntilIdle()
        assertEquals("Aayush", vm.findPartnerUIState.value.acceptedPartnerName)
        assertNotNull(vm.findPartnerUIState.value.acceptedRoomId)

        statusData = """{"connection_requested":false,"connection":null}"""
        vm.sendHaveCoupleConnectionRequested()
        advanceUntilIdle()

        val state = vm.findPartnerUIState.value
        assertNull(state.acceptedPartnerName)
        assertNull(state.acceptedPartnerPhotoUrl)
        assertNull(state.connectedSince)
        assertNull(state.acceptedRoomId)
        assertNull(state.acceptedCoupleId)
        assertFalse(state.acceptedNavigationPending)
    }

    @Test fun foregroundRefreshRecoversPendingInvitationAcceptanceWithoutSocket() = runTest(dispatcher) {
        statusData = """{"connection_requested":true,"connection":{"id":7,"connection_status":"PENDING","receiver_number":"+9779812345678","partner":{"full_name":"Aayush"}}}"""
        vm.onScreenEntered()
        advanceUntilIdle()
        val initialStatusReads = calls.count { it.first.endsWith("have-couple-connection-requested/") }
        assertEquals(7L, vm.findPartnerUIState.value.sentInvitation?.requestId)

        statusData = """{"connection_requested":false,"connection":{"id":7,"connection_status":"ACCEPTED","chat_room_id":"123e4567-e89b-12d3-a456-426614174000","couple_id":9,"partner":{"full_name":"Aayush"}}}"""
        lifetime.setForeground(true)
        advanceUntilIdle()

        assertEquals(initialStatusReads + 1, calls.count { it.first.endsWith("have-couple-connection-requested/") })
        assertEquals("Aayush", vm.findPartnerUIState.value.acceptedPartnerName)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", vm.findPartnerUIState.value.acceptedRoomId)
    }

    @Test fun inviteScreenPollingIsBoundedLifecycleOwnedAndFindsAcceptance() = runTest(dispatcher) {
        lifetime.setForeground(true)
        statusData = """{"connection_requested":true,"connection":{"id":7,"connection_status":"PENDING","receiver_number":"+9779812345678","partner":{"full_name":"Aayush"}}}"""
        vm.onScreenEntered()
        advanceUntilIdle()
        val initialStatusReads = calls.count { it.first.endsWith("have-couple-connection-requested/") }

        vm.startInviteStatusPolling()
        vm.startInviteStatusPolling()
        statusData = """{"connection_requested":false,"connection":{"id":7,"connection_status":"ACCEPTED","chat_room_id":"123e4567-e89b-12d3-a456-426614174000","couple_id":9,"partner":{"full_name":"Aayush"}}}"""
        advanceTimeBy(5_001)
        advanceUntilIdle()

        assertEquals(initialStatusReads + 1, calls.count { it.first.endsWith("have-couple-connection-requested/") })
        assertEquals("Aayush", vm.findPartnerUIState.value.acceptedPartnerName)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", vm.findPartnerUIState.value.acceptedRoomId)
    }

    @Test fun leavingInviteScreenCancelsStatusPolling() = runTest(dispatcher) {
        lifetime.setForeground(true)
        statusData = """{"connection_requested":true,"connection":{"id":7,"connection_status":"PENDING","receiver_number":"+9779812345678","partner":{"full_name":"Aayush"}}}"""
        vm.onScreenEntered()
        advanceUntilIdle()
        val initialStatusReads = calls.count { it.first.endsWith("have-couple-connection-requested/") }

        vm.startInviteStatusPolling()
        vm.stopInviteStatusPolling()
        advanceTimeBy(180_000)
        runCurrent()

        assertEquals(initialStatusReads, calls.count { it.first.endsWith("have-couple-connection-requested/") })
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

    @Test fun acceptanceRetryReusesIdempotencyKeyAndStoresTypedRoomId() = runTest(dispatcher) {
        updateStatus = HttpStatusCode.ServiceUnavailable
        vm.updateLoveRequest(42, "+9779812345678", "+9779800000000", Constants.ConnectionStatus.ACCEPTED)
        advanceUntilIdle()
        updateStatus = HttpStatusCode.OK
        updateData = """{"id":42,"connection_status":"ACCEPTED","chat_room_id":"123e4567-e89b-12d3-a456-426614174000","couple_id":9,"replayed":true}"""
        vm.updateLoveRequest(42, "+9779812345678", "+9779800000000", Constants.ConnectionStatus.ACCEPTED)
        advanceUntilIdle()
        assertEquals(2, idempotencyKeys.size)
        assertFalse(idempotencyKeys[0].isNullOrBlank())
        assertEquals(idempotencyKeys[0], idempotencyKeys[1])
        assertEquals("123e4567-e89b-12d3-a456-426614174000", vm.findPartnerUIState.value.acceptedRoomId)
        assertEquals(9L, vm.findPartnerUIState.value.acceptedCoupleId)
    }

    @Test fun connectionDateUsesServerValueAndRejectsInvalidDates() {
        assertEquals("10 Jan 2026", com.srisu.srisu.features.chat.presentation.findpartner.vm.formatConnectionDate("2026-01-10T12:34:56Z"))
        assertNull(com.srisu.srisu.features.chat.presentation.findpartner.vm.formatConnectionDate(null))
        assertNull(com.srisu.srisu.features.chat.presentation.findpartner.vm.formatConnectionDate("invalid"))
    }

    @Test fun relationshipContractsDecodeBigAutoFieldIdsBeyondIntRange() {
        val mutation = Json.decodeFromString<CoupleConnectionResponse>(
            """{"id":2147483648,"connection_status":"ACCEPTED"}"""
        )
        val status = Json.decodeFromString<HaveCoupleConnectionResponse>(
            """{"connection_requested":true,"connection":{"id":2147483649,"connection_status":"PENDING"}}"""
        )
        assertEquals(2_147_483_648L, mutation.id)
        assertEquals(2_147_483_649L, status.connection?.id)
    }

}
