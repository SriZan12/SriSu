package com.srisu.srisu.features.explore

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.*
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import srisu.composeapp.generated.resources.Res
import java.io.File

/** Real Android input/IME and scrolling, synthetic accounts. Never touches the user's session. */
class ExploreDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val actions=mutableListOf<String>()
    private val cards=(1L..30L).map {id->
        val previews=(1L..3L).map {n->MomentPreview(id*100+n,if(n==1L) "City day out" else "A little time together",
            photos=listOf(MomentPhoto(id*100+n,"fixture",width=900,height=1200)),expiresAt="2026-11-26T12:00:00Z")}
        ExploreCard(ExploreCouple(id,"Maya & Kiran $id",listOf("one","two"),sharedInterests=listOf("Hiking")),previews.first(),previews=previews,hasUnopened=true)
    }
    private var state by mutableStateOf(ExploreState(active=true,global=ExploreFeed(cards,loaded=true,fresh=true),
        faves=ExploreFeed(cards.take(2).map {it.copy(isFaved=true)},loaded=true,fresh=true),
        interests=listOf(ExploreInterest(1,"Hiking","outdoors"),ExploreInterest(2,"Sunrises","outdoors")),
        library=SavedLibrary(listOf(SavedCouple(1,cards.first().couple,true,true)),loaded=true),savedTotal=1))
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun show(scale:Float=1f,dark:Boolean=false,width:Int?=null) {
        compose.activityRule.scenario.onActivity {it.enableEdgeToEdge()}
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                SriSuTheme(darkTheme=dark) {
                    Box(if(width!=null) Modifier.width(width.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        ExploreContent(state,onSelect={state=state.copy(section=it)},onRefresh={actions+="refresh"},onMore={},onBroader={},
                            onManage={state=state.copy(managing=it)},onLibrary={},onFave={id,value->actions+="fave:$id:$value";state=state.copy(preferences=state.preferences+(id to FaveStatus(value)))},
                            onRetryFave={},onMoment={actions+="sequence:$it"},onProfile={actions+="profile:$it"},onPreferences={},loadImage={Res.readBytes("drawable/will.jpg")},
                            onBack={actions+="back"},onQuery={state=state.copy(query=it)},onInterest={state=state.copy(interestId=it)},
                            onOpenMoment={id,moment->actions+="moment:$id:$moment"},onYouTwo={actions+="home"})
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir,"explore-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun searchKeyboardSelectedMomentAndBack() {
        show();capture("discover")
        compose.onNodeWithText("Names, or what they love").performClick()
        compose.onNodeWithTag("explore-search").assertIsFocused().performTextInput("Hiking")
        compose.onNodeWithTag("explore-search").assertTextContains("Hiking");capture("keyboard")
        compose.onNodeWithTag("explore-search").performImeAction()
        compose.onNodeWithContentDescription("Go back").performClick()
        compose.onNodeWithText("Faves").performClick();capture("faves")
        compose.onNodeWithTag("explore-selected-moment").performClick()
        assertTrue(actions.contains("moment:1:101"))
        compose.onNodeWithContentDescription("Your Faves and followers").performClick()
        compose.onNodeWithText("Your Faves · 1").assertIsDisplayed();capture("library")
        compose.onNodeWithContentDescription("Go back").performClick()
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(actions.contains("back"))
    }
    @Test fun scrollAndManualRefresh() {
        show()
        compose.onNodeWithTag("explore-list").performScrollToNode(hasText("Maya & Kiran 30"))
        compose.onNodeWithText("Maya & Kiran 30").performClick();assertTrue(actions.contains("profile:30"))
        compose.onNodeWithTag("explore-list").performScrollToIndex(0)
        assertFalse(actions.contains("refresh"))
        compose.onNodeWithTag("explore-pull-refresh").performTouchInput {swipeDown(startY=height*.15f,endY=height*.85f,durationMillis=650)}
        compose.waitUntil(5000) {actions.contains("refresh")}
    }
    @Test fun darkSmallLargeTextEmpty() {
        show(scale=1.8f,dark=true,width=320);capture("dark-small-large")
        compose.onNodeWithText("Faves").performClick()
        compose.runOnIdle {state=state.copy(faves=ExploreFeed(loaded=true,fresh=true,emptyReason="no_faves"))}
        compose.onNodeWithText("Keep your favourites close").assertIsDisplayed()
        compose.onNodeWithText("Find couples").performClick();capture("dark-discover")
        assertEquals("global",state.section)
    }
}
