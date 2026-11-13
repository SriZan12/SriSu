package com.srisu.srisu.features.explore.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.moments.presentation.GuardedMomentImage
import com.srisu.srisu.features.moments.presentation.MomentError
import com.srisu.srisu.features.moments.presentation.MomentLifecycle
import com.srisu.srisu.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

@Composable fun ExploreScreen(onMoment: (Long)->Unit,onProfile: (Long)->Unit,onPreferences: ()->Unit,
    vm: ExploreViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MomentLifecycle(vm::active)
    ExploreContent(state,vm::select,vm::refresh,vm::more,vm::broader,vm::manage,vm::library,
        vm::fave,vm::retryFave,onMoment,onProfile,onPreferences,vm::image)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ExploreContent(state: ExploreState,onSelect: (String)->Unit,onRefresh: ()->Unit,
    onMore: ()->Unit,onBroader: ()->Unit,onManage: (Boolean)->Unit,onLibrary: (Boolean)->Unit,
    onFave: (Long,Boolean)->Unit,onRetryFave: (Long)->Unit,onMoment: (Long)->Unit,onProfile: (Long)->Unit,
    onPreferences: ()->Unit,loadImage: suspend (String)->ByteArray?,
    globalScroll: LazyListState=rememberLazyListState(),favesScroll: LazyListState=rememberLazyListState()) {
    val space=MaterialTheme.spacing
    val colors=MaterialTheme.colorScheme
    val feed=state.feed
    val rows=feed.cards.filter {state.section!="faves" || state.preferences[it.couple.id]?.confirmed!=false}
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=space.gutter,vertical=space.small),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Explore",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.semantics {heading()})
                Text("Little windows into life together",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
            }
            IconButton(onClick=onRefresh,enabled=!feed.loading) {Icon(Icons.Outlined.Refresh,"Refresh Explore")}
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=space.gutter,vertical=space.small)) {
            listOf("global" to "Global","faves" to "Faves").forEachIndexed {index,(key,label)->
                SegmentedButton(selected=state.section==key,onClick={onSelect(key)},shape=SegmentedButtonDefaults.itemShape(index,2)) {Text(label)}
            }
        }
        LazyColumn(Modifier.weight(1f).widthIn(max=640.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            state=if(state.section=="global") globalScroll else favesScroll,
            contentPadding=PaddingValues(start=space.gutter,end=space.gutter,top=space.small,bottom=space.section),
            verticalArrangement=Arrangement.spacedBy(space.medium)) {
            item("context") {
                if(state.section=="faves") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Saved by you, just for you",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                        TextButton(onClick={onManage(true)}) {Text("Manage Faves")}
                    }
                } else Column {
                    if(feed.coverage=="local_with_fallback" || feed.coverage=="broader") Text(
                        if(feed.coverage=="local_with_fallback") "A little further afield, too. Local Moments are limited right now." else "Public Moments from a wider world.",
                        style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    TextButton(onClick=onPreferences,contentPadding=PaddingValues(0.dp)) {Text("Set your city & interests")}
                }
            }
            if(feed.loading || !feed.fresh && feed.error==null) item("loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Finding Moments…",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            }
            feed.error?.let {item("error") {MomentError(it,onRefresh)}}
            if(feed.fresh && !feed.loading && rows.isEmpty() && feed.error==null) item("empty") {
                ExploreEmpty(state.section,feed.emptyReason,{onSelect("global")},onRefresh,{onManage(true)},onBroader)
            }
            if(feed.fresh && state.active) {
                if(state.section=="faves" && rows.isNotEmpty() && rows.none {it.hasUnopened}) item("seen") {
                    Text("All available Moments seen. Revisit any you like.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                }
            }
            // Retain keyed geometry during reauthorization so the list does not jump to
            // its first item. Protected pixels, semantics and actions remain hidden.
            items(rows,key={it.couple.id}) {card->
                val authorized=feed.fresh && state.active
                Surface(shape=MaterialTheme.shapes.large,color=colors.surfaceContainer) {
                    Box(if(authorized) Modifier else Modifier.graphicsLayer {alpha=0f}.clearAndSetSemantics {}) {
                        // Recreate guarded image effects when authorization returns. Their
                        // URL can stay the same while the earlier load was deliberately denied.
                        key(authorized) {
                            ExploreCoupleCard(card,state.preferences[card.couple.id],
                                {id,value->if(authorized) onFave(id,value)}, {id->if(authorized) onRetryFave(id)},
                                {id->if(authorized) onMoment(id)}, {id->if(authorized) onProfile(id)},
                                {url->if(authorized) loadImage(url) else null})
                        }
                    }
                }
            }
            if(rows.isNotEmpty() && feed.fresh) item("continuation") {
                Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                    when {
                        feed.cursorStale -> TextButton(onClick=onRefresh) {Text("Refresh for your latest Faves")}
                        feed.next!=null -> OutlinedButton(onClick=onMore,enabled=!feed.loading) {Text("More couples")}
                        else -> {
                            Text("You’re up to date",style=MaterialTheme.typography.titleMedium)
                            Text(if(feed.paginationAvailable) "That’s all for this visit." else "More results are temporarily unavailable.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                            Row {TextButton(onClick=onRefresh) {Text("Refresh")}
                                if(state.section=="global" && state.scope!="broad") TextButton(onClick=onBroader) {Text("Explore more widely")}}
                        }
                    }
                }
            }
        }
    }
    if(state.managing) ModalBottomSheet(onDismissRequest={onManage(false)}) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=640.dp),contentPadding=PaddingValues(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
            item {Text("Your Faved couples",style=MaterialTheme.typography.headlineMedium)
                Text("Saved even when their Moments have ended. Only you can see this list.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
            if(state.library.loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.library.error?.let {item {MomentError(it,{onLibrary(false)})}}
            val saved=state.library.rows.filter {state.preferences[it.coupleId]?.confirmed!=false}
            if(state.library.loaded && saved.isEmpty()) item {Text("No couples saved yet.");TextButton(onClick={onManage(false);onSelect("global")}) {Text("Explore couples")}}
            if(state.active && state.library.loaded) items(saved,key={it.coupleId}) {row->
                Surface(shape=MaterialTheme.shapes.large,color=colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(space.medium),verticalArrangement=Arrangement.spacedBy(space.small)) {
                        TextButton(onClick={onManage(false);onProfile(row.coupleId)},enabled=row.available,contentPadding=PaddingValues(0.dp)) {
                            Text(if(row.available) row.couple?.label ?: "A SriSu couple" else "Couple unavailable",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                            if(row.available) Icon(Icons.AutoMirrored.Outlined.ArrowForward,null)
                        }
                        Text(if(!row.available) "You can remove this saved preference." else if(row.hasActiveContent) "Active Moments available" else "No active Moments",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                        if(row.available && row.hasActiveContent) TextButton(onClick={onManage(false);onMoment(row.coupleId)}) {Text("View Moments")}
                        FaveControl(row.coupleId,true,state.preferences[row.coupleId],onFave,onRetryFave)
                    }
                }
            }
            if(state.library.next!=null) item {TextButton(onClick={onLibrary(true)},enabled=!state.library.loading) {Text("More saved couples")}}
            item {TextButton(onClick={onManage(false)}) {Text("Done")}}
        }
    }
}

@Composable private fun ExploreCoupleCard(card: ExploreCard,status: FaveStatus?,onFave: (Long,Boolean)->Unit,onRetry: (Long)->Unit,
    onMoment: (Long)->Unit,onProfile: (Long)->Unit,loadImage: suspend (String)->ByteArray?) {
    val space=MaterialTheme.spacing;val colors=MaterialTheme.colorScheme
    Surface(shape=MaterialTheme.shapes.large,color=colors.surfaceContainer) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel="Open couple profile") {onProfile(card.couple.id)}.padding(space.medium),
                horizontalArrangement=Arrangement.spacedBy(space.compact),verticalAlignment=Alignment.CenterVertically) {
                GuardedMomentImage(card.couple.avatars.firstOrNull(),loadImage,null,Modifier.size(space.touchTarget).clip(CircleShape),avatar=true)
                Column(Modifier.weight(1f)) {
                    Text(card.couple.label,style=MaterialTheme.typography.titleMedium)
                    Text("Couple profile",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,tint=colors.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clickable(role=Role.Button,onClickLabel="View Moments") {onMoment(card.couple.id)}) {
                GuardedMomentImage(card.preview.photos.firstOrNull()?.image,loadImage,"View Moments from ${card.couple.label}",Modifier.fillMaxSize())
            }
            Column(Modifier.padding(space.medium),verticalArrangement=Arrangement.spacedBy(space.small)) {
                Text(if(card.hasUnopened) "New Moments" else "Seen",style=MaterialTheme.typography.labelMedium,
                    color=if(card.hasUnopened) colors.primary else colors.onSurfaceVariant)
                if(card.preview.caption.isNotBlank()) Text(card.preview.caption,style=MaterialTheme.typography.bodyLarge,maxLines=3)
                card.reason?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                FaveControl(card.couple.id,card.isFaved,status,onFave,onRetry)
            }
        }
    }
}

@Composable private fun FaveControl(id: Long,fallback: Boolean,status: FaveStatus?,onFave: (Long,Boolean)->Unit,onRetry: (Long)->Unit) {
    val saved=status?.confirmed ?: fallback
    OutlinedButton(onClick={onFave(id,!saved)},enabled=status?.pending!=true,modifier=Modifier.fillMaxWidth()) {
        Icon(if(saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,null,Modifier.size(MaterialTheme.spacing.icon))
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        Text(if(status?.pending==true) "Saving…" else if(saved) "Faved · remove" else "Add to Faves")
    }
    status?.error?.let {MomentError(it,{onRetry(id)})}
}

@Composable private fun ExploreEmpty(section: String,reason: String?,onGlobal: ()->Unit,onRefresh: ()->Unit,onManage: ()->Unit,onBroader: ()->Unit) {
    val space=MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(vertical=space.section),verticalArrangement=Arrangement.spacedBy(space.compact)) {
        Icon(if(section=="faves") Icons.Outlined.BookmarkBorder else Icons.Outlined.Explore,null,Modifier.size(space.large),tint=MaterialTheme.colorScheme.primary)
        Text(when {section=="global"->"A quiet moment here";reason=="no_faves"->"Keep your favourites close";else->"Your Faves are still here"},style=MaterialTheme.typography.headlineMedium)
        Text(when {section=="global"->"No eligible public Moments right now. Try a wider discovery or check back later."
            reason=="no_faves"->"Save couples you enjoy. Their public Moments will appear here, just for you."
            else->"Your saved couples have no active public Moments right now. You can still visit or manage them."},style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(section=="faves") {
            Button(onClick=onGlobal) {Text("Explore couples")}
            if(reason!="no_faves") TextButton(onClick=onManage) {Text("Manage Faves")}
        } else Row {OutlinedButton(onClick=onRefresh) {Text("Refresh")};TextButton(onClick=onBroader) {Text("Explore more widely")}}
    }
}
