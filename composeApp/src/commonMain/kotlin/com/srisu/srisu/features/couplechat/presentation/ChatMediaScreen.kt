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

@Composable fun ChatMediaScreen(route: ChatNav.MediaViewer, onBack: () -> Unit,
    viewModel: com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel) {
    ProtectConversation()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val native = rememberNativeChatMedia()
    val scope = rememberCoroutineScope()
    val player = remember(native, viewModel) { ChatPlaybackOwner(native, scope, viewModel.uiState, {}, autoPlayKind = "video") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun retry() { if (route.draft) viewModel.openDraftAttachment(route.messageId) else viewModel.openAttachment(route.messageId, route.messageId) }
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { player.stop(); viewModel.dismissMedia() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.close(); native.close(); viewModel.returnFromMedia() }
    }
    LaunchedEffect(route) { retry() }
    val authorized = state.roomId == route.roomId && state.accountId != null
    val opened = state.openedMedia?.takeIf { authorized && it.messageId == route.messageId && it.media.kind != "voice" }
    val caption = (state.messages.firstOrNull { it.id == route.messageId }?.content as? ChatContent.Media)?.caption.orEmpty()
    ChatMediaViewerSurface(opened, authorized && state.mediaLoading,
        if (!authorized) "This conversation is unavailable." else state.mediaRequestError, caption, onBack, ::retry,
        video = { NativeChatVideo(native, Modifier.fillMaxSize()) }, videoControls = { VideoControls(player, ::retry) })
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
