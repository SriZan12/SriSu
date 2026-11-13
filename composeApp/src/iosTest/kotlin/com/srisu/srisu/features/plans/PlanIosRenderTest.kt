package com.srisu.srisu.features.plans

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
class PlanIosRenderTest {
    private fun render(name:String,dark:Boolean=false,fontScale:Float=1f,small:Boolean=false) {
        val width=if(small) 320 else 390;val height=if(small) 640 else 844
        runSkikoComposeUiTest(size=Size(width*2f,height*2f),density=Density(2f,fontScale)) {
            mainClock.autoAdvance=false
            setContent {Box(Modifier.size(width.dp,height.dp).testTag("moment-screen")) {SriSuTheme(darkTheme=dark) {Surface {PlanRenderFixture(name)}}}}
            mainClock.advanceTimeBy(500);waitForIdle()
            mainClock.advanceTimeBy(500);waitForIdle()
            val bitmap=onNodeWithTag("moment-screen").captureToImage().asSkiaBitmap()
            val png=Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
            writePng(name,png)
        }
    }
    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun writePng(name:String,bytes:ByteArray) {
        val directory=NSTemporaryDirectory()+"plans"
        NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null)
        val path="$directory/ios-$name.png"
        bytes.usePinned {NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true)}
        println("PLAN_RENDER $path")
    }
    @Test fun agenda()=render("agenda")
    @Test fun invitation()=render("invited")
    @Test fun agreed()=render("agreed")
    @Test fun change()=render("change")
    @Test fun completed()=render("completed")
    @Test fun failed()=render("failed")
    @Test fun past()=render("past")
    @Test fun empty()=render("empty")
    @Test fun dark()=render("agenda-dark",dark=true)
    @Test fun largeText()=render("invited-small-large-text",fontScale=1.6f,small=true)
    @Test fun editorDateAndTimeControlsOpen()=runSkikoComposeUiTest(size=Size(780f,1688f),density=Density(2f)) {
        setContent {SriSuTheme {com.srisu.srisu.features.plans.presentation.PlanEditor(
            PlanFixtures.base.copy(editing=true,draft=com.srisu.srisu.features.plans.data.PlanDraft(3,title="A walk",timeZone="Asia/Kathmandu")),{},{},{},{},{})}}
        onNodeWithText("Choose a date").performClick();waitForIdle()
        onNodeWithText("Choose date").assertExists().performClick();waitForIdle()
        onNodeWithText("Choose a time").performClick();waitForIdle()
        onNodeWithText("Choose time").assertExists()
    }

}
