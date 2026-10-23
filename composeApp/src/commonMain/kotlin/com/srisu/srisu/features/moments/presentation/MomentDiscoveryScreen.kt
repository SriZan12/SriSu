package com.srisu.srisu.features.moments.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.theme.spacing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.TimeSource
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

data class MomentDiscoveryState(val section: String="global",val cards: List<MomentDiscoveryCard> = emptyList(),
    val loading: Boolean=false,val error: String?=null,val next: String?=null,val fresh: Boolean=false)
@OptIn(kotlin.time.ExperimentalTime::class)
class MomentDiscoveryViewModel(private val source: MomentSource,private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp=sessions.stamp()
    private val mutable=MutableStateFlow(MomentDiscoveryState())
    val state=mutable.asStateFlow()
    private var work: Job?=null
    private var expiry: Job?=null
    private var generation=0
    private var active=false
    private val deadlines=mutableMapOf<Long,ComparableTimeMark>()
    fun active(value: Boolean) {
        active=value
        if(value) load() else { generation++; work?.cancel(); expiry?.cancel(); deadlines.clear(); mutable.update {it.copy(cards=emptyList(),fresh=false,loading=false)} }
    }
    init { viewModelScope.launch {sessions.state.collect { if(it!=stamp) {work?.cancel();expiry?.cancel();mutable.value=MomentDiscoveryState()} }} }
    fun load(section: String=state.value.section,more: Boolean=false) {
        if(sessions.stamp()!=stamp || (work?.isActive==true && section==state.value.section)) return
        work?.cancel(); val own=++generation
        val cursor=if(more) momentCursor(state.value.next) else null
        mutable.update {it.copy(section=section,cards=if(more) it.cards else emptyList(),loading=true,error=null)}
        work=viewModelScope.launch {
            val startedAt=TimeSource.Monotonic.markNow()
            when(val result=source.discover(section,cursor)) {
                is NetworkAPIResult.Success -> {
                    if(sessions.stamp()!=stamp || own!=generation) return@launch
                    val page=result.response
                    if(page==null) mutable.update {it.copy(loading=false,error="Moments could not be loaded.",fresh=false)}
                    else {
                        val now=TimeSource.Monotonic.markNow()
                        if(!more) deadlines.clear()
                        page.results.forEach {card->
                            val remaining=runCatching {(Instant.parse(card.preview.expiresAt)-Instant.parse(page.serverTime)).inWholeMilliseconds-startedAt.elapsedNow().inWholeMilliseconds}.getOrDefault(0)
                            deadlines[card.couple.id]=now+remaining.milliseconds
                        }
                        val rows=((if(more) state.value.cards else emptyList())+page.results).distinctBy {it.couple.id}
                        mutable.update {it.copy(cards=rows,next=page.next,loading=false,fresh=true)}
                        expiry?.cancel(); expiry=viewModelScope.launch {
                            while(deadlines.isNotEmpty()) {
                                val next=deadlines.values.minOf { -it.elapsedNow().inWholeMilliseconds }.coerceAtLeast(1)
                                delay(next)
                                val expired=deadlines.filterValues {it.hasPassedNow()}.keys
                                mutable.update {it.copy(cards=it.cards.filterNot {card->card.couple.id in expired})}
                                expired.toList().forEach(deadlines::remove)
                            }
                        }
                    }
                }
                is NetworkAPIResult.Error -> {if(sessions.stamp()==stamp && own==generation) mutable.update {it.copy(loading=false,error=result.failure.message,fresh=false,cards=if(result.failure.status in listOf(401,403,404)) emptyList() else it.cards)}}
            }
        }
    }
    fun fave(card: MomentDiscoveryCard) {
        if(state.value.loading) return
        viewModelScope.launch {
            val result=source.fave(card.couple.id,!card.isFaved)
            sessions.ensureCurrent(stamp)
            if(result is NetworkAPIResult.Success) load()
            else mutable.update {it.copy(error=(result as? NetworkAPIResult.Error)?.failure?.message)}
        }
    }
    suspend fun image(url: String)=source.image(url)
}

@Composable fun MomentDiscoveryScreen(onOpen: (Long,Long)->Unit,onProfile: (Long)->Unit,onBack: ()->Unit={},vm: MomentDiscoveryViewModel=koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MomentLifecycle(vm::active)
    val space=MaterialTheme.spacing
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),state=rememberLazyListState(),
        contentPadding=PaddingValues(space.gutter),verticalArrangement=Arrangement.spacedBy(space.medium)) {
        item("heading") {Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back to Home")}
            Text("Explore Moments",style=MaterialTheme.typography.headlineSmall)
        }}
        item("filters") {Row(horizontalArrangement=Arrangement.spacedBy(space.small)) {
            FilterChip(state.section=="global",{vm.load("global")},label={Text("Global")})
            FilterChip(state.section=="faves",{vm.load("faves")},label={Text("Faves")})
        }}
        if(state.loading) item("loading") {LinearProgressIndicator(Modifier.fillMaxWidth())}
        state.error?.let {item("error") {MomentError(it,{vm.load()})}}
        if(!state.loading && state.cards.isEmpty() && state.error==null) item("empty") {
            Text(if(state.section=="faves") "No active Moments from your Faves. Your Faves are personal to you." else "No public Moments right now. Check back later.",style=MaterialTheme.typography.bodyLarge)
        }
        items(state.cards,key={it.couple.id}) {card->
            Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(space.medium),verticalArrangement=Arrangement.spacedBy(space.compact)) {
                    card.preview.photos.firstOrNull()?.let {GuardedMomentImage(it.image,vm::image,"Moment preview",Modifier.fillMaxWidth().aspectRatio(1.2f).clip(MaterialTheme.shapes.medium))}
                    if(card.preview.caption.isNotBlank()) Text(card.preview.caption,maxLines=3,style=MaterialTheme.typography.bodyLarge)
                    Button(onClick={onOpen(card.preview.id,card.couple.id)},enabled=state.fresh) {Text("View Moment")}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={onProfile(card.couple.id)}) {Text("Couple profile")}
                        TextButton(onClick={vm.fave(card)}) {Text(if(card.isFaved) "Remove from Faves" else "Add to Faves")}
                    }
                }
            }
        }
        if(state.next!=null) item("more") {TextButton(onClick={vm.load(more=true)},enabled=!state.loading) {Text("Load more")}}
    }
}
