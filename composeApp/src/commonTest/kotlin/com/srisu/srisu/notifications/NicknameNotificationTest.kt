package com.srisu.srisu.notifications

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import com.srisu.srisu.features.moments.MomentFixtures
import com.srisu.srisu.features.notifications.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Synthetic server response: the chosen nickname is literal, never an actor-name span. */
object NicknameNotificationFixtures {
    val item = Json.decodeFromString<InboxItem>("""{
        "id":"00000000-0000-4000-8000-000000000026",
        "kind":"nickname.changed","category":"requests",
        "title":"Sam gave you a nickname","body":"You’re now “Sam” to them.",
        "created_at":"2026-11-23T08:55:00Z","available":true,
        "actor_id":2,"actor_name":"Sam","actor_real_name":"Sam",
        "avatar":false,"thumbnail":false,
        "segments":[{"text":"Sam","bold":true,"user_id":2,"user_name":"Sam"},
                    {"text":" gave you a nickname","bold":false},
                    {"text":" · You’re now “Sam” to them.","bold":false}]
    }""")
    val page = InboxFixtures.page.copy(items=listOf(item),unreadCount=1)
    val state = InboxFixtures.state.copy(items=listOf(item),unreadCount=1)
}

@OptIn(ExperimentalCoroutinesApi::class)
class NicknameNotificationTest {
    @Test fun inboxAcceptsNicknameAlertsAndOpensOnlyTheAuthorizedCoupleDestination() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:FakeInbox() {
            override suspend fun open(id:String):NetworkAPIResult<InboxOpen?> {
                opened++
                assertEquals(NicknameNotificationFixtures.item.id,id)
                return NetworkAPIResult.Success(InboxOpen(INBOX_CONTRACT,0,true,NotificationDestination("couple",coupleId=3)))
            }
        }.apply {result=NetworkAPIResult.Success(NicknameNotificationFixtures.page)}
        val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try {
            vm.active(true);runCurrent()
            assertEquals(listOf(NicknameNotificationFixtures.item),vm.state.value.items)
            val row=vm.state.value.items.single()
            assertEquals("requests",row.category)
            assertEquals(2L,row.segments.first().userId)
            assertNull(row.segments.last().userId)
            assertEquals(" · You’re now “Sam” to them.",row.segments.last().text)
            vm.open(row);vm.open(row);runCurrent()
            assertEquals(1,source.opened)
            assertEquals(CoupleProfileDestination(3),vm.state.value.navigation)
            assertNotNull(vm.state.value.items.single().readAt)
            assertEquals(0,vm.state.value.unreadCount)
        } finally {vm.active(false);vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }

    @Test fun clearedOrSupersededNicknameCannotNavigateOrRetainItsOldCopy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=object:FakeInbox() {
            override suspend fun open(id:String):NetworkAPIResult<InboxOpen?> =
                NetworkAPIResult.Success(InboxOpen(INBOX_CONTRACT,0,false))
        }.apply {result=NetworkAPIResult.Success(NicknameNotificationFixtures.page)}
        val vm=NotificationInboxViewModel(source,MomentFixtures.sessions())
        try {
            vm.active(true);runCurrent();vm.open(vm.state.value.items.single());runCurrent()
            assertNull(vm.state.value.navigation)
            val row=vm.state.value.items.single()
            assertFalse(row.available);assertEquals("Update unavailable",row.title)
            assertEquals("",row.body);assertTrue(row.segments.isEmpty())
            assertNull(row.actorName);assertNull(row.actorId);assertNull(row.actorRealName)
            assertEquals("This update is no longer available.",vm.state.value.notice)
        } finally {vm.active(false);vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }
}
