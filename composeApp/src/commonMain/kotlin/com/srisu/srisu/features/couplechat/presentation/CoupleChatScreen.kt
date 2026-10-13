@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CoupleChatScreen(roomId: String?, onBack: () -> Unit, onFindPartner: () -> Unit,
    onOpenPlan: (Long,Long)->Unit, onOpenMoment: (Long)->Unit,
    viewModel: ConversationViewModel = koinViewModel(),messageId: String? = null) {
    PrivateChatClipboard {CoupleChatContent(roomId,onBack,onFindPartner,onOpenPlan,onOpenMoment,viewModel,messageId)}
}

@Composable
private fun CoupleChatContent(roomId: String?, onBack: () -> Unit, onFindPartner: () -> Unit,
    onOpenPlan: (Long,Long)->Unit, onOpenMoment: (Long)->Unit, viewModel: ConversationViewModel,messageId: String?) {
    val repository = viewModel.conversation
    val media = rememberChatMediaController(repository)
    val state by viewModel.state.collectAsStateWithLifecycle()
    com.srisu.srisu.features.notifications.ReportNotificationScreen(
        state.roomId?.let { com.srisu.srisu.navigation.graph.ChatNav.CoupleConversation(it) }
    )
    ProtectConversation()
    DisposableEffect(viewModel, roomId) { viewModel.enter(roomId); onDispose { viewModel.leave() } }
    val list = rememberLazyListState()
    val latest by rememberUpdatedState(state)
    var options by rememberSaveable { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(false) }
    var cardSheet by remember { mutableStateOf<String?>(null) }
    var planTitle by remember {mutableStateOf("")}
    var selected by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    var replace by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf<String?>(null) }
    var restored by remember(state.roomId) {mutableStateOf(false)}
    var routeApplied by remember(state.roomId,messageId) {mutableStateOf(false)}
    LaunchedEffect(state.roomId,state.loading) {
        if(state.roomId!=null && !state.loading && !restored) {
            val anchor=state.readingAnchor
            val index=state.messages.indexOfFirst {it.id==anchor?.messageId}
            if(index>=0)list.scrollToItem(index,anchor!!.offset)
            restored=true
            if(messageId!=null) {jump=messageId;repository.jumpToMessage(messageId,reportUnavailable=false)}
        }
    }
    LaunchedEffect(state.roomId,messageId,state.loading,state.synchronizing,state.security) {
        if(messageId!=null && !routeApplied && !state.loading && state.roomId!=null &&
            (state.messages.any {it.id==messageId} || state.security==ConversationSecurity.READY && !state.synchronizing)) {
            routeApplied=true;jump=messageId;repository.jumpToMessage(messageId)
        }
    }
    LaunchedEffect(state.openedMedia) {if(state.openedMedia==null)media.stopPlayer()}
    LaunchedEffect(state.messages.lastOrNull()?.id, state.atLatest, state.loading) {
        if (state.atLatest && state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex)
    }
    LaunchedEffect(jump, state.messages) {
        val index = state.messages.indexOfFirst { it.id == jump }
        if (index >= 0) { if(jump==messageId)routeApplied=true;list.scrollToItem(index); jump = null }
    }
    LaunchedEffect(list, repository) {
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect {
            if (restored && list.layoutInfo.totalItemsCount > 0) {
                val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val first=list.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
                if(first!=null)repository.readingPosition(first,list.firstVisibleItemScrollOffset,last < list.layoutInfo.totalItemsCount - 1)
            }
        }
    }
    LaunchedEffect(list, repository) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .debounce(350).distinctUntilChanged().collect { ids ->
                repository.markVisible(ids)
            }
    }
    val canCompose = state.roomId != null && state.security in setOf(ConversationSecurity.READY,
        ConversationSecurity.PARTNER_SETUP, ConversationSecurity.SETTING_UP)
    Scaffold(topBar = {
        TopAppBar(title = { Column {
            Text(state.preferences.nickname.ifEmpty { state.partnerName }, maxLines = 1)
            Text(when {
                state.security==ConversationSecurity.READY && state.preferences.presence && state.partnerActivity.roomId==state.roomId && state.partnerActivity.typing -> "Typing…"
                state.security==ConversationSecurity.READY && state.preferences.presence && state.partnerActivity.roomId==state.roomId && state.partnerActivity.online -> "Active now"
                else -> securityLabel(state.security)
            }, style = MaterialTheme.typography.labelSmall)
        } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, "Conversation options") } })
    }, bottomBar = {
        if (canCompose) Surface(tonalElevation = MaterialTheme.spacing.tiny) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(MaterialTheme.spacing.small)) {
                if(media.recording) RecordingPanel(media)
                if(media.preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
                MediaDraftPreview(state,repository)
                state.reply?.let { quote -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to ${if (quote.authorId == state.accountId) "yourself" else state.partnerName}: ${quote.preview}",
                        Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { repository.setReply(null) }) { Icon(Icons.Default.Close, "Cancel reply") }
                } }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    IconButton(onClick={attachments=true},enabled=!media.recording && !media.preparing) {Icon(Icons.Default.Add,"Attach or share")}
                    OutlinedTextField(state.draft, repository::updateDraft, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 6)
                    if(state.draft.isBlank() && state.mediaDraft==null) VoiceRecordControl(media,!media.recording && !media.preparing)
                    else IconButton(onClick = repository::sendText, enabled = !media.recording && !media.preparing) { Icon(Icons.AutoMirrored.Filled.Send, "Send message") }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { error -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small)) {
                    Text(error, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = repository::retry) { Text("Try again") }
                        when (state.security) {
                            ConversationSecurity.REPLACE_DEVICE -> TextButton(onClick = { replace = true }) { Text("Use this device") }
                            ConversationSecurity.IDENTITY_CHANGED -> TextButton(onClick = repository::showSafetyCode) { Text("Verify identity") }
                            ConversationSecurity.KEY_LOST, ConversationSecurity.STORAGE_ERROR -> TextButton(onClick = { reset = true }) { Text("Recovery options") }
                            else -> Unit
                        }
                    }
                }
            } }
            if (!state.loading && state.roomId == null) {
                Column(Modifier.padding(MaterialTheme.spacing.gutter)) {
                    Text("Connect with your partner to start your private conversation.")
                    TextButton(onClick = onFindPartner) { Text("Find your partner") }
                }
            } else if (state.messages.isEmpty() && !state.loading) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(MaterialTheme.spacing.gutter),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A little space for the two of you", style = MaterialTheme.typography.headlineSmall)
                    Text("Share a thought, ask a question, or just say hello.", Modifier.padding(vertical = MaterialTheme.spacing.medium))
                    listOf("What made you smile today?", "I’m thinking of you", "How was your day?").forEach { suggestion ->
                        SuggestionChip(onClick = { repository.updateDraft(suggestion) }, label = { Text(suggestion) }, enabled = canCompose)
                    }
                }
            } else {
                if (state.hasOlder) TextButton(onClick = repository::older, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Earlier messages") }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                    contentPadding = PaddingValues(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                    itemsIndexed(state.messages, key = { _, message -> message.id }, contentType = { _, _ -> "message" }) { index, message ->
                        val previous = state.messages.getOrNull(index - 1)
                        Column(Modifier.fillMaxWidth()) {
                            if (previous?.createdAt?.let(::chatLocalDate) != chatLocalDate(message.createdAt))
                                Text(chatLocalDate(message.createdAt), Modifier.align(Alignment.CenterHorizontally).padding(MaterialTheme.spacing.medium), style = MaterialTheme.typography.labelSmall)
                            MessageBubble(message, message.authorId == state.accountId, state.partnerName,state.accountId,
                                grouped = previous?.authorId == message.authorId && previous.createdAt.take(16) == message.createdAt.take(16),
                                onMenu = { selected = message.id }, onReply = { repository.setReply(message.id) },
                                onOriginal = { id -> jump = id; repository.jumpToMessage(id) }, onRetry = { repository.retryMessage(message.id) },
                                onMedia={id->repository.openAttachment(message.id,id)},uploadProgress=state.uploadProgress[message.id],offlineMedia=state.offlineMedia,
                                cardContent={ChatCardBubble(message,state,repository,onOpenPlan,{title->planTitle=title;cardSheet="planner"},onOpenMoment)})
                        }
                    }
                }
            }
            if (state.newMessages > 0 || !state.atLatest) TextButton(onClick = repository::latest, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (state.newMessages > 0) "${state.newMessages} new messages" else "Latest messages")
            }
        }
    }
    state.messages.firstOrNull { it.id == selected }?.let { message ->
        MessageActions(message, state, repository, onDelete = { delete = message.id }, onDismiss = { selected = null })
    }
    if (options) ConversationOptions(state, repository, onDismiss = { options = false }, onReset = { options = false; reset = true })
    if(attachments) ChatAttachmentSheet(media,onDismiss={attachments=false},onCard={planTitle="";cardSheet=it})
    cardSheet?.let {ChatCardSheet(it,planTitle,state,repository) {cardSheet=null;planTitle=""}}
    state.openedMedia?.let {ChatMediaViewer(it,media)}
    if(state.mediaLoading) AlertDialog(onDismissRequest=repository::dismissMedia,title={Text("Opening attachment")},
        text={LinearProgressIndicator(Modifier.fillMaxWidth())},confirmButton={TextButton(onClick=repository::dismissMedia) {Text("Cancel")}})
    ConversationConfirmations(state, repository, media, delete, replace, reset,
        onDeleteDismiss = { delete = null }, onReplaceDismiss = { replace = false }, onResetDismiss = { reset = false })
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
