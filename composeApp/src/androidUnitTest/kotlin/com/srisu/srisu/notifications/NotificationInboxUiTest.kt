package com.srisu.srisu.notifications

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationInboxUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val actions=mutableListOf<String>()
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun show(state:InboxState=InboxFixtures.state,dark:Boolean=false,scale:Float=1f) {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                SriSuTheme(darkTheme=dark) { NotificationInboxContent(state,
                    onBack={actions+="back"},onSettings={actions+="settings"},onRefresh={actions+="refresh"},
                    onMore={actions+="more"},onFilter={actions+="filter:$it"},onReadAll={actions+="read-all"},
                    onOpen={actions+=it.id},now=InboxFixtures.now,zone=kotlinx.datetime.TimeZone.UTC) }
            }
        }
    }
    private fun capture(name:String)=compose.runOnIdle {
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=File("build/reports/notifications/inbox-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun activityRowsHaveSpecificCopyAndIndependentActions() {
        show();compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("Sam invited you to a plan").assertIsDisplayed()
        compose.onNodeWithTag("notification:${InboxFixtures.id(1)}").performClick()
        compose.onNodeWithText("Unread",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Mark all read").performClick()
        compose.onNodeWithContentDescription("Notification settings").performClick()
        assertEquals(listOf(InboxFixtures.id(1),"filter:true","read-all","settings"),actions)
        capture("light")
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Partner status"))
        compose.onNodeWithText("No longer available").assertIsDisplayed();capture("history")
    }
    @Test fun darkAppearance() {show(dark=true);compose.onNodeWithText("Sam answered your question").assertIsDisplayed();capture("dark")}
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun compactLargeTextKeepsControlsAndRowsReachable() {
        show(scale=1.6f);compose.onNodeWithText("All",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Sam invited you to a plan"))
        compose.onNodeWithTag("notification:${InboxFixtures.id(1)}").performClick()
        assertEquals(listOf(InboxFixtures.id(1)),actions);capture("compact-large")
    }
    @Test fun emptyUnreadAndReadAllDoNotClaimNewActivity() {
        show(InboxState(loaded=true,loading=false,unreadOnly=true,readToken="synthetic"))
        compose.onNodeWithText("You’re all caught up").assertIsDisplayed()
        compose.onNodeWithText("Mark all read").assertIsNotEnabled()
        compose.onNodeWithText("View all activity").performClick();assertEquals(listOf("filter:false"),actions);capture("empty")
    }
    @Test fun failureIsRetryableAndLoadingNeverClaimsEmpty() {
        show(InboxState(loading=false,error="Couldn’t load notifications."))
        compose.onNodeWithText("A little quiet here").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick();assertEquals(listOf("refresh"),actions);capture("error")
    }
}
