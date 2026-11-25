package com.srisu.srisu.features.home.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.srisu.srisu.features.home.dashboard.state.HomeConversationUi
import com.srisu.srisu.features.home.dashboard.state.HomePersonUi
import com.srisu.srisu.theme.HomeTokens
import com.srisu.srisu.theme.ask
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.planner
import com.srisu.srisu.theme.reminder
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.spark
import com.srisu.srisu.theme.success
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

/** "From partner" panel: latest message card, reply entry point and quick actions. */
@Composable
fun HomeConversationPanel(
    partner: HomePersonUi,
    conversation: HomeConversationUi?,
    load: HomeImageLoader,
    onOpenChat: () -> Unit,
    onSpark: () -> Unit,
    onAsk: () -> Unit,
    onPlanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val partnerName = partner.name.ifBlank { stringResource(Res.string.home_partner_fallback_title) }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(MaterialTheme.spacing.compact), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
            HomeMessageCard(partner.copy(name = partnerName), conversation, load, onOpenChat)
            HomeReplyField(partnerName, onOpenChat)
            HomeQuickActions(onSpark, onAsk, onPlanner)
        }
    }
}

@Composable
fun HomeMessageCard(
    partner: HomePersonUi,
    conversation: HomeConversationUi?,
    load: HomeImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLowest) {
        Row(Modifier.padding(MaterialTheme.spacing.medium), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
            Box {
                HomeAvatar(partner.photoUrl, load, HomeTokens.messageAvatar, null)
                if (conversation?.partnerOnline == true) {
                    val online = stringResource(Res.string.home_online)
                    Box(Modifier.align(Alignment.BottomEnd).size(HomeTokens.presenceDot)
                        .border(HomeTokens.presenceRing, colors.surfaceContainerLowest, CircleShape)
                        .clip(CircleShape).background(colors.success).semantics { contentDescription = online })
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    Text(partner.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    if (conversation != null) {
                        Text(conversation.sentAt, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        if (conversation.unread > 0) HomeUnreadBadge(conversation.unread)
                    }
                }
                Text(conversation?.text ?: stringResource(Res.string.home_conversation_empty), style = MaterialTheme.typography.bodyLarge,
                    color = if (conversation == null) colors.onSurfaceVariant else colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val photos = conversation?.photoUrls.orEmpty()
                if (photos.isNotEmpty()) {
                    val photo = stringResource(Res.string.home_shared_photo)
                    LazyRow(Modifier.padding(top = MaterialTheme.spacing.tiny), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                        itemsIndexed(photos, key = { index, url -> "$index:$url" }) { _, url ->
                            HomePhoto(url, load, HomeTokens.thumbnail, photo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeUnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.home_unread, count)
    Box(modifier.heightIn(min = HomeTokens.unreadBadge).widthIn(min = HomeTokens.unreadBadge).clip(MaterialTheme.shapes.pill)
        .background(MaterialTheme.colorScheme.reminder).semantics { contentDescription = description }
        .padding(horizontal = MaterialTheme.spacing.tiny), contentAlignment = Alignment.Center) {
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surfaceContainerLowest)
    }
}

/** Pill entry point into the conversation; sending happens in the chat screen. */
@Composable
fun HomeReplyField(partnerName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val hint = stringResource(Res.string.home_reply_to, partnerName)
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().semantics { role = Role.Button }, shape = MaterialTheme.shapes.pill,
        color = colors.surfaceContainerLowest) {
        Row(Modifier.heightIn(min = MaterialTheme.spacing.fieldHeight).padding(start = MaterialTheme.spacing.gutter, end = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically) {
            Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Box(Modifier.size(MaterialTheme.spacing.touchTarget).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Send, stringResource(Res.string.home_send), Modifier.size(MaterialTheme.spacing.chromeIcon),
                    tint = colors.onPrimary)
            }
        }
    }
}

@Composable
fun HomeQuickActions(onSpark: () -> Unit, onAsk: () -> Unit, onPlanner: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val stacked = LocalDensity.current.fontScale > HomeTokens.stackActionsFontScale
    val actions: @Composable (Modifier) -> Unit = { item ->
        HomeQuickAction(stringResource(Res.string.home_action_spark), Icons.Outlined.AutoAwesome, colors.spark, onSpark, item)
        HomeQuickAction(stringResource(Res.string.home_action_ask), Icons.Outlined.HelpOutline, colors.ask, onAsk, item)
        HomeQuickAction(stringResource(Res.string.home_action_planner), Icons.Outlined.CalendarToday, colors.planner, onPlanner, item)
    }
    if (stacked) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) { actions(Modifier.fillMaxWidth()) }
    else Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) { actions(Modifier.weight(1f)) }
}

@Composable
fun HomeQuickAction(label: String, icon: ImageVector, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.pill, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.heightIn(min = MaterialTheme.spacing.touchTarget)
            .padding(start = MaterialTheme.spacing.tiny, end = MaterialTheme.spacing.small, top = MaterialTheme.spacing.tiny, bottom = MaterialTheme.spacing.tiny),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
            HomeIconPlate(icon, accent, HomeTokens.actionIcon, glyph = HomeTokens.actionIconGlyph)
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
