package com.srisu.srisu.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.home.HomeRenderFixture
import com.srisu.srisu.features.home.dashboard.screen.HomeActions
import com.srisu.srisu.features.home.dashboard.screen.HomeInteractions
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRenderTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val events = mutableListOf<String>()
    private val actions = HomeActions(onNotifications = { events += "requests" }, onCoupleProfile = { events += "profile" },
        onViewMoments = { c, p -> events += "moments:$c:$p" }, onCreateMoment = { events += "create:$it" },
        onChat = { events += "chat" }, onAsk = { events += "ask" }, onPlanner = { events += "planner" },
        onChallenge = { events += "challenge:$it" }, onPlan = { events += "plan:$it" },
        onFindPartner = { events += "find" }, onInvitations = { events += "invitations" },
        onQuestion = { events += "question:$it" }, onDraft = { events += "draft:$it" }, onChatMessage = { events += "message:$it" })
    private fun render(name: String, scale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                SriSuTheme(darkTheme = false) { HomeRenderFixture(name, actions,
                    HomeInteractions(suggest = { events += "idea:$it" }), { events += "retry" }) }
            }
        }
        compose.waitForIdle(); capture("$name-${scale}-${compose.activity.resources.configuration.screenWidthDp}-top")
    }
    private fun scroll(label: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        return compose.onNodeWithText(label)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val output = File("build/reports/home/$name.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun sparkHeroAndMomentsHaveIndependentDestinations() {
        render("spark")
        compose.onNodeWithText("You & Aatithi").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add a Moment").performClick()
        scroll("Answer to reveal").performClick()
        scroll("Day 248").performClick()
        assertEquals(listOf("create:10", "question:11111111-1111-4111-8111-111111111111", "profile"), events)
        scroll("Your story so far"); capture("spark-bottom")
    }
    @Test fun latestMessageOpensItsMessageAndAskIsSeparate() {
        render("message")
        scroll("found the trail we talked about…").performClick()
        scroll("Ask").performClick()
        assertEquals(listOf("message:message", "ask"), events)
    }
    @Test fun quietChipsAreDraftsAndIdeasAreSeparateMutations() {
        render("quiet")
        scroll("👋 Thinking of you").performClick()
        scroll("☕ Coffee").performClick()
        assertEquals(listOf("draft:thinking", "idea:coffee"), events)
        capture("quiet-bottom")
    }
    @Test fun unpairedHasNoFictionalQuestionOrStatistics() {
        render("unpaired")
        scroll("Find your partner").performClick()
        compose.onNodeWithText("Your story so far").assertDoesNotExist()
        compose.onNodeWithText("Answer to reveal").assertDoesNotExist()
        assertEquals(listOf("find"), events)
    }
    @Test fun sentInvitationOpensExistingReview() {
        render("sent"); scroll("Review invitation").performClick()
        assertEquals(listOf("invitations"), events)
    }
    @Test fun receivedInvitation() = render("received")
    @Test fun newlyConnected() = render("new")
    @Test fun loadingNeverClaimsCaughtUp() {
        render("loading"); compose.onNodeWithText("You’re all caught up").assertDoesNotExist()
    }
    @Test fun failureRetriesInsteadOfOfferingToReconnect() {
        render("failed"); compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Find your partner").assertDoesNotExist(); assertEquals(listOf("retry"), events)
    }
    @Test fun failedSectionsNeverClaimEmpty() {
        render("sections-failed"); compose.onNodeWithText("Your question couldn’t refresh.").assertExists()
        compose.onNodeWithText("Nothing planned yet").assertDoesNotExist()
    }
    @Test @Config(qualifiers = "w320dp-h700dp-xhdpi") fun compactHome() {
        render("spark"); scroll("Answer to reveal").assertIsDisplayed()
        capture("compact-question"); scroll("Your story so far"); capture("compact-bottom")
    }
    @Test @Config(qualifiers = "w320dp-h700dp-xhdpi") fun compactAtTwoHundredPercent() {
        render("quiet", 2f)
        scroll("Go first").assertIsDisplayed(); capture("large-question")
        scroll("Plan a date").assertIsDisplayed(); capture("large-actions")
        scroll("Plans done").assertIsDisplayed(); capture("large-bottom")
    }
}
