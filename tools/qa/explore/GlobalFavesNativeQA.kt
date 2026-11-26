package com.srisu.srisu.qa

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.explore.presentation.*
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import srisu.composeapp.generated.resources.Res

/** Injected by the standalone QA build only; not part of a production source set.
 * Actual production composables in UIKit, with deterministic synthetic callbacks.
 * Network contracts are checked separately by ExploreTransportIntegrationTest.
 */
@OptIn(ExperimentalResourceApi::class)
fun globalFavesQaController(viewer:Boolean,large:Boolean,dark:Boolean,compact:Boolean) = ComposeUIViewController(configure={
    onFocusBehavior=OnFocusBehavior.DoNothing
}) {
    val density=LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density,if(large) 1.8f else density.fontScale)) {
        SriSuTheme(darkTheme=dark) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                Box(if(compact) Modifier.widthIn(max=320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                    if(viewer) NativeViewerFixture() else NativeExploreFixture()
                }
            }
        }
    }
}

private const val NOW="2026-11-26T06:00:00Z"
private const val EXPIRY="2026-11-27T06:00:00Z"

@OptIn(ExperimentalResourceApi::class)
@Composable private fun NativeExploreFixture() {
    val cards=remember {(1L..30L).map {id->ExploreCard(
        couple=ExploreCouple(id,"Maya & Kiran $id",listOf("fixture-avatar","fixture-avatar"),sharedInterests=listOf("Hiking")),
        preview=MomentPreview(id,"A slow morning together",listOf(MomentPhoto(id,"fixture-photo")),EXPIRY,createdAt=NOW),
        isFaved=true,activeCount=1)}}
    val feed=remember {ExploreFeed(cards,loaded=true,fresh=true,serverTime=NOW)}
    var state by remember {mutableStateOf(ExploreState(active=true,global=feed,faves=feed,savedTotal=30,
        interests=listOf(ExploreInterest(1,"Hiking","outdoors"),ExploreInterest(2,"Coffee","food")),
        library=SavedLibrary(cards.map {SavedCouple(it.couple.id,it.couple,true,true)},loaded=true,totalCount=30)))}
    ExploreContent(state,onSelect={state=state.copy(section=it)},onRefresh={},onMore={},onBroader={},
        onManage={state=state.copy(managing=it)},onLibrary={},onFave={_,_->},onRetryFave={},onMoment={},onProfile={},onPreferences={},
        loadImage={Res.readBytes("drawable/will.jpg")},onBack={},onQuery={state=state.copy(query=it)},
        onInterest={state=state.copy(interestId=it)},onYouTwo={})
}

@Composable private fun NativeViewerFixture() {
    var state by remember {mutableStateOf(MomentViewerState(
        moment=Moment(10,3,2,MomentPerson(2,"Maya"),"A slow morning, a new trail, and time for us.","public",
            publishedAt=NOW,expiresAt=EXPIRY,serverTime=NOW),loading=false,fresh=true,hasNext=true,hasPrevious=true))}
    var attempts by remember {mutableStateOf(0)}
    val scope=rememberCoroutineScope()
    PublicMomentViewerContent(state,onBack={},onRetry={},onMove={},onPresented={},onVideoPresented={},
        onNote={state=state.copy(note=it)},onSendNote={
            attempts++;state=state.copy(busy=true,error=null)
            scope.launch {delay(350);state=if(attempts==1) state.copy(busy=false,error="Connection interrupted. Try again.")
                else state.copy(busy=false,error=null,note="",noteSent=true)}
        },onNotes={state=state.copy(notes=emptyList())},onCloseNotes={state=state.copy(notes=null)},onProfile={},avatar={null})
}
