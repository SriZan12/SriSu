package com.srisu.srisu.features.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.navigation.graph.Route
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

data class InboxState(val items: List<InboxItem> = emptyList(), val unreadOnly: Boolean = false,
    val loading: Boolean = true, val loadingMore: Boolean = false, val loaded: Boolean = false,
    val unreadCount: Int = 0, val nextCursor: String? = null, val readToken: String? = null,
    val error: String? = null, val pageError: String? = null, val notice: String? = null,
    val busy: String? = null, val navigation: Route? = null)

class NotificationInboxViewModel(private val source: NotificationInboxSource,
    private val sessions: SessionCoordinator) : ViewModel() {
    private val stamp = sessions.stamp()
    private val mutable = MutableStateFlow(InboxState())
    val state = mutable.asStateFlow()
    private var active = false
    private var work: Job? = null
    private var action: Job? = null
    private var generation = 0L
    private val images = linkedMapOf<String, ByteArray>()
    private val imageSlots = Semaphore(3)
    private var imageGeneration = 0L
    init { viewModelScope.launch { sessions.state.collect { if (it != stamp) clear() } } }
    private fun current() = active && sessions.stamp() == stamp
    private fun clear() {
        imageGeneration++; images.clear()
        generation++; work?.cancel(); action?.cancel()
        mutable.value = InboxState(unreadOnly=state.value.unreadOnly, loading=false)
    }
    private fun accessDenied() {
        imageGeneration++; images.clear(); generation++; work?.cancel()
        mutable.value=InboxState(unreadOnly=state.value.unreadOnly,loading=false,
            error="Notifications aren’t available. Please refresh or sign in again.")
    }
    fun active(value: Boolean) {
        active=value
        if (value) refresh() else clear()
    }
    fun filter(unread: Boolean) {
        if (state.value.unreadOnly == unread || state.value.busy != null) return
        generation++; imageGeneration++; images.clear(); work?.cancel()
        mutable.value=InboxState(unreadOnly=unread)
        refresh()
    }
    fun refresh() { if (current() && state.value.busy == null) { imageGeneration++; images.clear(); load(false) } }
    fun more() {
        if (current() && state.value.nextCursor != null && !state.value.loading && !state.value.loadingMore && state.value.busy == null) load(true)
    }
    private fun load(append: Boolean) {
        work?.cancel(); val version=++generation
        val cursor=if(append) state.value.nextCursor else null
        val unread=state.value.unreadOnly
        work=viewModelScope.launch {
            mutable.update { it.copy(loading=!append,loadingMore=append,error=null,pageError=null,notice=null) }
            val result=source.page(cursor,unread)
            if (!current() || version!=generation) return@launch
            val page=(result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract==INBOX_CONTRACT }
            if (page != null) {
                // Even a malformed/older server cannot put conversations into this surface.
                val items=page.items.filter { validNotificationId(it.id) && it.kind in inboxKinds }
                mutable.update { it.copy(items=(if(append) it.items+items else items).distinctBy(InboxItem::id),
                    unreadCount=page.unreadCount, nextCursor=page.nextCursor, readToken=if(append) it.readToken else page.readToken,
                    loaded=true,loading=false,loadingMore=false) }
            } else {
                val denied=(result as? NetworkAPIResult.Error)?.failure?.status in listOf(401,403,404)
                if (denied) { imageGeneration++; images.clear() }
                val message=if(denied) "Notifications aren’t available. Please refresh or sign in again." else "Couldn’t load notifications. Check your connection and try again."
                mutable.update { it.copy(items=if(denied) emptyList() else it.items, loading=false,loadingMore=false,
                    error=if(append && !denied) null else message,pageError=if(append && !denied) message else null,
                    nextCursor=if(denied) null else it.nextCursor,readToken=if(denied) null else it.readToken) }
            }
        }
    }
    fun open(item: InboxItem) {
        if (!current() || state.value.busy!=null || !validNotificationId(item.id) || item.kind !in inboxKinds ||
            state.value.items.none {it.id==item.id && it.kind==item.kind}) return
        work?.cancel();val version=++generation
        mutable.update { it.copy(busy=item.id,notice=null,loading=false,loadingMore=false) }
        action=viewModelScope.launch {
            val result=source.open(item.id)
            if (!current() || version!=generation) return@launch
            val response=(result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract==INBOX_CONTRACT }
            if (response != null) {
                val route=response.destination?.route()?.takeUnless { it is ChatNav.CoupleConversation }
                if (!response.available) images.keys.filter { it.startsWith("${item.id}/") }.forEach(images::remove)
                mutable.update { it.copy(busy=null,unreadCount=response.unreadCount,
                    items=it.items.map { row -> if(row.id!=item.id) row else if(response.available)
                        row.copy(readAt=Clock.System.now().toString(),available=true)
                        else row.copy(readAt=Clock.System.now().toString(),available=false,title="Update unavailable",body="",
                            actorName=null,actorId=null,actorRealName=null,avatar=false,thumbnail=false,segments=emptyList()) }
                        .filter { row -> !it.unreadOnly || row.readAt==null },
                    navigation=route.takeIf { response.available },
                    notice=if(!response.available || route==null) "This update is no longer available." else null) }
            } else {
                val status=(result as? NetworkAPIResult.Error)?.failure?.status
                if(status in listOf(401,403)) {accessDenied();return@launch}
                val gone=status in listOf(404,410)
                if(gone) images.keys.filter {it.startsWith("${item.id}/")}.forEach(images::remove)
                mutable.update { it.copy(busy=null,items=if(gone) it.items.filterNot { row -> row.id==item.id } else it.items,
                    notice=if(gone) "This update is no longer available." else "Couldn’t open this update. Tap it to try again.") }
            }
        }
    }
    fun readAll() {
        val token=state.value.readToken ?: return
        if (!current() || state.value.busy!=null || state.value.loading || state.value.loadingMore) return
        val version=++generation
        mutable.update { it.copy(busy="read-all",notice=null) }
        action=viewModelScope.launch {
            val result=source.readAll(token)
            if (!current() || version!=generation) return@launch
            val response=(result as? NetworkAPIResult.Success)?.response?.takeIf { it.contract==INBOX_CONTRACT }
            val status=(result as? NetworkAPIResult.Error)?.failure?.status
            if(status in listOf(401,403)) {accessDenied();return@launch}
            mutable.update { old -> if(response != null) old.copy(busy=null,unreadCount=response.unreadCount,
                items=if(old.unreadOnly) emptyList() else old.items.map { it.copy(readAt=it.readAt ?: Clock.System.now().toString()) },
                nextCursor=if(old.unreadOnly) null else old.nextCursor,
                notice=if(response.unreadCount==0) "All notifications marked as read." else "Marked as read. New notifications have arrived.")
            else old.copy(busy=null,readToken=if(status==400) null else old.readToken,
                notice=if(status==400) "Refresh notifications before marking them as read."
                    else "Couldn’t mark notifications as read. Please try again.") }
            // A snapshot intentionally leaves new arrivals unread. Fetch them instead
            // of showing an empty 'caught up' screen with a nonzero badge.
            if(response != null && response.unreadCount>0 && state.value.unreadOnly) refresh()
        }
    }
    fun navigated() { mutable.update { it.copy(navigation=null) } }
    suspend fun image(id: String, slot: String): ByteArray? {
        val version = imageGeneration
        val key = "$id/$slot"
        if (!current() || state.value.items.none { it.id==id && it.available }) return null
        images[key]?.let { return it }
        return imageSlots.withPermit {
            if (!current() || version != imageGeneration || state.value.items.none { it.id==id && it.available }) return@withPermit null
            val bytes = source.image(id, slot)
            if (!current() || version != imageGeneration || state.value.items.none { it.id==id && it.available }) return@withPermit null
            if (bytes != null) {
                if (images.size >= 32) images.remove(images.keys.first())
                images[key] = bytes
            }
            bytes
        }
    }
}

internal val inboxKinds = setOf("invite.received","invite.accepted","sharing.proposed","sharing.approved",
    "nickname.changed",
    "plan.created","plan.responded","plan.cancelled","spark.started","spark.submitted","spark.revealed",
    "challenge.invited","challenge.responded","challenge.progress","challenge.completed","challenge.cancelled",
    "moment.published","moment.hearted","moment.appreciated","moment.replied","status.changed","idea.created","idea.interested")
