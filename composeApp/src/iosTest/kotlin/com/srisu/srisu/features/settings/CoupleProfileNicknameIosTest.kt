package com.srisu.srisu.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.coupleprofile.presentation.CoupleProfileNicknameContent
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.cinterop.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.*

/** Native Compose interaction with synthetic state; the shared editor is also used by Settings. */
@OptIn(ExperimentalTestApi::class)
class CoupleProfileNicknameIosTest {
    private val partner=SettingsPartner(2,"Annapurna Alexandra Shrestha",coupleId=1,connectionId=3,revision=2,nicknameRevision=1)
    private fun checkLayout(width:Int,height:Int,scale:Float,name:String)=runSkikoComposeUiTest(
        size=Size(width*2f,height*2f),density=Density(2f,scale)) {
        val state=mutableStateOf(SettingsState(loading=false,loaded=true,partner=partner))
        val writes=mutableListOf<Pair<SettingsPartner,String>>()
        setContent {SriSuTheme(darkTheme=true) {Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CoupleProfileNicknameContent(1,state.value,onSave={expected,value ->
                    writes += expected to value
                    state.value=state.value.copy(partner=expected.copy(nickname=value.takeIf {it.isNotBlank()},
                        nicknameRevision=expected.nicknameRevision!!+1),nicknameSaved=state.value.nicknameSaved+1)
                },onRetry={})
            }
        }}}
        onNodeWithText("Add a nickname for Annapurna Alexandra Shrestha").assertIsDisplayed()
        capture("$name-entry",Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithTag("couple-profile-nickname").performScrollTo().performClick()
        onNodeWithTag("partner-nickname").performScrollTo().performTextInput("Sunshine")
        onNodeWithText("Save",substring=false).assertIsDisplayed().assertIsEnabled()
        val roots=onAllNodes(isRoot())
        capture("$name-editor",Image.makeFromBitmap(roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithText("Save",substring=false).performClick()
        onNodeWithTag("partner-nickname").assertDoesNotExist()
        onNodeWithText("Sunshine").assertIsDisplayed()
        assertEquals(listOf(partner to "Sunshine"),writes)
        onNodeWithTag("couple-profile-nickname").performClick()
        onNodeWithTag("partner-nickname").performScrollTo().assertTextContains("Sunshine")
        onNodeWithText("Remove nickname").performScrollTo().assertIsDisplayed().performClick()
        onNodeWithTag("partner-nickname").assertDoesNotExist()
        onNodeWithText("Add a nickname for Annapurna Alexandra Shrestha").assertIsDisplayed()
        assertEquals(2,writes.size);assertEquals("",writes.last().second)
    }
    @Test fun profileNicknameSaveRemove()=checkLayout(390,844,1f,"standard-dark")
    @Test fun profileNicknameSmallLargeText()=checkLayout(320,640,1.8f,"small-large-dark")
    @Test fun profileNicknameLandscape()=checkLayout(640,320,1f,"landscape-dark")

    @Test fun failedSaveRetainsInputAndReplacedCoupleClosesEditor()=runSkikoComposeUiTest(size=Size(780f,1688f),density=Density(2f)) {
        val state=mutableStateOf(SettingsState(loading=false,loaded=true,partner=partner))
        var writes=0
        setContent {SriSuTheme(darkTheme=true) {Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CoupleProfileNicknameContent(1,state.value,onSave={_,_ ->
                    writes++;state.value=state.value.copy(nicknameSaving=true)
                },onRetry={})
            }
        }}}
        onNodeWithTag("couple-profile-nickname").performClick()
        onNodeWithTag("partner-nickname").performScrollTo().performTextInput("Moonbeam")
        onNodeWithText("Save",substring=false).performClick()
        onNodeWithText("Saving…").assertIsNotEnabled()
        runOnIdle {state.value=state.value.copy(nicknameSaving=false,nicknameError="Couldn’t save. Try again.")}
        onNodeWithTag("partner-nickname").assertTextContains("Moonbeam")
        onNodeWithText("Save",substring=false).assertIsEnabled()
        runOnIdle {state.value=state.value.copy(partner=partner.copy(coupleId=9,connectionId=10))}
        onNodeWithTag("partner-nickname").assertDoesNotExist()
        onNodeWithTag("couple-profile-nickname").assertDoesNotExist()
        assertEquals(1,writes)
    }

    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun capture(name:String,bytes:ByteArray) {
        val directory=NSTemporaryDirectory()+"couple-profile-nickname"
        NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null)
        val path="$directory/ios-$name.png"
        bytes.usePinned {NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true)}
        println("COUPLE_NICKNAME_RENDER $path")
    }
}
