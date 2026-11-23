package com.srisu.srisu.notifications

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.features.plans.navigation.PlansDestination
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.datetime.TimeZone
import kotlin.test.*
import kotlin.time.Instant

object InboxFixtures {
    val now=Instant.parse("2026-11-23T09:00:00Z")
    fun id(number: Int)="00000000-0000-4000-8000-${number.toString().padStart(12,'0')}"
    val items=listOf(
        InboxItem(id(1),"plan.created","plans","Sam invited you to a plan","Sunset picnic · Sat, 28 Nov · 17:00 · Riverside park","2026-11-23T08:55:00Z",available=true),
        InboxItem(id(2),"spark.submitted","spark_progress","Sam answered your question","What small thing made you smile today?","2026-11-23T08:30:00Z",available=true),
        InboxItem(id(3),"challenge.progress","challenge_progress","Sam finished their part of your challenge","Make their day a little easier","2026-11-23T07:00:00Z",available=true),
        InboxItem(id(4),"moment.published","moments","Sam shared a moment","Our quiet Sunday morning, just the two of us.","2026-11-22T12:00:00Z",readAt="2026-11-22T12:10:00Z",available=true),
        InboxItem(id(5),"status.changed","status","Partner status","This update is no longer available.","2026-11-21T08:00:00Z",readAt="2026-11-22T12:10:00Z",available=false))
    val page=InboxPage(INBOX_CONTRACT,items,3,readToken="synthetic-read-boundary",serverTime=now.toString())
    val state=InboxState(items=items,loaded=true,loading=false,unreadCount=3,readToken=page.readToken)
}

open class FakeInbox : NotificationInboxSource {
    override val unreadCount=MutableStateFlow<Int?>(3)
    var result: NetworkAPIResult<InboxPage?> = NetworkAPIResult.Success(InboxFixtures.page)
    var opened=0;var reads=mutableListOf<String>()
    override suspend fun page(cursor: String?,unread: Boolean)=result
    override suspend fun readAll(through: String): NetworkAPIResult<InboxSummary?> {
        reads+=through;return NetworkAPIResult.Success(InboxSummary(INBOX_CONTRACT,0))
    }
    override suspend fun open(id: String): NetworkAPIResult<InboxOpen?> {
        opened++;return NetworkAPIResult.Success(InboxOpen(INBOX_CONTRACT,2,true,NotificationDestination("plan",coupleId=3,planId=7)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationInboxTest {
    private fun TestScope.setup() { Dispatchers.setMain(StandardTestDispatcher(testScheduler)) }
    private fun finish(vm: NotificationInboxViewModel) { vm.active(false);vm.viewModelScope.cancel();Dispatchers.resetMain() }

    @Test fun openingListDoesNotMarkReadAndNeverIncludesChats()=runTest {
        setup();val source=FakeInbox().apply { result=NetworkAPIResult.Success(InboxFixtures.page.copy(items=InboxFixtures.items+
            InboxFixtures.items[0].copy(id=InboxFixtures.id(6),kind="matrix.message"))) }
        val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try { vm.active(true);runCurrent();assertEquals(5,vm.state.value.items.size);assertEquals(0,source.opened);assertTrue(source.reads.isEmpty())
            vm.open(vm.state.value.items[0]);vm.open(vm.state.value.items[0]);runCurrent()
            assertEquals(1,source.opened);assertEquals(PlansDestination(7),vm.state.value.navigation)
            assertNotNull(vm.state.value.items[0].readAt);assertEquals(2,vm.state.value.unreadCount)
        } finally { finish(vm) }
    }
    @Test fun unavailableItemIsReadWithoutNavigatingAndReadAllUsesTheServerBoundary()=runTest {
        setup();val source=object:FakeInbox() { override suspend fun open(id:String)=NetworkAPIResult.Success<InboxOpen?>(InboxOpen(INBOX_CONTRACT,2,false)) }
        val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try { vm.active(true);runCurrent();vm.open(InboxFixtures.items[0]);runCurrent()
            assertNull(vm.state.value.navigation);assertEquals("This update is no longer available.",vm.state.value.notice)
            vm.readAll();vm.readAll();runCurrent();assertEquals(listOf(InboxFixtures.page.readToken),source.reads)
            assertTrue(vm.state.value.items.all { it.readAt!=null });assertEquals(0,vm.state.value.unreadCount)
        } finally { finish(vm) }
    }
    @Test fun backgroundAndAccountChangeClearPrivateContentAndRejectLateResults()=runTest {
        setup();val gate=CompletableDeferred<Unit>();val sessions=MomentFixtures.sessions()
        val source=object:FakeInbox() { override suspend fun page(cursor:String?,unread:Boolean):NetworkAPIResult<InboxPage?> {
            withContext(NonCancellable) {gate.await()};return result
        } }
        val vm=NotificationInboxViewModel(source,sessions)
        try { vm.active(true);runCurrent();vm.active(false);sessions.clearSession();gate.complete(Unit);runCurrent()
            assertTrue(vm.state.value.items.isEmpty());assertNull(vm.state.value.navigation)
        } finally { gate.complete(Unit);finish(vm) }
    }
    @Test fun lateOpenAfterLeavingAndReturningCannotNavigate()=runTest {
        setup();val gate=CompletableDeferred<Unit>()
        val source=object:FakeInbox() { override suspend fun open(id:String):NetworkAPIResult<InboxOpen?> {
            withContext(NonCancellable) { gate.await() };return super.open(id)
        } }
        val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try { vm.active(true);runCurrent();vm.open(InboxFixtures.items[0]);runCurrent();vm.active(false);vm.active(true);runCurrent()
            gate.complete(Unit);runCurrent();assertNull(vm.state.value.navigation)
        } finally { gate.complete(Unit);finish(vm) }
    }
    @Test fun paginationDeduplicatesAndFilterRejectsAnOlderResponse()=runTest {
        setup();val source=FakeInbox().apply {result=NetworkAPIResult.Success(InboxFixtures.page.copy(nextCursor="synthetic-next"))}
        val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try {vm.active(true);runCurrent();vm.more();runCurrent();assertEquals(5,vm.state.value.items.size)
            source.result=NetworkAPIResult.Success(InboxFixtures.page.copy(items=emptyList(),unreadCount=0))
            vm.filter(true);runCurrent();assertTrue(vm.state.value.items.isEmpty());assertTrue(vm.state.value.unreadOnly);assertNull(vm.state.value.nextCursor)
        } finally {finish(vm)}
    }
    @Test fun failureIsNotAnEmptyStateAndPermissionFailureClearsOldRows()=runTest {
        setup();val source=FakeInbox();val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try {vm.active(true);runCurrent();source.result=NetworkAPIResult.Error("Synthetic offline")
            vm.refresh();runCurrent();assertNotNull(vm.state.value.error);assertEquals(5,vm.state.value.items.size)
            source.result=NetworkAPIResult.Error(ApiError(NetworkAPIResult.ErrorType.FORBIDDEN,"forbidden","Denied",status=403))
            vm.refresh();runCurrent();assertTrue(vm.state.value.items.isEmpty());assertNull(vm.state.value.readToken)
            source.result=NetworkAPIResult.Success(InboxFixtures.page);vm.refresh();runCurrent();assertNull(vm.state.value.error)
        } finally {finish(vm)}
    }
    @Test fun datesUseTheDevicesDayAndFutureClocksDoNotShowNegativeTime() {
        assertEquals("Today",inboxDay("2026-11-22T19:00:00Z",Instant.parse("2026-11-23T00:00:00Z"),TimeZone.of("Asia/Kathmandu")))
        assertEquals("Yesterday",inboxDay("2026-11-22T12:00:00Z",InboxFixtures.now,TimeZone.UTC))
        assertEquals("Just now",inboxTime("2026-11-24T00:00:00Z",InboxFixtures.now))
        assertEquals("5m ago",inboxTime(InboxFixtures.items[0].createdAt,InboxFixtures.now))
    }
}
