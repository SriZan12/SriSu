package com.srisu.srisu.features.home.connection.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.srisu.srisu.core.data.response.connection.SingleConnectionResponse
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CrushOnMeScreen(
    onNavigateToProfile: (userProfileData: SingleConnectionResponse.Result.Receiver?) -> Unit,
    onAcceptCrushRequest: (crushRequestId, senderNumber, receiverNumber) -> Unit,
    onRejectCrushRequest: (crushRequestId, senderNumber, receiverNumber) -> Unit,
    crushOnMeList: StateFlow<PagingData<SingleConnectionResponse.Result>>,
) {
    CrushOnMeScreenContent(
        onNavigateToProfile = onNavigateToProfile,
        onAcceptCrushRequest = onAcceptCrushRequest,
        onRejectCrushRequest = onRejectCrushRequest,
        crushList = crushOnMeList
    )
}


@Composable
fun CrushOnMeScreenContent(
    onNavigateToProfile: (userProfileData: SingleConnectionResponse.Result.Receiver?) -> Unit,
    onAcceptCrushRequest: (crushRequestId, senderNumber, receiverNumber) -> Unit,
    onRejectCrushRequest: (crushRequestId, senderNumber, receiverNumber) -> Unit,
    crushList: StateFlow<PagingData<SingleConnectionResponse.Result>>
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val crushOnMeList = crushList.collectAsLazyPagingItems()
        val loadState = crushOnMeList.loadState

        when {
            loadState.refresh is LoadState.Loading -> {
                ConnectionShimmerCompo()
            }

            crushOnMeList.itemCount == 0 -> {
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
                        title = "Hmm… seems quiet for now. Maybe someone’s secretly admiring you \uD83D\uDE0C"
                    )
                }
            }

            else -> {
                CrushOnMeListCompo(
                    modifier = Modifier,
                    myCrushList = crushOnMeList,
                    onNavigateToProfile = onNavigateToProfile,
                    onAcceptCrushRequest = onAcceptCrushRequest,
                    onRejectCrushRequest = onRejectCrushRequest

                )
            }
        }
    }
}

@Composable
private fun CrushOnMeListCompo(
    modifier: Modifier = Modifier,
    myCrushList: LazyPagingItems<SingleConnectionResponse.Result>,
    onNavigateToProfile: (userProfileData: SingleConnectionResponse.Result.Receiver?) -> Unit,
    onAcceptCrushRequest: (crushRequestId: Int?, senderNumber: String?, receiverNumber: String?) -> Unit,
    onRejectCrushRequest: (crushRequestId: Int?, senderNumber: String?, receiverNumber: String?) -> Unit
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
            count = myCrushList.itemCount,
            key = { index -> myCrushList[index]?.id ?: index },
            contentType = myCrushList.itemContentType { "myCrush" }
        ) { index ->
            val crush = myCrushList[index]

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
                            onAcceptCrushRequest(it.id, it.senderNumber, it.receiverNumber)
                        },
                        onClickSecondButton = {
                            onRejectCrushRequest(it.id, it.senderNumber, it.receiverNumber)
                        }
                    )
                }
            }
        }

        // Optional: append loading/error UI for infinite scroll (uncomment if needed)
        /*
        myCrushList.apply {
            when {
                loadState.append is LoadState.Loading -> {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(28.dp)
                        )
                    }
                }

                loadState.append is LoadState.Error -> {
                    val e = loadState.append as LoadState.Error
                    item {
                        Text(
                            text = e.error.message ?: "Error loading more",
                            color = Color.Red,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
        */
    }
}

