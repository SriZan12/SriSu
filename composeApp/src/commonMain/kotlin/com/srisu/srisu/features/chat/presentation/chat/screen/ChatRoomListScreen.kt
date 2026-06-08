package com.srisu.srisu.features.chat.presentation.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomItemDto
import com.srisu.srisu.features.chat.data.remote.response.ChatRoomResponse
import com.srisu.srisu.features.chat.presentation.chat.state.ChatState
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.utils.Constants.ChatConstants.IMAGE
import com.srisu.srisu.utils.DateTimeUtils.getChatTimestamp
import com.srisu.srisu.utils.isInternetAvailable
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.collections.any
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.orEmpty

@Composable
fun ChatRoomScreen(
    viewModel: ChatViewModel,
    onNavigateToChatScreen: (ChatRoomItemDto) -> Unit,
) {
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()

    HandleChatUiStates(
        viewModel = viewModel,
        chatState = chatState,
    )

    ChatRoomContent(
        chatState = chatState,
        viewModel = viewModel,
        onClickChatRoom = onNavigateToChatScreen,
    )
}

@Composable
private fun HandleChatUiStates(
    viewModel: ChatViewModel,
    chatState: ChatState,
) {
    val isConnected = isInternetAvailable()
    var showBottomSheet by remember { mutableStateOf(!isConnected) }

    LaunchedEffect(isConnected) {
        showBottomSheet = !isConnected
    }

    when (val baseUIState = chatState.baseUIState) {
        is BaseUIState.Error -> {
            ErrorDialog(
                title = baseUIState.errorType,
                errorMessage = baseUIState.message,
                show = true,
                onDismiss = viewModel::idleScreen,
            )
        }

        is BaseUIState.Loading -> {
            LoadingScrim(
                onDismissRequest = viewModel::idleScreen,
            )
        }

        is BaseUIState.Success<*> -> {
            SuccessDialog(
                successMessage = baseUIState.message,
                show = true,
                onDismiss = viewModel::idleScreen,
            )
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        BaseUIState.Idle -> Unit
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                viewModel.idleScreen()
            },
        )
    }
}

@Composable
private fun ChatRoomContent(
    chatState: ChatState,
    viewModel: ChatViewModel,
    onClickChatRoom: (ChatRoomItemDto) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PrimaryToolBar(
                title = "Chats",
                onNavigate = {},
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) { innerPadding ->
        ChatRoomListCompo(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            chatRoomList = chatState.chatRoomList,
            me = chatState.session?.id,
            onFetchChatRoom = viewModel::fetchOlderChatRooms,
            onClickChatRoom = onClickChatRoom,
        )
    }
}

@Composable
private fun ChatRoomListCompo(
    modifier: Modifier = Modifier,
    chatRoomList: List<ChatRoomItemDto>,
    onFetchChatRoom: () -> Unit,
    me: Long?,
    onClickChatRoom: (ChatRoomItemDto) -> Unit,
) {
    val myId = me?.toString()
    val listState = rememberLazyListState()
    var isFetching by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalCount) ->
                val shouldFetch =
                    lastVisibleIndex >= totalCount - 3 &&
                            totalCount > 0 &&
                            !isFetching

                if (shouldFetch) {
                    isFetching = true
                    onFetchChatRoom()
                }
            }
    }

    LaunchedEffect(chatRoomList.size) {
        isFetching = false
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
    ) {
        items(
            items = chatRoomList,
            key = { it.id ?: it.hashCode() },
        ) { room ->
            val lastMessageText = room.lastMessage?.let { message ->
                if (message.messageType == IMAGE) "Photo" else message.text.orEmpty()
            }.orEmpty()

            ChatRoomItem(
                avatarUrl = room.otherUser?.profilePhoto,
                name = room.otherUser?.fullName,
                isTyping = isSomeoneElseTyping(
                    me = me,
                    room = room,
                ),
                lastMessage = lastMessageText,
                time = getChatTimestamp(room.updatedAt),
                unreadCount = getUnreadCount(
                    myId = myId,
                    room = room,
                ),
                onClick = { onClickChatRoom(room) },
            )

            HorizontalDivider()
        }
    }
}


@Composable
fun ChatRoomItem(
    avatarUrl: String?,
    name: String?,
    lastMessage: String,
    time: String?,
    unreadCount: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    isTyping: Boolean,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Profile Picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            val isUnread = unreadCount.isNotEmpty()

            if (isTyping) {
                Text(
                    text = "Typing...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = lastMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isUnread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            if (!time.isNullOrEmpty()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (unreadCount.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = unreadCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private fun getUnreadCount(
    myId: String?,
    room: ChatRoomItemDto,
): String {
    return myId
        ?.let { room.unreadCount[it] }
        ?.takeIf { it > 0 }
        ?.toString()
        .orEmpty()
}

private fun isSomeoneElseTyping(
    me: Long?,
    room: ChatRoomItemDto,
): Boolean {
    return room.isTyping.any { (userId, typing) ->
        userId != me?.toString() && typing
    }
}
@Preview()
@Composable
private fun ChatRoomItemPreview() {
    ChatRoomItem(
        avatarUrl = "https://randomuser.me/api/portraits/men/86.jpg",
        name = "Sarthak Koirala",
        lastMessage = "I'd ma haldincha paisa?",
        time = "Fri",
        unreadCount = "3",
        isTyping = false
    )
}

