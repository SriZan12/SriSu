package com.srisu.srisu.features.notifications

import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.srisu.srisu.notifications.NotificationReferenceFixture
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.cinterop.*
import kotlinx.datetime.TimeZone
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NotificationInboxIosTest {
    private fun render(name:String,width:Int=390,height:Int=844,scale:Float=1f,dark:Boolean=false,
        state:InboxState=NotificationReferenceFixture.state) = runSkikoComposeUiTest(
        size=Size(width*2f,height*2f),density=Density(2f,scale)) {
        val actions=mutableListOf<String>()
        setContent {SriSuTheme(darkTheme=dark) {Surface {NotificationInboxContent(state,
            onBack={actions+="back"},onReadAll={actions+="read"},onOpen={actions+=it.id},onSettings={actions+="settings"},
            now=NotificationReferenceFixture.now,zone=TimeZone.UTC,loadImage=NotificationReferenceFixture::image)}}}
        waitForIdle()
        if(state.items.isNotEmpty()) {
            waitUntil(timeoutMillis=5_000) {onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Photo ready"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            mainClock.advanceTimeBy(1000);waitForIdle()
        }
        onNodeWithText("Notifications").assertIsDisplayed()
        capture(name,Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        if(state.items.isNotEmpty()) {
            onNodeWithText("Mark read").performClick()
            onNodeWithTag("notification-list").performScrollToNode(hasTestTag("notification:${state.items.last().id}"))
            onNodeWithTag("notification:${state.items.last().id}").performClick()
            onNodeWithTag("notification-list").performScrollToNode(hasText("Settings"))
            onNodeWithText("Settings").performClick()
            onNodeWithContentDescription("Go back").performClick()
            assertEquals(listOf("read",state.items.last().id,"settings","back"),actions)
        }
    }
    @Test fun standard()=render("standard")
    @Test fun small()=render("small",320,640)
    @Test fun largeText()=render("large-text",320,640,1.8f)
    @Test fun dark()=render("dark",dark=true)
    @Test fun landscape()=render("landscape",640,320)
    @Test fun empty()=render("empty",state=InboxState(loading=false,loaded=true))
    @Test fun loading()=render("loading",state=InboxState())
    @Test fun failed()=render("error",state=InboxState(loading=false,error="Couldn’t load notifications. Check your connection and try again."))
    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun capture(name:String,bytes:ByteArray) {
        val directory=NSTemporaryDirectory()+"notification-inbox"
        NSFileManager.defaultManager.createDirectoryAtPath(directory,true,null,null)
        val path="$directory/ios-$name.png"
        bytes.usePinned {NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true)}
        println("INBOX_RENDER $path")
    }
}
