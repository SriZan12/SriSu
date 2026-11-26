package com.srisu.srisu.features.moments

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.cinterop.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.*

/** Native Compose semantics and renders; synthetic content, no live publication. */
@OptIn(ExperimentalTestApi::class)
class PublicMomentIosTest {
    @Test fun photoAndNavigation()=runSkikoComposeUiTest(size=Size(780f,1688f),density=Density(2f)) {
        val state=mutableStateOf(PublicMomentUiFixture.state());val actions=mutableListOf<String>()
        setContent {SriSuTheme {PublicMomentUiContent(state.value,{state.value=it},{actions+=it})}}
        waitUntil(timeoutMillis=8_000) {onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Photo ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        onNodeWithContentDescription("Public Moment photo").assertExists()
        capture("photo",Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithContentDescription("Previous Moment").performScrollTo().performClick()
        onNodeWithContentDescription("Next Moment").performClick()
        onNodeWithText("Their page →").performClick()
        assertTrue(actions.containsAll(listOf("move:-1","move:1","profile:3")))
        runOnIdle {state.value=state.value.copy(hasPrevious=false,hasNext=false)}
        onNodeWithContentDescription("Previous Moment").assertIsNotEnabled()
        onNodeWithContentDescription("Next Moment").assertIsNotEnabled()
    }

    @Test fun smallDarkTextMomentCanSendAppreciationAndRecoverFromFailure()=runSkikoComposeUiTest(size=Size(640f,1280f),density=Density(2f,1.8f)) {
        val state=mutableStateOf(PublicMomentUiFixture.state(textOnly=true));var sends=0
        setContent {SriSuTheme(darkTheme=true) {PublicMomentUiContent(state.value,{state.value=it},{if(it=="send") sends++})}}
        onNodeWithText(PublicMomentUiFixture.caption).performScrollTo().assertIsDisplayed()
        capture("text-small-dark",Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithText("Send appreciation").performScrollTo().performClick()
        onNode(hasSetTextAction()).performScrollTo().performTextInput("A lovely little moment.")
        onAllNodes(hasText("Send appreciation") and hasClickAction()).onLast().performScrollTo().performClick()
        assertEquals(1,sends)
        runOnIdle {state.value=state.value.copy(busy=true)}
        onNodeWithText("Sending…").assertIsNotEnabled()
        runOnIdle {state.value=state.value.copy(busy=false,error="Connection interrupted. Try again.")}
        onNode(hasSetTextAction()).assertTextContains("A lovely little moment.")
        val roots=onAllNodes(isRoot())
        capture("appreciation-retry",Image.makeFromBitmap(roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onAllNodes(hasText("Send appreciation") and hasClickAction()).onLast().performScrollTo().performClick()
        assertEquals(2,sends)
        runOnIdle {state.value=state.value.copy(note="",noteSent=true,error=null)}
        onNode(hasSetTextAction()).assertDoesNotExist()
        onNodeWithText("Your appreciation was sent").performScrollTo().assertIsDisplayed()
    }

    @Test fun remoteUnavailableHidesCaptionAndClosesAppreciationEditor()=runSkikoComposeUiTest(size=Size(1280f,640f),density=Density(2f)) {
        val state=mutableStateOf(PublicMomentUiFixture.state(textOnly=true))
        setContent {SriSuTheme {PublicMomentUiContent(state.value,{state.value=it})}}
        onNodeWithText("Send appreciation").performScrollTo().performClick()
        onNode(hasSetTextAction()).performScrollTo().performTextInput("A private draft")
        runOnIdle {state.value=state.value.copy(fresh=false,unavailable="unavailable",note="")}
        onNode(hasSetTextAction()).assertDoesNotExist()
        onNodeWithText(PublicMomentUiFixture.caption).assertDoesNotExist()
        onNodeWithText("Maya").assertDoesNotExist()
        onNodeWithText("Their page →").assertDoesNotExist()
        onNodeWithText("This Moment is no longer available").assertIsDisplayed()
        capture("unavailable-landscape",Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun capture(name:String,bytes:ByteArray) {
        val directory=NSTemporaryDirectory()+"public-moments"
        NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null)
        val path="$directory/ios-$name.png"
        bytes.usePinned {NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true)}
        println("PUBLIC_MOMENT_RENDER $path")
    }
}
