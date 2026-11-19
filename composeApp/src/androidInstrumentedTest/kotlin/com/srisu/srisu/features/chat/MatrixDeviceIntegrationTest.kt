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
            val run = System.nanoTime()
            val first = "Synthetic encrypted Android delivery A $run"
            assertTrue("First encrypted send accepted", repositories[0].sendText(room, first).isSuccess)
            awaitState(1, "Partner decrypted first message") { state -> state.messages.any { !it.mine && it.body == first && !it.unavailable } }
            val second = "Synthetic encrypted Android delivery B $run"
            assertTrue("Reply encrypted send accepted", repositories[1].sendText(room, second).isSuccess)
            awaitState(0, "Partner decrypted reply") { state -> state.messages.any { !it.mine && it.body == second && !it.unavailable } }
            val original = awaitState(0, "Own send acknowledged") { state -> state.messages.any { it.mine && it.body == first && it.delivery == MatrixMessageDelivery.SENT } }
                .messages.first { it.mine && it.body == first }.id
            assertTrue("Sender can edit encrypted text", repositories[0].editMessage(room, original, "Synthetic edited text").isSuccess)
            awaitState(1, "Partner receives edit on original bubble") { state -> state.messages.any { it.id == original && it.body == "Synthetic edited text" && it.edited } }
            assertTrue("Partner cannot edit sender's text", repositories[1].editMessage(room, original, "Unauthorized").isFailure)
            assertTrue("Partner cannot delete sender's text", repositories[1].deleteMessage(room, original).isFailure)
            val image = android.graphics.Bitmap.createBitmap(1024, 768, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(image).apply {
                drawColor(android.graphics.Color.rgb(235, 215, 202)) // theme-audit: image pixels
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(28, 53, 68); textSize = 54f; isAntiAlias = true } // theme-audit: image pixels
                drawText("SriSu synthetic quality check", 30f, 200f, paint)
            }
            val photo = java.io.ByteArrayOutputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }
            image.recycle()
            for ((kind, bytes, mime) in listOf(Triple("photo", photo, "image/jpeg"), Triple("voice", ByteArray(2048), "audio/wav"))) {
                val label = "Synthetic $kind deletion check"
                val media = com.srisu.srisu.features.couplechat.data.PreparedChatMedia(bytes, kind, mime,
                    width = if (kind == "photo") 1024 else 0, height = if (kind == "photo") 768 else 0,
                    durationMs = if (kind == "voice") 1000 else 0, thumbnail = if (kind == "photo") kotlin.io.encoding.Base64.encode(photo) else null)
                assertTrue("Encrypted media accepted", repositories[0].sendMedia(room, media, label, null).isSuccess)
                val received = awaitState(1, "Partner decrypted attachment") { state -> state.messages.any { it.body == label && it.attachment?.kind == kind } }
                    .messages.first { it.body == label }
                awaitState(0, "Media acknowledged") { state -> state.messages.any { it.id == received.id && it.delivery == MatrixMessageDelivery.SENT } }
                assertArrayEquals("Encrypted media round trip", bytes, repositories[1].attachment(room, received.id).getOrThrow())
                if (kind == "photo") {
                    val preview = repositories[1].attachment(room, received.id, thumbnail = true).getOrThrow()
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(preview, 0, preview.size, bounds)
                    assertTrue("Bubble preview retains enough pixels", bounds.outWidth >= 800)
                }
                assertTrue("Sender can delete $kind", repositories[0].deleteMessage(room, received.id).isSuccess)
                awaitState(1, "Partner sees deleted $kind") { state -> state.messages.any { it.id == received.id && it.deleted && it.attachment == null } }
                assertTrue("Deleted media cannot reopen", repositories[1].attachment(room, received.id).isFailure)
            }
            assertTrue("Sender can delete edited text", repositories[0].deleteMessage(room, original).isSuccess)
            awaitState(1, "Edited text becomes a tombstone") { state -> state.messages.any { it.id == original && it.deleted && it.body.isEmpty() } }
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
