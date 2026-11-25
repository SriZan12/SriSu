package com.srisu.srisu.notifications

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import com.srisu.srisu.core.identity.LocalPartnerNames
import com.srisu.srisu.core.identity.PartnerNameSnapshot
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import kotlinx.coroutines.cancel
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
                    onOpen={actions+=it.id},now=NotificationReferenceFixture.now,zone=kotlinx.datetime.TimeZone.UTC,loadImage=NotificationReferenceFixture::image) }
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
        compose.onNodeWithText("Sam invited you to a plan",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("notification:${InboxFixtures.id(1)}").performClick()
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Unread"))
        compose.onNodeWithText("Unread",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Mark read").performClick()
        compose.onNodeWithContentDescription("Notification settings").performClick()
        assertEquals(listOf(InboxFixtures.id(1),"filter:true","read-all","settings"),actions)
        compose.onNodeWithTag("notification-list").performScrollToIndex(0)
        capture("light")
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Partner status",substring=true))
        compose.onNodeWithText("No longer available").assertIsDisplayed();capture("history")
    }
    @Test fun reference() { show(NotificationReferenceFixture.state); capture("reference") }

    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun nicknameAlertKeepsTheChosenNicknameLiteralAtLargeText() {
        val names=mutableStateOf(PartnerNameSnapshot(accountId=1,coupleId=3,partnerId=2,
            connectionId=4,nickname="Sunshine",canonicalName="Sam"))
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalPartnerNames provides names.value,
                LocalDensity provides Density(density.density,1.6f)) {
                SriSuTheme(darkTheme=true) {NotificationInboxContent(NicknameNotificationFixtures.state,
                    onOpen={actions+=it.id},now=InboxFixtures.now,zone=kotlinx.datetime.TimeZone.UTC)}
            }
        }
        compose.onNodeWithText("Sunshine gave you a nickname · You’re now “Sam” to them.").assertIsDisplayed()
        compose.onNodeWithTag("notification:${NicknameNotificationFixtures.item.id}").performClick()
        assertEquals(listOf(NicknameNotificationFixtures.item.id),actions)
        compose.runOnIdle {names.value=names.value.copy(nickname=null)}
        compose.onNodeWithText("Sam gave you a nickname · You’re now “Sam” to them.").assertIsDisplayed()
    }
    @Test fun skeleton() { show(InboxState());compose.onNodeWithContentDescription("Loading notifications").assertIsDisplayed();capture("loading") }
    @Test @Config(qualifiers="w640dp-h320dp-xhdpi") fun landscape() {
        show(NotificationReferenceFixture.state)
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Settings"))
        compose.onNodeWithText("Settings").performClick();assertEquals(listOf("settings"),actions);capture("landscape")
    }
    @Test fun darkAppearance() {show(dark=true);compose.onNodeWithText("Sam answered your question",substring=true).assertIsDisplayed();capture("dark")}
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun compactLargeTextKeepsControlsAndRowsReachable() {
        show(scale=1.6f);compose.onNodeWithText("Mark read").assertIsDisplayed()
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Sam invited you to a plan",substring=true))
        compose.onNodeWithTag("notification:${InboxFixtures.id(1)}").performClick()
        assertEquals(listOf(InboxFixtures.id(1)),actions);capture("compact-large")
    }
    @Test fun emptyUnreadAndReadAllDoNotClaimNewActivity() {
        show(InboxState(loaded=true,loading=false,unreadOnly=true,readToken="synthetic"))
        compose.onNodeWithText("You’re all caught up").assertIsDisplayed()
        compose.onNodeWithText("Mark read").assertIsNotEnabled()
        compose.onNodeWithText("View all activity").performClick();assertEquals(listOf("filter:false"),actions);capture("empty")
    }
    @Test fun failureIsRetryableAndLoadingNeverClaimsEmpty() {
        show(InboxState(loading=false,error="Couldn’t load notifications."))
        compose.onNodeWithText("A little quiet here").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick();assertEquals(listOf("refresh"),actions);capture("error")
    }

    @Test fun realViewModelRetryReadAndUnreadFilterUpdateTheScreen() {
        val source=FakeInbox().apply {result=NetworkAPIResult.Error("Synthetic offline")}
        val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try {
            compose.setContent {
                DisposableEffect(Unit) {vm.active(true);onDispose {vm.active(false)}}
                SriSuTheme {NotificationInboxContent(vm.state.collectAsState().value,
                    onRefresh=vm::refresh,onOpen=vm::open,onReadAll=vm::readAll,onFilter=vm::filter,
                    now=InboxFixtures.now,zone=kotlinx.datetime.TimeZone.UTC)}
            }
            compose.onNodeWithText("Try again").assertIsDisplayed()
            compose.runOnIdle {source.result=NetworkAPIResult.Success(InboxFixtures.page)}
            compose.onNodeWithText("Try again").performClick()
            val first=compose.onNodeWithTag("notification:${InboxFixtures.items.first().id}")
            first.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Unread"))
            first.performClick()
            first.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Read"))
            compose.runOnIdle {
                assertEquals(com.srisu.srisu.features.plans.navigation.PlansDestination(7),vm.state.value.navigation)
                vm.navigated()
            }
            compose.onNodeWithText("Mark read").performClick()
            compose.onNodeWithText("All notifications marked as read.").assertIsDisplayed()
            compose.onNodeWithText("Mark read").assertIsNotEnabled()
            compose.runOnIdle {source.result=NetworkAPIResult.Success(InboxFixtures.page.copy(items=emptyList(),unreadCount=0))}
            compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Unread"))
            compose.onNodeWithText("Unread",useUnmergedTree=true).performClick()
            compose.onNodeWithText("You’re all caught up").assertIsDisplayed()
        } finally {compose.runOnIdle {vm.active(false);vm.viewModelScope.cancel()}}
    }

    @Test @Config(qualifiers="w360dp-h640dp-xhdpi") fun longNamesThumbnailsAndLargeTextRemainScrollable() {
        val item=NotificationReferenceFixture.state.items.first().copy(thumbnail=true,
            segments=listOf(InboxSegment("Alexandra and Christopher",true),InboxSegment(" shared a moment from our afternoon together by the lake.")))
        show(NotificationReferenceFixture.state.copy(items=listOf(item)),scale=2f)
        compose.onNodeWithText("Mark read").assertIsDisplayed()
        val text=compose.onNodeWithText("Alexandra and Christopher",substring=true,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val thumbnail=compose.onNodeWithTag("notification-thumbnail:${item.id}",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Large text keeps the thumbnail below the message",thumbnail.top>=text.bottom)
        compose.onNodeWithTag("notification-list").performScrollToNode(hasText("Settings"))
        compose.onNodeWithText("Settings").performClick()
        assertEquals(listOf("settings"),actions)
        compose.onNodeWithTag("notification-list").performScrollToIndex(0)
        capture("qa-long-text")
    }
}
