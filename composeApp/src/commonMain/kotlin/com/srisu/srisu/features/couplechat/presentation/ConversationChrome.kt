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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.core.data.remote.SocketState
import com.srisu.srisu.theme.*

internal val ConversationState.canCompose: Boolean
    get() = roomId != null && security in setOf(ConversationSecurity.READY,
        ConversationSecurity.PARTNER_SETUP, ConversationSecurity.SETTING_UP)

internal fun conversationSubtitle(state: ConversationState, connection: SocketState? = null): String = when {
    state.loading && state.roomId == null -> "Opening your conversation…"
    state.roomId == null -> "A space for the two of you"
    !state.canCompose -> securityLabel(state.security)
    connection is SocketState.Reconnecting -> "Reconnecting…"
    connection is SocketState.Connecting -> "Connecting…"
    connection is SocketState.Disconnected -> "Live updates paused"
    connection is SocketState.Terminal -> "Live updates unavailable"
    state.security == ConversationSecurity.READY && state.preferences.presence &&
        state.partnerActivity.roomId == state.roomId && state.partnerActivity.typing -> "Typing…"
    state.security == ConversationSecurity.READY && state.preferences.presence &&
        state.partnerActivity.roomId == state.roomId && state.partnerActivity.online -> "Active now"
    else -> securityLabel(state.security)
}

@Composable
internal fun ConversationHeader(state: ConversationState, onBack: () -> Unit, onOptions: () -> Unit, connection: SocketState? = null) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().widthIn(max = 840.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = MaterialTheme.spacing.tiny, vertical = MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                val name = state.preferences.nickname.ifBlank { state.partnerName }
                val initials = name.split(' ').filter(String::isNotBlank).take(2)
                    .mapNotNull { part -> part.firstOrNull(Char::isLetterOrDigit)?.toString() }.joinToString("").uppercase()
                // The chat contract has no photo URL. A name-derived avatar does not invent one.
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp).clearAndSetSemantics {}) {
                    Box(contentAlignment = Alignment.Center) {
                        if (initials.isEmpty()) Icon(Icons.Outlined.Person, null)
                        else Text(initials,
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Column(Modifier.weight(1f).padding(start = MaterialTheme.spacing.compact)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(conversationSubtitle(state, connection), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onOptions, enabled = state.roomId != null) {
                    Icon(ChatIcons.MoreHoriz, "Conversation options")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
    }
}

@Composable
internal fun QuotePreview(quote: ChatQuote, accountId: Long?, partner: String, modifier: Modifier = Modifier,
    onOriginal: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
            Column(Modifier.weight(1f).padding(MaterialTheme.spacing.small)) {
                Text(if (quote.authorId == accountId) "You" else partner,
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(quote.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onCancel != null) IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "Cancel reply") }
        }
    }
    if (onOriginal != null) Surface(onClick = onOriginal, modifier = modifier.semantics { contentDescription = "Quoted message. Open original" },
        shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = .65f), content = content)
    else Surface(modifier = modifier, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow, content = content)
}

@Composable
internal fun ConversationComposer(state: ConversationState, busy: Boolean, recording: Boolean,
    onDraft: (String) -> Unit, onSend: () -> Unit, onAttach: () -> Unit, onCancelReply: () -> Unit,
    voice: @Composable () -> Unit, extras: @Composable ColumnScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints {
        val compact = maxHeight < 420.dp
        val previewHeight = (maxHeight * .25f).coerceAtMost(200.dp)
        Column(Modifier.navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .padding(horizontal = MaterialTheme.spacing.compact, vertical = MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                if (recording || busy || state.mediaDraft != null || state.reply != null) {
                    Column(Modifier.heightIn(max = previewHeight).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                        extras()
                        state.reply?.let { QuotePreview(it, state.accountId, state.partnerName, onCancel = onCancelReply) }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    IconButton(onClick = onAttach, enabled = !busy && !recording) {
                        Icon(Icons.Outlined.Add, "Attach or share")
                    }
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f)) {
                        BasicTextField(value = state.draft, onValueChange = onDraft,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), enabled = !recording,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            maxLines = if (compact) 2 else if (LocalDensity.current.fontScale > 1.3f) 3 else 5,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .semantics { contentDescription = "Message" }
                                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.compact),
                            decorationBox = { input -> Box {
                                if (state.draft.isEmpty()) Text(if (state.mediaDraft == null) "Message" else "Add a caption…",
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                input()
                            } })
                    }
                    if (state.draft.isBlank() && state.mediaDraft == null) voice()
                    else FilledIconButton(onClick = onSend, enabled = !busy && !recording,
                        modifier = Modifier.size(MaterialTheme.spacing.touchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send message")
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun ConversationNotice(state: ConversationState, onRetry: () -> Unit, onSecurityAction: () -> Unit) {
    val text = state.error ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.small,
            top = MaterialTheme.spacing.tiny, bottom = MaterialTheme.spacing.tiny), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(MaterialTheme.spacing.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
            val action = when (state.security) {
                ConversationSecurity.REPLACE_DEVICE -> "Use device"
                ConversationSecurity.IDENTITY_CHANGED -> "Verify"
                ConversationSecurity.KEY_LOST, ConversationSecurity.STORAGE_ERROR -> "Recover"
                else -> null
            }
            TextButton(onClick = if (action == null) onRetry else onSecurityAction) { Text(action ?: "Retry") }
        }
    }
}

@Composable
internal fun ConversationEmpty(connected: Boolean, onFindPartner: () -> Unit, onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.padding(MaterialTheme.spacing.large), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(ChatIcons.Forum, null,
                Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(Modifier.height(MaterialTheme.spacing.large))
        Text("Just the two of you", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(if (connected) "A thought, a story, a little part of your day.\nStart your conversation here."
            else "Connect with your partner to start your private conversation.",
            Modifier.padding(vertical = MaterialTheme.spacing.compact), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (connected) SuggestionChip(onClick = { onSuggestion("What made you smile today?") }, enabled = enabled,
            label = { Text("What made you smile today?", textAlign = TextAlign.Center) })
        else Button(onClick = onFindPartner) { Text("Find your partner") }
    }
}

@Composable internal fun ChatDateDivider(value: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.pill, color = MaterialTheme.colorScheme.surfaceContainer) {
            Text(chatDateLabel(value), Modifier.padding(horizontal = MaterialTheme.spacing.compact, vertical = MaterialTheme.spacing.tiny),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
