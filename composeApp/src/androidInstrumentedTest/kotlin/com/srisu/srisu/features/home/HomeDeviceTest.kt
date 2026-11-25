package com.srisu.srisu.features.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import java.io.File
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.coupleprofile.data.CoupleStatuses
import com.srisu.srisu.features.home.dashboard.screen.*
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.plans.data.DateIdeas
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.navigation.navhost.*
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.features.settings.ApplyPlatformAppearance
import com.srisu.srisu.features.settings.AppAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import org.junit.Rule
import org.junit.Test

/** Installed Android rendering, scrolling, touch and system-back checks. No network/account fixture. */
class HomeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val darkMode = androidx.compose.runtime.mutableStateOf(false)
    private val events = mutableListOf<String>()
    private val me = HomePersonUi(1,"Srijan",null)
    private val partner = HomePersonUi(2,"Aatithi",null)
    private val question = SparkSession("11111111-1111-4111-8111-111111111111",10,1,
        "22222222-2222-4222-8222-222222222222","!synthetic:example.test",1,"deep-7-v1",
        "What made you feel close to me this week?","deep","open",1)
    private val paired = HomeUiState(HomeStatus.READY,name="Srijan",couple=HomeCoupleUi(
        10,partner,248,listOf(HomeMomentUi(me,true,0,0),HomeMomentUi(partner,false,0,0)),true,
        null,HomeSection.Empty,HomeSection.Empty,0,title="Lovebirds",account=1,
        question=HomeSection.Ready(SparkDaily(question,"2026-11-09","Asia/Kathmandu")),questionFresh=true,
        statuses=CoupleStatuses(10,serverTime="2026-11-09T06:00:00Z"),statusesFresh=true,
        ideas=HomeSection.Ready(DateIdeas(10)),ideasFresh=true,planFresh=true,
        questionsDone=0,plansDone=0,chatAvailability=MatrixChatAvailability.Ready(),unread=false,
        statusState=HomeStatus.READY,sharingRequest=HomeSection.Ready(false)))
    private val contentState = androidx.compose.runtime.mutableStateOf(paired)
    private fun show(state:HomeUiState=paired,scale:Float=1f,dark:Boolean=false,refreshOnRetry:Boolean=false) {
        darkMode.value=dark
        contentState.value=state
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val d=LocalDensity.current
            ApplyPlatformAppearance(if(darkMode.value) AppAppearance.DARK else AppAppearance.LIGHT,darkMode.value)
            CompositionLocalProvider(LocalDensity provides Density(d.density,scale)) {
                SriSuTheme(darkTheme=darkMode.value) {
                    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={AppBottomBar({},BottomDestination.HOME,true)}) {padding ->
                        HomeContent(contentState.value,{null},{events+="retry";if(refreshOnRetry) contentState.value=contentState.value.copy(refreshing=true)},HomeActions(
                            onCreateMoment={events+="moment"},onQuestion={events+="question"},
                            onChat={events+="chat"},onFindPartner={events+="find"}),Modifier.padding(padding).consumeWindowInsets(padding))
                    }
                }
            }
        }
    }
    private fun scroll(label:String)=compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
    private fun pullHome(expectedRefreshes:Int?=null) {
        compose.onNodeWithTag("home-pull-refresh").performTouchInput {
            swipeDown(startY=height*.15f,endY=height*.85f,durationMillis=650)
        }
        // Material awaits its release-to-threshold animation before calling onRefresh.
        compose.waitForIdle()
        if(expectedRefreshes != null) compose.waitUntil(5_000) {events.count {it=="retry"}>=expectedRefreshes}
    }

    private fun capture(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        // WindowManager's dialog fade runs outside the Compose test clock.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        val file=File(instrumentation.targetContext.filesDir,"home-device-$name.png")
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }

    @Test fun darkHomeAndStatusSheet() {
        show(dark=true);capture("dark")
        val pixels=compose.onRoot().captureToImage().toPixelMap()
        assertTrue("Home canvas follows the active dark theme", pixels[1,pixels.height/2].luminance()<.05f)
        scroll("Set status");compose.onNodeWithText("Set status").performClick()
        compose.onNodeWithText("Feeling cosy").assertIsDisplayed();capture("dark-status")
        compose.runOnIdle {
            fun windows(view: android.view.View): List<android.view.Window> =
                (if(view is androidx.compose.ui.window.DialogWindowProvider) listOf(view.window) else emptyList()) +
                (if(view is android.view.ViewGroup) (0 until view.childCount).flatMap {windows(view.getChildAt(it))} else emptyList())
            val dialogs=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap(::windows)
            assertTrue("The status sheet has its own native window", dialogs.isNotEmpty())
            dialogs.forEach {window ->
                val bars=androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
                assertTrue("Sheet status icons follow app dark mode",!bars.isAppearanceLightStatusBars)
                assertTrue("Sheet navigation icons follow app dark mode",!bars.isAppearanceLightNavigationBars)
            }
        }
    }
    @Test fun appearanceChangesUpdateHomeAndBottomNavigation() {
        show(dark=true)
        fun darkCanvas()=compose.onRoot().captureToImage().toPixelMap().let {it[1,it.height/2].luminance()<.05f}
        assertTrue(darkCanvas())
        compose.runOnIdle {darkMode.value=false};compose.waitForIdle();assertTrue(!darkCanvas())
        compose.runOnIdle {darkMode.value=true};compose.waitForIdle();assertTrue(darkCanvas())
        compose.onNodeWithText("Home").assertIsDisplayed()
    }
    @Test fun darkUnpaired() {show(HomeUiState(HomeStatus.READY,name="Alex"),dark=true);capture("dark-unpaired")}
    @Test fun pullRefreshHasFeedbackAndDoesNotReplaceTheLoadedHome() {
        show(dark=true,refreshOnRetry=true)
        compose.onNodeWithTag("home-refresh").assertDoesNotExist()
        pullHome(expectedRefreshes=1)
        assertEquals(listOf("retry"),events)
        compose.onNodeWithContentDescription("Refreshing Home").assertIsDisplayed()
        compose.onNodeWithText("You & Aatithi").assertIsDisplayed()
        pullHome()
        assertEquals("A gesture during refresh does not start another request",listOf("retry"),events)
        capture("refreshing")
        compose.runOnIdle {contentState.value=paired.copy(stale=true)}
        compose.onNodeWithContentDescription("Refreshing Home").assertDoesNotExist()
        scroll("Showing your last update. Home couldn’t refresh.")
        compose.onNodeWithText("Showing your last update. Home couldn’t refresh.").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        pullHome(expectedRefreshes=2)
        assertEquals(listOf("retry","retry"),events)
    }
    @Test fun pullRefreshWorksWithoutPartnerAndDoesNotRefreshInitialLoading() {
        show(HomeUiState(),dark=true,refreshOnRetry=true)
        pullHome()
        assertTrue(events.isEmpty())
        compose.runOnIdle {contentState.value=HomeUiState(HomeStatus.READY,name="Alex")}
        pullHome(expectedRefreshes=1)
        assertEquals(listOf("retry"),events)
        compose.onNodeWithContentDescription("Refreshing Home").assertIsDisplayed()
        compose.onNodeWithText("Find your partner").assertIsDisplayed()
        compose.onNodeWithText("Your story so far").assertDoesNotExist()
    }
    @Test fun downwardScrollInTheMiddleDoesNotRefreshHome() {
        show()
        scroll("Plans done")
        compose.onNodeWithTag("home-pull-refresh").performTouchInput {
            swipeDown(startY=height*.4f,endY=height*.6f,durationMillis=500)
        }
        compose.waitForIdle()
        assertTrue("Scroll back through content before pulling at the top",events.isEmpty())
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        pullHome(expectedRefreshes=1)
        assertEquals(listOf("retry"),events)
    }
    @Test fun independentActionsAndStatusSheetSystemBack() {
        show();capture("paired");compose.onNodeWithContentDescription("Add a Moment").performClick()
        scroll("Go first");compose.onNodeWithText("Go first").performClick()
        scroll("Open chat →");compose.onNodeWithText("Open chat →").performClick()
        assertEquals(listOf("moment","question","chat"),events)
        scroll("Set status");compose.onNodeWithText("Set status").performClick()
        compose.onNodeWithText("Feeling cosy").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);compose.onNodeWithText("Feeling cosy").assertDoesNotExist()
        compose.onNodeWithText("Set status").assertIsDisplayed()
    }
    @Test fun twoHundredPercentTextCanReachTheBottom() {
        show(scale=2f)
        pullHome(expectedRefreshes=1)
        assertEquals(listOf("retry"),events)
        scroll("Go first");compose.onNodeWithText("Go first").assertIsDisplayed()
        scroll("Plans done");compose.onNodeWithText("Plans done").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed();compose.onNodeWithText("Profile").assertIsDisplayed();capture("large-text-bottom")
    }
    @Test fun unpairedDoesNotInventPartnerActivity() {
        show(HomeUiState(HomeStatus.READY,name="Srijan"))
        compose.onNodeWithTag("home-pull-refresh").assertIsDisplayed()
        compose.onNodeWithTag("home-refresh").assertDoesNotExist()
        capture("unpaired");compose.onNodeWithText("Find your partner").performClick()
        compose.onNodeWithText("Your story so far").assertDoesNotExist()
        compose.onNodeWithText("Go first").assertDoesNotExist();assertEquals(listOf("find"),events)
    }
    @Test fun failureOffersRetryRatherThanFalseEmptyState() {
        show(HomeUiState(HomeStatus.FAILED));compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Find your partner").assertDoesNotExist();assertEquals(listOf("retry"),events)
    }
}
