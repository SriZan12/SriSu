package com.srisu.srisu.features.chat.matrix.presentation

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

@Composable
fun SecureChatScreen(viewModel: SecureChatViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val input by viewModel.messageInput.collectAsState()
    val validationError by viewModel.composerValidationError.collectAsState()
    val partner by viewModel.partner.collectAsState()
    val availability = state?.availability ?: MatrixChatAvailability.Loading
    Scaffold(
        topBar = { ChatHeader(partner, availability, onBack) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            when (availability) {
                is MatrixChatAvailability.Error -> ChatStatusState(
                    title = stringResource(Res.string.chat_secure_error_title),
                    message = availability.message.ifBlank { stringResource(Res.string.chat_secure_error) },
                    icon = painterResource(Res.drawable.chat_offline),
                    action = if (availability.retryable) viewModel::retry else null,
                    modifier = Modifier.fillMaxSize(),
                )
                is MatrixChatAvailability.Unavailable -> ChatStatusState(
                    title = stringResource(Res.string.chat_secure_unavailable_title),
                    message = availability.message.ifBlank { stringResource(Res.string.chat_secure_not_available) },
                    vector = Icons.Filled.Lock,
                    modifier = Modifier.fillMaxSize(),
                )
                MatrixChatAvailability.Loading, is MatrixChatAvailability.Offline, is MatrixChatAvailability.Ready -> {
                    val loading = availability == MatrixChatAvailability.Loading
                    val ready = availability is MatrixChatAvailability.Ready
                    (availability as? MatrixChatAvailability.Offline)?.let {
                        ChatOfflineBanner(it.message, viewModel::retry)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            loading -> ChatLoadingState(Modifier.fillMaxSize())
                            state?.messages.isNullOrEmpty() -> ChatEmptyState(
                                partner = partner,
                                starterEnabled = true,
                                onStarter = viewModel::updateMessageInput,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> ChatTimelineList(state?.messages.orEmpty(), viewModel::retryMessage)
                        }
                    }
                    // The draft stays editable while offline or loading; only sending is gated.
                    ChatComposer(
                        value = input,
                        onValueChange = viewModel::updateMessageInput,
                        onSend = viewModel::sendMessage,
                        partnerName = partner?.name?.substringBefore(' '),
                        editable = true,
                        canSend = ready && input.isNotBlank(),
                        sending = ready && state?.sending == true,
                        helper = validationError ?: state?.composerError.takeIf { ready },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTimelineList(messages: List<MatrixChatMessage>, onRetry: (String) -> Unit) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    // Newest first, rendered bottom-up: the conversation anchors to the composer like other messengers.
    val items = remember(messages, timeZone) { buildChatTimeline(messages, timeZone).asReversed() }
    val today = remember(messages, timeZone) { Clock.System.now().toLocalDateTime(timeZone).date }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
        if (latest.message.mine || wasAtLatest) {
            unseen = 0
            listState.animateScrollToItem(0)
        } else {
            unseen += items.asSequence()
                .takeWhile { it.key != previous }
                .count { it is ChatTimelineItem.Message && !it.message.mine }
        }
    }
    LaunchedEffect(atLatest) { if (atLatest) unseen = 0 }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = maxWidth - MaterialTheme.spacing.medium * 2
        val maxBubbleWidth = minOf(contentWidth * ChatTokens.bubbleMaxWidthFraction, ChatTokens.bubbleMaxWidth)
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
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
                    is ChatTimelineItem.Message -> ChatMessageRow(item, maxBubbleWidth, onRetry)
                }
            }
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
