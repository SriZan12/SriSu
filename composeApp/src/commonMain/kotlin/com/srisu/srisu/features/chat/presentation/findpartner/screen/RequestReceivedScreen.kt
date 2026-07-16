package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.screen.receiverNumber
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.screen.senderNumber
import com.srisu.srisu.utils.Constants
import kotlin.time.ExperimentalTime

@Composable
fun ReceivedLoveRequestScreen(
    findPartnerViewModel: FindPartnerViewModel,
    onNavigateToProfile: (String?) -> Unit,
    onNavigateBack: () -> Unit,
    onNavToChatScreen: () -> Unit
) {
    val loveRequests = findPartnerViewModel.loveRequests.collectAsLazyPagingItems()

    Initialization(findPartnerViewModel = findPartnerViewModel)

    ReceivedLoveRequestsScreenContent(
        loveRequests = loveRequests,
        onNavigateBack = onNavigateBack,
        onNavigateToProfile = { user ->
            val userProfile = findPartnerViewModel.getUserProfile(userProfile = user)
            onNavigateToProfile(userProfile)
        },
        onAcceptLoveRequest = { loveRequestId, senderNumber, receiverNumber ->
            findPartnerViewModel.updateLoveRequest(
                loveRequestId = loveRequestId,
                senderNumber = senderNumber,
                receiverNumber = receiverNumber,
                connectionStatus = Constants.ConnectionStatus.ACCEPTED
            ) {
                onNavToChatScreen()
            }
        },
        onRejectLoveRequest = { loveRequestId, senderNumber, receiverNumber ->
            findPartnerViewModel.updateLoveRequest(
                loveRequestId = loveRequestId,
                senderNumber = senderNumber,
                receiverNumber = receiverNumber,
                connectionStatus = Constants.ConnectionStatus.REJECTED
            ) {

            }
        }
    )
}

@Composable
private fun Initialization(
    findPartnerViewModel: FindPartnerViewModel
) {
    LaunchedEffect(Unit) {
        findPartnerViewModel.refreshLoveRequests()
    }
}

@Composable
private fun ReceivedLoveRequestsScreenContent(
    loveRequests: LazyPagingItems<CoupleConnectionRequestResponse.Result>?,
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId: Long?, senderNumber, receiverNumber) -> Unit,
    onRejectLoveRequest: (loveRequestId: Long?, senderNumber, receiverNumber) -> Unit,
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
            onNavigateToProfile = onNavigateToProfile,
            onAcceptLoveRequest = onAcceptLoveRequest,
            onRejectLoveRequest = onRejectLoveRequest
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ReceivedLoveRequestsList(
    modifier: Modifier = Modifier,
    loveRequests: LazyPagingItems<CoupleConnectionRequestResponse.Result>?,
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId: Long?, senderNumber, receiverNumber) -> Unit,
    onRejectLoveRequest: (loveRequestId: Long?, senderNumber, receiverNumber) -> Unit,
) {

    loveRequests?.let {
        when {
            loveRequests.loadState.refresh is LoadState.Loading -> {
                ConnectionShimmerCompo()
            }

            loveRequests.itemCount == 0 -> {
                AnimatedEmptyState(title = "No Requests received yet")
            }

            else -> {
                LoveRequestListContent(
                    modifier = modifier,
                    onNavigateToProfile = onNavigateToProfile,
                    onAcceptLoveRequest = onAcceptLoveRequest,
                    onRejectLoveRequest = onRejectLoveRequest,
                    loveRequestList = loveRequests
                )
            }
        }
    }


}

@Composable
private fun LoveRequestListContent(
    modifier: Modifier,
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId: Long?, senderNumber, receiverNumber) -> Unit,
    onRejectLoveRequest: (loveRequestId: Long?, senderNumber, receiverNumber) -> Unit,
    loveRequestList: LazyPagingItems<CoupleConnectionRequestResponse.Result>
) {

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
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
            count = loveRequestList.itemCount,
            key = { index -> loveRequestList[index]?.id ?: index },
            contentType = { "love_request_item" },
        ) { index ->
            val loveRequest = loveRequestList[index] ?: return@items

            ConnectionItem(
                modifier = Modifier,
                userName = loveRequest.receiver?.username.orEmpty(),
                userImage = loveRequest.receiver?.profilePhoto.orEmpty(),
                dob = loveRequest.receiver?.dob,
                zodiacSign = loveRequest.receiver?.zodiacSign,
                onClick = { onNavigateToProfile(loveRequest.receiver) },
                showSecondButton = true,
                firstButtonTitle = "Accept",
                secondButtonTitle = "Reject",
                onClickFirstButton = {
                    onAcceptLoveRequest(
                        loveRequest.id,
                        loveRequest.senderNumber,
                        loveRequest.receiverNumber
                    )
                },
                onClickSecondButton = {
                    onRejectLoveRequest(
                        loveRequest.id,
                        loveRequest.senderNumber,
                        loveRequest.receiverNumber
                    )
                }
            )
        }
    }
}