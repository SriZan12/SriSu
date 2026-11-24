package com.srisu.srisu.features.moments

import android.Manifest
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Synthetic emulator camera only: no account, server, gallery insertion or public publishing. */
class MomentVideoCaptureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val result = AtomicReference<MomentVideoSelection?>()
    private val deliveries = AtomicInteger()
    @Before fun onlySyntheticEmulator() {
        assumeTrue(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
    }
    private fun openCamera() {
        // This class is only run explicitly against the disposable QA emulator.
        for (permission in listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, permission)
        }
        showDriver()
        compose.onNodeWithText("Open video camera").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("moment-camera-record").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("moment-camera-record") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun showDriver() {
        compose.setContent {
            SriSuTheme {
                val picker = rememberMomentVideoPicker()
                Button(onClick = { picker.record { result.set(it); deliveries.incrementAndGet() } }) { Text("Open video camera") }
            }
        }
    }
    private fun assertUsable() {
        compose.waitUntil(30_000) { result.get()!=null }
        val selected=result.get()!!
        assertNull(selected.error)
        val video=selected.video!!
        try {
            assertTrue(video.size in 1..MOMENT_VIDEO_MAX_BYTES)
            assertTrue(video.durationMs in 100..MOMENT_VIDEO_MAX_DURATION)
            assertTrue(maxOf(video.width,video.height)<=1280)
            assertNotNull(selected.poster)
            assertEquals(1,deliveries.get())
            val metadata=MediaMetadataRetriever()
            try {
                metadata.setDataSource(video.path)
                assertEquals("yes",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            } finally {metadata.release()}
            assertTrue(File(instrumentation.targetContext.cacheDir,"srisu-moment-capture").listFiles().orEmpty().isEmpty())
            if(InstrumentationRegistry.getArguments().getString("captureFixture")=="true") {
                File(video.path).copyTo(File(instrumentation.targetContext.filesDir,"moment-camera-source-qa.mp4"),overwrite=true)
            }
        } finally {removeMomentVideoFile(video)}
    }
    @Test fun recordWithSoundReturnsAnOwnedPreviewAndCleansCapture() {
        openCamera()
        compose.onNodeWithContentDescription("Switch camera").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("moment-camera-record") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-camera-record").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("0:02 / 1:00").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-camera-record").performClick()
        assertUsable()
    }
    @Test fun cancelRecordingDiscardsFileAndAllowsAnotherAttempt() {
        openCamera()
        compose.onNodeWithTag("moment-camera-record").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("0:01 / 1:00").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Cancel recording").performClick()
        compose.waitUntil(10_000) { result.get()!=null }
        assertNull(result.get()!!.video);assertNull(result.get()!!.error)
        compose.waitUntil(10_000) { File(instrumentation.targetContext.cacheDir,"srisu-moment-capture").listFiles().orEmpty().isEmpty() }
        result.set(null)
        compose.onNodeWithText("Open video camera").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("moment-camera-record") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Cancel recording").performClick()
        compose.waitUntil {result.get()!=null}
        assertEquals(2,deliveries.get())
    }
    @Test fun durationLimitFinalizesWithoutAStopTap() {
        openCamera()
        compose.onNodeWithTag("moment-camera-record").performClick()
        compose.waitUntil(80_000) { result.get()!=null }
        assertUsable()
        assertTrue(result.get()!!.video!!.durationMs in 59_000..60_000)
    }
    @Test fun leavingAppStopsRecordingAndDiscardsThePartialClip() {
        openCamera()
        compose.onNodeWithTag("moment-camera-record").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("0:01 / 1:00").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) {result.get()!=null}
        assertNull(result.get()!!.video);assertNull(result.get()!!.error)
        compose.waitUntil(10_000) {File(instrumentation.targetContext.cacheDir,"srisu-moment-capture").listFiles().orEmpty().isEmpty()}
        assertEquals(1,deliveries.get())
    }
    @Test fun deniedPermissionReturnsErrorAndReleasesThePicker() {
        // Opt-in: revoke camera/mic before starting this instrumentation process.
        assumeTrue(InstrumentationRegistry.getArguments().getString("denyCapturePermission")=="true")
        showDriver()
        val device=androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        repeat(2) {
            result.set(null)
            compose.onNodeWithText("Open video camera").performClick()
            // record() schedules the permission request from a Compose effect.
            compose.waitForIdle()
            val deadline=android.os.SystemClock.elapsedRealtime()+20_000
            while(result.get()==null && android.os.SystemClock.elapsedRealtime()<deadline) {
                val deny=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res(
                    java.util.regex.Pattern.compile(".*:id/permission_deny.*_button"))),1_000)
                if(deny!=null) {runCatching {deny.click()};device.waitForIdle();compose.waitForIdle();android.os.SystemClock.sleep(300)}
            }
            compose.waitUntil(10_000) {result.get()!=null}
            assertNull(result.get()!!.video)
            assertTrue(result.get()!!.error!!.contains("camera and microphone"))
            compose.onNodeWithTag("moment-camera-record").assertDoesNotExist()
        }
        assertEquals(2,deliveries.get())
    }
    @Test fun emptyComposerCameraMenuFitsSmallScreenAndLargeText() {
        val source=AtomicReference<String?>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.4f,1.5f)) {
                SriSuTheme {
                    MomentComposerContent(MomentComposerState(loading=false),{null},{},{},{},{_,_->},{},{},{},{},source::set)
                }
            }
        }
        compose.onNodeWithContentDescription("Open camera").performScrollTo().performClick()
        compose.onNodeWithText("Take photo").assertIsDisplayed()
        compose.onNodeWithTag("moment-record-video").assertIsDisplayed().performClick()
        assertEquals("record-video",source.get())
    }
}
