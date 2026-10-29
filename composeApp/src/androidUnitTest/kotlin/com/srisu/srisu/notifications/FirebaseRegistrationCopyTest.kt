package com.srisu.srisu.notifications

import android.content.ClipboardManager
import android.content.ClipData
import android.content.pm.ApplicationInfo
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseRegistrationCopyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)
    private val ready = FirebaseIdAccess(debugBuild=true, configured=true, signedIn=true, permissionGranted=true, hasId=true)

    @BeforeTest fun before() {
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        clipboard.clearPrimaryClip()
    }

    @AfterTest fun after() { clipboard.clearPrimaryClip() }

    @Test fun explicitCopyUsesCurrentRegisteredIdAndHidesClipboardPreview() {
        assertTrue(copyFirebaseRegistrationId(context, ready, "synthetic-first-fid"))
        assertTrue(copyFirebaseRegistrationId(context, ready, "synthetic-rotated-fid"))
        val clip = assertNotNull(clipboard.primaryClip)
        assertEquals("synthetic-rotated-fid", clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test fun logoutPermissionRevocationAndUnregisterBlockEvenACachedId() {
        clipboard.setPrimaryClip(ClipData.newPlainText("Existing", "unrelated clipboard value"))
        for (access in listOf(ready.copy(signedIn=false), ready.copy(permissionGranted=false),
            ready.copy(unregistering=true), ready.copy(configured=false), ready.copy(hasId=false))) {
            assertFalse(copyFirebaseRegistrationId(context, access, "synthetic-stale-fid"))
            assertEquals("unrelated clipboard value", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test fun releaseBuildCannotCopyEvenWithAReadySnapshot() {
        context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        assertFalse(copyFirebaseRegistrationId(context, ready, "synthetic-fid"))
        assertFalse(clipboard.hasPrimaryClip())
        assertEquals(FirebaseIdState.HIDDEN, ready.copy(debugBuild=false).state)
    }

    @Test fun registrationProgressFailureAndMissingIdentifierStayUnavailable() {
        assertEquals(FirebaseIdState.REGISTERING, ready.copy(hasId=false, registering=true).state)
        assertEquals(FirebaseIdState.FAILED, ready.copy(hasId=false, registrationFailed=true).state)
        assertEquals(FirebaseIdState.WAITING, ready.copy(hasId=false).state)
        assertFalse(copyFirebaseRegistrationId(context, ready, null))
        assertFalse(copyFirebaseRegistrationId(context, ready, " "))
    }
}
