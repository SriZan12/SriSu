package com.srisu.srisu.features.home.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.srisu.srisu.features.home.dashboard.state.HomeDaypart
import com.srisu.srisu.features.home.dashboard.state.HomeMomentUi
import com.srisu.srisu.theme.HomeTokens
import com.srisu.srisu.theme.SriSuPartnerLinkTypography
import com.srisu.srisu.theme.moments
import com.srisu.srisu.theme.reminder
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

@Composable
fun HomeHeader(
    daypart: HomeDaypart,
    name: String,
    partnerName: String?,
    daysTogether: Int?,
    hasAlerts: Boolean,
    onCoupleProfile: () -> Unit,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting = stringResource(when (daypart) {
        HomeDaypart.MORNING -> Res.string.home_greeting_morning
        HomeDaypart.AFTERNOON -> Res.string.home_greeting_afternoon
        HomeDaypart.EVENING -> Res.string.home_greeting_evening
    })
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(if (name.isBlank()) greeting else stringResource(Res.string.home_greeting_named, greeting, name),
                style = SriSuPartnerLinkTypography().headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            if (partnerName != null) HomeTogetherLine(partnerName, daysTogether, onCoupleProfile)
        }
        val alerts = stringResource(if (hasAlerts) Res.string.home_notifications_new else Res.string.home_notifications)
        IconButton(onClick = onNotifications, modifier = Modifier.semantics { contentDescription = alerts }) {
            Box {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(MaterialTheme.spacing.chromeIcon))
                if (hasAlerts) Box(Modifier.align(Alignment.TopEnd).size(HomeTokens.alertDot).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.reminder))
            }
        }
    }
}

@Composable
private fun HomeTogetherLine(partnerName: String, daysTogether: Int?, onClick: () -> Unit) {
    val partner = partnerName.ifBlank { stringResource(Res.string.home_partner_fallback) }
    val label = if (daysTogether == null) stringResource(Res.string.home_space_with, partner)
        else stringResource(Res.string.home_day_with, daysTogether, partner)
    val action = stringResource(Res.string.home_open_profile)
    Row(Modifier.clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
        .minimumInteractiveComponentSize().padding(end = MaterialTheme.spacing.tiny),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(MaterialTheme.spacing.icon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HomeMomentsRow(
    moments: List<HomeMomentUi>,
    fresh: Boolean,
    load: HomeImageLoader,
    onOpen: (personId: Long) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        items(moments, key = { it.person.id }) { moment ->
            HomeMomentAvatar(moment, fresh, load, onOpen = { onOpen(moment.person.id) }, onCreate = onCreate)
        }
    }
}

@Composable
fun HomeMomentAvatar(
    moment: HomeMomentUi,
    fresh: Boolean,
    load: HomeImageLoader,
    onOpen: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val name = if (moment.own) stringResource(Res.string.home_your_moment)
        else moment.person.name.ifBlank { stringResource(Res.string.home_partner_fallback_title) }
    val description = when {
        !fresh -> stringResource(Res.string.home_moment_refresh, name)
        moment.activeCount == 0 && moment.own -> stringResource(Res.string.home_moment_add_first, name)
        moment.activeCount == 0 -> stringResource(Res.string.home_moment_none, name)
        moment.hasUnseen -> stringResource(Res.string.home_moment_new, name, moment.unseenCount)
        else -> stringResource(Res.string.home_moment_seen, name)
    }
    val enabled = fresh && (moment.own || moment.activeCount > 0)
    Column(modifier.width(HomeTokens.momentLabelWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(HomeTokens.momentAvatar)) {
            val ring = if (moment.hasUnseen && fresh) colors.moments else colors.outlineVariant
            Box(Modifier.fillMaxSize().clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button) { if (moment.activeCount > 0) onOpen() else onCreate() }
                .semantics(mergeDescendants = true) { contentDescription = description }
                .border(HomeTokens.momentRing, ring, CircleShape).padding(HomeTokens.momentRingInset)) {
                HomeAvatar(moment.person.photoUrl, load, HomeTokens.momentAvatar, null)
            }
            if (moment.own) {
                val add = stringResource(Res.string.home_add_moment)
                Box(Modifier.align(Alignment.BottomEnd).offset(x = MaterialTheme.spacing.tiny, y = MaterialTheme.spacing.tiny)
                    .clip(CircleShape).clickable(enabled = fresh, role = Role.Button, onClick = onCreate)
                    .semantics { contentDescription = add }.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(HomeTokens.momentBadge).border(HomeTokens.momentRing, colors.surface, CircleShape)
                        .clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(HomeTokens.momentBadgeIcon), tint = colors.onPrimary)
                    }
                }
            }
        }
        Text(name, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (moment.own) colors.onSurfaceVariant else colors.onSurface,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small))
    }
}
