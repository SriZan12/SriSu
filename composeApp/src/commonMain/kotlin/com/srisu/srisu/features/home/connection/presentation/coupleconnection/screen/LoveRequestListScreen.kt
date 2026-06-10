package com.srisu.srisu.features.home.connection.presentation.coupleconnection.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.LazyPagingItems
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItem
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionShimmerCompo
import com.srisu.srisu.features.home.connection.presentation.components.PagedConnectionContent

typealias senderNumber = String?
typealias receiverNumber = String?
typealias loveRequestId = Int?

@Composable
fun LoveRequestListScreen(
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    onRejectLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    loveRequestList: LazyPagingItems<CoupleConnectionRequestResponse.Result>
) {

    Box(
        modifier = Modifier.fillMaxSize().background(
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        PagedConnectionContent(
            items = loveRequestList,
            emptyTitle = "maybe it’s time to find one?",
            loadingContent = {
                ConnectionShimmerCompo()
            },
            listContent = {
                LoveRequestListContent(
                    modifier = Modifier,
                    onNavigateToProfile = onNavigateToProfile,
                    onAcceptLoveRequest = onAcceptLoveRequest,
                    onRejectLoveRequest = onRejectLoveRequest,
                    loveRequestList = loveRequestList
                )
            }
        )
    }


}

@Composable
private fun LoveRequestListContent(
    modifier: Modifier,
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId: Int?, senderNumber, receiverNumber) -> Unit,
    onRejectLoveRequest: (loveRequestId: Int?, senderNumber, receiverNumber) -> Unit,
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

