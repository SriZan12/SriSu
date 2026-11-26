package com.srisu.srisu.features.moments

import androidx.compose.runtime.*
import com.srisu.srisu.features.moments.presentation.MomentViewerState
import com.srisu.srisu.features.moments.presentation.PublicMomentViewerContent
import org.jetbrains.compose.resources.ExperimentalResourceApi
import srisu.composeapp.generated.resources.Res

/** Synthetic snapshots of production public chrome; never wired into the app. */
object PublicMomentUiFixture {
    const val caption="A slow morning, a new trail, and time for us."
    fun state(textOnly:Boolean=false)=MomentViewerState(moment=MomentFixtures.partner.copy(
        visibility="public",canReply=false,canEdit=false,canDelete=false,caption=caption,publishedAt="2026-10-22T04:00:00Z",
        creator=MomentFixtures.partner.creator.copy(name="Maya",photoUrl="fixture-avatar"),
        photos=if(textOnly) emptyList() else MomentFixtures.partner.photos),
        loading=false,fresh=true,hasPrevious=true,hasNext=true)
}

@OptIn(ExperimentalResourceApi::class)
@Composable internal fun PublicMomentUiContent(state:MomentViewerState,onState:(MomentViewerState)->Unit,
    onAction:(String)->Unit={}) {
    val bytes by produceState<ByteArray?>(null) {value=Res.readBytes("drawable/will.jpg")}
    PublicMomentViewerContent(state.copy(bytes=if(state.fresh) bytes else null,
        imageLoading=state.fresh && state.moment?.photos?.isNotEmpty()==true && bytes==null),
        onBack={onAction("back")},onRetry={onAction("retry")},onMove={onAction("move:$it")},
        onPresented={onAction("presented")},onVideoPresented={onAction("video:$it")},
        onNote={onState(state.copy(note=it))},onSendNote={onAction("send")},onNotes={onAction("notes:$it")},
        onCloseNotes={onAction("close-notes")},onProfile={onAction("profile:$it")},avatar={bytes})
}
