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
    private fun render(name:String,dark:Boolean=false,fontScale:Float=1f,small:Boolean=false) {
        val width=if(small) 320 else 390;val height=if(small) 640 else 844
        runSkikoComposeUiTest(size=Size(width*2f,height*2f),density=Density(2f,fontScale)) {
            mainClock.autoAdvance=false
            setContent {Box(Modifier.size(width.dp,height.dp).testTag("moment-screen")) {SriSuTheme(darkTheme=dark) {Surface {MomentRenderFixture(name)}}}}
            mainClock.advanceTimeBy(500);waitForIdle()
            if((name.startsWith("create") && !name.contains("select")) || (name.startsWith("viewer") && !name.contains("expired"))) {
                waitUntil(timeoutMillis=8_000) {
                    mainClock.advanceTimeByFrame()
                    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Photo ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
                }
            }
            mainClock.advanceTimeBy(500);waitForIdle()
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
    @Test fun preview()=render("create-preview")
    @Test fun navigation()=render("navigation-home")
    @Test fun navigationLargeText()=render("navigation-small-large-text",fontScale=1.6f,small=true)
    @Test fun audience()=render("create-audience")
    @Test fun viewer()=render("viewer-partner")
    @Test fun visitor()=render("viewer-public")
    @Test fun creator()=render("viewer-creator")
    @Test fun expired()=render("viewer-expired")
    @Test fun smallLargeText()=render("home-long-small-large-text",fontScale=1.6f,small=true)
}
