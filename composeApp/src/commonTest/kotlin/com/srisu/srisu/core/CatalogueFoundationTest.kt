@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.core

import com.srisu.srisu.core.data.local.*
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.home.profile.data.*
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.home.profile.presentation.state.InterestCatalogueStateHolder
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class MemoryCatalogue : CatalogueDao() {
    val rows = mutableMapOf<String, CatalogueSnapshot>()
    override suspend fun read(scope: String) = rows[scope]
    override suspend fun insert(snapshot: CatalogueSnapshot) { rows[snapshot.scope] = snapshot }
    override suspend fun delete(scope: String) { rows.remove(scope) }
}

class CatalogueFoundationTest {
    @Test fun freshnessOfflineExpiryAndAuthorizationFallback() = runTest {
        var calls = 0; var status = 200; var now = 100L
        val sessions = session(); val dao = MemoryCatalogue()
        val client = HttpClientFactory.create(sessions, environment, MockEngine {
            calls++
            respond("""{"data":{"interests":[{"id":1,"name":"Hiking","category":null}]}}""", HttpStatusCode.fromValue(status))
        })
        val repo = InterestCatalogueRepository(dao, ProfileApiService(client), environment, sessions, now = { now })
        assertEquals("Hiking", repo.load().value?.interests?.single()?.name)
        repo.load(); assertEquals(1, calls)
        now += InterestCatalogueRepository.FRESH_MS + 1; status = 503
        assertTrue(repo.load().offline)
        status = 403; assertNull(repo.load().value)
        status = 503; now += InterestCatalogueRepository.MAX_STALE_MS
        assertNull(repo.load().value)
        client.close()
    }

    @Test fun malformedCatalogueIsNotCachedAsAnEmptySuccess() = runTest {
        val sessions = session(); val dao = MemoryCatalogue()
        val client = HttpClientFactory.create(sessions, environment, MockEngine { respond("""{"data":{}}""") })
        val result = InterestCatalogueRepository(dao, ProfileApiService(client), environment, sessions).load()
        assertEquals(NetworkAPIResult.ErrorType.SERIALIZATION, result.error?.kind)
        assertFalse(result.offline); assertTrue(dao.rows.isEmpty())
        client.close()
    }

    @Test fun cancelledRequestCannotPopulateCacheAfterLogout() = runTest {
        val sessions = session(); val dao = MemoryCatalogue(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClientFactory.create(sessions, environment, MockEngine { entered.complete(Unit); release.await(); respond("""{"data":{"interests":[]}}""") })
        val repo = InterestCatalogueRepository(dao, ProfileApiService(client), environment, sessions)
        val request = async { repo.load() }; entered.await(); sessions.clearSession(); release.complete(Unit)
        assertFailsWith<CancellationException> { request.await() }; assertTrue(dao.rows.isEmpty())
        client.close()
    }

    @Test fun presentationKeepsDurableOfflineAndEmptyStatesAndCancelsReplacedLoad() = runTest {
        var calls = 0
        val holder = InterestCatalogueStateHolder(backgroundScope) {
            calls++
            if (calls == 1) delay(1_000)
            CatalogueResult(InterestResponse(emptyList()), offline = calls == 2)
        }
        holder.refresh(); runCurrent(); assertTrue(holder.state.value.loading)
        holder.refresh(true); runCurrent()
        assertFalse(holder.state.value.loading); assertTrue(holder.state.value.offline)
        assertTrue(holder.state.value.loaded); assertTrue(holder.state.value.items.isEmpty())
        advanceTimeBy(2_000); runCurrent(); assertTrue(holder.state.value.offline)
    }
}
