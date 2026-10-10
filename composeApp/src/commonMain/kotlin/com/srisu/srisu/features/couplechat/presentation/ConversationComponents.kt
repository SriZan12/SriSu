@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.spacing

@Composable
internal fun ConversationOptions(state: ConversationState, repository: ConversationRepository, onDismiss: () -> Unit, onReset: () -> Unit) {
    var nickname by remember { mutableStateOf(state.preferences.nickname) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(MaterialTheme.spacing.gutter).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            Text("Conversation options", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(nickname, { if (it.length <= 60) nickname = it }, label = { Text("Personal nickname") },
                supportingText = { Text("Only you see this name. It stays on this device.") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { repository.preferences(state.preferences.copy(nickname = nickname)); onDismiss() }) { Text("Save nickname") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Read receipts"); Text("Let your partner know when you view messages.", style = MaterialTheme.typography.bodySmall) }
                Switch(state.preferences.readReceipts, { repository.preferences(state.preferences.copy(readReceipts = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Activity and typing"); Text("Share and see short-lived activity while this conversation is open.", style = MaterialTheme.typography.bodySmall) }
                Switch(state.preferences.presence, { repository.preferences(state.preferences.copy(presence = it)) })
            }
            TextButton(onClick = { repository.showSafetyCode(); onDismiss() }, enabled = state.partnerId != null) { Text("Verify safety code") }
            TextButton(onClick = { repository.reconcileHistory(); onDismiss() }) { Text("Reconcile saved history") }
            TextButton(onClick = onReset) { Text("Reset local chat storage…") }
        }
    }
}

@Composable
internal fun ConversationConfirmations(state: ConversationState, repository: ConversationRepository, media: ChatMediaController,
    delete: String?, replace: Boolean, reset: Boolean, onDeleteDismiss: () -> Unit, onReplaceDismiss: () -> Unit, onResetDismiss: () -> Unit) {
    if (delete != null) AlertDialog(onDismissRequest = onDeleteDismiss, title = { Text("Delete for everyone?") },
        text = { Text("Your message will be replaced with a deletion notice. Screenshots and exported copies cannot be erased.") },
        confirmButton = { TextButton(onClick = { repository.deleteForEveryone(delete); onDeleteDismiss() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDeleteDismiss) { Text("Cancel") } })
    if (replace) AlertDialog(onDismissRequest = onReplaceDismiss, title = { Text("Use chat on this device?") },
        text = { Text("SriSu chat supports one active device per account. Your other chat device will be revoked. Its undelivered messages and history cannot be restored here.") },
        confirmButton = { TextButton(onClick = { repository.confirmDeviceReplacement(); onReplaceDismiss() }) { Text("Use this device") } },
        dismissButton = { TextButton(onClick = onReplaceDismiss) { Text("Cancel") } })
    if (reset) AlertDialog(onDismissRequest = onResetDismiss, title = { Text("Reset protected chat storage?") },
        text = { Text("This permanently removes chat history, drafts, pending sends, and keys on this device. SriSu has no recovery key. You will need to set up this chat device again. Try reopening SriSu first if storage is temporarily unavailable.") },
        confirmButton = { TextButton(onClick = { repository.resetSecureStorage(); onResetDismiss() }) { Text("Erase local chat and reset") } },
        dismissButton = { TextButton(onClick = onResetDismiss) { Text("Keep my history") } })
    state.safetyCode?.let { code -> AlertDialog(onDismissRequest = repository::dismissSafetyCode, title = { Text("Verify your conversation") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Compare this code with your partner in person or through a channel you already trust.")
            state.safetyQr?.let {qr ->
                val bitmap=remember(qr) {media.safetyQr(qr)?.let(::decodePrivatePhoto)}
                bitmap?.let {androidx.compose.foundation.Image(it,"Your safety QR code",Modifier.fillMaxWidth().aspectRatio(1f))}
            }
            Text(code.chunked(5).joinToString(" "), Modifier.padding(vertical = MaterialTheme.spacing.medium))
            TextButton(onClick={media.scanSafetyQr(false)}) {Text("Scan partner’s QR")}
            TextButton(onClick={media.scanSafetyQr(true)}) {Text("Read QR from photo")}
            state.safetyVerified?.let {Text(if(it)"Safety codes match." else "Safety codes do not match. Do not confirm this identity.")}
            Text("Matching codes verify your current identities. A new identity requires another comparison.")
        } }, confirmButton = { TextButton(onClick = {
            if (state.security == ConversationSecurity.IDENTITY_CHANGED) repository.confirmIdentityChange()
            else repository.dismissSafetyCode()
        },enabled=state.security!=ConversationSecurity.IDENTITY_CHANGED || state.safetyVerified!=false) { Text(if (state.security == ConversationSecurity.IDENTITY_CHANGED) "Codes match — continue" else "Done") } },
        dismissButton = { if (state.security == ConversationSecurity.IDENTITY_CHANGED) TextButton(onClick = repository::dismissSafetyCode) { Text("Cancel") } }) }
}
