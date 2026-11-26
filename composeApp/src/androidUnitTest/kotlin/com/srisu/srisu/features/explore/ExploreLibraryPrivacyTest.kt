package com.srisu.srisu.features.explore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
class ExploreLibraryPrivacyTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun savedCoupleReauthorizationHidesIdentityWithoutLosingScroll()=checkRefresh(false)
    @Test fun followerReauthorizationHidesIdentityWithoutLosingScroll()=checkRefresh(true)

    private fun checkRefresh(followers:Boolean) {
        var state by mutableStateOf(ExploreState(active=true,managing=true,followersOpen=followers,
            library=SavedLibrary((1L..40).map {SavedCouple(it,ExploreCouple(it,"Private fixture $it",listOf("fixture/$it")),true,true)},loaded=true),
            followers=FollowersState((1L..40).map {PublicFollower(it,it,"Private fixture $it","fixture/$it","2026-11-25T00:00:00Z")},loaded=true)))
        lateinit var libraryScroll:LazyListState
        lateinit var followersScroll:LazyListState
        lateinit var scope:CoroutineScope
        var actions=0
        var images=0
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            libraryScroll=rememberLazyListState();followersScroll=rememberLazyListState();scope=rememberCoroutineScope()
            SriSuTheme {Column(Modifier.fillMaxSize()) {
                ExploreLibrary(state,{},{},{},{},{},{},{actions++},{actions++},{_,_->actions++},{actions++},
                    {images++;null},libraryScroll,followersScroll)
            }}
        }
        val scroll=if(followers) followersScroll else libraryScroll
        compose.runOnIdle {scope.launch {scroll.scrollToItem(20,12)}}
        compose.waitForIdle()
        val index=scroll.firstVisibleItemIndex
        val offset=scroll.firstVisibleItemScrollOffset
        val visibleNode=compose.onNodeWithText("Private fixture 21")
        visibleNode.assertIsDisplayed()
        val center=visibleNode.fetchSemanticsNode().boundsInRoot.center
        compose.runOnIdle {
            images=0
            state=state.copy(library=state.library.copy(loaded=false,loading=true),followers=state.followers.copy(loaded=false,loading=true))
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Private fixture",substring=true).assertCountEquals(0)
        compose.onRoot().performTouchInput {click(center)}
        compose.runOnIdle {assertEquals(0,actions);assertEquals(0,images)}
        compose.runOnIdle {state=state.copy(library=state.library.copy(loaded=true,loading=false),followers=state.followers.copy(loaded=true,loading=false))}
        compose.waitForIdle()
        assertEquals(index,scroll.firstVisibleItemIndex)
        assertEquals(offset,scroll.firstVisibleItemScrollOffset)
        compose.onNodeWithText("Private fixture 21").assertIsDisplayed()
    }
}
