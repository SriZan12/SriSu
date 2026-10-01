package com.srisu.srisu.features.home.entertainment.screen

import srisu.composeapp.generated.resources.cp_view_profile
import srisu.composeapp.generated.resources.cp_explore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.theme.spacing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.srisu.srisu.features.home.entertainment.vm.EntertainmentViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel


@Composable
@Preview
fun HomeScreen(
    onFindPartner: () -> Unit = {},
    onCoupleProfile: () -> Unit = {},
    onExplore: () -> Unit = {},
    entertainmentViewModel: EntertainmentViewModel = koinViewModel<EntertainmentViewModel>()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background

    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                SriSuButton(org.jetbrains.compose.resources.stringResource(srisu.composeapp.generated.resources.Res.string.cp_view_profile), onCoupleProfile)
                SriSuButton(org.jetbrains.compose.resources.stringResource(srisu.composeapp.generated.resources.Res.string.cp_explore), onExplore)
                SriSuButton("Find your partner", onFindPartner)
            }
        }

//        ChatScreen(
//            session = null,
//            navController = rememberNavController()
//        )
    }

}

