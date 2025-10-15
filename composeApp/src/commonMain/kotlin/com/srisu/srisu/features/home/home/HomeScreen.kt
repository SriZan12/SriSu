package com.srisu.srisu.features.home.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel


@Composable
@Preview
fun HomeScreen(
    homeViewModel: HomeViewModel = koinViewModel<HomeViewModel>()
) {
    Scaffold() { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("HOME SCREEN")
        }
    }

}