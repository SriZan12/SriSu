package com.srisu.srisu.features.chat.chatroom

// ChatScreen.kt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_EVERYONE
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_ME
import com.srisu.srisu.utils.DateTimeUtils.formatTimeInHourAndMinute
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    session: Session?,
    navController: NavController,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val chatState by viewModel.chatState.collectAsState()
    val error by viewModel.error.collectAsState()

    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.updateSession(session = session)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.chatMessages?.size) {
        chatState.chatMessages?.let {
            if (it.isNotEmpty()) {
                listState.animateScrollToItem(0)
                viewModel.sendMessageDeliveredRequest()
                viewModel.sendMessageReadRequest()
            }
        }
    }
    var chatTopBarSubTitle by remember { mutableStateOf("Online") }

    LaunchedEffect(key1 = chatState.isTyping) {
        AppLogger.log("Inside launched Effect for typing")
        chatTopBarSubTitle = if (chatState.isTyping) {
            "Typing..."
        } else {
            "Online"
        }
    }


    Scaffold(
        topBar = {
            ChatTopBar(
                title = "Srijan Khadka", // fetch from chatRoom
                subtitle = chatTopBarSubTitle,
                avatarUrl = "https://randomuser.me/api/portraits/men/76.jpg",
                onBack = { navController.popBackStack() },
                onCall = { /* TODO */ },
                onVideoCall = { /* TODO */ }
            )
        },
        bottomBar = {
            ChatInputBar(
                value = chatState.messageInput,
                onValueChange = { viewModel.onMessageInputChanged(it) },
                onSend = {
                    if (chatState.messageInput.text.isNotBlank()) {
                        if (chatState.isEditMessage) {
                            viewModel.editMessage(chatState.selectedMessageForAction?.id)
                        } else {
                            viewModel.sendMessage()
                        }
                    }
                },
                onCancelEdit = {
                    viewModel.updateIsEditMessage(isEditMessage = false)
                    inputFocusRequester.freeFocus()
                    viewModel.onMessageInputChanged(value = TextFieldValue())
                },

                isEditing = chatState.isEditMessage,
                focusRequester = inputFocusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding)) {
            Column(
                modifier = Modifier
            ) {
                error?.let { message ->
                    ErrorBanner(
                        message = message,
                        onDismiss = viewModel::clearError,
                        onRetry = viewModel::retryConnection
                    )
                }

                ChatMessagesList(
                    messages = chatState.chatMessages ?: emptyList(),
                    currentUserId = chatState.session?.id,
                    listState = listState,
                    selectedMessageId = chatState.selectedMessageIdForActions,
                    onMessageLongClick = { id, msg ->
                        viewModel.showActionsForMessage(
                            messageId = id,
                            message = msg
                        )
                    },
                    onDismissActions = viewModel::dismissActions,
                    onEdit = { msg ->
                        viewModel.setMessageInputText(msg.text.orEmpty())
                        viewModel.updateIsEditMessage(true)
                        inputFocusRequester.requestFocus()
                    },
                    onDelete = { deleteOption, messageId ->
                        viewModel.deleteMessage(deleteOption = deleteOption, messageId = messageId)
                    },
                    onFetchOlder = viewModel::fetchOlderMessages
                )
            }

//            FloatingHearts()
        }
    }


}

@OptIn(ExperimentalTime::class)
@Composable
private fun ChatMessagesList(
    messages: List<ChatMessage?>,
    currentUserId: Int?,
    listState: LazyListState,
    selectedMessageId: Long?,
    onMessageLongClick: (Long?, ChatMessage) -> Unit,
    onDismissActions: () -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onDelete: (String, Long?) -> Unit,
    onFetchOlder: () -> Unit,
    modifier: Modifier = Modifier
) {

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItems = layoutInfo.totalItemsCount
                val visibleItems = layoutInfo.visibleItemsInfo

                if (visibleItems.isNotEmpty()) {
                    val lastVisibleIndex = visibleItems.last().index

                    // OLD messages reached (top of chat)
                    if (lastVisibleIndex >= totalItems - 1) {
                        AppLogger.log("Reached top, fetching older messages")
                        onFetchOlder()
                    }
                }
            }
    }


    AppLogger.log("Messages in CHAT UI = ${messages.size}")

    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = messages,
            key = { it?.id ?: Clock.System.now().epochSeconds }
        ) { message ->
            if (message != null) {
                AnimatedMessageItem(
                    message = message,
                    currentUserId = currentUserId,
                    isOwn = message.senderId == currentUserId,
                    isActionShown = selectedMessageId == message.id,
                    onLongClick = { onMessageLongClick(message.id, message) },
                    onDismissActions = onDismissActions,
                    onEdit = { onEdit(message) },
                    onDeleteForMe = { onDelete(DELETE_FOR_ME, message.id) },
                    onDeleteForEveryone = {
                        onDelete(
                            DELETE_FOR_EVERYONE,
                            message.id
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AnimatedMessageItem(
    message: ChatMessage,
    currentUserId: Int?,
    isOwn: Boolean,
    isActionShown: Boolean,
    onLongClick: () -> Unit,
    onDismissActions: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    val hasAnimated = remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        delay(50) // Stagger animations slightly
        hasAnimated.value = true
    }

    AnimatedVisibility(
        visible = hasAnimated.value,
        enter = fadeIn(tween(300)) +
                slideInVertically(tween(300)) { it / 8 } +
                scaleIn(tween(300), initialScale = 0.94f),
        exit = fadeOut(tween(150))
    ) {
        Box {
            MessageBubble(
                message = message,
                currentUserId = currentUserId,
                isOwn = isOwn,
                onLongClick = onLongClick
            )

            AnimatedVisibility(
                visible = isActionShown,
                enter = fadeIn(tween(150)) + scaleIn(tween(150), 0.9f),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), 0.9f),
                modifier = Modifier.align(if (isOwn) Alignment.TopEnd else Alignment.TopStart)
            ) {
                MessageActionsDropdown(
                    isOwn = isOwn,
                    onDismiss = onDismissActions,
                    onEdit = { onEdit() },
                    onDeleteForMe = { onDeleteForMe() },
                    onDeleteForEveryone = { onDeleteForEveryone() },
                    canEdit = isOwn
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    currentUserId: Int?,
    isOwn: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val deleteEntry = message.deleteFor
        ?.values
        ?.flatten()
        ?.firstOrNull { it.option == DELETE_FOR_EVERYONE }

    val isDeletedForEveryone = deleteEntry != null

    val displayText = if (isDeletedForEveryone) {
        if (deleteEntry.user_id == currentUserId) {
            "You deleted this message"
        } else {
            "This message was deleted"
        }
    } else {
        message.text.orEmpty()
    }

    val textColor = if (isDeletedForEveryone) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    } else if (isOwn) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // Hide long-click action if message is deleted
    val clickableModifier = if (isDeletedForEveryone) {
        Modifier
    } else {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
            onClick = { }
        )
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isOwn) 20.dp else 4.dp,
        bottomEnd = if (isOwn) 4.dp else 20.dp
    )

    val backgroundColor = if (isOwn)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh

    // Slightly transparent background for deleted messages
    val finalBackground = if (isDeletedForEveryone) {
        backgroundColor.copy(alpha = 0.7f)
    } else {
        backgroundColor
    }

    val alignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
        contentAlignment = alignment
    ) {
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = finalBackground),
            modifier = Modifier.widthIn(max = 240.dp)
        ) {
            Column(
                modifier = Modifier.padding(all = 12.dp)
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontStyle = if (isDeletedForEveryone) FontStyle.Italic else FontStyle.Normal
                )

                if (!isDeletedForEveryone) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        val formattedTime = formatTimeInHourAndMinute(isoTime = message.timestamp)

                        if (formattedTime.isNotEmpty()) {
                            Text(
                                text = formattedTime, // Replace with formatted message.time
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isOwn) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                        alpha = 0.7f
                                    )
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        if (isOwn) {


//                            if (message.isRead == true) {
//                                Icon(
//                                    imageVector = Icons.Default.DoneAll,
//                                    contentDescription = "Read",
//                                    tint = MaterialTheme.colorScheme.primary,
//                                    modifier = Modifier.size(16.dp)
//                                )
//                            } else
                            if (message.isDelivered == true) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    modifier = Modifier.size(16.dp)

                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Sent",
                                    tint = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String,
    avatarUrl: String,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onCall) {
                Icon(Icons.Default.Call, contentDescription = "Call")
            }
            IconButton(onClick = onVideoCall) {
                Icon(Icons.Default.Videocam, contentDescription = "Video call")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}

@Composable
private fun ChatInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onCancelEdit: () -> Unit,
    isEditing: Boolean = false,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {

        if (isEditing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit message",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = {
                        onCancelEdit()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Type a message") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = {
                    IconButton(onClick = { /* Attach media */ }) {
                        Icon(Icons.Default.Photo, contentDescription = "Attach")
                    }
                }
            )

            AnimatedContent(
                targetState = value.text.isNotBlank(),
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200))).togetherWith(
                        fadeOut(tween(150)) + scaleOut(tween(150))
                    )
                },
                label = "InputActionButton"
            ) { hasText ->
                if (hasText) {
                    FilledIconButton(
                        onClick = onSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                } else {
                    IconButton(onClick = { /* Voice message */ }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice")
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Row {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
fun MessageActionsDropdown(
    isOwn: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    canEdit: Boolean
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
    ) {
        DropdownMenuItem(
            text = { Text("Reply") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
            onClick = { onDismiss() }
        )
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
            onClick = { onDismiss() }
        )
        if (canEdit) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = {
                    onEdit()
                    onDismiss()
                }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete for me", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDeleteForMe()
                onDismiss()
            }
        )

        if (isOwn) {
            DropdownMenuItem(
                text = { Text("Delete for everyone", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    onDeleteForEveryone()
                    onDismiss()
                }
            )
        }
    }
}

data class FloatingHeart(
    val startX: Float,
    val size: Float,
    val delay: Int
)

@Composable
fun FloatingHearts() {
    val hearts = remember {
        List(6) {
            FloatingHeart(
                startX = (30..300).random().toFloat(),
                size = (8..20).random().toFloat(),
                delay = (0..800).random()
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        hearts.forEach { heart ->
            var offsetY by remember { mutableStateOf(80f) }

            LaunchedEffect(Unit) {
                delay(heart.delay.toLong())
                animate(
                    initialValue = 80f,
                    targetValue = -40f,
                    animationSpec = infiniteRepeatable(
                        tween(2000, easing = LinearEasing),
                        RepeatMode.Restart
                    )
                ) { value, _ -> offsetY = value }
            }

            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(heart.size.dp)
                    .graphicsLayer {
                        translationX = heart.startX
                        translationY = offsetY
                        alpha = 0.9f
                    }
            )
        }
    }
}

@Composable
@Preview
fun ChatScreenPreview() {
    ChatInputBar(
        value = TextFieldValue(),
        onValueChange = { },
        onSend = {

        },
        focusRequester = FocusRequester(),
        onCancelEdit = {

        },

        isEditing = false,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Preview
@Composable
fun PreviewMessageBubble() {
    MessageBubble(
        message = ChatMessage(
            text = "Hello"
        ),
        currentUserId = 97,
        isOwn = true,
        onLongClick = {}
    )
}

