package com.srisu.srisu.features.moments

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.*
import org.junit.Assert.*

/** Production public viewer with synthetic media/state and the real Android IME. */
class PublicMomentDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private var previousAccessibilityFlags:Int?=null
    @After fun restoreAutomation() {
        previousAccessibilityFlags?.let {flags->
            val info=instrumentation.uiAutomation.serviceInfo
            info.flags=flags;instrumentation.uiAutomation.serviceInfo=info
        }
    }
    private fun keyboardShown()=android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
    ).bufferedReader().use {it.readText().contains("mInputShown=true")}
    private fun sendButton()=compose.onAllNodes(hasText("Send appreciation") and hasClickAction()).onLast()
    private data class KeyboardLayout(val button:androidx.compose.ui.geometry.Rect,val keyboard:Rect)
    private fun keyboardLayout():KeyboardLayout? {
        val ime=instrumentation.uiAutomation.windows.firstOrNull {it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD} ?: return null
        val keyboard=Rect();ime.getBoundsInScreen(keyboard)
        if(keyboard.isEmpty) return null
        return runCatching {KeyboardLayout(sendButton().fetchSemanticsNode().boundsInWindow,keyboard)}.getOrNull()
    }
    private fun stableKeyboardLayout():KeyboardLayout {
        var previous:KeyboardLayout?=null
        var stableSince=SystemClock.uptimeMillis()
        compose.waitUntil(10_000) {
            val layout=keyboardLayout()
            val now=SystemClock.uptimeMillis()
            if(layout==null || layout!=previous) {previous=layout;stableSince=now;false}
            else now-stableSince>=300
        }
        return requireNotNull(previous)
    }
    private fun aboveKeyboard(name:String) {
        // Let both IME animation and the error-driven sheet remeasure finish before
        // scrolling. Then wait for the final scroll geometry; no relaxed overlap.
        stableKeyboardLayout()
        sendButton().performScrollTo().assertIsDisplayed().assertIsEnabled()
        val layout=stableKeyboardLayout()
        capture(name)
        assertTrue("Send action must sit entirely above the native keyboard: button=${layout.button}, keyboard=${layout.keyboard}",
            layout.button.bottom<=layout.keyboard.top+1f)
    }
    private fun capture(name:String) {
        compose.waitForIdle();instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir,"public-moment-$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun largeTextAppreciationRemainsAboveKeyboardAndRetainsDraftAfterFailure() {
        val info=instrumentation.uiAutomation.serviceInfo
        previousAccessibilityFlags=info.flags
        info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=info
        val bitmap=Bitmap.createBitmap(120,160,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(173,194,181))} // theme-audit: image pixels — synthetic fixture
        val bytes=ByteArrayOutputStream().use {out->bitmap.compress(Bitmap.CompressFormat.PNG,100,out);out.toByteArray()}
        bitmap.recycle()
        var state by mutableStateOf(MomentViewerState(moment=Moment(10,3,2,MomentPerson(2,"Maya"),
            caption="A little time outside, together.",visibility="public",photos=listOf(MomentPhoto(1,"fixture",width=120,height=160)),
            publishedAt="2026-11-25T06:00:00Z",expiresAt="2026-11-26T06:00:00Z",serverTime="2026-11-25T07:00:00Z"),
            bytes=bytes,loading=false,fresh=true,hasNext=true))
        var sends=0
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                SriSuTheme(darkTheme=true) {Box(Modifier.width(320.dp).fillMaxHeight()) {
                    PublicMomentViewerContent(state,{},{},{},{},{},onNote={state=state.copy(note=it)},
                        onSendNote={sends++;state=if(sends==1) state.copy(busy=true) else state.copy(note="",noteSent=true,error=null)},
                        onNotes={},onCloseNotes={},onProfile={},avatar={bytes})
                }}
            }
        }
        compose.onNodeWithText("Send appreciation").performScrollTo().performTouchInput {click()}
        sendButton().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTouchInput {click()}
        compose.waitUntil(10_000) {keyboardShown()}
        val text="This little moment made us smile."
        compose.onNode(hasSetTextAction()).performTextInput(text)
        aboveKeyboard("large-dark-keyboard")
        sendButton().performTouchInput {click()}
        compose.onNodeWithText("Sending…").assertIsNotEnabled();assertEquals(1,sends)
        compose.runOnIdle {state=state.copy(busy=false,error="Connection interrupted. Please try again.")}
        compose.onNode(hasSetTextAction()).assertTextContains(text)
        compose.onNode(hasSetTextAction()).performScrollTo().performTouchInput {click()}
        compose.waitUntil(10_000) {keyboardShown()}
        aboveKeyboard("keyboard-retry")
        sendButton().performTouchInput {click()}
        compose.waitUntil(10_000) {!keyboardShown()}
        assertEquals(2,sends)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Your appreciation was sent").performScrollTo().assertIsDisplayed()
        capture("sent")
    }
}
