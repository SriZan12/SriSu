package com.srisu.srisu.features.chat.chatroom

// ChatScreen.kt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel = koinViewModel(), meId: Int) {
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(messages) { msg ->
                val mine = msg.sender == meId
                ChatBubble(text = msg.text.toString(), mine = mine)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        HorizontalDivider()

        Row(modifier = Modifier.padding(8.dp)) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                viewModel.onSend(input)
                input = ""
            }) {
                Text("Send")
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, mine: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(text = text, modifier = Modifier.padding(10.dp))
        }
    }
}
