package com.srisu.srisu.features.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
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

/** Opt-in real Synapse -> Django outbox -> FCM -> Android -> conversation acceptance.
 * Use only a fresh test installation and the isolated synthetic database. No fixture
 * loader, account override, or notification test endpoint ships in the application. */
class MatrixPushDeviceIntegrationTest {
    @get:Rule val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private class MemorySession(private var value: String?) : SessionStorage {
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }
    private class SenderContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir() = File(super.getNoBackupFilesDir(), "matrix-push-sender").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("matrix-push-sender-$name", mode)
    }

    @Test fun encryptedMessageRingsAndTapOpensItsConversation(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var foregroundActivity: ComponentActivity
        activityRule.scenario.onActivity { foregroundActivity = it }
        val context = instrumentation.targetContext
        val fixtureFile = File(context.filesDir, "matrix-push-device-fixture.json")
        assumeTrue("Requires the isolated Matrix push fixture", fixtureFile.isFile)
        val fixture = JSONObject(fixtureFile.readText())
        require(fixture.getString("scope") == "isolated-matrix-push-e2e")
        require(fixture.getString("base_url") == "http://127.0.0.1:8029/")
        val users = fixture.getJSONArray("users")
        for (i in 0..2) require(users.getJSONObject(i).getLong("id").toString().startsWith("8200000000000000"))
        val vault = KVault(context = context, fileName = Constants.Auth.SESSION_FILE)
        vault.string(Constants.Auth.SESSION_KEY)?.let { previous ->
            val saved = JSONObject(previous)
            require(saved.optLong("id") == users.getJSONObject(1).getLong("id") &&
                saved.optString("phone_number") == users.getJSONObject(1).getString("phone_number")) {
                "Use a fresh test installation, never a personal login"
            }
            vault.clear() // Recover only this synthetic session after an interrupted run.
        }
        val room = fixture.getString("room_id")
        val environment = ApiEnvironment(fixture.getString("base_url"), development = true)
        assertEquals("The actual app must target the isolated backend", environment.baseUrl, ApiEnvironment.configured().baseUrl)
        val sessions = (0..2).map { SessionCoordinator(MemorySession(users.getJSONObject(it).toString())) }
        val clients = sessions.map { HttpClientFactory.create(it, environment, OkHttp.create()) }
        val remotes = clients.map { MatrixSessionApiService(it, environment) }
        val lifetimes = (0..1).map { ApplicationLifetime() }
        val engines = (0..1).map { OkHttp.create() }
        val repositories = (0..1).map {
            TrixnityMatrixChatRepository(remotes[it], sessions[it], environment, engines[it],
                AndroidMatrixPlatformStore(if (it == 0) SenderContext(context) else context), lifetimes[it])
        }
        val owners = (0..1).map { MatrixChatRoomOwner() }
        val api = NotificationApi(clients[1], environment)
        val strangerApi = NotificationApi(clients[2], environment)
        val notifications = NotificationRepository(api, sessions[1], lifetimes[1])
        val manager = context.getSystemService(NotificationManager::class.java)
        val native = requireNotNull(NativeNotifications.driver)
        var opened: android.app.Activity? = null
        fun stage(value: String) { instrumentation.sendStatus(2, Bundle().apply { putString("stream", "Matrix push: $value\n") }) }
        suspend fun waitFor(label: String, condition: () -> Boolean) {
            assertEquals(label, true, withTimeoutOrNull(120_000) { while (!condition()) delay(250); true })
        }
        suspend fun ready(index: Int) {
            assertNotNull("Encrypted participant ready", withTimeoutOrNull(180_000) {
                repositories[index].observeRoom(room).first { it.availability is MatrixChatAvailability.Ready }
            })
        }
        suspend fun send(text: String) {
            assertTrue("Encrypted synthetic message queued", repositories[0].sendText(room, text).isSuccess)
        }
        fun privateAlert(alert: Notification) {
            assertEquals("SriSu", alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("You have an update. Open SriSu to view it.", alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        }
        try {
            withContext(Dispatchers.Main) {
                foregroundActivity.setContent { androidx.compose.material3.Text("Testing Matrix notifications") }
                foregroundActivity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            stage("registering the native Android installation")
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            withContext(Dispatchers.Main) { native.refresh(); lifetimes.forEach { it.setForeground(true) } }
            val payload = users.getJSONObject(1).getString("access").split('.')[1]
            val sid = JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))).getString("sid")
            var binding: InstallationRequest? = null
            withContext(Dispatchers.Main) { waitFor("FCM native registration") { binding = native.registration(sid); binding != null } }
            assertTrue(requireNotNull(binding).capabilities.contains("matrix-chat-push-1"))
            assertTrue("Installation registered", api.register(requireNotNull(binding)).result is NetworkAPIResult.Success)
            notifications.reconcile()
            assertTrue("Server enables Matrix push", notifications.state.value.preferences!!.capabilities.contains("matrix-chat-push-1"))
            assertFalse("Unrelated account denied", remotes[2].create(room) is NetworkAPIResult.Success)
            for (i in 0..1) { withContext(Dispatchers.Main) { repositories[i].openRoom(room, owners[i]) }; ready(i) }
            withContext(Dispatchers.Main) {
                waitFor("Test activity is visibly foreground and unlocked") {
                    val process = android.app.ActivityManager.RunningAppProcessInfo()
                    android.app.ActivityManager.getMyMemoryState(process)
                    foregroundActivity.hasWindowFocus() &&
                        process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
                }
            }
            stage("sending and decrypting in the foreground")
            val foregroundSignal = async(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(120_000) { NativeNotifications.signal.filterNotNull().first { !it.tapped } }
            }
            send("Synthetic Matrix push foreground")
            assertNotNull("Partner decrypts foreground message", withTimeoutOrNull(120_000) {
                repositories[1].observeRoom(room).first { it.messages.any { message -> !message.mine && message.body == "Synthetic Matrix push foreground" && !message.unavailable } }
            })
            stage("partner decrypted the foreground message")
            val received = foregroundSignal.await()
            assertNotNull("Foreground FCM callback; OS alerts=${manager.activeNotifications.size}", received)
            val foreground = requireNotNull(received)
            assertEquals("Foreground has no OS alert", 0, manager.activeNotifications.size)
            assertEquals(NotificationResolution.Ready(ChatNav.CoupleConversation(room, origin = "notification")), notifications.resolve(foreground))
            assertEquals("Unrelated account cannot resolve delivery", 404,
                (strangerApi.resolve(foreground.deliveryId).result as? NetworkAPIResult.Error)?.failure?.status)
            withContext(Dispatchers.Main) { NativeNotifications.consume(foreground); repositories[1].close(); lifetimes[1].setForeground(false) }
            // Keep its protected E2EE store for the real MainActivity to reopen on tap.
            assertTrue(vault.set(Constants.Auth.SESSION_KEY, requireNotNull(sessions[1].getSession(Constants.Auth.SESSION_KEY))))
            delay(31_000) // Reset the real server's per-installation audible budget.
            withContext(Dispatchers.Main) { foregroundActivity.moveTaskToBack(true) }
            delay(1_000)
            stage("checking the audible background notification")
            send("Synthetic Matrix push audible")
            waitFor("Audible background arrival") { manager.activeNotifications.isNotEmpty() }
            val audible = manager.activeNotifications.single().notification
            privateAlert(audible)
            assertEquals("Audible channel", "srisu_updates", audible.channelId)
            val channel = manager.getNotificationChannel(audible.channelId)
            assertNotNull("Channel sound is enabled", channel.sound)
            assertTrue("Channel importance allows sound", channel.importance >= NotificationManager.IMPORTANCE_DEFAULT)
            assertEquals("Phone allows notification interruption", NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            assertEquals("Phone is not silent", android.media.AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
            assertTrue("Notification volume is nonzero", audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION) > 0)
            manager.cancelAll()
            stage("checking burst throttling and opening the right chat")
            send("Synthetic Matrix push tap destination")
            waitFor("Quiet follow-up arrival") { manager.activeNotifications.isNotEmpty() }
            val quiet = manager.activeNotifications.single().notification
            privateAlert(quiet)
            assertEquals("Burst uses quiet channel", "srisu_updates_quiet", quiet.channelId)
            assertNull("Quiet channel has no sound", manager.getNotificationChannel(quiet.channelId).sound)
            notifications.close()
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            val tappedSignal = async(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
                withTimeout(30_000) { NativeNotifications.signal.filterNotNull().first { it.tapped } }
            }
            quiet.contentIntent.send()
            opened = instrumentation.waitForMonitorWithTimeout(monitor, 30_000)
            instrumentation.removeMonitor(monitor)
            assertNotNull("Notification opens the real MainActivity", opened)
            val tap = tappedSignal.await()
            assertEquals(NotificationResolution.Ready(ChatNav.CoupleConversation(room, origin = "notification")), notifications.resolve(tap))
            delay(5_000)
            // Only this guarded synthetic installation may retain a UI diagnostic.
            // Capture inside the existing automation session, never a second UIAutomator.
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() == context.packageName) {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(context.filesDir, "matrix-push-tap.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            stage("checking the real chat view after MainActivity startup")
            waitFor("Real chat screen decrypts the message from the notified room") {
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Synthetic Matrix push tap destination")?.isNotEmpty() == true
            }
            waitFor("Real chat composer is available") {
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Send")?.isNotEmpty() == true
            }
            manager.cancelAll()
            stage("checking that chat preferences suppress further alerts")
            val prefs = (api.preferences().result as NetworkAPIResult.Success).response!!
            assertTrue(api.save(PreferenceWrite(prefs.revision, true, false, true)).result is NetworkAPIResult.Success)
            withContext(Dispatchers.Main) { opened?.moveTaskToBack(true) }
            send("Synthetic Matrix push disabled")
            delay(10_000)
            assertEquals("Disabled chat has no OS alert", 0, manager.activeNotifications.size)
            stage("PASS: encrypted delivery, audible/quiet channels, real chat tap, and preference suppression")
        } finally {
            withContext(Dispatchers.Main) {
                opened?.finish(); notifications.close(); repositories.forEach { it.close() }; lifetimes.forEach { it.close() }
                manager.cancelAll()
            }
            clients.forEach { it.close() }; engines.forEach { it.close() }
            vault.clear(); fixtureFile.delete()
        }
    }
}
