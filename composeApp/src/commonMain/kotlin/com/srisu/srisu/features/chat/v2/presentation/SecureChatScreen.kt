package com.srisu.srisu.features.chat.v2.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.features.chat.v2.domain.SecureChatAvailability
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.chat_secure_back
import srisu.composeapp.generated.resources.chat_secure_error
import srisu.composeapp.generated.resources.chat_secure_loading
import srisu.composeapp.generated.resources.chat_secure_not_available
import srisu.composeapp.generated.resources.chat_secure_ready
import srisu.composeapp.generated.resources.chat_secure_retry
import srisu.composeapp.generated.resources.chat_secure_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureChatScreen(
    viewModel: SecureChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.chat_secure_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.chat_secure_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(MaterialTheme.spacing.gutter),
            contentAlignment = Alignment.Center,
        ) {
            when (val availability = state?.availability ?: SecureChatAvailability.Loading) {
                SecureChatAvailability.Loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.chat_secure_loading), textAlign = TextAlign.Center)
                }
                is SecureChatAvailability.Error -> Status(
                    message = availability.message.ifBlank { stringResource(Res.string.chat_secure_error) },
                    action = { SriSuButton(stringResource(Res.string.chat_secure_retry), viewModel::retry) },
                )
                is SecureChatAvailability.Unavailable -> Status(
                    message = stringResource(Res.string.chat_secure_not_available),
                    action = { SriSuButton(stringResource(Res.string.chat_secure_retry), viewModel::retry) },
                )
                is SecureChatAvailability.Ready -> Status(
                    message = state?.errorMessage ?: stringResource(Res.string.chat_secure_ready),
                    action = state?.errorMessage?.let { { SriSuButton(stringResource(Res.string.chat_secure_retry), viewModel::retry) } },
                )
            }
        }
    }
}

@Composable
private fun Status(message: String, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.invoke()
    }
}
