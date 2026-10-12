package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.theme.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

class ChatMediaViewModel(private val repository: ConversationRepository) : ViewModel() {
    val state = repository.state
    private val _exportError=kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val exportError=_exportError.asStateFlow()
    private val account = state.value.accountId
    private var target: ChatNav.MediaViewer? = null
    fun start(route: ChatNav.MediaViewer) { if(target!=route) {target=route;retry()} }
    fun authorized(value: ConversationState, route: ChatNav.MediaViewer) = account!=null && value.accountId==account && value.roomId==route.roomId
    fun retry() {
        val route=target ?: return
        if(!authorized(state.value,route))return
        if(route.draft)repository.openDraftAttachment(route.attachmentId,route.roomId,route.messageId)
        else repository.openAttachment(route.messageId,route.attachmentId,route.roomId)
    }
    fun dismissExportError() { _exportError.value=null }
    fun export(opened: OpenedChatMedia, native: NativeChatMedia) {
        val route=target ?: return
        if(!authorized(state.value,route) || state.value.openedMedia !== opened)return
        native.export(opened.bytes,opened.media.mime) { result ->
            if(result=="error" && authorized(state.value,route)) _exportError.value="The export could not be completed."
        }
    }
    fun suspendMedia() {
        val route=target ?: return
        val current=state.value
        if(authorized(current,route) && current.mediaRequest?.let {it.messageId==route.messageId && it.attachmentId==route.attachmentId}==true)
            repository.dismissMedia()
    }
    override fun onCleared() { suspendMedia() }
}

@Composable fun ChatMediaScreen(route: ChatNav.MediaViewer, onBack: () -> Unit, viewModel: ChatMediaViewModel = koinViewModel()) {
    ProtectConversation()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val native=rememberNativeChatMedia()
    val scope=rememberCoroutineScope()
    val player=remember(native,viewModel) {ChatPlaybackOwner(native,scope,viewModel.state,{},autoPlayKind="video")}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,player) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {player.stop();viewModel.suspendMedia()} }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);player.close();viewModel.suspendMedia();native.close()}
    }
    LaunchedEffect(route) {viewModel.start(route)}
    val authorized=viewModel.authorized(state,route)
    val opened=state.openedMedia?.takeIf {authorized && it.messageId==route.messageId && it.media.descriptor.id==route.attachmentId && it.media.kind!="voice"}
    var confirmExport by remember(route) { mutableStateOf(false) }
    val caption=state.messages.firstOrNull {it.id==route.messageId}?.let {it.content as? ChatContent.Media}?.caption.orEmpty()
    ChatMediaViewerSurface(opened,authorized && state.mediaLoading,
        if(!authorized)"This conversation is unavailable." else state.mediaRequestError,
        caption,onBack,viewModel::retry,video={media ->
            NativeChatVideo(native,Modifier.fillMaxSize())
        },videoControls={ VideoControls(player,viewModel::retry) },onExport=if(opened!=null)({confirmExport=true}) else null)
    exportError?.let { AlertDialog(onDismissRequest=viewModel::dismissExportError,text={Text(it)},
        confirmButton={TextButton(onClick=viewModel::dismissExportError) {Text("Close")}}) }
    if(confirmExport && opened!=null) AlertDialog(onDismissRequest={confirmExport=false},title={Text("Export this attachment?")},
        text={Text("The exported copy will leave SriSu’s protected chat storage. Deleting the message cannot erase that copy.")},
        confirmButton={TextButton(onClick={confirmExport=false;viewModel.export(opened,native)}) {Text("Choose destination")}},
        dismissButton={TextButton(onClick={confirmExport=false}) {Text("Cancel")}})
}

/** A full destination surface; never presented as a Dialog or sheet. */
@Composable internal fun ChatMediaViewerSurface(opened: OpenedChatMedia?, loading: Boolean, error: String?, caption: String,
    onBack: () -> Unit, onRetry: () -> Unit, video: @Composable (ChatMedia) -> Unit = {}, videoControls: @Composable () -> Unit = {}, onExport: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.mediaBackground,contentColor=MaterialTheme.colorScheme.onMedia) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=MaterialTheme.spacing.small),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to conversation")}
                Text(if(opened?.media?.kind=="video")"Video" else if(opened?.media?.kind=="photo")"Photo" else "Attachment",
                    modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                if(onExport!=null) IconButton(onClick=onExport) {Icon(Icons.Outlined.Download,"Save or export attachment")}
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(),contentAlignment=Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(Modifier.semantics {contentDescription="Opening attachment"},color=MaterialTheme.colorScheme.onMedia)
                    error!=null || opened==null -> Column(Modifier.padding(MaterialTheme.spacing.large),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(error ?: "This attachment is no longer available.",style=MaterialTheme.typography.bodyMedium)
                        TextButton(onClick=onRetry,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onMedia)) {Text("Retry")}
                    }
                    opened.media.kind=="photo" -> key(opened.media.descriptor.id,opened.bytes) { ZoomableChatPhoto(opened.bytes,onRetry) }
                    opened.media.kind=="video" -> {
                        val aspect=if(opened.media.width>0 && opened.media.height>0) opened.media.width.toFloat()/opened.media.height else 16f/9f
                        val width=minOf(maxWidth,maxHeight*aspect)
                        Box(Modifier.width(width).height(width/aspect)) {video(opened.media)}
                    }
                    else -> Text("This attachment type is unavailable.")
                }
            }
            if(opened?.media?.kind=="video" && !loading && error==null) videoControls()
            if(caption.isNotBlank() && opened!=null) Text(caption,Modifier.fillMaxWidth().heightIn(max=112.dp)
                .verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.medium),style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun ZoomableChatPhoto(bytes: ByteArray,onRetry: () -> Unit) {
    val decoded by produceState<Pair<Boolean,ImageBitmap?>>(false to null,bytes) {
        value=true to withContext(Dispatchers.Default) {decodePrivatePhoto(bytes)}
    }
    var scale by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(Offset.Zero)}
    var extent by remember {mutableStateOf(Offset.Zero)}
    val transform=rememberTransformableState {zoom,offset,_ ->
        scale=(scale*zoom).coerceIn(1f,5f)
        val limit=extent*((scale-1f)/2f)
        pan=Offset((pan.x+offset.x).coerceIn(-limit.x,limit.x),(pan.y+offset.y).coerceIn(-limit.y,limit.y))
    }
    val image=decoded.second
    if(image!=null) BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().transformable(transform),contentAlignment=Alignment.Center) {
        val density=androidx.compose.ui.platform.LocalDensity.current
        SideEffect {extent=with(density) {Offset(maxWidth.toPx(),maxHeight.toPx())}}
        Image(image,"Shared photo",Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=pan.x;translationY=pan.y},contentScale=ContentScale.Fit)
    } else if(!decoded.first) CircularProgressIndicator(color=MaterialTheme.colorScheme.onMedia)
    else Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text("This photo could not be decoded.",style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick=onRetry,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onMedia)) {Text("Retry")}
    }
}

@Composable internal fun VideoControls(player: ChatPlaybackOwner, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=MaterialTheme.spacing.medium)) {
        if(player.playbackError!=null) {
            Text(player.playbackError.orEmpty(),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onRetry,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onMedia)) {Text("Retry video")}
        }
        else Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=player::togglePlayback,enabled=!player.playbackLoading) {
                if(player.playbackLoading) CircularProgressIndicator(Modifier.size(24.dp).semantics {contentDescription="Buffering video"},color=MaterialTheme.colorScheme.onMedia,strokeWidth=2.dp)
                else Icon(if(player.playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(player.playing)"Pause video" else "Play video")
            }
            Slider(value=if(player.duration>0)(player.position.toFloat()/player.duration).coerceIn(0f,1f) else 0f,
                onValueChange=player::seek,enabled=player.duration>0 && !player.playbackLoading,
                modifier=Modifier.weight(1f).semantics {contentDescription="Seek video"},
                colors=SliderDefaults.colors(thumbColor=MaterialTheme.colorScheme.onMedia,activeTrackColor=MaterialTheme.colorScheme.onMedia))
        }
        Text("${mediaTime(player.position)} / ${mediaTime(player.duration)}",Modifier.padding(bottom=MaterialTheme.spacing.small),style=MaterialTheme.typography.bodySmall)
    }
}
