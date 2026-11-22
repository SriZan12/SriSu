package com.srisu.srisu.features.moments.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

@Composable fun MomentNoteScreen(id: Long, onBack: () -> Unit, vm: MomentNoteViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.open(id) }
    MomentLifecycle(vm::active)
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter),
        verticalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        TextButton(onClick=onBack) { Text("Back") }
        Text("Private appreciation", style=MaterialTheme.typography.headlineSmall)
        Text("Only the sender and the original couple can read this conversation.")
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.unavailable) Text("This appreciation is no longer available.")
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        TextButton(onClick=vm::refresh,enabled=!state.loading && !state.sending) { Text("Reload replies") }
        state.note?.let { note ->
            Card(Modifier.fillMaxWidth()) { Text(note.message,Modifier.padding(MaterialTheme.spacing.medium)) }
            Text("Latest replies",style=MaterialTheme.typography.titleMedium)
            if(note.replies.isEmpty()) Text("No replies yet.")
            note.replies.sortedBy { it.id }.forEach { reply ->
                Card(Modifier.fillMaxWidth()) { Text(reply.message,Modifier.padding(MaterialTheme.spacing.medium)) }
            }
            if(note.replies.size>=50) Text("Showing the latest 50 replies.")
            if(note.canReply) {
                OutlinedTextField(state.draft,vm::draft,label={Text("Reply privately")},modifier=Modifier.fillMaxWidth(),enabled=!state.sending)
                Button(onClick=vm::reply,enabled=!state.sending && state.draft.isNotBlank()) { Text(if(state.sending) "Sending…" else "Send reply") }
            }
        }
    }
}
