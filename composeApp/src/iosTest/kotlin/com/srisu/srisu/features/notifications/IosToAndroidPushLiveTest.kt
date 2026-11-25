@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.notifications

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.toKString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertTrue

/** Opt-in native iOS sender. Use a dedicated simulator and the isolated Windows
 * test stack only. The Android companion owns arrival, sound-policy and tap checks.
 * Private fixture/control files never ship in an app or enter test output. */
class IosToAndroidPushLiveTest {
    private class MemorySession(private var value: String?) : SessionStorage {
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }
    @Test fun nativeIosMessageReachesAndroid() = runBlocking {
        val path = getenv("SRISU_IOS_ANDROID_PUSH_FIXTURE")?.toKString() ?: return@runBlocking
        val fs = FileSystem.SYSTEM
        val folder = requireNotNull(path.toPath().parent)
        val fixture = ApiJson.parseToJsonElement(fs.read(path.toPath()) { readUtf8() }).jsonObject
        require(fixture.getValue("scope").jsonPrimitive.content == "isolated-matrix-push-e2e")
        val base = fixture.getValue("base_url").jsonPrimitive.content
        require(base == "http://127.0.0.1:8029/")
        val sender = fixture.getValue("users").jsonArray[0].jsonObject
        require(sender.getValue("id").jsonPrimitive.long == 820000000000000001L)
        val runId = fixture.getValue("run_id").jsonPrimitive.content
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val body = "Synthetic iOS to Android $runId"
        val room = fixture.getValue("room_id").jsonPrimitive.content
        val session = SessionCoordinator(MemorySession(sender.toString()))
        val environment = ApiEnvironment(base, true)
        val http = HttpClientFactory.create(session, environment, Darwin.create())
        val engine = Darwin.create()
        Dispatchers.setMain(Dispatchers.Default)
        val lifetime = ApplicationLifetime()
        val repository = TrixnityMatrixChatRepository(MatrixSessionApiService(http, environment), session,
            environment, engine, IosMatrixPlatformStore(legacyKeyCleaner = {}), lifetime)
        val model = SecureChatViewModel(repository, sessions = session)
        fun status(value: String) {
            val temp = folder / "ios-status.tmp"
            fs.write(temp) { writeUtf8(value) }
            fs.atomicMove(temp, folder / "ios-status.txt")
        }
        try {
            lifetime.setForeground(true)
            model.openRoom(room)
            withTimeout(180_000) { model.state.first { it?.availability is MatrixChatAvailability.Ready } }
            status("ready")
            withTimeout(15 * 60_000) {
                while (!fs.exists(folder / "ios-send.txt")) delay(250)
            }
            assertTrue(fs.read(folder / "ios-send.txt") { readUtf8() } == runId)
            model.updateMessageInput(body)
            model.sendMessage()
            withTimeout(120_000) {
                model.state.first { it?.messages?.any { m -> m.mine && m.body == body &&
                    !m.unavailable && m.delivery == MatrixMessageDelivery.SENT } == true }
            }
            status("sent")
            // Keep the iOS crypto client available while Android opens/decrypts.
            withTimeout(10 * 60_000) { while (!fs.exists(folder / "ios-stop.txt")) delay(250) }
            status("complete")
            println("IOS_ANDROID_PUSH: native iOS composer ViewModel and protected Matrix store sent an encrypted message; Android companion verifies delivery/tap separately")
        } finally {
            model.closeRoom(); model.viewModelScope.cancel(); repository.close(); lifetime.close()
            http.close(); engine.close(); Dispatchers.resetMain()
        }
    }
}
