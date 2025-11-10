package com.srisu.srisu.features.chat.couple.loverequest.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.CommonTabPager
import com.srisu.srisu.features.chat.couple.loverequest.state.LoveRequestListState
import com.srisu.srisu.features.chat.couple.loverequest.vm.LoveRequestViewModel
import com.srisu.srisu.features.home.connection.screen.ConnectionToolBar
import org.koin.compose.viewmodel.koinViewModel

@Composable
private fun LoveRequestListScreen(
    loveRequestViewModel: LoveRequestViewModel = koinViewModel(),
) {
    val loveRequestUiState by loveRequestViewModel.loveRequestListState.collectAsStateWithLifecycle()

    LoveRequestContent(
        loveRequestViewModel = loveRequestViewModel,
        loveRequestUiState = loveRequestUiState,
    )
}

@Composable
private fun LoveRequestContent(
    loveRequestViewModel: LoveRequestViewModel,
    loveRequestUiState: LoveRequestListState
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
                            onNavigateToProfile = {

                            },
                            onAcceptLoveRequest = { id, senderNumber, receiverNumber ->

                            },
                            onRejectLoveRequest = { id, senderNumber, receiverNumber ->

                            },
                            loveRequestList = loveRequestViewModel.loveRequests
                        )
                    }

                    1 -> {
                        LoveRequestSentListScreen(
                            onNavigateToProfile = {

                            },
                            onCancelCrushRequest = { id, senderNumber, receiverNumber ->

                            },
                            sentLoveRequestList = loveRequestViewModel.sentLoveRequests
                        )
                    }
                }
            }

        }
    }
}
