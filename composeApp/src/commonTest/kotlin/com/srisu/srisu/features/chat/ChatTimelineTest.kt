package com.srisu.srisu.features.chat

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.ChatPartnerSource
import com.srisu.srisu.features.chat.matrix.MatrixChatMessage
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import com.srisu.srisu.features.chat.matrix.presentation.ChatDayLabel
import com.srisu.srisu.features.chat.matrix.presentation.ChatTimelineItem
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.features.chat.matrix.presentation.buildChatTimeline
import com.srisu.srisu.features.chat.matrix.presentation.chatDayLabel
import com.srisu.srisu.features.chat.matrix.presentation.formatChatDate
import com.srisu.srisu.features.chat.matrix.presentation.formatChatTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatTimelineTest {
    private val zone = TimeZone.of("Asia/Kathmandu")
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun messages(items: List<ChatTimelineItem>) = items.filterIsInstance<ChatTimelineItem.Message>()

    @Test
    fun groupsSameSenderWithinWindowAndSplitsOnSenderChange() {
        val timeline = messages(buildChatTimeline(ChatFixtures.conversationMessages(), zone))
        // e1,e2 partner (joined); e3,e4 mine (joined); e5 partner alone.
        assertEquals(listOf(false, true, false, true, false), timeline.take(5).map { it.joinsPrevious })
        assertEquals(listOf(true, false, true, false, false), timeline.take(5).map { it.joinsNext })
        assertEquals(listOf(false, true, false, true, true), timeline.take(5).map { it.showMeta })
    }

    @Test
    fun standaloneCardsAndVoiceDoNotJoinTextGroups() {
        val timeline = messages(buildChatTimeline(ChatFixtures.pure().messages, zone))
        val spark = timeline.single { it.message.sparkReference != null }
        val voice = timeline.single { it.message.attachment != null }
        assertFalse(spark.joinsPrevious)
        assertFalse(spark.joinsNext)
        assertFalse(voice.joinsPrevious)
        assertTrue(timeline.single { it.message.id == "pure-4" }.joinsPrevious)
    }

    @Test
    fun insertsOneDayHeaderPerLocalDate() {
        val items = buildChatTimeline(ChatFixtures.conversationMessages(), zone)
        val headers = items.filterIsInstance<ChatTimelineItem.DayHeader>().map { it.date }
        assertEquals(listOf(LocalDate(2026, 10, 16), LocalDate(2026, 10, 17)), headers)
        // A day boundary never joins bubbles, even for the same sender.
        val firstToday = messages(items).first { it.message.id == "e6" }
        assertFalse(firstToday.joinsPrevious)
    }

    @Test
    fun failedAndPendingMessagesAlwaysShowStatusAndFailedBreaksGroup() {
        val timeline = messages(buildChatTimeline(ChatFixtures.deliveryStates().messages, zone))
        val pending = timeline.single { it.message.delivery == MatrixMessageDelivery.SENDING }
        val failed = timeline.single { it.message.delivery == MatrixMessageDelivery.FAILED }
        assertTrue(pending.showMeta)
        assertTrue(failed.showMeta)
        assertFalse(failed.joinsNext)
    }

    @Test
    fun keysStayStableAcrossOutboxAcknowledgementAndAreUnique() {
        val pending = MatrixChatMessage(
            id = "txn:abc", body = "hi", senderMatrixId = "@me:example.test", mine = true,
            timestampMillis = 1_000, delivery = MatrixMessageDelivery.SENDING, transactionId = "abc",
        )
        val acknowledged = pending.copy(id = "\$event", delivery = MatrixMessageDelivery.SENT)
        assertEquals(pending.stableKey, acknowledged.stableKey)

        // During an outbox/timeline race the same transaction can appear twice; keys must stay unique.
        val items = buildChatTimeline(listOf(acknowledged, pending), zone)
        val keys = items.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(1, messages(items).size)
    }

    @Test
    fun partnerMessagesWithoutTransactionKeepEventIdentity() {
        val partner = ChatFixtures.conversationMessages().first()
        assertNull(partner.transactionId)
        assertEquals(partner.id, partner.stableKey)
    }

    @Test
    fun formatsTimesAndDays() {
        assertEquals("12:05 AM", formatChatTime(LocalDateTime(2026, 10, 17, 0, 5)))
        assertEquals("8:02 AM", formatChatTime(LocalDateTime(2026, 10, 17, 8, 2)))
        assertEquals("12:30 PM", formatChatTime(LocalDateTime(2026, 10, 17, 12, 30)))
        assertEquals("11:59 PM", formatChatTime(LocalDateTime(2026, 10, 17, 23, 59)))
        val today = LocalDate(2026, 10, 17)
        assertEquals(ChatDayLabel.TODAY, chatDayLabel(today, today))
        assertEquals(ChatDayLabel.YESTERDAY, chatDayLabel(LocalDate(2026, 10, 16), today))
        assertEquals(ChatDayLabel.DATE, chatDayLabel(LocalDate(2026, 10, 14), today))
        assertEquals("Wed, 14 Oct", formatChatDate(LocalDate(2026, 10, 14), today))
        assertEquals("31 Dec 2025", formatChatDate(LocalDate(2025, 12, 31), today))
    }

    @Test
    fun partnerIdentityIsLoadedForTheOpenRoomAndClearedOnClose() = runTest(dispatcher) {
        val partners = FixedPartnerSource(ChatFixtures.partner)
        val viewModel = SecureChatViewModel(ChatFixtureRepository(ChatFixtures.conversation()), partners)
        viewModel.openRoom(ChatFixtures.ROOM_ID)
        advanceUntilIdle()
        assertEquals(ChatFixtures.partner, viewModel.partner.value)
        assertEquals(listOf(ChatFixtures.ROOM_ID), partners.requests)

        viewModel.closeRoom()
        assertNull(viewModel.partner.value)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun latePartnerLookupForPreviousRoomIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<ChatPartnerIdentity?>()
        val partners = object : ChatPartnerSource {
            override suspend fun partnerFor(roomId: String): ChatPartnerIdentity? =
                if (roomId == "room-a") gate.await() else null
        }
        val viewModel = SecureChatViewModel(ChatFixtureRepository(ChatFixtures.conversation()), partners)
        viewModel.openRoom("room-a")
        advanceUntilIdle()
        viewModel.openRoom("room-b")
        gate.complete(ChatFixtures.partner)
        advanceUntilIdle()
        assertNull(viewModel.partner.value)
        viewModel.closeRoom()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun partnerLookupFailureLeavesChatUsable() = runTest(dispatcher) {
        val partners = object : ChatPartnerSource {
            override suspend fun partnerFor(roomId: String): ChatPartnerIdentity? = error("synthetic failure")
        }
        val repository = ChatFixtureRepository(ChatFixtures.conversation())
        val viewModel = SecureChatViewModel(repository, partners)
        viewModel.openRoom(ChatFixtures.ROOM_ID)
        advanceUntilIdle()
        assertNull(viewModel.partner.value)
        assertEquals(ChatFixtures.conversation(), viewModel.state.value)

        viewModel.updateMessageInput("still works")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertEquals(listOf("still works"), repository.sent)
        assertEquals("", viewModel.messageInput.value)
        viewModel.closeRoom()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun failedSendPreservesDraft() = runTest(dispatcher) {
        val repository = ChatFixtureRepository(ChatFixtures.conversation()).apply {
            sendResult = Result.failure(IllegalStateException("synthetic"))
        }
        val viewModel = SecureChatViewModel(repository)
        viewModel.openRoom(ChatFixtures.ROOM_ID)
        viewModel.updateMessageInput("keep me")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertEquals("keep me", viewModel.messageInput.value)
        viewModel.closeRoom()
        viewModel.viewModelScope.cancel()
    }
}
