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
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val chatState by viewModel.chatState.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUserId = 97
    val inputFocusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = "Srijan Khadka",
                subtitle = "Online",
                avatarUrl = "https://randomuser.me/api/portraits/men/76.jpg",
                onBack = { navController.popBackStack() },
                onCall = { },
                onVideoCall = { }
            )
        },
        bottomBar = {
            ChatInputBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                value = chatState.messageInput,
                onValueChange = {
                    viewModel.onMessageInputChanged(value = it)
                },
                onSend = {
                    if (chatState.messageInput.text.isNotBlank() && !chatState.isEditMessage) {
                        viewModel.sendMessage()
                    } else {
                        viewModel.editMessage(messageId = chatState.selectedMessageForAction?.id)
                    }
                },
                enabled = true,
                focusRequester = inputFocusRequester
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            error?.let { errorMessage ->
                ErrorBanner(
                    message = errorMessage,
                    onDismiss = { viewModel.clearError() },
                    onRetry = { viewModel.retryConnection() }
                )
            }

            val listState = rememberLazyListState()
            val chatMessages = chatState.chatMessages

            LaunchedEffect(chatMessages?.size) {
                if (!chatMessages.isNullOrEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }


            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = listState,
                reverseLayout = true
            ) {
                items(
                    items = chatMessages ?: emptyList(),
                    key = { (it?.id ?: it?.timestamp ?: Clock.System.now().epochSeconds) }
                ) { message ->

                    if (message != null) {
                        val isSentByCurrentUser = message.senderId == currentUserId
                        var triggerFocus by remember { mutableStateOf(false) }
                        val keyboardController = LocalSoftwareKeyboardController.current

                        AnimatedMessageItem(
                            chatMessage = message,
                            isSentByCurrentUser = isSentByCurrentUser,
                            onLongClick = {
                                viewModel.showActionsForMessage(it.id, it)
                            },
                            onClick = { },
                            onDismiss = {
                                viewModel.dismissActions()
                            },
                            onReply = { },
                            onCopy = { },
                            onEdit = {
                                viewModel.setMessageInputText(text = chatState.selectedMessageForAction?.text.orEmpty())
                                viewModel.dismissActions()
                                triggerFocus = true
                                viewModel.updateIsEditMessage(true)
                            },
                            onDelete = {
                                viewModel.deleteMessage(messageId = message.id)
                            },
                            showChatActionDropDown = chatState.selectedMessageIdForActions == message.id
                        )

                        LaunchedEffect(triggerFocus) {
                            if (triggerFocus) {
                                delay(100) // CRITICAL: let dialog fully dismiss
                                inputFocusRequester.requestFocus()
                                keyboardController?.show()
                                triggerFocus = false
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
fun ChatTopBar(
    title: String,
    subtitle: String = "Online",
    avatarUrl: String,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onCall) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Voice call",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onVideoCall) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video call",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Row {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    modifier: Modifier,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f)
                .padding(end = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Type a message") },
            enabled = enabled,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = { onSend() }
            ),
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Voice message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceDim,
                unfocusedIndicatorColor = Color.Transparent,
            )

        )

        AnimatedContent(
            targetState = value.text.isNotBlank(),
            transitionSpec = {
                fadeIn(tween(150)) + scaleIn() togetherWith
                        fadeOut(tween(150)) + scaleOut()
            },
            label = "SendSwitch"
        ) { hasText ->

            if (hasText) {
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
fun AnimatedMessageItem(
    chatMessage: ChatMessage,
    isSentByCurrentUser: Boolean,
    onLongClick: (ChatMessage) -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canEdit: Boolean = true,
    showChatActionDropDown: Boolean,
) {

    // Prevent re-playing animation on scroll
    val hasAnimatedIn = remember(chatMessage.id) { mutableStateOf(false) }

    LaunchedEffect(chatMessage.id) {
        hasAnimatedIn.value = true
    }

    AnimatedVisibility(
        visible = hasAnimatedIn.value,
        enter = fadeIn(tween(220)) +
                slideInVertically(
                    animationSpec = tween(220),
                    initialOffsetY = { it / 6 }
                ) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(220)
                ),
        exit = fadeOut(tween(120))
    ) {

        Box {

            MessageItem(
                modifier = Modifier,
                chatMessage = chatMessage,
                isSentByCurrentUser = isSentByCurrentUser,
                onLongClick = { onLongClick(chatMessage) },
                onClick = onClick
            )

            val dropDownAlignment =
                if (isSentByCurrentUser) Alignment.TopEnd else Alignment.TopStart

            AnimatedVisibility(
                visible = showChatActionDropDown,
                enter = fadeIn(tween(120)) +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = tween(120)
                        ),
                exit = fadeOut(tween(90)) +
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = tween(90)
                        ),
                modifier = Modifier.align(dropDownAlignment)
            ) {
                MessageActionsDropdown(
                    expanded = true,
                    onDismiss = onDismiss,
                    onReply = onReply,
                    onCopy = onCopy,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    canEdit = canEdit
                )
            }
        }
    }
}


@Composable
fun MessageItem(
    modifier: Modifier = Modifier,
    chatMessage: ChatMessage,
    isSentByCurrentUser: Boolean,
    onLongClick: (chatMessage: ChatMessage) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(chatMessage)
                }
            )
    ) {

        val romanticShape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (isSentByCurrentUser) 20.dp else 4.dp,
            bottomEnd = if (isSentByCurrentUser) 4.dp else 20.dp
        )

        val alignment = if (isSentByCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
        val backgroundColor = if (isSentByCurrentUser)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainer

        val textColor = if (isSentByCurrentUser)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurface

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = alignment
        ) {
            Card(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = romanticShape,
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Text(
                    text = chatMessage.text.orEmpty(),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                )
            }
        }
    }
}


@Composable
fun MessageActionsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canEdit: Boolean = true,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {

        DropdownMenuItem(
            text = { Text("Reply") },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
            },
            onClick = {
                onReply()
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
            },
            onClick = {
                onCopy()
                onDismiss()
            }
        )

        if (canEdit) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
                onClick = {
                    onEdit()
                    onDismiss()
                }
            )
        }

        HorizontalDivider()

        DropdownMenuItem(
            text = {
                Text(
                    "Delete for me",
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDelete()
                onDismiss()
            }
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = {
                Text(
                    "Delete for everyone",
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDelete()
                onDismiss()
            }
        )
    }
}


@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor
        )
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


@Preview
@Composable
private fun MessageItemPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
        MessageItem(
            modifier = Modifier,
            chatMessage = ChatMessage(text = "Hello! How are you?", senderId = 1),
            isSentByCurrentUser = true,
            onLongClick = {},
            onClick = {}
        )
        MessageItem(
            chatMessage = ChatMessage(
                text = "I'm doing great, thanks for asking! How about you?",
                senderId = 2
            ),
            isSentByCurrentUser = false,
            onLongClick = {},
            onClick = {},
            modifier = Modifier
        )
    }
}

@Composable
@Preview
private fun ChatInputCompo() {
    ChatInputBar(
        modifier = Modifier,
        value = TextFieldValue(),
        onValueChange = {},
        onSend = {},
        enabled = true,
        focusRequester = FocusRequester()
    )
}

@Composable
@Preview
private fun MessageActionsDialogPreview() {
    MaterialTheme {
        MessageActionsDropdown(
            expanded = true,
            onDismiss = {},
            onReply = {},
            onCopy = {},
            onEdit = {},
            onDelete = {},
            canEdit = true
        )
    }
}
