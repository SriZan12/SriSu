package com.srisu.srisu.identity

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.identity.LocalPartnerNames
import com.srisu.srisu.core.identity.PartnerNameSnapshot
import com.srisu.srisu.features.chat.ChatFixtureRepository
import com.srisu.srisu.features.chat.ChatFixtures
import com.srisu.srisu.features.chat.FixedPartnerSource
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatScreen
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.features.home.HomeRenderFixture
import com.srisu.srisu.features.notifications.InboxItem
import com.srisu.srisu.features.notifications.InboxSegment
import com.srisu.srisu.features.notifications.InboxState
import com.srisu.srisu.features.notifications.NotificationInboxContent
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerNicknameUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun names(nickname: String? = "Sunshine") = PartnerNameSnapshot(
        accountId=1, coupleId=10, partnerId=2, connectionId=3, nickname=nickname, revision=1)

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun homeUpdatesImmediatelyWithoutRewritingTheUnderlyingContent() {
        val nickname=mutableStateOf(names())
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalPartnerNames provides nickname.value) {
                SriSuTheme { HomeRenderFixture("spark") }
            }
        }
        compose.onNodeWithText("You & Sunshine").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("From Sunshine"))
        compose.onNodeWithText("From Sunshine").assertIsDisplayed()
        compose.runOnIdle { nickname.value=names(null) }
        compose.onNodeWithText("From Aatithi").assertIsDisplayed()
        compose.onNodeWithText("From Sunshine").assertDoesNotExist()
    }

    @Test fun inboxResolvesOnlyTaggedPersonSpansAndClearsToCanonicalName() {
        val nickname=mutableStateOf(names())
        val item=InboxItem(id="11111111-1111-4111-8111-111111111111",kind="plan.invited",category="plans",
            title="Old alias invited you",body="Aatithi's cafe",createdAt="2026-11-25T08:00:00Z",available=true,
            actorName="Old alias",actorId=2,actorRealName="Aatithi",
            segments=listOf(InboxSegment("Old alias",true,2,"Aatithi"),InboxSegment(" suggested Aatithi’s cafe.")))
        compose.setContent {
            CompositionLocalProvider(LocalPartnerNames provides nickname.value) {
                SriSuTheme { NotificationInboxContent(InboxState(items=listOf(item),loading=false,loaded=true),
                    now=Instant.parse("2026-11-25T09:00:00Z"),zone=TimeZone.UTC) }
            }
        }
        compose.onNodeWithText("Sunshine suggested Aatithi’s cafe.").assertIsDisplayed()
        compose.runOnIdle { nickname.value=names(null) }
        compose.onNodeWithText("Aatithi suggested Aatithi’s cafe.").assertIsDisplayed()
        compose.onNodeWithText("Old alias",substring=true).assertDoesNotExist()
    }

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun chatHeaderAndComposerFollowNicknameEditsWithoutChangingTheRoom() {
        val nickname=mutableStateOf(names())
        val store=ViewModelStore()
        val vm=SecureChatViewModel(ChatFixtureRepository(ChatFixtures.empty()),
            FixedPartnerSource(ChatPartnerIdentity("Aatithi",null,2)))
        store.put("chat",vm)
        vm.openRoom(ChatFixtures.ROOM_ID)
        try {
            compose.setContent {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                    org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
                }
                CompositionLocalProvider(LocalPartnerNames provides nickname.value) {
                    SriSuTheme { SecureChatScreen(vm,{}) }
                }
            }
            compose.onNodeWithText("Sunshine").assertIsDisplayed()
            compose.onNodeWithText("Message Sunshine").assertIsDisplayed()
            compose.runOnIdle { nickname.value=names("Moon 🌙") }
            compose.onNodeWithText("Moon 🌙").assertIsDisplayed()
            compose.onNodeWithText("Message Moon 🌙").assertIsDisplayed()
            compose.runOnIdle { nickname.value=names(null) }
            compose.onNodeWithText("Aatithi").assertIsDisplayed()
            compose.onNodeWithText("Message Aatithi").assertIsDisplayed()
        } finally { compose.runOnUiThread {store.clear()} }
    }
}
