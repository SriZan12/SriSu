package com.srisu.srisu.features.explore

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.*
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.theme.SriSuTheme
import kotlin.test.*

/** Native iOS Compose runtime. Text input here uses semantics, not UIKit keyboard automation. */
@OptIn(ExperimentalTestApi::class)
class ExploreIosInteractionTest {
    @Test fun searchSelectedMomentAndLibraryReturn()=runSkikoComposeUiTest(size=Size(780f,1688f),density=Density(2f)) {
        val card=FakeExplore.card(10).copy(couple=ExploreCouple(10,"Maya & Kiran"),isFaved=true,
            previews=listOf(FakeExplore.card(10).preview.copy(id=101),FakeExplore.card(10).preview.copy(id=102)))
        var state by mutableStateOf(ExploreState(active=true,global=ExploreFeed(listOf(card),loaded=true,fresh=true),
            faves=ExploreFeed(listOf(card),loaded=true,fresh=true),savedTotal=1,library=SavedLibrary(listOf(SavedCouple(10,card.couple,true,true)),loaded=true)))
        val actions=mutableListOf<String>()
        setContent {SriSuTheme {ExploreContent(state,{state=state.copy(section=it)},{},{},{},{state=state.copy(managing=it)},{},
            {_,_->},{},{},{},{},{MomentFixtures.image},onBack={actions+="back"},onQuery={state=state.copy(query=it)},onOpenMoment={id,moment->actions+="$id:$moment"})}}
        onNodeWithText("Names, or what they love").performClick()
        onNodeWithTag("explore-search").assertIsFocused().performTextInput("Hiking")
        assertEquals("Hiking",state.query)
        onNodeWithTag("explore-search").performImeAction()
        onNodeWithContentDescription("Go back").performClick()
        onNodeWithText("Faves").performClick()
        onNodeWithTag("explore-selected-moment").performClick()
        assertEquals(listOf("10:101"),actions)
        onNodeWithContentDescription("Your Faves and followers").performClick()
        onNodeWithText("Your Faves · 1").assertIsDisplayed()
        onNodeWithContentDescription("Go back").performClick()
        onNodeWithTag("explore-selected-moment").assertExists()
        onNodeWithContentDescription("Go back").performClick()
        assertEquals("back",actions.last())
    }
    @Test fun landscapeAndLargeTextKeepEssentialActionsReachable()=runSkikoComposeUiTest(size=Size(1280f,640f),density=Density(2f,1.8f)) {
        var state by mutableStateOf(ExploreState(active=true,section="faves",faves=ExploreFeed(loaded=true,fresh=true,emptyReason="no_faves")))
        setContent {SriSuTheme(darkTheme=true) {ExploreContent(state,{state=state.copy(section=it)},{},{},{},{},{},{_,_->},{},{},{},{},{null},onBack={},onYouTwo={})}}
        onNodeWithText("Faves").assertIsDisplayed()
        onNodeWithTag("explore-list").performScrollToNode(hasText("Find couples"))
        onNodeWithText("Find couples").performClick()
        assertEquals("global",state.section)
    }
}
