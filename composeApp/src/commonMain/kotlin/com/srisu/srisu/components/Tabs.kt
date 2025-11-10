package com.srisu.srisu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

data class TabItem(
    val title: String
)

@Composable
fun CommonTabPager(
    tabItems: List<TabItem>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    onUpdateCurrentTab: (TabItem) -> Unit,
    pageContent: @Composable (pageIndex: Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {

        // Sync tab with pager state
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .collect { page ->
                    onUpdateCurrentTab(tabItems[page])
                }
        }

        // ----------- TAB ROW -----------
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

                        onUpdateCurrentTab(item)
                    },
                    text = {
                        val isSelected = pagerState.currentPage == index
                        val style = if (isSelected) {
                            MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Text(item.title, style = style)
                    }
                )
            }
        }

        // ----------- PAGER CONTENT -----------
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { index ->
            pageContent(index)
        }
    }
}
