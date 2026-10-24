@file:OptIn(
    ExperimentalMaterial3Api::class
)

package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*

/** Content geometry derived from the thread frame; spacing/corners stay in the theme. */
internal object ChatMessageLayout {
    val maxWidth = 360.dp
    val mediaWidth = 260.dp
    val voiceWidth = 280.dp
    val avatarSize = 28.dp
}

internal fun messageStatus(message: LocalChatMessage): String = when (message.transport) {
    ChatTransport.QUEUED -> "Queued"
    ChatTransport.SENDING -> "Sending"
    ChatTransport.FAILED -> "Failed"
    ChatTransport.SENT -> if (message.read) "Read" else if (message.delivered) "Delivered" else "Sent"
}

/** Shared geometry for text, voice, media and feature cards. */
@Composable
internal fun MessageBubble(
    message: LocalChatMessage,
    own: Boolean,
    partner: String,
    accountId: Long?,
    grouped: Boolean,
    onMenu: () -> Unit,
    onReply: () -> Unit,
    onOriginal: (String) -> Unit,
    onRetry: () -> Unit,
    onMedia: (String) -> Unit,
    uploadProgress: Float?,
    offlineMedia: Set<String>,
    groupContinues: Boolean = false,
    partnerPhoto: androidx.compose.ui.graphics.ImageBitmap? = null,
    highlighted: Boolean = false,
    afterReactions: Boolean = false,
    mediaRequest: ChatMediaRequest? = null,
    mediaLoading: Boolean = false,
    mediaError: String? = null,
    voiceContent: (@Composable (ChatMedia, Modifier) -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    cardContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val available = !message.hidden && !message.deleted && !message.unavailable
    val attachmentMessage = (message.content as? ChatContent.Media)?.takeIf { available }
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface.copy(
                    alpha = 0f
                ),
                MaterialTheme.shapes.medium
            )
            .padding(top = if (grouped || afterReactions) MaterialTheme.spacing.tiny else MaterialTheme.spacing.compact)
    ) {
        val visualMedia = attachmentMessage?.items?.any { it.kind != "voice" } == true
        val width = (maxWidth * if (visualMedia) .64f else .82f).coerceAtMost(
            when {
                attachmentMessage == null -> ChatMessageLayout.maxWidth
                attachmentMessage.items.all { it.kind == "voice" } -> ChatMessageLayout.voiceWidth
                else -> ChatMessageLayout.mediaWidth
            })
        Row(
            Modifier.align(if (own) Alignment.TopEnd else Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            if (!own) {
                if (!grouped && avatar != null) avatar()
                else if (!grouped) ParticipantAvatar(
                    partner,
                    partnerPhoto,
                    Modifier.padding(top = MaterialTheme.spacing.tiny)
                        .size(ChatMessageLayout.avatarSize)
                )
                else Spacer(Modifier.width(ChatMessageLayout.avatarSize))
            }
            Column(
                Modifier.widthIn(max = width),
                horizontalAlignment = if (own) Alignment.End else Alignment.Start
            ) {
                val shapes = MaterialTheme.shapes
                val corner = shapes.extraSmall.topStart
                val shape = if (own) shapes.medium.copy(
                    topEnd = if (grouped) corner else shapes.medium.topEnd,
                    bottomEnd = if (groupContinues) corner else shapes.medium.bottomEnd
                )
                else shapes.medium.copy(
                    topStart = if (grouped) corner else shapes.medium.topStart,
                    bottomStart = if (groupContinues) corner else shapes.medium.bottomStart
                )
                Surface(
                    shape = shape,
                    color = MaterialTheme.chatColors.incoming,
                    contentColor = MaterialTheme.chatColors.text,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClickLabel = "Message options",
                        onLongClick = onMenu
                    )
                        .semantics {
                            customActions =
                                listOf(CustomAccessibilityAction("Message options") { onMenu(); true }) +
                                        if (available) listOf(CustomAccessibilityAction("Reply") { onReply(); true }) else emptyList()
                        }
                        .pointerInput(message.id, available) {
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onDragEnd = {
                                    if (available && distance > 64.dp.toPx()) onReply()
                                },
                                onHorizontalDrag = { change, amount ->
                                    if (available) {
                                        distance += amount; if (distance > 0) change.consume()
                                    }
                                })
                        }) {
                    Column(
                        Modifier.then(if (own) Modifier.background(MaterialTheme.chatColors.outgoing) else Modifier)
                            .then(if (attachmentMessage != null) Modifier.width(width) else Modifier)
                            .padding(
                                horizontal = if (visualMedia) 0.dp else if (attachmentMessage != null) MaterialTheme.spacing.tiny else MaterialTheme.spacing.compact,
                                vertical = if (visualMedia) 0.dp else if (attachmentMessage != null) MaterialTheme.spacing.tiny else MaterialTheme.spacing.small
                            )
                    ) {
                        when {
                            message.hidden -> UnavailableMessage("Deleted for you")
                            message.deleted -> UnavailableMessage("This message was deleted")
                            message.unavailable -> UnavailableMessage("Message unavailable on this device")
                            else -> when (val content = message.content) {
                                is ChatContent.Moment -> { cardContent(); MessageText(content.body) }
                                is ChatContent.Plan -> { cardContent(); MessageText(content.body) }
                                is ChatContent.Challenge -> { cardContent(); MessageText(content.body) }
                                is ChatContent.Spark -> { cardContent(); MessageText(content.body) }
                                is ChatContent.Text -> {
                                    content.quote?.let {
                                        QuotePreview(
                                            it,
                                            accountId,
                                            partner,
                                            Modifier.padding(bottom = MaterialTheme.spacing.small),
                                            onOriginal = { onOriginal(it.messageId) })
                                    }
                                    MessageText(content.body)
                                }

                                is ChatContent.Media -> {
                                    content.quote?.let {
                                        QuotePreview(
                                            it,
                                            accountId,
                                            partner,
                                            Modifier.padding(MaterialTheme.spacing.small),
                                            onOriginal = { onOriginal(it.messageId) })
                                    }
                                    content.items.forEachIndexed { index, media ->
                                        val request =
                                            mediaRequest?.messageId == message.id && mediaRequest.attachmentId == media.descriptor.id
                                        val inset = Modifier.padding(MaterialTheme.spacing.small)
                                            .fillMaxWidth()
                                        if (media.kind == "voice" && voiceContent != null) voiceContent(
                                            media,
                                            inset
                                        )
                                        else MediaPreview(
                                            media,
                                            { onMedia(media.descriptor.id) },
                                            if (media.kind == "voice") inset else Modifier.fillMaxWidth()
                                                .padding(top = if (index > 0) MaterialTheme.spacing.tiny else 0.dp),
                                            savedOffline = media.descriptor.id in offlineMedia,
                                            loading = request && mediaLoading,
                                            error = mediaError.takeIf { request },
                                            edgeToEdge = visualMedia,
                                            showDownloadBadge = !visualMedia
                                        )
                                    }
                                    if (content.caption.isNotBlank()) Box(
                                        Modifier.padding(horizontal = MaterialTheme.spacing.compact)
                                            .padding(top = MaterialTheme.spacing.small)
                                    ) { MessageText(content.caption) }
                                    if (uploadProgress != null && message.transport != ChatTransport.SENT) Column(
                                        Modifier.padding(MaterialTheme.spacing.small)
                                    ) {
                                        LinearProgressIndicator(progress = {
                                            uploadProgress.coerceIn(
                                                0f,
                                                1f
                                            )
                                        }, modifier = Modifier.fillMaxWidth())
                                        Text(
                                            "Uploading ${
                                                (uploadProgress.coerceIn(
                                                    0f,
                                                    1f
                                                ) * 100).toInt()
                                            }%", style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                else -> UnavailableMessage("Unsupported message")
                            }
                        }
                        if (visualMedia) Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MaterialTheme.spacing.compact)
                                .padding(
                                    top = if (attachmentMessage!!.caption.isNotBlank()) MaterialTheme.spacing.tiny else MaterialTheme.spacing.small,
                                    bottom = MaterialTheme.spacing.small
                                ), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)
                        ) {
                            if (message.transport == ChatTransport.SENT && attachmentMessage.items.any { it.descriptor.id !in offlineMedia })
                                Icon(
                                    ChatIcons.Download,
                                    "Downloads when opened",
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.chatColors.metadata
                                )
                            Box(
                                Modifier.weight(1f),
                                contentAlignment = Alignment.CenterEnd
                            ) { MessageMetadata(message, own) }
                        } else MessageMetadata(
                            message, own, Modifier.align(Alignment.End)
                                .padding(top = MaterialTheme.spacing.tiny)
                                .then(
                                    if (attachmentMessage != null) Modifier.padding(horizontal = MaterialTheme.spacing.small)
                                        .padding(bottom = MaterialTheme.spacing.tiny) else Modifier
                                )
                        )
                    }
                }
                if (available && message.reactions.isNotEmpty()) FlowRow(
                    Modifier.padding(top = MaterialTheme.spacing.tiny),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)
                ) {
                    message.reactions.values.groupingBy { it }.eachCount()
                        .forEach { (emoji, count) ->
                            val mine = message.reactions[accountId] == emoji
                            Box(
                                Modifier.sizeIn(
                                    minWidth = MaterialTheme.spacing.touchTarget,
                                    minHeight = MaterialTheme.spacing.touchTarget
                                )
                                    .clickable(onClickLabel = "Reaction options", onClick = onMenu)
                                    .semantics {
                                        selected = mine; contentDescription =
                                        "$emoji, $count ${if (count == 1) "reaction" else "reactions"}" + if (mine) ", including yours" else ""
                                    }) {
                                Surface(
                                    shape = MaterialTheme.shapes.pill,
                                    border = if (mine) BorderStroke(
                                        MaterialTheme.spacing.hairline,
                                        MaterialTheme.colorScheme.secondary
                                    ) else null,
                                    color = if (mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.align(Alignment.Center)
                                ) {
                                    Text(
                                        emoji + if (count > 1) " $count" else "", Modifier.padding(
                                            horizontal = MaterialTheme.spacing.small,
                                            vertical = MaterialTheme.spacing.tiny
                                        ), style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                }
                if (message.transport == ChatTransport.FAILED && own && !message.hidden) TextButton(
                    onClick = onRetry
                ) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(MaterialTheme.spacing.icon))
                    Spacer(Modifier.width(MaterialTheme.spacing.tiny)); Text("Retry send")
                }
            }
        }
    }
}

@Composable
private fun MessageText(body: String) {
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        textDecoration = TextDecoration.Underline
    )
    val text = remember(body, linkStyle) {
        buildAnnotatedString {
            append(body)
            Regex("https?://[^\\s]+").findAll(body).forEach { match ->
                val url = match.value.trimEnd('.', ',', ')', '!', '?')
                addLink(
                    LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle)),
                    match.range.first,
                    match.range.first + url.length
                )
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun UnavailableMessage(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ChatIcons.Block, null, Modifier.size(MaterialTheme.spacing.icon))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun MessageMetadata(
    message: LocalChatMessage,
    own: Boolean,
    modifier: Modifier = Modifier
) {
    val failed = own && message.transport == ChatTransport.FAILED
    val color =
        if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.chatColors.metadata
    val status =
        if (message.transport == ChatTransport.SENT && message.played) "Played" else messageStatus(
            message
        )
    FlowRow(
        modifier.clearAndSetSemantics {
            contentDescription = chatLocalTime(message.createdAt) + if (own) ", $status" else ""
        }.then(
            if (failed) Modifier.background(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.shapes.extraSmall
            )
                .padding(
                    horizontal = MaterialTheme.spacing.tiny,
                    vertical = MaterialTheme.spacing.hairline
                ) else Modifier
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            chatLocalTime(message.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
        if (own) {
            Icon(
                when (message.transport) {
                    ChatTransport.QUEUED -> Icons.Outlined.Schedule
                    ChatTransport.SENDING -> Icons.Outlined.CloudUpload
                    ChatTransport.FAILED -> Icons.Outlined.ErrorOutline
                    ChatTransport.SENT -> when {
                        message.played -> Icons.Outlined.PlayCircleOutline
                        message.read -> Icons.Outlined.DoneAll
                        message.delivered -> Icons.Outlined.DoneAll
                        else -> Icons.Outlined.Done
                    }
                }, null, Modifier.size(16.dp).align(Alignment.CenterVertically),
                tint = if (message.transport == ChatTransport.SENT && (message.read || message.played)) MaterialTheme.chatColors.receipt else color
            )
            if (message.transport != ChatTransport.SENT) Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
internal fun ChatActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            Modifier.heightIn(min = MaterialTheme.spacing.touchTarget)
                .padding(all = MaterialTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Icon(icon, null, Modifier.size(MaterialTheme.spacing.chromeIcon))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
