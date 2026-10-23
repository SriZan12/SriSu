package com.srisu.srisu.features.chat

import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.matrix.*
import com.srisu.srisu.features.chat.matrix.presentation.*
import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.*
import de.connect2x.trixnity.core.model.events.*
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.FileBased.Image
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRecoveryTest {
    private val me = "@one:test"
    private val partner = "@two:test"
    private fun event(id: String, content: MessageEventContent, timestamp: Long, sender: String = me) = TimelineEvent(
        MessageEvent(MegolmEncryptedMessageEventContent(MegolmMessageValue("synthetic"), sessionId = "test"),
            EventId(id), UserId(sender), RoomId("!synthetic:test"), timestamp), Result.success(content))

    @Test fun encryptedMomentReferenceContainsOnlyAuthoritativeIdentity() {
        val ref=com.srisu.srisu.features.moments.data.MomentReference(7,3)
        val message=matrixTimelineMessages(listOf(event("$"+"moment",Text("Thinking of this morning",externalUrl=ref.url),1)),me,partner,null).single()
        assertEquals(ref,message.momentReference);assertNull(message.attachment)
        assertEquals("Thinking of this morning",message.body)
        val foreign=matrixTimelineMessages(listOf(event("$"+"url",Text("hello",externalUrl="https://external.invalid/photo"),1)),me,partner,null).single()
        assertNull(foreign.momentReference)
    }

    @Test fun momentReplyUsesEncryptedRepositoryPathAndPreservesDraftOnFailure()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val room=ChatFixtures.conversation().copy(accountId=1)
        val base=ChatFixtureRepository(room)
        var received:com.srisu.srisu.features.moments.data.MomentReference?=null
        var succeeds=false
        val repository=object:MatrixChatRepository by base {
            override suspend fun sendMomentReply(roomId:String,text:String,reference:com.srisu.srisu.features.moments.data.MomentReference):Result<String> {
                received=reference;return if(succeeds) Result.success("synthetic-txn") else Result.failure(Exception("synthetic"))
            }
        }
        val vm=SecureChatViewModel(repository,moments=com.srisu.srisu.features.moments.FakeMoments())
        try {
            vm.openRoom(room.roomId);runCurrent()
            val reference=com.srisu.srisu.features.moments.data.MomentReference(8,3)
            vm.openMomentReply(reference);vm.updateMessageInput("A thought for you");vm.sendMessage();runCurrent()
            assertEquals(reference,received);assertEquals(reference,vm.momentReply.value);assertEquals("A thought for you",vm.messageInput.value)
            succeeds=true;vm.sendMessage();runCurrent();assertNull(vm.momentReply.value);assertEquals("",vm.messageInput.value)
        } finally {vm.closeRoom();vm.viewModelScope.cancel();Dispatchers.resetMain()}
    }

    @Test fun ownMessagesFromAnotherDeviceDoNotMoveTheReadingPosition() {
        val message = MatrixChatMessage("\$message", "Hello", me, true, 1, MatrixMessageDelivery.SENT)
        assertFalse(shouldFollowNewMessage(message, wasAtLatest = false))
        assertTrue(shouldFollowNewMessage(message.copy(transactionId = "local-send"), wasAtLatest = false))
        assertTrue(shouldFollowNewMessage(message.copy(mine = false), wasAtLatest = true))
        assertFalse(shouldFollowNewMessage(message.copy(mine = false), wasAtLatest = false))
        assertEquals(message.copy(transactionId = "local-send").stableKey,
            message.copy(id = "txn:local-send", transactionId = "local-send", delivery = MatrixMessageDelivery.SENDING).stableKey)
    }

    @Test fun logoutImmediatelyClearsDraftReplyAndPrivateMediaEvenBeforeRepositoryCleanup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values = mutableMapOf<String, String>()
        val storage = object : SessionStorage {
            override fun saveSession(credentials: String, sessionKey: String) { values[sessionKey] = credentials }
            override fun getSession(sessionKey: String) = values[sessionKey]
            override fun clearSession(): Boolean { values.clear(); return true }
            override fun clearOnReinstall(key: String) = Unit
        }
        val sessions = com.srisu.srisu.core.session.SessionCoordinator(storage)
        sessions.saveSession(kotlinx.serialization.json.Json.encodeToString(com.srisu.srisu.core.session.Session(id = 1, access = "synthetic")), com.srisu.srisu.utils.Constants.Auth.SESSION_KEY)
        val room = ChatFixtures.conversation().copy(accountId = 1)
        val vm = SecureChatViewModel(ChatFixtureRepository(room), storage = storage, sessions = sessions)
        try {
            vm.openRoom(room.roomId); runCurrent()
            vm.updateMessageInput("Private draft"); vm.setReply(room.messages.first().id)
            vm.prepareAttachments(listOf(com.srisu.srisu.features.couplechat.data.PreparedChatMedia(ChatRecoveryFixtures.image, "photo", "image/png")))
            runCurrent()
            vm.openDraftAttachment(vm.uiState.value.mediaDraft!!.items.single().descriptor.id); runCurrent()
            assertNotNull(vm.uiState.value.openedMedia)
            sessions.clearSession(); runCurrent()
            assertEquals("", vm.messageInput.value)
            assertNull(vm.uiState.value.reply)
            assertNull(vm.uiState.value.mediaDraft)
            assertNull(vm.uiState.value.openedMedia)
            assertTrue(vm.uiState.value.messages.isEmpty())
        } finally { vm.closeRoom(); vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun encryptedReactionsReplaceAndRemoveWithoutBecomingTimelineBubbles() {
        val original = event("\$text", Text("Hello"), 1)
        val add = event("\$add", Text("", relatesTo = RelatesTo.Annotation(EventId("\$text"), "❤️")), 2, partner)
        val replace = event("\$replace", Text("", relatesTo = RelatesTo.Annotation(EventId("\$text"), "🥰")), 3, partner)
        val remove = event("\$remove", Text("", relatesTo = RelatesTo.Annotation(EventId("\$text"), "")), 4, partner)
        assertEquals(mapOf(partner to "🥰"), matrixTimelineMessages(listOf(original, add, replace), me, partner, null).single().reactions)
        assertTrue(matrixTimelineMessages(listOf(original, add, replace, remove), me, partner, null).single().reactions.isEmpty())
    }

    @Test fun repliesStripOnlyTheActualMatrixFallbackAndIgnoreOtherSenders() {
        val reply = Text("> <@two:test> Hello\n\nI love you", relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(EventId("\$original"))))
        val mapped = matrixTimelineMessages(listOf(event("\$reply", reply, 4), event("\$outsider", Text("no"), 5, "@other:test")), me, partner, null).single()
        assertEquals("I love you", mapped.body)
        assertEquals("\$original", mapped.replyTo)
        assertEquals("> ordinary quote\n\nMy text", Text("> ordinary quote\n\nMy text").displayBody())
    }

    @Test fun readTicksRequireARealKnownReceiptAndPlaintextAttachmentsStayUnavailable() {
        val events = listOf(event("\$first", Text("one"), 1), event("\$second", Text("two"), 2),
            event("\$plain", Image("Photo", url = "https://example.invalid/private.jpg"), 3))
        assertTrue(matrixTimelineMessages(events, me, partner, "\$unknown").none { it.read })
        val mapped = matrixTimelineMessages(events, me, partner, "\$second")
        assertTrue(mapped[0].read && mapped[1].read)
        assertFalse(mapped[2].read)
        assertTrue(mapped[2].unavailable)
        assertNull(mapped[2].attachment)
    }

    @Test fun redactedCiphertextRetainsItsIdentityWithoutOldTextOrMedia() {
        val tombstone = TimelineEvent(MessageEvent(RedactedEventContent("m.room.encrypted"), EventId("\$deleted"),
            UserId(me), RoomId("!synthetic:test"), 3), Result.success(Text("stale decrypted content")))
        val mapped = matrixTimelineMessages(listOf(tombstone), me, partner, null).single()
        assertEquals("\$deleted", mapped.id)
        assertTrue(mapped.deleted)
        assertEquals("", mapped.body)
        assertNull(mapped.attachment)
    }

    @Test fun animationSkipsInitialIncrementalHistoryPaginationAndDuplicateReceipts() {
        val tracker = RomanticArrivalTracker(openedAt = 100)
        val ready = MatrixChatRoomState("room", accountId = 1, availability = MatrixChatAvailability.Ready())
        fun message(id: String, time: Long, delivery: MatrixMessageDelivery = MatrixMessageDelivery.SENT) = MatrixChatMessage(id, "I love you", me, true, time, delivery)
        assertTrue(tracker.accept(ready).isEmpty())
        assertTrue(tracker.accept(ready.copy(messages = listOf(message("old", 50)))).isEmpty())
        val pending = message("new", 110, MatrixMessageDelivery.SENDING)
        assertTrue(tracker.accept(ready.copy(messages = listOf(pending))).isEmpty())
        val confirmed = pending.copy(delivery = MatrixMessageDelivery.SENT)
        assertEquals(1, tracker.accept(ready.copy(messages = listOf(confirmed))).size)
        assertTrue(tracker.accept(ready.copy(messages = listOf(message("older-page", 20), confirmed.copy(read = true)))).isEmpty())
    }

    @Test fun themeDraftAndReplySurviveReopeningAndFailedSend() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val values = mutableMapOf<String, String>()
        val storage = object : SessionStorage {
            override fun saveSession(credentials: String, sessionKey: String) { values[sessionKey] = credentials }
            override fun getSession(sessionKey: String) = values[sessionKey]
            override fun clearSession(): Boolean { values.clear(); return true }
            override fun clearOnReinstall(key: String) = Unit
        }
        val room = ChatFixtures.conversation().copy(accountId = 1)
        val repository = ChatFixtureRepository(room).apply { sendResult = Result.failure(IllegalStateException()) }
        val first = SecureChatViewModel(repository, storage = storage)
        val second = SecureChatViewModel(repository, storage = storage)
        try {
            first.openRoom(room.roomId); runCurrent()
            first.updateMessageInput("Keep this draft"); first.chatTheme(ChatThemePreset.DAWN.id)
            first.sendMessage(); runCurrent()
            assertEquals("Keep this draft", first.messageInput.value)
            first.setReply(room.messages.first().id); runCurrent(); first.closeRoom()
            second.openRoom(room.roomId); runCurrent()
            assertEquals("Keep this draft", second.messageInput.value)
            assertEquals(ChatThemePreset.DAWN.id, second.uiState.value.preferences.chatTheme)
            assertEquals(room.messages.first().id, second.uiState.value.reply?.messageId)
        } finally { first.closeRoom(); second.closeRoom(); first.viewModelScope.cancel(); second.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun typingWhileOpeningSurvivesReadyAndDeletedReplyContentIsRemoved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ChatFixtureRepository(ChatFixtures.loading())
        val vm = SecureChatViewModel(repository)
        try {
            vm.openRoom(ChatFixtures.ROOM_ID); runCurrent()
            vm.updateMessageInput("Already typing")
            val ready = ChatFixtures.conversation().copy(accountId = 1)
            repository.state.value = ready; runCurrent()
            assertEquals("Already typing", vm.messageInput.value)
            vm.setReply(ready.messages.first().id); runCurrent()
            repository.state.value = ready.copy(messages = ready.messages.mapIndexed { index, message -> if (index == 0) message.copy(deleted = true) else message }, failedChangeIds = listOf("reaction-transaction"))
            runCurrent()
            assertEquals("Original message unavailable", vm.uiState.value.reply?.preview)
            vm.retryFailedChanges(); runCurrent()
            assertEquals(listOf("reaction-transaction"), repository.retried)
        } finally { vm.closeRoom(); vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
