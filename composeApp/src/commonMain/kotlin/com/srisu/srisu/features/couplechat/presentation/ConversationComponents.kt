@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.spacing

@Composable
internal fun MessageBubble(message: LocalChatMessage, own: Boolean, partner: String, accountId: Long?, grouped: Boolean,
    onMenu: () -> Unit, onReply: () -> Unit, onOriginal: (String) -> Unit, onRetry: () -> Unit,
    onMedia: (String)->Unit, uploadProgress: Float?, offlineMedia: Set<String>,cardContent: @Composable ()->Unit = {}) {
    val available = !message.hidden && !message.deleted && !message.unavailable
    Row(Modifier.fillMaxWidth().padding(top = if (grouped) MaterialTheme.spacing.none else MaterialTheme.spacing.small),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        Surface(shape = MaterialTheme.shapes.large,
            color = if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = onMenu)
                .semantics { customActions = listOf(CustomAccessibilityAction("Message options") { onMenu(); true }) +
                    if (available) listOf(CustomAccessibilityAction("Reply") { onReply(); true }) else emptyList() }
                .pointerInput(message.id, available) {
                    var distance = 0f
                    detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                        if (available && distance > 64.dp.toPx()) onReply()
                    }) { _, amount -> distance += amount }
                }) {
            Column(Modifier.padding(MaterialTheme.spacing.compact), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                when {
                    message.hidden -> Text("Deleted for you", style = MaterialTheme.typography.bodySmall)
                    message.deleted -> Text("This message was deleted", style = MaterialTheme.typography.bodySmall)
                    message.unavailable -> Text("Message unavailable on this device", style = MaterialTheme.typography.bodySmall)
                    else -> when (val content = message.content) {
                        is ChatContent.Text -> {
                            content.quote?.let { quote -> Surface(shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                modifier = Modifier.combinedClickable(onClick = { onOriginal(quote.messageId) })) {
                                Column(Modifier.padding(MaterialTheme.spacing.small)) {
                                    Text(if (quote.authorId == accountId) "You" else partner, style = MaterialTheme.typography.labelSmall)
                                    Text(quote.preview, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                                }
                            } }
                            SelectionContainer { Text(content.body, style = MaterialTheme.typography.bodyLarge) }
                        }
                        is ChatContent.Media -> {
                            content.quote?.let {quote -> TextButton(onClick={onOriginal(quote.messageId)}) {Text(quote.preview,maxLines=2)} }
                            content.items.forEach {media ->
                                MediaPreview(media,{onMedia(media.descriptor.id)},Modifier.widthIn(min=200.dp))
                                Text(if(media.descriptor.id in offlineMedia)"Available offline" else "Download to open",style=MaterialTheme.typography.labelSmall)
                            }
                            if(content.caption.isNotBlank()) SelectionContainer {Text(content.caption,style=MaterialTheme.typography.bodyLarge)}
                            if(uploadProgress!=null && message.transport!=ChatTransport.SENT) {
                                LinearProgressIndicator(progress={uploadProgress},modifier=Modifier.fillMaxWidth())
                                Text("Uploading ${(uploadProgress*100).toInt()}%",style=MaterialTheme.typography.labelSmall)
                            }
                            if(message.played && own && content.items.any {it.kind=="voice"}) Text("Played",style=MaterialTheme.typography.labelSmall)
                        }
                        is ChatContent.Card -> cardContent()
                        else -> Text("Unsupported message", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (message.reactions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                    message.reactions.values.forEach { reaction -> Text(reaction) }
                }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    Text(chatLocalTime(message.createdAt), style = MaterialTheme.typography.labelSmall)
                    if (own) Text(when (message.transport) {
                        ChatTransport.QUEUED -> "Queued"
                        ChatTransport.SENDING -> "Sending"
                        ChatTransport.FAILED -> "Failed"
                        ChatTransport.SENT -> if (message.read) "Read" else if (message.delivered) "Delivered" else "Sent"
                    }, style = MaterialTheme.typography.labelSmall)
                }
                if (message.transport == ChatTransport.FAILED && own) TextButton(onClick = onRetry) { Text("Retry send") }
            }
        }
    }
}

@Composable
internal fun MessageActions(message: LocalChatMessage, state: ConversationState, repository: ConversationRepository,
    onDelete: () -> Unit, onDismiss: () -> Unit) {
    val copy = rememberCopyPrivateText()
    val available = !message.hidden && !message.deleted && !message.unavailable
    val connected = state.security == ConversationSecurity.READY
    var moreReactions by remember {mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter).navigationBarsPadding()) {
            if (available && connected && message.sequence != null) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (if(moreReactions)listOf("❤️","😂","🥰","😮","😢","👍","🥹","😘","🙌","😌","😊","🫶","💛","💜","🔥","🎉","🤗","🙏")
                        else listOf("❤️", "😂", "🥰", "😮", "😢", "👍")).forEach { emoji ->
                        TextButton(onClick = { repository.react(message.id, if (message.reactions[state.accountId] == emoji) null else emoji); onDismiss() }) { Text(emoji) }
                    }
                }
                TextButton(onClick={moreReactions=!moreReactions}) {Text(if(moreReactions)"Fewer reactions" else "More reactions")}
                if (message.reactions.containsKey(state.accountId)) TextButton(onClick = { repository.react(message.id, null); onDismiss() }) { Text("Remove my reaction") }
            }
            if (available) {
                if (connected) TextButton(onClick = { repository.setReply(message.id); onDismiss() }) { Text("Reply") }
                (message.content as? ChatContent.Text)?.let { text -> TextButton(onClick = { copy(text.body); onDismiss() }) { Text("Copy text") } }
            }
            if (!message.hidden && message.authorId==state.accountId && message.transport in setOf(ChatTransport.QUEUED,ChatTransport.SENDING))
                TextButton(onClick={repository.cancelSend(message.id);onDismiss()}) {Text("Cancel send")}
            else if (!message.hidden) TextButton(onClick = { repository.deleteForMe(message.id); onDismiss() }) { Text("Delete for me") }
            if (connected && message.authorId == state.accountId && !message.deleted && message.sequence != null)
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete for everyone") }
        }
    }
}

@Composable
internal fun ConversationOptions(state: ConversationState, repository: ConversationRepository, onDismiss: () -> Unit, onReset: () -> Unit) {
    var nickname by remember { mutableStateOf(state.preferences.nickname) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.gutter).navigationBarsPadding(),
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
