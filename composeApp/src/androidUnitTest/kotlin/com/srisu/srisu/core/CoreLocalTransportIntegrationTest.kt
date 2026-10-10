@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
package com.srisu.srisu.core

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.local.CatalogueDatabase
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.chat.data.remote.api.*
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.home.connection.data.remote.api.ConnectionApiService
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CoreLocalTransportIntegrationTest {
    @Test fun pairedHttpSocketDatabaseAndLogout() {
        val fixturePath = System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Run with tools/core_integration.py for a disposable backend", fixturePath != null)
        val fixture = ApiJson.parseToJsonElement(File(fixturePath!!).readText()).jsonObject
        val dispatcher = newSingleThreadContext("core-integration-main")
        Dispatchers.setMain(dispatcher)
        val environment = ApiEnvironment(fixture["base_url"]!!.jsonPrimitive.content, development = true)
        val sessions = SessionCoordinator(MemorySession()).apply {
            saveSession(ApiJson.encodeToString(Session(id = fixture["account_id"]!!.jsonPrimitive.long, access = fixture["access"]!!.jsonPrimitive.content)), SESSION_KEY)
        }
        val client = HttpClientFactory.create(sessions, environment, OkHttp.create { config { retryOnConnectionFailure(false) } })
        val lifetime = ApplicationLifetime()
        val lastFailure = java.util.concurrent.atomic.AtomicReference("none")
        val connector = KtorSocketConnector(client, environment)
        val socket = ChatWebSocketClient(SocketConnector {
            try { connector.open() } catch (failure: Exception) {
                lastFailure.set(failure.javaClass.name + " at " + failure.stackTrace.take(4).joinToString())
                throw failure
            }
        }, sessions, lifetime)
        val repository = ChatRepository(socket, ChatApiService(client, environment), sessions, lifetime)
        val path = RuntimeEnvironment.getApplication().getDatabasePath("integration-catalogue.db").absolutePath
        val database = Room.databaseBuilder<CatalogueDatabase>(RuntimeEnvironment.getApplication(), path).setDriver(AndroidSQLiteDriver()).build()
        try { runBlocking {
            withTimeout(15_000) {
                val catalogue = InterestCatalogueRepository(database.catalogue(), ProfileApiService(client, environment), environment, sessions)
                assertEquals("Synthetic hiking", catalogue.load(force = true).value?.interests?.single()?.name)
                assertNotNull(database.catalogue().read("interests:1:${environment.baseUrl}"))
                // Real service calls (including the formerly global-URL services)
                // must reach this disposable backend and decode its wire responses.
                val profile = assertIs<NetworkAPIResult.Success<*>>(AuthApiService(client).getProfile().result)
                assertNotNull(profile.response)
                val auth = AuthApiService(client)
                for (gender in listOf("FEMALE", "MALE")) {
                    val saved = assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.auth.data.remote.response.ProfileResponse?>>(auth.updateGender(gender).result)
                    assertEquals(gender, saved.response?.user?.gender)
                    val restored = assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.auth.data.remote.response.ProfileResponse?>>(auth.getProfile().result)
                    assertEquals(gender, restored.response?.user?.gender)
                    assertEquals("complete", restored.response?.progress?.nextStep)
                }
                assertEquals(400, assertIs<NetworkAPIResult.Error<*>>(auth.updateGender("NONE").result).failure.status)
                val connections = ConnectionApiService(client)
                assertIs<NetworkAPIResult.Success<*>>(connections.getSentLoveRequests(20, 1).result)
                assertIs<NetworkAPIResult.Success<*>>(connections.getLoveRequests(1, 20).result)
                assertIs<NetworkAPIResult.Success<*>>(connections.haveCoupleConnectionRequested().result)
                assertIs<NetworkAPIResult.Success<*>>(connections.sendFindYourPartnerRequest("+15005550102").result)
                // Couple Profile uses real HTTP bodies, migrations and guarded media.
                val couple = com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository(client, environment, sessions)
                val current = assertNotNull(assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.coupleprofile.data.CoupleProfile?>>(couple.load(null)).response)
                val song = assertNotNull(assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.coupleprofile.data.CoupleProfile?>>(couple.save(current.id,"song",buildJsonObject { put("expected_revision",current.revisions.getValue("song"));put("title","Synthetic song") })).response)
                assertEquals("Synthetic song",song.song?.title)
                assertEquals(409,assertIs<NetworkAPIResult.Error<*>>(couple.save(current.id,"song",buildJsonObject{put("expected_revision",current.revisions.getValue("song"));put("title","Stale") })).failure.status)
                val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
                val imageBytes=java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
                bitmap.recycle()
                val cover=assertNotNull(assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.coupleprofile.data.CoupleProfile?>>(couple.cover(current.id,song.revisions.getValue("cover"),.3f,com.srisu.srisu.utils.MediaFile(id=null,fileName="synthetic.png",mimeType="image/png",fileBytes=imageBytes),null)).response)
                assertNotNull(couple.image(requireNotNull(cover.cover?.url)))
                val partnerSessions=SessionCoordinator(MemorySession()).apply{saveSession(ApiJson.encodeToString(Session(id=fixture.getValue("partner_id").jsonPrimitive.long,access=fixture.getValue("partner_access").jsonPrimitive.content)),SESSION_KEY)}
                val visitorSessions=SessionCoordinator(MemorySession()).apply{saveSession(ApiJson.encodeToString(Session(id=fixture.getValue("visitor_id").jsonPrimitive.long,access=fixture.getValue("visitor_access").jsonPrimitive.content)),SESSION_KEY)}
                val partnerClient=HttpClientFactory.create(partnerSessions,environment,OkHttp.create())
                val visitorClient=HttpClientFactory.create(visitorSessions,environment,OkHttp.create())
                try {
                    val partner=com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository(partnerClient,environment,partnerSessions)
                    val visitor=com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository(visitorClient,environment,visitorSessions)
                    val hidden=assertNotNull(assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.coupleprofile.data.CoupleProfile?>>(visitor.load(current.id)).response)
                    assertNull(hidden.song);assertNull(hidden.cover);assertFalse(hidden.canEdit)
                    assertNull(visitor.image(requireNotNull(cover.cover?.url)))
                    assertIs<NetworkAPIResult.Success<*>>(couple.save(current.id,"sharing",buildJsonObject{put("expected_revision",cover.revisions.getValue("sharing"));put("action","propose");put("sections",JsonArray(listOf(JsonPrimitive("song"),JsonPrimitive("cover"))))}))
                    val review=assertNotNull(assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.coupleprofile.data.CoupleProfile?>>(partner.load(current.id)).response)
                    assertIs<NetworkAPIResult.Success<*>>(partner.save(current.id,"sharing",buildJsonObject{put("expected_revision",review.revisions.getValue("sharing"));put("action","approve")}))
                    val visible=assertNotNull(assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.coupleprofile.data.CoupleProfile?>>(visitor.load(current.id)).response)
                    assertEquals("Synthetic song",visible.song?.title);assertNull(visible.members)
                    assertNotNull(visitor.image(requireNotNull(visible.cover?.url)))
                    assertIs<NetworkAPIResult.Success<*>>(couple.save(current.id,"sharing",buildJsonObject{put("expected_revision",cover.revisions.getValue("sharing"));put("action","revoke")}))
                    assertNull(visitor.image(requireNotNull(cover.cover?.url)))
                    assertIs<NetworkAPIResult.Success<*>>(couple.invite(current.id,"how_met",java.util.UUID.randomUUID().toString()))
                    val planned=assertIs<NetworkAPIResult.Success<com.srisu.srisu.features.coupleprofile.data.CouplePlan?>>(couple.createPlan(current.id,buildJsonObject{put("request_id",java.util.UUID.randomUUID().toString());put("title","Synthetic walk");put("starts_at",java.time.Instant.now().plusSeconds(3600).toString())})).response
                    assertNotNull(planned)
                    assertIs<NetworkAPIResult.Success<*>>(partner.respond(current.id,planned.id,buildJsonObject{put("expected_revision",planned.revision);put("response","yes")}))
                    assertEquals(404,assertIs<NetworkAPIResult.Error<*>>(visitor.plan(current.id,planned.id)).failure.status)
                } finally {partnerClient.close();visitorClient.close()}
                withContext(dispatcher) { lifetime.setForeground(true); repository.connect() }
                val connected = withTimeoutOrNull(12_000) { socket.connectionState.first { it == SocketState.Connected } }
                assertNotNull(connected, "Socket state=${socket.connectionState.value}; failure=${lastFailure.get()}")
                val room = fixture["room_id"]!!.jsonPrimitive.content
                repository.chatRoomsList.first { rooms -> rooms.any { it.id == room } }
                repository.fetchInitialMessages(room)
                repository.messages.first { messages -> messages.any { it.text == "Synthetic baseline" } }
                repository.sendMessage(room, text = "Synthetic KMP message")
                repository.messages.first { messages -> messages.any { it.text == "Synthetic KMP message" } }
                sessions.clearSession()
                socket.connectionState.first { it == SocketState.Disconnected }
                repository.messages.first { it.isEmpty() }
            }
        } } finally {
            repository.close(); socket.close(); lifetime.close(); client.close(); database.close()
            Dispatchers.resetMain(); dispatcher.close()
        }
    }
}
