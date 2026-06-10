package com.srisu.srisu.features.home.connection.presentation.singleconnection.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.itemContentType
import com.srisu.srisu.features.home.connection.data.remote.response.SingleConnectionResponse
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItem
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionShimmerCompo
import com.srisu.srisu.features.home.connection.presentation.components.PagedConnectionContent
import org.jetbrains.compose.ui.tooling.preview.Preview

typealias CrushRequestId = Int?
typealias SenderNumber = String?
typealias ReceiverNumber = String?

@Composable
fun MyCrushScreen(
    crushList: LazyPagingItems<SingleConnectionResponse.Result>,
    onNavigateToProfile: (SingleConnectionResponse.Result.Receiver?) -> Unit,
    onCancelCrushRequest: (CrushRequestId, SenderNumber, ReceiverNumber) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        PagedConnectionContent(
            items = crushList,
            emptyTitle = "maybe it’s time to find one?",
            loadingContent = { ConnectionShimmerCompo() },
            listContent = {
                MyCrushListContent(
                    myCrushList = crushList,
                    onNavigateToProfile = onNavigateToProfile,
                    onCancelCrushRequest = onCancelCrushRequest
                )
            }
        )
    }
}

@Composable
private fun MyCrushListContent(
    myCrushList: LazyPagingItems<SingleConnectionResponse.Result>,
    onNavigateToProfile: (SingleConnectionResponse.Result.Receiver?) -> Unit,
    onCancelCrushRequest: (CrushRequestId, SenderNumber, ReceiverNumber) -> Unit,
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
            count = myCrushList.itemCount,
            key = { index -> myCrushList[index]?.id ?: index },
            contentType = { "my_crush_item" }
        ) { index ->
            val crush = myCrushList[index] ?: return@items

            ConnectionItem(
                modifier = Modifier,
                userName = crush.receiver?.username.orEmpty(),
                userImage = crush.receiver?.profilePhoto.orEmpty(),
                dob = crush.receiver?.dob,
                zodiacSign = crush.receiver?.zodiacSign,
                onClick = { onNavigateToProfile(crush.receiver) },
                firstButtonTitle = "Cancel",
                onClickFirstButton = {
                    onCancelCrushRequest(
                        crush.id,
                        crush.senderNumber,
                        crush.receiverNumber
                    )
                }
            )
        }
    }
}

@Composable
private fun MyCrushListCompo(
    modifier: Modifier = Modifier,
    myCrushList: LazyPagingItems<SingleConnectionResponse.Result>,
    onNavigateToProfile: (userProfileData: SingleConnectionResponse.Result.Receiver?) -> Unit,
    onCancelCrushRequest: (CrushRequestId, SenderNumber, ReceiverNumber) -> Unit
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
            count = myCrushList.itemCount,
            key = { index -> myCrushList[index]?.id ?: index },
            contentType = myCrushList.itemContentType { "myCrush" }
        ) { index ->
            val crush = myCrushList[index]
            var animate by remember { mutableStateOf(false) }
            LaunchedEffect(crush?.id) {
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

                crush?.let {
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

        //  Append load / error states for infinite scroll
        /* myCrushList.apply {
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
         }*/
    }
}


@Composable
@Preview
fun PreviewMyCrushScreen() {
    /*    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            MyCrushScreen(
                onNavigateToProfile = {},
                onCancelCrushRequest = { _, _, _ ->

                },
                crushList = null
            )
        }*/
}