package com.srisu.srisu.features.home.connection.coupleconnection.loverequest.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import app.cash.paging.PagingData
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import com.srisu.srisu.core.data.response.connection.LoveRequestResponse
import com.srisu.srisu.features.home.connection.singleconnection.screen.ConnectionItem
import com.srisu.srisu.features.home.connection.singleconnection.screen.ConnectionShimmerCompo
import com.srisu.srisu.features.home.connection.singleconnection.screen.NoConnectionsFound
import kotlinx.coroutines.flow.StateFlow


@Composable
fun LoveRequestSentListScreen(
    onNavigateToProfile: (userProfileData: LoveRequestResponse.Result.Receiver?) -> Unit,
    onCancelLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    sentLoveRequestList: StateFlow<PagingData<LoveRequestResponse.Result>>
) {
    LoveRequestSentListContent(
        onNavigateToProfile = onNavigateToProfile,
        onCancelLoveRequest = onCancelLoveRequest,
        sentLoveRequestList = sentLoveRequestList
    )
}

@Composable
private fun LoveRequestSentListContent(
    onNavigateToProfile: (userProfileData: LoveRequestResponse.Result.Receiver?) -> Unit,
    onCancelLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    sentLoveRequestList: StateFlow<PagingData<LoveRequestResponse.Result>>,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {

        val sentLoveRequestPagingItems = sentLoveRequestList.collectAsLazyPagingItems()
        val loadState = sentLoveRequestPagingItems.loadState

        when {
            loadState.refresh is LoadState.Loading -> {
                ConnectionShimmerCompo()
            }

            sentLoveRequestPagingItems.itemCount == 0 -> {
                var isVisible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    isVisible = true
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(600, easing = FastOutSlowInEasing)
                    ),
                ) {
                    NoConnectionsFound(
                        modifier = Modifier.fillMaxSize(),
                        title = "maybe it’s time to find one?"
                    )
                }
            }

            else -> {
                LoveRequestSentListCompo(
                    modifier = Modifier,
                    sentLoveRequestList = sentLoveRequestPagingItems,
                    onNavigateToProfile = onNavigateToProfile,
                    onCancelCrushRequest = onCancelLoveRequest

                )
            }
        }
    }

}

@Composable
private fun LoveRequestSentListCompo(
    modifier: Modifier = Modifier,
    sentLoveRequestList: LazyPagingItems<LoveRequestResponse.Result>,
    onNavigateToProfile: (userProfileData: LoveRequestResponse.Result.Receiver?) -> Unit,
    onCancelCrushRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit
) {

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)), // smooth expand/shrink animation
        contentPadding = PaddingValues(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            count = sentLoveRequestList.itemCount,
            key = { index -> sentLoveRequestList[index]?.id ?: index },
            contentType = sentLoveRequestList.itemContentType { "myCrush" }
        ) { index ->
            val sentRequest = sentLoveRequestList[index]
            var animate by remember { mutableStateOf(false) }
            LaunchedEffect(sentRequest?.id) {
                animate = true
            }
            AnimatedVisibility(
                visible = animate,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(400)
                ),
                exit = fadeOut(animationSpec = tween(300))
            ) {

                sentRequest?.let {
                    ConnectionItem(
                        modifier = Modifier,
                        userName = it.receiver?.username ?: "",
                        userImage = it.receiver?.profilePhoto ?: "",
                        dob = it.receiver?.dob,
                        zodiacSign = it.receiver?.zodiacSign,
                        onClick = {
                            onNavigateToProfile(
                                it.receiver
                            )
                        },
                        firstButtonTitle = "Cancel",
                        onClickFirstButton = {
                            val senderNumber = it.senderNumber
                            val receiverNumber = it.receiverNumber
                            onCancelCrushRequest(
                                it.id,
                                senderNumber,
                                receiverNumber
                            )
                        }
                    )
                }
            }
        }

    }
}
