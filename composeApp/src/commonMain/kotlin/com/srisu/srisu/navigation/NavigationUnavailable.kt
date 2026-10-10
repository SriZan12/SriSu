package com.srisu.srisu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

@Composable fun NavigationUnavailable(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(MaterialTheme.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(Res.string.nav_unavailable))
        SriSuButton(stringResource(Res.string.nav_go_back), onBack)
    }
}
