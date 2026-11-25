package com.srisu.srisu.features.settings

import com.srisu.srisu.core.MemorySession
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SettingsTransportIntegrationTest {
    @Test fun linkedUnlinkedForeignAndDisconnectThroughRealHttp() {
        val path=System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Requires disposable backend",path!=null)
        val fixture=ApiJson.parseToJsonElement(File(path!!).readText()).jsonObject
        val origin=fixture.getValue("base_url").jsonPrimitive.content
        require(java.net.URI(origin).host=="127.0.0.1")
        val environment=ApiEnvironment(origin,development=true)
        fun account(id:String,token:String):Pair<io.ktor.client.HttpClient,SettingsRepository> {
            val sessions=SessionCoordinator(MemorySession()).apply {
                saveSession(ApiJson.encodeToString(Session(id=fixture.getValue(id).jsonPrimitive.long,
                    access=fixture.getValue(token).jsonPrimitive.content)),SESSION_KEY)
            }
            val client=HttpClientFactory.create(sessions,environment,OkHttp.create())
            return client to SettingsRepository(client,environment)
        }
        val mine=account("account_id","access");val partner=account("partner_id","partner_access")
        val stranger=account("visitor_id","visitor_access")
        fun <T> NetworkAPIResult<T?>.value():T=assertNotNull(assertIs<NetworkAPIResult.Success<T?>>(this).response)
        try {runBlocking {withTimeout(30_000) {
            val context=mine.second.load().value()
            assertEquals("settings-1",context.contract)
            val selected=assertNotNull(context.partner)
            assertEquals(fixture.getValue("partner_id").jsonPrimitive.long,selected.userId)
            assertNotNull(selected.nicknameRevision)
            val renamed=assertNotNull(mine.second.saveNickname(selected,"  Sunshine 💛  ").value().partner)
            assertEquals("Sunshine 💛",renamed.nickname)
            assertEquals(selected.name,renamed.name)
            assertEquals("Sunshine 💛",mine.second.load().value().partner?.nickname)
            assertNull(partner.second.load().value().partner?.nickname)
            assertEquals(404,assertIs<NetworkAPIResult.Error<SettingsContext?>>(stranger.second.saveNickname(selected,"No")).failure.status)
            assertEquals(409,assertIs<NetworkAPIResult.Error<SettingsContext?>>(mine.second.saveNickname(selected,"Stale")).failure.status)
            val cleared=assertNotNull(mine.second.saveNickname(renamed,"").value().partner)
            assertNull(cleared.nickname)
            assertEquals(selected.name,cleared.name)
            val denied=assertIs<NetworkAPIResult.Error<SettingsContext?>>(stranger.second.disconnect(selected))
            assertEquals(404,denied.failure.status)
            assertNull(mine.second.disconnect(selected).value().partner)
            assertNull(partner.second.load().value().partner)
            assertNull(mine.second.disconnect(selected).value().partner)
        }}} finally {mine.first.close();partner.first.close();stranger.first.close()}
    }
}
