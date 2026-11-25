package com.srisu.srisu.features.notifications

import com.srisu.srisu.core.identity.displayInitial

import com.srisu.srisu.core.identity.partnerDisplayName

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.NavigationBackButton
import com.srisu.srisu.features.moments.presentation.MomentImage
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
    NotificationInboxContent(state,onBack,onSettings,vm::refresh,vm::more,vm::filter,vm::readAll,vm::open,now,
        loadImage=vm::image)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NotificationInboxContent(state: InboxState, onBack: () -> Unit = {},
    onSettings: () -> Unit = {}, onRefresh: () -> Unit = {}, onMore: () -> Unit = {},
    onFilter: (Boolean) -> Unit = {}, onReadAll: () -> Unit = {}, onOpen: (InboxItem) -> Unit = {},
    now: Instant = Clock.System.now(), zone: TimeZone = TimeZone.currentSystemDefault(),
    loadImage: suspend (String, String) -> ByteArray? = { _, _ -> null }) {
    val colors=MaterialTheme.colorScheme
    val list=rememberLazyListState()
    LaunchedEffect(state.unreadOnly) { list.scrollToItem(0) }
    val canRead=state.loaded && state.unreadCount>0 && state.readToken!=null &&
        state.busy==null && !state.loading && !state.loadingMore
    Scaffold(containerColor=colors.surface, topBar={
        InboxHeader(onBack, onReadAll, canRead, state.busy=="read-all")
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            PullToRefreshBox(isRefreshing=state.loading && state.items.isNotEmpty(),
                onRefresh={if(state.busy==null) onRefresh()},modifier=Modifier.widthIn(max=600.dp).fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().testTag("notification-list"),state=list,
                    contentPadding=PaddingValues(bottom=24.dp)) {
                    if(state.unreadOnly) item("filter-status") {
                        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text("Unread updates",style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                            TextButton(onClick={onFilter(false)}) { Text("View all activity") }
                        }
                    }
                    if(state.loading && state.items.isEmpty()) item("loading") { InboxSkeleton() }
                    state.notice?.let { notice -> item("notice") {
                        Text(notice,Modifier.padding(horizontal=20.dp,vertical=12.dp).semantics { liveRegion=LiveRegionMode.Polite },
                            style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    } }
                    state.error?.let { error -> item("error") { InboxError(error,onRefresh) } }
                    if(state.loaded && !state.loading && state.items.isEmpty() && state.error==null) item("empty") {
                        Column(Modifier.fillMaxWidth().padding(horizontal=32.dp,vertical=64.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Icon(if(state.unreadOnly) Icons.Outlined.DoneAll else Icons.Outlined.NotificationsNone,null,
                                Modifier.size(32.dp),tint=colors.onSurfaceVariant)
                            Text(if(state.unreadOnly) "You’re all caught up" else "A little quiet here",
                                style=MaterialTheme.typography.titleMedium)
                            Text(if(state.unreadOnly) "No unread updates. Your shared activity is still in All."
                                else "Invitations, plans, questions and shared moments will appear here.",
                                style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant,textAlign=TextAlign.Center)
                        }
                    }
                    var previous: String?=null
                    state.items.forEach { row ->
                        val day=inboxDay(row.createdAt,now,zone)
                        if(day!=previous) {
                            item("day:${row.id}",contentType="section") {
                                Text(day.uppercase(),Modifier.padding(start=20.dp,end=20.dp,top=20.dp,bottom=8.dp).semantics { heading() },
                                    style=NotificationInboxStyle.section,color=colors.onSurfaceVariant)
                            }
                            previous=day
                        }
                        item(row.id,contentType="notification") {
                            key(state.readToken) {
                                InboxRow(row,now,zone,state.busy==row.id,state.busy==null,loadImage) { onOpen(row) }
                            }
                        }
                    }
                    state.pageError?.let { message -> item("page-error") { InboxError(message,onMore) } }
                    if(state.nextCursor!=null) item("more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment=Alignment.Center) {
                            if(state.loadingMore) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                            else TextButton(onClick=onMore,enabled=state.busy==null && !state.loading) { Text("Load earlier updates") }
                        }
                    }
                    // Secondary tools remain available, outside the reference's primary hierarchy.
                    item("tools") {
                        FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),horizontalArrangement=Arrangement.Center) {
                            TextButton(onClick={onFilter(!state.unreadOnly)},enabled=state.busy==null) { Text(if(state.unreadOnly) "All activity" else "Unread",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant) }
                            TextButton(onClick=onRefresh,enabled=!state.loading && state.busy==null) { Text("Refresh",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant) }
                            TextButton(onClick=onSettings,modifier=Modifier.semantics { contentDescription="Notification settings" }) { Text("Settings",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun InboxHeader(onBack:()->Unit,onReadAll:()->Unit,enabled:Boolean,busy:Boolean) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),contentAlignment=Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max=600.dp).fillMaxWidth().padding(horizontal=8.dp)) {
            val expanded=LocalDensity.current.fontScale>1.3f || maxWidth<340.dp
            val title: @Composable ()->Unit = {Text("Notifications",style=NotificationInboxStyle.heading)}
            val action: @Composable ()->Unit = {
                TextButton(onClick=onReadAll,enabled=enabled,
                    modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Mark all notifications as read" }) {
                    if(busy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    else Text("Mark read",style=MaterialTheme.typography.labelMedium,color=LocalContentColor.current)
                }
            }
            if(expanded) Column {
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                    NavigationBackButton(onBack);Box(Modifier.weight(1f).padding(end=48.dp),contentAlignment=Alignment.Center) { title() }
                }
                Box(Modifier.fillMaxWidth().padding(end=8.dp),contentAlignment=Alignment.CenterEnd) {action()}
            } else Box(Modifier.fillMaxWidth().heightIn(min=60.dp)) {
                NavigationBackButton(onBack,Modifier.align(Alignment.CenterStart))
                Box(Modifier.align(Alignment.Center)) {title()}
                Box(Modifier.align(Alignment.CenterEnd)) {action()}
            }
        }
    }
}

@Composable private fun InboxError(message:String,retry:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick=retry) {Text("Try again")}
    }
}

private data class InboxAppearance(val label:String,val icon:ImageVector,val color:Color)
@Composable private fun appearance(kind:String):InboxAppearance {
    val c=MaterialTheme.colorScheme
    return when(kind.substringBefore('.')) {
        "plan" -> InboxAppearance("Plans",if(kind=="plan.responded") Icons.Outlined.Check else Icons.Outlined.CalendarMonth,
            if(kind=="plan.responded") c.success else c.planner)
        "spark" -> InboxAppearance("Questions",Icons.Outlined.AutoAwesome,c.spark)
        "challenge" -> InboxAppearance("Challenges",Icons.Outlined.Flag,c.reminder)
        "moment" -> InboxAppearance("Moments",if(kind in setOf("moment.hearted","moment.appreciated")) Icons.Outlined.FavoriteBorder else Icons.Outlined.PhotoCamera,c.moments)
        "status" -> InboxAppearance("Partner status",Icons.Outlined.SentimentSatisfied,c.spark)
        "idea" -> InboxAppearance("Date ideas",Icons.Outlined.Add,c.spark)
        "sharing" -> InboxAppearance("Sharing",Icons.Outlined.Shield,c.secondary)
        else -> InboxAppearance("Partner requests",Icons.Outlined.PersonAdd,c.spark)
    }
}

@Composable private fun InboxRow(item:InboxItem,now:Instant,zone:TimeZone,busy:Boolean,enabled:Boolean,
    loadImage:suspend (String,String)->ByteArray?,onClick:()->Unit) {
    val c=MaterialTheme.colorScheme
    val style=appearance(item.kind)
    val actorName=partnerDisplayName(item.actorId,item.actorRealName ?: item.actorName.orEmpty())
    // Only server-tagged person spans are aliases; captions, titles and other prose stay exact.
    val segments=item.segments.take(8).map { segment ->
        if(segment.userId == null) segment else segment.copy(text=partnerDisplayName(segment.userId,segment.userName ?: segment.text))
    }
    val unread=item.readAt==null
    val largeText=LocalDensity.current.fontScale>1.3f
    val background=if(unread) NotificationInboxStyle.unread else c.surface
    Surface(onClick=onClick,enabled=enabled,shape=RectangleShape,color=background,
        modifier=Modifier.fillMaxWidth().testTag("notification:${item.id}").semantics {
            stateDescription=if(unread) "Unread" else "Read"
        }) {
        Row(Modifier.padding(horizontal=20.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=if(largeText) Alignment.Top else Alignment.CenterVertically) {
            Box(Modifier.size(44.dp)) {
                val avatar by produceState<ByteArray?>(null,item.id,item.avatar,item.available,loadImage) {
                    value=if(item.available && item.avatar) loadImage(item.id,"avatar") else null
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.mediaBackground),contentAlignment=Alignment.Center) {
                    if(actorName.isNotBlank()) Text(displayInitial(actorName),style=MaterialTheme.typography.titleMedium,color=c.onMedia)
                    else Box(Modifier.size(10.dp).background(c.reminder,CircleShape))
                    if(avatar!=null) MomentImage(avatar,null,Modifier.fillMaxSize())
                }
                Box(Modifier.align(Alignment.BottomEnd).offset(x=4.dp,y=4.dp).size(24.dp).background(style.color,CircleShape)
                    .border(2.dp,background,CircleShape),contentAlignment=Alignment.Center) {
                    Icon(style.icon,null,Modifier.size(14.dp),tint=c.surface)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                val message=remember(item,segments) { buildAnnotatedString {
                    if(segments.isNotEmpty()) segments.forEach { segment ->
                        withStyle(SpanStyle(fontWeight=if(segment.bold) FontWeight.Bold else FontWeight.Normal)) { append(segment.text.take(360)) }
                    } else {
                        withStyle(SpanStyle(fontWeight=FontWeight.Bold)) {append(item.title)}
                        if(item.available && item.body.isNotBlank()) append(" · ${item.body}")
                    }
                } }
                Text(message,style=NotificationInboxStyle.message)
                Text(inboxTime(item.createdAt,now,zone),style=NotificationInboxStyle.time,color=c.onSurfaceVariant)
                if(!item.available) Text("No longer available",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                if(largeText && item.available && item.thumbnail) InboxThumbnail(item,loadImage)
            }
            if(!largeText && item.available && item.thumbnail) InboxThumbnail(item,loadImage)
            if(busy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
            else if(unread) Box(Modifier.size(8.dp).background(c.reminder,CircleShape))
        }
    }
}

@Composable private fun InboxThumbnail(item:InboxItem,loadImage:suspend (String,String)->ByteArray?) {
    val c=MaterialTheme.colorScheme
    val thumbnail by produceState<ByteArray?>(null,item.id,loadImage) {value=loadImage(item.id,"thumbnail")}
    Box(Modifier.size(44.dp).testTag("notification-thumbnail:${item.id}").clip(MaterialTheme.shapes.field)
        .background(c.surfaceContainerLow),contentAlignment=Alignment.Center) {
        if(thumbnail!=null) MomentImage(thumbnail,null,Modifier.fillMaxSize())
        else Icon(Icons.Outlined.Image,null,Modifier.size(20.dp),tint=c.onSurfaceVariant)
    }
}

@Composable private fun InboxSkeleton() {
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().semantics {contentDescription="Loading notifications"}) {
        Spacer(Modifier.height(24.dp))
        repeat(5) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(c.surfaceContainerHigh,CircleShape))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(.9f).height(12.dp).background(c.surfaceContainerHigh,MaterialTheme.shapes.small))
                    Box(Modifier.fillMaxWidth(.65f).height(12.dp).background(c.surfaceContainerHigh,MaterialTheme.shapes.small))
                    Box(Modifier.width(28.dp).height(8.dp).background(c.surfaceContainerHigh,MaterialTheme.shapes.small))
                }
            }
        }
    }
}

internal fun inboxDay(value:String,now:Instant,zone:TimeZone):String {
    val day=runCatching {Instant.parse(value).toLocalDateTime(zone).date}.getOrNull() ?: return "Earlier"
    val today=now.toLocalDateTime(zone).date
    val age=day.daysUntil(today)
    return when {age<=0 -> "Today";age<today.dayOfWeek.ordinal+1 -> "This week";else -> "Earlier"}
}
internal fun inboxTime(value:String,now:Instant,zone:TimeZone=TimeZone.currentSystemDefault()):String {
    val at=runCatching {Instant.parse(value)}.getOrNull() ?: return "Earlier"
    val minutes=(now-at).inWholeMinutes.coerceAtLeast(0)
    if(minutes<1) return "Now"
    if(minutes<60) return "${minutes}m"
    if(minutes<1440) return "${minutes/60}h"
    val day=at.toLocalDateTime(zone).date
    val age=day.daysUntil(now.toLocalDateTime(zone).date)
    if(age in 1..6) return listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")[day.dayOfWeek.ordinal]
    return "${listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")[day.month.ordinal]} ${day.day}"
}
