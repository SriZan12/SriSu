package com.srisu.srisu.features.chat

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.matrix.*
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in native E2EE acceptance against a separate synthetic Django/Synapse stack.
 * Fixture credentials are app-private and never included in assertion messages. */
class MatrixDeviceIntegrationTest {
    private class MemorySession(private var value: String?) : SessionStorage {
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }

    // Two independently protected stores model two devices without letting the
    // production account-switch cleanup wipe the other synthetic participant.
    private class ParticipantContext(base: Context, private val participant: Int) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir() = File(super.getNoBackupFilesDir(), "matrix-e2e-$participant").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("matrix-e2e-$participant-$name", mode)
    }

    @Test fun partnersExchangeEncryptedMessagesAndTyping(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureFile = File(context.filesDir, "matrix-device-fixture.json")
        assumeTrue("Requires the opt-in isolated Matrix device fixture", fixtureFile.isFile)
        val fixture = JSONObject(fixtureFile.readText())
        require(fixture.getString("scope") == "isolated-core-matrix-e2e")
        require(fixture.getString("base_url") == "http://127.0.0.1:8029/")
        val users = fixture.getJSONArray("users")
        for (i in 0..2) require(users.getJSONObject(i).getLong("id").toString().startsWith("8200000000000000"))
        val room = fixture.getString("room_id")
        val environment = ApiEnvironment(fixture.getString("base_url"), development = true)
        val sessions = (0..2).map { SessionCoordinator(MemorySession(users.getJSONObject(it).toString())) }
        val clients = sessions.map { HttpClientFactory.create(it, environment, OkHttp.create()) }
        val remotes = clients.map { MatrixSessionApiService(it, environment) }
        val lifetimes = (0..1).map { ApplicationLifetime() }
        val engines = (0..1).map { OkHttp.create() }
        val stores = (0..1).map { AndroidMatrixPlatformStore(ParticipantContext(context, it)) }
        val repositories = (0..1).map {
            TrixnityMatrixChatRepository(remotes[it], sessions[it], environment, engines[it], stores[it], lifetimes[it])
        }
        val owners = (0..1).map { MatrixChatRoomOwner() }

        suspend fun awaitState(index: Int, label: String, condition: (MatrixChatRoomState) -> Boolean): MatrixChatRoomState {
            val value = withTimeoutOrNull(180_000) { repositories[index].observeRoom(room).first(condition) }
            assertNotNull(label, value)
            return requireNotNull(value)
        }
        try {
            assertFalse("Unrelated user cannot bootstrap the couple room", remotes[2].create(room) is NetworkAPIResult.Success)
            for (i in 0..1) {
                withContext(Dispatchers.Main) { lifetimes[i].setForeground(true); repositories[i].openRoom(room, owners[i]) }
                awaitState(i, "Native Matrix participant ready") { it.availability is MatrixChatAvailability.Ready }
            }
            val first = "Synthetic encrypted Android delivery A"
            assertTrue("First encrypted send accepted", repositories[0].sendText(room, first).isSuccess)
            awaitState(1, "Partner decrypted first message") { state -> state.messages.any { !it.mine && it.body == first && !it.unavailable } }
            val second = "Synthetic encrypted Android delivery B"
            assertTrue("Reply encrypted send accepted", repositories[1].sendText(room, second).isSuccess)
            awaitState(0, "Partner decrypted reply") { state -> state.messages.any { !it.mine && it.body == second && !it.unavailable } }
            withContext(Dispatchers.Main) { repositories[1].setTyping(room, owners[1], true) }
            awaitState(0, "Partner typing arrived through Matrix sync") { it.partnerTyping }
            withContext(Dispatchers.Main) { repositories[1].setTyping(room, owners[1], false) }
            awaitState(0, "Partner typing cleared through Matrix sync") { !it.partnerTyping }
        } finally {
            fixtureFile.delete()
            withContext(Dispatchers.Main) { repositories.forEach { it.close() }; lifetimes.forEach { it.close() } }
            clients.forEach { it.close() }
            engines.forEach { it.close() }
        }
    }
}
