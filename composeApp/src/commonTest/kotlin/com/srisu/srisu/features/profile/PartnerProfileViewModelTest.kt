package com.srisu.srisu.features.profile

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.features.home.connection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var client: HttpClient
    private lateinit var vm: ProfileViewModel
    private var status = HttpStatusCode.OK
    private var body = """{"data":{"user":{"id":2,"full_name":"Maya","bio":"Fresh biography","user_interests":[{"name":"Walking"}],"user_photos":[{"photo":"private.jpg"}]},"details_visible":true,"connection_state":"available"}}"""
    private var latency = 0L
    private val requests = mutableListOf<Pair<String, String?>>()
    @BeforeTest fun setup() {
        Dispatchers.setMain(dispatcher)
        client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = this@PartnerProfileViewModelTest.dispatcher
            addHandler { request ->
                requests += request.url.encodedPath to request.headers["X-Partner-Profile-Access"]
                val snapshot = body
                delay(latency)
                respond(snapshot, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        client.attributes.put(ApiEnvironmentKey, ApiEnvironment("https://synthetic.example/"))
        vm = ProfileViewModel(ConnectionRepository(ConnectionApiService(client)))
    }
    @AfterTest fun teardown() { vm.viewModelScope.cancel(); client.close(); Dispatchers.resetMain() }
    @Test fun fetchesSelectedIdAndShowsRealPermittedDetailsWithoutExtraPhotos() = runTest(dispatcher) {
        vm.loadProfile(2, "synthetic-access")
        assertTrue(vm.profileUIState.value.isLoading)
        advanceUntilIdle()
        assertEquals(listOf<Pair<String, String?>>("/api/social/partner-profiles/2/" to "synthetic-access"), requests)
        assertEquals("Fresh biography", vm.profileUIState.value.userProfileData?.bio)
        assertEquals("Walking", vm.profileUIState.value.userProfileData?.userInterests?.first()?.name)
        assertNull(vm.profileUIState.value.userProfileData?.userPhotos)
        assertEquals("available", vm.profileUIState.value.connectionState)
        vm.loadProfile(2, "synthetic-access")
        advanceUntilIdle()
        assertEquals(1, requests.size)
    }
    @Test fun retryReloadsDataAndRemovesDetailsOnOptOut() = runTest(dispatcher) {
        vm.loadProfile(2, null); advanceUntilIdle()
        body = body.replace("\"details_visible\":true", "\"details_visible\":false")
        vm.retry(); advanceUntilIdle()
        assertFalse(vm.profileUIState.value.detailsVisible)
        assertNull(vm.profileUIState.value.userProfileData?.bio)
        assertNull(vm.profileUIState.value.userProfileData?.userInterests)
    }
    @Test fun staleSelectionCannotOverwriteNewSelection() = runTest(dispatcher) {
        latency = 500
        vm.loadProfile(2, null); runCurrent()
        body = body.replace("\"id\":2", "\"id\":3").replace("Maya", "Alex")
        vm.loadProfile(3, null); advanceUntilIdle()
        assertEquals(3L, vm.profileUIState.value.userProfileData?.id)
        assertEquals("Alex", vm.profileUIState.value.userProfileData?.fullName)
    }
    @Test fun failureThenRetryRecoversWithoutStaleIdentity() = runTest(dispatcher) {
        status = HttpStatusCode.ServiceUnavailable
        vm.loadProfile(2, null); advanceUntilIdle()
        assertNotNull(vm.profileUIState.value.error)
        assertNull(vm.profileUIState.value.userProfileData)
        status = HttpStatusCode.OK
        vm.retry(); advanceUntilIdle()
        assertNull(vm.profileUIState.value.error)
        assertEquals(2L, vm.profileUIState.value.userProfileData?.id)
    }
    @Test fun unauthorizedForbiddenAndMissingDoNotLeaveOldDetails() = runTest(dispatcher) {
        vm.loadProfile(2, null); advanceUntilIdle()
        for (failure in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.NotFound)) {
            status = failure
            vm.retry(); advanceUntilIdle()
            assertNull(vm.profileUIState.value.userProfileData)
            assertEquals(failure.value, vm.profileUIState.value.error?.status)
        }
    }
    @Test fun invalidIdMakesNoRequestAndMismatchedResponseIsNotShown() = runTest(dispatcher) {
        vm.loadProfile(-1, null); advanceUntilIdle()
        assertTrue(requests.isEmpty())
        assertFalse(vm.profileUIState.value.isLoading)
        vm.loadProfile(3, null); advanceUntilIdle()
        assertNull(vm.profileUIState.value.userProfileData)
    }
}
