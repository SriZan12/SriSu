package com.srisu.srisu.core.session

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionUtilsTest {

    @Test
    fun readsTypedSessionDataFromStorage() {
        val storage = FakeSessionStorage(
            storedValue = Json.encodeToString(
                Session(
                    id = 42,
                    fullName = "Sri Su",
                    phoneNumber = "+9779800000000",
                )
            )
        )
        val sessionUtils = SessionUtils(storage)

        assertEquals(42, sessionUtils.getCurrentUserId())
        assertEquals("Sri Su", sessionUtils.getFullName())
        assertEquals("+9779800000000", sessionUtils.getPhoneNumber())
    }

    @Test
    fun malformedSessionDataDoesNotCrashCallers() {
        val sessionUtils = SessionUtils(FakeSessionStorage(storedValue = "not-json"))

        assertNull(sessionUtils.getSession())
        assertNull(sessionUtils.getCurrentUserId())
    }

    private class FakeSessionStorage(
        private var storedValue: String?,
    ) : SessionStorage {
        override fun saveSession(credentials: String, sessionKey: String) {
            storedValue = credentials
        }

        override fun getSession(sessionKey: String): String? = storedValue

        override fun clearSession(): Boolean {
            storedValue = null
            return true
        }

        override fun clearOnReinstall(key: String) = Unit
    }
}
