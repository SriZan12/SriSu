package com.srisu.srisu.features.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
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
    @Test fun connected()=verify()
    @Test fun notConnected()=verify(false)
    @Test fun largeTextDark()=verify(scale=1.8f,dark=true)
    @Test fun smallScreenDoubleText()=verify(scale=2f,narrow=true)
}
