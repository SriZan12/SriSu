package com.srisu.srisu.features.explore.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.explore.data.*
import com.srisu.srisu.features.moments.data.MomentSource
import com.srisu.srisu.features.moments.data.momentCursor
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds

data class ExploreFeed(val cards: List<ExploreCard> = emptyList(), val loaded: Boolean=false,
    val loading: Boolean=false,val fresh: Boolean=false,val error: String?=null,val next: String?=null,
    val emptyReason: String?=null,val coverage: String?=null,val paginationAvailable: Boolean=true,val cursorStale: Boolean=false,
    val refreshing: Boolean=false,val serverTime: String?=null)
data class SavedLibrary(val rows: List<SavedCouple> = emptyList(),val loaded: Boolean=false,
    val loading: Boolean=false,val error: String?=null,val next: String?=null,val faveVersion: Long=-1,val totalCount: Int?=null)
data class ExploreInterest(val id: Int,val label: String,val category: String)
data class FollowersState(val rows: List<PublicFollower> = emptyList(),val loaded: Boolean=false,
    val loading: Boolean=false,val error: String?=null,val next: String?=null,val coupleId: Long?=null)
data class PublicFollowState(val snapshot: PublicFollowSnapshot?=null,val loading: Boolean=false,
    val saving: Boolean=false,val error: String?=null)
data class ExploreState(val section: String="global",val scope: String="local",val global: ExploreFeed=ExploreFeed(),
    val faves: ExploreFeed=ExploreFeed(),val library: SavedLibrary=SavedLibrary(),val managing: Boolean=false,
    val active: Boolean=false,val preferences: Map<Long,FaveStatus> = emptyMap(),
    val query: String="",val interestId: Int?=null,val interests: List<ExploreInterest> = emptyList(),
    val interestsLoading: Boolean=false,val interestsError: String?=null,val savedTotal: Int?=null,
    val followersOpen: Boolean=false,val followers: FollowersState=FollowersState(),
    val following: Map<Long,PublicFollowState> = emptyMap(),val imageRevision: Long=0) {
    val feed get()=if(section=="global") global else faves
}

@OptIn(kotlin.time.ExperimentalTime::class)
class ExploreViewModel(private val source: ExploreSource,private val faves: FaveStore,
    private val sessions: SessionCoordinator,private val moments: MomentSource,
    private val catalogue: InterestCatalogueRepository?=null) : ViewModel() {
    private val stamp=sessions.stamp()
    private val mutable=MutableStateFlow(ExploreState())
    val state=mutable.asStateFlow()
    private val jobs=mutableMapOf<String,Job>()
    private val deadlines=mutableMapOf<Triple<String,Long,Long>,ComparableTimeMark>()
    private val images=linkedMapOf<String,ByteArray>()
    private val imageJobs=mutableMapOf<String,Deferred<ByteArray?>>()
    private var imageBytes=0
    private var imageGeneration=0L
    private val imagePermits=Semaphore(3)
    private var ticker: Job?=null
    private var searchJob: Job?=null
    private var favesDirty=false
    private fun discoveryPreferences() = sessions.currentSession()?.let {Triple(it.country,it.city,it.userInterests)}
    private var preferences=discoveryPreferences()
    init {
        viewModelScope.launch { sessions.state.collect { if(it!=stamp) {
            jobs.values.forEach { j->j.cancel() }; searchJob?.cancel();ticker?.cancel();deadlines.clear();clearImages();mutable.value=ExploreState()
        } } }
        viewModelScope.launch { faves.state.collect { values -> mutable.update { it.copy(preferences=if(current()) values else emptyMap()) } } }
        viewModelScope.launch { faves.changes.drop(1).collect {
            if(!current()) return@collect
            favesDirty=true
            jobs.remove("faves")?.cancel()
            jobs.remove("library")?.cancel()
            update("global") { it.copy(cursorStale=true) }; update("faves") { it.copy(cursorStale=true,loading=false,refreshing=false,fresh=false) }
            mutable.update { it.copy(library=it.library.copy(loaded=false,loading=false),savedTotal=null) }
            if(state.value.active && state.value.section=="faves") load("faves",false)
            if(state.value.active) library()
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
            if(!feed(section).loaded || (section=="faves" && favesDirty)) load(section,false) else validate(section)
            library();loadInterests()
            if(state.value.followersOpen) loadFollowers()
            ticker=viewModelScope.launch { var elapsed=0
                while(isActive) { delay(1000); prune(); if(++elapsed%30==0) when {
                    state.value.followersOpen->loadFollowers()
                    state.value.managing->library()
                    else->validate(state.value.section)
                } }
            }
        } else {
            // Keep IDs/order/scroll context, but hide cached pixels until reauthorized on return.
            searchJob?.cancel();jobs.values.forEach {it.cancel()};jobs.clear();clearImages()
            update("global") {it.copy(fresh=false,loading=false,refreshing=false)};update("faves") {it.copy(fresh=false,loading=false,refreshing=false)}
            mutable.update {it.copy(library=it.library.copy(loading=false,loaded=false),interestsLoading=false,
                followers=it.followers.copy(loading=false,loaded=false),following=emptyMap())}
        }
    }
    fun select(section: String) {
        if(section !in listOf("global","faves") || !current() || section==state.value.section) return
        mutable.update {it.copy(section=section)}
        searchJob?.cancel()
        // A retained tab can have changed permissions while the other tab was open.
        // Keep its geometry/scroll state, but reauthorize before showing its content.
        update(section) {it.copy(fresh=false)}
        clearImages()
        if(!feed(section).loaded || section=="faves" && favesDirty) load(section,false)
        else validate(section)
    }
    private fun filters(section: String)=if(section=="global") state.value.query.trim().replace(Regex("\\s+")," ") to state.value.interestId else "" to null
    fun setQuery(value: String) {
        val bounded=value.take(80).let { if(it.lastOrNull()?.isHighSurrogate()==true) it.dropLast(1) else it }
        if(!current() || bounded==state.value.query) return
        mutable.update {it.copy(query=bounded)}
        invalidateSearch(debounce=true)
    }
    fun selectInterest(id: Int?) {
        if(!current() || id==state.value.interestId || id!=null && id<=0) return
        mutable.update {it.copy(interestId=id)}
        invalidateSearch(debounce=false)
    }
    private fun invalidateSearch(debounce: Boolean) {
        searchJob?.cancel();jobs.remove("global")?.cancel()
        clearImages()
        deadlines.keys.removeAll {it.first=="global"}
        update("global") {ExploreFeed()}
        if(state.value.active && state.value.section=="global") searchJob=viewModelScope.launch {
            if(debounce) delay(300)
            load("global",false)
        }
    }
    fun loadInterests(force: Boolean=false) {
        val repository=catalogue ?: return
        if(!current() || jobs["interests"]?.isActive==true || !force && state.value.interests.isNotEmpty()) return
        mutable.update {it.copy(interestsLoading=true,interestsError=null)}
        jobs["interests"]=viewModelScope.launch {
            val result=repository.load(force)
            currentCoroutineContext().ensureActive();if(!current()) return@launch
            val values=result.value?.interests.orEmpty().mapNotNull {entry->
                val id=entry?.id ?: return@mapNotNull null; val label=entry.name?.trim().orEmpty()
                if(id<=0 || label.isBlank()) null else ExploreInterest(id,label,entry.category?.label ?: entry.category?.name.orEmpty())
            }.distinctBy {it.id}
            mutable.update {it.copy(interests=values,interestsLoading=false,interestsError=result.error?.message)}
        }
    }
    fun broader() { jobs.remove("global")?.cancel(); mutable.update {it.copy(scope="broad")};load("global",false) }
    /** The refresh indicator is exclusively for an explicit pull/accessibility action. */
    fun refresh(section: String=state.value.section) {
        searchJob?.cancel();jobs.remove(section)?.cancel();load(section,false,manual=true)
        if(state.value.active) library()
    }
    fun more()=load(state.value.section,true)
    private fun load(section: String,more: Boolean,manual: Boolean=false) {
        if(!current() || jobs[section]?.isActive==true) return
        val revision=faves.changes.value
        val prior=feed(section)
        val scope=state.value.scope
        val filter=filters(section)
        if(more && (prior.next==null || prior.cursorStale)) return
        val cursor=if(more) momentCursor(prior.next) else null
        if(!more) clearImages()
        update(section) {it.copy(loading=true,refreshing=manual,error=null)}
        jobs[section]=viewModelScope.launch {
            val began=TimeSource.Monotonic.markNow()
            val result=source.feed(section,scope,cursor,filter.first,filter.second)
            currentCoroutineContext().ensureActive()
            if(!current() || filter!=filters(section)) return@launch
            val page=(result as? NetworkAPIResult.Success)?.response
            if(page==null) { failed(section,result);return@launch }
            applyPage(section,page,began,more,revision,manual)
        }
    }
    private fun applyPage(section: String,page: ExplorePage,began: ComparableTimeMark,more: Boolean,revision: Long,manual: Boolean=false) {
        if(!more) deadlines.keys.removeAll {it.first==section}
        accept(section,page,began)
        update(section) {it.copy(cards=((if(more) it.cards else emptyList())+page.results).distinctBy {r->r.couple.id},
            loaded=true,loading=false,refreshing=false,fresh=true,error=null,next=page.next,emptyReason=page.emptyReason,
            coverage=page.coverage,paginationAvailable=page.paginationAvailable,cursorStale=faves.changes.value!=revision,serverTime=page.serverTime)}
        if(section=="faves") favesDirty=false
        prune()
        // Keep existing pixels throughout the gesture/request. Recreate URL-keyed
        // image effects after an explicit successful refresh, including failed images.
        if(manual) mutable.update {it.copy(imageRevision=it.imageRevision+1)}
    }
    private fun validate(section: String) {
        if(!current() || jobs[section]?.isActive==true || !feed(section).loaded) return
        clearImages()
        // With no retained IDs there is nothing to validate: query for new content.
        if(feed(section).cards.isEmpty()) {load(section,false);return}
        jobs[section]=viewModelScope.launch {
            val filter=filters(section)
            val revision=faves.changes.value
            val original=feed(section).cards
            val checked=mutableMapOf<Long,ExploreCard>()
            var serverTime: String?=null
            for(batch in original.map {it.couple.id}.chunked(50)) {
                val began=TimeSource.Monotonic.markNow()
                val result=source.validate(batch,section,filter.first,filter.second)
                currentCoroutineContext().ensureActive()
                if(!current() || filter!=filters(section)) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) {failed(section,result);return@launch}
                accept(section,page,began);page.results.forEach {checked[it.couple.id]=it};serverTime=page.serverTime
            }
            if(checked.isEmpty()) {
                // Recheck membership and supply, so removing the last Fave elsewhere
                // cannot leave the misleading "saved, no active Moments" empty state.
                val began=TimeSource.Monotonic.markNow()
                val result=source.feed(section,state.value.scope,null,filter.first,filter.second)
                currentCoroutineContext().ensureActive()
                if(!current() || filter!=filters(section)) return@launch
                val page=(result as? NetworkAPIResult.Success)?.response
                if(page==null) {failed(section,result);return@launch}
                applyPage(section,page,began,false,revision)
                return@launch
            }
            update(section) {it.copy(cards=original.mapNotNull {old->checked[old.couple.id]?.copy(reason=old.reason)},fresh=true,error=null,loading=false,refreshing=false,serverTime=serverTime)}
            prune()
        }
    }
    private fun accept(section: String,page: ExplorePage,began: ComparableTimeMark) {
        val now=TimeSource.Monotonic.markNow()
        page.results.forEach {card->
            faves.accept(card.couple.id,card.isFaved,page.faveVersion)
            card.visiblePreviews.forEach {preview->
                val left=runCatching {(Instant.parse(preview.expiresAt)-Instant.parse(page.serverTime)).inWholeMilliseconds-began.elapsedNow().inWholeMilliseconds}.getOrDefault(0)
                deadlines[Triple(section,card.couple.id,preview.id)]=now+left.milliseconds
            }
        }
    }
    private fun prune() {
        var expired=false
        for(section in listOf("global","faves")) update(section) {it.copy(cards=it.cards.mapNotNull {card->
            val previews=card.visiblePreviews.filter {preview->deadlines[Triple(section,card.couple.id,preview.id)]?.hasPassedNow()!=true}
            if(previews.size!=card.visiblePreviews.size) expired=true
            previews.firstOrNull()?.let {card.copy(preview=it,previews=previews)}
        })}
        if(expired) clearImages()
    }
    private fun failed(section: String,result: NetworkAPIResult<*>) {
        clearImages()
        val error=(result as? NetworkAPIResult.Error)?.failure
        update(section) {it.copy(loading=false,refreshing=false,fresh=false,error=error?.message ?: "Couldn’t load Explore. Please retry.",
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
        if(!more) clearImages()
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
            var page=(result as? NetworkAPIResult.Success)?.response
            // Reauthorize the already-read depth, bounded to five pages. Never retain
            // earlier identities after an error or a revision change during the walk.
            if(!append && page!=null) {
                val first=requireNotNull(page)
                var collected=first.results
                var tail=first
                val visited=mutableSetOf<String>();var pages=1
                while(collected.size<prior.rows.size && tail.next!=null && pages<5) {
                    val cursor=momentCursor(tail.next)
                    if(cursor==null || !visited.add(cursor)) {result=NetworkAPIResult.Error("Couldn’t refresh saved couples. Please retry.");page=null;break}
                    result=source.saved(cursor)
                    currentCoroutineContext().ensureActive();if(!current()) return@launch
                    val next=(result as? NetworkAPIResult.Success)?.response
                    if(next==null) {page=null;break}
                    if(next.faveVersion!=first.faveVersion) {result=NetworkAPIResult.Error("Your saved couples changed. Refresh to see the latest list.");page=null;break}
                    collected=(collected+next.results).distinctBy {it.coupleId};tail=next;pages++
                    page=first.copy(results=collected,next=tail.next)
                }
            }
            val confirmedPage=page
            if(confirmedPage==null) {
                val error=(result as? NetworkAPIResult.Error)?.failure
                val clear=!append || error?.status in listOf(401,403,404)
                mutable.update {it.copy(library=it.library.copy(loading=false,
                    rows=if(clear) emptyList() else it.library.rows,loaded=!clear && it.library.loaded,
                    next=if(clear) null else it.library.next,error=error?.message ?: "Couldn’t load your saved couples."),
                    savedTotal=if(clear) null else it.savedTotal)}
            }
            else {
                confirmedPage.results.forEach {faves.accept(it.coupleId,true,confirmedPage.faveVersion)}
                val total=confirmedPage.totalCount ?: if(confirmedPage.next==null) ((if(append) prior.rows else emptyList())+confirmedPage.results).distinctBy {it.coupleId}.size else null
                mutable.update {it.copy(library=SavedLibrary(((if(append) prior.rows else emptyList())+confirmedPage.results).distinctBy {r->r.coupleId},true,false,null,confirmedPage.next,confirmedPage.faveVersion,total),savedTotal=total)}
            }
        }
    }
    fun fave(id: Long,value: Boolean)=faves.set(id,value)
    fun retryFave(id: Long)=faves.retry(id)
    fun showFollowers(value: Boolean) {
        if(!current()) return
        mutable.update {it.copy(followersOpen=value)}
        if(value) loadFollowers() else {
            jobs.remove("followers")?.cancel()
            mutable.update {it.copy(followers=FollowersState())}
        }
    }
    fun loadFollowers(more: Boolean=false) {
        if(!current() || jobs["followers"]?.isActive==true) return
        val prior=state.value.followers
        if(more && (!prior.loaded || prior.next==null)) return
        if(!more) clearImages()
        mutable.update {it.copy(followers=it.followers.copy(loading=true,loaded=more && prior.loaded,error=null))}
        jobs["followers"]=viewModelScope.launch {
            var append=more
            var result=source.followers(if(more) momentCursor(prior.next) else null)
            currentCoroutineContext().ensureActive();if(!current()) return@launch
            val continuation=(result as? NetworkAPIResult.Success)?.response
            if(more && (continuation?.coupleId!=prior.coupleId || (result as? NetworkAPIResult.Error)?.failure?.status in listOf(409,410))) {
                append=false
                mutable.update {it.copy(followers=FollowersState(loading=true))}
                result=source.followers(null)
                currentCoroutineContext().ensureActive();if(!current()) return@launch
            }
            var page=(result as? NetworkAPIResult.Success)?.response
            if(!append && page!=null) {
                val first=requireNotNull(page);var collected=first.results;var tail=first
                val visited=mutableSetOf<String>();var pages=1
                while(collected.size<prior.rows.size && tail.next!=null && pages<5) {
                    val cursor=momentCursor(tail.next)
                    if(cursor==null || !visited.add(cursor)) {result=NetworkAPIResult.Error("Couldn’t refresh followers. Please retry.");page=null;break}
                    result=source.followers(cursor)
                    currentCoroutineContext().ensureActive();if(!current()) return@launch
                    val next=(result as? NetworkAPIResult.Success)?.response
                    if(next==null) {page=null;break}
                    if(next.coupleId!=first.coupleId) {result=NetworkAPIResult.Error("Your connection changed. Refresh to see the latest list.");page=null;break}
                    collected=(collected+next.results).distinctBy {it.userId};tail=next;pages++
                    page=first.copy(results=collected,next=tail.next)
                }
            }
            val confirmedPage=page
            mutable.update {it.copy(followers=if(confirmedPage!=null) FollowersState(
                ((if(append) prior.rows else emptyList())+confirmedPage.results).distinctBy {row->row.userId},
                loaded=true,next=confirmedPage.next,coupleId=confirmedPage.coupleId)
            else FollowersState(error=(result as? NetworkAPIResult.Error)?.failure?.message ?: "Couldn’t load followers. Please retry."))}
        }
    }
    private fun updateFollow(id: Long,change: (PublicFollowState)->PublicFollowState) {
        mutable.update {it.copy(following=it.following+(id to change(it.following[id] ?: PublicFollowState())))}
    }
    fun loadFollow(id: Long) {
        val key="follow:$id"
        if(id<=0 || !current() || jobs[key]?.isActive==true) return
        updateFollow(id) {PublicFollowState(loading=true)}
        jobs[key]=viewModelScope.launch {
            val result=source.followState(id)
            currentCoroutineContext().ensureActive();if(!current()) return@launch
            updateFollow(id) {publicFollowResult(id,result)}
        }
    }
    /** Public following is a separate disclosure, never inferred from a private Fave. */
    fun follow(id: Long,desired: Boolean,shareIdentity: Boolean=false) {
        val key="follow:$id"
        if(id<=0 || !current() || jobs[key]?.isActive==true) return
        if(desired && !shareIdentity) {
            updateFollow(id) {it.copy(error="Confirm sharing your identity before following.")};return
        }
        updateFollow(id) {it.copy(saving=true,error=null)}
        jobs[key]=viewModelScope.launch {
            val result=changePublicFollow(source,id,desired,shareIdentity) {sessions.ensureCurrent(stamp)}
            updateFollow(id) {result}
        }
    }
    private fun clearImages() {
        imageGeneration++;images.clear();imageBytes=0
        val pending=imageJobs.values.toList();imageJobs.clear();pending.forEach {it.cancel()}
    }
    private fun rememberImage(url:String,bytes:ByteArray) {
        val maxBytes=8*1024*1024
        if(bytes.size>maxBytes) return
        images.remove(url)?.let {imageBytes-=it.size}
        images[url]=bytes;imageBytes+=bytes.size
        while(imageBytes>maxBytes || images.size>32) {
            val key=images.keys.first();imageBytes-=images.remove(key)!!.size
        }
    }
    /** Memory only, bounded to 8 MiB / 32 images for this foreground authorization
     * generation. Strip and card consumers share one protected HTTP read. */
    suspend fun image(url: String): ByteArray? {
        while(current() && state.value.active) {
            currentCoroutineContext().ensureActive()
            val own=imageGeneration
            images.remove(url)?.let {images[url]=it;return it}
            val request=imageJobs[url] ?: viewModelScope.async(start=CoroutineStart.LAZY) {
                val bytes=imagePermits.withPermit {source.image(url)}
                currentCoroutineContext().ensureActive()
                if(bytes!=null && own==imageGeneration && current() && state.value.active) rememberImage(url,bytes)
                bytes
            }.also {request->
                imageJobs[url]=request
                request.invokeOnCompletion {if(imageJobs[url]===request) imageJobs.remove(url)}
                request.start()
            }
            val bytes=try {request.await()} catch(cancelled:CancellationException) {
                currentCoroutineContext().ensureActive()
                if(own!=imageGeneration) continue else throw cancelled
            }
            if(own==imageGeneration) return if(current() && state.value.active) bytes else null
        }
        return null
    }
}
