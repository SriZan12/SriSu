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


@Composable
fun LoveRequestSentListScreen(
    onNavigateToProfile: (CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onCancelLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    sentLoveRequestList: LazyPagingItems<CoupleConnectionRequestResponse.Result>
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        PagedConnectionContent(
            items = sentLoveRequestList,
            emptyTitle = "maybe it’s time to find one?",
            loadingContent = {
                ConnectionShimmerCompo()
            },
            listContent = {
                LoveRequestSentListContent(
                    onNavigateToProfile = onNavigateToProfile,
                    onCancelLoveRequest = onCancelLoveRequest,
                    sentLoveRequestList = sentLoveRequestList
                )
            }
        )
    }

}

@Composable
private fun LoveRequestSentListContent(
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onCancelLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    sentLoveRequestList: LazyPagingItems<CoupleConnectionRequestResponse.Result>
) {

    val list = rememberLazyListState()

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxWidth(),
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
            count = sentLoveRequestList.itemCount,
            key = { index -> sentLoveRequestList[index]?.id ?: index },
            contentType = { "love_request_item" },
        ) { index ->

            val sentLoveRequest = sentLoveRequestList[index] ?: return@items

            ConnectionItem(
                modifier = Modifier,
                userName = sentLoveRequest.receiver?.username ?: "",
                userImage = sentLoveRequest.receiver?.profilePhoto ?: "",
                dob = sentLoveRequest.receiver?.dob,
                zodiacSign = sentLoveRequest.receiver?.zodiacSign,
                onClick = {
                    onNavigateToProfile(
                        sentLoveRequest.receiver
                    )
                },
                firstButtonTitle = "Cancel",
                onClickFirstButton = {
                    val senderNumber = sentLoveRequest.senderNumber
                    val receiverNumber = sentLoveRequest.receiverNumber
                    onCancelLoveRequest(
                        sentLoveRequest.id,
                        senderNumber,
                        receiverNumber
                    )
                }
            )
        }
    }


}

