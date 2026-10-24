@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.srisu.srisu.features.chat.matrix.presentation

import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import com.srisu.srisu.theme.planner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import com.srisu.srisu.features.couplechat.presentation.*
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*
import kotlinx.coroutines.flow.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.chat.matrix.MatrixChatMessage
import com.srisu.srisu.theme.ChatTokens
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.chat_offline
import srisu.composeapp.generated.resources.chat_secure_error
import srisu.composeapp.generated.resources.chat_secure_error_title
import srisu.composeapp.generated.resources.chat_secure_not_available
import srisu.composeapp.generated.resources.chat_secure_today
import srisu.composeapp.generated.resources.chat_secure_unavailable_title
import srisu.composeapp.generated.resources.chat_secure_yesterday
import kotlin.time.Clock
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.AutoAwesome

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SecureChatScreen(viewModel: SecureChatViewModel, onBack: () -> Unit,
    onOpenPlans: (Long?)->Unit = {},
    onOpenChallenges: (String?)->Unit = {},
    onOpenSparks: (String?)->Unit = {},
    onOpenMoment: (com.srisu.srisu.features.moments.data.MomentReference)->Unit = {},
    onOpenMedia: (String, Boolean) -> Unit = { _, _ -> }) {
    val state by viewModel.state.collectAsState()
    val momentReply by viewModel.momentReply.collectAsState()
    com.srisu.srisu.features.moments.presentation.MomentLifecycle(viewModel::destinationVisible)
    val ui by viewModel.uiState.collectAsState()
    val validationError by viewModel.composerValidationError.collectAsState()
    val partner by viewModel.partner.collectAsState()
    val availability = state?.availability ?: MatrixChatAvailability.Loading
    val ready = availability is MatrixChatAvailability.Ready
    var appearance by rememberSaveable { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var jump by remember { mutableStateOf<String?>(null) }
    val media = rememberChatMediaController(viewModel)
    val copy = rememberCopyPrivateText()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    ProtectConversation()
    PrivateChatClipboard {
        SriSuChatTheme(ChatThemePreset.fromId(ui.preferences.chatTheme)) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(topBar = { ChatHeader(partner, availability, onBack) { keyboard?.hide(); appearance = true } },
                    containerColor = MaterialTheme.colorScheme.background) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        (availability as? MatrixChatAvailability.Offline)?.let { ChatOfflineBanner(it.message, viewModel::retry) }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                availability is MatrixChatAvailability.Error -> ChatStatusState(
                                    stringResource(Res.string.chat_secure_error_title), availability.message,
                                    icon = painterResource(Res.drawable.chat_offline), action = viewModel::retry, modifier = Modifier.fillMaxSize())
                                availability is MatrixChatAvailability.Unavailable -> ChatStatusState(
                                    stringResource(Res.string.chat_secure_unavailable_title), availability.message,
                                    vector = Icons.Filled.Lock, modifier = Modifier.fillMaxSize())
                                availability is MatrixChatAvailability.Loading -> ChatLoadingState(Modifier.fillMaxSize())
                                state?.messages.isNullOrEmpty() -> ConversationEmpty(true, {}, viewModel::updateMessageInput, Modifier.fillMaxSize(), composing = ui.draft.isNotBlank())
                                else -> ChatTimelineList(state!!.messages, viewModel, ui, media, partner,
                                    onOpenPlans = onOpenPlans, onOpenChallenges = onOpenChallenges, onOpenSparks = onOpenSparks, onOpenMoment = onOpenMoment, onMenu = { selected = it }, jump = jump, onJump = { jump = it },
                                    onMedia = { id -> keyboard?.hide(); media.handoffToViewer(); viewModel.coverWithMedia(); onOpenMedia(id, false) })
                            }
                        }
                        (validationError ?: ui.error)?.let { error ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                                Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                            }
                        }
                        if (state?.failedChangeIds?.isNotEmpty() == true) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                                Text("A reaction or deletion wasn’t saved.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = viewModel::retryFailedChanges, enabled = ready) { Text("Retry") }
                            }
                        }
                        momentReply?.let { reference ->
                            Row(Modifier.fillMaxWidth().padding(horizontal=MaterialTheme.spacing.gutter), verticalAlignment=Alignment.CenterVertically) {
                                Text("Replying to a Moment",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                                TextButton(onClick={viewModel.setMomentReply(null)}) { Text("Cancel") }
                            }
                        }
                        ConversationComposer(ui, busy = !ready || media.preparing || state?.sending == true, recording = media.recording,
                            onDraft = viewModel::updateMessageInput, onSend = viewModel::sendMessage,
                            onAttach = { keyboard?.hide(); attachments = true }, onCancelReply = { viewModel.setReply(null) },
                            voice = { VoiceRecordControl(media, ready && !media.preparing) }) {
                            if (media.recording) RecordingPanel(media)
                            if (media.preparing) { Text("Preparing attachment…", style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            MediaDraftPreview(ui, viewModel, media) { id, _ ->
                                keyboard?.hide(); media.handoffToViewer(); viewModel.coverWithMedia(); onOpenMedia(id, true)
                            }
                        }
                    }
                }
                RomanticMessageOverlay(viewModel.romanticEffects, ui.accountId, ui.roomId,
                    ready && !appearance && !attachments && selected == null && !media.recording,
                    Modifier.matchParentSize().imePadding().safeDrawingPadding())
            }
        }
    }
    if (appearance) ChatAppearanceSheet(ui.preferences.chatTheme, { viewModel.chatTheme(it); appearance = false }, { appearance = false })
    if (attachments) ChatAttachmentSheet(media, { attachments = false }, { onOpenSparks(null) }, { onOpenChallenges(null) }, { onOpenPlans(null) })
    val message = ui.messages.firstOrNull { it.id == selected }
    if (message != null) ModalBottomSheet(onDismissRequest = { selected = null }) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.gutter).navigationBarsPadding()) {
            Text("Message options", style = MaterialTheme.typography.titleMedium)
            if (ready && message.transport == ChatTransport.SENT && !message.deleted && !message.unavailable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("❤️", "😂", "🥰", "😮", "😢", "👍").forEach { emoji ->
                        TextButton(onClick = { viewModel.react(message.id, if (message.reactions[ui.accountId] == emoji) null else emoji); selected = null },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "React $emoji" }) { Text(emoji) }
                    }
                }
                ChatActionRow("Reply", ChatIcons.Reply, { viewModel.setReply(message.id); selected = null })
                if (message.reactions.containsKey(ui.accountId)) ChatActionRow("Remove my reaction", ChatIcons.RemoveCircleOutline,
                    { viewModel.react(message.id, null); selected = null })
                if (message.authorId == ui.accountId) ChatActionRow("Delete for everyone", ChatIcons.DeleteOutline, { deleting = message.id; selected = null }, true)
            }
            val body = when(val content = message.content) { is ChatContent.Text -> content.body; is ChatContent.Media -> content.caption; is ChatContent.Moment -> content.body; else -> "" }
            if (body.isNotBlank() && !message.deleted && !message.unavailable) ChatActionRow("Copy text", ChatIcons.ContentCopy, { copy(body); selected = null })
        }
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete for everyone?") },
        text = { Text("This replaces the message with a deletion notice. Exported copies cannot be erased.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteMessage(id); deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

@Composable
private fun ChatTimelineList(messages: List<MatrixChatMessage>, viewModel: SecureChatViewModel,
    ui: ConversationState, media: ChatMediaController, partner: com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity?,
    onOpenPlans: (Long?)->Unit = {},
    onOpenChallenges: (String?)->Unit = {},
    onOpenSparks: (String?)->Unit = {},
    onOpenMoment: (com.srisu.srisu.features.moments.data.MomentReference)->Unit, onMenu: (String) -> Unit, jump: String?, onJump: (String?) -> Unit, onMedia: (String) -> Unit) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    // Newest first, rendered bottom-up: the conversation anchors to the composer like other messengers.
    val items = remember(messages, timeZone) { buildChatTimeline(messages, timeZone).asReversed() }
    val displayMessages = remember(ui.messages) { ui.messages.associateBy { it.id } }
    val today = remember(messages, timeZone) { Clock.System.now().toLocalDateTime(timeZone).date }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motionEnabled = scope.coroutineContext[MotionDurationScale]?.scaleFactor != 0f
    val loadAlpha = rememberChatLoadAlpha(motionEnabled)
    val threshold = with(LocalDensity.current) { MaterialTheme.spacing.huge.roundToPx() }
    val atLatest by remember { derivedStateOf { listState.isNearLatest(threshold) } }
    var unseen by rememberSaveable { mutableIntStateOf(0) }
    var lastLatestKey by rememberSaveable { mutableStateOf<String?>(null) }

    val latest = items.firstOrNull { it is ChatTimelineItem.Message } as? ChatTimelineItem.Message
    // Read during composition, before the new item is laid out: was the reader following the conversation?
    val wasAtLatest = atLatest
    LaunchedEffect(latest?.key) {
        val key = latest?.key ?: return@LaunchedEffect
        val previous = lastLatestKey
        lastLatestKey = key
        if (previous == null || previous == key) return@LaunchedEffect
        if (shouldFollowNewMessage(latest.message, wasAtLatest)) {
            unseen = 0
            listState.animateScrollToItem(0)
        } else {
            unseen += items.asSequence()
                .takeWhile { it.key != previous }
                .count { it is ChatTimelineItem.Message && !it.message.mine }
        }
    }
    LaunchedEffect(atLatest) { if (atLatest) unseen = 0 }

    LaunchedEffect(jump) {
        val index = items.indexOfFirst { it is ChatTimelineItem.Message && it.message.id == jump }
        if (index >= 0) { listState.animateScrollToItem(index); onJump(null) }
        else if (jump != null) { viewModel.mediaError("The original message is outside the loaded history."); onJump(null) }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(listState, lifecycleState, messages.lastOrNull()?.id) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { visible -> (items.getOrNull(visible.index) as? ChatTimelineItem.Message)?.message }
                .firstOrNull { !it.mine && it.delivery == com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery.SENT }?.id
        }.filterNotNull().distinctUntilChanged().debounce(500).collect(viewModel::markVisible)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = maxWidth - MaterialTheme.spacing.medium * 2
        val maxBubbleWidth = minOf(contentWidth * ChatTokens.bubbleMaxWidthFraction, ChatTokens.bubbleMaxWidth)
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Draw-phase reads avoid per-frame recomposition/layout and a full-list offscreen buffer.
                alpha = loadAlpha.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.medium,
                end = MaterialTheme.spacing.medium,
                top = MaterialTheme.spacing.small,
                bottom = MaterialTheme.spacing.compact,
            ),
        ) {
            items(items, key = ChatTimelineItem::key, contentType = { it::class }) { item ->
                when (item) {
                    is ChatTimelineItem.DayHeader -> ChatDayHeader(
                        when (chatDayLabel(item.date, today)) {
                            ChatDayLabel.TODAY -> stringResource(Res.string.chat_secure_today)
                            ChatDayLabel.YESTERDAY -> stringResource(Res.string.chat_secure_yesterday)
                            ChatDayLabel.DATE -> remember(item.date, today) { formatChatDate(item.date, today) }
                        },
                    )
                    is ChatTimelineItem.Message -> {
                        val display = displayMessages[item.message.id]
                        if (display != null) {
                            if (item.message.attachment?.kind in setOf("photo", "video")) LaunchedEffect(item.message.id, item.message.delivery) { viewModel.loadThumbnail(item.message.id) }
                            MessageBubble(display, item.message.mine, partner?.name ?: "Your partner", ui.accountId,
                                item.joinsPrevious, cardContent = {
                                    item.message.planReference?.let { reference ->
                                        TextButton(onClick = { onOpenPlans(reference.id) }) {
                                            Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.planner)
                                            Spacer(Modifier.width(MaterialTheme.spacing.small)); Text("Open Plan")
                                        }
                                    }
                                    item.message.challengeReference?.let { reference ->
                                        TextButton(onClick = { onOpenChallenges(reference.id) }) {
                                            Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.reminder)
                                            Spacer(Modifier.width(MaterialTheme.spacing.small)); Text("Open Challenge")
                                        }
                                    }
                                    item.message.sparkReference?.let { reference ->
                                        TextButton(onClick = { onOpenSparks(reference.id) }) {
                                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.spark)
                                            Spacer(Modifier.width(MaterialTheme.spacing.small)); Text("Open Spark")
                                        }
                                    }
                                    item.message.momentReference?.let { reference ->
                                        MomentChatReference(reference,viewModel,onOpenMoment)
                                    }
                                }, onMenu = { onMenu(display.id) }, onReply = { if (item.message.delivery == com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery.SENT) viewModel.setReply(display.id) },
                                onOriginal = { onJump(it) }, onRetry = { item.message.retryTransactionId?.let(viewModel::retryMessage) },
                                onMedia = { onMedia(display.id) }, uploadProgress = item.message.uploadProgress, offlineMedia = emptySet(),
                                groupContinues = item.joinsNext, mediaRequest = ui.mediaRequest, mediaLoading = ui.mediaLoading, mediaError = ui.mediaRequestError,
                                modifier = chatMessageEntrance(motionEnabled),
                                avatar = partner?.let { { PartnerAvatar(it, ChatMessageLayout.avatarSize, MaterialTheme.typography.bodySmall) } },
                                voiceContent = { voice, modifier -> InlineVoiceMessage(display.id, voice, media, false,
                                    ui.mediaLoading && ui.mediaRequest?.messageId == display.id, ui.mediaRequestError.takeIf { ui.mediaRequest?.messageId == display.id }, modifier) })
                        }
                    }
                }
            }
            if (ui.hasOlder) item(key = "older") { TextButton(onClick = viewModel::loadOlder, modifier = Modifier.fillMaxWidth()) { Text("Load earlier messages") } }
        }
        AnimatedVisibility(
            visible = !atLatest,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.medium),
        ) {
            JumpToLatestButton(unseen, onClick = { scope.launch { listState.animateScrollToItem(0) } })
        }
    }
}

private fun LazyListState.isNearLatest(thresholdPx: Int): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= thresholdPx

@Composable private fun MomentChatReference(reference: com.srisu.srisu.features.moments.data.MomentReference,
    vm: SecureChatViewModel, open: (com.srisu.srisu.features.moments.data.MomentReference)->Unit) {
    val status by remember(reference) { vm.momentReferences.observe(reference) }.collectAsState()
    DisposableEffect(reference) { vm.momentReferences.retain(reference); onDispose { vm.momentReferences.release(reference) } }
    val available=status==com.srisu.srisu.features.moments.presentation.MomentReferenceState.AVAILABLE
    val retry=status==com.srisu.srisu.features.moments.presentation.MomentReferenceState.RETRY
    TextButton(onClick={if(available) open(reference) else vm.momentReferences.refresh(reference)}, enabled=available || retry) {
        Icon(androidx.compose.material.icons.Icons.Outlined.Photo, null,Modifier.size(20.dp))
        Spacer(Modifier.width(MaterialTheme.spacing.small)); Text(status.label)
    }
}
