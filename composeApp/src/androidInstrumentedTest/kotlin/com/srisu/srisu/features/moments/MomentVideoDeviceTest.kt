package com.srisu.srisu.features.moments

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/** Emulator-only, synthetic loopback fixture. Never reads or replaces account storage. */
class MomentVideoDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val files get() = InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    @Test fun localPreviewRetryRecreatesTheFailedPlayer() {
        val source=File(files,"moment-video-source.mp4")
        assumeTrue(source.exists())
        val recovered=File(files,"moment-video-retry.mp4").apply {delete()}
        try {
            val photo=LocalMomentPhoto("retry-video",video=LocalMomentVideo(recovered.absolutePath,source.length(),1200,360,640))
            compose.setContent {SriSuTheme {
                MomentComposerContent(MomentComposerState(loading=false,photos=listOf(photo)),
                    {null},{},{},{},{_,_->},{},{},{},{},{})
            }}
            visibleControl("Play video").performClick()
            compose.waitUntil(15_000) {compose.onAllNodesWithText("Video preview unavailable").fetchSemanticsNodes().isNotEmpty()}
            source.copyTo(recovered,overwrite=true)
            compose.onNodeWithText("Retry").performClick()
            visibleControl("Play video").performClick()
            compose.waitUntil(15_000) {compose.onAllNodes(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Video ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        } finally {recovered.delete()}
    }

    @Test fun systemVideoPickerPreparesAnOwnedBoundedFile() {
        val source=File(files,"moment-video-source.mp4")
        assumeTrue(source.exists() && android.os.Build.VERSION.SDK_INT>=29)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // MainApplication initializes AppContext, with no global Koin registry.
        // Starting global Koin here hid a production video-cache lookup failure.
        assertNull(org.koin.core.context.GlobalContext.getOrNull())
        val resolver=context.contentResolver
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME,"SriSu synthetic video.mp4")
            put(android.provider.MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH,"Movies/SriSuQA")
            put(android.provider.MediaStore.Video.Media.IS_PENDING,1)
        }
        val uri=resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)!!
        val selected=AtomicReference<MomentVideoSelection?>()
        try {
            resolver.openOutputStream(uri)!!.use {out->source.inputStream().use {it.copyTo(out)}}
            resolver.update(uri,android.content.ContentValues().apply {put(android.provider.MediaStore.Video.Media.IS_PENDING,0)},null,null)
            compose.setContent {
                val picker=rememberMomentVideoPicker()
                androidx.compose.material3.Button(onClick={picker.pick {selected.set(it)}}) {androidx.compose.material3.Text("Choose video")}
            }
            compose.onNodeWithText("Choose video").performClick()
            val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val thumbnail=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res(java.util.regex.Pattern.compile(".*:id/icon_thumbnail"))),10_000)
            assertNotNull("Native video picker must show the synthetic clip",thumbnail)
            thumbnail.click()
            compose.waitUntil(150_000) {selected.get()!=null}
            val result=selected.get()!!
            assertNull(result.error)
            val video=result.video!!
            assertTrue(video.size in 1..source.length())
            assertTrue(video.durationMs in 7500..8500)
            assertNotNull(result.poster)
            kotlinx.coroutines.runBlocking {assertEquals(32,readMomentVideoChunk(video,0,32).size)}
        } finally {
            selected.get()?.video?.let(::removeMomentVideoFile)
            resolver.delete(uri,null,null)
        }
    }

    @Test fun videoComposerKeepsControlsAndKeyboardReachableAtLargeText() {
        val file = File(files, "moment-video-source.mp4")
        assumeTrue("Requires synthetic local clip", file.exists())
        val photo = LocalMomentPhoto("video", video=LocalMomentVideo(file.absolutePath,file.length(),8000,360,640))
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                SriSuTheme(darkTheme=true) {
                    MomentComposerContent(MomentComposerState(record=MomentDraftRecord("synthetic-video",3),loading=false,photos=listOf(photo)),
                        {null},{},{},{},{_,_->},{},{},{},{},{})
                }
            }
        }
        compose.onNodeWithTag("moment-share").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("moment-photo-crop").assertDoesNotExist()
        visibleControl("Play video").assertIsDisplayed()
        visibleControl("Add video").assertIsDisplayed()
        visibleControl("Play video").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Video ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
        }
        capture("composer")
        compose.onNodeWithContentDescription("Add or edit caption").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("An eight-second memory")
        compose.onNodeWithTag("moment-caption-done").assertIsDisplayed()
        capture("keyboard")
        compose.onNodeWithTag("moment-caption-done").performClick()
        compose.onNodeWithTag("moment-share").assertIsDisplayed()
    }

    private fun capture(name:String) {
        compose.waitForIdle()
        val frames=java.util.concurrent.CountDownLatch(1)
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation {frames.countDown()}
            }
        }
        assertTrue(frames.await(3,java.util.concurrent.TimeUnit.SECONDS))
        var bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if(name=="composer" || name=="playback") {
            val bounds=if(name=="composer") compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Video ready"),useUnmergedTree=true).fetchSemanticsNode().boundsInWindow
                else androidx.compose.ui.geometry.Rect(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat())
            fun countPixels():Int {
                var colorful=0
                for(y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height) step 8)
                for(x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width) step 8) {
                    val color=bitmap.getPixel(x,y)
                    val channels=listOf(android.graphics.Color.red(color),android.graphics.Color.green(color),android.graphics.Color.blue(color)) // theme-audit: image pixels — synthetic video verification
                    if(channels.max()-channels.min()>80) colorful++
                }
                return colorful
            }
            // Decoder callbacks precede SurfaceFlinger presentation. Poll actual
            // pixels for a bounded interval instead of treating the callback as proof.
            val deadline=android.os.SystemClock.elapsedRealtime()+4_000
            while(countPixels()<=50 && android.os.SystemClock.elapsedRealtime()<deadline) {
                bitmap.recycle();Thread.sleep(100)
                bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            }
            assertTrue("Synthetic video pixels must be visible, not only a decoder callback",countPixels()>50)
        }
        File(files,"moment-video-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }

    private fun visibleControl(description:String):SemanticsNodeInteraction =
        compose.onNodeWithContentDescription(description).also {if(!it.isDisplayed()) it.performScrollTo()}

    @Test fun nativeHlsRendersPausesResumesAndEnds() {
        val file = File(files, "moment-video-fixture.json")
        assumeTrue("Requires disposable fixture", file.exists())
        val fixture = JSONObject(file.readText())
        val origin = fixture.getString("base_url")
        require(URL(origin).host == "127.0.0.1") // adb reverse, never a deployed backend.
        val connection = URL(origin+"api/social/moment-videos/"+fixture.getString("video_id")+"/playback/").openConnection() as HttpURLConnection
        val access = try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer "+fixture.getString("partner_access"))
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray()) }
            assertEquals(200, connection.responseCode)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("data")
        } finally { connection.disconnect() }
        val source = MomentPlayback(access.getString("url"), access.getString("cookie_name"),
            access.getString("cookie_value"), access.getString("cookie_path"), access.getInt("expires_in"))
        val playing = mutableStateOf(true)
        val event = AtomicReference(MomentPlaybackEvent("buffering"))
        val started = android.os.SystemClock.elapsedRealtime()
        compose.setContent { MomentVideoPlayer(source, playing.value, true, Modifier.fillMaxSize()) { event.set(it) } }
        compose.waitUntil(20_000) { event.get().firstFrame || event.get().state == "failed" }
        assertNotEquals("failed", event.get().state)
        assertTrue(event.get().firstFrame)
        val firstFrameMs = android.os.SystemClock.elapsedRealtime()-started
        capture("playback")
        compose.waitUntil(10_000) { event.get().positionMs > 1000 }
        compose.runOnIdle { playing.value = false }
        Thread.sleep(500)
        val pausedAt = event.get().positionMs
        Thread.sleep(1200)
        assertTrue("Paused video must not progress", event.get().positionMs-pausedAt < 100)
        compose.runOnIdle { playing.value = true }
        compose.waitUntil(15_000) { event.get().state in setOf("ended", "failed") }
        assertEquals("ended", event.get().state)
        assertTrue(event.get().durationMs in 7500..8500)
        File(files, "moment-video-native-result.json").writeText(JSONObject().put("first_frame_ms",firstFrameMs)
            .put("duration_ms",event.get().durationMs).put("pause_resume_end",true).toString())
    }
}
