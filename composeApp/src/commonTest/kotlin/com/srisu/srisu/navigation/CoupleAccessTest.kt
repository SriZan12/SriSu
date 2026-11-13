@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.navigation

import com.srisu.srisu.core.*
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.auth.domain.*
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class CoupleAccessTest {
    @Test fun membershipFailureDoesNotLogOutAndRevocationRemovesOldIdentity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = session(); val lifetime = ApplicationLifetime()
        var status = HttpStatusCode.OK
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (request.url.encodedPath.contains("/auth/")) respond("""{"data":{"user":{"id":1,"full_name":"Synthetic User","username":"synthetic","is_phone_verified":true,"is_profile_complete":true},"progress":{"phone_verified":true,"profile_complete":true,"next_step":"complete","photo_skipped":true,"membership":"unlinked","couple_id":null}}}""", headers = headersOf(HttpHeaders.ContentType,"application/json"))
                else respond(CoreContractFixtures.COUPLE_PROFILE_MEMBER, status, headersOf(HttpHeaders.ContentType,"application/json"))
            }
        }))
        try {
            val startup = StartupCoordinator(sessions, AuthRepository(AuthApiService(client, environment)), lifetime.scope, AuthDataStore(MemoryPreferences()))
            val access = CoupleAccessCoordinator(sessions, startup, CoupleProfileRepository(client, environment, sessions), lifetime)
            advanceUntilIdle()
            val initial = assertIs<CoupleAccess.Ready>(access.state.value)
            assertNotNull(initial.coupleId)
            status = HttpStatusCode.ServiceUnavailable; access.retry(); advanceUntilIdle()
            assertEquals(initial.coupleId, assertIs<CoupleAccess.Ready>(access.state.value).coupleId)
            assertNotNull(assertIs<CoupleAccess.Ready>(access.state.value).warning)
            assertNotNull(sessions.accessToken())
            status = HttpStatusCode.NotFound; access.retry(); advanceUntilIdle()
            assertNull(assertIs<CoupleAccess.Ready>(access.state.value).coupleId)
            sessions.clearSession(); advanceUntilIdle()
            assertIs<CoupleAccess.Resolving>(access.state.value)
        } finally { lifetime.close(); client.close(); Dispatchers.resetMain() }
    }
}
