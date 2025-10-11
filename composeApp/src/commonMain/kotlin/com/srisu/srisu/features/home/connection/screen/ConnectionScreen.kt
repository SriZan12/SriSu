package com.srisu.srisu.features.home.connection.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.srisu.srisu.components.TransparentToolBar
import com.srisu.srisu.components.shimmerEffect
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import com.srisu.srisu.features.home.connection.vm.ConnectionViewModel
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.no_love

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel = koinViewModel<ConnectionViewModel>()) {

    val connectionUiState: ConnectionUIState by viewModel.connectionUiState.collectAsStateWithLifecycle()

    ConnectionScreenContent(
        viewModel = viewModel,
        connectionUiState =
            connectionUiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreenContent(
    viewModel: ConnectionViewModel,
    connectionUiState: ConnectionUIState
) {
    val tabItems = connectionUiState.connectionTabList
    val pagerState = rememberPagerState { tabItems.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TransparentToolBar(
                title = connectionUiState.currentTab?.title ?: "Connection"
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->


        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .collect { page ->
                        viewModel.updateCurrentTab(tab = tabItems[page])
                    }
            }

            TabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                tabItems.forEachIndexed { index, item ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }

                            viewModel.updateCurrentTab(
                                tab = item
                            )
                        },
                        text = {

                            val isSelected = pagerState.currentPage == index
                            val style = if (isSelected) {
                                MaterialTheme.typography.titleMedium.copy(
                                    MaterialTheme.colorScheme.primary

                                )
                            } else {
                                MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Text(
                                text = item.title,
                                style = style

                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
                    .background(color = MaterialTheme.colorScheme.surfaceContainerLowest)
            ) { index ->
                if (index == 0) {

                    MyCrushScreen(
                        crushList = viewModel.myCrushList,
                        onNavigateToProfile = {},
                        onCancelCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
                            viewModel.cancelCrushRequest(
                                crushRequestId = crushRequestId,
                                senderNumber = senderNumber,
                                receiverNumber = receiverNumber
                            )
                        }
                    )
                } else {
                    CrushOnMeScreen()
                }
            }
        }
    }
}

@Composable
fun ConnectionItem(
    modifier: Modifier,
    userName: String,
    userImage: String?,
    dob: String?,
    zodiacSign: String?,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(
            indication = null,
            interactionSource = MutableInteractionSource(),
            onClick = {
                onClick()
            }),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

            Text(
                text = userName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val age = DateTimeUtils.calculateAge(dateString = dob).toString()

                Text(
                    text = age,
                    style = MaterialTheme.typography.titleSmall
                )

                Text(" | ", style = MaterialTheme.typography.titleSmall)

                val zodiacSignImage = ZodiacUtils.getZodiacSignImage(zodiacSign?.trim() ?: "")

                zodiacSignImage?.let {
                    Image(
                        painter = painterResource(resource = zodiacSignImage),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }

            }

            Spacer(modifier = Modifier.height(4.dp))

            CancelButtonCompo(
                label = "Cancel",
                onClick = {
                    onCancel()
                }

            )

        }

        Spacer(modifier = Modifier.width(12.dp))

        AsyncImage(
            model = userImage,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp).clip(shape = RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop

        )
    }


}

@Composable
fun ShimmerEffect(
    modifier: Modifier,
    widthOfShadowBrush: Int = 500,
    angleOfAxisY: Float = 270f,
    durationMillis: Int = 1000,
) {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.3f),
        Color.White.copy(alpha = 0.5f),
        Color.White.copy(alpha = 1.0f),
        Color.White.copy(alpha = 0.5f),
        Color.White.copy(alpha = 0.3f),
    )
    val transition = rememberInfiniteTransition(label = "")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = (durationMillis + widthOfShadowBrush).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "Shimmer loading animation",
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation.value - widthOfShadowBrush, y = 0.0f),
        end = Offset(x = translateAnimation.value, y = angleOfAxisY),
    )
    Row(modifier = Modifier.fillMaxWidth()) {


        Box(
            modifier = modifier
        ) {
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .background(brush)
            )
        }
    }

}


@Composable
private fun CancelButtonCompo(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.wrapContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        onClick = onClick
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
fun NoConnectionsFound(
    modifier: Modifier = Modifier,
    title: String
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Res.drawable.no_love),
                contentDescription = null,
                modifier = Modifier.size(100.dp)

            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun ConnectionShimmerCompo() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(7) {
            ConnectionItemShimmer()
        }
    }
}

@Composable
fun ConnectionItemShimmer() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmerEffect()
            )

            // Age and zodiac row shimmer
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Age shimmer
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )

                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                // Zodiac icon shimmer
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Cancel button shimmer
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .shimmerEffect()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Profile image shimmer
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .shimmerEffect()
        )
    }
}


@Preview
@Composable
fun PreviewShimmerEffect() {

    ConnectionItemShimmer()

}

@Preview
@Composable
fun PreviewConnectionItem() {
    ConnectionItem(
        modifier = Modifier,
        userName = "Srisu",
        userImage = "https://photosmint.com/wp-content/uploads/2025/03/Indian-Beauty-DP.jpeg",
        dob = "23",
        zodiacSign = "Leo",
        onClick = {},
        onCancel = {}
    )
}