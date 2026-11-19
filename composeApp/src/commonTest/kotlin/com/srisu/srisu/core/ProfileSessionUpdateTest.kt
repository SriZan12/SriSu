package com.srisu.srisu.core

import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.auth.data.remote.response.ProfileResponse
import kotlin.test.*

class ProfileSessionUpdateTest {
    @Test fun savingProfileRetainsRotatedCredentialsAndDoesNotResetNavigationScope() {
        val sessions = session()
        val stamp = sessions.stamp()
        sessions.rotateIfCurrent("new-synthetic-access", "new-synthetic-refresh", stamp)
        val refreshRequest = sessions.refreshRequestId(stamp)
        assertTrue(sessions.updateProfileIfCurrent(ProfileResponse.User(id = 1, fullName = "Updated synthetic name"), stamp))
        assertEquals("new-synthetic-access", sessions.currentSession()?.access)
        assertEquals("new-synthetic-refresh", sessions.currentSession()?.refresh)
        assertEquals(refreshRequest, sessions.currentSession()?.refreshRequestId)
        assertEquals("Updated synthetic name", sessions.currentSession()?.fullName)
        assertEquals(stamp, sessions.stamp())
    }
    @Test fun aLateProfileResponseCannotRestoreSignedOutOrDifferentAccounts() {
        val sessions = session()
        val stamp = sessions.stamp()
        assertFalse(sessions.updateProfileIfCurrent(ProfileResponse.User(id = 2), stamp))
        sessions.clearSession()
        assertFalse(sessions.updateProfileIfCurrent(ProfileResponse.User(id = 1), stamp))
        assertNull(sessions.currentSession())
    }
}
