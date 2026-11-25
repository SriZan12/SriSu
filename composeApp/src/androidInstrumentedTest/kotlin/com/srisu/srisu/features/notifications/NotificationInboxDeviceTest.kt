package com.srisu.srisu.features.notifications

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import srisu.composeapp.generated.resources.Res
import java.io.File

/** Native Android Compose flow, synthetic rows only; no live account is touched. */
class NotificationInboxDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun verify(scale:Float=1f,dark:Boolean=false) {
        val now=Instant.parse("2026-11-25T12:00:00Z")
        val rows=(1..30).map { n -> InboxItem("00000000-0000-4000-8000-${n.toString().padStart(12,'0')}",
            if(n==1) "spark.submitted" else "moment.published","moments","Sam shared a video moment","",
            "2026-11-25T11:48:00Z",readAt=if(n>2) now.toString() else null,available=true,actorName="Sam",avatar=true,
            segments=listOf(InboxSegment("Sam",true),InboxSegment(if(n==1) " answered your Spark. Answer to see theirs." else " shared a video moment"))) }
        val actions=mutableListOf<String>()
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                SriSuTheme(darkTheme=dark) {NotificationInboxContent(
                    InboxState(items=rows,loaded=true,loading=false,unreadCount=2,readToken="synthetic"),
                    onReadAll={actions+="read"},onOpen={actions+=it.id},onBack={actions+="back"},onSettings={actions+="settings"},
                    now=now,zone=TimeZone.UTC,loadImage={_,_->Res.readBytes("drawable/will.jpg")})}
            }
        }
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("Mark read").performClick()
        compose.onNodeWithTag("notification:${rows.first().id}").performClick()
        repeat(3) {compose.onNodeWithTag("notification-list").performTouchInput {swipeUp(durationMillis=250)}}
        compose.onNodeWithTag("notification-list").performTouchInput {swipeDown(durationMillis=250)}
        compose.onNodeWithTag("notification-list").performScrollToIndex(30)
        compose.onNodeWithTag("notification:${rows.last().id}").performClick()
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Settings"))
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithContentDescription("Go back").performClick()
        assertEquals(listOf("read",rows.first().id,rows.last().id,"settings","back"),actions)
        compose.onNodeWithTag("notification-list").performScrollToIndex(0)
        compose.onNodeWithText("TODAY").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val target=File(instrumentation.targetContext.filesDir,"inbox-$scale-$dark.png")
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun standardAndLongList()=verify()
    @Test fun largeTextDark()=verify(1.8f,true)
}
