@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

@Composable internal fun PrivatePhoto(bytes: ByteArray?, description: String, modifier: Modifier = Modifier, fit: Boolean = false) {
    val image by produceState<ImageBitmap?>(null,bytes) {
        value=withContext(Dispatchers.Default) {bytes?.let {decodePrivatePhoto(it)}}
    }
    image?.let {Image(it,description,modifier,contentScale=if(fit)ContentScale.Fit else ContentScale.Crop)}
        ?: Box(modifier,contentAlignment=Alignment.Center) {Text(description,style=MaterialTheme.typography.labelMedium)}
}
private fun thumbnail(media: ChatMedia): ByteArray? = try {media.thumbnail?.takeIf {it.isNotBlank() && it.length<=8192}?.let(Base64::decode)} catch(_: Exception) {null}

@Composable internal fun MediaPreview(media: ChatMedia,onOpen: ()->Unit,modifier: Modifier=Modifier) {
    Column(modifier.clickable(onClickLabel="Open ${media.kind}",onClick=onOpen)) {
        if(media.kind=="voice") {
            Text("Voice message · ${mediaTime(media.durationMs)}",style=MaterialTheme.typography.bodyMedium)
            VoiceWaveform(media.waveform,Modifier.fillMaxWidth().height(32.dp))
            Text("Tap to play",style=MaterialTheme.typography.labelSmall)
        } else {
            PrivatePhoto(remember(media.thumbnail) {thumbnail(media)},if(media.kind=="video") "Video preview" else "Photo preview",
                Modifier.fillMaxWidth().height(160.dp))
            if(media.kind=="video") Text("Play video · ${mediaTime(media.durationMs)}",style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable internal fun VoiceWaveform(levels: List<Int>,modifier: Modifier=Modifier) {
    val color=MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics {contentDescription="Recorded audio waveform"}) {
        val samples=levels.ifEmpty {listOf(1)}
        val step=size.width/samples.size
        samples.forEachIndexed {index,level ->
            val amplitude=(level.coerceIn(1,100)/100f*size.height/2).coerceAtLeast(1f)
            drawLine(color,Offset((index+0.5f)*step,size.height/2-amplitude),Offset((index+0.5f)*step,size.height/2+amplitude),strokeWidth=(step*.55f).coerceIn(1f,4f))
        }
    }
}
internal fun mediaTime(milliseconds: Long): String {
    val seconds=milliseconds.coerceAtLeast(0)/1000
    return "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
}
@Composable internal fun MediaDraftPreview(state: ConversationState,repository: ConversationRepository) {
    state.mediaDraft?.let {draft ->
        Text("Ready to send · ${draft.items.size}/3",style=MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            draft.items.forEach {media -> Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.width(160.dp).padding(MaterialTheme.spacing.small)) {
                    MediaPreview(media,{repository.openDraftAttachment(media.descriptor.id)})
                    TextButton(onClick={repository.removeDraftAttachment(media.descriptor.id)}) {Text("Remove")}
                }
            } }
        }
    }
}
@Composable internal fun ChatAttachmentSheet(controller: ChatMediaController,onDismiss: ()->Unit,onCard: (String)->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.gutter).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Text("Share something",style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={onDismiss();onCard("spark")}) {Text("Spark")}
            TextButton(onClick={onDismiss();onCard("ask")}) {Text("Ask")}
            TextButton(onClick={onDismiss();onCard("planner")}) {Text("Planner")}
            TextButton(onClick={onDismiss();onCard("moment")}) {Text("Moment")}
            TextButton(onClick={onDismiss();controller.pick("photo")}) {Text("Photo library")}
            TextButton(onClick={onDismiss();controller.pick("video")}) {Text("Choose a video")}
            TextButton(onClick={onDismiss();controller.pick("camera")}) {Text("Take a photo")}
            TextButton(onClick={onDismiss();controller.record(true)}) {Text("Record a voice draft")}
            Text("Up to 3 attachments. Videos: H.264/AAC, 1080p, 2 minutes, 16 MiB. Photos are resized and location tags removed.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable internal fun VoiceRecordControl(controller: ChatMediaController,enabled: Boolean) {
    val enabledNow by rememberUpdatedState(enabled)
    // The accessibility action starts locked recording with explicit controls.
    // The pointer gesture supports hold/release, left cancellation and up locking.
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.secondaryContainer,
        modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics {
            role=Role.Button;contentDescription="Record voice message"
            onClick("Start locked recording") {if(enabled)controller.record(true);enabled}
        }.pointerInput(controller) {
            awaitEachGesture {
                val down=awaitFirstDown()
                if(!enabledNow)return@awaitEachGesture
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
        }) {Box(contentAlignment=Alignment.Center,modifier=Modifier.padding(MaterialTheme.spacing.small)) {Text("Voice",style=MaterialTheme.typography.labelMedium)} }
}
@Composable internal fun RecordingPanel(controller: ChatMediaController) {
    Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small)) {
        Text("${if(controller.locked) "Recording locked" else "Recording"} · ${mediaTime(controller.elapsed)}",style=MaterialTheme.typography.titleSmall,
            modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
        VoiceWaveform(controller.levels,Modifier.fillMaxWidth().height(40.dp))
        if(!controller.locked) Text("Release to send · slide left to cancel · slide up to lock",style=MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick={controller.stop(true)}) {Text("Delete recording")}
            if(controller.locked) {
                TextButton(onClick={controller.stop(false)}) {Text("Preview")}
                TextButton(onClick={controller.stop(false,send=true)}) {Text("Send")}
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
    Dialog(onDismissRequest=controller::dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(MaterialTheme.spacing.medium)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=controller::dismiss) {Text("Close")}
                Text(opened.media.kind.replaceFirstChar {it.uppercase()},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={confirmExport=true}) {Text("Save / export")}
            }
            when(opened.media.kind) {
                "photo" -> Box(Modifier.weight(1f).fillMaxWidth().transformable(transform),contentAlignment=Alignment.Center) {
                    PrivatePhoto(opened.bytes,"Shared photo",Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=pan.x;translationY=pan.y},fit=true)
                }
                "video" -> Column(Modifier.weight(1f),verticalArrangement=Arrangement.Center) {
                    PrivatePhoto(remember(opened.media.thumbnail) {thumbnail(opened.media)},"Video preview",Modifier.fillMaxWidth().height(240.dp),true)
                    Button(onClick={controller.play(opened)}) {Text("Play video · ${mediaTime(opened.media.durationMs)}")}
                }
                else -> Column(Modifier.weight(1f),verticalArrangement=Arrangement.Center) {
                    VoiceWaveform(opened.media.waveform,Modifier.fillMaxWidth().height(80.dp))
                    Slider(value=if(controller.duration>0)controller.position.toFloat()/controller.duration else 0f,
                        onValueChange=controller::seek,modifier=Modifier.semantics {contentDescription="Seek voice message"})
                    Text("${mediaTime(controller.position)} / ${mediaTime(opened.media.durationMs)}")
                    Row {
                        Button(onClick=controller::togglePlayback) {Text(if(controller.playing)"Pause" else "Play")}
                        TextButton(onClick=controller::speed) {Text("${controller.speed}×")}
                    }
                }
            }
        } }
    }
    if(confirmExport) AlertDialog(onDismissRequest={confirmExport=false},title={Text("Export this attachment?")},
        text={Text("The exported copy will leave SriSu’s protected chat storage. Deleting the message cannot erase that copy.")},
        confirmButton={TextButton(onClick={confirmExport=false;controller.export(opened)}) {Text("Choose destination")}},
        dismissButton={TextButton(onClick={confirmExport=false}) {Text("Cancel")}})
}
