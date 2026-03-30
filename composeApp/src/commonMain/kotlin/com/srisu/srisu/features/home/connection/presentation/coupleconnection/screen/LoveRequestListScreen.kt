package com.srisu.srisu.features.home.connection.presentation.coupleconnection.screen

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItem
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionShimmerCompo
import com.srisu.srisu.features.home.connection.presentation.components.NoConnectionsFound
import kotlinx.coroutines.flow.StateFlow

typealias senderNumber = String?
typealias receiverNumber = String?
typealias loveRequestId = Int?

@Composable
fun LoveRequestListScreen(
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    onRejectLoveRequest: (loveRequestId, senderNumber, receiverNumber) -> Unit,
    loveRequestList: StateFlow<PagingData<CoupleConnectionRequestResponse.Result>>
) {

    LoveRequestListContent(
        onNavigateToProfile = onNavigateToProfile,
        onAcceptLoveRequest = onAcceptLoveRequest,
        onRejectLoveRequest = onRejectLoveRequest,
        loveRequestList
    )
}

@Composable
private fun LoveRequestListContent(
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId: Int?, senderNumber, receiverNumber) -> Unit,
    onRejectLoveRequest: (loveRequestId: Int?, senderNumber, receiverNumber) -> Unit,
    loveRequestList: StateFlow<PagingData<CoupleConnectionRequestResponse.Result>>
) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        val loveRequestPagingItems = loveRequestList.collectAsLazyPagingItems()
        val loadState = loveRequestPagingItems.loadState

        when {
            loadState.refresh is LoadState.Loading -> {
                ConnectionShimmerCompo(
                    showSecondButton = false
                )
            }

            loveRequestPagingItems.itemCount == 0 -> {
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
                        title = " Maybe your perfect match is on the way \uD83D\uDC96"
                    )
                }
            }

            else -> {
                LoveRequestListCompo(
                    modifier = Modifier,
                    loveRequestList = loveRequestPagingItems,
                    onNavigateToProfile = onNavigateToProfile,
                    onAcceptLoveRequest = onAcceptLoveRequest,
                    onRejectLoveRequest = onRejectLoveRequest
                )
            }
        }

    }
}

@Composable
private fun LoveRequestListCompo(
    modifier: Modifier = Modifier,
    loveRequestList: LazyPagingItems<CoupleConnectionRequestResponse.Result>,
    onNavigateToProfile: (userProfileData: CoupleConnectionRequestResponse.Result.Receiver?) -> Unit,
    onAcceptLoveRequest: (loveRequestId: Int?, senderNumber: String?, receiverNumber: String?) -> Unit,
    onRejectLoveRequest: (loveRequestId: Int?, senderNumber: String?, receiverNumber: String?) -> Unit,
) {

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)), // Smooth expand/shrink animation
        contentPadding = PaddingValues(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            count = loveRequestList.itemCount,
            key = { index -> loveRequestList[index]?.id ?: index },
            contentType = loveRequestList.itemContentType { "myCrush" }
        ) { index ->
            val crush = loveRequestList[index]

            var isVisible by remember { mutableStateOf(false) }

            LaunchedEffect(crush?.id) {
                isVisible = true
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(400)) +
                        slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(400)
                        ),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                crush?.let {
                    ConnectionItem(
                        modifier = Modifier,
                        userName = it.receiver?.username.orEmpty(),
                        userImage = it.receiver?.profilePhoto.orEmpty(),
                        dob = it.receiver?.dob,
                        zodiacSign = it.receiver?.zodiacSign,
                        onClick = { onNavigateToProfile(it.receiver) },
                        showSecondButton = true,
                        firstButtonTitle = "Accept",
                        secondButtonTitle = "Reject",
                        onClickFirstButton = {
                            onAcceptLoveRequest(it.id, it.senderNumber, it.receiverNumber)
                        },
                        onClickSecondButton = {
                            onRejectLoveRequest(it.id, it.senderNumber, it.receiverNumber)
                        }
                    )
                }
            }
        }
    }
}
