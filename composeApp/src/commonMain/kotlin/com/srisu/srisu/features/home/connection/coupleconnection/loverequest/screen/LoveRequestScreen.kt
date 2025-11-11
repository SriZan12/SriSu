package com.srisu.srisu.features.home.connection.coupleconnection.loverequest.screen

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
import com.srisu.srisu.components.CommonTabPager
import com.srisu.srisu.features.home.connection.coupleconnection.loverequest.state.LoveRequestListState
import com.srisu.srisu.features.home.connection.coupleconnection.loverequest.vm.LoveRequestViewModel
import com.srisu.srisu.features.home.connection.singleconnection.screen.ConnectionToolBar
import com.srisu.srisu.utils.Constants
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoveRequestScreen(
    loveRequestViewModel: LoveRequestViewModel = koinViewModel(),
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {
    val loveRequestUiState by loveRequestViewModel.loveRequestListState.collectAsStateWithLifecycle()

    LoveRequestContent(
        loveRequestViewModel = loveRequestViewModel,
        loveRequestUiState = loveRequestUiState,
        onNavigateToProfile = { userProfileData ->
            onNavigateToProfile(userProfileData)
        }
    )
}

@Composable
private fun LoveRequestContent(
    loveRequestViewModel: LoveRequestViewModel,
    loveRequestUiState: LoveRequestListState,
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ConnectionToolBar(
                title = "Love Requests"
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding).background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {

            val tabItems = loveRequestUiState.loveRequestTabList
            val pagerState = rememberPagerState { tabItems.size }

            CommonTabPager(
                tabItems = tabItems,
                pagerState = pagerState,
                onUpdateCurrentTab = {
                    loveRequestViewModel.updateCurrentTab(it)
                }
            ) { index ->

                when (index) {
                    0 -> {
                        LoveRequestListScreen(
                            onNavigateToProfile = { userProfileData ->
                                val user =
                                    loveRequestViewModel.getUserProfile(userProfile = userProfileData)
                                onNavigateToProfile(user)
                            },
                            onAcceptLoveRequest = { id, senderNumber, receiverNumber ->
                                loveRequestViewModel.updateLoveRequest(
                                    loveRequestId = id,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = Constants.ConnectionStatus.ACCEPTED
                                )
                            },
                            onRejectLoveRequest = { id, senderNumber, receiverNumber ->
                                loveRequestViewModel.updateLoveRequest(
                                    loveRequestId = id,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = Constants.ConnectionStatus.REJECTED
                                )
                            },
                            loveRequestList = loveRequestViewModel.loveRequests
                        )
                    }

                    1 -> {
                        LoveRequestSentListScreen(
                            onNavigateToProfile = { userProfileData ->
                                val user =
                                    loveRequestViewModel.getUserProfile(userProfile = userProfileData)
                                onNavigateToProfile(user)
                            },
                            onCancelLoveRequest = { id, senderNumber, receiverNumber ->
                                loveRequestViewModel.updateLoveRequest(
                                    loveRequestId = id,
                                    senderNumber = senderNumber,
                                    receiverNumber = receiverNumber,
                                    connectionStatus = Constants.ConnectionStatus.NOTHING
                                )
                            },
                            sentLoveRequestList = loveRequestViewModel.sentLoveRequests
                        )
                    }
                }
            }

        }
    }
}
