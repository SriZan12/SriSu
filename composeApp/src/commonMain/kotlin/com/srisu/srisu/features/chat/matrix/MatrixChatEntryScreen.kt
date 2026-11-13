package com.srisu.srisu.features.chat.matrix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.srisu.srisu.features.chat.matrix.presentation.ChatStatusState
import org.jetbrains.compose.resources.painterResource
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.chat_secure_loading
import srisu.composeapp.generated.resources.chat_offline
import srisu.composeapp.generated.resources.chat_secure_error_title
import srisu.composeapp.generated.resources.chat_secure_unavailable_title

@Composable
fun MatrixChatEntryScreen(
    viewModel: MatrixChatEntryViewModel,
    onOpenRoom: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.resolve() }
    LaunchedEffect(state) {
        (state as? MatrixChatEntryState.Ready)?.roomId?.let(onOpenRoom)
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(androidx.compose.material3.MaterialTheme.spacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.MaterialTheme.spacing.medium),
        ) {
            when (val current = state) {
                MatrixChatEntryState.Resolving, is MatrixChatEntryState.Ready -> {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.chat_secure_loading), textAlign = TextAlign.Center)
                }
                is MatrixChatEntryState.Unavailable -> ChatStatusState(
                    title = stringResource(Res.string.chat_secure_unavailable_title),
                    message = current.message,
                    vector = Icons.Filled.Lock,
                    action = { viewModel.resolve(force = true) },
                )
                is MatrixChatEntryState.Error -> ChatStatusState(
                    title = stringResource(Res.string.chat_secure_error_title),
                    message = current.message,
                    icon = painterResource(Res.drawable.chat_offline),
                    action = { viewModel.resolve(force = true) },
                )
            }
        }
    }
}
