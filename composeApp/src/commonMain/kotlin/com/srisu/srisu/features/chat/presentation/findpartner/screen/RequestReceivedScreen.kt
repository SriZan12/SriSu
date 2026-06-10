package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import com.srisu.srisu.components.PrimaryToolBar
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.presentation.components.AnimatedEmptyState
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItem
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionShimmerCompo
import com.srisu.srisu.utils.Constants
import kotlin.time.ExperimentalTime

@Composable
fun ReceivedLoveRequestScreen(
    findPartnerViewModel: FindPartnerViewModel,
    onNavigateToProfile: (String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val receivedRequests = findPartnerViewModel.loveRequests.collectAsLazyPagingItems()

    ReceivedLoveRequestsScreenContent(
        loveRequests = receivedRequests,
        onNavigateBack = onNavigateBack,
        onProfileClick = { user ->
            val userProfile = findPartnerViewModel.getUserProfile(userProfile = user)
            onNavigateToProfile(userProfile)
        },
        onAcceptClick = { request ->
            findPartnerViewModel.updateLoveRequest(
                loveRequestId = request.id,
                senderNumber = request.senderNumber,
                receiverNumber = request.receiverNumber,
                connectionStatus = Constants.ConnectionStatus.ACCEPTED
            )
        },
        onRejectClick = { request ->
            findPartnerViewModel.updateLoveRequest(
                loveRequestId = request.id,
                senderNumber = request.senderNumber,
                receiverNumber = request.receiverNumber,
                connectionStatus = Constants.ConnectionStatus.REJECTED
            )
        }
    )
}

@Composable
private fun ReceivedLoveRequestsScreenContent(
    loveRequests: LazyPagingItems<CoupleConnectionRequestResponse.Result>,
    onProfileClick: (CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptClick: (CoupleConnectionRequestResponse.Result) -> Unit,
    onRejectClick: (CoupleConnectionRequestResponse.Result) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        topBar = {
            PrimaryToolBar(
                title = "Love Requests",
                onNavigate = onNavigateBack
            )
        }
    ) { innerPadding ->
        ReceivedLoveRequestsList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            loveRequests = loveRequests,
            onProfileClick = onProfileClick,
            onAcceptClick = onAcceptClick,
            onRejectClick = onRejectClick
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ReceivedLoveRequestsList(
    modifier: Modifier = Modifier,
    loveRequests: LazyPagingItems<CoupleConnectionRequestResponse.Result>,
    onProfileClick: (CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptClick: (CoupleConnectionRequestResponse.Result) -> Unit,
    onRejectClick: (CoupleConnectionRequestResponse.Result) -> Unit
) {
    when {
        loveRequests.loadState.refresh is LoadState.Loading -> {
            ConnectionShimmerCompo()
        }

        loveRequests.loadState.refresh is LoadState.Error -> {
            AnimatedEmptyState(title = "Something went wrong")
        }

        loveRequests.itemCount == 0 -> {
            AnimatedEmptyState(title = "No requests received yet")
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 80.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = loveRequests.itemCount,
                    key = { index -> loveRequests[index]?.id ?: index },
                    contentType = { "received_love_request" }
                ) { index ->
                    val request = loveRequests[index] ?: return@items
                    val sender = request.receiver

                    ConnectionItem(
                        modifier = Modifier,
                        userName = sender?.username.orEmpty(),
                        userImage = sender?.profilePhoto.orEmpty(),
                        dob = sender?.dob,
                        zodiacSign = sender?.zodiacSign,
                        onClick = { onProfileClick(sender) },
                        showSecondButton = true,
                        firstButtonTitle = "Accept",
                        secondButtonTitle = "Reject",
                        onClickFirstButton = { onAcceptClick(request) },
                        onClickSecondButton = { onRejectClick(request) },
                    )
                }
            }
        }
    }
}