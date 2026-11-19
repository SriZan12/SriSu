package com.srisu.srisu.features.moments

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.moments.data.MomentDraftRecord
import com.srisu.srisu.features.moments.data.MomentCrop
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production UI with synthetic state: real keyboard, system bars and touch; no account or API. */
class MomentComposerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var caption = ""
    private var shares = 0
    private var visibility = "private"
    private var crops = emptyMap<String, MomentCrop>()
    private fun show(scale: Float = 1f) {
        // A portrait image exercises letterboxing without using a person's photo.
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(160, 190, 183)) // theme-audit: image pixels — synthetic fixture
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().also { it.color = android.graphics.Color.rgb(230, 170, 110) } // theme-audit: image pixels — synthetic fixture
        canvas.drawCircle(60f, 25f, 18f, paint)
        paint.color = android.graphics.Color.rgb(55, 85, 115) // theme-audit: image pixels — synthetic fixture
        canvas.drawRect(35f, 50f, 85f, 155f, paint)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        compose.activityRule.scenario.onActivity { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
        compose.setContent {
            var state by remember { mutableStateOf(MomentComposerState(
                record = MomentDraftRecord("synthetic-composer", 3), loading = false,
                photos = listOf(LocalMomentPhoto("one", bytes), LocalMomentPhoto("two", bytes)),
            )) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                SriSuTheme(darkTheme = false) {
                    MomentComposerContent(state, { bytes },
                        onCaption = { caption = it; state = state.copy(record = state.record?.copy(caption = it)) },
                        onAudience = { visibility = it; state = state.copy(record = state.record?.copy(visibility = it)) },
                        onCrop = { id, crop ->
                            crops = if(crop==null) crops-id else crops+(id to crop)
                            state = state.copy(record = state.record?.copy(crops = crops))
                        },
                        onRemove = { id -> state = state.copy(photos = state.photos.filterNot { it.id == id }) },
                        onReorder = { _, _ -> }, onShare = { shares++ }, onRetry = {}, onDiscard = {}, onBack = {}, onPick = {})
                }
            }
        }
    }
    private fun keyboardShown() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use { it.readText().contains("mInputShown=true") }
    private fun capture(name: String) {
        compose.waitForIdle()
        // UIAutomator can capture the previous RenderThread buffer immediately after a
        // gesture; wait for two presented frame callbacks, without changing app behavior.
        val frames = java.util.concurrent.CountDownLatch(1)
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { frames.countDown() }
            }
        }
        assertTrue(frames.await(3, java.util.concurrent.TimeUnit.SECONDS))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "moment-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun primaryActionIsAboveSystemUi() {
        val tag = if(compose.onAllNodesWithTag("moment-share").fetchSemanticsNodes().isNotEmpty()) "moment-share" else "moment-caption-done"
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
        val button = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val bottom = decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime() or android.view.WindowInsets.Type.systemBars()).bottom
            assertTrue("Primary action is entirely above keyboard/navigation", button.bottom <= decor.height - bottom + 1)
        }
    }
    @Test fun cropGesturesApplyResetAndCancelOnNativeScreen() {
        show()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("moment-photo-fill") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-photo-full").assertIsSelected()
        capture("crop-full")
        compose.onNodeWithTag("moment-photo-fill").performScrollTo().performTouchInput { click() }
        val filled = crops.getValue("one")
        compose.onNodeWithTag("moment-photo-crop").performTouchInput { click() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("moment-crop-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        // Move the photo down to expose its top; no zoom is needed for the Fill crop.
        compose.onNodeWithTag("moment-crop-canvas").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f,500f)) }
        capture("crop-editor")
        compose.onNodeWithTag("moment-crop-apply").performTouchInput { click() }
        assertTrue("Drag exposes the top of the source", crops.getValue("one").y < filled.y)
        assertEquals(0.0, crops.getValue("one").y, .000001)
        capture("crop-applied")
        compose.onNodeWithTag("moment-photo-crop").performTouchInput { click() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("moment-crop-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("moment-crop-ratio-1:1").performScrollTo().performTouchInput { click() }
        compose.onNodeWithTag("moment-crop-canvas").performTouchInput {
            val a = center - androidx.compose.ui.geometry.Offset(20f,0f)
            val b = center + androidx.compose.ui.geometry.Offset(20f,0f)
            down(0,a); down(1,b)
            for(i in 1..10) {
                moveTo(0,a-androidx.compose.ui.geometry.Offset(i*5f,0f),delayMillis=16)
                moveTo(1,b+androidx.compose.ui.geometry.Offset(i*5f,0f),delayMillis=16)
            }
            up(0); up(1)
        }
        compose.onNodeWithTag("moment-crop-apply").performTouchInput { click() }
        assertTrue("Pinch zooms into the original", crops.getValue("one").width < 1.0)
        val applied = crops
        compose.onNodeWithTag("moment-photo-crop").performTouchInput { click() }
        compose.onNodeWithTag("moment-crop-reset").performScrollTo().performTouchInput { click() }
        compose.onNodeWithTag("moment-crop-cancel").performTouchInput { click() }
        assertEquals(applied, crops)
        compose.onNodeWithTag("moment-photo-full").performTouchInput { click() }
        assertTrue(crops.isEmpty())
        primaryActionIsAboveSystemUi()
    }
    @Test fun captionTypingAndDoneKeepShareAboveNativeKeyboard() {
        show()
        primaryActionIsAboveSystemUi(); capture("standard")
        compose.onNodeWithContentDescription("Add or edit caption").performScrollTo().performTouchInput { click() }
        compose.waitUntil(10_000) { keyboardShown() }
        val draft = "A little thought for you.\nSee you soon."
        val editor = compose.onNode(hasSetTextAction())
        draft.forEach { editor.performTextInput(it.toString()) }
        editor.assertIsDisplayed().assertTextContains(draft)
        assertEquals(draft, caption)
        primaryActionIsAboveSystemUi(); capture("keyboard")
        editor.performImeAction()
        compose.waitUntil(10_000) { !keyboardShown() }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithTag("moment-share").assertIsDisplayed()
        compose.onNodeWithText(draft).assertExists()
        compose.onNodeWithText("Public").performScrollTo().performTouchInput { click() }
        assertEquals("public", visibility)
        primaryActionIsAboveSystemUi()
        compose.onNodeWithTag("moment-share").performTouchInput { click() }
        assertEquals(1, shares)
    }
    @Test fun largeTextCanScrollEditAndDismissKeyboardWithoutLosingDraft() {
        show(scale = 2f)
        primaryActionIsAboveSystemUi()
        compose.onNodeWithText("Public").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("Just you two").performScrollTo().performTouchInput { click() }
        assertEquals("private", visibility)
        capture("large-text")
        compose.onNodeWithContentDescription("Add or edit caption").performScrollTo().performTouchInput { click() }
        compose.waitUntil(10_000) { keyboardShown() }
        compose.onNode(hasSetTextAction()).performTextInput("A thought for us.\nAnd another.")
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        primaryActionIsAboveSystemUi(); capture("large-keyboard")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { !keyboardShown() }
        assertEquals("A thought for us.\nAnd another.", caption)
        primaryActionIsAboveSystemUi()
        compose.onNodeWithTag("moment-caption-done").performClick()
        compose.onNodeWithTag("moment-share").assertIsDisplayed()
    }
}
