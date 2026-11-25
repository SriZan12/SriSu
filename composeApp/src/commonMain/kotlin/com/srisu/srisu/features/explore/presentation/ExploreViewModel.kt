package com.srisu.srisu.features.explore.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.moments.data.MomentSource
import com.srisu.srisu.features.moments.data.momentCursor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds

data class ExploreFeed(val cards: List<ExploreCard> = emptyList(), val loaded: Boolean=false,
    val loading: Boolean=false,val fresh: Boolean=false,val error: String?=null,val next: String?=null,
    val emptyReason: String?=null,val coverage: String?=null,val paginationAvailable: Boolean=true,val cursorStale: Boolean=false)
data class SavedLibrary(val rows: List<SavedCouple> = emptyList(),val loaded: Boolean=false,
    val loading: Boolean=false,val error: String?=null,val next: String?=null,val faveVersion: Long=-1)
data class ExploreState(val section: String="global",val scope: String="local",val global: ExploreFeed=ExploreFeed(),
    val faves: ExploreFeed=ExploreFeed(),val library: SavedLibrary=SavedLibrary(),val managing: Boolean=false,
    val active: Boolean=false,val preferences: Map<Long,FaveStatus> = emptyMap()) {
    val feed get()=if(section=="global") global else faves
}

@OptIn(kotlin.time.ExperimentalTime::class)
class ExploreViewModel(private val source: ExploreSource,private val faves: FaveStore,
    private val sessions: SessionCoordinator,private val moments: MomentSource) : ViewModel() {
    private val stamp=sessions.stamp()
    private val mutable=MutableStateFlow(ExploreState())
    val state=mutable.asStateFlow()
    private val jobs=mutableMapOf<String,Job>()
    private val deadlines=mutableMapOf<Pair<String,Long>,ComparableTimeMark>()
    private var ticker: Job?=null
    private var favesDirty=false
    private fun discoveryPreferences() = sessions.currentSession()?.let {Triple(it.country,it.city,it.userInterests)}
    private var preferences=discoveryPreferences()
    init {
        viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
            jobs.values.forEach { j->j.cancel() }; ticker?.cancel();deadlines.clear();mutable.value=ExploreState()
        } } }
        viewModelScope.launch { faves.state.collect { values -> mutable.update { it.copy(preferences=if(current()) values else emptyMap()) } } }
        viewModelScope.launch { faves.changes.drop(1).collect {
            if(!current()) return@collect
            favesDirty=true
            jobs.remove("faves")?.cancel()
            jobs.remove("library")?.cancel()
            update("global") { it.copy(cursorStale=true) }; update("faves") { it.copy(cursorStale=true,loading=false,fresh=false) }
            mutable.update { it.copy(library=it.library.copy(loaded=false,loading=false)) }
            if(state.value.active && state.value.section=="faves") refresh()
            if(state.value.active && state.value.managing) library()
        } }
        viewModelScope.launch { moments.changes.drop(1).collect {
            if(state.value.active) validate(state.value.section)
        } }
    }
    private fun current()=sessions.stamp()==stamp
    private fun feed(section: String)=if(section=="global") state.value.global else state.value.faves
    private fun update(section: String, change: (ExploreFeed)->ExploreFeed) { mutable.update {
        if(section=="global") it.copy(global=change(it.global)) else it.copy(faves=change(it.faves)) } }
    fun active(value: Boolean) {
        if(!current() || state.value.active==value) return
        mutable.update { it.copy(active=value) }
        ticker?.cancel()
        if(value) {
            val latest=discoveryPreferences()
            if(latest!=preferences) {
                preferences=latest
                jobs.remove("global")?.cancel()
                update("global") {it.copy(loaded=false,fresh=false,loading=false,cursorStale=true)}
            }
            val section=state.value.section
            if(!feed(section).loaded || (section=="faves" && favesDirty)) refresh() else validate(section)
            if(state.value.managing) library()
            ticker=viewModelScope.launch { var elapsed=0
                while(isActive) { delay(1000); prune(); if(++elapsed%30==0) validate(state.value.section) }
            }
        } else {
            // Keep IDs/order/scroll context, but hide cached pixels until reauthorized on return.
            jobs.values.forEach {it.cancel()};jobs.clear()
            update("global") {it.copy(fresh=false,loading=false)};update("faves") {it.copy(fresh=false,loading=false)}
            mutable.update {it.copy(library=it.library.copy(loading=false,loaded=false))}
        }
    }
    fun select(section: String) {
        if(section !in listOf("global","faves") || !current() || section==state.value.section) return
        mutable.update {it.copy(section=section)}
        // A retained tab can have changed permissions while the other tab was open.
        // Keep its geometry/scroll state, but reauthorize before showing its content.
        update(section) {it.copy(fresh=false)}
        if(!feed(section).loaded || section=="faves" && favesDirty) refresh()
        else validate(section)
    }
    fun broader() { jobs.remove("global")?.cancel(); mutable.update {it.copy(scope="broad")};refresh("global") }
    fun refresh(section: String=state.value.section)=load(section,false)
    fun more()=load(state.value.section,true)
    private fun load(section: String,more: Boolean) {
        if(!current() || jobs[section]?.isActive==true) return
        val revision=faves.changes.value
        val prior=feed(section)
        val scope=state.value.scope
        if(more && (prior.next==null || prior.cursorStale)) return
        val cursor=if(more) momentCursor(prior.next) else null
        update(section) {it.copy(loading=true,error=null)}
        jobs[section]=viewModelScope.launch {
            val began=TimeSource.Monotonic.markNow()
            val result=source.feed(section,scope,cursor)
            currentCoroutineContext().ensureActive()
            if(!current()) return@launch
            val page=(result as? NetworkAPIResult.Success)?.response
            if(page==null) { failed(section,result);return@launch }
            applyPage(section,page,began,more,revision)
        }
    }
    private fun applyPage(section: String,page: ExplorePage,began: ComparableTimeMark,more: Boolean,revision: Long) {
        if(!more) deadlines.keys.removeAll {it.first==section}
        accept(section,page,began)
        update(section) {it.copy(cards=((if(more) it.cards else emptyList())+page.results).distinctBy {r->r.couple.id},
            loaded=true,loading=false,fresh=true,error=null,next=page.next,emptyReason=page.emptyReason,
            coverage=page.coverage,paginationAvailable=page.paginationAvailable,cursorStale=faves.changes.value!=revision)}
        if(section=="faves") favesDirty=false
        prune()
    }
    private fun validate(section: String) {
        if(!current() || jobs[section]?.isActive==true || !feed(section).loaded) return
        // With no retained IDs there is nothing to validate: query for new content.
        if(feed(section).cards.isEmpty()) {load(section,false);return}
        jobs[section]=viewModelScope.launch {
            val revision=faves.changes.value
            val original=feed(section).cards
            val checked=mutableMapOf<Long,ExploreCard>()
            for(batch in original.map {it.couple.id}.chunked(50)) {
                val began=TimeSource.Monotonic.markNow()
                val result=source.validate(batch,section)
                currentCoroutineContext().ensureActive()
                if(!current()) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) {failed(section,result);return@launch}
                accept(section,page,began);page.results.forEach {checked[it.couple.id]=it}
            }
            if(checked.isEmpty()) {
                // Recheck membership and supply, so removing the last Fave elsewhere
                // cannot leave the misleading "saved, no active Moments" empty state.
                val began=TimeSource.Monotonic.markNow()
                val result=source.feed(section,state.value.scope,null)
                currentCoroutineContext().ensureActive()
                if(!current()) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) {failed(section,result);return@launch}
                applyPage(section,page,began,false,revision)
                return@launch
            }
            update(section) {it.copy(cards=original.mapNotNull {old->checked[old.couple.id]?.copy(reason=old.reason)},fresh=true,error=null,loading=false)}
            prune()
        }
    }
    private fun accept(section: String,page: ExplorePage,began: ComparableTimeMark) {
        val now=TimeSource.Monotonic.markNow()
        page.results.forEach {card->
            faves.accept(card.couple.id,card.isFaved,page.faveVersion)
            val left=runCatching {(Instant.parse(card.preview.expiresAt)-Instant.parse(page.serverTime)).inWholeMilliseconds-began.elapsedNow().inWholeMilliseconds}.getOrDefault(0)
            deadlines[section to card.couple.id]=now+left.milliseconds
        }
    }
    private fun prune() { for(section in listOf("global","faves")) update(section) {it.copy(cards=it.cards.filter {card->deadlines[section to card.couple.id]?.hasPassedNow()!=true})} }
    private fun failed(section: String,result: NetworkAPIResult<*>) {
        val error=(result as? NetworkAPIResult.Error)?.failure
        update(section) {it.copy(loading=false,fresh=false,error=error?.message ?: "Couldn’t load Explore. Please retry.",
            cards=if(error?.status in listOf(401,403,404)) emptyList() else it.cards,cursorStale=it.cursorStale || error?.status in listOf(409,410))}
    }
    fun manage(value: Boolean) {
        mutable.update {it.copy(managing=value)}
        if(value) library() else jobs.remove("library")?.cancel()
    }
    fun library(more: Boolean=false) {
        if(!current() || jobs["library"]?.isActive==true) return
        val prior=state.value.library
        if(more && prior.next==null) return
        // Opening and Retry always refresh. A successful earlier page must not
        // suppress a retry after its continuation failed.
        mutable.update {it.copy(library=it.library.copy(loading=true,error=null,loaded=more && prior.loaded))}
        jobs["library"]=viewModelScope.launch {
            var append=more
            var result=source.saved(if(more) momentCursor(prior.next) else null)
            currentCoroutineContext().ensureActive()
            if(!current()) return@launch
            val continuation=(result as? NetworkAPIResult.Success)?.response
            if(more && continuation!=null && continuation.faveVersion!=prior.faveVersion) {
                // A change on another device invalidates the assembled library,
                // not just the next page. Never retain removed rows from page one.
                append=false
                mutable.update {it.copy(library=it.library.copy(loaded=false))}
                result=source.saved(null)
                currentCoroutineContext().ensureActive()
                if(!current()) return@launch
            }
            val page=(result as? NetworkAPIResult.Success)?.response
            if(page==null) mutable.update {it.copy(library=it.library.copy(loading=false,error=(result as? NetworkAPIResult.Error)?.failure?.message ?: "Couldn’t load your saved couples."))}
            else {
                page.results.forEach {faves.accept(it.coupleId,true,page.faveVersion)}
                mutable.update {it.copy(library=SavedLibrary(((if(append) prior.rows else emptyList())+page.results).distinctBy {r->r.coupleId},true,false,null,page.next,page.faveVersion))}
            }
        }
    }
    fun fave(id: Long,value: Boolean)=faves.set(id,value)
    fun retryFave(id: Long)=faves.retry(id)
    suspend fun image(url: String): ByteArray? { if(!current() || !state.value.active) return null
        val bytes=source.image(url);return if(current() && state.value.active) bytes else null }
}
