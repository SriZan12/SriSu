package com.srisu.srisu.features.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
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
                writePng("$name-$width-$fontScale${if(dark) "-dark" else ""}$suffix",png)
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
    @Test fun darkHome()=render("spark",dark=true)
    @Test fun darkRefreshing()=render("refreshing",dark=true)
    @Test fun pullRefreshWorksAtSmallWidthAndLargeText() {
        runSkikoComposeUiTest(size=Size(640f,1400f),density=Density(2f,2f)) {
            val page=mutableStateOf("spark")
            var calls=0
            setContent {SriSuTheme(darkTheme=true) {Surface {
                HomeRenderFixture(page.value,retry={calls++;page.value="refreshing"})
            }}}
            fun pull(expectedRefreshes:Int?=null) {
                onNodeWithTag("home-pull-refresh").performTouchInput {
                    swipeDown(startY=height*.15f,endY=height*.85f,durationMillis=650)
                }
                // onRefresh runs after Material's release-to-threshold animation completes.
                waitForIdle()
                if(expectedRefreshes != null) waitUntil(timeoutMillis=5_000) {calls>=expectedRefreshes}
            }
            onNodeWithTag("home-refresh").assertDoesNotExist()
            pull(expectedRefreshes=1)
            assertEquals(1,calls)
            onNodeWithContentDescription("Refreshing Home").assertIsDisplayed()
            onNodeWithText("You & Aatithi").assertIsDisplayed()
            pull()
            assertEquals(1,calls,"Do not start another refresh while the first is running")
            runOnIdle {page.value="refresh-failed"}
            onNodeWithContentDescription("Refreshing Home").assertDoesNotExist()
            onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Showing your last update. Home couldn’t refresh."))
            onNodeWithText("Showing your last update. Home couldn’t refresh.").assertIsDisplayed()
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            pull(expectedRefreshes=2)
            assertEquals(2,calls)
        }
    }
    @Test fun pullRefreshWaitsUntilTheListIsBackAtTheTop()=runSkikoComposeUiTest(size=Size(780f,1688f),density=Density(2f)) {
        var calls=0
        setContent {SriSuTheme {Surface {HomeRenderFixture("spark",retry={calls++})}}}
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plans done"))
        onNodeWithTag("home-pull-refresh").performTouchInput {
            swipeDown(startY=height*.4f,endY=height*.6f,durationMillis=500)
        }
        waitForIdle()
        assertEquals(0,calls)
        onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        onNodeWithTag("home-pull-refresh").performTouchInput {
            swipeDown(startY=height*.15f,endY=height*.85f,durationMillis=650)
        }
        waitForIdle()
        waitUntil(timeoutMillis=5_000) {calls>=1}
        assertEquals(1,calls)
    }
    @Test fun darkQuiet()=render("quiet",dark=true)
    @Test fun darkLoading()=render("loading",dark=true)
    @Test fun darkUnpaired()=render("unpaired",dark=true)
    @Test fun darkSmallLarge()=render("quiet",dark=true,fontScale=2f,small=true,focus="Plans done")
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
