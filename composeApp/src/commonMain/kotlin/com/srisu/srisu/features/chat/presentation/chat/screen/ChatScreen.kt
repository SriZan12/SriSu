package com.srisu.srisu.features.chat.presentation.chat.screen

// ChatScreen.kt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
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
import coil3.Uri
import coil3.compose.AsyncImage
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.ImageViewerScreen
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomsData
import com.srisu.srisu.features.chat.presentation.chat.state.ChatState
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_EVERYONE
import com.srisu.srisu.utils.Constants.ChatConstants.DELETE_FOR_ME
import com.srisu.srisu.utils.Constants.ChatConstants.IMAGE
import com.srisu.srisu.utils.Constants.ChatConstants.TEXT
import com.srisu.srisu.utils.DateTimeUtils.formatTimeInHourAndMinute
import com.srisu.srisu.utils.MediaType
import com.srisu.srisu.utils.isInternetAvailable
import com.srisu.srisu.utils.rememberGalleryManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.random.Random

typealias reaction = String
typealias messageId = Long?

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavBack: () -> Unit,
    session: Session?,
) {
    val chatState by viewModel.chatState.collectAsState()

    Initialization(
        chatViewModel = viewModel,
        session = session,
    )

    HandleUiStates(
        chatRoomVm = viewModel,
        chatUiState = chatState,
    )

    ChatContent(
        chatState = chatState,
        viewModel = viewModel,
        onNavBack = onNavBack,
    )
}

@Composable
private fun Initialization(
    chatViewModel: ChatViewModel,
    session: Session?,
) {
    LaunchedEffect(chatViewModel) {
        chatViewModel.updateSession(session = session)
//        chatViewModel.setChatRoomData()
    }
}

@Composable
private fun HandleUiStates(
    chatRoomVm: ChatViewModel,
    chatUiState: ChatState,
) {
    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = chatUiState.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = chatRoomVm::idleScreen,
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = chatRoomVm::idleScreen,
            )
        }

        is BaseUIState.Success<*> -> {
            SuccessDialog(
                successMessage = baseUIState.message,
                show = true,
                onDismiss = chatRoomVm::idleScreen,
            )
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> Unit
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                chatRoomVm.idleScreen()
            },
        )
    }
}

@Composable
private fun ChatContent(
    chatState: ChatState,
    viewModel: ChatViewModel,
    onNavBack: () -> Unit,
) {
    val showImageScreen = chatState.showImageScreen

    if (showImageScreen.show && showImageScreen.images.isNotEmpty()) {
        ImageViewerScreen(
            images = showImageScreen.images,
            startIndex = showImageScreen.startingIndex,
            onDismiss = {
                viewModel.updateShowImageScreen(
                    show = false,
                    images = emptyList(),
                    startingIndex = 0,
                )
            },
        )
        return
    }

    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    var openGallery by remember { mutableStateOf(false) }

    LaunchedEffect(chatState.chatMessages.size, chatState.chatRoomData?.id) {
        if (chatState.chatMessages.isNotEmpty() && chatState.chatRoomData?.id != null) {
            listState.animateScrollToItem(0)
            viewModel.sendMessageDeliveredRequest()
            viewModel.sendMessageReadRequest()
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                chatState = chatState,
                onBack = onNavBack,
                onCall = {},
                onVideoCall = {},
            )
        },
        bottomBar = {
            ChatInputBar(
                value = chatState.messageInput,
                onValueChange = viewModel::onMessageInputChanged,
                onSend = {
                    if (chatState.messageInput.text.isNotBlank()) {
                        if (chatState.isEditMessage) {
                            viewModel.editMessage(chatState.selectedMessageForAction)
                        } else {
                            viewModel.sendTextMessage()
                        }
                    }
                },
                onCancelEdit = {
                    viewModel.updateIsEditMessage(false)
                    viewModel.setReplyMessage(chatMessage = null, isOn = false)
                    inputFocusRequester.freeFocus()
                    viewModel.onMessageInputChanged(TextFieldValue())
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ChatMessagesList(
                messages = chatState.chatMessages,
                currentUserId = chatState.session?.id,
                listState = listState,
                selectedMessageId = chatState.selectedMessageIdForActions,
                onMessageLongClick = { id, message ->
                    viewModel.showActionsForMessage(
                        messageId = id,
                        message = message,
                    )
                },
                onDismissActions = viewModel::dismissActions,
                onEdit = { message ->
                    viewModel.setMessageInputText(message.text.orEmpty())
                    viewModel.updateIsEditMessage(true)
                    inputFocusRequester.requestFocus()
                },
                onDelete = { deleteOption, messageId ->
                    viewModel.deleteMessage(
                        deleteOption = deleteOption,
                        messageId = messageId,
                    )
                },
                onFetchOlder = viewModel::fetchOlderMessages,
                onReactionSelected = { reaction, messageId ->
                    viewModel.reactToMessage(
                        reaction = reaction,
                        messageId = messageId,
                    )
                },
                onReplyMessage = { message ->
                    viewModel.setReplyMessage(chatMessage = message, isOn = true)
                    inputFocusRequester.requestFocus()
                },
                onPhotoClick = { images, startingIndex ->
                    viewModel.updateShowImageScreen(
                        show = true,
                        images = images,
                        startingIndex = startingIndex,
                    )
                },
                onMessageRepliedClicked = { /* future scroll-to-replied-message */ },
            )

            if (openGallery) {
                MediaCompo(
                    onResult = { uris ->
                        viewModel.updateSelectedPhotos(uris)
                        viewModel.uploadMedias()
                        openGallery = false
                    },
                    onDismiss = {
                        openGallery = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    currentUserId: Long?,
    listState: LazyListState,
    selectedMessageId: Long?,
    onMessageLongClick: (Long?, ChatMessage) -> Unit,
    onDismissActions: () -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onDelete: (String, Long?) -> Unit,
    onFetchOlder: () -> Unit,
    onReactionSelected: (String, Long?) -> Unit,
    onReplyMessage: (ChatMessage) -> Unit,
    onPhotoClick: (List<String?>, Int) -> Unit,
    onMessageRepliedClicked: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFetchingOlder by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            val totalItemsCount = layoutInfo.totalItemsCount
            firstVisibleIndex to totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (firstVisibleIndex, totalItems) ->
                val shouldFetchOlder = firstVisibleIndex <= 2 &&
                        totalItems > 0 &&
                        !isFetchingOlder

                if (shouldFetchOlder) {
                    isFetchingOlder = true
                    onFetchOlder()
                }
            }
    }

    LaunchedEffect(messages.size) {
        isFetchingOlder = false
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = messages,
            key = { it.id ?: it.hashCode() },
        ) { message ->
            AnimatedMessageItem(
                message = message,
                currentUserId = currentUserId,
                isOwn = message.senderId == currentUserId,
                isActionShown = selectedMessageId == message.id,
                onLongClick = { onMessageLongClick(message.id, message) },
                onDismissActions = onDismissActions,
                onEdit = { onEdit(message) },
                onDeleteForMe = { onDelete(DELETE_FOR_ME, message.id) },
                onDeleteForEveryone = { onDelete(DELETE_FOR_EVERYONE, message.id) },
                onReactionSelected = { reaction -> onReactionSelected(reaction, message.id) },
                onReplyMessage = onReplyMessage,
                onPhotoClick = onPhotoClick,
                onClickMessageReplied = onMessageRepliedClicked,
            )
        }
    }
}


@Composable
private fun AnimatedMessageItem(
    message: ChatMessage,
    currentUserId: Long?,
    isOwn: Boolean,
    isActionShown: Boolean,
    onLongClick: () -> Unit,
    onDismissActions: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onReplyMessage: (ChatMessage) -> Unit,
    onClickMessageReplied: (Long?) -> Unit,
    onPhotoClick: (List<String?>, Int) -> Unit,
) {
    var showReactions by remember { mutableStateOf(false) }

    Box {
        SwipeableMessageCompo(
            message = message,
            currentUserId = currentUserId,
            isOwn = isOwn,
            onLongClick = onLongClick,
            onReplyMessage = onReplyMessage,
            onReactionClick = { showReactions = true },
            onPhotoClick = onPhotoClick,
            onClickMessageReplied = onClickMessageReplied,
        )

        AnimatedMessageDropDownCompo(
            modifier = Modifier.align(
                if (isOwn) Alignment.TopEnd else Alignment.TopStart
            ),
            isOwn = isOwn,
            isActionShown = isActionShown,
            onDismissActions = onDismissActions,
            onEdit = onEdit,
            onDeleteForMe = onDeleteForMe,
            onDeleteForEveryone = onDeleteForEveryone,
            canEdit = isOwn && message.messageType != IMAGE,
            canCopy = message.messageType == TEXT,
        )

        AnimatedReactionPickerOverlay(
            modifier = Modifier.align(Alignment.TopEnd),
            isOwn = isOwn,
            showReactions = showReactions,
            onReactionSelected = onReactionSelected,
            onDismiss = { showReactions = false },
        )
    }
}

@Composable
private fun SwipeableMessageCompo(
    message: ChatMessage,
    currentUserId: Long?,
    isOwn: Boolean,
    onLongClick: () -> Unit,
    onReplyMessage: (ChatMessage) -> Unit,
    onReactionClick: () -> Unit,
    onClickMessageReplied: (Long?) -> Unit,
    onPhotoClick: (List<String?>, Int) -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        confirmValueChange = { newValue ->
            when (newValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (message.deleteFor == null) {
                        onReplyMessage(message)
                    }
                    false
                }

                SwipeToDismissBoxValue.EndToStart -> false
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        SwipeToDismissBox(
            state = swipeState,
            modifier = Modifier.wrapContentWidth(),
            backgroundContent = {},
        ) {
            when (message.messageType) {
                TEXT -> {
                    MessageBubble(
                        message = message,
                        currentUserId = currentUserId,
                        isOwn = isOwn,
                        onLongClick = onLongClick,
                        onReactionClick = onReactionClick,
                        onClickMessageReplied = onClickMessageReplied,
                    )
                }

                IMAGE -> {
                    PhotoMessageBubble(
                        message = message,
                        currentUserId = currentUserId,
                        isOwn = isOwn,
                        onLongClick = onLongClick,
                        onReactionClick = onReactionClick,
                        onPhotoClick = onPhotoClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    currentUserId: Long?,
    isOwn: Boolean,
    onLongClick: () -> Unit,
    onReactionClick: () -> Unit,
    onClickMessageReplied: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var bubbleWidthPx by remember { mutableStateOf(0) }

    val isDeletedForEveryone =
        message.isDeleted == true && message.deleteOption == DELETE_FOR_EVERYONE
    val finalBackground = if (isDeletedForEveryone) {
        backgroundColor(isOwn).copy(alpha = 0.7f)
    } else {
        backgroundColor(isOwn)
    }

    val displayedReaction = message.reactions[currentUserId?.toString()]
        ?: message.reactions.values.firstOrNull()

    Box(
        modifier = modifier
            .wrapContentWidth()
            .then(
                clickableModifier(
                    isDeletedForEveryone = isDeletedForEveryone,
                    haptic = haptic,
                    onLongClick = onLongClick,
                    onClick = {},
                )
            )
    ) {
        Card(
            shape = bubbleShape(isOwn),
            colors = CardDefaults.cardColors(containerColor = finalBackground),
            modifier = Modifier
                .widthIn(max = 240.dp)
                .onSizeChanged { bubbleWidthPx = it.width },
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                if (!isDeletedForEveryone) {
                    message.replyTo?.let {
                        ReplyPreview(
                            reply = it,
                            isOwn = isOwn,
                            onClickMessageReplied = onClickMessageReplied,
                        )
                    }
                }

                Text(
                    text = messageDisplayText(
                        isOwn = isOwn,
                        isDeletedForEveryone = isDeletedForEveryone,
                        messageText = message.text,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = messageTextColor(
                        isOwn = isOwn,
                        isDeletedForEveryone = isDeletedForEveryone,
                    ),
                    fontStyle = if (isDeletedForEveryone) FontStyle.Italic else FontStyle.Normal,
                )

                if (!isDeletedForEveryone) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val formattedTime = formatTimeInHourAndMinute(message.timestamp)

                        if (formattedTime.isNotEmpty()) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isOwn) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    }
                                ),
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }

                        if (isOwn) {
                            when {
                                message.isRead == true -> {
                                    Icon(
                                        Icons.Default.DoneAll,
                                        contentDescription = "Read",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                else -> {
                                    Icon(
                                        Icons.Default.Done,
                                        contentDescription = "Sent",
                                        tint = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val bubbleWidthDp = with(density) { bubbleWidthPx.toDp() }

        if (!isOwn && bubbleWidthPx > 0 && message.deleteFor == null && !isDeletedForEveryone) {
            ReactionBubble(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = bubbleWidthDp + 6.dp),
                onClick = onReactionClick,
                reaction = displayedReaction,
            )
        } else if (displayedReaction != null && isOwn && bubbleWidthPx > 0 && !isDeletedForEveryone) {
            ReactionBubble(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -(bubbleWidthDp + 6.dp)),
                onClick = onReactionClick,
                reaction = displayedReaction,
            )
        }
    }
}

@Composable
fun PhotoMessageBubble(
    message: ChatMessage,
    currentUserId: Long?,
    isOwn: Boolean,
    onLongClick: () -> Unit,
    onReactionClick: () -> Unit,
    onPhotoClick: (List<String?>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var bubbleWidthPx by remember { mutableStateOf(0) }

    val deleteEntry = message.deleteFor
        ?.values
        ?.flatten()
        ?.firstOrNull { it.option == DELETE_FOR_EVERYONE }

    val isDeletedForEveryone = deleteEntry != null
    val displayedReaction = message.reactions[currentUserId?.toString()]
        ?: message.reactions.values.firstOrNull()

    Box(
        modifier = modifier.wrapContentWidth(),
    ) {
        Card(
            shape = bubbleShape(isOwn),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier
                .widthIn(max = 260.dp)
                .onSizeChanged { bubbleWidthPx = it.width },
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (message.isLocalOnly && message.uploadingPhotos.isNotEmpty()) {
                    UploadingPhotoGrid(message.uploadingPhotos)
                } else {
                    if (!isDeletedForEveryone) {
                        if (message.medias.isNotEmpty()) {
                            PhotoGrid(
                                photos = message.medias,
                                onPhotoClick = { index ->
                                    onPhotoClick(
                                        message.medias.map { it.mediaUrl },
                                        index,
                                    )
                                },
                                onLongClickPhoto = onLongClick,
                            )
                        }
                    } else {
                        Text(
                            text = messageDisplayText(
                                isOwn = isOwn,
                                isDeletedForEveryone = true,
                                messageText = "",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = messageTextColor(isOwn, true),
                        )
                    }

                    if (!isDeletedForEveryone) {
                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val formattedTime = formatTimeInHourAndMinute(message.timestamp)

                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 4.dp),
                            )

                            if (isOwn) {
                                when {
                                    message.isRead == true -> {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Read",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }

                                    message.isDelivered == true -> {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Delivered",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = "Sent",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val bubbleWidthDp = with(density) { bubbleWidthPx.toDp() }

        if (!isOwn && bubbleWidthPx > 0 && message.deleteFor == null && !isDeletedForEveryone) {
            ReactionBubble(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = bubbleWidthDp + 6.dp),
                onClick = onReactionClick,
                reaction = displayedReaction,
            )
        } else if (displayedReaction != null && isOwn && bubbleWidthPx > 0 && !isDeletedForEveryone) {
            ReactionBubble(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -(bubbleWidthDp + 6.dp)),
                onClick = onReactionClick,
                reaction = displayedReaction,
            )
        }
    }
}


@Composable
private fun PhotoGrid(
    photos: List<ChatMessage.Media>,
    modifier: Modifier = Modifier,
    onPhotoClick: (Int) -> Unit,
    onLongClickPhoto: () -> Unit,
) {
    when (photos.size) {
        1 -> {
            AsyncImage(
                model = photos.first().mediaUrl,
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        clickableModifier(
                            isDeletedForEveryone = false,
                            haptic = LocalHapticFeedback.current,
                            onLongClick = onLongClickPhoto,
                            onClick = { onPhotoClick(0) },
                        )
                    ),
            )
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false,
            ) {
                itemsIndexed(photos.take(4)) { index, photo ->
                    Box {
                        AsyncImage(
                            model = photo.mediaUrl,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    clickableModifier(
                                        isDeletedForEveryone = false,
                                        haptic = LocalHapticFeedback.current,
                                        onLongClick = onLongClickPhoto,
                                        onClick = { onPhotoClick(index) },
                                    )
                                ),
                        )

                        if (index == 3 && photos.size > 4) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+${photos.size - 4}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadingPhotoGrid(
    photos: List<ChatMessage.UploadingPhoto>,
) {
    when (photos.size) {
        1 -> {
            UploadingPhotoItem(photo = photos.first())
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false,
            ) {
                itemsIndexed(photos.take(4)) { index, photo ->
                    Box {
                        UploadingPhotoItem(photo = photo)

                        if (index == 3 && photos.size > 4) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+${photos.size - 4}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadingPhotoItem(
    photo: ChatMessage.UploadingPhoto?
) {
    Box {
        AsyncImage(
            model = photo?.localUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .aspectRatio(ratio = 1f)
                .alpha(alpha = 0.6f)
        )

        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun ReplyPreview(
    reply: ChatMessage.ReplyMessage,
    isOwn: Boolean,
    onClickMessageReplied: (Long?) -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Card(
        modifier = Modifier.padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(8.dp),
        onClick = { onClickMessageReplied(reply.id) },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(2.dp),
                    )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = reply.messageOwnerName ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = if (reply.messageType == IMAGE) "Photo" else reply.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onVideoCall: () -> Unit,
) {
    val isTyping = chatState.isTyping
    val subtitle by remember(isTyping) {
        mutableStateOf(if (isTyping) "Typing..." else "Online")
    }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = chatState.chatRoomData?.otherUser?.profilePhoto,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = chatState.chatRoomData?.otherUser?.fullName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(
                onClick = onCall,
                colors = IconButtonDefaults.filledIconButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call")
            }
            IconButton(
                onClick = onVideoCall,
                colors = IconButtonDefaults.filledIconButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "Video call")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                val replyingOnText =
                    if (isReplying.message?.messageType == IMAGE) "Photo"
                    else isReplying.message?.text.orEmpty()

                Text(
                    text = replyingOnText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { onCancelEdit() }
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
    canEdit: Boolean,
    canCopy: Boolean
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
            canEdit = canEdit,
            canCopy = canCopy
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
    canEdit: Boolean,
    canCopy: Boolean
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
    ) {

        if (canCopy) {
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = { onDismiss() }
            )
        }

        if (canEdit) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = {
                    onEdit()
                    onDismiss()
                }
            )

            HorizontalDivider()

        }


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
    val startX: Float,           // starting horizontal position
    val size: Float,
    val delay: Int,
    val duration: Int,           // different hearts can have slightly different speeds
    val drift: Float             // small horizontal movement
)

@Composable
fun FloatingHearts(
    count: Int = 16,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val screenWidthPx = with(density) { maxWidth.toPx() }

        val centerX = screenWidthPx / 2f
        val spread = screenWidthPx * 0.4f

        val hearts = remember(screenWidthPx) {
            List(count) {
                FloatingHeart(
                    startX = centerX + Random.nextFloat() * spread - spread / 2f,
                    size = Random.nextInt(16, 32).toFloat(),
                    delay = Random.nextInt(0, 4000),
                    duration = Random.nextInt(2800, 4800),
                    drift = Random.nextFloat() * 120f - 60f
                )
            }
        }

        hearts.forEach { heart ->
            FloatingHeartItem(
                heart = heart,
                screenHeightPx = screenHeightPx
            )
        }
    }
}


@Composable
private fun FloatingHeartItem(
    heart: FloatingHeart,
    screenHeightPx: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "floatingHeart")

    val offsetY by infiniteTransition.animateFloat(
        initialValue = screenHeightPx + 100f,           // start below screen
        targetValue = -heart.size * 2,           // go well above screen
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = heart.duration,
                delayMillis = heart.delay,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "offsetY"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = heart.duration,
                delayMillis = heart.delay,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    val offsetX by infiniteTransition.animateFloat(
        initialValue = heart.startX,
        targetValue = heart.startX + heart.drift,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = heart.duration * 2,  // slower drift
                delayMillis = heart.delay,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse          // go left → right → left...
        ),
        label = "offsetX"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
        modifier = Modifier
            .size(heart.size.dp)
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
                this.alpha = alpha.coerceIn(0f, 0.8f)
                rotationZ = (offsetY / screenHeightPx) * 15f - 7.5f
            }
    )
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
    onLongClick: () -> Unit,
    onClick: () -> Unit
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
        onClick = onClick
    )
}

@Composable
private fun messageDisplayText(
    isOwn: Boolean,
    isDeletedForEveryone: Boolean,
    messageText: String?,
) = if (isDeletedForEveryone) {
    if (isOwn) {
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






