package com.srisu.srisu.features.chat.chatroom

// ChatScreen.kt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.Uri
import coil3.compose.AsyncImage
import coil3.toUri
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.profile.screen.ProfilePictureCompo
import com.srisu.srisu.features.profile.state.EditProfileUIState
import com.srisu.srisu.features.profile.vm.EditProfileViewModel
import com.srisu.srisu.permissionmanager.PermissionCallback
import com.srisu.srisu.permissionmanager.PermissionState
import com.srisu.srisu.permissionmanager.PermissionType
import com.srisu.srisu.permissionmanager.createPermissionsManager
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_EVERYONE
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_ME
import com.srisu.srisu.utils.DateTimeUtils.formatTimeInHourAndMinute
import com.srisu.srisu.utils.MediaType
import com.srisu.srisu.utils.rememberGalleryManager
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.collections.emptyList
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

typealias reaction = String
typealias messageId = Long?

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    session: Session?,
    navController: NavController,
    viewModel: ChatViewModel = koinViewModel(),
) {

    val chatState by viewModel.chatState.collectAsState()

    ChatInitialization(
        session = session,
        viewModel = viewModel
    )

    ChatScaffold(
        chatState = chatState,
        viewModel = viewModel,
        onNavBack = {}
    )
}

@Composable
private fun ChatInitialization(
    session: Session?,
    viewModel: ChatViewModel
) {
    LaunchedEffect(Unit) {
        viewModel.updateSession(session = session)
    }
}

@Composable
private fun ChatScaffold(
    chatState: ChatState,
    viewModel: ChatViewModel,
    onNavBack: () -> Unit
) {
    val error by viewModel.error.collectAsState()
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    var openGallery by remember { mutableStateOf(false) }


    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(key1 = chatState.chatMessages?.size) {
        chatState.chatMessages?.let {
            if (it.isNotEmpty()) {
                listState.animateScrollToItem(0)
                viewModel.sendMessageDeliveredRequest()
                viewModel.sendMessageReadRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                chatState = chatState,
                onBack = { onNavBack() },
                onCall = { },
                onVideoCall = { }
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
                    viewModel.setReplyMessage(chatMessage = null, isOn = false)
                    inputFocusRequester.freeFocus()
                    viewModel.onMessageInputChanged(value = TextFieldValue())
                },

                onClickedMedia = {
                    if (!openGallery) {
                        openGallery = true
                    }
                },

                isEditing = chatState.isEditMessage,
                focusRequester = inputFocusRequester,
                isReplying = chatState.replyMessage,
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
                    onFetchOlder = viewModel::fetchOlderMessages,
                    onReactionSelected = { reaction, messageId ->
                        viewModel.reactToMessage(
                            reaction = reaction,
                            messageId = messageId
                        )
                    },
                    onReplyMessage = { chatMessage ->
                        viewModel.setReplyMessage(chatMessage = chatMessage, isOn = true)
                        inputFocusRequester.requestFocus()
                    }
                )
            }

//            FloatingHearts()

            if (openGallery) {
                MediaCompo(
                    onResult = { uris ->

                        viewModel.updateSelectedPhotos(
                            photos = uris
                        )

                        viewModel.uploadMedias()

                        openGallery = false
                    },
                    onDismiss = {
                        viewModel.updateSelectedPhotos(null)
                        openGallery = false
                    }
                )
            }


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
    onReactionSelected: (reaction, messageId) -> Unit,
    onReplyMessage: (ChatMessage) -> Unit,
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
                    },
                    onReactionSelected = { reaction ->
                        onReactionSelected(reaction, message.id)
                    },
                    onReplyMessage = onReplyMessage
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
    onDeleteForEveryone: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onReplyMessage: (ChatMessage) -> Unit
) {
    val hasAnimated = remember(message.id) { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        delay(50) // Stagger animations slightly
        hasAnimated.value = true
    }

    AnimatedVisibility(
        visible = hasAnimated.value,
        enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                slideInVertically(animationSpec = tween(durationMillis = 300)) { it / 8 } +
                scaleIn(animationSpec = tween(durationMillis = 300), initialScale = 0.94f),
        exit = fadeOut(animationSpec = tween(durationMillis = 150))
    ) {
        Box {

            SwipeableMessageCompo(
                message = message,
                currentUserId = currentUserId,
                isOwn = isOwn,
                onLongClick = onLongClick,
                onReplyMessage = onReplyMessage,
                onReactionClick = { showReactions = true }
            )

            AnimatedMessageDropDownCompo(
                modifier = Modifier.align(alignment = if (isOwn) Alignment.TopEnd else Alignment.TopStart),
                isOwn = isOwn,
                isActionShown = isActionShown,
                onDismissActions = onDismissActions,
                onEdit = onEdit,
                onDeleteForMe = onDeleteForMe,
                onDeleteForEveryone = onDeleteForEveryone
            )

            AnimatedReactionPickerOverlay(
                modifier = Modifier.align(Alignment.TopEnd),
                isOwn = isOwn,
                showReactions = showReactions,
                onReactionSelected = onReactionSelected,
                onDismiss = { showReactions = false }
            )
        }
    }
}

@Composable
private fun SwipeableMessageCompo(
    message: ChatMessage,
    currentUserId: Int?,
    isOwn: Boolean,
    onLongClick: () -> Unit,
    onReplyMessage: (ChatMessage) -> Unit,
    onReactionClick: () -> Unit
) {

    val swipeToReplyBoxState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        confirmValueChange = { newValue ->
            when (newValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onReplyMessage(message)
                    false // snap back, do NOT dismiss
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    false // explicitly ignore
                }

                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )

    SwipeToDismissBox(
        state = swipeToReplyBoxState,
        modifier = Modifier,
        content = {
            MessageBubble(
                message = message,
                currentUserId = currentUserId,
                isOwn = isOwn,
                onLongClick = onLongClick,
                onReactionClick = { onReactionClick() }
            )
        },
        backgroundContent = {
        },

        )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    currentUserId: Int?,
    isOwn: Boolean,
    onLongClick: () -> Unit,
    onReactionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var bubbleWidthPx by remember { mutableStateOf(0) }

    val deleteEntry = message.deleteFor
        ?.values
        ?.flatten()
        ?.firstOrNull { it.option == DELETE_FOR_EVERYONE }

    val isDeletedForEveryone = deleteEntry != null


    // Slightly transparent background for deleted messages
    val finalBackground = if (isDeletedForEveryone) {
        backgroundColor(isOwn = isOwn).copy(alpha = 0.7f)
    } else {
        backgroundColor(isOwn = isOwn)
    }

    val alignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                clickableModifier(
                    isDeletedForEveryone = isDeletedForEveryone,
                    haptic = haptic,
                    onLongClick = onLongClick
                )
            ),
        contentAlignment = alignment
    ) {
        Card(
            shape = bubbleShape(isOwn = isOwn),
            colors = CardDefaults.cardColors(containerColor = finalBackground),
            modifier = Modifier
                .widthIn(max = 240.dp)
                .onSizeChanged {
                    bubbleWidthPx = it.width
                }
        ) {
            Column(
                modifier = Modifier.padding(all = 12.dp)
            ) {

                if (!isDeletedForEveryone) {
                    message.replyTo?.let {
                        ReplyPreview(
                            reply = message.replyTo,
                            isOwn = isOwn
                        )
                    }
                }

                Text(
                    text = messageDisplayText(
                        deleteEntry = deleteEntry,
                        isDeletedForEveryone = isDeletedForEveryone,
                        messageText = message.text,
                        currentUserId = currentUserId
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = messageTextColor(
                        isOwn = isOwn,
                        isDeletedForEveryone = isDeletedForEveryone
                    ),
                    fontStyle = if (isDeletedForEveryone) FontStyle.Italic else FontStyle.Normal
                )

                if (!isDeletedForEveryone) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        val formattedTime =
                            formatTimeInHourAndMinute(isoTime = message.timestamp)

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


                            if (message.isRead == true) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else
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

        val reaction = message.reactions
        val bubbleWidthDp = with(density) { bubbleWidthPx.toDp() }

        if (!isOwn && bubbleWidthPx > 0 && message.deleteFor == null && !isDeletedForEveryone) {

            ReactionBubble(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(
                        x = bubbleWidthDp + 6.dp
                    ),  // move right from start-aligned bubble,
                onClick = onReactionClick,
                reaction = reaction?.reaction
            )
        } else if (reaction != null && isOwn && bubbleWidthPx > 0 && !isDeletedForEveryone) {
            ReactionBubble(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(
                        x = -(bubbleWidthDp + 6.dp)
                    ),
                onClick = onReactionClick,
                reaction = reaction.reaction
            )
        }


    }

}

@Composable
private fun backgroundColor(isOwn: Boolean) = if (isOwn)
    MaterialTheme.colorScheme.primaryContainer
else
    MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun bubbleShape(isOwn: Boolean) = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = if (isOwn) 20.dp else 4.dp,
    bottomEnd = if (isOwn) 4.dp else 20.dp
)

@Composable
private fun clickableModifier(
    isDeletedForEveryone: Boolean,
    haptic: HapticFeedback,
    onLongClick: () -> Unit
) = if (isDeletedForEveryone) {
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

@Composable
private fun messageDisplayText(
    deleteEntry: ChatMessage.DeleteMessageAction??,
    isDeletedForEveryone: Boolean,
    messageText: String?,
    currentUserId: Int?
) = if (isDeletedForEveryone) {
    if (deleteEntry?.user_id == currentUserId) {
        "You deleted this message"
    } else {
        "This message was deleted"
    }
} else {
    messageText.orEmpty()
}

@Composable
private fun messageTextColor(isOwn: Boolean, isDeletedForEveryone: Boolean): Color =
    if (isDeletedForEveryone) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    } else if (isOwn) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

@Composable
private fun ReplyPreview(
    reply: ChatMessage.ReplyMessage,
    isOwn: Boolean
) {
    val indicatorColor =
        if (isOwn) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.secondary

    val cardColor = if (isOwn) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = 0.5f
    )


    Card(
        modifier = Modifier.padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        shape = RoundedCornerShape(size = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(all = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Vertical indicator bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(
                        color = indicatorColor,
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Supriya Parajuli",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = reply.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun ReactionBubble(
    modifier: Modifier = Modifier,
    reaction: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.size(28.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            if (reaction != null) {
                Text(
                    text = reaction,
                    fontSize = 14.sp,           // tuned for 28dp bubble
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = "Reaction",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AnimatedReactionPickerOverlay(
    modifier: Modifier,
    isOwn: Boolean,
    showReactions: Boolean,
    onReactionSelected: (String) -> Unit,
    onDismiss: (reaction?) -> Unit
) {
    if (!isOwn) {

        AnimatedVisibility(
            modifier = modifier,
            visible = showReactions,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            ReactionPickerOverlay(
                onReactionSelected = onReactionSelected,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }
    }
}


@Composable
private fun ReactionPickerOverlay(
    onReactionSelected: (String) -> Unit,
    onDismiss: (reaction?) -> Unit,
    modifier: Modifier = Modifier
) {
    val reactions = listOf("💜", "😂", "😮", "😢", "😡", "👍")

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            reactions.forEach { emoji ->
                AnimatedEmojiItem(
                    emoji = emoji,
                    onClick = {
                        onReactionSelected(emoji)
                        onDismiss(emoji)
                    }
                )
            }

            // Plus button
            IconButton(onClick = { onDismiss(null) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "More reactions"
                )
            }
        }
    }
}

@Composable
fun AnimatedEmojiItem(
    emoji: String,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(emoji) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Text(
        text = emoji,
        fontSize = 24.sp,
        modifier = Modifier
            .scale(scale.value)
            .clickable { onClick() }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    chatState: ChatState,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onVideoCall: () -> Unit
) {

    var chatTopBarSubTitle by remember { mutableStateOf("Online") }
    val avatarUrl = "https://randomuser.me/api/portraits/men/80.jpg"
    val isTyping = chatState.isTyping

    LaunchedEffect(key1 = isTyping) {
        AppLogger.log("Inside launched Effect for typing")
        chatTopBarSubTitle = if (isTyping) {
            "Typing..."
        } else {
            "Online"
        }
    }

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
                        text = "Srijan Khadka",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = chatTopBarSubTitle,
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
    isReplying: ChatState.ReplyMessage,
    focusRequester: FocusRequester,
    onClickedMedia: () -> Unit,
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
        } else if (isReplying.isOn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = isReplying.message?.text.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
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
                    IconButton(onClick = {
                        onClickedMedia()
                    }) {
                        Icon(imageVector = Icons.Default.Photo, contentDescription = "Attach")
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
private fun AnimatedMessageDropDownCompo(
    modifier: Modifier,
    isActionShown: Boolean,
    isOwn: Boolean,
    onDismissActions: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
    AnimatedVisibility(
        visible = isActionShown,
        enter = fadeIn(animationSpec = tween(durationMillis = 150)) + scaleIn(
            animationSpec = tween(
                durationMillis = 150
            ), initialScale = 0.9f
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 100)) + scaleOut(
            animationSpec = tween(
                durationMillis = 100
            ), targetScale = 0.9f
        ),
        modifier = modifier
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

@Composable
private fun MessageActionsDropdown(
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

@Composable
private fun MediaCompo(
    onResult: (List<Uri>) -> Unit,
    onDismiss: () -> Unit
) {
    val galleryManager = rememberGalleryManager(
        onResult = { uris ->
            val parsedUris = uris
                ?.filterNotNull()
                ?.map { it.toUri() }
                ?: emptyList()

            if (parsedUris.isNotEmpty()) {
                onResult(parsedUris)
            }

            onDismiss()
        },
        mediaType = MediaType.IMAGE_ONLY,
        isMultiple = true
    )

    LaunchedEffect(Unit) {
        galleryManager.launch()
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


