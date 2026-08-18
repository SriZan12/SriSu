package com.srisu.srisu.features.home.home.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.home.home.vm.HomeVM
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun HomeScreen(
    homeVM: HomeVM = koinViewModel<HomeVM>(),
    onNavigateToChat: () -> Unit,
    onNavigateToFindYourPartner: () -> Unit,
) {

    val homeUiState by homeVM.homeUiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest

    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    if (homeUiState.isEngaged) {
                        onNavigateToChat()
                    } else {
                        onNavigateToFindYourPartner()
                    }
                },
                content = { Text("Go to Chats") }
            )
        }
    }
}

