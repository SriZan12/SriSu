package com.srisu.srisu.features.home.connection.presentation.singleconnection.screen

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
import com.srisu.srisu.features.home.connection.data.remote.response.SingleConnectionResponse
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItem
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionShimmerCompo
import com.srisu.srisu.features.home.connection.presentation.components.PagedConnectionContent

@Composable
fun CrushOnMeScreen(
    crushOnMeList: LazyPagingItems<SingleConnectionResponse.Result>,
    onNavigateToProfile: (SingleConnectionResponse.Result.Receiver?) -> Unit,
    onAcceptCrushRequest: (CrushRequestId, SenderNumber, ReceiverNumber) -> Unit,
    onRejectCrushRequest: (CrushRequestId, SenderNumber, ReceiverNumber) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        PagedConnectionContent(
            items = crushOnMeList,
            emptyTitle = "Maybe someone’s secretly admiring you 😌",
            loadingContent = {
                ConnectionShimmerCompo(showSecondButton = false)
            },
            listContent = {
                CrushOnMeListContent(
                    crushOnMeList = crushOnMeList,
                    onNavigateToProfile = onNavigateToProfile,
                    onAcceptCrushRequest = onAcceptCrushRequest,
                    onRejectCrushRequest = onRejectCrushRequest
                )
            }
        )
    }
}


@Composable
private fun CrushOnMeListContent(
    crushOnMeList: LazyPagingItems<SingleConnectionResponse.Result>,
    onNavigateToProfile: (SingleConnectionResponse.Result.Receiver?) -> Unit,
    onAcceptCrushRequest: (CrushRequestId, SenderNumber, ReceiverNumber) -> Unit,
    onRejectCrushRequest: (CrushRequestId, SenderNumber, ReceiverNumber) -> Unit,
    modifier: Modifier = Modifier
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
            count = crushOnMeList.itemCount,
            key = { index -> crushOnMeList[index]?.id ?: index },
            contentType = { "crush_on_me_item" }
        ) { index ->
            val crush = crushOnMeList[index] ?: return@items

            ConnectionItem(
                modifier = Modifier,
                userName = crush.receiver?.username.orEmpty(),
                userImage = crush.receiver?.profilePhoto.orEmpty(),
                dob = crush.receiver?.dob,
                zodiacSign = crush.receiver?.zodiacSign,
                onClick = { onNavigateToProfile(crush.receiver) },
                showSecondButton = true,
                firstButtonTitle = "Accept",
                secondButtonTitle = "Reject",
                onClickFirstButton = {
                    onAcceptCrushRequest(
                        crush.id,
                        crush.senderNumber,
                        crush.receiverNumber
                    )
                },
                onClickSecondButton = {
                    onRejectCrushRequest(
                        crush.id,
                        crush.senderNumber,
                        crush.receiverNumber
                    )
                }
            )
        }
    }
}

