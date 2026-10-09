package com.srisu.srisu.features.chat.matrix.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.chat.matrix.MatrixChatMessage
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.chat_secure_back
import srisu.composeapp.generated.resources.chat_secure_content_unavailable
import srisu.composeapp.generated.resources.chat_secure_empty
import srisu.composeapp.generated.resources.chat_secure_error
import srisu.composeapp.generated.resources.chat_secure_failed
import srisu.composeapp.generated.resources.chat_secure_loading
import srisu.composeapp.generated.resources.chat_secure_message_hint
import srisu.composeapp.generated.resources.chat_secure_not_available
import srisu.composeapp.generated.resources.chat_secure_retry
import srisu.composeapp.generated.resources.chat_secure_send
import srisu.composeapp.generated.resources.chat_secure_sending
import srisu.composeapp.generated.resources.chat_secure_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureChatScreen(viewModel: SecureChatViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val input by viewModel.messageInput.collectAsState()
    val validationError by viewModel.composerValidationError.collectAsState()
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
        when (val availability = state?.availability ?: MatrixChatAvailability.Loading) {
            MatrixChatAvailability.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is MatrixChatAvailability.Error -> Status(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = availability.message.ifBlank { stringResource(Res.string.chat_secure_error) },
                action = if (availability.retryable) {
                    { SriSuButton(stringResource(Res.string.chat_secure_retry), viewModel::retry) }
                } else null,
            )
            is MatrixChatAvailability.Offline -> Conversation(
                modifier = Modifier.fillMaxSize().padding(padding),
                messages = state?.messages.orEmpty(),
                input = input,
                sending = false,
                error = validationError,
                composerEnabled = false,
                offlineMessage = availability.message,
                onInput = viewModel::updateMessageInput,
                onSend = viewModel::sendMessage,
                onRetry = viewModel::retryMessage,
                onReconnect = viewModel::retry,
            )
            is MatrixChatAvailability.Unavailable -> Status(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = availability.message.ifBlank { stringResource(Res.string.chat_secure_not_available) },
            )
            is MatrixChatAvailability.Ready -> Conversation(
                modifier = Modifier.fillMaxSize().padding(padding),
                messages = state?.messages.orEmpty(),
                input = input,
                sending = state?.sending == true,
                error = validationError ?: state?.composerError,
                composerEnabled = true,
                offlineMessage = null,
                onInput = viewModel::updateMessageInput,
                onSend = viewModel::sendMessage,
                onRetry = viewModel::retryMessage,
                onReconnect = viewModel::retry,
            )
        }
    }
}

@Composable
private fun Conversation(
    modifier: Modifier,
    messages: List<MatrixChatMessage>,
    input: String,
    sending: Boolean,
    error: String?,
    composerEnabled: Boolean,
    offlineMessage: String?,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: (String) -> Unit,
    onReconnect: () -> Unit,
) {
    val listState = rememberLazyListState()
    var previousLastMessageId by remember { mutableStateOf<String?>(null) }
    val latest = messages.lastOrNull()
    LaunchedEffect(latest?.id) {
        if (latest == null) {
            previousLastMessageId = null
            return@LaunchedEffect
        }
        val initialLoad = previousLastMessageId == null
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val wasNearLatest = lastVisibleIndex != null && lastVisibleIndex >= messages.lastIndex - 1
        if (initialLoad) listState.scrollToItem(messages.lastIndex)
        else if (wasNearLatest || latest.mine) listState.animateScrollToItem(messages.lastIndex)
        previousLastMessageId = latest.id
    }
    Column(modifier) {
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.chat_secure_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                items(messages, key = MatrixChatMessage::id) { message -> MessageBubble(message, onRetry) }
            }
        }
        offlineMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    Text(
                        message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = onReconnect) {
                        Text(stringResource(Res.string.chat_secure_retry))
                    }
                }
            }
        }
        error?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.gutter),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(Res.string.chat_secure_message_hint)) },
                enabled = composerEnabled && !sending,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank() && !sending) onSend() }),
            )
            SriSuButton(
                label = stringResource(if (sending) Res.string.chat_secure_sending else Res.string.chat_secure_send),
                onClick = onSend,
                enabled = composerEnabled && input.isNotBlank() && !sending,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: MatrixChatMessage, onRetry: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = MaterialTheme.shapes.large,
            color = if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)) {
                Text(
                    if (message.unavailable) stringResource(Res.string.chat_secure_content_unavailable) else message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.unavailable) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                when (message.delivery) {
                    MatrixMessageDelivery.SENDING -> Text(
                        stringResource(Res.string.chat_secure_sending),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MatrixMessageDelivery.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.chat_secure_failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        message.retryTransactionId?.let { transactionId ->
                            TextButton(onClick = { onRetry(transactionId) }) {
                                Text(stringResource(Res.string.chat_secure_retry))
                            }
                        }
                    }
                    MatrixMessageDelivery.SENT -> Unit
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier.padding(MaterialTheme.spacing.gutter), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.chat_secure_loading), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Status(modifier: Modifier, message: String, action: (@Composable () -> Unit)? = null) {
    Box(modifier.padding(MaterialTheme.spacing.gutter), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            action?.invoke()
        }
    }
}
