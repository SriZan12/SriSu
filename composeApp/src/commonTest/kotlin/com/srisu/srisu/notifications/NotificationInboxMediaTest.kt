package com.srisu.srisu.notifications

import com.srisu.srisu.core.session
import com.srisu.srisu.core.environment
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.notifications.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationInboxMediaTest {
    @Test fun mediaUsesFixedAuthenticatedPathsAndRejectsOversizedAndDeniedResponses()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions=session(); val lifetime=ApplicationLifetime()
        var size=100;var denied=false;var calls=0
        val client=HttpClientFactory.create(sessions,environment,MockEngine(MockEngineConfig().apply {
            dispatcher=StandardTestDispatcher(testScheduler)
            addHandler { request ->
            calls++
            assertEquals("/api/notifications/v1/inbox/${InboxFixtures.id(1)}/media/avatar/",request.url.encodedPath)
            assertNotNull(request.headers[HttpHeaders.Authorization])
            respond(ByteArray(size),if(denied) HttpStatusCode.NotFound else HttpStatusCode.OK)
            }
        }))
        val repo=NotificationInboxRepository(client,environment,sessions,lifetime)
        try {
            assertEquals(100,repo.image(InboxFixtures.id(1),"avatar")?.size)
            size=256*1024+1;assertNull(repo.image(InboxFixtures.id(1),"avatar"))
            denied=true;assertNull(repo.image(InboxFixtures.id(1),"avatar"))
            assertNull(repo.image("https://other.invalid/","avatar"))
            assertNull(repo.image(InboxFixtures.id(1),"../../other"))
            assertEquals(3,calls)
        } finally {repo.close();client.close();lifetime.close();Dispatchers.resetMain()}
    }
}
