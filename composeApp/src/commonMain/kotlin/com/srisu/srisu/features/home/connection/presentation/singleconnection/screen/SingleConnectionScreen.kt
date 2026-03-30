package com.srisu.srisu.features.home.connection.presentation.singleconnection.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import com.srisu.srisu.components.CommonTabPager
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.SingleConnectionResponse
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItem
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItemShimmer
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionToolBar
import com.srisu.srisu.features.home.connection.presentation.components.NoConnectionsFound
import com.srisu.srisu.features.home.connection.presentation.singleconnection.state.ConnectionUIState
import com.srisu.srisu.features.home.connection.presentation.singleconnection.vm.SingleConnectionViewModel
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import com.srisu.srisu.utils.Constants.ConnectionStatus.NOTHING
import com.srisu.srisu.utils.Constants.ConnectionStatus.REJECTED
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun SingleConnectionScreen(
    singleConnectionViewModel: SingleConnectionViewModel,
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {
    val connectionUiState by singleConnectionViewModel.connectionUiState.collectAsStateWithLifecycle()
    val myCrushList = singleConnectionViewModel.myCrushList.collectAsLazyPagingItems()
    val crushOnMeList = singleConnectionViewModel.crushOnMeList.collectAsLazyPagingItems()

    Initialization(viewModel = singleConnectionViewModel)

    ConnectionScreenContent(
        viewModel = singleConnectionViewModel,
        connectionUiState = connectionUiState,
        myCrushList = myCrushList,
        crushOnMeList = crushOnMeList,
        onNavigateToProfile = onNavigateToProfile
    )
}

@Composable
private fun Initialization(
    viewModel: SingleConnectionViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
//        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AppLogger.log("SingleConnectionScreen Initialization")
            viewModel.refreshMyCrushList()
            viewModel.refreshCrushOnMeList()

//        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreenContent(
    viewModel: SingleConnectionViewModel,
    connectionUiState: ConnectionUIState,
    myCrushList: LazyPagingItems<SingleConnectionResponse.Result>,
    crushOnMeList: LazyPagingItems<SingleConnectionResponse.Result>,
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ConnectionToolBar(
                title = connectionUiState.currentTab?.title ?: "Connection"
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) { innerPadding ->

        val tabItems = connectionUiState.connectionTabList
        val pagerState = rememberPagerState { tabItems.size }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            CommonTabPager(
                tabItems = tabItems,
                pagerState = pagerState,
                onUpdateCurrentTab = viewModel::updateCurrentTab
            ) { index ->
                when (index) {
                    0 -> {
                        MyCrushScreen(
                            crushList = myCrushList,
                            onNavigateToProfile = { receiver ->
                                onNavigateToProfile(viewModel.getUserProfile(receiver))
                            },
                            onCancelCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
                                viewModel.updateCrushRequest(
                                    crushRequestId = crushRequestId,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = NOTHING
                                )
                            }
                        )
                    }

                    1 -> {
                        CrushOnMeScreen(
                            crushOnMeList = crushOnMeList,
                            onAcceptCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
                                viewModel.updateCrushRequest(
                                    crushRequestId = crushRequestId,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = ACCEPTED
                                )
                            },
                            onRejectCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
                                viewModel.updateCrushRequest(
                                    crushRequestId = crushRequestId,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = REJECTED
                                )
                            },
                            onNavigateToProfile = { receiver ->
                                onNavigateToProfile(viewModel.getUserProfile(receiver))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T : Any> PagedConnectionContent(
    items: LazyPagingItems<T>,
    emptyTitle: String,
    loadingContent: @Composable () -> Unit,
    listContent: @Composable () -> Unit
) {
    when {
        items.loadState.refresh is LoadState.Loading -> {
            loadingContent()
        }

        items.itemCount == 0 -> {
            AnimatedEmptyState(title = emptyTitle)
        }

        else -> {
            listContent()
        }
    }
}

@Composable
private fun AnimatedEmptyState(
    title: String,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(600)) +
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
    ) {
        NoConnectionsFound(
            modifier = modifier.fillMaxSize(),
            title = title
        )
    }
}


@Preview
@Composable
fun PreviewShimmerEffect() {

    ConnectionItemShimmer()

}

@Preview
@Composable
fun PreviewConnectionItem() {
    ConnectionItem(
        modifier = Modifier,
        userName = "Srisu",
        userImage = "https://photosmint.com/wp-content/uploads/2025/03/Indian-Beauty-DP.jpeg",
        dob = "23",
        zodiacSign = "Leo",
        firstButtonTitle = "Connect",
        secondButtonTitle = "Cancel",
        onClick = {},
        onClickFirstButton = {}
    )
}