package com.srisu.srisu.features.moments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.cinterop.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.Test

/** iOS simulator Compose runtime, synthetic data; does not exercise native picker/camera. */
@OptIn(ExperimentalTestApi::class)
class MomentIosRenderTest {
    private fun render(name:String,dark:Boolean=false,fontScale:Float=1f,small:Boolean=false,landscape:Boolean=false,short:Boolean=false,keyboardViewport:Boolean=false) {
        val width=if(landscape || keyboardViewport) 640 else if(small || short) 320 else 390;val height=if(keyboardViewport) 240 else if(landscape) 320 else if(short) 480 else if(small) 640 else 844
        runSkikoComposeUiTest(size=Size(width*2f,height*2f),density=Density(2f,fontScale)) {
            mainClock.autoAdvance=false
            setContent {Box(Modifier.size(width.dp,height.dp).testTag("moment-screen")) {SriSuTheme(darkTheme=dark) {Surface {MomentRenderFixture(name)}}}}
            mainClock.advanceTimeBy(500);waitForIdle()
            if((name.startsWith("create") && !name.contains("select") && !keyboardViewport) || (name.startsWith("viewer") && !name.contains("expired"))) {
                waitUntil(timeoutMillis=8_000) {
                    mainClock.advanceTimeByFrame()
                    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Photo ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
                }
            }
            mainClock.advanceTimeBy(500);waitForIdle()
            if(name.startsWith("create")) onNodeWithTag(if(keyboardViewport) "moment-caption-done" else "moment-share").assertIsDisplayed()
            if(keyboardViewport) onNode(hasSetTextAction()).assertIsDisplayed()
            val bitmap=onNodeWithTag("moment-screen").captureToImage().asSkiaBitmap()
            val png=Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
            writePng(name,png)
        }
    }
    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun writePng(name:String,bytes:ByteArray) {
        val directory=NSTemporaryDirectory()+"moments"
        NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null)
        val path="$directory/ios-$name.png"
        bytes.usePinned {NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true)}
        println("MOMENT_RENDER $path")
    }
    @Test fun baseline()=render("00-baseline-home")
    @Test fun home()=render("home-active")
    @Test fun homeDark()=render("home-active-dark",dark=true)
    @Test fun empty()=render("home-empty")
    @Test fun selection()=render("create-select")
    @Test fun emptySmallLargeText()=render("create-select-small",small=true,fontScale=2f)
    @Test fun emptyLandscape()=render("create-select-landscape",landscape=true)
    @Test fun emptyComposerKeepsDraftWhenAddingAndRemovingPhoto()=runSkikoComposeUiTest(size=Size(780f,1688f),density=Density(2f)) {
        setContent {SriSuTheme {MomentRenderFixture("create-select-flow")}}
        onNodeWithTag("moment-add-first-photo").assertIsDisplayed()
        onNodeWithTag("moment-share").assertIsNotEnabled()
        onNodeWithContentDescription("Add or edit caption").performScrollTo().performClick()
        onNode(hasSetTextAction()).performTextInput("A thought before a photo.")
        onNode(hasSetTextAction()).performImeAction()
        onNodeWithText("Public").performScrollTo().performClick()
        onNodeWithTag("moment-add-first-photo").performScrollTo().performClick()
        onNodeWithTag("moment-share").assertIsEnabled()
        onNodeWithContentDescription("Remove selected photo").performScrollTo().performClick()
        onNodeWithTag("moment-add-first-photo").assertIsDisplayed()
        onNodeWithTag("moment-share").assertIsNotEnabled()
        onNodeWithText("A thought before a photo.").assertExists()
        onNode(hasText("Public") and isSelectable()).assertIsSelected()
    }
    private fun cropEditor(name: String, width: Int, height: Int, scale: Float) = runSkikoComposeUiTest(
        size=Size(width*2f,height*2f),density=Density(2f,scale)) {
        setContent { Box(Modifier.size(width.dp,height.dp).testTag("moment-screen")) { SriSuTheme { MomentRenderFixture("create-preview") } } }
        onNodeWithTag("moment-photo-full").assertIsSelected()
        onNodeWithTag("moment-photo-crop").performScrollTo().performClick()
        waitUntil(timeoutMillis=8_000) { onAllNodes(hasTestTag("moment-crop-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("moment-crop-ratio-1:1").performScrollTo().performClick()
        onNodeWithTag("moment-crop-zoom").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
        onNodeWithTag("moment-crop-canvas").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(50f,80f)) }
        onNodeWithTag("moment-crop-apply").assertIsDisplayed()
        onNodeWithTag("moment-crop-reset").performScrollTo().assertIsDisplayed()
        val bitmap=onNodeWithTag("moment-screen").captureToImage().asSkiaBitmap()
        writePng(name,Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithTag("moment-crop-apply").performClick()
        onNodeWithTag("moment-photo-full").assertIsNotSelected()
        onNodeWithTag("moment-photo-crop").performClick()
        onNodeWithTag("moment-crop-reset").performScrollTo().performClick()
        onNodeWithTag("moment-crop-cancel").performClick()
        onNodeWithTag("moment-photo-full").assertIsNotSelected()
        onNodeWithTag("moment-photo-full").performClick().assertIsSelected()
        onNodeWithTag("moment-share").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun cropPortrait()=cropEditor("create-crop",390,844,1f)
    @Test fun cropSmallLargeText()=cropEditor("create-crop-small",320,640,2f)
    @Test fun cropLandscape()=cropEditor("create-crop-landscape",640,320,1f)
    @Test fun preview()=render("create-preview")
    @Test fun previewSmallLargeText()=render("create-small-large-text",small=true,fontScale=2f)
    @Test fun shortLandscapeCaption()=render("create-caption-compact",fontScale=2f,keyboardViewport=true)
    @Test fun previewLandscape()=render("create-landscape",landscape=true)
    @Test fun previewShort()=render("create-short",short=true)
    @Test fun previewDark()=render("create-dark",dark=true)
    @Test fun failedUpload()=render("create-failure")
    @Test fun captionAndAudienceRemainReachable()=runSkikoComposeUiTest(size=Size(640f,1280f),density=Density(2f,1.6f)) {
        setContent {SriSuTheme {MomentRenderFixture("create-preview")}}
        onNodeWithContentDescription("Add or edit caption").performScrollTo().performClick()
        onNode(hasSetTextAction()).performTextReplacement("A little thought for you.\nSee you soon.")
        onNode(hasSetTextAction()).performImeAction()
        onNode(hasSetTextAction()).assertDoesNotExist()
        onNodeWithText("A little thought for you.\nSee you soon.").assertExists()
        onNodeWithText("Public").performScrollTo().performClick()
        onNode(hasText("Public") and isSelectable()).assertIsSelected()
        onNodeWithTag("moment-share").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun navigation()=render("navigation-home")
    @Test fun navigationLargeText()=render("navigation-small-large-text",fontScale=1.6f,small=true)
    @Test fun audience()=render("create-audience")
    @Test fun viewer()=render("viewer-partner")
    @Test fun visitor()=render("viewer-public")
    @Test fun creator()=render("viewer-creator")
    @Test fun expired()=render("viewer-expired")
    @Test fun smallLargeText()=render("home-long-small-large-text",fontScale=1.6f,small=true)
}
