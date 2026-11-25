package com.srisu.srisu.features.moments.presentation

import com.srisu.srisu.theme.sriSuFilterChipColors

import com.srisu.srisu.theme.selectedSurface

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonSize
import com.srisu.srisu.components.DraftEditor
import com.srisu.srisu.components.rememberDraftEditor
import com.srisu.srisu.features.couplechat.presentation.rememberNativeChatMedia
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.theme.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.*

@Composable fun MomentComposerScreen(couple: Long,onBack: ()->Unit,onPublished: (Long)->Unit,
    vm: MomentComposerViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val native=rememberNativeChatMedia()
    val videoPicker=rememberMomentVideoPicker()
    val scope=rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var pickerEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(couple) { vm.open(couple) }
    DisposableEffect(native) { onDispose { native.close();videoPicker.close() } }
    LaunchedEffect(state.publishedId,state.discarded) { state.publishedId?.let(onPublished); if(state.discarded) onBack() }
    MomentComposerContent(state,vm::image,vm::caption,vm::audience,vm::remove,vm::reorder,vm::share,vm::restore,vm::discard,onBack,
        picking=picking,onCrop=vm::crop,playback=vm::playback,onCancelUpload={pickerEpoch++;videoPicker.cancel();picking=false;if(state.busy) vm.cancelUpload()},onPick={source->
            if(!picking) {
                picking=true
                val stamp=vm.pickerStamp()
                val ownEpoch=++pickerEpoch
                if(source=="video" || source=="record-video") {
                    val receive: (MomentVideoSelection)->Unit = {result -> scope.launch {if(ownEpoch==pickerEpoch) {picking=false;vm.pickedVideo(result,stamp)} else result.video?.let(::removeMomentVideoFile)}}
                    if(source=="record-video") videoPicker.record(receive) else videoPicker.pick(receive)
                }
                else if(source=="photo") native.pickPhotos(vm.remainingPhotoSlots()) { batch ->
                    scope.launch { if(ownEpoch==pickerEpoch) {picking=false; vm.pickedPhotos(batch,stamp)} }
                } else native.pick(source) { raw,bytes -> scope.launch { if(ownEpoch==pickerEpoch) {picking=false; vm.picked(raw,bytes,stamp)} } }
            }
        })
}

/** One safe-area shell; only the body scrolls, so publication stays reachable at any text size. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun MomentComposerContent(
    state: MomentComposerState, image: suspend (String) -> ByteArray?,
    onCaption: (String) -> Unit, onAudience: (String) -> Unit, onRemove: (String) -> Unit,
    onReorder: (String, Int) -> Unit, onShare: () -> Unit, onRetry: () -> Unit,
    onDiscard: () -> Unit, onBack: () -> Unit, onPick: (String) -> Unit,
    picking: Boolean = false, initialCaptionEditing: Boolean = false,
    onCrop: (String, MomentCrop?) -> Unit = { _, _ -> },
    playback: suspend (String)->MomentResult<MomentPlayback> = {com.srisu.srisu.core.data.remote.NetworkAPIResult.Error("Video unavailable")},
    onCancelUpload: ()->Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val space = MaterialTheme.spacing
    val enabled = !state.loading && !state.busy && !picking && state.record?.publication == null
    var leave by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(initialCaptionEditing) }
    var focusedCaption by rememberSaveable { mutableStateOf(false) }
    var cropping by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraMenu by remember { mutableStateOf(false) }
    val captionEditor = rememberDraftEditor(state.record?.caption.orEmpty())
    val portraitScroll = rememberScrollState()
    val detailsScroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val done = { focus.clearFocus(); keyboard?.hide(); editing = false }
    val cropPhoto = state.photos.firstOrNull { it.id == cropping }
    if (cropPhoto != null && enabled) {
        MomentPhotoEditor(cropPhoto, image, state.record?.crops?.get(cropPhoto.id),
            onCancel = { cropping = null }, onApply = { onCrop(cropPhoto.id, it); cropping = null })
        return
    }
    val back = {
        if (editing) done()
        else if (state.photos.isNotEmpty() || state.record?.caption?.isNotBlank() == true || state.busy) leave = true
        else onBack()
    }
    BackHandler(onBack = back)
    Surface(Modifier.fillMaxSize(), color = colors.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            // Height changes from the keyboard must not switch a portrait phone to a two-pane layout.
            val wide = maxWidth >= MomentComposerTokens.twoPaneMinWidth && maxWidth > maxHeight
            // Stay in focused editing until Done/Back; dismissing the IME must not remount and refocus the field.
            val compactEditor = editing && (focusedCaption || maxHeight < MomentComposerTokens.compactEditorHeight)
            SideEffect { focusedCaption = compactEditor }
            val previewHeight = (maxHeight * MomentComposerTokens.previewFraction)
                .coerceIn(MomentComposerTokens.previewMinHeight, MomentComposerTokens.previewMaxHeight)
            Column(Modifier.widthIn(max = MomentComposerTokens.maxWidth).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = space.small), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Outlined.Close, stringResource(Res.string.moment_close)) }
                    Text(stringResource(if (compactEditor) Res.string.moment_caption_label else Res.string.moment_new),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if(state.busy || picking) TextButton(onClick=onCancelUpload) {Text(if(picking) "Cancel" else "Pause")}
                    if (editing) TextButton(onClick = done, modifier = Modifier.testTag("moment-caption-done")) { Text(stringResource(Res.string.moment_caption_done)) }
                }
                if (state.loading || picking) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (compactEditor) {
                    // A short landscape IME must not squeeze the editable text between two toolbars.
                    MomentCaptionField(state.record?.caption.orEmpty(), true, enabled, onCaption, { editing = true }, done,
                        captionEditor, Modifier.weight(1f).padding(horizontal = space.medium).padding(bottom = space.small), expanded = true)
                } else {
                    val media: @Composable (Modifier, Dp?) -> Unit = { modifier, height ->
                        if (state.photos.isEmpty()) {
                            MomentEmptyPreview(enabled, picking, { onPick("photo") }, { done(); cameraMenu = true }, {onPick("video")}, modifier, height)
                        } else {
                            val index = selected.coerceIn(state.photos.indices)
                            val photo = state.photos[index]
                            MomentComposerMedia(state.photos, index, image, enabled, modifier, height,
                                crop = state.record?.crops?.get(photo.id),
                                onCrop = { onCrop(photo.id, it) }, onAdjust = { done(); cropping = photo.id },
                                onSelect = { selected = it },
                                onEarlier = { onReorder(photo.id, -1); selected = (index - 1).coerceAtLeast(0) },
                                onLater = { onReorder(photo.id, 1); selected = (index + 1).coerceAtMost(state.photos.lastIndex) },
                                onRemove = { onRemove(photo.id); selected = 0 },
                                onAdd = { onPick("photo") }, onCamera = { done(); cameraMenu = true },onVideo = {onPick("video")},playback=playback)
                        }
                    }
                    val details: @Composable () -> Unit = {
                        MomentCaptionField(state.record?.caption.orEmpty(), editing, enabled, onCaption, { editing = true }, done, captionEditor)
                        MomentAudienceSection(state, enabled, onAudience, onShare, onRetry, onDiscard)
                    }
                    val footer: @Composable () -> Unit = {
                        MomentShareFooter(state, picking) { done(); onShare() }
                    }
                    if (wide) {
                        Row(Modifier.weight(1f).padding(horizontal = space.medium), horizontalArrangement = Arrangement.spacedBy(space.large)) {
                            media(Modifier.weight(1f).fillMaxHeight().padding(bottom = space.small), null)
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                Column(Modifier.weight(1f).verticalScroll(detailsScroll).padding(bottom = space.medium),
                                    verticalArrangement = Arrangement.spacedBy(space.medium)) { details() }
                                footer()
                            }
                        }
                    } else {
                        // A bounded preview and one continuous scroll area avoid squeezing the audience/CTA off screen.
                        Column(Modifier.weight(1f).verticalScroll(portraitScroll)
                            .padding(horizontal = space.medium).padding(bottom = space.medium),
                            verticalArrangement = Arrangement.spacedBy(space.medium)) {
                            media(Modifier.fillMaxWidth(), previewHeight)
                            details()
                        }
                        Box(Modifier.padding(horizontal = space.medium)) { footer() }
                    }
                }
            }
        }
    }
    if (cameraMenu && enabled) AlertDialog(onDismissRequest = { cameraMenu = false },
        title = { Text("Camera") },
        text = { Column {
            TextButton(onClick = { cameraMenu = false; onPick("camera") }, modifier = Modifier.fillMaxWidth().heightIn(min = space.touchTarget)) {
                Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(space.small)); Text("Take photo", Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            TextButton(onClick = { cameraMenu = false; onPick("record-video") }, modifier = Modifier.fillMaxWidth().heightIn(min = space.touchTarget).testTag("moment-record-video")) {
                Icon(Icons.Outlined.Videocam, null); Spacer(Modifier.width(space.small)); Text("Record video · up to 60s", Modifier.weight(1f), textAlign = TextAlign.Start)
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { cameraMenu = false }) { Text("Cancel") } })
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text(stringResource(Res.string.moment_leave_title)) },
        text = { Text(stringResource(if (state.busy) Res.string.moment_leave_busy else Res.string.moment_leave_body)) },
        confirmButton = { TextButton(onClick = { leave = false; if (!state.busy) onBack() }) {
            Text(stringResource(if (state.busy) Res.string.moment_leave_stay else Res.string.moment_leave_keep))
        } },
        dismissButton = { if (!state.busy) TextButton(onClick = { leave = false; onDiscard() }) { Text(stringResource(Res.string.moment_leave_discard)) } })
}

/** The same composer is shown before selection; picking starts only from an explicit photo action. */
@Composable private fun MomentEmptyPreview(
    enabled: Boolean, picking: Boolean, onLibrary: () -> Unit, onCamera: () -> Unit, onVideo: () -> Unit,
    modifier: Modifier, previewHeight: Dp?,
) {
    val space = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val description = stringResource(Res.string.moment_library)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.small)) {
        val previewModifier = if (previewHeight == null) Modifier.weight(1f) else Modifier.height(previewHeight)
        Box(previewModifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerLow).border(MomentTokens.outline, colors.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onLibrary)
            .semantics { contentDescription = description }.testTag("moment-add-first-photo"), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(space.medium), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space.small)) {
                Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(space.extraLarge), tint = colors.onSurfaceVariant)
                Text(stringResource(if (picking) Res.string.moment_opening else Res.string.moment_media_add),
                    style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = space.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.moment_photo_limit), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            IconButton(onClick=onVideo,enabled=enabled) {Icon(Icons.Outlined.VideoLibrary,"Add video")}
            IconButton(onClick = onCamera, enabled = enabled) { Icon(Icons.Outlined.PhotoCamera, "Open camera") }
        }
    }
}

@Composable private fun MomentComposerMedia(
    photos: List<LocalMomentPhoto>, selected: Int, image: suspend (String) -> ByteArray?, enabled: Boolean,
    modifier: Modifier, previewHeight: Dp?, onSelect: (Int) -> Unit, onEarlier: () -> Unit, onLater: () -> Unit,
    onRemove: () -> Unit, onAdd: () -> Unit, onCamera: () -> Unit, onVideo: () -> Unit,
    playback: suspend (String)->MomentResult<MomentPlayback>,
    crop: MomentCrop?, onCrop: (MomentCrop?) -> Unit, onAdjust: () -> Unit,
) {
    val space = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val photo = photos[selected]
    val pixels = rememberMomentPixels(photo, image)
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.small)) {
        val previewModifier = if (previewHeight == null) Modifier.weight(1f) else Modifier.height(previewHeight)
        Box(previewModifier.fillMaxWidth().onSizeChanged { previewSize = it }.clip(MaterialTheme.shapes.medium).background(colors.mediaBackground)) {
            val description = stringResource(Res.string.moment_photo_selected, selected + 1, photos.size)
            if(photo.video!=null || photo.remote?.video!=null) MomentVideoPreview(photo,playback,enabled,Modifier.fillMaxSize()) {
                MomentFramedPhoto(pixels, MomentCrop.Full, description, Modifier.fillMaxSize())
            }
            else MomentFramedPhoto(pixels, crop ?: MomentCrop.Full, description, Modifier.fillMaxSize().testTag("moment-photo-preview"))
            Row(Modifier.fillMaxWidth().padding(space.tiny), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.pill, color = colors.mediaBackground.copy(alpha = MomentTokens.pillAlpha)) {
                    Text(stringResource(Res.string.moment_photo_position, selected + 1, photos.size),
                        Modifier.padding(horizontal = space.compact, vertical = space.small),
                        style = MaterialTheme.typography.labelSmall, color = colors.onMedia)
                }
                Spacer(Modifier.weight(1f))
                if (photos.size > 1) {
                    MomentChromeButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.moment_photo_earlier), onEarlier, enabled = enabled && selected > 0)
                    MomentChromeButton(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(Res.string.moment_photo_later), onLater, enabled = enabled && selected < photos.lastIndex)
                }
                MomentChromeButton(Icons.Outlined.DeleteOutline, stringResource(Res.string.moment_photo_remove), onRemove, enabled = enabled)
            }
        }
        if(photo.video==null && photo.remote?.video==null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(space.small), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = crop == null, onClick = { onCrop(null) }, enabled = enabled,
                label = { Text(stringResource(Res.string.moment_crop_full)) },
                modifier = Modifier.heightIn(min = space.touchTarget).testTag("moment-photo-full"), colors = sriSuFilterChipColors())
            TextButton(onClick = {
                pixels.bitmap?.let { bitmap ->
                    if (previewSize.width > 0 && previewSize.height > 0) onCrop(MomentCrop.centered(
                        bitmap.width.toDouble() / bitmap.height, previewSize.width.toDouble() / previewSize.height))
                }
            }, enabled = enabled && pixels.bitmap != null, modifier = Modifier.testTag("moment-photo-fill")) {
                Text(stringResource(Res.string.moment_crop_fill))
            }
            TextButton(onClick = onAdjust, enabled = enabled, modifier = Modifier.testTag("moment-photo-crop")) {
                Icon(Icons.Outlined.Crop, null, Modifier.size(space.large))
                Spacer(Modifier.width(space.tiny))
                Text(stringResource(Res.string.moment_crop_action))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.small)) {
            LazyRow(Modifier.weight(1f).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(space.small)) {
                itemsIndexed(photos, key = { _, p -> p.id }) { index, p ->
                    val description = stringResource(Res.string.moment_photo_select, index + 1)
                    Box(Modifier.size(MomentTokens.stripThumb).clip(MaterialTheme.shapes.small)
                        .then(if (index == selected) Modifier.border(MomentTokens.stripBorder, colors.primary, MaterialTheme.shapes.small) else Modifier)
                        .selectable(index == selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(MomentTokens.stripBorder).clip(MaterialTheme.shapes.small)) {
                        if (p.bytes != null) MomentImage(p.bytes, description, Modifier.fillMaxSize())
                        else GuardedMomentImage(p.remote?.image, image, description, Modifier.fillMaxSize())
                        if(p.video!=null || p.remote?.video!=null) Icon(Icons.Outlined.PlayArrow,"Video",Modifier.size(space.large).align(Alignment.Center),tint=colors.onMedia)
                    }
                }
            }
            if (photos.size < MAX_PHOTOS) {
                IconButton(onClick=onVideo,enabled=enabled) {Icon(Icons.Outlined.VideoLibrary,"Add video")}
                IconButton(onClick = onAdd, enabled = enabled) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(Res.string.moment_photo_add)) }
                IconButton(onClick = onCamera, enabled = enabled) { Icon(Icons.Outlined.PhotoCamera, "Open camera") }
            }
        }
    }
}


@Composable private fun MomentVideoPreview(photo:LocalMomentPhoto,playback:suspend (String)->MomentResult<MomentPlayback>,enabled:Boolean,modifier:Modifier,poster:@Composable ()->Unit) {
    var playing by remember(photo.id) {mutableStateOf(false)}
    var started by remember(photo.id) {mutableStateOf(false)}
    var muted by remember {mutableStateOf(true)}
    var foreground by remember {mutableStateOf(true)}
    var retry by remember(photo.id) {mutableIntStateOf(0)}
    var event by remember(photo.id,retry) {mutableStateOf(MomentPlaybackEvent("buffering"))}
    MomentLifecycle {foreground=it}
    val source by produceState<MomentPlayback?>(null,photo.id,retry,photo.remote?.video?.state) {
        value=photo.video?.let {MomentPlayback(it.path,durationMs=it.durationMs)}
            ?: photo.remote?.video?.takeIf {it.state=="ready"}?.let {(playback(it.id) as? com.srisu.srisu.core.data.remote.NetworkAPIResult.Success)?.response}
    }
    val colors=MaterialTheme.colorScheme
    val space=MaterialTheme.spacing
    Box(modifier.semantics {stateDescription=if(event.firstFrame) "Video ready" else if(event.state=="failed") "Video unavailable" else "Video loading"}) {
        if(started) source?.let {key(photo.id,retry) {MomentVideoPlayer(it,playing && foreground && enabled,muted,Modifier.fillMaxSize(),embedded=true) {next ->
            event=next;if(next.state in listOf("ended","paused","failed"))playing=false
        }}}
        if(!event.firstFrame) poster()
        if(source==null || event.state=="failed") Column(Modifier.align(Alignment.Center).padding(space.medium),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(photo.remote?.video?.state in listOf("queued","processing")) "Preparing video" else "Video preview unavailable",color=colors.onMedia)
            TextButton(onClick={retry++}) {Text("Retry",color=colors.onMedia)}
        } else {
            if(event.state=="buffering" && playing) CircularProgressIndicator(Modifier.align(Alignment.Center),color=colors.onMedia)
            Box(Modifier.align(Alignment.Center)) {
                MomentChromeButton(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if(playing) "Pause video" else "Play video", {started=true;playing=!playing}, enabled=enabled)
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(space.small),verticalAlignment=Alignment.CenterVertically) {
                val seconds=((photo.video?.durationMs ?: photo.remote?.video?.durationMs ?: 0)/1000).toInt()
                Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')} · Video",Modifier.weight(1f),color=colors.onMedia,style=MaterialTheme.typography.labelMedium)
                IconButton(onClick={muted=!muted}) {Icon(if(muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,if(muted) "Turn sound on" else "Mute video",tint=colors.onMedia)}
            }
        }
    }
}

@Composable private fun MomentCaptionField(
    caption: String, editing: Boolean, enabled: Boolean, onCaption: (String) -> Unit, onEdit: () -> Unit, onDone: () -> Unit,
    editor: DraftEditor, modifier: Modifier = Modifier, expanded: Boolean = false,
) {
    val space = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val label = stringResource(Res.string.moment_text_description)
    if (editing) {
        val focus = remember { FocusRequester() }
        val reveal = remember { BringIntoViewRequester() }
        val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
        LaunchedEffect(Unit) { focus.requestFocus() }
        LaunchedEffect(keyboardHeight) {
            // Wait for the resized scroll viewport before revealing the whole field and counter.
            withFrameNanos { }
            reveal.bringIntoView()
        }
        Column(modifier.bringIntoViewRequester(reveal), verticalArrangement = Arrangement.spacedBy(space.tiny)) {
            BasicTextField(editor.value, { next -> if (next.text.length <= 1000 && editor.edit(next)) onCaption(next.text) },
                enabled = enabled, maxLines = 4, cursorBrush = SolidColor(colors.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = (if (expanded) Modifier.weight(1f) else Modifier).fillMaxWidth().focusRequester(focus).semantics { contentDescription = label }
                    .clip(MaterialTheme.shapes.medium).background(colors.surfaceContainerLow)
                    .border(MomentTokens.outline, colors.outlineVariant, MaterialTheme.shapes.medium).padding(if (expanded) space.compact else space.medium),
                decorationBox = { inner -> Box {
                    if (editor.value.text.isEmpty()) Text(stringResource(Res.string.moment_caption_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    inner()
                } })
            if (!expanded) Text(stringResource(Res.string.moment_caption_count, editor.value.text.length), Modifier.align(Alignment.End),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    } else {
        Surface(onClick = onEdit, enabled = enabled, shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow,
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label }) {
            Row(Modifier.heightIn(min = space.touchTarget).padding(space.compact), horizontalArrangement = Arrangement.spacedBy(space.compact),
                verticalAlignment = Alignment.CenterVertically) {
                Text(caption.ifBlank { stringResource(Res.string.moment_caption_hint) }, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = if (caption.isBlank()) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.Edit, null, Modifier.size(space.icon), tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun MomentAudienceSection(
    state: MomentComposerState, enabled: Boolean, onAudience: (String) -> Unit, onShare: () -> Unit, onRetry: () -> Unit, onDiscard: () -> Unit,
) {
    val space = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val public = state.record?.visibility == "public"
    Column(verticalArrangement = Arrangement.spacedBy(space.small)) {
        Text(stringResource(Res.string.moment_share_to), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(space.small)) {
            MomentAudienceOption(stringResource(Res.string.moment_private_title), stringResource(Res.string.moment_private_body),
                Icons.Outlined.People, !public, enabled) { onAudience("private") }
            MomentAudienceOption(stringResource(Res.string.moment_public_title), stringResource(Res.string.moment_public_body),
                Icons.Outlined.Public, public, enabled) { onAudience("public") }
        }
        if (public) Text(stringResource(Res.string.moment_public_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        state.error?.let { MomentError(it, if (!state.busy) { { if (state.record?.publication != null) onShare() else onRetry() } } else null) }
        if (state.error != null && !state.busy) TextButton(onClick = onDiscard) { Text(stringResource(Res.string.moment_discard)) }
    }
}

@Composable private fun MomentAudienceOption(
    title: String, body: String, icon: ImageVector, selected: Boolean, enabled: Boolean, onClick: () -> Unit,
) {
    val space = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .background(if (selected) colors.selectedSurface else colors.surfaceContainerLow)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = space.compact, vertical = space.small), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.compact)) {
        Icon(icon, null, Modifier.size(space.icon), tint = colors.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        RadioButton(selected, onClick = null, enabled = enabled, modifier = Modifier.size(space.chromeIcon))
    }
}

@Composable private fun MomentShareFooter(state: MomentComposerState, picking: Boolean, onShare: () -> Unit) {
    val space = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(vertical = space.small), verticalArrangement = Arrangement.spacedBy(space.small)) {
        if (state.busy) {
            Text(state.stage, style = MaterialTheme.typography.bodySmall)
            if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(space.tiny), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(space.medium), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.moment_disappears_after), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val label = when {
            state.record?.publication != null && !state.busy -> Res.string.moment_check_publication
            state.record?.visibility == "public" -> Res.string.moment_share_public
            else -> Res.string.moment_share_private
        }
        SriSuButton(stringResource(label),
            onShare, Modifier.fillMaxWidth().heightIn(min = space.touchTarget).testTag("moment-share"),
            enabled = state.photos.isNotEmpty() && !state.loading && !state.busy && !picking, size = SriSuButtonSize.Large,
            textStyle = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center))
    }
}

/** Translucent dark circle used for media chrome (close, reorder, add). */
@Composable internal fun MomentChromeButton(icon: ImageVector,description: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true) {
    val colors=MaterialTheme.colorScheme
    IconButton(onClick=onClick,enabled=enabled,modifier=modifier,colors=IconButtonDefaults.iconButtonColors(
        containerColor=colors.mediaBackground.copy(alpha=MomentTokens.chromeAlpha),contentColor=colors.onMedia,
        disabledContainerColor=colors.mediaBackground.copy(alpha=MomentTokens.trackAlpha),disabledContentColor=colors.onMedia.copy(alpha=MomentTokens.trackAlpha))) {
        Icon(icon,description)
    }
}

/** Dark translucent pill for titles, captions and text actions over media. */
@Composable internal fun MomentPill(text: String,modifier: Modifier=Modifier,onClick: (()->Unit)?=null,enabled: Boolean=true,large: Boolean=false,
    container: androidx.compose.ui.graphics.Color=MaterialTheme.colorScheme.mediaBackground.copy(alpha=MomentTokens.pillAlpha)) {
    val colors=MaterialTheme.colorScheme
    val content: @Composable ()->Unit = {
        Text(text,style=if(large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,color=colors.onMedia,
            textAlign=TextAlign.Center,modifier=Modifier.heightIn(min=if(onClick!=null) MaterialTheme.spacing.touchTarget else MaterialTheme.spacing.buttonMedium)
                .wrapContentHeight().padding(horizontal=MaterialTheme.spacing.gutter,vertical=MaterialTheme.spacing.small))
    }
    val color=container
    if(onClick!=null) Surface(onClick=onClick,enabled=enabled,modifier=modifier,shape=MaterialTheme.shapes.pill,color=color) { content() }
    else Surface(modifier=modifier,shape=MaterialTheme.shapes.pill,color=color) { content() }
}

private const val MAX_PHOTOS=5
