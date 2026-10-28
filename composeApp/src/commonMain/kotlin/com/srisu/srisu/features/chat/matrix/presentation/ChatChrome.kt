package com.srisu.srisu.features.chat.matrix.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.srisu.srisu.theme.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.features.chat.matrix.MatrixPartnerPresence
import srisu.composeapp.generated.resources.chat_partner_online
import srisu.composeapp.generated.resources.chat_partner_offline
import srisu.composeapp.generated.resources.chat_partner_away
import srisu.composeapp.generated.resources.chat_partner_status_unavailable
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.theme.ChatTokens
import com.srisu.srisu.theme.caret
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.chat_secure_back
import srisu.composeapp.generated.resources.chat_secure_empty_body
import srisu.composeapp.generated.resources.chat_secure_empty_title
import srisu.composeapp.generated.resources.chat_secure_encrypted
import srisu.composeapp.generated.resources.chat_secure_encrypted_offline
import srisu.composeapp.generated.resources.chat_secure_header_unavailable
import srisu.composeapp.generated.resources.chat_secure_jump_latest
import srisu.composeapp.generated.resources.chat_secure_loading
import srisu.composeapp.generated.resources.chat_secure_message_hint
import srisu.composeapp.generated.resources.chat_secure_message_partner
import srisu.composeapp.generated.resources.chat_secure_new_messages
import srisu.composeapp.generated.resources.chat_secure_retry
import srisu.composeapp.generated.resources.chat_secure_send
import srisu.composeapp.generated.resources.chat_secure_sending
import srisu.composeapp.generated.resources.chat_secure_starter_dinner
import srisu.composeapp.generated.resources.chat_secure_starter_smile
import srisu.composeapp.generated.resources.chat_secure_starter_song
import srisu.composeapp.generated.resources.chat_secure_starters_label
import srisu.composeapp.generated.resources.chat_secure_title
import srisu.composeapp.generated.resources.chat_secure_verifying
import srisu.composeapp.generated.resources.chat_offline
import srisu.composeapp.generated.resources.partner_link_back

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatHeader(
    partner: ChatPartnerIdentity?,
    availability: MatrixChatAvailability,
    onBack: () -> Unit,
    presence: MatrixPartnerPresence = MatrixPartnerPresence.UNKNOWN,
    onAppearance: (() -> Unit)? = null,
) {
    val status = if (availability !is MatrixChatAvailability.Ready) MatrixPartnerPresence.UNKNOWN else presence
    val statusLabel = when (status) {
        MatrixPartnerPresence.ONLINE -> Res.string.chat_partner_online
        MatrixPartnerPresence.OFFLINE -> Res.string.chat_partner_offline
        MatrixPartnerPresence.AWAY -> Res.string.chat_partner_away
        MatrixPartnerPresence.UNKNOWN -> Res.string.chat_partner_status_unavailable
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(PureChatTokens.control)) {
                Icon(PureChatIcons.Back, stringResource(Res.string.chat_secure_back), Modifier.size(PureChatTokens.icon))
            }
            Box(Modifier.weight(1f).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    if (partner != null) PartnerAvatar(partner, ChatTokens.headerAvatar, pureChatSerif(avatar = true))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(partner?.name ?: stringResource(Res.string.chat_secure_title),
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(statusLabel), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.chatColors.metadata, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (onAppearance != null) IconButton(onClick = onAppearance, modifier = Modifier.size(PureChatTokens.control)) {
                Icon(com.srisu.srisu.features.couplechat.presentation.ChatIcons.MoreHoriz, "Chat appearance", Modifier.size(PureChatTokens.icon))
            } else Spacer(Modifier.size(PureChatTokens.control))
        }
    }
}

/** Partner photo with an initials fallback that stays visible until (or unless) the photo loads. */
@Composable
internal fun PartnerAvatar(partner: ChatPartnerIdentity, size: androidx.compose.ui.unit.Dp, initialsStyle: TextStyle) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (MaterialTheme.chatColors.pure) MaterialTheme.chatColors.incoming else MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // Initials are decorative inside a fixed circle, so they follow the avatar size, not font scale.
        val initialsSize = with(LocalDensity.current) { (size * .65625f).toSp() }
        Text(
            partner.name.split(' ').filter(String::isNotBlank).take(1).joinToString("") { it.take(1).uppercase() },
            style = initialsStyle.copy(fontSize = initialsSize, lineHeight = initialsSize),
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        partner.photoUrl?.let { url ->
            AsyncImage(
                model = url,
                // Decorative: the partner's name is always shown beside or below the avatar.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun ChatEmptyState(
    partner: ChatPartnerIdentity?,
    starterEnabled: Boolean,
    onStarter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val starters = listOf(
        stringResource(Res.string.chat_secure_starter_smile),
        stringResource(Res.string.chat_secure_starter_dinner),
        stringResource(Res.string.chat_secure_starter_song),
    )
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (partner != null) {
                PartnerAvatar(partner, ChatTokens.emptyAvatar, MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(MaterialTheme.spacing.medium))
            }
            Text(
                stringResource(Res.string.chat_secure_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            Text(
                stringResource(Res.string.chat_secure_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
        val startersLabel = stringResource(Res.string.chat_secure_starters_label)
        Column(
            Modifier.fillMaxWidth().semantics { contentDescription = startersLabel },
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            starters.forEach { starter ->
                Surface(
                    onClick = { onStarter(starter) },
                    enabled = starterEnabled,
                    shape = MaterialTheme.shapes.pill,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.heightIn(min = MaterialTheme.spacing.touchTarget - MaterialTheme.spacing.small),
                ) {
                    Text(
                        starter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = ChatTokens.bubbleHorizontalPadding,
                            vertical = MaterialTheme.spacing.small,
                        ),
                    )
                }
            }
        }
    }
}

/** Placeholder bubbles keep the conversation's shape while the secure room opens. */
@Composable
internal fun ChatLoadingState(modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact, Alignment.Bottom),
    ) {
        listOf(0.55f to false, 0.4f to false, 0.62f to true, 0.3f to true, 0.5f to false).forEach { (fraction, mine) ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(ChatTokens.composerMinHeight - MaterialTheme.spacing.small)
                        .clip(ChatTokens.roundedShape)
                        .background(placeholder),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(ChatTokens.metaIcon),
                strokeWidth = MaterialTheme.spacing.hairline * 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.chat_secure_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ChatStatusState(
    title: String,
    message: String,
    icon: Painter? = null,
    vector: ImageVector? = null,
    action: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(MaterialTheme.spacing.gutter), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = ChatTokens.bubbleMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(ChatTokens.statusIconContainer)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                when {
                    icon != null -> Icon(icon, null, tint = tint)
                    vector != null -> Icon(vector, null, tint = tint)
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.medium))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(MaterialTheme.spacing.tiny))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(MaterialTheme.spacing.large))
                SriSuButton(stringResource(Res.string.chat_secure_retry), action)
            }
        }
    }
}

@Composable
internal fun ChatOfflineBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact),
        ) {
            Icon(
                painterResource(Res.drawable.chat_offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(MaterialTheme.spacing.icon),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(vertical = MaterialTheme.spacing.compact),
            )
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.chat_secure_retry)) }
        }
    }
}

@Composable
internal fun ChatDayHeader(label: String) {
    Box(
        Modifier.fillMaxWidth().padding(top = 21.dp, bottom = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
internal fun JumpToLatestButton(newCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BadgedBox(
        modifier = modifier,
        badge = {
            if (newCount > 0) Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(stringResource(Res.string.chat_secure_new_messages, newCount)) }
        },
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = MaterialTheme.spacing.tiny / 2,
            modifier = Modifier
                .size(ChatTokens.jumpButton)
                .border(MaterialTheme.spacing.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.chat_secure_jump_latest),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    partnerName: String?,
    editable: Boolean,
    canSend: Boolean,
    sending: Boolean,
    helper: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                start = MaterialTheme.spacing.compact,
                end = MaterialTheme.spacing.compact,
                top = MaterialTheme.spacing.small,
                bottom = MaterialTheme.spacing.small,
            ),
    ) {
        helper?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.tiny,
                ),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            val placeholder = partnerName?.let { stringResource(Res.string.chat_secure_message_partner, it) }
                ?: stringResource(Res.string.chat_secure_message_hint)
            val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = editable,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.caret),
                maxLines = ChatTokens.composerMaxLines,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ChatTokens.composerMinHeight)
                    .semantics { contentDescription = placeholder },
                decorationBox = { field ->
                    Box(
                        Modifier
                            .clip(ChatTokens.roundedShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(MaterialTheme.spacing.hairline, MaterialTheme.colorScheme.outlineVariant, ChatTokens.roundedShape)
                            .heightIn(min = ChatTokens.composerMinHeight)
                            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.compact),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) Text(
                            placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        field()
                    }
                },
            )
            Box(Modifier.height(ChatTokens.composerMinHeight), contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend && !sending,
                    modifier = Modifier.size(ChatTokens.sendButton),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    if (sending) {
                        val sendingLabel = stringResource(Res.string.chat_secure_sending)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(MaterialTheme.spacing.icon)
                                .semantics { contentDescription = sendingLabel },
                            strokeWidth = MaterialTheme.spacing.hairline * 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(Res.string.chat_secure_send),
                            modifier = Modifier.size(MaterialTheme.spacing.icon).padding(start = MaterialTheme.spacing.hairline),
                        )
                    }
                }
            }
        }
    }
}
