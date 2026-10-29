package com.srisu.srisu.features.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.liftric.kvault.KVault
import com.srisu.srisu.MainActivity
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.HttpClientFactory
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.utils.Constants
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Opt-in live FCM test. The runner must supply short-lived synthetic sessions from
 * a separate database through an app-private file. No fixture loader ships in the app. */
class NotificationDeviceIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class MemorySession(var value: String?) : SessionStorage {
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }

    @Test fun registeredFidDeliversPrivatelyAndTapReauthorizes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtureFile = File(context.filesDir, "notification-device-fixture.json")
        assumeTrue("Requires the opt-in isolated notification device fixture", fixtureFile.isFile)
        val fixture = JSONObject(fixtureFile.readText())
        require(fixture.getString("scope") == "isolated-notifications-e2e")
        val base = fixture.getString("base_url")
        require(base == "http://127.0.0.1:8029/")
        val users = fixture.getJSONArray("users")
        for (i in 0..2) require(users.getJSONObject(i).getLong("id").toString().startsWith("8200000000000000"))
        val sender = users.getJSONObject(0)
        val recipient = users.getJSONObject(1)
        val stranger = users.getJSONObject(2)
        val sessions = SessionCoordinator(MemorySession(recipient.toString()))
        val lifetime = ApplicationLifetime()
        val environment = ApiEnvironment(base, development = true)
        val http = HttpClientFactory.create(sessions, environment, OkHttp.create())
        val api = NotificationApi(http, environment)
        val repository = NotificationRepository(api, sessions, lifetime)
        val manager = context.getSystemService(NotificationManager::class.java)
        val native = requireNotNull(NativeNotifications.driver)
        var opened: android.app.Activity? = null

        // Never print response bodies, credentials, registration targets or private payloads.
        fun request(user: JSONObject, method: String, path: String, body: JSONObject? = null, expected: Int = 200): JSONObject {
            val connection = URL(base + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer " + user.getString("access"))
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            try {
                assertEquals("Unexpected HTTP status for $method $path", expected, connection.responseCode)
                if (expected >= 400 || expected == 204) return JSONObject()
                return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally { connection.disconnect() }
        }
        fun invitationBody(status: String = "PENDING") = JSONObject()
            .put("sender_number", sender.getString("phone_number"))
            .put("receiver_number", recipient.getString("phone_number"))
            .put("connection_status", status)
        fun invite() = request(sender, "POST", "api/social/connect-couple/", invitationBody(), 201).getJSONObject("data").getLong("id")
        fun reject(id: Long) { request(recipient, "PUT", "api/social/connect-couple/$id/", invitationBody("REJECTED")) }
        suspend fun waitFor(label: String, condition: () -> Boolean) {
            val success = withTimeoutOrNull(120_000) { while (!condition()) delay(250); true }
            assertEquals(label, true, success)
        }
        fun binding(user: JSONObject): String {
            val payload = user.getString("access").split('.')[1]
            return JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))).getString("sid")
        }
        try {
            assertTrue("Firebase app configured", NativeNotifications.state.value.configured)
            withContext(Dispatchers.Main) { native.refresh() }
            if (NativeNotifications.state.value.permission != "granted") {
                withContext(Dispatchers.Main) { assertNull("No target before permission", native.registration(binding(recipient))) }
            }
            if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            withContext(Dispatchers.Main) { native.refresh(); lifetime.setForeground(true) }
            var registration: InstallationRequest? = null
            withContext(Dispatchers.Main) {
                waitFor("Registered FCM FID callback") { registration = native.registration(binding(recipient)); registration != null }
            }
            val original = requireNotNull(registration)
            assertEquals("fid", original.targetKind)
            assertTrue("Native registration accepted", api.register(original).result is NetworkAPIResult.Success)
            repository.reconcile()
            assertNotNull("Preferences retrieved", repository.state.value.preferences)
            assertFalse("Registration succeeded", repository.state.value.registrationFailed)
            assertFalse("Diagnostics default off", NativeNotifications.state.value.diagnostics)

            val firstInvite = invite()
            val foreground = withTimeout(120_000) { NativeNotifications.signal.filterNotNull().first { !it.tapped } }
            assertEquals(0, manager.activeNotifications.size)
            assertEquals(NotificationResolution.Ready(ChatNav.PartnerRequests), repository.resolve(foreground))
            request(stranger, "GET", "api/notifications/v1/deliveries/${foreground.deliveryId}/", expected = 404)
            withContext(Dispatchers.Main) { NativeNotifications.consume(foreground) }
            reject(firstInvite)
            assertEquals(NotificationResolution.Unavailable, repository.resolve(foreground))

            // Test server preference suppression while the actual app is backgrounded.
            val pref = repository.state.value.preferences!!
            assertTrue(api.save(PreferenceWrite(pref.revision, false, true, true)).result is NetworkAPIResult.Success)
            withContext(Dispatchers.Main) { lifetime.setForeground(false); compose.activity.moveTaskToBack(true) }
            val suppressed = invite()
            delay(8_000)
            assertEquals("Disabled push creates no OS alert", 0, manager.activeNotifications.size)
            assertNull("Disabled push creates no foreground signal", NativeNotifications.signal.value)
            reject(suppressed)
            val disabled = request(recipient, "GET", "api/notifications/v1/preferences/").getJSONObject("data")
            assertTrue(api.save(PreferenceWrite(disabled.getLong("revision"), true, true, true)).result is NetworkAPIResult.Success)

            val backgroundInvite = invite()
            waitFor("Background OS notification") { manager.activeNotifications.isNotEmpty() }
            val alert = manager.activeNotifications.single().notification
            assertEquals("SriSu", alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("You have an update. Open SriSu to view it.", alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertTrue("Private notification channel", alert.channelId in setOf("srisu_updates", "srisu_updates_quiet"))
            val vault = KVault(context = context, fileName = Constants.Auth.SESSION_FILE)
            assertTrue(vault.set(Constants.Auth.SESSION_KEY, recipient.toString()))
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            val tapped = async(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
                withTimeout(30_000) { NativeNotifications.signal.filterNotNull().first { it.tapped } }
            }
            alert.contentIntent.send()
            opened = instrumentation.waitForMonitorWithTimeout(monitor, 30_000)
            instrumentation.removeMonitor(monitor)
            assertNotNull("Notification PendingIntent opens MainActivity", opened)
            val tap = tapped.await()
            assertEquals(NotificationResolution.Ready(ChatNav.PartnerRequests), repository.resolve(tap))
            withContext(Dispatchers.Main) { opened?.finish(); manager.cancelAll(); NativeNotifications.consume(tap) }
            delay(1_000)
            reject(backgroundInvite)
            assertEquals(NotificationResolution.Unavailable, repository.resolve(tap))
            vault.clear()

            // Transfer the same installation to another login and fence stale registration/taps.
            withContext(Dispatchers.Main) { sessions.saveSession(sender.toString(), Constants.Auth.SESSION_KEY); lifetime.setForeground(true) }
            withContext(Dispatchers.Main) {
                waitFor("Account switch registration") { registration = native.registration(binding(sender)); registration != null && registration!!.version > original.version }
            }
            assertTrue(api.register(requireNotNull(registration)).result is NetworkAPIResult.Success)
            request(recipient, "GET", "api/notifications/v1/deliveries/${tap.deliveryId}/", expected = 404)
            withContext(Dispatchers.Main) { native.setDiagnostics(true) }
            assertTrue(NativeNotifications.state.value.diagnostics)
            request(sender, "POST", "api/auth/logout/", JSONObject().put("refresh", sender.getString("refresh")), 204)
            withContext(Dispatchers.Main) { sessions.clearSession() }
            waitFor("Logout clears diagnostics consent") { !NativeNotifications.state.value.diagnostics }
            assertEquals("Logout clears notifications", 0, manager.activeNotifications.size)
            // Controlled, sanitized nonfatal probe; the host verifies SDK upload/cloud receipt separately.
            withContext(Dispatchers.Main) { native.setDiagnostics(true); native.report(DiagnosticCode.TEST_NONFATAL) }
        } finally {
            withContext(Dispatchers.Main) { opened?.finish(); repository.close(); lifetime.close() }
            http.close()
            fixtureFile.delete()
        }
    }
}
