package com.srisu.srisu.features.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.liftric.kvault.KVault
import com.srisu.srisu.MainActivity
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.utils.Constants
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Native iOS is the only sender. Requires an empty, dedicated test installation. */
class IosToAndroidPushDeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private class MemorySession(private var value: String?) : SessionStorage {
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }
    // Compose exposes virtual accessibility nodes. Traverse those nodes directly;
    // findAccessibilityNodeInfosByText is not a portable virtual-node search.
    private fun visibleText(node: android.view.accessibility.AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.isVisibleToUser && (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { if (visibleText(child, text)) return true } finally { child.recycle() }
        }
        return false
    }

    private fun visibleEditor(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isVisibleToUser && node.isEditable) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { if (visibleEditor(child)) return true } finally { child.recycle() }
        }
        return false
    }

    @Test fun iosMessageRingsAndOpensItsConversation(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtureFile = File(context.filesDir, "ios-android-push-fixture.json")
        assumeTrue("Requires the isolated iOS/Android push fixture", fixtureFile.isFile)
        require(context.packageName == "com.srisu.srisu.qa") { "Use the separate QA package" }
        val fixture = JSONObject(fixtureFile.readText())
        require(fixture.getString("scope") == "isolated-matrix-push-e2e")
        require(fixture.getString("base_url") == "http://127.0.0.1:8029/")
        val user = fixture.getJSONArray("users").getJSONObject(1)
        require(user.getLong("id") == 820000000000000002L)
        val runId = fixture.getString("run_id")
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val body = "Synthetic iOS to Android $runId"
        val vault = KVault(context = context, fileName = Constants.Auth.SESSION_FILE)
        assertTrue("Never replace a personal session", vault.string(Constants.Auth.SESSION_KEY) == null)
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
        val notifications = NotificationRepository(api, session, lifetime)
        val manager = context.getSystemService(NotificationManager::class.java)
        val native = requireNotNull(NativeNotifications.driver)
        lateinit var activity: ComponentActivity
        activityRule.scenario.onActivity { activity = it }
        var opened: android.app.Activity? = null
        fun stage(value: String) {
            File(context.filesDir, "ios-android-push-stage.txt").writeText(value)
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "iOS to Android: $value\n") })
        }
        suspend fun waitFor(label: String, timeout: Long = 120_000, condition: () -> Boolean) {
            assertEquals(label, true, withTimeoutOrNull(timeout) { while (!condition()) delay(250); true })
        }
        try {
            withContext(Dispatchers.Main) {
                activity.setContent { androidx.compose.material3.Text("Testing iOS to Android notifications") }
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                native.refresh(); lifetime.setForeground(true)
            }
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            val payload = user.getString("access").split('.')[1]
            val sid = JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))).getString("sid")
            var binding: InstallationRequest? = null
            withContext(Dispatchers.Main) { waitFor("Native Firebase registration") { binding = native.registration(sid); binding != null } }
            assertTrue(api.register(requireNotNull(binding)).result is NetworkAPIResult.Success)
            val prefs = (api.preferences().result as NetworkAPIResult.Success).response!!
            assertTrue(api.save(PreferenceWrite(prefs.revision, true, true, true)).result is NetworkAPIResult.Success)
            notifications.reconcile()
            withContext(Dispatchers.Main) { repository.openRoom(room, MatrixChatRoomOwner()) }
            withTimeout(180_000) { repository.observeRoom(room).first { it.availability is MatrixChatAvailability.Ready } }
            withContext(Dispatchers.Main) {
                waitFor("Phone must be unlocked and test activity visible") {
                    activity.hasWindowFocus() && !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
                }
                repository.close(); lifetime.setForeground(false)
            }
            assertTrue(vault.set(Constants.Auth.SESSION_KEY, requireNotNull(session.getSession(Constants.Auth.SESSION_KEY))))
            manager.cancelAll()
            withContext(Dispatchers.Main) { activity.moveTaskToBack(true) }
            delay(1_000)
            stage("background-ready")
            waitFor("iOS message must produce an Android OS notification", 15 * 60_000) { manager.activeNotifications.isNotEmpty() }
            val alert = manager.activeNotifications.single().notification
            assertEquals("SriSu", alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("You have an update. Open SriSu to view it.", alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals("srisu_updates", alert.channelId)
            val channel = manager.getNotificationChannel(alert.channelId)
            assertNotNull(channel.sound)
            assertTrue(channel.importance >= NotificationManager.IMPORTANCE_DEFAULT)
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            assertEquals(android.media.AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
            assertTrue(audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION) > 0)
            stage("audible-arrived")
            delay(8_000) // Allows the human observer to hear the phone before navigation.
            notifications.close()
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            val signal = async(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
                withTimeout(30_000) { NativeNotifications.signal.filterNotNull().first { it.tapped } }
            }
            alert.contentIntent.send()
            opened = instrumentation.waitForMonitorWithTimeout(monitor, 30_000)
            instrumentation.removeMonitor(monitor)
            assertNotNull("Notification opens the real MainActivity", opened)
            assertEquals(NotificationResolution.Ready(ChatNav.CoupleConversation(room, origin = "notification")), notifications.resolve(signal.await()))
            waitFor("Correct room displays the exact decrypted iOS message") {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                root?.packageName?.toString() == context.packageName && visibleText(root, body)
            }
            waitFor("Real chat composer is available") {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                root?.packageName?.toString() == context.packageName && visibleEditor(root)
            }
            withContext(Dispatchers.Main) {
                assertTrue(requireNotNull(opened).window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            stage("PASS: iOS message arrived on audible channel and real notification tap opened its decrypted chat")
        } finally {
            withContext(Dispatchers.Main) { opened?.finish(); notifications.close(); repository.close(); lifetime.close(); manager.cancelAll() }
            http.close(); engine.close(); vault.clear(); fixtureFile.delete()
        }
    }
}
