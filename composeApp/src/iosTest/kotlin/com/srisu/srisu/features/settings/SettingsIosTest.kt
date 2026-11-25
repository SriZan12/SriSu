package com.srisu.srisu.features.settings

import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.navigation.graph.*
import kotlinx.cinterop.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class SettingsIosTest {
    private fun render(name:String,width:Int=390,height:Int=844,scale:Float=1f,dark:Boolean=false,
        state:SettingsState=SettingsState(loading=false,loaded=true,partner=samplePartner)) = runSkikoComposeUiTest(
        size=Size(width*2f,height*2f),density=Density(2f,scale)) {
        val routes=mutableListOf<Route>();var disconnected=0
        setContent {SriSuTheme(darkTheme=dark) {Surface {SettingsContent(state,{},routes::add,onDisconnect={disconnected++})}}}
        waitForIdle()
        onNodeWithText("Settings").assertIsDisplayed()
        capture(name,Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithText("Account").performClick();assertEquals(ProfileNav.SettingsDetail("account"),routes.last())
        if(state.partner != null) {
            onNodeWithTag("settings-list").performScrollToNode(hasText("Disconnect partner"))
            onNodeWithText("Disconnect partner").performClick()
            onNodeWithText("Keep connected").performClick();assertEquals(0,disconnected)
            onNodeWithText("Disconnect partner").performClick()
            onNodeWithText("Disconnect",substring=false).performClick();assertEquals(1,disconnected)
        } else if(state.loaded) {
            onNodeWithTag("settings-list").performScrollToNode(hasText("Connect"))
            onNodeWithText("Connect").performClick();assertEquals(ChatNav.PartnerFlow,routes.last())
        } else {onNodeWithText("Connect").assertDoesNotExist()}
        onNodeWithTag("settings-list").performScrollToNode(hasText("Privacy & Security"))
        onNodeWithText("Privacy & Security").performClick();assertEquals(ProfileNav.SettingsDetail("privacy"),routes.last())
    }
    @Test fun longTitleAtLargeText() = runSkikoComposeUiTest(size=Size(640f,1280f),density=Density(2f,1.8f)) {
        setContent {SriSuTheme {SettingsPage("Privacy & Security",{}) {item {SettingsNotice("Your privacy controls")}}}}
        onNodeWithText("Privacy & Security").assertIsDisplayed()
        onNodeWithText("Your privacy controls").assertIsDisplayed()
        val title=onNodeWithText("Privacy & Security").fetchSemanticsNode().boundsInRoot
        val content=onNodeWithText("Your privacy controls").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= content.top)
        capture("large-title",Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    @Test fun connected()=render("connected")
    @Test fun unconnected()=render("unconnected",state=SettingsState(loading=false,loaded=true))
    @Test fun small()=render("small",320,640)
    @Test fun largeTextDark()=render("large-text-dark",320,640,1.8f,true)
    @Test fun landscape()=render("landscape",640,320)
    @Test fun doubleText()=render("double-text",320,640,2f)
    @Test fun staleDisconnectConfirmationClosesWhenRevisionChanges()=runSkikoComposeUiTest(size=Size(780f,1688f),density=Density(2f)) {
        val state=androidx.compose.runtime.mutableStateOf(SettingsState(loading=false,loaded=true,partner=samplePartner))
        var writes=0
        setContent {SriSuTheme {SettingsContent(state.value,{}, {},onDisconnect={writes++})}}
        onNodeWithTag("settings-list").performScrollToNode(hasText("Disconnect partner"))
        onNodeWithText("Disconnect partner").performClick()
        onNodeWithText("Disconnect partner?").assertIsDisplayed()
        runOnIdle {state.value=state.value.copy(loading=true)}
        onNodeWithText("Disconnect",substring=false).assertIsNotEnabled()
        runOnIdle {state.value=state.value.copy(loading=false,partner=samplePartner.copy(revision=3))}
        onNodeWithText("Disconnect partner?").assertDoesNotExist()
        assertEquals(0,writes)
    }
    @Test fun loading()=render("loading",state=SettingsState())
    @Test fun failed()=render("failed",state=SettingsState(loading=false,error="Couldn’t check your connection. Try again."))
    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun capture(name:String,bytes:ByteArray) {
        val directory=NSTemporaryDirectory()+"settings-ui"
        NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null)
        val path="$directory/ios-$name.png"
        bytes.usePinned {NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true)}
        println("SETTINGS_RENDER $path")
    }
}
