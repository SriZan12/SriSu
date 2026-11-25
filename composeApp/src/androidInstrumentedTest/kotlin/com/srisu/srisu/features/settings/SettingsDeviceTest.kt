package com.srisu.srisu.features.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.core.identity.LocalPartnerNames
import com.srisu.srisu.core.identity.PartnerNameSnapshot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun nicknameEditor(scale:Float=1f,narrow:Boolean=false,keyboard:Boolean=false) {
        val partner=SettingsPartner(2,"Sam",coupleId=4,connectionId=3,revision=2,connectedDays=248,nicknameRevision=1)
        val state=mutableStateOf(SettingsState(loading=false,loaded=true,partner=partner))
        val writes=mutableListOf<Pair<SettingsPartner,String>>()
        compose.activityRule.scenario.onActivity {it.enableEdgeToEdge()}
        compose.setContent {
            val density=LocalDensity.current
            val current=state.value.partner!!
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale),
                LocalPartnerNames provides PartnerNameSnapshot(1,4,2,3,current.nickname,current.nicknameRevision,current.name)) {
                ApplyPlatformAppearance(AppAppearance.DARK,true)
                Box(if(narrow) Modifier.widthIn(max=320.dp).heightIn(max=640.dp) else Modifier) {
                    SriSuTheme(darkTheme=true) {SettingsContent(state.value,{}, {},onNickname={expected,value ->
                        writes += expected to value
                        state.value=state.value.copy(partner=expected.copy(nickname=value.trim().takeIf {it.isNotEmpty()},
                            nicknameRevision=expected.nicknameRevision!!+1),nicknameSaved=state.value.nicknameSaved+1)
                    })}
                }
            }
        }
        fun open() {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Nickname",substring=false))
            compose.onNodeWithText("Nickname",substring=false).performClick()
            compose.onNodeWithTag("partner-nickname").performScrollTo()
        }
        open()
        compose.onNodeWithText("Save",substring=false).assertIsNotEnabled()
        val field=compose.onNodeWithTag("partner-nickname")
        if(keyboard) {
            field.performTouchInput {click()}
            compose.waitUntil(10_000) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("dumpsys input_method")).bufferedReader().use {it.readText().contains("mInputShown=true")}
            }
        }
        field.performTextInput("Sunshine ☀️")
        compose.onNodeWithText("Save",substring=false).assertIsDisplayed().assertIsEnabled()
        if(keyboard) {
            field.assertIsFocused()
            val button=compose.onNodeWithText("Save",substring=false).fetchSemanticsNode().boundsInWindow
            compose.runOnIdle {
                fun windows(view:android.view.View):List<android.view.Window> =
                    (if(view is androidx.compose.ui.window.DialogWindowProvider) listOf(view.window) else emptyList()) +
                    (if(view is android.view.ViewGroup) (0 until view.childCount).flatMap {windows(view.getChildAt(it))} else emptyList())
                val window=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap(::windows).first()
                val decor=window.decorView
                val visible=android.graphics.Rect().also {decor.getWindowVisibleDisplayFrame(it)}
                val offset=IntArray(2).also {decor.getLocationOnScreen(it)}
                // AlertDialog consumes IME insets while resizing its own window. The
                // focused editor + input-method check above establish keyboard visibility;
                // the visible screen frame is the actual unobscured boundary here.
                assertTrue("Nickname Save stays above the keyboard",button.bottom+offset[1]<=visible.bottom+1)
            }
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            android.os.SystemClock.sleep(400) // Native dialog/IME animation is outside Compose's test clock.
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.filesDir,"settings-nickname-keyboard-dark.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            };bitmap.recycle()
        }
        compose.onNodeWithText("Save",substring=false).performClick()
        compose.onNodeWithTag("partner-nickname").assertDoesNotExist()
        assertEquals(listOf(partner to "Sunshine ☀️"),writes)
        open()
        compose.onNodeWithTag("partner-nickname").assertTextContains("Sunshine ☀️")
        compose.onNodeWithText("Remove nickname").performScrollTo().performClick()
        compose.onNodeWithTag("partner-nickname").assertDoesNotExist()
        assertEquals("",writes.last().second)
        assertEquals(2,writes.size)
        assertNull(state.value.partner!!.nickname)
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Sam",substring=false))
        compose.onNodeWithText("Sam",substring=false).assertIsDisplayed()
    }
    private fun verify(connected:Boolean=true,scale:Float=1f,dark:Boolean=false,narrow:Boolean=false) {
        val partner=SettingsPartner(2,"Sam",coupleId=4,connectionId=3,revision=2,connectedDays=248)
        val routes=mutableListOf<Route>();var disconnects=0
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                ApplyPlatformAppearance(if(dark) AppAppearance.DARK else AppAppearance.LIGHT,dark)
                Box(if(narrow) Modifier.widthIn(max=320.dp).heightIn(max=640.dp) else Modifier) {
                SriSuTheme(darkTheme=dark) {SettingsContent(
                    SettingsState(loading=false,loaded=true,partner=if(connected) partner else null),
                    {},routes::add,onDisconnect={disconnects++})}
                }
            }
        }
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.waitForIdle()
        compose.runOnIdle {
            val bars=androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,compose.activity.window.decorView)
            assertEquals(!dark,bars.isAppearanceLightStatusBars)
            assertEquals(!dark,bars.isAppearanceLightNavigationBars)
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=File(instrumentation.targetContext.filesDir,"settings-$connected-$scale-$dark.png")
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        target.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        compose.onNodeWithText("Account").performClick()
        assertEquals(ProfileNav.SettingsDetail("account"),routes.last())
        if(connected) {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Partner",substring=false))
            compose.onNodeWithText("Partner",substring=false).performClick()
            compose.onNodeWithText("Disconnect partner").assertDoesNotExist()
            compose.onNodeWithText("Partner",substring=false).performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Disconnect partner"))
            compose.onNodeWithText("Disconnect partner").performClick()
            compose.onNodeWithText("Keep connected").performClick();assertEquals(0,disconnects)
            compose.onNodeWithText("Disconnect partner").performClick()
            compose.onNodeWithText("Disconnect",substring=false).performClick();assertEquals(1,disconnects)
        } else {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Connect"))
            compose.onNodeWithText("Connect").performClick();assertEquals(ChatNav.PartnerFlow,routes.last())
        }
        repeat(2) {compose.onNodeWithTag("settings-list").performTouchInput {swipeUp(durationMillis=250)}}
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Privacy & Security"))
        compose.onNodeWithText("Privacy & Security").performClick()
        assertEquals(ProfileNav.SettingsDetail("privacy"),routes.last())
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Sign out"))
        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Cancel").performClick()
    }
    @Test fun darkConnected()=verify(dark=true)
    @Test fun nicknameSaveAndRemoveWithNativeKeyboard()=nicknameEditor(keyboard=true)
    @Test fun nicknameAtSmallWidthAndLargeText()=nicknameEditor(scale=1.8f,narrow=true)
    @Test fun connected()=verify()
    @Test fun notConnected()=verify(false)
    @Test fun largeTextDark()=verify(scale=1.8f,dark=true)
    @Test fun smallScreenDoubleText()=verify(scale=2f,narrow=true)
}
