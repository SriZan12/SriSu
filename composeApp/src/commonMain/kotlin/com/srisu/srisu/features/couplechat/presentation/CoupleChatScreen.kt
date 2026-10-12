@file:OptIn(
    ExperimentalMaterial3Api::class,
    kotlinx.coroutines.FlowPreview::class
)

package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.core.data.remote.SocketState
import com.srisu.srisu.theme.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CoupleChatScreen(
    roomId: String?, onBack: () -> Unit, onFindPartner: () -> Unit,
    onOpenPlan: (Long, Long) -> Unit, onOpenMoment: (Long) -> Unit,
    viewModel: ConversationViewModel = koinViewModel(), messageId: String? = null,
    onOpenMedia: (String, String, String, Boolean) -> Unit
) {
    PrivateChatClipboard {
        CoupleChatContent(
            roomId,
            onBack,
            onFindPartner,
            onOpenPlan,
            onOpenMoment,
            viewModel,
            messageId,
            onOpenMedia
        )
    }
}

@Composable
private fun CoupleChatContent(
    roomId: String?,
    onBack: () -> Unit,
    onFindPartner: () -> Unit,
    onOpenPlan: (Long, Long) -> Unit,
    onOpenMoment: (Long) -> Unit,
    viewModel: ConversationViewModel,
    messageId: String?,
    onOpenMedia: (String, String, String, Boolean) -> Unit
) {
    val repository = viewModel.conversation
    val media = rememberChatMediaController(repository)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val partnerPhoto by viewModel.partnerPhoto.collectAsStateWithLifecycle()
    val partnerAvatar = privatePhotoBitmap(partnerPhoto)
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    ProtectConversation()
    DisposableEffect(viewModel, roomId) { viewModel.enter(roomId); onDispose { viewModel.leave() } }
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun dismissKeyboard() {
        focus.clearFocus(); keyboard?.hide()
    }

    val latest by rememberUpdatedState(state)
    var appearance by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(false) }
    var cardSheet by remember { mutableStateOf<String?>(null) }
    var planTitle by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var exportVoice by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    var deleteForMe by remember { mutableStateOf<String?>(null) }
    var selectText by remember { mutableStateOf<String?>(null) }
    var replace by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    var jump by remember { mutableStateOf<String?>(null) }
    var restored by rememberSaveable(state.roomId) { mutableStateOf(false) }
    var routeApplied by remember(state.roomId, messageId) { mutableStateOf(false) }
    LaunchedEffect(state.roomId, state.loading) {
        if (state.roomId != null && !state.loading && !restored) {
            val anchor = state.readingAnchor
            val index = state.messages.indexOfFirst { it.id == anchor?.messageId }
            if (index >= 0) list.scrollToItem(index, anchor!!.offset)
            else if (messageId == null && state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex)
            restored = true
        }
    }
    LaunchedEffect(state.roomId, messageId, state.loading, state.synchronizing, state.security) {
        if (messageId != null && !routeApplied && !state.loading && state.roomId != null &&
            (state.messages.any { it.id == messageId } || state.security == ConversationSecurity.READY && !state.synchronizing)
        ) {
            routeApplied = true; jump = messageId; repository.jumpToMessage(messageId)
        }
    }
    // A reaction or receipt must never restart a scroll. Only new tail items,
    // explicit "latest", or a viewport resize while following the tail do so.
    LaunchedEffect(state.messages.lastOrNull()?.id, state.atLatest, restored) {
        if (restored && state.atLatest && !list.isScrollInProgress && jump == null && state.messages.isNotEmpty())
            list.scrollToItem(state.messages.lastIndex)
    }
    LaunchedEffect(list, restored) {
        if (restored) snapshotFlow { list.layoutInfo.viewportEndOffset }.distinctUntilChanged()
            .collect {
                if (latest.atLatest && !list.isScrollInProgress && jump == null && latest.messages.isNotEmpty())
                    list.scrollToItem(latest.messages.lastIndex)
            }
    }
    LaunchedEffect(jump, state.messages) {
        val index = state.messages.indexOfFirst { it.id == jump }
        if (index >= 0) {
            list.scrollToItem(index); highlighted = jump; jump = null
        }
    }
    LaunchedEffect(highlighted) {
        if (highlighted != null) {
            kotlinx.coroutines.delay(1800.milliseconds); highlighted = null
        }
    }
    LaunchedEffect(list, repository) {
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect {
            if (restored && list.layoutInfo.totalItemsCount > 0) {
                val first = list.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
                if (first != null) repository.readingPosition(
                    first,
                    list.firstVisibleItemScrollOffset,
                    list.canScrollForward
                )
            }
        }
    }
    LaunchedEffect(list, repository) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .debounce(350.milliseconds).distinctUntilChanged().collect(repository::markVisible)
    }
    var openingMedia by remember { mutableStateOf(false) }
    val chatLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(chatLifecycle, media, viewModel, roomId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // A quick Back may retain this composition through the navigation transition.
                openingMedia = false; media.resumeConversation(); viewModel.enter(roomId)
            }
        }
        chatLifecycle.addObserver(observer)
        onDispose { chatLifecycle.removeObserver(observer) }
    }
    fun openMedia(messageId: String, attachmentId: String, draft: Boolean) {
        val room = state.roomId ?: return
        if (openingMedia) return
        openingMedia = true
        list.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.let { key ->
            (key as? String)?.let {
                repository.readingPosition(
                    it,
                    list.firstVisibleItemScrollOffset,
                    list.canScrollForward
                )
            }
        }
        dismissKeyboard(); media.handoffToViewer(); viewModel.coverWithMedia()
        onOpenMedia(room, messageId, attachmentId, draft)
    }
    SriSuChatTheme(ChatThemePreset.fromId(state.preferences.chatTheme)) {
        Box(Modifier.fillMaxSize()) {
        ConversationSurface(
            state,
            list,
            onBack,
            { dismissKeyboard(); options = true },
            onFindPartner,
            repository::updateDraft,
            repository::older,
            { jump = null; repository.latest() },
            repository::retry,
            onSecurityAction = {
                when (state.security) {
                    ConversationSecurity.REPLACE_DEVICE -> replace = true
                    ConversationSecurity.IDENTITY_CHANGED -> repository.showSafetyCode()
                    ConversationSecurity.KEY_LOST, ConversationSecurity.STORAGE_ERROR -> reset =
                        true

                    else -> repository.retry()
                }
            },
            composer = {
                ConversationComposer(
                    state,
                    busy = media.preparing,
                    media.recording,
                    onDraft = repository::updateDraft,
                    onSend = { jump = null; repository.sendText(); repository.latest() },
                    onAttach = { dismissKeyboard(); attachments = true },
                    onCancelReply = { repository.setReply(null) },
                    voice = { VoiceRecordControl(media, !media.recording && !media.preparing) }) {
                    if (media.recording) RecordingPanel(media)
                    if (media.preparing) {
                        Text("Preparing attachment…", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    MediaDraftPreview(state, repository, media) { message, attachment ->
                        openMedia(
                            messageId = message,
                            attachmentId = attachment,
                            draft = true
                        )
                    }
                }
            },
            connection = connection,
            partnerPhoto = partnerAvatar,
            messageContent = { index, message ->
                MessageBubble(
                    message,
                    own = message.authorId == state.accountId,
                    partner = state.preferences.nickname.ifBlank { state.partnerName },
                    state.accountId,
                    grouped = messagesGroup(state.messages.getOrNull(index - 1), message),
                    onMenu = { dismissKeyboard(); selected = message.id },
                    onReply = { repository.setReply(message.id) },
                    onOriginal = { id -> jump = id; repository.jumpToMessage(id) },
                    onRetry = { repository.retryMessage(message.id) },
                    onMedia = { id -> openMedia(message.id, id, false) },
                    uploadProgress = state.uploadProgress[message.id],
                    offlineMedia = state.offlineMedia,
                    groupContinues = messagesGroup(message, state.messages.getOrNull(index + 1)),
                    partnerPhoto = partnerAvatar,
                    afterReactions = state.messages.getOrNull(index - 1)?.hasVisibleReactions == true,
                    highlighted = highlighted == message.id,
                    mediaRequest = state.mediaRequest,
                    mediaLoading = state.mediaLoading,
                    mediaError = state.mediaRequestError,
                    voiceContent = { item, modifier ->
                        InlineVoiceMessage(
                            message.id,
                            item,
                            media,
                            item.descriptor.id in state.offlineMedia,
                            state.mediaLoading && state.mediaRequest?.attachmentId == item.descriptor.id,
                            state.mediaRequestError.takeIf { state.mediaRequest?.attachmentId == item.descriptor.id },
                            modifier
                        )
                    },
                    cardContent = {
                        ChatCardBubble(
                            message,
                            state,
                            repository,
                            onOpenPlan,
                            { title -> planTitle = title; cardSheet = "planner" },
                            onOpenMoment
                        )
                    })
            })
        RomanticMessageOverlay(viewModel.romanticEffects,state.accountId,state.roomId,
            enabled=state.security==ConversationSecurity.READY && !openingMedia && !options && !appearance &&
                !attachments && selected==null && cardSheet==null && !media.recording,
            modifier=Modifier.matchParentSize().imePadding().safeDrawingPadding())
        }
        state.messages.firstOrNull { it.id == selected }?.let { message ->
            MessageActions(
                message,
                state,
                repository,
                onDelete = { delete = message.id },
                onDeleteForMe = { deleteForMe = message.id },
                onSelectText = { selectText = message.id },
                onDismiss = { selected = null },
                onExport = if (state.openedMedia?.messageId == message.id && state.openedMedia?.media?.kind == "voice")
                    ({ exportVoice = message.id }) else null
            )
        }
        state.openedMedia?.takeIf { it.messageId == exportVoice && it.media.kind == "voice" }
            ?.let { opened ->
                AlertDialog(
                    onDismissRequest = { exportVoice = null },
                    title = { Text("Export this attachment?") },
                    text = { Text("The exported copy will leave SriSu’s protected chat storage. Deleting the message cannot erase that copy.") },
                    confirmButton = {
                        TextButton(onClick = {
                            exportVoice = null; media.export(opened)
                        }) { Text("Choose destination") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            exportVoice = null
                        }) { Text("Cancel") }
                    })
            }
        // Keep an ID, not another plaintext snapshot. Deletion, room change and
        // background clearing immediately remove the selectable body as well.
        state.messages.firstOrNull { it.id == selectText && !it.deleted && !it.hidden && !it.unavailable }
            ?.let { message ->
                val text = when (val content = message.content) {
                    is ChatContent.Text -> content.body
                    is ChatContent.Media -> content.caption
                    else -> null
                }
                if (text != null) ModalBottomSheet(onDismissRequest = { selectText = null }) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .navigationBarsPadding().padding(MaterialTheme.spacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        Text("Select text", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        TextButton(onClick = { selectText = null }) { Text("Done") }
                    }
                }
            }
        if (deleteForMe != null) AlertDialog(
            onDismissRequest = { deleteForMe = null },
            title = { Text("Delete for you?") },
            text = { Text("This message will be removed from your view on this device. Your partner’s copy will stay.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteForMe?.let(repository::deleteForMe); deleteForMe = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteForMe = null
                }) { Text("Keep message") }
            })
        if (options) ConversationOptions(
            state,
            repository,
            onDismiss = { options = false },
            onReset = { options = false; reset = true },
            onAppearance = { options = false; appearance = true })
        if (attachments) ChatAttachmentSheet(
            media,
            onDismiss = { attachments = false },
            onCard = { planTitle = ""; cardSheet = it })
        cardSheet?.let {
            ChatCardSheet(it, planTitle, state, repository) {
                cardSheet = null; planTitle = ""
            }
        }
        ConversationConfirmations(
            state,
            repository,
            media,
            delete,
            replace,
            reset,
            onDeleteDismiss = { delete = null },
            onReplaceDismiss = { replace = false },
            onResetDismiss = { reset = false })
    }
    if (appearance) key(state.accountId, state.roomId) {
        ChatAppearanceSheet(
            state.preferences.chatTheme,
            onApply = { repository.chatTheme(it); appearance = false },
            onDismiss = { appearance = false })
    }
}

/** State-only layout: shared by the real conversation and synthetic rendering checks. */
@Composable
internal fun ConversationSurface(
    state: ConversationState,
    list: LazyListState,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    onFindPartner: () -> Unit,
    onSuggestion: (String) -> Unit,
    onOlder: () -> Unit,
    onLatest: () -> Unit,
    onRetry: () -> Unit,
    onSecurityAction: () -> Unit,
    composer: @Composable () -> Unit,
    messageContent: @Composable (Int, LocalChatMessage) -> Unit,
    connection: SocketState? = null,
    partnerPhoto: androidx.compose.ui.graphics.ImageBitmap? = null
) {
    Scaffold(
        modifier = Modifier.imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ConversationHeader(state, onBack, onOptions, connection, partnerPhoto) },
        bottomBar = { if (state.canCompose) composer() else Spacer(Modifier.navigationBarsPadding()) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            ConversationNotice(state, onRetry, onSecurityAction)
            if (state.loading || state.synchronizing && state.messages.isEmpty()) LinearProgressIndicator(
                Modifier.fillMaxWidth()
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                when {
                    state.loading && state.messages.isEmpty() -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(
                            "Opening your conversation…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    state.messages.isEmpty() && state.security == ConversationSecurity.REPLACE_DEVICE ->
                        ConversationDeviceRequired(
                            onSecurityAction,
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        )

                    state.messages.isEmpty() -> ConversationEmpty(
                        state.roomId != null,
                        onFindPartner,
                        onSuggestion,
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        state.canCompose,
                        composing = state.draft.isNotBlank() || state.reply != null || state.mediaDraft != null
                    )

                    else -> LazyColumn(
                        Modifier.widthIn(max = 840.dp).fillMaxSize(), state = list,
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.small
                        )
                    ) {
                        itemsIndexed(
                            state.messages, key = { _, message -> message.id },
                            contentType = { _, message -> if (message.content is ChatContent.Media) "media" else if (message.content is ChatContent.Card) "card" else "text" }) { index, message ->
                            Column(Modifier.fillMaxWidth()) {
                                if (index == 0 && state.hasOlder) TextButton(
                                    onClick = onOlder,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) { Text("Earlier messages") }
                                if (state.messages.getOrNull(index - 1)?.createdAt?.let(::chatLocalDate) != chatLocalDate(
                                        message.createdAt
                                    )
                                )
                                    ChatDateDivider(message.createdAt)
                                messageContent(index, message)
                            }
                        }
                    }
                }
                if (state.newMessages > 0 || !state.atLatest) FilledTonalButton(
                    onClick = onLatest,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(MaterialTheme.spacing.small)
                ) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        null,
                        Modifier.size(MaterialTheme.spacing.icon)
                    )
                    Spacer(Modifier.width(MaterialTheme.spacing.tiny))
                    Text(if (state.newMessages > 0) "${state.newMessages} new messages" else "Latest messages")
                }
            }
        }
    }
}

internal fun securityLabel(security: ConversationSecurity) = when (security) {
    ConversationSecurity.READY -> "End-to-end encrypted"
    ConversationSecurity.PARTNER_SETUP -> "Waiting for your partner’s secure setup"
    ConversationSecurity.SETTING_UP -> "Setting up secure chat"
    ConversationSecurity.REPLACE_DEVICE -> "Chat is active on another device"
    ConversationSecurity.IDENTITY_CHANGED -> "Verify your partner’s new identity"
    ConversationSecurity.KEY_LOST -> "Local storage key unavailable"
    ConversationSecurity.STORAGE_ERROR -> "Protected storage unavailable"
    ConversationSecurity.ARCHIVED -> "Saved conversation"
}
