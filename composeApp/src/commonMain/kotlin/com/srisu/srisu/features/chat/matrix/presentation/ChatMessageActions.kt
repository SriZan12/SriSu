package com.srisu.srisu.features.chat.matrix.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.features.couplechat.presentation.*
import com.srisu.srisu.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMessageActionsSheet(message: LocalChatMessage, accountId: Long?, ready: Boolean,
    onDismiss: () -> Unit, onReact: (String?) -> Unit, onReply: () -> Unit, onEdit: () -> Unit,
    onDelete: () -> Unit, onCopy: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = null) {
        Column(Modifier.fillMaxWidth().testTag("chat-message-actions").verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.medium).padding(bottom = MaterialTheme.spacing.medium)) {
            Row(Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
                Text("Message", Modifier.weight(1f).padding(start = MaterialTheme.spacing.small), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close message options") }
            }
            val body = when (val content = message.content) {
                is ChatContent.Text -> content.body
                is ChatContent.Media -> content.caption
                is ChatContent.Moment -> content.body
                else -> ""
            }
            val available = !message.deleted && !message.unavailable
            if (available && body.isNotBlank()) Text(body, Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.small)
                .padding(bottom = MaterialTheme.spacing.small), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ready && message.transport == ChatTransport.SENT && available) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth < 288.dp || LocalDensity.current.fontScale > 1.3f) 3 else 6
                    val gap = if (maxWidth < 308.dp) 0.dp else MaterialTheme.spacing.tiny
                    Column(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                        listOf("❤️", "😂", "🥰", "😮", "😢", "👍").chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { emoji ->
                                    val selected = message.reactions[accountId] == emoji
                                    Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.pill,
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            .clickable(onClickLabel = "React $emoji") { onReact(if (selected) null else emoji) }
                                            .semantics { contentDescription = "React $emoji" }
                                            .padding(MaterialTheme.spacing.small), contentAlignment = Alignment.Center) {
                                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ChatActionRow("Reply", ChatIcons.Reply, onReply)
                if (message.authorId == accountId && message.content is ChatContent.Text)
                    ChatActionRow("Edit text", Icons.Outlined.Edit, onEdit)
                if (message.reactions.containsKey(accountId)) ChatActionRow("Remove my reaction", ChatIcons.RemoveCircleOutline, { onReact(null) })
            }
            if (available && body.isNotBlank()) ChatActionRow("Copy text", ChatIcons.ContentCopy, { onCopy(body) })
            if (ready && !message.deleted && !message.hidden && message.transport == ChatTransport.SENT && message.authorId == accountId) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ChatActionRow("Delete for everyone", ChatIcons.DeleteOutline, onDelete, destructive = true)
            }
        }
    }
}
