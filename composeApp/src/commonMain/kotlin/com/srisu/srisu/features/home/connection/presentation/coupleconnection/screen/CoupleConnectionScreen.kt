package com.srisu.srisu.features.home.connection.presentation.coupleconnection.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import com.srisu.srisu.components.CommonTabPager
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionToolBar
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.state.CoupleConnectionUiState
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm.CoupleConnectionViewModel
import com.srisu.srisu.utils.Constants
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CoupleConnectionScreen(
    coupleConnectionViewModel: CoupleConnectionViewModel = koinViewModel(),
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {
    val coupleConnectionUiState by coupleConnectionViewModel.coupleConnectionUiState.collectAsStateWithLifecycle()
    val loveRequestList = coupleConnectionViewModel.loveRequests.collectAsLazyPagingItems()
    val loveRequestSentList = coupleConnectionViewModel.sentLoveRequests.collectAsLazyPagingItems()

    Initialization(viewModel = coupleConnectionViewModel)

    LoveRequestContent(
        coupleConnectionViewModel = coupleConnectionViewModel,
        coupleConnectionUiState = coupleConnectionUiState,
        loveRequestList = loveRequestList,
        loveRequestSentList = loveRequestSentList,
        onNavigateToProfile = { userProfileData ->
            onNavigateToProfile(userProfileData)
        }
    )
}

@Composable
private fun Initialization(
    viewModel: CoupleConnectionViewModel
) {
    LaunchedEffect(Unit) {
        viewModel.refreshLoveRequests()
        viewModel.refreshSentLoveRequests()
    }

}

@Composable
private fun LoveRequestContent(
    coupleConnectionViewModel: CoupleConnectionViewModel,
    coupleConnectionUiState: CoupleConnectionUiState,
    onNavigateToProfile: (userProfileData: String?) -> Unit,
    loveRequestList: LazyPagingItems<CoupleConnectionRequestResponse.Result>,
    loveRequestSentList: LazyPagingItems<CoupleConnectionRequestResponse.Result>
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ConnectionToolBar(
                title = coupleConnectionUiState.currentTab?.title ?: "Connection"
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                )
        ) {

            val tabItems = coupleConnectionUiState.loveRequestTabList
            val pagerState = rememberPagerState { tabItems.size }

            CommonTabPager(
                tabItems = tabItems,
                pagerState = pagerState,
                onUpdateCurrentTab = coupleConnectionViewModel::updateCurrentTab

            ) { index ->

                when (index) {
                    0 -> {
                        LoveRequestListScreen(
                            onNavigateToProfile = { userProfileData ->
                                val user =
                                    coupleConnectionViewModel.getUserProfile(userProfile = userProfileData)
                                onNavigateToProfile(user)
                            },
                            onAcceptLoveRequest = { id, senderNumber, receiverNumber ->
                                coupleConnectionViewModel.updateLoveRequest(
                                    loveRequestId = id,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = Constants.ConnectionStatus.ACCEPTED
                                )
                            },
                            onRejectLoveRequest = { id, senderNumber, receiverNumber ->
                                coupleConnectionViewModel.updateLoveRequest(
                                    loveRequestId = id,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = Constants.ConnectionStatus.REJECTED
                                )
                            },
                            loveRequestList = loveRequestList
                        )
                    }

                    1 -> {
                        LoveRequestSentListScreen(
                            onNavigateToProfile = { userProfileData ->
                                val user =
                                    coupleConnectionViewModel.getUserProfile(userProfile = userProfileData)
                                onNavigateToProfile(user)
                            },
                            onCancelLoveRequest = { id, senderNumber, receiverNumber ->
                                coupleConnectionViewModel.updateLoveRequest(
                                    loveRequestId = id,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = Constants.ConnectionStatus.NOTHING
                                )
                            },
                            sentLoveRequestList = loveRequestSentList
                        )
                    }
                }
            }

        }
    }
}
