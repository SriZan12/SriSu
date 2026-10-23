@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*

@Composable
internal fun ParticipantAvatar(
    name: String,
    photo: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) {
        name.split(' ').filter(String::isNotBlank).take(2)
            .mapNotNull { it.firstOrNull(Char::isLetterOrDigit)?.toString() }.joinToString("")
            .uppercase()
    }
    Surface(
        modifier = modifier.semantics { contentDescription = "$name, partner avatar" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (photo != null) Image(
                photo,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            else if (initials.isNotEmpty()) Text(
                initials.take(1),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            else Icon(Icons.Outlined.Person, null, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun QuotePreview(
    quote: ChatQuote, accountId: Long?, partner: String, modifier: Modifier = Modifier,
    onOriginal: (() -> Unit)? = null, onCancel: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary)
            )
            Column(Modifier.weight(1f).padding(MaterialTheme.spacing.small)) {
                val sender = if (quote.authorId == accountId) "You" else partner
                Text(
                    if (onCancel != null) "Replying to $sender" else sender,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing),
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    quote.preview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (onCancel != null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onCancel != null) IconButton(onClick = onCancel) {
                Icon(
                    Icons.Outlined.Close,
                    "Cancel reply"
                )
            }
        }
    }
    if (onOriginal != null && quote.preview != "Message unavailable") Surface(
        onClick = onOriginal,
        modifier = modifier.semantics { contentDescription = "Quoted message. Open original" },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .04f),
        content = content
    )
    else Surface(
        modifier = modifier, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow, content = content
    )
}

@Composable
internal fun ConversationComposer(
    state: ConversationState, busy: Boolean, recording: Boolean,
    onDraft: (String) -> Unit, onSend: () -> Unit, onAttach: () -> Unit, onCancelReply: () -> Unit,
    voice: @Composable () -> Unit, extras: @Composable ColumnScope.() -> Unit = {}
) {
    val inputFocus = remember { FocusRequester() }
    var previousReply by remember { mutableStateOf(state.reply?.messageId) }
    LaunchedEffect(state.reply?.messageId) {
        if (state.reply != null && state.reply.messageId != previousReply && !recording) inputFocus.requestFocus()
        previousReply = state.reply?.messageId
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints {
            val compact = maxHeight < 420.dp
            val previewHeight = (maxHeight * .25f).coerceAtMost(200.dp)
            Column(
                Modifier.navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Column(
                    Modifier.widthIn(max = 840.dp).fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(
                            horizontal = MaterialTheme.spacing.compact,
                            vertical = MaterialTheme.spacing.small
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    if (recording || busy || state.mediaDraft != null || state.reply != null) {
                        Column(
                            Modifier.heightIn(max = previewHeight)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                        ) {
                            extras()
                            state.reply?.let {
                                QuotePreview(
                                    it,
                                    state.accountId,
                                    state.preferences.nickname.ifBlank { state.partnerName },
                                    onCancel = onCancelReply
                                )
                            }
                        }
                    }
                    val largeType = LocalDensity.current.fontScale > 1.3f
                    val input: @Composable (Modifier) -> Unit = { modifier ->
                        BasicTextField(
                            value = state.draft,
                            onValueChange = onDraft,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            enabled = !recording,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            maxLines = if (compact || largeType) 2 else 5,
                            modifier = modifier.heightIn(min = MaterialTheme.spacing.touchTarget)
                                .focusRequester(inputFocus)
                                .semantics { contentDescription = "Message" }
                                .padding(
                                    horizontal = MaterialTheme.spacing.compact,
                                    vertical = MaterialTheme.spacing.compact
                                ),
                            decorationBox = { input ->
                                Box {
                                    if (recording || state.draft.isEmpty()) Text(
                                        if (recording) "Recording a voice message…" else if (state.mediaDraft == null) "Message" else "Add a caption…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!recording) input()
                                }
                            })
                    }
                    val attach: @Composable () -> Unit = {
                        IconButton(
                            onClick = onAttach,
                            enabled = !busy && !recording
                        ) { Icon(Icons.Outlined.Add, "Attach or share") }
                    }
                    val submit: @Composable () -> Unit = {
                        if (state.draft.isBlank() && state.mediaDraft == null) voice()
                        else FilledIconButton(
                            onClick = onSend,
                            enabled = !busy && !recording,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(MaterialTheme.spacing.touchTarget)
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send message") }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        if (largeType) Column {
                            input(Modifier.fillMaxWidth())
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.spacing.tiny),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) { attach(); submit() }
                        } else Row(
                            Modifier.fillMaxWidth().padding(MaterialTheme.spacing.tiny),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            attach()
                            input(Modifier.weight(1f))
                            submit()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationEmpty(
    connected: Boolean, onFindPartner: () -> Unit, onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, composing: Boolean = false
) {
    Column(
        modifier.padding(MaterialTheme.spacing.large), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (composing && connected) {
            Text(
                "Your conversation starts here.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            return@Column
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    ChatIcons.Forum, null,
                    Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.height(MaterialTheme.spacing.large))
        Text(
            "Just the two of you",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            if (connected) "A thought, a story, a little part of your day.\nStart your conversation here."
            else "Connect with your partner to start your private conversation.",
            Modifier.padding(vertical = MaterialTheme.spacing.compact),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (connected) SuggestionChip(
            onClick = { onSuggestion("What made you smile today?") }, enabled = enabled,
            label = { Text("What made you smile today?", textAlign = TextAlign.Center) })
        else Button(onClick = onFindPartner) { Text("Find your partner") }
    }
}

@Composable
internal fun ChatDateDivider(value: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.pill,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Text(
                chatDateLabel(value),
                Modifier.padding(
                    horizontal = MaterialTheme.spacing.compact,
                    vertical = MaterialTheme.spacing.tiny
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
