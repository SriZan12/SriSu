package com.srisu.srisu.features.notifications

import android.Manifest
import android.app.NotificationManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.liftric.kvault.KVault
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.utils.Constants
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Only prepares a separate QA installation. The host then ends this process,
 * removes its task, locks the handset, sends from native iOS, and opens the actual
 * System UI notification after the owner unlocks. Never run on a personal package. */
class ClosedLockedPushPreparationTest {
    @get:Rule val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private class MemorySession(private var value: String?) : SessionStorage {
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }
    @Test fun prepareReceiverThenRemoveItsTask(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.filesDir, "locked-push-fixture.json")
        assumeTrue("Requires the isolated locked-phone fixture", file.isFile)
        require(context.packageName == "com.srisu.srisu.qa")
        val fixture = JSONObject(file.readText())
        require(fixture.getString("scope") == "isolated-matrix-push-e2e")
        require(fixture.getString("base_url") == "http://127.0.0.1:8029/")
        val user = fixture.getJSONArray("users").getJSONObject(1)
        require(user.getLong("id") == 820000000000000002L)
        val vault = KVault(context = context, fileName = Constants.Auth.SESSION_FILE)
        assertTrue("Preserve any existing login", vault.string(Constants.Auth.SESSION_KEY) == null)
        val environment = ApiEnvironment(fixture.getString("base_url"), true)
        assertEquals(environment.baseUrl, ApiEnvironment.configured().baseUrl)
        val room = fixture.getString("room_id")
        val session = SessionCoordinator(MemorySession(user.toString()))
        val http = HttpClientFactory.create(session, environment, OkHttp.create())
        val engine = OkHttp.create()
        val lifetime = ApplicationLifetime()
        val repository = TrixnityMatrixChatRepository(MatrixSessionApiService(http, environment), session,
            environment, engine, AndroidMatrixPlatformStore(context), lifetime)
        val api = NotificationApi(http, environment)
        val manager = context.getSystemService(NotificationManager::class.java)
        val native = requireNotNull(NativeNotifications.driver)
        lateinit var activity: ComponentActivity
        activityRule.scenario.onActivity { activity = it }
        var prepared = false
        try {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            withContext(Dispatchers.Main) {
                activity.setContent { androidx.compose.material3.Text("Preparing locked-phone notification test") }
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                native.refresh(); lifetime.setForeground(true)
            }
            val payload = user.getString("access").split('.')[1]
            val sid = JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))).getString("sid")
            val binding = withContext(Dispatchers.Main) {
                withTimeout(120_000) {
                    var current = native.registration(sid)
                    while (current == null) { delay(250); current = native.registration(sid) }
                    current
                }
            }
            assertTrue(binding.capabilities.contains("matrix-chat-push-1"))
            assertTrue(api.register(binding).result is NetworkAPIResult.Success)
            val prefs = (api.preferences().result as NetworkAPIResult.Success).response!!
            assertTrue(api.save(PreferenceWrite(prefs.revision, true, true, true)).result is NetworkAPIResult.Success)
            withContext(Dispatchers.Main) { repository.openRoom(room, MatrixChatRoomOwner()) }
            withTimeout(180_000) { repository.observeRoom(room).first { it.availability is MatrixChatAvailability.Ready } }
            delay(10_000) // Allow initial device/key synchronization to settle before terminating the process.
            val channel = manager.getNotificationChannel("srisu_updates")
            assertTrue("Audible channel enabled", channel.sound != null && channel.importance >= NotificationManager.IMPORTANCE_DEFAULT)
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            assertEquals(android.media.AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
            assertTrue(audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION) > 0)
            assertTrue(vault.set(Constants.Auth.SESSION_KEY, requireNotNull(session.getSession(Constants.Auth.SESSION_KEY))))
            withContext(Dispatchers.Main) {
                repository.close(); lifetime.setForeground(false)
                manager.cancelAll(); activity.finishAndRemoveTask()
            }
            prepared = true
            println("LOCKED_PUSH_PREPARED: native Firebase registered; Matrix store ready; audible settings enabled; task removed. Delivery is not yet tested.")
        } finally {
            withContext(Dispatchers.Main) { repository.close(); lifetime.close() }
            http.close(); engine.close(); file.delete()
            if (!prepared) vault.clear()
        }
    }
}
