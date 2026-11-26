package com.srisu.srisu.features.moments.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.NavigationBackButton
import com.srisu.srisu.components.DraftOutlinedTextField
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.theme.*

/** Public visitor chrome. Media, authorization, expiry and notes still use the shared Moment VM. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun PublicMomentViewerContent(state:MomentViewerState,onBack:()->Unit,onRetry:()->Unit,onMove:(Int)->Unit,
    onPresented:()->Unit,onVideoPresented:(Long)->Unit,onNote:(String)->Unit,onSendNote:()->Unit,onNotes:(Boolean)->Unit,onCloseNotes:()->Unit,
    onProfile:(Long)->Unit,avatar:suspend(String)->ByteArray?,actions:@Composable (Long)->Unit={}) {
    val moment=state.moment ?: return
    val colors=MaterialTheme.colorScheme;val space=MaterialTheme.spacing
    val photo=moment.photos.getOrNull(state.photo)
    val video=photo?.video
    var foreground by remember {mutableStateOf(true)}
    MomentLifecycle {foreground=it}
    val motion=rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    var playing by remember(moment.id,state.photo) {mutableStateOf(motion>0f)}
    var muted by remember {mutableStateOf(true)}
    var firstFrame by remember(moment.id,state.photo,state.playback) {mutableStateOf(false)}
    var failedImage by remember(moment.id,state.photo,state.bytes) {mutableStateOf(false)}
    var event by remember(moment.id,state.photo,state.playback) {mutableStateOf(MomentPlaybackEvent("buffering"))}
    var writing by remember(moment.id) {mutableStateOf(false)}
    LaunchedEffect(state.fresh,foreground) {if(!state.fresh || !foreground) writing=false}
    LaunchedEffect(state.noteSent) {if(state.noteSent) writing=false}
    LaunchedEffect(state.fresh,foreground,moment.id) {if(state.fresh && foreground && moment.photos.isEmpty()) onPresented()}
    Surface(color=ExploreTokens.canvas(colors),modifier=Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                NavigationBackButton(onBack)
                Text("Public Moment",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Icon(Icons.Outlined.Public,"Shared publicly",Modifier.padding(end=space.gutter))
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=space.gutter,vertical=space.small),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(space.medium)) {
                if(state.fresh && foreground) {
                    Box(Modifier.widthIn(max=ExploreTokens.maxWidth).fillMaxWidth().padding(space.small)) {
                        Surface(Modifier.matchParentSize().graphicsLayer {rotationZ=2f},shape=MaterialTheme.shapes.small,color=colors.surfaceContainerHigh,shadowElevation=2.dp) {}
                        Surface(shape=MaterialTheme.shapes.small,color=ExploreTokens.paper(colors),shadowElevation=3.dp) {
                            Column(Modifier.padding(space.small),verticalArrangement=Arrangement.spacedBy(space.compact)) {
                                if(photo!=null) Box(Modifier.fillMaxWidth().aspectRatio(.85f).background(colors.mediaBackground)) {
                                    if(video!=null && state.playback!=null) {
                                        MomentVideoPlayer(state.playback,playing && foreground && state.fresh && !writing && state.notes==null,muted,Modifier.fillMaxSize(),embedded=true) {next->
                                            event=next
                                            if(next.firstFrame && !firstFrame) {firstFrame=true;onVideoPresented(photo.id)}
                                            if(next.state=="ended") playing=false
                                        }
                                        if(!firstFrame && state.bytes!=null) MomentImage(state.bytes,null,Modifier.fillMaxSize(),ContentScale.Fit)
                                    } else if(video==null && state.bytes!=null) MomentImage(state.bytes,"Public Moment photo",Modifier.fillMaxSize(),ContentScale.Fit,onPresented,{failedImage=true})
                                    if(state.imageLoading || video!=null && event.state=="buffering" && state.playback!=null) CircularProgressIndicator(Modifier.align(Alignment.Center),color=colors.onMedia)
                                    if(video!=null) Row(Modifier.align(Alignment.BottomEnd).padding(space.small)) {
                                        Surface(shape=MaterialTheme.shapes.pill,color=colors.mediaBackground.copy(alpha=.75f),contentColor=colors.onMedia) {
                                            Row {
                                                IconButton(onClick={playing=!playing}) {Icon(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing) "Pause video" else "Play video")}
                                                IconButton(onClick={muted=!muted}) {Icon(if(muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,if(muted) "Turn sound on" else "Mute video")}
                                            }
                                        }
                                    }
                                }
                                if(moment.caption.isNotBlank()) Text(moment.caption,style=ExploreTokens.editorial())
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                                    com.srisu.srisu.features.explore.presentation.ExploreAvatar(moment.creator.photoUrl,avatar,null,Modifier.size(28.dp).clip(CircleShape))
                                    Text(moment.creator.name,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                    minutesBetween(moment.publishedAt,moment.serverTime)?.let {minutes->
                                        Text(ageLabel(minutes),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                    }
                                    Text("${state.photo+1} / ${moment.photos.size.coerceAtLeast(1)}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(Modifier.widthIn(max=ExploreTokens.maxWidth).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={onMove(-1)},enabled=state.hasPrevious) {Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,"Previous Moment")}
                        TextButton(onClick={onProfile(moment.couple)}) {Text("Their page →")}
                        IconButton(onClick={onMove(1)},enabled=state.hasNext) {Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,"Next Moment")}
                    }
                    actions(moment.couple)
                    if(failedImage || event.state=="failed") MomentError("This Moment couldn’t load. Please try again.",onRetry)
                    state.error?.let {MomentError(it,onRetry)}
                    if(state.noteSent) Text("Your appreciation was sent",style=MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(space.small),verticalArrangement=Arrangement.spacedBy(space.small)) {
                        TextButton(onClick={writing=true}) {Text("Send appreciation")}
                        TextButton(onClick={onNotes(false)}) {Text("Your appreciations")}
                    }
                } else {
                    if(state.loading) CircularProgressIndicator()
                    state.error?.let {MomentError(it,onRetry)}
                    if(state.unavailable!=null) {Text("This Moment is no longer available",style=MaterialTheme.typography.titleMedium);TextButton(onClick=onBack) {Text("Back to couples")}}
                }
            }
        }
    }
    if(writing && state.fresh && foreground) ModalBottomSheet(onDismissRequest={writing=false},
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(space.gutter),verticalArrangement=Arrangement.spacedBy(space.compact)) {
            Text("Send a little appreciation",style=MaterialTheme.typography.titleMedium)
            Text("Only this couple can see your note.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            DraftOutlinedTextField(state.note,onNote,modifier=Modifier.fillMaxWidth(),label={Text("Your note")},maxLines=5,enabled=!state.busy)
            state.error?.let {MomentError(it)}
            Button(onClick=onSendNote,enabled=!state.busy && state.note.isNotBlank()) {Text(if(state.busy) "Sending…" else "Send appreciation")}
        }
    }
    if(state.notes!=null && state.fresh && foreground) ModalBottomSheet(onDismissRequest=onCloseNotes,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            item {Text("Your appreciations",style=MaterialTheme.typography.titleMedium)}
            if(state.notesLoading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let {item {MomentError(it,{onNotes(false)})}}
            if(state.notes.isEmpty() && !state.notesLoading) item {Text("No notes yet. Send a little appreciation.",style=MaterialTheme.typography.bodyMedium)}
            items(state.notes.size,key={state.notes[it].id}) {index->
                val note=state.notes[index]
                Column(verticalArrangement=Arrangement.spacedBy(space.small)) {
                    Text(note.message,style=MaterialTheme.typography.bodyMedium)
                    note.replies.forEach {Text(it.message,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
                    HorizontalDivider()
                }
            }
            if(state.notesNext) item {TextButton(onClick={onNotes(true)},enabled=!state.notesLoading) {Text("More notes")}}
        }
    }
}
