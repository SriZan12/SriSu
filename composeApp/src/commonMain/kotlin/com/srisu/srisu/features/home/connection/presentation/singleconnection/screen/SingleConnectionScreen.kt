package com.srisu.srisu.features.home.connection.presentation.singleconnection.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.CommonTabPager
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItem
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionItemShimmer
import com.srisu.srisu.features.home.connection.presentation.components.ConnectionToolBar
import com.srisu.srisu.features.home.connection.presentation.singleconnection.state.ConnectionUIState
import com.srisu.srisu.features.home.connection.presentation.singleconnection.vm.SingleConnectionViewModel
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import com.srisu.srisu.utils.Constants.ConnectionStatus.NOTHING
import com.srisu.srisu.utils.Constants.ConnectionStatus.REJECTED
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun SingleConnectionScreen(
    singleConnectionViewModel: SingleConnectionViewModel,
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {

    val connectionUiState: ConnectionUIState by singleConnectionViewModel.connectionUiState.collectAsStateWithLifecycle()

    Initialization(
        viewModel = singleConnectionViewModel
    )

    ConnectionScreenContent(
        viewModel = singleConnectionViewModel,
        connectionUiState =
            connectionUiState,
        onNavigateToProfile = onNavigateToProfile
    )
}

@Composable
private fun Initialization(
    viewModel: SingleConnectionViewModel
) {
    LaunchedEffect(Unit){
        viewModel.getMyCrushList()
        viewModel.getCrushOnMeList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreenContent(
    viewModel: SingleConnectionViewModel,
    connectionUiState: ConnectionUIState,
    onNavigateToProfile: (userProfileData: String?) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
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

            val tabItems = connectionUiState.connectionTabList
            val pagerState = rememberPagerState { tabItems.size }

            CommonTabPager(
                tabItems = tabItems,
                pagerState = pagerState,
                onUpdateCurrentTab = {
                    viewModel.updateCurrentTab(it)
                }
            ) { index ->

                when (index) {
                    0 -> {
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
                    }

                    1 -> {
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

            Spacer(modifier = Modifier.height(64.dp))
        }
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