package com.srisu.srisu.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.home.dashboard.screen.HomeActions
import com.srisu.srisu.features.home.dashboard.screen.HomeContent
import com.srisu.srisu.features.home.dashboard.state.HomeUiState
import com.srisu.srisu.previews.HomePreviewData
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
@Config(sdk = [35], qualifiers = "w390dp-h1100dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRenderTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val events = mutableListOf<String>()
    private val actions = HomeActions(
        onNotifications = { events += "notifications" }, onCoupleProfile = { events += "profile" },
        onViewMoments = { c, p -> events += "moments:$c:$p" }, onCreateMoment = { events += "create:$it" },
        onChat = { events += "chat" }, onSpark = { events += "spark" }, onAsk = { events += "ask" },
        onPlanner = { events += "planner" }, onChallenge = { events += "challenge:$it" }, onPlan = { events += "plan:$it" },
        onFindPartner = { events += "find" }, onInvitations = { events += "invitations" },
    )
    private var retries = 0
    private val homeList = hasScrollToIndexAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun render(state: HomeUiState, name: String, dark: Boolean = false, largeText: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                SriSuTheme(darkTheme = dark) { HomeContent(state, { null }, { retries++ }, actions) }
            }
        }
        compose.waitForIdle()
        val output = File("build/reports/home/$name.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun contentLight() {
        render(HomePreviewData.content, "content-light")
        compose.onNodeWithText("Good evening, Salil").assertIsDisplayed()
        compose.onNodeWithText("Day 248 with Aayush").assertIsDisplayed()
        compose.onNodeWithText("From Aayush").assertIsDisplayed()
        compose.onNodeWithContentDescription("3 unread messages").assertIsDisplayed()
        compose.onNodeWithText("Reply to Aayush…").performClick()
        compose.onNodeWithText("Open chat").performClick()
        compose.onNodeWithText("Spark").performClick()
        compose.onNodeWithText("Ask").performClick()
        compose.onNodeWithText("Planner").performClick()
        compose.onNodeWithText("Day 248 with Aayush").performClick()
        compose.onNodeWithContentDescription("Notifications, new items to review").performClick()
        compose.onNodeWithContentDescription("Add a moment").performClick()
        compose.onNodeWithContentDescription("Aayush. 2 new moments").performClick()
        compose.onNodeWithText("Cook something new together").performClick()
        compose.onNode(homeList).performScrollToNode(hasText("Sunrise hike to Nagarkot"))
        compose.onNodeWithText("Sunrise hike to Nagarkot").performClick()
        compose.onNodeWithText("Sun 20 • 5:30 AM • Both going").assertExists()
        assertEquals(listOf("chat", "chat", "spark", "ask", "planner", "profile", "notifications", "create:10", "moments:10:2",
            "challenge:c1", "plan:7"), events)
    }

    @Test fun contentDark() = render(HomePreviewData.content, "content-dark", dark = true)

    @Test fun contentLargeText() {
        render(HomePreviewData.content, "content-large-text", largeText = true)
        compose.onNode(homeList).performScrollToNode(hasText("Planner"))
        compose.onNodeWithText("Planner").assertIsDisplayed()
    }

    @Test fun loading() = render(HomePreviewData.loading, "loading")

    @Test fun emptyOffersPartnerLinking() {
        render(HomePreviewData.empty, "empty")
        compose.onNodeWithText("Find your partner").performClick()
        compose.onNodeWithContentDescription("Notifications").performClick()
        assertEquals(listOf("find", "invitations"), events)
    }

    @Test fun sectionFailuresRetry() {
        render(HomePreviewData.sectionsEmpty, "sections-empty")
        compose.onNodeWithText("Pick up where you left off").assertIsDisplayed()
        compose.onNodeWithText("A little experience, together").performClick()
        compose.onNodeWithText("Plans couldn’t refresh").performScrollTo().performClick()
        assertEquals(listOf("challenge:null"), events)
        assertEquals(1, retries)
    }

    @Test fun failedRetries() {
        render(HomePreviewData.failed, "failed")
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }
}
