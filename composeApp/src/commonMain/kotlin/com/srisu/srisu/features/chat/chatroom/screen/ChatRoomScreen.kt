package com.srisu.srisu.features.chat.chatroom.screen

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.ErrorDialog
import com.srisu.srisu.components.LoadingScrim
import com.srisu.srisu.components.OfflineBottomSheetCompo
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.components.SuccessDialog
import com.srisu.srisu.core.data.apiservice.base.BaseApiService.Companion.BASE_URL
import com.srisu.srisu.core.data.dto.chatdto.ChatRoom
import com.srisu.srisu.core.data.response.chat.ChatRoomResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.chat.chatroom.ChatState
import com.srisu.srisu.features.chat.chatroom.ChatViewModel
import com.srisu.srisu.session.Session
import com.srisu.srisu.utils.isInternetAvailable
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatRoomScreen(
    session: Session?,
    viewModel: ChatViewModel = koinViewModel()
) {
    val chatState by viewModel.chatState.collectAsState()

    Initialization(
        session = session,
        viewModel = viewModel
    )

    HandleUiStates(
        chatRoomVm = viewModel,
        chatUiState = chatState
    )

    ChatRoomContent(
        chatState = chatState,
        viewModel = viewModel
    )

}

@Composable
private fun Initialization(
    session: Session?,
    viewModel: ChatViewModel
) {
    viewModel.updateSession(session = session)
}

@Composable
private fun HandleUiStates(
    chatRoomVm: ChatViewModel,
    chatUiState: ChatState
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
                onDismiss = {
                    chatRoomVm.idleScreen()
                },
            )
        }

        is BaseUIState.Loading -> {
            AppLogger.log("I am loading....")

            LoadingScrim(
                onDismissRequest = {
                    chatRoomVm.idleScreen()
                }
            )
        }

        is BaseUIState.Success<*> -> {
            SuccessDialog(
                successMessage = baseUIState.message,
                show = true,
                onDismiss = {
                    chatRoomVm.idleScreen()
                }
            )
        }

        is BaseUIState.NoInternetConnection -> {
            showBottomSheet = baseUIState.isOffline
        }

        is BaseUIState.Idle -> {
            Unit
        }
    }

    if (showBottomSheet) {
        OfflineBottomSheetCompo(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                chatRoomVm.idleScreen()
            }
        )
    }
}

@Composable
private fun ChatRoomContent(
    chatState: ChatState,
    viewModel: ChatViewModel
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PrimaryToolBar(
                title = "Chats",
                onNavigate = {},
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding)) {
            ChatRoomListCompo(
                chatRoomList = chatState.chatRoomList,
                me = chatState.session?.id,
                onFetchChatRoom = {
                    viewModel.fetchNewChatRooms()
                }
            )
        }
    }
}


@Composable
private fun ChatRoomListCompo(
    chatRoomList: List<ChatRoomResponse.Data.ChatRoom?>,
    onFetchChatRoom: () -> Unit,
    me: Int?
) {
    val myId = me?.toString()
    val listState = rememberLazyListState()

    // Prevent multiple triggers
    var isFetching by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItemIndex =
                layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItemsCount = layoutInfo.totalItemsCount

            lastVisibleItemIndex to totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItems) ->

                val shouldFetch =
                    lastVisibleIndex >= totalItems - 3 && // threshold
                            totalItems > 0 &&
                            !isFetching

                if (shouldFetch) {
                    isFetching = true
                    AppLogger.log("Reached bottom, fetching more chat rooms")
                    onFetchChatRoom()
                }
            }
    }

    // Reset fetching flag when new data arrives
    LaunchedEffect(chatRoomList.size) {
        isFetching = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        items(
            items = chatRoomList,
            key = { it?.chatRoom?.id ?: it.hashCode() }
        ) { chatRoom ->

            val otherUser = chatRoom?.otherUser
            val room = chatRoom?.chatRoom

            val unreadCount = myId
                ?.let { room?.unreadCount?.get(it) }
                ?.takeIf { it > 0 }
                ?.toString()
                .orEmpty()

            ChatRoomItem(
                avatarUrl = otherUser?.profilePhoto.orEmpty(),
                name = otherUser?.fullName.orEmpty(),
                lastMessage = room?.lastMessage?.text.orEmpty(),
                time = "Friday",
                unreadCount = unreadCount,
                modifier = Modifier,
                onClick = {}
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
    time: String,
    unreadCount: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AsyncImage(
            model = avatarUrl,
            contentDescription = "Profile Picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))
            val isUnread = unreadCount.isNotEmpty()

            Text(
                text = lastMessage,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isUnread)
                    MaterialTheme.colorScheme.onSurface
                else
                    Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            if (unreadCount.isNotEmpty()) {
                AppLogger.log("UnRead count = $unreadCount")
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
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
        unreadCount = "3"
    )
}

