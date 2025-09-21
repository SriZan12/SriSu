package com.srisu.srisu.features.home.connection.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun MyCrushScreen() {
    MyCrushScreenContent()
}

val sampleMatches = listOf(
    Match("Sophia", "24", "https://photosmint.com/wp-content/uploads/2025/03/Indian-Beauty-DP.jpeg"),
    Match("Isabella", "18", "https://randomuser.me/api/portraits/women/2.jpg"),
    Match("Sita", "17", "https://photosmint.com/wp-content/uploads/2025/03/Hot-Girls-Dp.jpeg"),
    Match("Olivia", "31", "https://randomuser.me/api/portraits/women/4.jpg"),
    Match("Sophia", "28", "https://randomuser.me/api/portraits/women/5.jpg")
)

@Composable
fun MyCrushScreenContent(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = sampleMatches) {
            MatchItem(it)
        }
    }
}

@Composable
@Preview
fun PreviewMyCrushScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MyCrushScreen()
    }
}