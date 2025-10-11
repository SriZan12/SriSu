package com.srisu.srisu.features.home.connection.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
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
import com.srisu.srisu.core.data.response.connection.MyCrushListResponse
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.jetbrains.compose.ui.tooling.preview.Preview

typealias crushRequestId = Int?
typealias senderNumber = String?
typealias receiverNumber = String?

@Composable
fun MyCrushScreen(
    onNavigateToProfile: (userProfileData: MyCrushListResponse.Result.Receiver?) -> Unit,
    onCancelCrushRequest: (crushRequestId, senderNumber, receiverNumber) -> Unit,
    crushList: StateFlow<PagingData<MyCrushListResponse.Result>>
) {
    MyCrushScreenContent(
        crushList = crushList,
        onNavigateToProfile = onNavigateToProfile,
        onCancelCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
            onCancelCrushRequest(crushRequestId, senderNumber, receiverNumber)
        }
    )
}

@Composable
fun MyCrushScreenContent(
    modifier: Modifier = Modifier,
    crushList: StateFlow<PagingData<MyCrushListResponse.Result>>,
    onNavigateToProfile: (userProfileData: MyCrushListResponse.Result.Receiver?) -> Unit,
    onCancelCrushRequest: (crushRequestId, senderNumber, receiverNumber) -> Unit
) {
    val myCrushList = crushList.collectAsLazyPagingItems()
    val loadState = myCrushList.loadState

    when {
        loadState.refresh is LoadState.Loading -> {
            ConnectionShimmerCompo()
        }

        myCrushList.itemCount == 0 -> {
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
                    title = "You don't have any crush"
                )
            }
        }

        else -> {
            MyCrushListCompo(
                modifier = modifier,
                myCrushList = myCrushList,
                onNavigateToProfile = onNavigateToProfile,
                onCancelCrushRequest = onCancelCrushRequest

            )
        }
    }
}

@Composable
private fun MyCrushListCompo(
    modifier: Modifier = Modifier,
    myCrushList: LazyPagingItems<MyCrushListResponse.Result>,
    onNavigateToProfile: (userProfileData: MyCrushListResponse.Result.Receiver?) -> Unit,
    onCancelCrushRequest: (crushRequestId, senderNumber, receiverNumber) -> Unit
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
                        onCancel = {
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