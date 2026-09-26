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
