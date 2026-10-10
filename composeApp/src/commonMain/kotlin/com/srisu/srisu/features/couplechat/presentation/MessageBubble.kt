@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*

internal fun messageStatus(message: LocalChatMessage): String = when (message.transport) {
    ChatTransport.QUEUED -> "Queued"
    ChatTransport.SENDING -> "Sending"
    ChatTransport.FAILED -> "Failed"
    ChatTransport.SENT -> if (message.read) "Read" else if (message.delivered) "Delivered" else "Sent"
}

@Composable
internal fun MessageBubble(message: LocalChatMessage, own: Boolean, partner: String, accountId: Long?, grouped: Boolean,
    onMenu: () -> Unit, onReply: () -> Unit, onOriginal: (String) -> Unit, onRetry: () -> Unit,
    onMedia: (String) -> Unit, uploadProgress: Float?, offlineMedia: Set<String>, cardContent: @Composable () -> Unit = {}) {
    val available = !message.hidden && !message.deleted && !message.unavailable
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = if (grouped) MaterialTheme.spacing.tiny else MaterialTheme.spacing.compact,
        bottom = MaterialTheme.spacing.tiny)) {
        val width = (maxWidth * .88f).coerceAtMost(420.dp)
        Column(Modifier.widthIn(max = width).align(if (own) Alignment.TopEnd else Alignment.TopStart),
            horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
            Surface(shape = if (own) MaterialTheme.shapes.medium.copy(bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd)
                else MaterialTheme.shapes.medium.copy(bottomStart = MaterialTheme.shapes.extraSmall.bottomStart),
                color = if (own) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (own) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                border = if (own) null else BorderStroke(MaterialTheme.spacing.hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                modifier = Modifier.combinedClickable(onClick = {}, onLongClickLabel = "Message options", onLongClick = onMenu)
                    .semantics { customActions = listOf(CustomAccessibilityAction("Message options") { onMenu(); true }) +
                        if (available) listOf(CustomAccessibilityAction("Reply") { onReply(); true }) else emptyList() }
                    .pointerInput(message.id, available) {
                        var distance = 0f
                        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                            if (available && distance > 64.dp.toPx()) onReply()
                        }, onHorizontalDrag = { change, amount ->
                            if (available) { distance += amount; if (distance > 0) change.consume() }
                        })
                    }) {
                Column(Modifier.padding(MaterialTheme.spacing.compact), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    when {
                        message.hidden -> UnavailableMessage("Deleted for you")
                        message.deleted -> UnavailableMessage("This message was deleted")
                        message.unavailable -> UnavailableMessage("Message unavailable on this device")
                        else -> when (val content = message.content) {
                            is ChatContent.Text -> {
                                content.quote?.let { QuotePreview(it, accountId, partner, onOriginal = { onOriginal(it.messageId) }) }
                                Text(content.body, style = MaterialTheme.typography.bodyLarge)
                            }
                            is ChatContent.Media -> {
                                content.quote?.let { QuotePreview(it, accountId, partner, onOriginal = { onOriginal(it.messageId) }) }
                                content.items.forEach { media ->
                                    MediaPreview(media, { onMedia(media.descriptor.id) }, Modifier.width(width),
                                        savedOffline = media.descriptor.id in offlineMedia)
                                }
                                if (content.caption.isNotBlank()) Text(content.caption, style = MaterialTheme.typography.bodyLarge)
                                if (uploadProgress != null && message.transport != ChatTransport.SENT) {
                                    LinearProgressIndicator(progress = { uploadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                    Text("Uploading ${(uploadProgress.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                }
                                if (message.played && own && content.items.any { it.kind == "voice" }) Text("Played", style = MaterialTheme.typography.bodySmall)
                            }
                            is ChatContent.Card -> cardContent()
                            else -> UnavailableMessage("Unsupported message")
                        }
                    }
                    MessageMetadata(message, own, Modifier.align(Alignment.End))
                }
            }
            if (available && message.reactions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                message.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) ->
                    Surface(onClick = onMenu, shape = MaterialTheme.shapes.pill,
                        border = BorderStroke(MaterialTheme.spacing.hairline,
                            if (message.reactions[accountId] == emoji) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.tiny).semantics {
                            contentDescription = "$emoji, $count ${if (count == 1) "reaction" else "reactions"}" +
                                if (message.reactions[accountId] == emoji) ", including yours" else ""
                        }) {
                        Text(emoji + if (count > 1) " $count" else "", Modifier.padding(horizontal = MaterialTheme.spacing.small,
                            vertical = MaterialTheme.spacing.tiny), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (message.transport == ChatTransport.FAILED && own && !message.hidden) TextButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(MaterialTheme.spacing.icon))
                Spacer(Modifier.width(MaterialTheme.spacing.tiny)); Text("Retry send")
            }
        }
    }
}

@Composable private fun UnavailableMessage(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Icon(ChatIcons.Block, null, Modifier.size(MaterialTheme.spacing.icon))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun MessageMetadata(message: LocalChatMessage, own: Boolean, modifier: Modifier = Modifier) {
    val failed = own && message.transport == ChatTransport.FAILED
    val color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(modifier.then(if (failed) Modifier.background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraSmall)
        .padding(horizontal = MaterialTheme.spacing.tiny, vertical = MaterialTheme.spacing.hairline) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny), verticalArrangement = Arrangement.Center) {
        Text(chatLocalTime(message.createdAt), style = MaterialTheme.typography.bodySmall, color = color)
        if (own) {
            Icon(when (message.transport) {
                ChatTransport.QUEUED -> ChatIcons.Schedule
                ChatTransport.SENDING -> ChatIcons.MoreHoriz
                ChatTransport.FAILED -> ChatIcons.ErrorOutline
                ChatTransport.SENT -> if (message.read || message.delivered) ChatIcons.DoneAll else Icons.Outlined.Done
            }, null, Modifier.size(16.dp).align(Alignment.CenterVertically), tint = color)
            Text(messageStatus(message), style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable internal fun ChatActionRow(label: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(),
        contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.heightIn(min = MaterialTheme.spacing.touchTarget).padding(vertical = MaterialTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            Icon(icon, null, Modifier.size(MaterialTheme.spacing.chromeIcon))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun MessageActions(message: LocalChatMessage, state: ConversationState, repository: ConversationRepository,
    onDelete: () -> Unit, onDeleteForMe: () -> Unit, onSelectText: () -> Unit, onDismiss: () -> Unit) {
    val copy = rememberCopyPrivateText()
    val available = !message.hidden && !message.deleted && !message.unavailable
    val connected = state.security == ConversationSecurity.READY
    var moreReactions by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.gutter)
            .padding(bottom = MaterialTheme.spacing.medium).navigationBarsPadding()) {
            Text("Message options", style = MaterialTheme.typography.titleMedium)
            if (available && connected && message.sequence != null) {
                FlowRow(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.small), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (if (moreReactions) listOf("❤️", "😂", "🥰", "😮", "😢", "👍", "🥹", "😘", "🙌", "😌", "😊", "🫶", "💛", "💜", "🔥", "🎉", "🤗", "🙏")
                    else listOf("❤️", "😂", "🥰", "😮", "😢", "👍")).forEach { emoji ->
                        TextButton(onClick = { repository.react(message.id, if (message.reactions[state.accountId] == emoji) null else emoji); onDismiss() },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                                contentDescription = "React $emoji"; selected = message.reactions[state.accountId] == emoji
                            }, contentPadding = PaddingValues(MaterialTheme.spacing.tiny)) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
                TextButton(onClick = { moreReactions = !moreReactions }) { Text(if (moreReactions) "Fewer reactions" else "More reactions") }
                if (message.reactions.containsKey(state.accountId)) ChatActionRow("Remove my reaction", ChatIcons.RemoveCircleOutline,
                    { repository.react(message.id, null); onDismiss() })
            }
            HorizontalDivider(Modifier.padding(vertical = MaterialTheme.spacing.small))
            if (available) {
                if (connected) ChatActionRow("Reply", ChatIcons.Reply, { repository.setReply(message.id); onDismiss() })
                val text = when (val content = message.content) {
                    is ChatContent.Text -> content.body
                    is ChatContent.Media -> content.caption.takeIf(String::isNotBlank)
                    else -> null
                }
                text?.let {
                    ChatActionRow("Copy text", ChatIcons.ContentCopy, { copy(it); onDismiss() })
                    TextButton(onClick = { onSelectText(); onDismiss() }) { Text("Select part of the text") }
                }
            }
            if (!message.hidden && message.authorId == state.accountId && message.transport in setOf(ChatTransport.QUEUED, ChatTransport.SENDING))
                ChatActionRow("Cancel send", Icons.Outlined.Close, { repository.cancelSend(message.id); onDismiss() })
            else if (!message.hidden) ChatActionRow("Delete for me", ChatIcons.DeleteOutline, { onDeleteForMe(); onDismiss() }, true)
            if (connected && message.authorId == state.accountId && !message.deleted && message.sequence != null)
                ChatActionRow("Delete for everyone", ChatIcons.DeleteSweep, { onDelete(); onDismiss() }, true)
        }
    }
}
