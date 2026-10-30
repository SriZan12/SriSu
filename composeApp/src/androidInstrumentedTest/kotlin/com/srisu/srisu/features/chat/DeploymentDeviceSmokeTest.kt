package com.srisu.srisu.features.chat

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.MainActivity
import com.srisu.srisu.core.config.ApiEnvironment
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only deployment probe. Never signs in or creates application data. */
class DeploymentDeviceSmokeTest {
    @Test fun configuredBackendIsReachableAndAppLaunches() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "deployment-device-fixture.json")
        assumeTrue("Requires an explicit deployment smoke fixture", file.isFile)
        val fixture = JSONObject(file.readText())
        require(fixture.getString("scope") == "read-only-deployment-smoke")
        val configured = ApiEnvironment.configured()
        assertEquals("APK targets the intended deployed backend", fixture.getString("base_url"), configured.baseUrl)
        fun status(url: String, expected: Int) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            try { assertEquals("Deployment HTTP response", expected, connection.responseCode) }
            finally { connection.disconnect() }
        }
        try {
            status(configured.baseUrl + "api/auth/interests/", 200)
            status(configured.baseUrl + "api/notifications/v1/preferences/", 401)
            status(configured.baseUrl + "_matrix/app/v1/transactions/untrusted", 403)
            status(fixture.getString("matrix_url") + "/_matrix/client/versions", 200)
            status(fixture.getString("matrix_url") + "/_synapse/client/srisu/policy/v1", 200)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertFalse("MainActivity remains available", it.isFinishing) }
            }
        } finally { file.delete() }
    }
}
