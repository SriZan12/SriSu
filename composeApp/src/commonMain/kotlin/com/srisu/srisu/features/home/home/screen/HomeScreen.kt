package com.srisu.srisu.features.home.home.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.features.chat.chatroom.ChatScreen
import com.srisu.srisu.features.home.home.vm.HomeViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel


@Composable
@Preview
fun HomeScreen(
    homeViewModel: HomeViewModel = koinViewModel<HomeViewModel>()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest

    ) { innerPadding ->
//        Box(
//            modifier = Modifier.fillMaxSize().padding(innerPadding),
//            contentAlignment = Alignment.Center
//        ) {
//            Text("HOME SCREEN")
//        }

        ChatScreen(
            navController = rememberNavController(
            )
        )
    }

}

