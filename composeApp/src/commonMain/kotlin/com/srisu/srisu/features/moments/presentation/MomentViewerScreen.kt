package com.srisu.srisu.features.moments.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.moments.data.MomentReference
import com.srisu.srisu.theme.*
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable fun MomentViewerScreen(momentId: Long?,coupleId: Long?,creatorId: Long?,onBack: ()->Unit,
    onReply: (MomentReference)->Unit, vm: MomentViewerViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(momentId,coupleId,creatorId) { vm.open(momentId,coupleId,creatorId) }
    MomentLifecycle(vm::active)
    MomentViewerContent(state,onBack,vm::refresh,vm::move,vm::presented,vm::react,vm::editCaption,vm::saveCaption,vm::delete,
        vm::note,vm::sendNote,onReply, onNotes=vm::loadNotes,onCloseNotes=vm::closeNotes,onNoteReply=vm::replyToNote,onSendNoteReply=vm::sendNoteReply)
}

@OptIn(kotlin.time.ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable internal fun MomentViewerContent(state: MomentViewerState,onBack: ()->Unit,onRetry: ()->Unit,
    onMove: (Int)->Unit,onPresented: ()->Unit,onReact: ()->Unit,onEdit: (String?)->Unit,onSave: ()->Unit,
    onDelete: ()->Unit,onNote: (String)->Unit,onSendNote: ()->Unit,onReply: (MomentReference)->Unit,
    onNotes: (Boolean)->Unit={},onCloseNotes: ()->Unit={},onNoteReply: (Long?,String)->Unit={_,_->},onSendNoteReply: ()->Unit={}) {
    val colors=MaterialTheme.colorScheme
    val space=MaterialTheme.spacing
    val moment=state.moment
    var pixelsPresented by remember(moment?.id,state.photo,state.bytes) { mutableStateOf(false) }
    var decodeFailed by remember(moment?.id,state.photo,state.bytes) { mutableStateOf(false) }
    val captionScroll=rememberScrollState()
    var paused by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var inForeground by remember { mutableStateOf(true) }
    MomentLifecycle { inForeground=it }
    val motion=rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val progress=remember(moment?.id,state.photo) { Animatable(0f) }
    val running=state.fresh && !state.loading && !state.imageLoading && (moment?.photos.isNullOrEmpty() || pixelsPresented) &&
        !paused && !held && !captionScroll.isScrollInProgress && state.notes==null && !options && !confirmDelete && state.captionDraft==null && !state.busy && state.note.isBlank() && inForeground && motion>0f
    val latestMove by rememberUpdatedState(onMove)
    LaunchedEffect(running,moment?.id,state.photo) {
        if(running && moment!=null) {
            progress.animateTo(1f,tween(((if(moment.caption.length>180) 12000 else 7000)*(1f-progress.value)).toInt(),easing=LinearEasing))
            if(state.hasNext) latestMove(1)
        }
    }
    LaunchedEffect(moment?.id,state.fresh,inForeground) {
        if(state.fresh && inForeground && moment?.photos?.isEmpty()==true) onPresented()
    }
    Surface(color=colors.mediaBackground,contentColor=colors.onMedia,modifier=Modifier.fillMaxSize()) {
        if(state.unavailable!=null || (state.error!=null && !state.fresh)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(space.large),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ImageNotSupported,null,Modifier.size(space.huge))
                Spacer(Modifier.height(space.large))
                Text(when(state.unavailable) { "expired"->"This Moment has expired";"deleted"->"Moment deleted";"empty"->"No active Moments";else->"Moment unavailable" },style=MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(space.compact))
                Text(state.error ?: if(state.unavailable=="expired") "Moments are visible for 24 hours." else "It may have been removed, or you may no longer have access.",style=MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(space.large))
                if(state.error!=null) Button(onClick=onRetry) {Text("Try again")}
                TextButton(onClick=onBack) {Text("Back",color=colors.onMedia)}
            }
        } else Box(Modifier.fillMaxSize()) {
            if(state.fresh && state.bytes!=null) MomentImage(state.bytes,"Moment photo ${state.photo+1}",Modifier.fillMaxSize()
                .pointerInput(moment?.id,state.photo) { detectTapGestures(onPress={ held=true; try { tryAwaitRelease() } finally {held=false} },
                    onTap={ offset -> onMove(if(offset.x<size.width/2) -1 else 1) }) },ContentScale.Fit,{pixelsPresented=true;onPresented()},{decodeFailed=true})
            if(state.loading || state.imageLoading) CircularProgressIndicator(Modifier.align(Alignment.Center),color=colors.onMedia)
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),verticalArrangement=Arrangement.SpaceBetween) {
                Surface(color=colors.mediaBackground.copy(alpha=.82f),contentColor=colors.onMedia) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=space.compact,vertical=space.small),verticalArrangement=Arrangement.spacedBy(space.tiny)) {
                        if(moment!=null) Row(horizontalArrangement=Arrangement.spacedBy(space.tiny)) {
                            repeat(moment.photos.size.coerceAtLeast(1)) { index ->
                                LinearProgressIndicator(progress={if(index<state.photo) 1f else if(index==state.photo) progress.value else 0f},
                                    modifier=Modifier.weight(1f).height(3.dp),color=colors.onMedia,trackColor=colors.onMedia.copy(alpha=.25f))
                            }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(start=space.small)) {
                                Text(if(moment?.canEdit==true) "Your Moment" else moment?.creator?.name?.ifBlank { "Moment" } ?: "Moment",style=MaterialTheme.typography.titleMedium)
                                if(moment!=null) Text(if(moment.visibility=="private") "Just you two" else "Public · SriSu members",style=MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick={paused=!paused}) {Icon(if(paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(paused) "Resume Moment" else "Pause Moment")}
                            if(moment?.canEdit==true || moment?.canDelete==true) Box {
                                IconButton(onClick={options=true}) {Icon(Icons.Outlined.MoreHoriz,"Moment options")}
                                DropdownMenu(expanded=options,onDismissRequest={options=false}) {
                                    if(moment.canEdit) DropdownMenuItem(text={Text("Edit caption")},onClick={options=false;onEdit(moment.caption)})
                                    if(moment.canDelete) DropdownMenuItem(text={Text("Delete Moment")},onClick={options=false;confirmDelete=true})
                                }
                            }
                            IconButton(onClick=onBack) {Icon(Icons.Outlined.Close,"Close Moment")}
                        }
                    }
                }
                if(moment!=null) Surface(color=colors.mediaBackground.copy(alpha=.9f),contentColor=colors.onMedia) {
                    Column(Modifier.fillMaxWidth().padding(space.medium),verticalArrangement=Arrangement.spacedBy(space.small)) {
                        if(moment.caption.isNotBlank()) Text(moment.caption,style=MaterialTheme.typography.bodyLarge,
                            modifier=Modifier.heightIn(max=160.dp).verticalScroll(captionScroll))
                        val remaining=runCatching {(Instant.parse(moment.expiresAt)-Instant.parse(moment.serverTime)).inWholeMinutes}.getOrDefault(0)
                        Text(if(remaining<60) "Expires in under an hour" else "Expires in about ${remaining/60}h",style=MaterialTheme.typography.bodySmall)
                        if(decodeFailed) MomentError("This photo could not be opened.",onRetry)
                        state.error?.let { MomentError(it,onRetry) }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            IconButton(onClick={onMove(-1)},enabled=state.hasPrevious && !state.loading) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Previous photo")}
                            Text("${state.photo+1} / ${moment.photos.size.coerceAtLeast(1)}",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.align(Alignment.CenterVertically))
                            IconButton(onClick={onMove(1)},enabled=state.hasNext && !state.loading) {Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Next photo")}
                        }
                        if(moment.visibility=="public") TextButton(onClick={onNotes(false)},enabled=state.fresh) {
                            Text(if(moment.canReply || moment.canEdit) "Private appreciations" else "Your appreciations",color=colors.onMedia)
                        }
                        if(moment.canReply) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                            OutlinedButton(onClick={onReply(MomentReference(moment.id,moment.couple))},enabled=state.fresh,modifier=Modifier.weight(1f),
                                colors=ButtonDefaults.outlinedButtonColors(contentColor=colors.onMedia)) {Text("Reply in your private chat")}
                            IconToggleButton(checked=moment.reacted,onCheckedChange={onReact()},enabled=!state.busy && state.fresh) {
                                Icon(if(moment.reacted) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,if(moment.reacted) "Remove heart reaction" else "Send heart reaction",tint=if(moment.reacted) colors.moments else colors.onMedia)
                            }
                        } else if(moment.canEdit) Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if(moment.partnerSeenAt==null) "Not seen by your partner yet" else "Seen by your partner",style=MaterialTheme.typography.bodyMedium)
                                if(moment.partnerReacted) Text("Your partner sent a heart",style=MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick={onReply(MomentReference(moment.id,moment.couple))}) {Text("Open chat",color=colors.onMedia)}
                        } else if(moment.visibility=="public") {
                            if(state.noteSent) Text("Your private appreciation was sent.",style=MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(state.note,onNote,modifier=Modifier.fillMaxWidth(),label={Text("Send a private appreciation")},maxLines=3,
                                colors=OutlinedTextFieldDefaults.colors(focusedTextColor=colors.onMedia,unfocusedTextColor=colors.onMedia,focusedLabelColor=colors.onMedia,unfocusedLabelColor=colors.onMedia),
                                trailingIcon={IconButton(onClick=onSendNote,enabled=!state.busy && state.note.isNotBlank()) {Icon(Icons.AutoMirrored.Outlined.Send,"Send appreciation",tint=colors.onMedia)}})
                        }
                    }
                }
            }
        }
    }
    if(state.notes!=null && state.fresh) ModalBottomSheet(onDismissRequest=onCloseNotes) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            item { Text("Private appreciations",style=MaterialTheme.typography.titleLarge) }
            if(state.notesLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let {item {MomentError(it,{onNotes(false)})}}
            if(state.notes.isEmpty() && !state.notesLoading) item {Text("No appreciations yet.")}
            items(state.notes.size,key={state.notes[it].id}) {index->
                val note=state.notes[index]
                Column(verticalArrangement=Arrangement.spacedBy(space.small)) {
                    Text(note.message,style=MaterialTheme.typography.bodyLarge)
                    note.replies.forEach {Text(it.message,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
                    if(moment?.canReply==true || moment?.canEdit==true) {
                        if(state.noteReplyId==note.id) {
                            OutlinedTextField(state.noteReplyDraft,{onNoteReply(note.id,it)},label={Text("Private reply")},modifier=Modifier.fillMaxWidth(),maxLines=4)
                            TextButton(onClick=onSendNoteReply,enabled=!state.busy && state.noteReplyDraft.isNotBlank()) {Text("Send reply")}
                        } else TextButton(onClick={onNoteReply(note.id,"")}) {Text("Reply privately")}
                    }
                    HorizontalDivider()
                }
            }
            if(state.notesNext) item {TextButton(onClick={onNotes(true)},enabled=!state.notesLoading) {Text("Load more")}}
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this Moment?")},text={Text("It will disappear for everyone. Chat references will show it as unavailable.")},
        confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}) {Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}) {Text("Cancel")}})
    if(state.captionDraft!=null) ModalBottomSheet(onDismissRequest={if(!state.busy) onEdit(null)}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            Text("Edit caption",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(state.captionDraft,onEdit,modifier=Modifier.fillMaxWidth(),enabled=!state.busy,maxLines=5,label={Text("Caption")})
            state.error?.let {MomentError(it)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick={onEdit(null)},enabled=!state.busy) {Text("Cancel")}
                Button(onClick=onSave,enabled=!state.busy) {Text("Save caption")}
            }
        }
    }
}
