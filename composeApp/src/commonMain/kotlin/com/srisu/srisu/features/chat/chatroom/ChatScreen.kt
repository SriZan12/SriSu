package com.srisu.srisu.features.chat.chatroom

// ChatScreen.kt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val error by viewModel.error.collectAsState()
    // Replace with the actual current user ID from your ViewModel or auth manager
    val currentUserId = 97

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Room") },
                actions = {
                    // Connection status indicator
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            )
        },
        bottomBar = {
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier

            ) {
                // Error banner
                error?.let { errorMessage ->
                    ErrorBanner(
                        message = errorMessage,
                        onDismiss = { viewModel.clearError() },
                        onRetry = { viewModel.retryConnection() }
                    )
                }


                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true // To show latest messages at the bottom
                ) {
                    items(
                        items = messages?.reversed() ?: emptyList(),
                        key = { (it?.id ?: it?.timestamp ?: "") }
                    ) { message ->
                        if (message != null) {
                            val isSentByCurrentUser = message.senderId == currentUserId
                            AppLogger.log("Sender id = ${message.senderId}")
                            MessageItem(
                                chatMessage = message,
                                isSentByCurrentUser = isSentByCurrentUser
                            )

                        }
                    }
                }
            }
//            FloatingHearts()
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Row {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = enabled,
                singleLine = false,
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = enabled && value.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}


@Composable
fun MessageItem(
    chatMessage: ChatMessage,
    isSentByCurrentUser: Boolean
) {
    Box {

        val romanticShape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (isSentByCurrentUser) 20.dp else 4.dp,
            bottomEnd = if (isSentByCurrentUser) 4.dp else 20.dp
        )

        val alignment = if (isSentByCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
        val backgroundColor = if (isSentByCurrentUser)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainer

        val textColor = if (isSentByCurrentUser)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurface

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            Card(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = romanticShape,
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Text(
                    text = chatMessage.text ?: "",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                )
            }
        }

    }
}


data class FloatingHeart(
    val startX: Float,
    val size: Float,
    val delay: Int
)

@Composable
fun FloatingHearts() {
    val hearts = remember {
        List(6) {
            FloatingHeart(
                startX = (30..300).random().toFloat(),
                size = (8..20).random().toFloat(),
                delay = (0..800).random()
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        hearts.forEach { heart ->
            var offsetY by remember { mutableStateOf(80f) }

            LaunchedEffect(Unit) {
                delay(heart.delay.toLong())
                animate(
                    initialValue = 80f,
                    targetValue = -40f,
                    animationSpec = infiniteRepeatable(
                        tween(2000, easing = LinearEasing),
                        RepeatMode.Restart
                    )
                ) { value, _ -> offsetY = value }
            }

            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(heart.size.dp)
                    .graphicsLayer {
                        translationX = heart.startX
                        translationY = offsetY
                        alpha = 0.9f
                    }
            )
        }
    }
}


@Preview
@Composable
private fun MessageItemPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
        MessageItem(
            chatMessage = ChatMessage(text = "Hello! How are you?", senderId = 1),
            isSentByCurrentUser = true
        )
        MessageItem(
            chatMessage = ChatMessage(
                text = "I'm doing great, thanks for asking! How about you?",
                senderId = 2
            ),
            isSentByCurrentUser = false
        )
    }
}
