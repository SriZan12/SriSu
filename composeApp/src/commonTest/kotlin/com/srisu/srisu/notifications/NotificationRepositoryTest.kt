package com.srisu.srisu.notifications

import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.core.session
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryTest {
    @Test fun failedSaveKeepsConfirmedValueAndReloadRecovers() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val sessions=session();val lifetime=ApplicationLifetime()
        val client=HttpClientFactory.create(sessions,ApiEnvironment("https://fixture.invalid/"),MockEngine(MockEngineConfig().apply { this.dispatcher=dispatcher; addHandler { request ->
            if(request.method==HttpMethod.Put) respond("{}",HttpStatusCode.Conflict,headersOf(HttpHeaders.ContentType,"application/json"))
            else respond("""{"data":{"contract":"notifications-1","push":true,"chat":true,"activity":true,"revision":1}}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        } }))
        val repo=NotificationRepository(NotificationApi(client,ApiEnvironment("https://fixture.invalid/")),sessions,lifetime)
        try {
            repo.reload();advanceUntilIdle()
            assertTrue(repo.state.value.preferences!!.push)
            repo.save(push=false);advanceUntilIdle()
            assertTrue(repo.state.value.preferences!!.push)
            assertNotNull(repo.state.value.error)
            assertFalse(repo.state.value.busy)
            repo.reload();advanceUntilIdle();assertNull(repo.state.value.error)
        } finally {repo.close();lifetime.close();client.close();Dispatchers.resetMain()}
    }
    @Test fun missingResponseIsRecoverableAndAccountChangeCancelsOpen() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val sessions=session();val lifetime=ApplicationLifetime();val gate=CompletableDeferred<Unit>()
        val client=HttpClientFactory.create(sessions,ApiEnvironment("https://fixture.invalid/"),MockEngine(MockEngineConfig().apply { this.dispatcher=dispatcher; addHandler { request ->
            if(request.url.encodedPath.contains("deliveries")) gate.await()
            respond("""{"data":null}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        } }))
        val repo=NotificationRepository(NotificationApi(client,ApiEnvironment("https://fixture.invalid/")),sessions,lifetime)
        try {
            repo.reload();advanceUntilIdle();assertNotNull(repo.state.value.error)
            val result=async {repo.resolve(NotificationSignal("11111111-1111-4111-8111-111111111111",4102444800,true))}
            runCurrent();sessions.clearSession();gate.complete(Unit);runCurrent()
            assertTrue(result.isCancelled)
            assertNull(repo.state.value.preferences)
        } finally {repo.close();lifetime.close();client.close();Dispatchers.resetMain()}
    }
}
