package com.srisu.srisu.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.auth.domain.*
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import com.srisu.srisu.utils.profileImageDimensionsValid
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.test.*

class MemoryPreferences : DataStore<Preferences> {
    private val values = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = values
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(values.value).also { values.value = it }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationFlowTest {
    private fun TestScope.engine(handler: MockRequestHandler) = MockEngine(MockEngineConfig().apply { dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler) })
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private fun profile(step: String) = """{"data":{"user":{"id":1,"full_name":"Test User","username":"test","is_phone_verified":true,"is_profile_complete":${step == "complete"}},"progress":{"phone_verified":true,"profile_complete":${step == "complete"},"next_step":"$step","photo_skipped":${step == "complete"},"couple_id":null,"membership":"unlinked"}}}"""

    @Test fun bootstrapUsesServerProgressAndDeduplicates() = runTest {
        for ((step, destination) in listOf("name" to AccessDestination.NAME, "photo" to AccessDestination.GENDER, "complete" to AccessDestination.MAIN)) {
            var reads = 0
            val sessions = session()
            val client = HttpClientFactory.create(sessions, environment, engine { reads++; respond(profile(step), headers = headers) })
            val repo = AuthRepository(AuthApiService(client, environment))
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val coordinator = StartupCoordinator(sessions, repo, scope, AuthDataStore(MemoryPreferences()))
            runCurrent()
            val state = coordinator.state.first { it !is StartupState.Loading }.let { assertIs<StartupState.Available>(it, it.toString()) }
            assertEquals(destination, state.destination)
            repeat(5) { coordinator.state.value }
            assertEquals(1, reads)
            scope.cancel(); client.close()
        }
    }

    @Test fun networkFailureKeepsCredentialsAndRetryCanRecover() = runTest {
        val sessions = session()
        var fail = true
        val client = HttpClientFactory.create(sessions, environment, engine {
            if (fail) respond("", HttpStatusCode.ServiceUnavailable) else respond(profile("complete"), headers = headers)
        })
        val coordinator = StartupCoordinator(sessions, AuthRepository(AuthApiService(client, environment)), backgroundScope, AuthDataStore(MemoryPreferences()))
        runCurrent()
        assertIs<StartupState.Recovery>(coordinator.state.first { it !is StartupState.Loading })
        assertNotNull(sessions.accessToken())
        fail = false; coordinator.retry(); runCurrent()
        assertEquals(AccessDestination.MAIN, coordinator.state.first { it !is StartupState.Loading }.let { assertIs<StartupState.Available>(it, it.toString()) }.destination)
        client.close()
    }

    @Test fun sessionChangeDiscardsLateBootstrap() = runTest {
        val sessions = session()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClientFactory.create(sessions, environment, engine {
            entered.complete(Unit); withContext(NonCancellable) { release.await() }
            respond(profile("complete"), headers = headers)
        })
        val coordinator = StartupCoordinator(sessions, AuthRepository(AuthApiService(client, environment)), backgroundScope, AuthDataStore(MemoryPreferences()))
        runCurrent(); entered.await()
        sessions.clearSession(); runCurrent(); release.complete(Unit); runCurrent()
        assertEquals(AccessDestination.PHONE, coordinator.state.first { it !is StartupState.Loading }.let { assertIs<StartupState.Available>(it, it.toString()) }.destination)
        assertNull(sessions.accessToken()); client.close()
    }

    @Test fun phoneRequestDuplicatesAndEditingDiscardStaleChallenge() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = SessionCoordinator(MemorySession())
        var sends = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = HttpClientFactory.create(sessions, environment, engine {
            sends++; entered.complete(Unit); withContext(NonCancellable) { release.await() }
            respond("""{"data":{"challenge_id":"synthetic-challenge","expires_at":"2030-01-01T00:05:00Z","resend_at":"2030-01-01T00:01:00Z","server_time":"2030-01-01T00:00:00Z","retry_after_seconds":60}}""", headers = headers)
        })
        val repo = AuthRepository(AuthApiService(client, environment))
        val startup = StartupCoordinator(sessions, repo, backgroundScope, AuthDataStore(MemoryPreferences()))
        val vm = AuthViewModel(repo, sessions, AuthDataStore(MemoryPreferences()), startup)
        val owner = ViewModelStore().apply { put("auth", vm) }
        try {
            vm.updateCountry("US", "+1"); vm.updatePhoneNumber("5005550101") {}
            repeat(5) { vm.requestOTP { fail("Navigation must observe accepted state") } }
            runCurrent(); entered.await(); assertEquals(1, sends)
            vm.updatePhoneNumber("5005550102") {}
            release.complete(Unit); runCurrent()
            assertNull(vm.authUiState.value.challengeId)
            assertNull(sessions.accessToken())
        } finally { owner.clear(); client.close(); Dispatchers.resetMain() }
    }

    @Test fun staleVerificationCannotAuthenticateAfterEditingPhone() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = SessionCoordinator(MemorySession())
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var verifications = 0
        val client = HttpClientFactory.create(sessions, environment, engine { request ->
            if (request.url.encodedPath.endsWith("send-otp/")) {
                respond(CoreContractFixtures.AUTH_CHALLENGE, headers = headers)
            } else {
                verifications++; entered.complete(Unit); withContext(NonCancellable) { release.await() }
                respond("""{"data":{"user":{"id":1,"is_phone_verified":true},"tokens":{"access":"synthetic","refresh":"synthetic"}}}""", headers = headers)
            }
        })
        val repo = AuthRepository(AuthApiService(client, environment))
        val startup = StartupCoordinator(sessions, repo, backgroundScope, AuthDataStore(MemoryPreferences()))
        val vm = AuthViewModel(repo, sessions, AuthDataStore(MemoryPreferences()), startup)
        val owner = ViewModelStore().apply { put("auth", vm) }
        try {
            vm.updateCountry("US", "+1"); vm.updatePhoneNumber("5005550101") {}
            vm.requestOTP {}; vm.authUiState.first { it.challengeId != null }
            repeat(6) { vm.updateOtpValues(it, "1") }
            repeat(4) { vm.verifyOtp({}, {}) }
            entered.await(); assertEquals(1, verifications)
            vm.updatePhoneNumber("5005550102") {}
            release.complete(Unit); runCurrent()
            assertNull(sessions.accessToken())
            assertTrue(vm.authUiState.value.optValues.all { it.isEmpty() })
        } finally { owner.clear(); client.close(); Dispatchers.resetMain() }
    }

    private fun token(exp: Long) = "test." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("""{"exp":$exp,"sid":"synthetic"}""".encodeToByteArray()) + ".synthetic"

    @Test fun legacyAccessOnlySessionRemainsUsableUntilServerRejectsIt() = runTest {
        val legacy = "test." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"exp":2000}""".encodeToByteArray()) + ".synthetic"
        val sessions = SessionCoordinator(MemorySession(), epochSeconds = { 1000 }).apply {
            saveSession(ApiJson.encodeToString(Session(id = 1, access = legacy)), SESSION_KEY)
        }
        var denied = false
        var calls = 0
        val client = HttpClientFactory.create(sessions, environment, engine { request ->
            calls++
            assertEquals("/protected", request.url.encodedPath)
            assertEquals("Bearer $legacy", request.headers[HttpHeaders.Authorization])
            if (denied) respond("", HttpStatusCode.Unauthorized)
            else respond("""{"data":{}}""", headers = headers)
        })
        assertIs<NetworkAPIResult.Success<*>>(client.safeRequest<JsonObject> { url("https://example.test/protected") }.result)
        assertNotNull(sessions.accessToken())
        denied = true
        assertIs<NetworkAPIResult.Error<*>>(client.safeRequest<JsonObject> { url("https://example.test/protected") }.result)
        assertEquals(2, calls)
        assertNull(sessions.accessToken())
        client.close()
    }

    @Test fun concurrentRefreshIsSingleFlightAndPublicEndpointHasNoBearer() = runTest {
        val sessions = SessionCoordinator(MemorySession(), epochSeconds = { 1000 }).apply {
            saveSession(ApiJson.encodeToString(Session(id = 1, access = token(1), refresh = "synthetic-refresh")), SESSION_KEY)
        }
        var refreshes = 0
        val client = HttpClientFactory.create(sessions, environment, engine { request ->
            if (request.url.encodedPath == "/api/auth/refresh/") {
                refreshes++; assertNull(request.headers[HttpHeaders.Authorization]); delay(10)
                respond("""{"data":{"tokens":{"access":"${token(4102444800)}","refresh":"rotated-synthetic"}}}""", headers = headers)
            } else respond("""{"data":{}}""", headers = headers)
        })
        val calls = List(8) { async { client.safeRequest<JsonObject> { url("https://example.test/protected") } } }
        calls.awaitAll().forEach { assertIs<NetworkAPIResult.Success<*>>(it.result) }
        assertEquals(1, refreshes)
        client.close()
    }

    @Test fun logoutDuringRefreshCannotRestoreSession() = runTest {
        val sessions = SessionCoordinator(MemorySession(), epochSeconds = { 1000 }).apply {
            saveSession(ApiJson.encodeToString(Session(id = 1, access = token(1), refresh = "synthetic")), SESSION_KEY)
        }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClientFactory.create(sessions, environment, engine {
            entered.complete(Unit); release.await()
            respond("""{"data":{"tokens":{"access":"${token(4102444800)}","refresh":"rotated"}}}""", headers = headers)
        })
        val pending = async { client.safeRequest<JsonObject> { url("https://example.test/protected") } }
        entered.await(); sessions.clearSession(); release.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertNull(sessions.accessToken()); client.close()
    }

    @Test fun refreshOutageDoesNotReplayProtectedWriteOrLogout() = runTest {
        val sessions = SessionCoordinator(MemorySession(), epochSeconds = { 1000 }).apply {
            saveSession(ApiJson.encodeToString(Session(id = 1, access = token(1), refresh = "synthetic")), SESSION_KEY)
        }
        var calls = 0
        val client = HttpClientFactory.create(sessions, environment, engine { request ->
            calls++; assertEquals("/api/auth/refresh/", request.url.encodedPath)
            respond("", HttpStatusCode.ServiceUnavailable)
        })
        assertIs<NetworkAPIResult.Error<*>>(client.safeRequest<JsonObject> { url("https://example.test/write"); method = HttpMethod.Post }.result)
        assertEquals(1, calls); assertNotNull(sessions.accessToken()); client.close()
    }

    @Test fun missingRefreshRouteReportsBackendMismatchWithoutSendingProtectedRequest() = runTest {
        val sessions = SessionCoordinator(MemorySession(), epochSeconds = { 1000 }).apply {
            saveSession(ApiJson.encodeToString(Session(id = 1, access = token(1), refresh = "synthetic")), SESSION_KEY)
        }
        var calls = 0
        val client = HttpClientFactory.create(sessions, environment, engine { request ->
            calls++
            assertEquals("/api/auth/refresh/", request.url.encodedPath)
            respond("<html>Not Found</html>", HttpStatusCode.NotFound)
        })
        try {
            val error = assertIs<NetworkAPIResult.Error<*>>(client.safeRequest<JsonObject> {
                url("https://example.test/api/auth/setup-profile/")
            }.result).failure
            assertEquals("backend_upgrade_required", error.code)
            assertEquals(404, error.status)
            assertEquals(1, calls)
            assertNotNull(sessions.accessToken())
        } finally { client.close() }
    }

    @Test fun socketHandshakeRenewsCredentialsBeforeConnecting() = runTest {
        val sessions = SessionCoordinator(MemorySession(), epochSeconds = { 1000 }).apply {
            saveSession(ApiJson.encodeToString(Session(id = 1, access = token(1), refresh = "synthetic")), SESSION_KEY)
        }
        var refreshes = 0
        val client = HttpClientFactory.create(sessions, environment, engine { request ->
            assertEquals("/api/auth/refresh/", request.url.encodedPath)
            refreshes++
            respond("", HttpStatusCode.ServiceUnavailable)
        })
        assertFailsWith<SocketUnavailable> { KtorSocketConnector(client, environment).open() }
        assertEquals(1, refreshes)
        assertNotNull(sessions.accessToken())
        client.close()
    }

    @Test fun backendFixturesParseWithRequiredProgressAndChallengeMetadata() {
        val challenge = ApiJson.parseToJsonElement(CoreContractFixtures.AUTH_CHALLENGE).let { it as kotlinx.serialization.json.JsonObject }["data"]!!
        val profile = ApiJson.parseToJsonElement(CoreContractFixtures.AUTH_PROFILE).let { it as kotlinx.serialization.json.JsonObject }["data"]!!
        val parsedChallenge = ApiJson.decodeFromJsonElement(com.srisu.srisu.features.auth.data.remote.response.OtpChallenge.serializer(), challenge)
        val parsedProfile = ApiJson.decodeFromJsonElement(com.srisu.srisu.features.auth.data.remote.response.ProfileResponse.serializer(), profile)
        assertEquals(60, parsedChallenge.retryAfterSeconds)
        assertEquals("photo", parsedProfile.progress?.nextStep)
        assertFalse(parsedProfile.progress!!.photoSkipped)
    }

    @Test fun rejectedAccessRefreshesOnceAndNeverReplaysWrites() = runTest {
        for (method in listOf(HttpMethod.Get, HttpMethod.Post)) {
            val sessions = SessionCoordinator(MemorySession(), epochSeconds = { 1000 }).apply {
                saveSession(ApiJson.encodeToString(Session(id = 1, access = token(4102444800), refresh = "synthetic")), SESSION_KEY)
            }
            var writes = 0; var refreshes = 0
            val client = HttpClientFactory.create(sessions, environment, engine { request ->
                if (request.url.encodedPath.endsWith("refresh/")) {
                    refreshes++
                    respond("""{"data":{"tokens":{"access":"${token(4102444900)}","refresh":"rotated"}}}""", headers = headers)
                } else {
                    writes++
                    if (writes == 1) respond("", HttpStatusCode.Unauthorized)
                    else respond("""{"data":{}}""", headers = headers)
                }
            })
            val result = client.safeRequest<JsonObject> { url("https://example.test/protected"); this.method = method }.result
            assertEquals(1, refreshes)
            assertNotNull(sessions.accessToken())
            assertEquals(if (method == HttpMethod.Get) 2 else 1, writes)
            if (method == HttpMethod.Get) assertIs<NetworkAPIResult.Success<*>>(result)
            else assertEquals("session_refreshed", assertIs<NetworkAPIResult.Error<*>>(result).failure.code)
            client.close()
        }
    }

    @Test fun profileImageHeadersRejectOversizedAndTruncated() {
        assertFalse(profileImageDimensionsValid(ByteArray(3)))
        val png = ByteArray(24)
        png[0] = 137.toByte(); "PNG".encodeToByteArray().copyInto(png, 1)
        png[19] = 10; png[23] = 10
        assertTrue(profileImageDimensionsValid(png))
        png[16] = 127
        assertFalse(profileImageDimensionsValid(png))
    }
}
