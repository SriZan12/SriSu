package com.srisu.srisu.features.home.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.features.home.dashboard.state.HomeChallengeMemberUi
import com.srisu.srisu.features.home.dashboard.state.HomeChallengeStatus
import com.srisu.srisu.features.home.dashboard.state.HomeChallengeUi
import com.srisu.srisu.features.home.dashboard.state.HomePlanStatus
import com.srisu.srisu.features.home.dashboard.state.HomePlanUi
import com.srisu.srisu.features.home.dashboard.state.HomeSection
import com.srisu.srisu.theme.HomeTokens
import com.srisu.srisu.theme.ask
import com.srisu.srisu.theme.reminder
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.theme.success
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

@Composable
fun HomeChallengeSection(
    section: HomeSection<HomeChallengeUi>,
    load: HomeImageLoader,
    onOpen: (id: String?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is HomeSection.Ready -> HomeChallengeCard(section.value, load, { onOpen(section.value.id) }, modifier)
        HomeSection.Loading -> HomePlaceholderCard(modifier)
        HomeSection.Failed -> HomeMessageRow(Icons.Outlined.WbSunny, MaterialTheme.colorScheme.reminder,
            stringResource(Res.string.home_challenge_failed), stringResource(Res.string.home_tap_retry), onRetry, modifier)
        HomeSection.Empty -> HomeMessageRow(Icons.Outlined.WbSunny, MaterialTheme.colorScheme.reminder,
            stringResource(Res.string.home_challenge_empty_title), stringResource(Res.string.home_challenge_empty_body), { onOpen(null) }, modifier)
    }
}

@Composable
fun HomeChallengeCard(challenge: HomeChallengeUi, load: HomeImageLoader, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(MaterialTheme.spacing.large), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            HomeIconPlate(Icons.Outlined.WbSunny, MaterialTheme.colorScheme.reminder, HomeTokens.featureIcon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                Text(challenge.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(MaterialTheme.spacing.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(challenge.status.label), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (challenge.members.isNotEmpty()) HomeStackedAvatars(challenge.members, load)
        }
    }
}

@Composable
fun HomeStackedAvatars(members: List<HomeChallengeMemberUi>, load: HomeImageLoader, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-HomeTokens.stackedOverlap)) {
        members.forEach { member ->
            val name = member.person.name.ifBlank { stringResource(Res.string.home_partner_fallback_title) }
            val description = if (member.confirmed) stringResource(Res.string.home_member_confirmed, name) else name
            Box(Modifier.semantics { contentDescription = description }) {
                HomeAvatar(member.person.photoUrl, load, HomeTokens.stackedAvatar, null,
                    Modifier.border(HomeTokens.momentRing, colors.surfaceContainer, CircleShape))
                if (member.confirmed) Box(Modifier.align(Alignment.BottomStart).offset(y = MaterialTheme.spacing.tiny)
                    .size(HomeTokens.confirmedBadge).clip(CircleShape).background(colors.success), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(HomeTokens.confirmedBadgeIcon), tint = colors.surfaceContainerLowest)
                }
            }
        }
    }
}

@Composable
fun HomePlanSection(
    section: HomeSection<HomePlanUi>,
    onOpen: (id: Long?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is HomeSection.Ready -> HomePlanCard(section.value, { onOpen(section.value.id) }, modifier)
        HomeSection.Loading -> HomePlaceholderCard(modifier)
        HomeSection.Failed -> HomeMessageRow(Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.ask,
            stringResource(Res.string.home_plan_failed), stringResource(Res.string.home_tap_retry), onRetry, modifier)
        HomeSection.Empty -> HomeMessageRow(Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.ask,
            stringResource(Res.string.home_plan_empty_title), stringResource(Res.string.home_plan_empty_body), { onOpen(null) }, modifier)
    }
}

@Composable
fun HomePlanCard(plan: HomePlanUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val detail = stringResource(Res.string.home_plan_separator, planTime(plan.startsAt), stringResource(plan.status.label))
    HomeMessageRow(Icons.Filled.CalendarMonth, MaterialTheme.colorScheme.ask, plan.title, detail, onClick, modifier, plate = false)
}

/** Shared tappable row card: icon, title, supporting line and chevron. */
@Composable
fun HomeMessageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    plate: Boolean = true,
) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            if (plate) HomeIconPlate(icon, accent, HomeTokens.featureIcon)
            else Icon(icon, null, Modifier.size(HomeTokens.featureIcon), tint = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(MaterialTheme.spacing.chromeIcon))
        }
    }
}

@Composable
fun HomePlaceholderCard(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(MaterialTheme.spacing.large).height(HomeTokens.skeletonCard - MaterialTheme.spacing.large * 2),
            verticalArrangement = Arrangement.Center) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun HomeLinkPartnerCard(onFindPartner: () -> Unit, onInvitations: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(MaterialTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            Icon(Icons.Outlined.People, null, tint = MaterialTheme.colorScheme.secondary)
            Text(stringResource(Res.string.home_link_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.home_link_body), style = MaterialTheme.typography.bodyLarge)
            SriSuButton(stringResource(Res.string.home_link_find), onFindPartner)
            TextButton(onClick = onInvitations) { Text(stringResource(Res.string.home_link_invitations)) }
        }
    }
}

@Composable
fun HomeErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.home_retry)) }
        }
    }
}

@Composable
private fun planTime(value: LocalDateTime?): String {
    if (value == null) return stringResource(Res.string.home_time_unknown)
    val day = stringResource(Res.string.home_plan_when, stringResource(value.dayOfWeek.label), value.day)
    val hour = (value.hour % 12).let { if (it == 0) 12 else it }
    val minute = value.minute.toString().padStart(2, '0')
    val time = stringResource(if (value.hour < 12) Res.string.home_time_am else Res.string.home_time_pm, hour, minute)
    return stringResource(Res.string.home_plan_separator, day, time)
}

private val DayOfWeek.label: StringResource get() = when (this) {
    DayOfWeek.MONDAY -> Res.string.home_weekday_mon
    DayOfWeek.TUESDAY -> Res.string.home_weekday_tue
    DayOfWeek.WEDNESDAY -> Res.string.home_weekday_wed
    DayOfWeek.THURSDAY -> Res.string.home_weekday_thu
    DayOfWeek.FRIDAY -> Res.string.home_weekday_fri
    DayOfWeek.SATURDAY -> Res.string.home_weekday_sat
    DayOfWeek.SUNDAY -> Res.string.home_weekday_sun
}

private val HomeChallengeStatus.label: StringResource get() = when (this) {
    HomeChallengeStatus.INVITED -> Res.string.home_challenge_invited
    HomeChallengeStatus.ACTIVE -> Res.string.home_challenge_active
    HomeChallengeStatus.ONE_CONFIRMED -> Res.string.home_challenge_one_confirmed
    HomeChallengeStatus.PAUSED -> Res.string.home_challenge_paused
    HomeChallengeStatus.COMPLETED -> Res.string.home_challenge_completed
    HomeChallengeStatus.ENDED -> Res.string.home_challenge_ended
}

private val HomePlanStatus.label: StringResource get() = when (this) {
    HomePlanStatus.BOTH_GOING -> Res.string.home_plan_both_going
    HomePlanStatus.AWAITING_REPLY -> Res.string.home_plan_awaiting
    HomePlanStatus.CHANGE_SUGGESTED -> Res.string.home_plan_change
    HomePlanStatus.UPCOMING -> Res.string.home_plan_upcoming
}
