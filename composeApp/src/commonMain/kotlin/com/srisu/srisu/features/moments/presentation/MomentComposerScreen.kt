package com.srisu.srisu.features.moments.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonSize
import com.srisu.srisu.features.couplechat.presentation.rememberNativeChatMedia
import com.srisu.srisu.theme.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.*

@Composable fun MomentComposerScreen(couple: Long,onBack: ()->Unit,onPublished: (Long)->Unit,
    vm: MomentComposerViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val native=rememberNativeChatMedia()
    val scope=rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(couple) { vm.open(couple) }
    DisposableEffect(native) { onDispose { native.close() } }
    LaunchedEffect(state.publishedId,state.discarded) { state.publishedId?.let(onPublished); if(state.discarded) onBack() }
    MomentComposerContent(state,vm::image,vm::caption,vm::audience,vm::remove,vm::reorder,vm::share,vm::restore,vm::discard,onBack,
        picking=picking,onPick={source->
            if(!picking) {
                picking=true
                val stamp=vm.pickerStamp()
                if(source=="photo") native.pickPhotos(vm.remainingPhotoSlots()) { batch ->
                    scope.launch { picking=false; vm.pickedPhotos(batch,stamp) }
                } else native.pick(source) { raw,bytes -> scope.launch { picking=false; vm.picked(raw,bytes,stamp) } }
            }
        })
}

/** Capture stage until a photo exists, then a full-bleed preview with caption and the inline "Share to" sheet. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun MomentComposerContent(state: MomentComposerState, image: suspend (String)->ByteArray?,
    onCaption: (String)->Unit,onAudience: (String)->Unit,onRemove: (String)->Unit,onReorder: (String,Int)->Unit,
    onShare: ()->Unit,onRetry: ()->Unit,onDiscard: ()->Unit,onBack: ()->Unit,onPick: (String)->Unit,
    picking: Boolean=false, initialCaptionEditing: Boolean=false) {
    val frozen=state.busy || picking || state.record?.publication!=null
    var leave by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(initialCaptionEditing) }
    val back={ if(state.photos.isNotEmpty() || state.record?.caption?.isNotBlank()==true || state.busy) leave=true else onBack() }
    BackHandler(onBack=back)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.mediaBackground)) {
        if(state.photos.isEmpty()) MomentCaptureStage(picking,state.loading,state.error,back,{onPick("camera")},{onPick("photo")},onRetry,Modifier.fillMaxSize())
        else {
            val index=selected.coerceIn(state.photos.indices)
            val photo=state.photos[index]
            val description=stringResource(Res.string.moment_photo_selected,index+1,state.photos.size)
            if(photo.bytes!=null) MomentImage(photo.bytes,description,Modifier.fillMaxSize(),ContentScale.Crop)
            else GuardedMomentImage(photo.remote?.image,image,description,Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().imePadding()) {
                MomentComposerTopBar(editing,!frozen && !state.loading,back,{editing=!editing},Modifier.statusBarsPadding())
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                MomentPhotoStrip(state.photos,index,image,!frozen,state.photos.size<MAX_PHOTOS,{selected=it},
                    onEarlier={onReorder(photo.id,-1);selected=(index-1).coerceAtLeast(0)},
                    onLater={onReorder(photo.id,1);selected=(index+1).coerceAtMost(state.photos.lastIndex)},
                    onRemove={onRemove(photo.id);selected=0},onAdd={onPick("photo")},onCamera={onPick("camera")})
                MomentCaptionPill(state.record?.caption.orEmpty(),editing,!frozen && !state.loading,onCaption,{editing=true},
                    Modifier.align(Alignment.CenterHorizontally).padding(MaterialTheme.spacing.medium))
                MomentShareSheet(state,picking,onAudience,onShare,onRetry,onDiscard,Modifier.weight(1f,fill=false))
            }
        }
    }
    if(leave) AlertDialog(onDismissRequest={leave=false},title={Text(stringResource(Res.string.moment_leave_title))},
        text={Text(stringResource(if(state.busy) Res.string.moment_leave_busy else Res.string.moment_leave_body))},
        confirmButton={TextButton(onClick={leave=false;if(!state.busy) onBack()}) { Text(stringResource(if(state.busy) Res.string.moment_leave_stay else Res.string.moment_leave_keep)) }},
        dismissButton={if(!state.busy) TextButton(onClick={leave=false;onDiscard()}) {Text(stringResource(Res.string.moment_leave_discard))}})
}

@Composable internal fun MomentCaptureStage(picking: Boolean,loading: Boolean,error: String?,onClose: ()->Unit,onCamera: ()->Unit,
    onLibrary: ()->Unit,onRetry: ()->Unit,modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    val space=MaterialTheme.spacing
    val enabled=!picking && !loading
    Column(modifier.safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal=space.medium,vertical=space.small)) {
            MomentChromeButton(Icons.Outlined.Close,stringResource(Res.string.moment_close),onClose,Modifier.align(Alignment.CenterStart))
            MomentPill(stringResource(Res.string.moment_title),Modifier.align(Alignment.Center),container=colors.onMedia.copy(alpha=MomentTokens.titleAlpha))
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=colors.onMedia)
        Column(Modifier.weight(1f).fillMaxWidth().padding(space.large),verticalArrangement=Arrangement.spacedBy(space.medium,Alignment.CenterVertically),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.PhotoCamera,null,Modifier.size(space.huge),tint=colors.onMedia.copy(alpha=MomentTokens.mutedAlpha))
            Text(stringResource(if(picking) Res.string.moment_opening else Res.string.moment_capture_hint),style=MaterialTheme.typography.bodyLarge,
                color=colors.onMedia,textAlign=TextAlign.Center)
            error?.let { MomentError(it,onRetry) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=space.extraLarge,vertical=space.large),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween) {
            MomentLibraryButton(enabled,onLibrary)
            MomentShutter(enabled,onCamera)
            Spacer(Modifier.size(MomentTokens.galleryThumb))
        }
    }
}

@Composable internal fun MomentShutter(enabled: Boolean,onClick: ()->Unit,modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    val description=stringResource(Res.string.moment_shutter)
    Box(modifier.size(MomentTokens.shutterOuter).clip(CircleShape).border(MomentTokens.shutterRing,colors.onMedia,CircleShape)
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).semantics { contentDescription=description },contentAlignment=Alignment.Center) {
        Box(Modifier.size(MomentTokens.shutterInner).clip(CircleShape).background(colors.onMedia.copy(alpha=if(enabled) 1f else MomentTokens.trackAlpha)))
    }
}

@Composable internal fun MomentLibraryButton(enabled: Boolean,onClick: ()->Unit,modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    val description=stringResource(Res.string.moment_library)
    Box(modifier.size(MomentTokens.galleryThumb).clip(MaterialTheme.shapes.medium)
        .border(MomentTokens.galleryBorder,colors.onMedia,MaterialTheme.shapes.medium)
        .background(colors.onMedia.copy(alpha=MomentTokens.trackAlpha))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).semantics { contentDescription=description },contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.PhotoLibrary,null,tint=colors.onMedia)
    }
}

@Composable internal fun MomentComposerTopBar(editing: Boolean,enabled: Boolean,onClose: ()->Unit,onText: ()->Unit,modifier: Modifier=Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal=MaterialTheme.spacing.medium,vertical=MaterialTheme.spacing.small),
        verticalAlignment=Alignment.CenterVertically) {
        MomentChromeButton(Icons.Outlined.Close,stringResource(Res.string.moment_close),onClose)
        Spacer(Modifier.weight(1f))
        val label=stringResource(if(editing) Res.string.moment_caption_done else Res.string.moment_text)
        val description=if(editing) label else stringResource(Res.string.moment_text_description)
        MomentPill(label,Modifier.semantics { contentDescription=description },onClick=onText,enabled=enabled)
    }
}

@Composable internal fun MomentPhotoStrip(photos: List<LocalMomentPhoto>,selected: Int,image: suspend (String)->ByteArray?,enabled: Boolean,
    canAdd: Boolean,onSelect: (Int)->Unit,onEarlier: ()->Unit,onLater: ()->Unit,onRemove: ()->Unit,onAdd: ()->Unit,onCamera: ()->Unit,
    modifier: Modifier=Modifier) {
    val space=MaterialTheme.spacing
    val colors=MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal=space.medium),verticalArrangement=Arrangement.spacedBy(space.small)) {
        if(photos.size>1) Row(horizontalArrangement=Arrangement.spacedBy(space.small)) {
            MomentChromeButton(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(Res.string.moment_photo_earlier),onEarlier,enabled=enabled && selected>0)
            MomentChromeButton(Icons.Outlined.DeleteOutline,stringResource(Res.string.moment_photo_remove),onRemove,enabled=enabled)
            MomentChromeButton(Icons.AutoMirrored.Outlined.ArrowForward,stringResource(Res.string.moment_photo_later),onLater,enabled=enabled && selected<photos.lastIndex)
        } else MomentChromeButton(Icons.Outlined.DeleteOutline,stringResource(Res.string.moment_photo_remove),onRemove,enabled=enabled)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(space.small),verticalAlignment=Alignment.CenterVertically) {
            itemsIndexed(photos,key={_,p->p.id}) { index,p ->
                val description=stringResource(Res.string.moment_photo_select,index+1)
                val shape=MaterialTheme.shapes.medium
                Box(Modifier.size(MomentTokens.stripThumb).clip(shape)
                    .border(if(index==selected) MomentTokens.stripBorder else space.none,colors.onMedia,shape)
                    .clickable(role=Role.Tab) { onSelect(index) }.semantics { this.selected=index==selected }) {
                    if(p.bytes!=null) MomentImage(p.bytes,description,Modifier.fillMaxSize())
                    else GuardedMomentImage(p.remote?.image,image,description,Modifier.fillMaxSize())
                }
            }
            if(canAdd) {
                item(key="add") { MomentChromeButton(Icons.Outlined.AddPhotoAlternate,stringResource(Res.string.moment_photo_add),onAdd,enabled=enabled) }
                item(key="camera") { MomentChromeButton(Icons.Outlined.PhotoCamera,stringResource(Res.string.moment_photo_camera),onCamera,enabled=enabled) }
            }
        }
    }
}

@Composable internal fun MomentCaptionPill(caption: String,editing: Boolean,enabled: Boolean,onCaption: (String)->Unit,onEdit: ()->Unit,
    modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    val focus=remember { FocusRequester() }
    if(editing) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
            Surface(shape=MaterialTheme.shapes.pill,color=colors.mediaBackground.copy(alpha=MomentTokens.pillAlpha)) {
                BasicTextField(caption,onCaption,enabled=enabled,maxLines=4,cursorBrush=SolidColor(colors.onMedia),
                    textStyle=MaterialTheme.typography.headlineSmall.copy(color=colors.onMedia,textAlign=TextAlign.Center),
                    modifier=Modifier.focusRequester(focus).widthIn(min=MomentTokens.shutterOuter*2)
                        .padding(horizontal=MaterialTheme.spacing.gutter,vertical=MaterialTheme.spacing.compact),
                    decorationBox={ inner -> Box(contentAlignment=Alignment.Center) {
                        if(caption.isEmpty()) Text(stringResource(Res.string.moment_caption_hint),style=MaterialTheme.typography.headlineSmall,
                            color=colors.onMedia.copy(alpha=MomentTokens.mutedAlpha))
                        inner()
                    } })
            }
            Text(stringResource(Res.string.moment_caption_count,caption.length),style=MaterialTheme.typography.bodySmall,color=colors.onMedia,
                modifier=Modifier.padding(top=MaterialTheme.spacing.tiny))
        }
    } else if(caption.isNotBlank()) MomentPill(caption,modifier,onClick=onEdit,enabled=enabled,large=true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MomentShareSheet(state: MomentComposerState,picking: Boolean,onAudience: (String)->Unit,onShare: ()->Unit,
    onRetry: ()->Unit,onDiscard: ()->Unit,modifier: Modifier=Modifier) {
    val space=MaterialTheme.spacing
    val colors=MaterialTheme.colorScheme
    val frozen=state.busy || picking || state.record?.publication!=null
    val public=state.record?.visibility=="public"
    Surface(modifier.fillMaxWidth(),shape=BottomSheetDefaults.ExpandedShape,color=colors.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=space.gutter,vertical=space.large),
            verticalArrangement=Arrangement.spacedBy(space.medium)) {
            Text(stringResource(Res.string.moment_share_to),style=MaterialTheme.typography.headlineMedium)
            MomentAudienceOption(stringResource(Res.string.moment_private_title),stringResource(Res.string.moment_private_body),!public,!frozen,{onAudience("private")}) {
                MomentIconPlate(Icons.Outlined.People)
            }
            MomentAudienceOption(stringResource(Res.string.moment_public_title),stringResource(Res.string.moment_public_body),public,!frozen,{onAudience("public")}) {
                MomentIconPlate(Icons.Outlined.Public)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(space.small),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule,null,Modifier.size(space.icon),tint=colors.onSurfaceVariant)
                Text(stringResource(Res.string.moment_disappears_after),style=MaterialTheme.typography.bodyLarge,color=colors.onSurfaceVariant)
            }
            if(public) Text(stringResource(Res.string.moment_public_note),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
            state.error?.let { MomentError(it,if(!state.busy) { {if(state.record?.publication!=null) onShare() else onRetry()} } else null) }
            if(state.error!=null && !state.busy) TextButton(onClick=onDiscard) { Text(stringResource(Res.string.moment_discard)) }
            if(state.busy) {
                Text(state.stage,style=MaterialTheme.typography.bodyMedium)
                if(state.progress==null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
            }
            SriSuButton(stringResource(if(state.record?.publication!=null && !state.busy) Res.string.moment_check_publication else Res.string.moment_share),
                onShare,Modifier.fillMaxWidth().heightIn(min=space.huge),enabled=!state.loading && !state.busy && !picking,
                size=SriSuButtonSize.Large,textStyle=MaterialTheme.typography.titleLarge,
                trailingIcon={ Icon(Icons.AutoMirrored.Outlined.ArrowForward,null) })
        }
    }
}

@Composable internal fun MomentAudienceOption(title: String,body: String,selected: Boolean,enabled: Boolean,onClick: ()->Unit,
    modifier: Modifier=Modifier,leading: @Composable ()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().semantics { role=Role.RadioButton; this.selected=selected },
        shape=MaterialTheme.shapes.large,color=if(selected) colors.secondaryContainer else colors.surfaceContainer) {
        Row(Modifier.padding(horizontal=MaterialTheme.spacing.medium,vertical=MaterialTheme.spacing.gutter),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
            leading()
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleLarge,color=colors.onSurface)
                Text(body,style=MaterialTheme.typography.bodyLarge,color=colors.onSurfaceVariant)
            }
            MomentSelectionMark(selected)
        }
    }
}

@Composable internal fun MomentSelectionMark(selected: Boolean,modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    val size=modifier.size(MomentTokens.selectedMark).clip(CircleShape)
    if(selected) Box(size.background(colors.primary),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Check,null,Modifier.size(MomentTokens.selectedMarkIcon),tint=colors.onPrimary)
    } else Box(size.border(MomentTokens.outline,colors.outline,CircleShape))
}

@Composable internal fun MomentIconPlate(icon: ImageVector,modifier: Modifier=Modifier) {
    Box(modifier.size(MomentTokens.audienceAvatar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment=Alignment.Center) { Icon(icon,null,tint=MaterialTheme.colorScheme.onSurface) }
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
