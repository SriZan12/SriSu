package com.srisu.srisu.features.home.connection.screen

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.srisu.srisu.components.shimmerEffect
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import com.srisu.srisu.features.home.connection.vm.ConnectionViewModel
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import com.srisu.srisu.utils.Constants.ConnectionStatus.NOTHING
import com.srisu.srisu.utils.Constants.ConnectionStatus.REJECTED
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.filter_icon
import srisu.composeapp.generated.resources.no_love


@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = koinViewModel<ConnectionViewModel>(),
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {

    val connectionUiState: ConnectionUIState by viewModel.connectionUiState.collectAsStateWithLifecycle()

    Initialization(connectionViewModel = viewModel)

    ConnectionScreenContent(
        viewModel = viewModel,
        connectionUiState =
            connectionUiState,
        onNavigateToProfile = onNavigateToProfile
    )
}

@Composable
private fun Initialization(
    connectionViewModel: ConnectionViewModel
) {
//    LaunchedEffect(Unit){
//        connectionViewModel.getMyCrushList()
//        connectionViewModel.getCrushOnMeList()
//    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreenContent(
    viewModel: ConnectionViewModel,
    connectionUiState: ConnectionUIState,
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {
    val tabItems = connectionUiState.connectionTabList
    val pagerState = rememberPagerState { tabItems.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 64.dp),
        topBar = {
            ConnectionToolBar(
                title = connectionUiState.currentTab?.title ?: "Connection"
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) { innerPadding ->


        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .background(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .collect { page ->
                        viewModel.updateCurrentTab(tab = tabItems[page])
                    }
            }

            TabRow(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
            ) { index ->
                if (index == 0) {

                    MyCrushScreen(
                        crushList = viewModel.myCrushList,
                        onNavigateToProfile = { userProfileData ->
                            val user = viewModel.getUserProfile(userProfile = userProfileData)
                            onNavigateToProfile(user)
                        },
                        onCancelCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
                            viewModel.updateCrushRequest(
                                crushRequestId = crushRequestId,
                                senderNumber = senderNumber,
                                receiverNumber = receiverNumber,
                                connectionStatus = NOTHING
                            )
                        }
                    )
                } else {
                    CrushOnMeScreen(
                        crushOnMeList = viewModel.crushOnMeList,
                        onAcceptCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
                            viewModel.updateCrushRequest(
                                crushRequestId = crushRequestId,
                                senderNumber = senderNumber,
                                receiverNumber = receiverNumber,
                                connectionStatus = ACCEPTED
                            )
                        },
                        onRejectCrushRequest = { crushRequestId, senderNumber, receiverNumber ->
                            viewModel.updateCrushRequest(
                                crushRequestId = crushRequestId,
                                senderNumber = senderNumber,
                                receiverNumber = receiverNumber,
                                connectionStatus = REJECTED
                            )
                        },
                        onNavigateToProfile = { userProfileData ->
                            val user = viewModel.getUserProfile(userProfile = userProfileData)
                            onNavigateToProfile(user)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ConnectionToolBar(
    title: String,
    onClickAction: () -> Unit = {}
) {

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

        },
        actions = {
            IconButton(
                onClick = {
                    onClickAction()
                }
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.filter_icon),
                    contentDescription = "Filter Icon",
                )
            }
        }
    )

}

@Composable
fun ConnectionItem(
    modifier: Modifier,
    userName: String,
    userImage: String?,
    dob: String?,
    zodiacSign: String?,
    firstButtonTitle: String,
    secondButtonTitle: String = "Reject",
    onClick: () -> Unit,
    showSecondButton: Boolean = false,
    onClickFirstButton: () -> Unit,
    onClickSecondButton: () -> Unit = {}
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

            Row(modifier = Modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectionButtonCompo(
                    label = firstButtonTitle,
                    onClick = {
                        onClickFirstButton()
                    }

                )

                if (showSecondButton) {

                    ConnectionButtonCompo(
                        label = secondButtonTitle,
                        onClick = {
                            onClickSecondButton()
                        }

                    )
                }

            }


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
private fun ConnectionButtonCompo(label: String, onClick: () -> Unit) {
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
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun ConnectionShimmerCompo(
    showSecondButton: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(7) {
            ConnectionItemShimmer(
                showSecondButton = showSecondButton
            )
        }
    }
}

@Composable
fun ConnectionItemShimmer(
    showSecondButton: Boolean = false
) {
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
            Row(modifier = Modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect()
                )

                if (showSecondButton) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .shimmerEffect()
                    )
                }
            }

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
        firstButtonTitle = "Connect",
        secondButtonTitle = "Cancel",
        onClick = {},
        onClickFirstButton = {}
    )
}