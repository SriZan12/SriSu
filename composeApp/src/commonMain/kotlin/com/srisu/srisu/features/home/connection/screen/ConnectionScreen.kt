package com.srisu.srisu.features.home.connection.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cash.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.srisu.srisu.components.TransparentToolBar
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import com.srisu.srisu.features.home.connection.vm.ConnectionViewModel
import com.srisu.srisu.utils.DateTimeUtils
import com.srisu.srisu.utils.ZodiacUtils
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.leo

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
                        connectionUiState = connectionUiState
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
    userName: String,
    userImage: String?,
    dob: String?,
    zodiacSign: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                onClick = {}

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
private fun CancelButtonCompo(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.wrapContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),

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

/*
@Composable
@Preview
fun PreviewConnectionScreen() {
    ConnectionScreenContent(
        viewModel = ConnectionViewModel(),
        ConnectionUIState(
            connectionTabList = listOf(
                ConnectionUIState.Tab("My Crush"),
                ConnectionUIState.Tab("Crush on me"),
            )
        )
    )
}*/

@Preview
@Composable
fun PreviewConnectionItem() {
    ConnectionItem(
        userName = "Srisu",
        userImage = "https://photosmint.com/wp-content/uploads/2025/03/Indian-Beauty-DP.jpeg",
        dob = "23",
        zodiacSign = "Leo"

    )
}