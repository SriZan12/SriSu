package com.srisu.srisu.features.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
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
import kotlin.test.assertEquals

/** iOS simulator Compose runtime, synthetic data; does not exercise native picker/camera. */
@OptIn(ExperimentalTestApi::class)
class HomeIosRenderTest {
    private fun render(name:String,dark:Boolean=false,fontScale:Float=1f,small:Boolean=false,wide:Boolean=false,focus:String?=null) {
        val width=if(small) 320 else if(wide) 430 else 390;val height=if(small) 700 else if(wide) 932 else 844
        runSkikoComposeUiTest(size=Size(width*2f,height*2f),density=Density(2f,fontScale)) {
            mainClock.autoAdvance=false
            setContent {Box(Modifier.size(width.dp,height.dp).testTag("home-screen")) {SriSuTheme(darkTheme=dark) {Surface {HomeRenderFixture(name)}}}}
            mainClock.advanceTimeBy(500);waitForIdle()
            mainClock.advanceTimeBy(500);waitForIdle()
            fun capture(suffix:String="") {
                val bitmap=onNodeWithTag("home-screen").captureToImage().asSkiaBitmap()
                val png=Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
                writePng("$name-$width-$fontScale$suffix",png)
            }
            capture()
            if(name=="quiet" && fontScale==2f) {
                fun top(label:String)=onNode(SemanticsMatcher(label) {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label==label
                }).fetchSemanticsNode().boundsInRoot.top
                assertEquals(top("Open your Moments"),top("Open Aatithi’s Moments"),1f)
            }
            focus?.let {
                mainClock.autoAdvance=true
                onNode(hasScrollToIndexAction()).performScrollToNode(hasText(it))
                onNodeWithText(it).assertIsDisplayed();waitForIdle();capture("-detail")
            }
        }
    }
    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun writePng(name:String,bytes:ByteArray) {
        val directory=NSTemporaryDirectory()+"home-couple-space"
        NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null)
        val path="$directory/ios-$name.png"
        bytes.usePinned {NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true)}
        println("HOME_RENDER $path")
    }
    @Test fun spark()=render("spark")
    @Test fun message()=render("message")
    @Test fun quiet()=render("quiet")
    @Test fun unpaired()=render("unpaired")
    @Test fun sent()=render("sent")
    @Test fun received()=render("received")
    @Test fun newCouple()=render("new")
    @Test fun loading()=render("loading")
    @Test fun error()=render("failed")
    @Test fun small()=render("spark",small=true)
    @Test fun widerPhone()=render("message",wide=true)
    @Test fun largeText()=render("quiet",fontScale=2f,small=true)
    @Test fun longContent()=render("long-content",small=true,focus="Go first")
    @Test fun incomingPlanLargeText()=render("incoming-plan",fontScale=2f,small=true,focus="Another day")
    @Test fun waitingPlan()=render("waiting-plan",focus="👋 Nudge in chat")
    @Test fun staleQuestion()=render("stale-question",focus="Question hasn’t refreshed. Open it to check the latest state.")
    @Test fun partnerIdea()=render("partner-idea",focus="Me too")
}
