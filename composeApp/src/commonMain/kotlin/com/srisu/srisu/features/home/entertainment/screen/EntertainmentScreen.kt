package com.srisu.srisu.features.home.entertainment.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.theme.*
import kotlinx.datetime.*
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

@Composable fun HomeScreen(onFindPartner: ()->Unit={}, onCoupleProfile: ()->Unit={}, onExplore: ()->Unit={},
    onChat: ()->Unit={}, onPlans: (Long?)->Unit={}, onChallenges: (String?)->Unit={}, onSparks: (String?)->Unit={}, onInvitations: ()->Unit={}, onCreateMoment: (Long)->Unit={},
    onViewMoments: (Long,Long)->Unit={_,_->}, viewModel: HomeMomentsViewModel=koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MomentLifecycle(viewModel::visible)
    HomeMomentsContent(state,viewModel::image,viewModel::refresh,onFindPartner,onCoupleProfile,onExplore,
        onChat,onInvitations,onCreateMoment,onViewMoments, sparks = { com.srisu.srisu.features.sparks.presentation.HomeSparks(onSparks) },
        challenges = { com.srisu.srisu.features.challenges.presentation.HomeChallenges(onChallenges) },
        plans = { com.srisu.srisu.features.plans.presentation.HomePlans(onPlans) })
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable internal fun HomeMomentsContent(state: HomeMomentsState, image: suspend (String)->ByteArray?, onRefresh: ()->Unit,
    onFindPartner: ()->Unit, onCoupleProfile: ()->Unit, onExplore: ()->Unit, onChat: ()->Unit,
    onInvitations: ()->Unit, onCreate: (Long)->Unit, onView: (Long,Long)->Unit,
    hour: Int=Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour, sparks: @Composable ()->Unit = {}, challenges: @Composable ()->Unit = {}, plans: @Composable ()->Unit = {}) {
    val space=MaterialTheme.spacing
    val colors=MaterialTheme.colorScheme
    val couple=state.home?.couple
    val me=state.home?.user?.id
    val partner=couple?.members?.firstOrNull { it.id!=me }
    val greeting=when(hour) { in 5..11->"Good morning"; in 12..16->"Good afternoon"; else->"Good evening" }
    val days=remember(couple?.anniversary) { couple?.anniversary?.let { runCatching {
        LocalDate.parse(it).daysUntil(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date).coerceAtLeast(0)
    }.getOrNull() } }
    LazyColumn(state=rememberLazyListState(),modifier=Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding=PaddingValues(start=space.gutter,end=space.gutter,top=space.large,bottom=space.extraLarge),
        verticalArrangement=Arrangement.spacedBy(space.section)) {
        item("identity") {
            Row(verticalAlignment=Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(if(state.name.isBlank()) greeting else "$greeting, ${state.name}",style=SriSuPartnerLinkTypography().headlineSmall)
                    if(partner!=null) Text(if(days==null) "Your space with ${partner.name.ifBlank { "your partner" }}" else "Day ${days+1} with ${partner.name.ifBlank { "your partner" }}",
                        style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=space.tiny))
                }
                IconButton(onClick=onRefresh,enabled=!state.loading) { Icon(Icons.Outlined.Refresh,"Refresh Home") }
            }
        }
        if(state.loading) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(state.error!=null) item("error") { MomentError(state.error,onRefresh) }
        if(couple!=null) {
            item("moments") {
                Column(verticalArrangement=Arrangement.spacedBy(space.medium)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Moments",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        TextButton(onClick={onCreate(couple.id)},enabled=state.fresh,modifier=Modifier.semantics {contentDescription="Add a Moment"}) { Icon(Icons.Outlined.Add,null,Modifier.size(space.icon)); Text("Add") }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(space.large)) {
                        couple.members.sortedBy { if(it.id==me) 0 else 1 }.forEach { person ->
                            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.widthIn(max=124.dp)) {
                                val own=person.id==me
                                val label=when { !state.fresh->"Refresh to see Moments";person.activeCount==0->if(own) "Add your first Moment" else "No active Moments";person.unseenCount>0->"${person.unseenCount} new Moments";else->"All Moments seen" }
                                Box(Modifier.size(76.dp).semantics { contentDescription=label }.clickable(enabled=state.fresh && (own || person.activeCount>0)) {
                                    if(person.activeCount>0) onView(couple.id,person.id) else onCreate(couple.id)
                                }) {
                                    val ring=if(person.unseenCount>0 && state.fresh) colors.moments else colors.outlineVariant
                                    GuardedMomentImage(person.photoUrl,image,null,Modifier.fillMaxSize().border(2.dp,ring,CircleShape).padding(5.dp).clip(CircleShape),avatar=true)
                                }
                                Text(if(own) "Your Moment" else person.name.ifBlank { "Your partner" },style=MaterialTheme.typography.bodyMedium,
                                    modifier=Modifier.padding(top=space.small),maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(if(!state.fresh) "Needs refresh" else if(person.activeCount==0) "Nothing shared yet" else if(own) "Shared" else if(person.unseenCount>0) "New" else "Seen",
                                    style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if(couple.members.all { it.activeCount==0 }) item("empty") {
                Surface(shape=MaterialTheme.shapes.large,color=colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(space.large),verticalArrangement=Arrangement.spacedBy(space.compact)) {
                        Icon(Icons.Outlined.PhotoCamera,null,tint=colors.moments)
                        Text("A little part of your day",style=MaterialTheme.typography.titleLarge)
                        Text("The view from your window. Your morning coffee. Something that made you think of them.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                        Text("Moments are visible for 24 hours. New Moments are just for you two by default.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        SriSuButton("Share a Moment",{onCreate(couple.id)},enabled=state.fresh)
                    }
                }
            }
            item("sparks") { sparks() }
            item("challenges") { challenges() }
            item("plans") { plans() }
            item("chat") { HomeAction("Your conversation","Pick up where you left off",Icons.AutoMirrored.Outlined.Chat,onChat) }
            item("profile") { HomeAction("Our profile","Your story together",Icons.Outlined.People,onCoupleProfile) }
        } else if(!state.loading && state.error==null) item("linking") {
            Surface(shape=MaterialTheme.shapes.large,color=colors.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(space.large),verticalArrangement=Arrangement.spacedBy(space.medium)) {
                    Icon(Icons.Outlined.People,null,tint=colors.secondary)
                    Text("Make this space yours",style=MaterialTheme.typography.titleLarge)
                    Text("Connect with your partner to share Moments and start your conversation.",style=MaterialTheme.typography.bodyLarge)
                    SriSuButton("Find your partner",onFindPartner)
                    TextButton(onClick=onInvitations) { Text("View invitations") }
                }
            }
        }
        item("explore") { HomeAction("Explore Moments","A glimpse of the SriSu community",Icons.Outlined.Explore,onExplore) }
    }
}

@Composable private fun HomeAction(title: String,detail: String,icon: androidx.compose.ui.graphics.vector.ImageVector,onClick: ()->Unit) {
    Surface(onClick=onClick,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            Icon(icon,null); Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward,null)
        }
    }
}
