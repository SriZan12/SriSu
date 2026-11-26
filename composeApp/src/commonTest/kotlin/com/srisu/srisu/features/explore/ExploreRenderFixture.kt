package com.srisu.srisu.features.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.*
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.navigation.navhost.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import srisu.composeapp.generated.resources.Res

/** Synthetic render data only; never injected into the production application. */
@OptIn(ExperimentalResourceApi::class)
@Composable fun ExploreRenderFixture(name:String) {
    val faved=name.startsWith("faves") || name=="manage" || name=="no-active"
    val card=FakeExplore.card(10).copy(preview=FakeExplore.card(10).preview.copy(
        caption="A slow morning, a new trail, and time for the two of us.",photos=listOf(MomentFixtures.photo),createdAt="2026-10-22T04:00:00Z"),
        couple=ExploreCouple(10,"Maya & Kiran",listOf("avatar1","avatar2"),sharedInterests=listOf("Hiking", "Sunrises")),
        previews=(1L..3).map {FakeExplore.card(10).preview.copy(id=it+100,caption=listOf("City day out","Worth the climb","Morning light")[(it-1).toInt()],photos=listOf(MomentFixtures.photo),createdAt="2026-10-22T04:00:00Z")},
        isFaved=faved,reason="From your selected city",hasUnopened=name!="caught-up")
    val feed=ExploreFeed(listOf(card),loaded=true,fresh=true,coverage="local",serverTime=MomentFixtures.NOW)
    val selected=when(name) {
        "loading"->ExploreFeed(loading=true)
        "error"->ExploreFeed(error="Unable to connect. Check your connection.")
        "faves-empty"->feed.copy(cards=emptyList(),emptyReason="no_faves")
        "no-active"->feed.copy(cards=emptyList(),emptyReason="no_active_fave_moments")
        else->feed
    }
    var state by remember {mutableStateOf(ExploreState(section=if(faved) "faves" else "global",active=true,
        interests=listOf(ExploreInterest(1,"Hiking","outdoors"),ExploreInterest(2,"Momo","food"),ExploreInterest(3,"Sunrises","outdoors")),savedTotal=2,
        global=if(faved) feed else selected,faves=if(faved) selected else feed,
        managing=name=="manage",library=SavedLibrary(listOf(SavedCouple(10,card.couple,true,false),SavedCouple(11,available=false)),loaded=true),
        preferences=when(name) {
            "saving"->mapOf(10L to FaveStatus(false,desired=true,pending=true))
            "retry"->mapOf(10L to FaveStatus(false,desired=true,error="Couldn’t save your Fave. Please retry."))
            else->mapOf(10L to FaveStatus(faved))
        }))}
    Scaffold(bottomBar={AppBottomBar({},null,false)}) {padding->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ExploreContent(state,{state=state.copy(section=it)},{},{},{},{state=state.copy(managing=it)},{},
                {id,value->state=state.copy(preferences=state.preferences+(id to FaveStatus(value)))},{},{},{},{},{Res.readBytes("drawable/will.jpg")},onBack={},onQuery={state=state.copy(query=it)},onYouTwo={})
        }
    }
}
