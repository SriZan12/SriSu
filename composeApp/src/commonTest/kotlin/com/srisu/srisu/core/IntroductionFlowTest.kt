package com.srisu.srisu.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.auth.data.local.datastore.*
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.domain.*
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IntroductionFlowTest {
    private fun destination(coordinator: StartupCoordinator) = assertIs<StartupState.Available>(coordinator.state.value).destination
    private fun TestScope.client(sessions: SessionCoordinator, handler: MockRequestHandler = { error("Introduction must not call the backend") }) =
        HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler)
        }))

    @Test fun introductionOrderPersistsAcrossRestartsAndLoginBypassesSpace() = runTest {
        val sessions = SessionCoordinator(MemorySession())
        val client = client(sessions)
        val preferences = AuthDataStore(MemoryPreferences())
        val repo = AuthRepository(AuthApiService(client, environment))
        var scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var startup = StartupCoordinator(sessions, repo, scope, preferences)
        runCurrent(); assertEquals(AccessDestination.ONBOARDING, destination(startup))
        startup.showSpace(); runCurrent(); assertEquals(AccessDestination.SPACE, destination(startup))
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        startup = StartupCoordinator(sessions, repo, scope, preferences)
        runCurrent(); assertEquals(AccessDestination.SPACE, destination(startup))
        startup.showWelcome(); runCurrent(); assertEquals(AccessDestination.ONBOARDING, destination(startup))
        startup.beginAuthentication(); runCurrent(); assertEquals(AccessDestination.PHONE, destination(startup))
        assertEquals(IntroductionStep.PHONE, preferences.introductionStep())
        startup.leaveAuthentication(); runCurrent(); assertEquals(AccessDestination.ONBOARDING, destination(startup))
        startup.showSpace(); runCurrent(); startup.beginAuthentication(); runCurrent()
        assertEquals(AccessDestination.PHONE, destination(startup)) // Continue and Skip share this operation.
        startup.leaveAuthentication(); runCurrent(); assertEquals(AccessDestination.SPACE, destination(startup))
        assertNull(sessions.accessToken()); scope.cancel(); client.close()
    }

    @Test fun guestResumesWithoutCredentialsAndCanCancelAccountEntryAfterRepeatedTaps() = runTest {
        val sessions = SessionCoordinator(MemorySession())
        val client = client(sessions)
        val preferences = AuthDataStore(MemoryPreferences())
        preferences.saveIntroductionStep(IntroductionStep.GUEST)
        val startup = StartupCoordinator(sessions, AuthRepository(AuthApiService(client, environment)), backgroundScope, preferences)
        runCurrent(); assertEquals(AccessDestination.GUEST, destination(startup))
        repeat(5) { startup.beginAuthentication() }; runCurrent()
        assertEquals(AccessDestination.PHONE, destination(startup))
        assertEquals(IntroductionStep.GUEST, preferences.phoneReturnStep())
        startup.leaveAuthentication(); runCurrent(); assertEquals(AccessDestination.GUEST, destination(startup))
        assertEquals(IntroductionStep.GUEST, preferences.introductionStep())
        assertNull(sessions.currentSession()); client.close()
    }

    @Test fun accountEntryCanReturnToGuestAfterProcessRestart() = runTest {
        val sessions = SessionCoordinator(MemorySession())
        val client = client(sessions)
        val preferences = AuthDataStore(MemoryPreferences())
        preferences.saveIntroductionStep(IntroductionStep.PHONE, IntroductionStep.GUEST)
        val startup = StartupCoordinator(sessions, AuthRepository(AuthApiService(client, environment)), backgroundScope, preferences)
        runCurrent(); assertEquals(AccessDestination.PHONE, destination(startup))
        startup.leaveAuthentication(); runCurrent()
        assertEquals(AccessDestination.GUEST, destination(startup))
        assertNull(sessions.currentSession()); client.close()
    }

    @Test fun preferenceFailureRetriesTheChosenActionWithoutOpeningProtectedGraph() = runTest {
        var failWrite = false
        val backing = MemoryPreferences()
        val preferences = AuthDataStore(object : DataStore<Preferences> {
            override val data = backing.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failWrite) error("synthetic disk failure")
                return backing.updateData(transform)
            }
        })
        val sessions = SessionCoordinator(MemorySession())
        val client = client(sessions)
        val startup = StartupCoordinator(sessions, AuthRepository(AuthApiService(client, environment)), backgroundScope, preferences)
        runCurrent(); failWrite = true; startup.enterGuest(); runCurrent()
        assertIs<StartupState.Recovery>(startup.state.value)
        assertNull(sessions.accessToken())
        failWrite = false; startup.retry(); runCurrent()
        assertEquals(AccessDestination.GUEST, destination(startup)); client.close()
    }

    @Test fun authenticatedRestorationOverridesGuestPreferenceAndStaleCallbacks() = runTest {
        val sessions = session()
        val preferences = AuthDataStore(MemoryPreferences())
        preferences.saveIntroductionStep(IntroductionStep.GUEST)
        val client = client(sessions) {
            assertEquals("/api/auth/setup-profile/", it.url.encodedPath)
            respond(CoreContractFixtures.AUTH_PROFILE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val startup = StartupCoordinator(sessions, AuthRepository(AuthApiService(client, environment)), backgroundScope, preferences)
        runCurrent()
        assertEquals(AccessDestination.PHOTO, destination(startup))
        startup.enterGuest(); startup.showWelcome(); runCurrent()
        assertEquals(AccessDestination.PHOTO, destination(startup))
        assertEquals(IntroductionStep.PHONE, preferences.introductionStep())
        sessions.clearSession(); runCurrent()
        assertEquals(AccessDestination.PHONE, destination(startup)); client.close()
    }

    @Test fun guestCatalogueCallsOnlyPublicEndpointAndHasOfflineFallback() = runTest {
        val sessions = SessionCoordinator(MemorySession())
        var offline = false
        var calls = 0
        val client = client(sessions) { request ->
            calls++
            assertEquals("/api/auth/interests/", request.url.encodedPath)
            assertNull(request.headers[HttpHeaders.Authorization])
            if (offline) respond("", HttpStatusCode.ServiceUnavailable)
            else respond("""{"data":{"interests":[{"id":1,"name":"Hiking","category":{"id":1,"name":"Outdoors"}}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val repo = InterestCatalogueRepository(MemoryCatalogue(), ProfileApiService(client, environment), environment, sessions)
        assertEquals("Hiking", repo.load().value?.interests?.single()?.name)
        offline = true
        assertTrue(repo.load(force = true).offline)
        assertEquals(2, calls)
        assertNull(sessions.currentSession()); client.close()
    }

    @Test fun publicCatalogueDoesNotRefreshOrClearAnExistingSession() = runTest {
        val sessions = session()
        var calls = 0
        val client = client(sessions) { request ->
            calls++
            assertEquals("/api/auth/interests/", request.url.encodedPath)
            assertNull(request.headers[HttpHeaders.Authorization])
            respond("", HttpStatusCode.Unauthorized)
        }
        assertIs<NetworkAPIResult.Error<*>>(ProfileApiService(client, environment).getInterestList().result)
        assertEquals(1, calls)
        assertNotNull(sessions.currentSession()); client.close()
    }
}
