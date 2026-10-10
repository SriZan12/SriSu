@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

@Composable private fun privatePhotoBitmap(bytes: ByteArray?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null,bytes) {
        value=withContext(Dispatchers.Default) {bytes?.let {decodePrivatePhoto(it)}}
    }
    return image
}
@Composable internal fun PrivatePhoto(bytes: ByteArray?, description: String, modifier: Modifier = Modifier, fit: Boolean = false) {
    val image=privatePhotoBitmap(bytes)
    image?.let {Image(it,description,modifier,contentScale=if(fit)ContentScale.Fit else ContentScale.Crop)}
        ?: PhotoUnavailable(modifier)
}
@Composable private fun PhotoUnavailable(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Icon(ChatIcons.Image, null, Modifier.size(28.dp))
                Text("Preview unavailable",style=MaterialTheme.typography.bodySmall)
            }
        }
}
private fun thumbnail(media: ChatMedia): ByteArray? = try {media.thumbnail?.takeIf {it.isNotBlank() && it.length<=8192}?.let(Base64::decode)} catch(_: Exception) {null}

@Composable internal fun MediaPreview(media: ChatMedia,onOpen: ()->Unit,modifier: Modifier=Modifier, savedOffline: Boolean = false) {
    Column(modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel="Open ${media.kind}",onClick=onOpen)) {
        if(media.kind=="voice") {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Surface(shape=MaterialTheme.shapes.pill, color=MaterialTheme.colorScheme.primary, modifier=Modifier.size(44.dp)) {
                    Box(contentAlignment=Alignment.Center) { Icon(Icons.Outlined.PlayArrow, "Play voice message", tint=MaterialTheme.colorScheme.onPrimary) }
                }
                Column(Modifier.weight(1f)) {
                    VoiceWaveform(media.waveform,Modifier.fillMaxWidth().height(28.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(mediaTime(media.durationMs),style=MaterialTheme.typography.bodySmall)
                        Icon(if(savedOffline) ChatIcons.Mic else ChatIcons.Download, null, Modifier.size(16.dp))
                    }
                }
            }
        } else {
            val preview=privatePhotoBitmap(remember(media.thumbnail) {thumbnail(media)})
            Box(Modifier.fillMaxWidth().aspectRatio(if(media.width>0 && media.height>0)
                (media.width.toFloat()/media.height).coerceIn(.75f,1.7f) else 1.35f)) {
                if(preview!=null) Image(preview,if(media.kind=="video")"Video preview" else "Photo preview",
                    Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else if(media.kind=="video") Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                else PhotoUnavailable(Modifier.fillMaxSize())
                if(media.kind=="video") Surface(shape=MaterialTheme.shapes.pill, color=MaterialTheme.colorScheme.mediaBackground.copy(alpha=.75f),
                    contentColor=MaterialTheme.colorScheme.onMedia, modifier=Modifier.align(Alignment.Center).size(48.dp)) {
                    Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.PlayArrow,"Play video")}
                }
                if(media.kind=="video" || !savedOffline) Surface(shape=MaterialTheme.shapes.small,
                    color=MaterialTheme.colorScheme.mediaBackground.copy(alpha=.75f), contentColor=MaterialTheme.colorScheme.onMedia,
                    modifier=Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.small)) {
                    Row(Modifier.padding(MaterialTheme.spacing.tiny),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                        if(!savedOffline)Icon(ChatIcons.Download,"Download to open",Modifier.size(16.dp))
                        if(media.kind=="video")Text(mediaTime(media.durationMs),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // Keep the missing-preview label outside the play affordance and duration badge.
            if(media.kind=="video" && preview==null) Text("Preview unavailable",
                Modifier.padding(vertical=MaterialTheme.spacing.tiny),style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable internal fun VoiceWaveform(levels: List<Int>,modifier: Modifier=Modifier,progress: Float? = null) {
    val color=MaterialTheme.colorScheme.secondary
    val muted=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.4f)
    Canvas(modifier.semantics {contentDescription=if(levels.isEmpty())"Audio waveform unavailable" else "Recorded audio waveform"}) {
        if(levels.isEmpty()) {
            drawLine(muted,Offset(0f,size.height/2),Offset(size.width,size.height/2),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
        } else {
            val step=size.width/levels.size
            levels.forEachIndexed {index,level ->
                val amplitude=(level.coerceIn(0,100)/100f*size.height/2).coerceAtLeast(1f)
                drawLine(if(progress==null || index.toFloat()/levels.size<=progress)color else muted,
                    Offset((index+0.5f)*step,size.height/2-amplitude),Offset((index+0.5f)*step,size.height/2+amplitude),
                    strokeWidth=(step*.55f).coerceIn(1f,4.dp.toPx()),cap=StrokeCap.Round)
            }
        }
    }
}
internal fun mediaTime(milliseconds: Long): String {
    val seconds=milliseconds.coerceAtLeast(0)/1000
    return "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
}
@Composable internal fun MediaDraftPreview(state: ConversationState,repository: ConversationRepository) {
    state.mediaDraft?.let {draft ->
        Text("Ready to send · ${draft.items.size}/3",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            draft.items.forEach {media -> Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.width(if(media.kind=="voice")260.dp else 144.dp).padding(MaterialTheme.spacing.small)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(media.kind=="voice")"Voice draft" else if(media.kind=="video")"Video" else "Photo",
                            Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        IconButton(onClick={repository.removeDraftAttachment(media.descriptor.id)}) {Icon(Icons.Outlined.Close,"Remove ${media.kind}")}
                    }
                    MediaPreview(media,{repository.openDraftAttachment(media.descriptor.id)},savedOffline=true)
                }
            } }
        }
    }
}
@Composable internal fun ChatAttachmentSheet(controller: ChatMediaController,onDismiss: ()->Unit,onCard: (String)->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Text("Share something",style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                ShareTile("Photos",ChatIcons.Image,Modifier.weight(1f)) {onDismiss();controller.pick("photo")}
                ShareTile("Video",ChatIcons.Videocam,Modifier.weight(1f)) {onDismiss();controller.pick("video")}
                ShareTile("Camera",ChatIcons.PhotoCamera,Modifier.weight(1f)) {onDismiss();controller.pick("camera")}
            }
            ChatActionRow("Record a voice draft",ChatIcons.Mic,{onDismiss();controller.record(true)})
            HorizontalDivider()
            Text("Something for the two of you",style=MaterialTheme.typography.titleSmall)
            ChatActionRow("Spark",ChatIcons.AutoAwesome,{onDismiss();onCard("spark")})
            ChatActionRow("Ask",ChatIcons.ChatBubbleOutline,{onDismiss();onCard("ask")})
            ChatActionRow("Planner",ChatIcons.Event,{onDismiss();onCard("planner")})
            ChatActionRow("Moment",ChatIcons.Collections,{onDismiss();onCard("moment")})
            Text("Up to 3 attachments. Videos up to 2 minutes and 16 MB. Location tags are removed from photos.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun ShareTile(label:String,icon:ImageVector,modifier:Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical=MaterialTheme.spacing.medium,horizontal=MaterialTheme.spacing.small),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.secondary)
            Text(label,style=MaterialTheme.typography.titleSmall)
        }
    }
}
@Composable internal fun VoiceRecordControl(controller: ChatMediaController,enabled: Boolean) {
    val enabledNow by rememberUpdatedState(enabled)
    // The accessibility action starts locked recording with explicit controls.
    // The pointer gesture supports hold/release, left cancellation and up locking.
    Surface(shape=MaterialTheme.shapes.pill,color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
        modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics {
            role=Role.Button;contentDescription="Record voice message"
            if(!enabled)disabled()
            onClick("Start locked recording") {if(enabled)controller.record(true);enabled}
        }.pointerInput(controller) {
            awaitEachGesture {
                val down=awaitFirstDown()
                if(!enabledNow)return@awaitEachGesture
                val quickRelease=withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {waitForUpOrCancellation()}
                if(quickRelease!=null) {quickRelease.consume();controller.record(true);return@awaitEachGesture}
                if(currentEvent.changes.none {it.pressed} || currentEvent.changes.any {it.isConsumed})return@awaitEachGesture
                controller.record()
                val origin=down.position;var locked=false;var cancelled=false;var released=false;var finished=false
                try {
                    do {
                        val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                        if(change.isConsumed) {cancelled=true;break}
                        val offset=change.position-origin
                        if(!locked && offset.x < -80.dp.toPx()) {cancelled=true;change.consume();break}
                        if(!locked && offset.y < -80.dp.toPx()) {locked=true;controller.lock();change.consume()}
                        if(!change.pressed) {released=true;break}
                    } while(true)
                    if(cancelled || (!locked && !released))controller.stop(true)
                    else if(!locked)controller.stop(false,send=true)
                    finished=true
                } finally {if(!finished)controller.stop(true)}
            }
        }) {Box(contentAlignment=Alignment.Center,modifier=Modifier.size(48.dp)) {Icon(ChatIcons.Mic,null)} }
}
@Composable internal fun RecordingPanel(controller: ChatMediaController) {
    Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Icon(if(controller.locked)Icons.Outlined.Lock else ChatIcons.Mic,null,tint=MaterialTheme.colorScheme.error,modifier=Modifier.size(MaterialTheme.spacing.icon))
            Text("Recording · ${mediaTime(controller.elapsed)}",style=MaterialTheme.typography.titleSmall)
        }
        VoiceWaveform(controller.levels,Modifier.fillMaxWidth().height(28.dp))
        if(!controller.locked) Text("Release to send · slide left to cancel · slide up to lock",style=MaterialTheme.typography.bodySmall)
        Row {
            IconButton(onClick={controller.stop(true)}) {Icon(ChatIcons.DeleteOutline,"Delete recording")}
            if(controller.locked) {
                TextButton(onClick={controller.stop(false)}) {Icon(ChatIcons.Stop,null);Text("Preview")}
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick={controller.stop(false,send=true)}) {Icon(Icons.AutoMirrored.Filled.Send,"Send recording")}
            }
        }
    }
}
@Composable internal fun ChatMediaViewer(opened: OpenedChatMedia,controller: ChatMediaController) {
    var confirmExport by remember(opened.media.descriptor.id) {mutableStateOf(false)}
    var scale by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(Offset.Zero)}
    val transform=rememberTransformableState {zoom,offset,_ ->scale=(scale*zoom).coerceIn(1f,5f);pan=if(scale==1f)Offset.Zero else pan+offset}
    LaunchedEffect(opened.media.descriptor.id) {if(opened.media.kind=="voice")controller.play(opened)}
    if(opened.media.kind=="voice") ModalBottomSheet(onDismissRequest=controller::dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(MaterialTheme.spacing.gutter),
            verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Voice message",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick=controller::dismiss) {Icon(Icons.Outlined.Close,"Close voice message")}
            }
            VoicePlaybackPanel(opened.media,controller.playing,controller.position,controller.duration,controller.speed,
                controller.playbackLoading,controller.playbackError,
                onPlay={if(controller.playbackError!=null)controller.play(opened) else controller.togglePlayback()},
                onSeek=controller::seek,onSpeed=controller::speed)
            TextButton(onClick={confirmExport=true}) {Icon(ChatIcons.Download,null);Spacer(Modifier.width(MaterialTheme.spacing.small));Text("Save / export")}
        }
    } else Dialog(onDismissRequest=controller::dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(MaterialTheme.spacing.medium)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=controller::dismiss) {Icon(Icons.Outlined.Close,"Close attachment")}
                Text(opened.media.kind.replaceFirstChar {it.uppercase()},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={confirmExport=true}) {Text("Save / export")}
            }
            when(opened.media.kind) {
                "photo" -> Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().transformable(transform),contentAlignment=Alignment.Center) {
                    PrivatePhoto(opened.bytes,"Shared photo",Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=pan.x;translationY=pan.y},fit=true)
                }
                "video" -> Column(Modifier.weight(1f),verticalArrangement=Arrangement.Center) {
                    PrivatePhoto(remember(opened.media.thumbnail) {thumbnail(opened.media)},"Video preview",Modifier.fillMaxWidth().height(240.dp),true)
                    Button(onClick={controller.play(opened)}) {Text("Play video · ${mediaTime(opened.media.durationMs)}")}
                }
                else -> Text("This attachment type is unavailable.")
            }
            controller.playbackError?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
        } }
    }
    if(confirmExport) AlertDialog(onDismissRequest={confirmExport=false},title={Text("Export this attachment?")},
        text={Text("The exported copy will leave SriSu’s protected chat storage. Deleting the message cannot erase that copy.")},
        confirmButton={TextButton(onClick={confirmExport=false;controller.export(opened)}) {Text("Choose destination")}},
        dismissButton={TextButton(onClick={confirmExport=false}) {Text("Cancel")}})
}

@Composable internal fun VoicePlaybackPanel(media: ChatMedia, playing: Boolean, position: Long, duration: Long,
    speed: Float, loading: Boolean, error: String?, onPlay: () -> Unit, onSeek: (Float) -> Unit, onSpeed: () -> Unit) {
    val fraction=if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f) else 0f
    Column(verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        VoiceWaveform(media.waveform,Modifier.fillMaxWidth().height(56.dp),fraction)
        Slider(value=fraction,onValueChange=onSeek,enabled=duration>0 && !loading && error==null,
            modifier=Modifier.semantics {contentDescription="Seek voice message"})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("${mediaTime(position)} / ${mediaTime(if(duration>0)duration else media.durationMs)}",
                modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            FilledIconButton(onClick=onPlay,enabled=!loading,modifier=Modifier.size(56.dp)) {
                if(loading)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary)
                else Icon(if(error!=null)Icons.Outlined.Refresh else if(playing)ChatIcons.Pause else Icons.Outlined.PlayArrow,
                    if(error!=null)"Retry playback" else if(playing)"Pause voice message" else "Play voice message")
            }
            TextButton(onClick=onSpeed,enabled=!loading && error==null && duration>0,
                modifier=Modifier.semantics {contentDescription="Playback speed, $speed times"}) {Text("${speed}×")}
        }
        error?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
    }
}
