package com.srisu.srisu.features.moments.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.DraftOutlinedTextField
import com.srisu.srisu.features.moments.data.Moment
import com.srisu.srisu.features.moments.data.MomentReference
import com.srisu.srisu.theme.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.*
import kotlin.time.Instant

@Composable fun MomentViewerScreen(momentId: Long?,coupleId: Long?,creatorId: Long?,onBack: ()->Unit,
    onReply: (MomentReference)->Unit, vm: MomentViewerViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(momentId,coupleId,creatorId) { vm.open(momentId,coupleId,creatorId) }
    MomentLifecycle(vm::active)
    MomentViewerContent(state,onBack,vm::refresh,vm::move,vm::presented,vm::react,vm::editCaption,vm::saveCaption,vm::delete,
        vm::note,vm::sendNote,onReply, onNotes=vm::loadNotes,onCloseNotes=vm::closeNotes,onNoteReply=vm::replyToNote,
        onSendNoteReply=vm::sendNoteReply,avatar=vm::image)
}

@OptIn(kotlin.time.ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable internal fun MomentViewerContent(state: MomentViewerState,onBack: ()->Unit,onRetry: ()->Unit,
    onMove: (Int)->Unit,onPresented: ()->Unit,onReact: ()->Unit,onEdit: (String?)->Unit,onSave: ()->Unit,
    onDelete: ()->Unit,onNote: (String)->Unit,onSendNote: ()->Unit,onReply: (MomentReference)->Unit,
    onNotes: (Boolean)->Unit={},onCloseNotes: ()->Unit={},onNoteReply: (Long?,String)->Unit={_,_->},onSendNoteReply: ()->Unit={},
    avatar: suspend (String)->ByteArray? = { null }) {
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
        if(state.unavailable!=null || (state.error!=null && !state.fresh)) MomentUnavailable(state.unavailable,state.error,onRetry,onBack)
        else Box(Modifier.fillMaxSize()) {
            if(state.fresh && state.bytes!=null) {
                val previous=stringResource(Res.string.moment_previous)
                val next=stringResource(Res.string.moment_next)
                MomentImage(state.bytes,stringResource(Res.string.moment_photo,state.photo+1,moment?.photos?.size?.coerceAtLeast(1) ?: 1),Modifier.fillMaxSize()
                    .semantics { customActions=listOfNotNull(
                        if(state.hasPrevious) CustomAccessibilityAction(previous) { onMove(-1); true } else null,
                        if(state.hasNext) CustomAccessibilityAction(next) { onMove(1); true } else null) }
                    .pointerInput(moment?.id,state.photo) { detectTapGestures(onPress={ held=true; try { tryAwaitRelease() } finally {held=false} },
                        onTap={ offset -> onMove(if(offset.x<size.width/2) -1 else 1) }) },ContentScale.Crop,{pixelsPresented=true;onPresented()},{decodeFailed=true})
            }
            MomentScrims()
            if(state.loading || state.imageLoading) CircularProgressIndicator(Modifier.align(Alignment.Center),color=colors.onMedia)
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                MomentViewerHeader(moment,state.photo,{progress.value},paused,avatar,{paused=!paused},onBack,
                    menu={ if(moment!=null && (moment.canEdit || moment.canDelete)) Box {
                        MomentHeaderIcon(Icons.Outlined.MoreHoriz,stringResource(Res.string.moment_options)) { options=true }
                        DropdownMenu(expanded=options,onDismissRequest={options=false}) {
                            if(moment.canEdit) DropdownMenuItem(text={Text(stringResource(Res.string.moment_edit_caption))},onClick={options=false;onEdit(moment.caption)})
                            if(moment.canDelete) DropdownMenuItem(text={Text(stringResource(Res.string.moment_delete))},onClick={options=false;confirmDelete=true})
                        }
                    } })
                Spacer(Modifier.weight(1f))
                if(moment!=null) Column(Modifier.fillMaxWidth().padding(space.medium),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(space.compact)) {
                    if(moment.caption.isNotBlank()) Surface(shape=MaterialTheme.shapes.extraLarge,color=colors.mediaBackground.copy(alpha=MomentTokens.pillAlpha),contentColor=colors.onMedia) {
                        Text(moment.caption,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center,
                            modifier=Modifier.heightIn(max=MomentTokens.captionMaxHeight).verticalScroll(captionScroll)
                                .padding(horizontal=space.gutter,vertical=space.small))
                    }
                    MomentExpiry(moment)
                    if(decodeFailed) MomentError(stringResource(Res.string.moment_photo_failed),onRetry)
                    state.error?.let { MomentError(it,onRetry) }
                    if(moment.visibility=="public") TextButton(onClick={onNotes(false)},enabled=state.fresh) {
                        Text(stringResource(if(moment.canReply || moment.canEdit) Res.string.moment_appreciations else Res.string.moment_your_appreciations),color=colors.onMedia)
                    }
                    when {
                        moment.canReply -> MomentReplyBar(moment,state.fresh,!state.busy && state.fresh,{onReply(MomentReference(moment.id,moment.couple))},onReact)
                        moment.canEdit -> MomentSeenCard(moment,avatar,{onReply(MomentReference(moment.id,moment.couple))})
                        moment.visibility=="public" -> MomentAppreciationField(state.note,state.noteSent,!state.busy,onNote,onSendNote)
                    }
                }
            }
        }
    }
    if(state.notes!=null && state.fresh) ModalBottomSheet(onDismissRequest=onCloseNotes) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            item { Text(stringResource(Res.string.moment_appreciations),style=MaterialTheme.typography.titleLarge) }
            if(state.notesLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let {item {MomentError(it,{onNotes(false)})}}
            if(state.notes.isEmpty() && !state.notesLoading) item {Text(stringResource(Res.string.moment_notes_empty))}
            items(state.notes.size,key={state.notes[it].id}) {index->
                val note=state.notes[index]
                Column(verticalArrangement=Arrangement.spacedBy(space.small)) {
                    Text(note.message,style=MaterialTheme.typography.bodyLarge)
                    note.replies.forEach {Text(it.message,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
                    if(moment?.canReply==true || moment?.canEdit==true) {
                        if(state.noteReplyId==note.id) {
                            DraftOutlinedTextField(state.noteReplyDraft,{onNoteReply(note.id,it)},label={Text(stringResource(Res.string.moment_note_reply_label))},modifier=Modifier.fillMaxWidth(),maxLines=4,maxLength=1000)
                            TextButton(onClick=onSendNoteReply,enabled=!state.busy && state.noteReplyDraft.isNotBlank()) {Text(stringResource(Res.string.moment_note_reply_send))}
                        } else TextButton(onClick={onNoteReply(note.id,"")}) {Text(stringResource(Res.string.moment_note_reply))}
                    }
                    HorizontalDivider()
                }
            }
            if(state.notesNext) item {TextButton(onClick={onNotes(true)},enabled=!state.notesLoading) {Text(stringResource(Res.string.moment_notes_more))}}
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(stringResource(Res.string.moment_delete_title))},
        text={Text(stringResource(Res.string.moment_delete_body))},
        confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}) {Text(stringResource(Res.string.moment_delete_confirm))}},
        dismissButton={TextButton(onClick={confirmDelete=false}) {Text(stringResource(Res.string.moment_cancel))}})
    if(state.captionDraft!=null) ModalBottomSheet(onDismissRequest={if(!state.busy) onEdit(null)}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            Text(stringResource(Res.string.moment_edit_caption),style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(state.captionDraft,onEdit,modifier=Modifier.fillMaxWidth(),enabled=!state.busy,maxLines=5,label={Text(stringResource(Res.string.moment_caption_label))})
            state.error?.let {MomentError(it)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick={onEdit(null)},enabled=!state.busy) {Text(stringResource(Res.string.moment_cancel))}
                Button(onClick=onSave,enabled=!state.busy) {Text(stringResource(Res.string.moment_caption_save))}
            }
        }
    }
}

@Composable internal fun MomentUnavailable(reason: String?,error: String?,onRetry: ()->Unit,onBack: ()->Unit,modifier: Modifier=Modifier) {
    val space=MaterialTheme.spacing
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(space.large),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.ImageNotSupported,null,Modifier.size(space.huge))
        Spacer(Modifier.height(space.large))
        Text(stringResource(when(reason) { "expired"->Res.string.moment_expired;"deleted"->Res.string.moment_deleted;"empty"->Res.string.moment_empty;else->Res.string.moment_unavailable }),
            style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
        Spacer(Modifier.height(space.compact))
        Text(error ?: stringResource(if(reason=="expired") Res.string.moment_expired_body else Res.string.moment_unavailable_body),
            style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center)
        Spacer(Modifier.height(space.large))
        if(error!=null) Button(onClick=onRetry) {Text(stringResource(Res.string.moment_retry))}
        TextButton(onClick=onBack) {Text(stringResource(Res.string.moment_back),color=MaterialTheme.colorScheme.onMedia)}
    }
}

/** Soft top/bottom gradients so white chrome stays legible over bright photos. */
@Composable internal fun BoxScope.MomentScrims() {
    val shade=MaterialTheme.colorScheme.mediaBackground
    val clear=MaterialTheme.colorScheme.transparent
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(SCRIM_FRACTION).background(Brush.verticalGradient(listOf(shade.copy(alpha=MomentTokens.scrimAlpha),clear))))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(SCRIM_FRACTION).background(Brush.verticalGradient(listOf(clear,shade.copy(alpha=MomentTokens.scrimAlpha)))))
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable internal fun MomentViewerHeader(moment: Moment?,photo: Int,progress: ()->Float,paused: Boolean,avatar: suspend (String)->ByteArray?,
    onPause: ()->Unit,onClose: ()->Unit,modifier: Modifier=Modifier,menu: @Composable ()->Unit={}) {
    val colors=MaterialTheme.colorScheme
    val space=MaterialTheme.spacing
    Column(modifier.fillMaxWidth().padding(horizontal=space.medium,vertical=space.small),verticalArrangement=Arrangement.spacedBy(space.compact)) {
        if(moment!=null) Row(horizontalArrangement=Arrangement.spacedBy(space.tiny)) {
            repeat(moment.photos.size.coerceAtLeast(1)) { index ->
                LinearProgressIndicator(progress={if(index<photo) 1f else if(index==photo) progress() else 0f},
                    modifier=Modifier.weight(1f).height(MomentTokens.progressHeight).clip(MaterialTheme.shapes.pill),
                    color=colors.onMedia,trackColor=colors.onMedia.copy(alpha=MomentTokens.trackAlpha),drawStopIndicator={})
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
            GuardedMomentImage(if(moment?.canEdit==true) null else moment?.creator?.photoUrl,avatar,null,
                Modifier.size(MomentTokens.headerAvatar).clip(CircleShape),avatar=true)
            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.small)) {
                Text(if(moment?.canEdit==true) stringResource(Res.string.moment_yours) else moment?.creator?.name?.ifBlank { null } ?: stringResource(Res.string.moment_fallback_name),
                    style=MaterialTheme.typography.titleMedium,color=colors.onMedia,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                if(moment!=null) minutesBetween(moment.publishedAt,moment.serverTime)?.let {
                    Text(ageLabel(it),style=MaterialTheme.typography.bodyLarge,color=colors.onMedia.copy(alpha=MomentTokens.mutedAlpha),maxLines=1)
                }
            }
            MomentHeaderIcon(if(paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                stringResource(if(paused) Res.string.moment_resume else Res.string.moment_pause),onPause)
            menu()
            MomentHeaderIcon(Icons.Outlined.Close,stringResource(Res.string.moment_close),onClose)
        }
    }
}

@Composable internal fun MomentHeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector,description: String,onClick: ()->Unit) {
    IconButton(onClick=onClick) { Icon(icon,description,tint=MaterialTheme.colorScheme.onMedia) }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable internal fun MomentExpiry(moment: Moment,modifier: Modifier=Modifier) {
    val remaining=minutesBetween(moment.serverTime,moment.expiresAt) ?: 0
    val color=MaterialTheme.colorScheme.onMedia.copy(alpha=MomentTokens.mutedAlpha)
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small),verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule,null,Modifier.size(MaterialTheme.spacing.icon),tint=color)
        Text(if(remaining<60) stringResource(Res.string.moment_disappears_soon) else stringResource(Res.string.moment_disappears_in,ageLabel(remaining)),
            style=MaterialTheme.typography.bodyLarge,color=color)
    }
}

@Composable internal fun MomentReplyBar(moment: Moment,fresh: Boolean,canReact: Boolean,onReply: ()->Unit,onReact: ()->Unit,modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    val name=moment.creator.name.ifBlank { stringResource(Res.string.moment_fallback_name) }
    Row(modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
        Surface(onClick=onReply,enabled=fresh,modifier=Modifier.weight(1f).semantics { role=Role.Button },shape=MaterialTheme.shapes.pill,
            color=colors.mediaBackground.copy(alpha=MomentTokens.trackAlpha),border=BorderStroke(MomentTokens.outline,colors.onMedia.copy(alpha=MomentTokens.mutedAlpha))) {
            Text(stringResource(Res.string.moment_reply_to,name),style=MaterialTheme.typography.bodyLarge,color=colors.onMedia,maxLines=1,overflow=TextOverflow.Ellipsis,
                modifier=Modifier.heightIn(min=MaterialTheme.spacing.huge).wrapContentHeight().padding(horizontal=MaterialTheme.spacing.gutter))
        }
        val description=stringResource(if(moment.reacted) Res.string.moment_unreact else Res.string.moment_react)
        Box(Modifier.size(MomentTokens.heartButton).clip(CircleShape).background(colors.mediaBackground.copy(alpha=MomentTokens.chromeAlpha))
            .toggleable(moment.reacted,enabled=canReact,role=Role.Switch) { onReact() }.semantics { contentDescription=description },contentAlignment=Alignment.Center) {
            Icon(if(moment.reacted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,null,tint=if(moment.reacted) colors.moments else colors.onMedia)
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable internal fun MomentSeenCard(moment: Moment,avatar: suspend (String)->ByteArray?,onOpenChat: ()->Unit,modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    val space=MaterialTheme.spacing
    val seen=moment.partnerSeenAt
    Surface(modifier.fillMaxWidth(),shape=MaterialTheme.shapes.extraLarge,color=colors.mediaBackground.copy(alpha=MomentTokens.cardAlpha),contentColor=colors.onMedia) {
        Row(Modifier.padding(space.medium),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(space.compact)) {
            Box(Modifier.semantics(mergeDescendants=true) {}) {
                GuardedMomentImage(null,avatar,null,Modifier.size(MomentTokens.seenAvatar).clip(CircleShape),avatar=true)
                if(moment.partnerReacted) Box(Modifier.align(Alignment.BottomEnd).size(MomentTokens.heartBadge).clip(CircleShape).background(colors.onMedia),
                    contentAlignment=Alignment.Center) {
                    Icon(Icons.Filled.Favorite,stringResource(Res.string.moment_reacted),Modifier.size(MomentTokens.heartBadgeIcon),tint=colors.moments)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(space.tiny)) {
                Text(stringResource(if(seen==null) Res.string.moment_not_seen else Res.string.moment_seen),style=MaterialTheme.typography.titleMedium)
                val ago=seen?.let { minutesBetween(it,moment.serverTime) }?.let {
                    if(it<1) stringResource(Res.string.moment_just_now) else stringResource(Res.string.moment_ago,ageLabel(it))
                }
                if(ago!=null) Row(horizontalArrangement=Arrangement.spacedBy(space.tiny),verticalAlignment=Alignment.CenterVertically) {
                    val muted=colors.onMedia.copy(alpha=MomentTokens.mutedAlpha)
                    Icon(Icons.Outlined.Visibility,null,Modifier.size(space.icon),tint=muted)
                    Text(if(moment.partnerReacted) stringResource(Res.string.moment_seen_detail,ago) else ago,style=MaterialTheme.typography.bodyMedium,color=muted)
                }
            }
            Surface(onClick=onOpenChat,shape=MaterialTheme.shapes.pill,color=colors.onMedia,contentColor=colors.mediaBackground) {
                Text(stringResource(Res.string.moment_open_chat),style=MaterialTheme.typography.titleMedium,
                    modifier=Modifier.heightIn(min=MaterialTheme.spacing.touchTarget).wrapContentHeight().padding(horizontal=space.gutter))
            }
        }
    }
}

@Composable internal fun MomentAppreciationField(note: String,sent: Boolean,enabled: Boolean,onNote: (String)->Unit,onSend: ()->Unit,modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        if(sent) Text(stringResource(Res.string.moment_appreciation_sent),style=MaterialTheme.typography.bodyMedium,color=colors.onMedia)
        OutlinedTextField(note,onNote,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.pill,maxLines=3,
            placeholder={Text(stringResource(Res.string.moment_appreciation_hint),color=colors.onMedia.copy(alpha=MomentTokens.mutedAlpha))},
            colors=OutlinedTextFieldDefaults.colors(focusedTextColor=colors.onMedia,unfocusedTextColor=colors.onMedia,
                focusedBorderColor=colors.onMedia,unfocusedBorderColor=colors.onMedia.copy(alpha=MomentTokens.mutedAlpha),cursorColor=colors.onMedia),
            trailingIcon={IconButton(onClick=onSend,enabled=enabled && note.isNotBlank()) {Icon(Icons.AutoMirrored.Outlined.Send,stringResource(Res.string.moment_appreciation_send),tint=colors.onMedia)}})
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun minutesBetween(from: String,to: String): Long? = runCatching { (Instant.parse(to)-Instant.parse(from)).inWholeMinutes.coerceAtLeast(0) }.getOrNull()

@Composable internal fun ageLabel(minutes: Long): String = when {
    minutes<1 -> stringResource(Res.string.moment_age_now)
    minutes<60 -> stringResource(Res.string.moment_age_minutes,minutes.toInt())
    else -> stringResource(Res.string.moment_age_hours,(minutes/60).toInt())
}

private const val SCRIM_FRACTION=0.3f
