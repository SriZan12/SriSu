package com.srisu.srisu.features.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.theme.*
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel

@Composable fun NotificationInboxScreen(onBack: () -> Unit, onSettings: () -> Unit, onOpen: (Route) -> Unit,
    vm: NotificationInboxViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MomentLifecycle(vm::active)
    com.srisu.srisu.features.couplechat.presentation.ProtectConversation()
    LaunchedEffect(state.navigation) { state.navigation?.let { route -> vm.navigated(); onOpen(route) } }
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) { while(true) { delay(60_000); now=Clock.System.now() } }
    NotificationInboxContent(state,onBack,onSettings,vm::refresh,vm::more,vm::filter,vm::readAll,vm::open,now)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NotificationInboxContent(state: InboxState, onBack: () -> Unit = {},
    onSettings: () -> Unit = {}, onRefresh: () -> Unit = {}, onMore: () -> Unit = {},
    onFilter: (Boolean) -> Unit = {}, onReadAll: () -> Unit = {}, onOpen: (InboxItem) -> Unit = {},
    now: Instant = Clock.System.now(), zone: TimeZone = TimeZone.currentSystemDefault()) {
    val colors=MaterialTheme.colorScheme
    val space=MaterialTheme.spacing
    val list=rememberLazyListState()
    LaunchedEffect(state.unreadOnly) { list.scrollToItem(0) }
    Scaffold(containerColor=colors.surface, topBar={
        TopAppBar(title={ Text("Notifications",style=MaterialTheme.typography.titleLarge) },
            navigationIcon={ IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back") } },
            actions={
                IconButton(onClick=onRefresh,enabled=!state.loading && state.busy==null) { Icon(Icons.Outlined.Refresh,"Refresh notifications") }
                IconButton(onClick=onSettings) { Icon(Icons.Outlined.Tune,"Notification settings") }
            }, colors=TopAppBarDefaults.topAppBarColors(containerColor=colors.surface))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=680.dp).fillMaxSize().testTag("notification-list"),state=list,
                contentPadding=PaddingValues(horizontal=space.gutter,vertical=space.small),
                verticalArrangement=Arrangement.spacedBy(space.small)) {
                item("intro") {
                    Column(Modifier.padding(bottom=space.small),verticalArrangement=Arrangement.spacedBy(space.tiny)) {
                        Text("Your shared activity",style=MaterialTheme.typography.headlineMedium)
                        Text(if(state.loaded) "${state.unreadCount} unread · Last 30 days" else "Plans, questions, moments and more",
                            style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    }
                }
                item("controls") {
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(space.small),
                        verticalArrangement=Arrangement.spacedBy(space.tiny)) {
                        FilterChip(selected=!state.unreadOnly,onClick={onFilter(false)},enabled=state.busy==null,label={Text("All")})
                        FilterChip(selected=state.unreadOnly,onClick={onFilter(true)},enabled=state.busy==null,label={Text("Unread")})
                        TextButton(onClick=onReadAll,enabled=state.loaded && state.unreadCount>0 && state.readToken!=null &&
                            state.busy==null && !state.loading && !state.loadingMore) {
                            Icon(Icons.Outlined.DoneAll,null,Modifier.size(space.chromeIcon))
                            Spacer(Modifier.width(space.tiny));Text("Mark all read")
                        }
                    }
                }
                if(state.loading || state.busy=="read-all") item("loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription="Loading notifications" })
                }
                state.notice?.let { notice -> item("notice") {
                    Text(notice,Modifier.padding(vertical=space.small).semantics { liveRegion=LiveRegionMode.Polite },
                        style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                } }
                state.error?.let { error -> item("error") { InboxError(error,onRefresh) } }
                if(state.loaded && !state.loading && state.items.isEmpty() && state.error==null) item("empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical=space.section),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(space.medium)) {
                        Surface(shape=CircleShape,color=colors.secondaryContainer) {
                            Icon(if(state.unreadOnly) Icons.Outlined.DoneAll else Icons.Outlined.NotificationsNone,null,
                                Modifier.padding(space.large).size(space.touchTarget),tint=colors.onSecondaryContainer)
                        }
                        Text(if(state.unreadOnly) "You’re all caught up" else "A little quiet here",
                            style=MaterialTheme.typography.titleLarge)
                        Text(if(state.unreadOnly) "No unread updates. Your shared activity is still in All."
                            else "Invitations, plans, questions and shared moments will appear here.",
                            style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant,
                            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        if(state.unreadOnly) TextButton(onClick={onFilter(false)}) { Text("View all activity") }
                    }
                }
                var previous: String?=null
                state.items.forEach { row ->
                    val day=inboxDay(row.createdAt,now,zone)
                    if(day!=previous) {
                        item("day:${row.id}") { Text(day,Modifier.padding(top=space.medium,bottom=space.tiny).semantics { heading() },
                            style=MaterialTheme.typography.titleSmall,color=colors.onSurfaceVariant) }
                        previous=day
                    }
                    item(row.id) { InboxRow(row,now,state.busy==row.id,state.busy==null) { onOpen(row) } }
                }
                state.pageError?.let { message -> item("page-error") { InboxError(message,onMore) } }
                if(state.nextCursor!=null) item("more") {
                    if(state.loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else OutlinedButton(onClick=onMore,enabled=state.busy==null && !state.loading,modifier=Modifier.fillMaxWidth()) { Text("Load earlier updates") }
                }
                if(state.loaded) item("footer") { Text("Messages stay in Chats.",Modifier.fillMaxWidth().padding(vertical=space.large),
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant) }
            }
        }
    }
}

@Composable private fun InboxError(message: String, retry: () -> Unit) {
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium)) {
            Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick=retry) { Text("Try again") }
        }
    }
}

private data class InboxAppearance(val label: String, val icon: ImageVector, val background: Color, val foreground: Color)
@Composable private fun appearance(kind: String): InboxAppearance {
    val c=MaterialTheme.colorScheme
    return when(kind.substringBefore('.')) {
        "plan" -> InboxAppearance("Plans",Icons.Outlined.CalendarMonth,c.secondaryContainer,c.onSecondaryContainer)
        "spark" -> InboxAppearance("Questions",Icons.Outlined.AutoAwesome,c.tertiaryContainer,c.onTertiaryContainer)
        "challenge" -> InboxAppearance("Challenges",Icons.Outlined.Flag,c.primaryContainer,c.onPrimaryContainer)
        "moment" -> if(kind in setOf("moment.appreciated","moment.replied"))
            InboxAppearance("Appreciations",Icons.Outlined.FavoriteBorder,c.tertiaryContainer,c.onTertiaryContainer)
            else InboxAppearance("Moments",Icons.Outlined.PhotoLibrary,c.secondaryContainer,c.onSecondaryContainer)
        "status" -> InboxAppearance("Partner status",Icons.Outlined.SentimentSatisfied,c.secondaryContainer,c.onSecondaryContainer)
        "idea" -> InboxAppearance("Date ideas",Icons.Outlined.Lightbulb,c.tertiaryContainer,c.onTertiaryContainer)
        "sharing" -> InboxAppearance("Sharing",Icons.Outlined.Shield,c.primaryContainer,c.onPrimaryContainer)
        else -> InboxAppearance("Partner requests",Icons.Outlined.PersonAdd,c.primaryContainer,c.onPrimaryContainer)
    }
}

@Composable private fun InboxRow(item: InboxItem, now: Instant, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c=MaterialTheme.colorScheme;val space=MaterialTheme.spacing;val style=appearance(item.kind)
    val unread=item.readAt==null
    Surface(onClick=onClick,enabled=enabled,shape=MaterialTheme.shapes.medium,
        color=if(unread) c.surfaceContainerHigh else c.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth().testTag("notification:${item.id}").semantics {
            stateDescription=if(unread) "Unread" else "Read"
        }) {
        Row(Modifier.padding(space.medium),horizontalArrangement=Arrangement.spacedBy(space.compact),verticalAlignment=Alignment.Top) {
            Surface(color=style.background,shape=CircleShape) {
                Icon(style.icon,null,Modifier.padding(space.compact).size(space.chromeIcon),tint=style.foreground)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(space.tiny)) {
                Text(item.title,style=MaterialTheme.typography.titleMedium,fontWeight=if(unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines=3,overflow=TextOverflow.Ellipsis)
                Text(item.body,style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant,maxLines=3,overflow=TextOverflow.Ellipsis)
                Text("${style.label} · ${inboxTime(item.createdAt,now)}",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                if(!item.available) Text("No longer available",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(space.small)) {
                if(unread) Box(Modifier.size(space.small).background(c.primary,CircleShape))
                if(busy) CircularProgressIndicator(Modifier.size(space.chromeIcon),strokeWidth=2.dp)
                else if(item.available) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.size(space.chromeIcon),tint=c.onSurfaceVariant)
            }
        }
    }
}

internal fun inboxDay(value: String, now: Instant, zone: TimeZone): String {
    val day=runCatching { Instant.parse(value).toLocalDateTime(zone).date }.getOrNull() ?: return "Earlier"
    return when(day.daysUntil(now.toLocalDateTime(zone).date)) { 0 -> "Today";1 -> "Yesterday";else -> "Earlier" }
}
internal fun inboxTime(value: String, now: Instant): String {
    val at=runCatching { Instant.parse(value) }.getOrNull() ?: return "Earlier"
    val minutes=(now-at).inWholeMinutes.coerceAtLeast(0)
    return when { minutes<1 -> "Just now";minutes<60 -> "${minutes}m ago";minutes<1440 -> "${minutes/60}h ago";else -> "${minutes/1440}d ago" }
}
