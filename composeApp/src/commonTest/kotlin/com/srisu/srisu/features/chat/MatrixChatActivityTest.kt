package com.srisu.srisu.features.chat

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.*
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.TypingEventContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixChatActivityTest {
    @Test fun unknownIsNeverReportedAsOffline() {
        assertEquals(MatrixPartnerPresence.UNKNOWN, null.toPartnerPresence())
        assertEquals(MatrixPartnerPresence.ONLINE, Presence.ONLINE.toPartnerPresence())
        assertEquals(MatrixPartnerPresence.OFFLINE, Presence.OFFLINE.toPartnerPresence())
        assertEquals(MatrixPartnerPresence.AWAY, Presence.UNAVAILABLE.toPartnerPresence())
    }

    @Test fun initialTypingIsRetainedButDisconnectedAndUnrelatedActivityIsNotShown() = runTest {
        val room = RoomId("!couple:example.test")
        val other = RoomId("!other:example.test")
        val partner = UserId("@partner:example.test")
        val me = UserId("@me:example.test")
        val events = MutableSharedFlow<ClientEvent<TypingEventContent>>()
        val sync = MutableStateFlow(SyncState.STOPPED)
        val foreground = MutableStateFlow(true)
        val presence = MutableStateFlow(MatrixPartnerPresence.ONLINE)
        val typing = trackMatrixTyping(backgroundScope, events, sync, foreground)
        sync.value = SyncState.INITIAL_SYNC; runCurrent()
        events.emit(ClientEvent.EphemeralEvent(TypingEventContent(setOf(partner)), roomId = room)); runCurrent()
        sync.value = SyncState.RUNNING
        var activity = MatrixPartnerActivity()
        backgroundScope.launch { partnerActivity(room, partner, presence, typing, sync, foreground).collect { activity = it } }
        runCurrent()
        assertTrue(activity.typing) // Snapshot arrived before the conversation observer.
        events.emit(ClientEvent.EphemeralEvent(TypingEventContent(setOf(me)), roomId = room)); runCurrent()
        assertFalse(activity.typing)
        events.emit(ClientEvent.EphemeralEvent(TypingEventContent(setOf(partner)), roomId = other)); runCurrent()
        assertFalse(activity.typing)
        events.emit(ClientEvent.EphemeralEvent(TypingEventContent(setOf(partner)), roomId = room)); runCurrent()
        assertTrue(activity.typing)
        sync.value = SyncState.ERROR; runCurrent()
        assertEquals(MatrixPartnerActivity(), activity)
        assertTrue(typing.value.isEmpty())
        sync.value = SyncState.RUNNING; runCurrent()
        assertFalse(activity.typing) // No resurrected cached indicator after reconnect.
        events.emit(ClientEvent.EphemeralEvent(TypingEventContent(setOf(partner)), roomId = room)); runCurrent()
        foreground.value = false; runCurrent()
        assertEquals(MatrixPartnerActivity(), activity)
        foreground.value = true; runCurrent()
        assertFalse(activity.typing)
        presence.value = MatrixPartnerPresence.OFFLINE; runCurrent()
        assertEquals(MatrixPartnerPresence.OFFLINE, activity.presence)
        events.emit(ClientEvent.EphemeralEvent(TypingEventContent(emptySet()), roomId = room)); runCurrent()
        assertFalse(activity.typing)
    }

    @Test fun typingIsThrottledRefreshedAndClearedAfterIdle() = runTest {
        val sent = mutableListOf<Pair<String, Boolean>>()
        val publisher = MatrixTypingPublisher<String>(backgroundScope, { true }) { room, typing -> sent += room to typing }
        publisher.activity("room"); runCurrent()
        repeat(9) { advanceTimeBy(100); publisher.activity("room"); runCurrent() }
        assertEquals(listOf("room" to true), sent)
        advanceTimeBy(2_100); runCurrent()
        assertEquals(listOf(true, true), sent.map { it.second })
        advanceTimeBy(2_900); runCurrent()
        assertEquals(listOf(true, true, false), sent.map { it.second })
        advanceTimeBy(20_000); runCurrent()
        assertEquals(3, sent.size)
    }

    @Test fun roomReplacementOrdersStopBeforeStartAndRejectsStaleOwner() = runTest {
        var current = "first"
        val sent = mutableListOf<Pair<String, Boolean>>()
        val publisher = MatrixTypingPublisher<String>(backgroundScope, { it == current }) { room, typing ->
            delay(100); sent += room to typing
        }
        publisher.activity("first"); runCurrent()
        current = "second"; publisher.activity("second")
        advanceTimeBy(400); runCurrent()
        assertEquals(listOf("first" to true, "first" to false, "second" to true), sent)
        current = "closed"
        advanceTimeBy(3_000); runCurrent()
        assertEquals("second" to false, sent.last())
        assertEquals(4, sent.size)
    }

    @Test fun networkFailureDoesNotKillThePublisherAndStopCancelsRefresh() = runTest {
        val sent = mutableListOf<Boolean>()
        var fail = true
        val publisher = MatrixTypingPublisher<String>(backgroundScope, { true }) { _, typing ->
            if (fail) { fail = false; error("Synthetic outage") }
            sent += typing
        }
        publisher.activity("room"); runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf(true), sent)
        publisher.stop(); runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf(true, false), sent)
    }

    @Test fun composerClearsTypingOnEmptySendBlurAndLeaving() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = ChatFixtureRepository(ChatFixtures.empty())
        val signals = mutableListOf<Boolean>()
        val repo = object : MatrixChatRepository by base {
            override fun setTyping(roomId: String, owner: MatrixChatRoomOwner, typing: Boolean) { signals += typing }
        }
        val vm = SecureChatViewModel(repo)
        try {
            vm.openRoom(ChatFixtures.ROOM_ID); runCurrent()
            assertTrue(signals.isEmpty())
            vm.updateMessageInput("Hello"); assertTrue(signals.last())
            vm.updateMessageInput(""); assertFalse(signals.last())
            vm.updateMessageInput("Hello"); vm.composerFocusChanged(false); assertFalse(signals.last())
            vm.updateMessageInput("Hello again"); vm.destinationVisible(false); assertFalse(signals.last())
            vm.updateMessageInput("Send this"); vm.sendMessage(); runCurrent()
            assertFalse(signals.last()); assertEquals("", vm.messageInput.value)
        } finally { vm.closeRoom(); vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
