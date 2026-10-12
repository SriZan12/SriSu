package com.srisu.srisu.features.couplechat.data

import com.srisu.srisu.features.couplechat.crypto.*
import com.srisu.srisu.core.data.remote.*
import kotlinx.serialization.json.*
import kotlin.test.*

/** Projection-only store double. These tests make no claim about cryptography. */
private class ProjectionDriver : NativeChatDriver {
    private val records = mutableMapOf<String, MutableMap<String, String>>()
    var failWrite = false
    override fun close(accountId: String) = Unit
    override fun executeBinary(accountId: String, command: String, payload: String, bytes: ByteArray): String = error("No native media in projection tests")
    override fun readAttachment(accountId: String, descriptor: String, decrypt: Boolean): ByteArray? = error("No native media in projection tests")
    override fun execute(accountId: String, command: String, payload: String): String {
        val input = ChatJson.parseToJsonElement(payload).jsonObject
        val data = records.getOrPut(accountId) { mutableMapOf() }
        if (command == "write" && failWrite) return "{\"ok\":false,\"code\":\"storage_or_protocol_failure\"}"
        val value: JsonElement = when (command) {
            "read" -> data[input.getValue("key").jsonPrimitive.content]?.let(::JsonPrimitive) ?: JsonNull
            "read_many" -> JsonArray(input.getValue("keys").jsonArray.map { data[it.jsonPrimitive.content]?.let(::JsonPrimitive) ?: JsonNull })
            "count" -> JsonPrimitive(data.keys.count { it.startsWith(input.getValue("prefix").jsonPrimitive.content) })
            "page" -> {
                val prefix = input.getValue("prefix").jsonPrimitive.content
                val before = input["before"]?.jsonPrimitive?.content.orEmpty()
                val limit = input.getValue("limit").jsonPrimitive.int
                JsonArray(data.keys.filter { it.startsWith(prefix) && (before.isEmpty() || it < before) }.sortedDescending().take(limit).map {
                    buildJsonObject { put("key", it); put("value", data.getValue(it)) }
                })
            }
            "write" -> {
                input.getValue("entries").jsonArray.forEach {
                    val item = it.jsonObject; val key = item.getValue("key").jsonPrimitive.content; val value = item.getValue("value")
                    if (value == JsonNull) data.remove(key) else data[key] = value.jsonPrimitive.content
                }
                JsonNull
            }
            else -> error("Projection test called a native protocol command")
        }
        return buildJsonObject { put("ok", true); put("value", value) }.toString()
    }
}

class LocalConversationTest {
    private val driver = ProjectionDriver()
    private val store = ProtectedChatStore(driver, 1)
    private val local = LocalConversation(store, "room-one")
    private fun event(seq: Long, id: String = "message-one", kind: String = "message", target: String = id,
        actor: Long = 2, applied: Boolean = true, deleted: Boolean = false) = ChatChange(seq, kind, "2026-10-10T01:00:00Z",
        ChatReceivedOperation(id, actor, "sender", "recipient", kind, target, version = if (kind == "message") 0 else 1,
            applied = applied, signalType = 3, ciphertext = "opaque", unavailable = false, targetDeleted = deleted))
    private fun body(content: ChatContent) = ChatPrivatePayload(content = content)

    @Test fun chatThemeSurvivesReopeningAndIsIsolatedByAccountAndRoom() {
        local.store.put(local.preferencesKey,ConversationPreferences(nickname="Private name",chatTheme="blend"))
        assertEquals("blend",LocalConversation(ProtectedChatStore(driver,1),"room-one").preferences().chatTheme)
        assertEquals("Private name",local.preferences().nickname)
        assertEquals("iris",LocalConversation(ProtectedChatStore(driver,2),"room-one").preferences().chatTheme)
        assertEquals("iris",LocalConversation(store,"another-room").preferences().chatTheme)
        store.put(local.preferencesKey,ChatJson.decodeFromString<ConversationPreferences>("""{"nickname":"Existing preference","readReceipts":false}"""))
        assertEquals("iris",local.preferences().chatTheme);assertFalse(local.preferences().readReceipts)
        assertEquals(ChatThemePreset.ORIGINAL,ChatThemePreset.fromId("future-preset"))
    }

    @Test fun newDefaultPreservesEveryExplicitThemeIncludingOriginal() {
        assertEquals(ChatThemePreset.DEFAULT.id,ConversationPreferences().chatTheme)
        for(preset in ChatThemePreset.entries) {
            val saved=ConversationPreferences(nickname="Kept",readReceipts=false,chatTheme=preset.id)
            // The protected store encodes defaults too, so a previously selected Original is explicit.
            store.put(local.preferencesKey,saved)
            assertEquals(saved,LocalConversation(ProtectedChatStore(driver,1),"room-one").preferences())
        }
        val old=ChatJson.decodeFromString<ConversationPreferences>("""{"chatTheme":"original","presence":false}""")
        assertEquals("original",old.chatTheme);assertFalse(old.presence)
    }

    @Test fun cursorAndMessageCommitTogetherAfterNativeProcessing() {
        driver.failWrite = true
        assertFailsWith<ChatStorageFailure> { local.apply(event(1), body(ChatContent.Text("synthetic"))) }
        assertEquals(0L, local.cursor()); assertNull(local.message("message-one"))
        driver.failWrite = false
        local.apply(event(1), body(ChatContent.Text("synthetic")))
        assertEquals(1L, local.cursor()); assertNotNull(local.message("message-one"))
        assertFailsWith<ChatStorageFailure> { local.apply(event(3), body(ChatContent.Text("gap"))) }
        assertEquals(1L, local.cursor())
    }
    @Test fun hiddenAndDeletedBodiesCannotReturnAfterFullCatchup() {
        local.apply(event(1), body(ChatContent.Text("synthetic")))
        local.hide("message-one")
        store.put(local.cursorKey, 0L)
        local.apply(event(1), body(ChatContent.Text("synthetic")))
        assertTrue(local.message("message-one")!!.hidden); assertNull(local.message("message-one")!!.content)
        local.apply(event(2,"another"), body(ChatContent.Text("second")))
        local.apply(event(3,"delete","delete","another",deleted=true), body(ChatContent.Delete))
        assertTrue(local.message("another")!!.deleted); assertNull(local.message("another")!!.content)
    }
    @Test fun failedReactionDoesNotReplaceConfirmedSelectionOrCreateDuplicates() {
        local.apply(event(1),body(ChatContent.Text("Synthetic")))
        local.apply(event(2,"reaction-one","reaction","message-one",actor=1),body(ChatContent.Reaction("❤️")))
        val pending=local.enqueue("reaction",ChatContent.Reaction("👍"),"message-one")
        assertEquals(mapOf(1L to "❤️"),local.message("message-one")!!.reactions)
        local.failed(pending,"permission_denied",true,0)
        assertEquals(mapOf(1L to "❤️"),local.message("message-one")!!.reactions)
        local.apply(event(3,"reaction-two","reaction","message-one",actor=1),body(ChatContent.Reaction("👍")))
        // Duplicate sequences are rejected at the projection boundary; the confirmed chip stays singular.
        assertFailsWith<ChatStorageFailure> {
            local.apply(event(3,"reaction-two","reaction","message-one",actor=1),body(ChatContent.Reaction("👍")))
        }
        assertEquals(mapOf(1L to "👍"),local.message("message-one")!!.reactions)
    }

    @Test fun lateReactionCannotRestoreOlderSelectionAndReceiptsAreMonotonic() {
        local.apply(event(1), body(ChatContent.Text("synthetic")))
        local.apply(event(2,"reaction-new","reaction","message-one",actor=1), body(ChatContent.Reaction("❤️")))
        local.apply(event(3,"reaction-old","reaction","message-one",actor=1,applied=false), body(ChatContent.Reaction("👍")))
        assertEquals("❤️",local.message("message-one")!!.reactions[1])
        local.apply(event(4,"read","receipt.read","message-one",actor=1), body(ChatContent.Receipt))
        local.apply(event(5,"delivered","receipt.delivered","message-one",actor=1), body(ChatContent.Receipt))
        assertTrue(local.message("message-one")!!.read); assertTrue(local.message("message-one")!!.delivered)
    }
    @Test fun unreadablePayloadIsDiscardedAndNeverAcknowledged() {
        local.apply(event(1), body(ChatContent.Text("invalid decoded data")), unreadable=true)
        assertTrue(local.message("message-one")!!.unavailable)
        assertNull(local.message("message-one")!!.content)
        assertEquals(0,store.count("app/pending-receipt/"))
    }
    @Test fun acceptanceRetryKeepsOneBubbleAndOriginalOperation() {
        val queued = local.enqueue("message",ChatContent.Text("नमस्ते 👋"))
        local.failed(queued,"network",false,100)
        assertEquals(queued.id,local.queue().single().id)
        local.accepted(queued,ChatAcceptance(queued.id,1,true,"2026-10-10T01:00:00Z"))
        assertTrue(local.queue().isEmpty())
        local.apply(event(1,queued.id,actor=1),queued.content)
        assertEquals(1,local.page().first.size)
        assertEquals(ChatTransport.SENT,local.message(queued.id)!!.transport)
    }
    @Test fun draftsQueuesAndHistoryAreScopedToAccountAndRelationship() {
        store.put(local.draftKey,"private synthetic draft")
        local.enqueue("message",ChatContent.Text("synthetic"))
        assertTrue(LocalConversation(store,"another-room").page().first.isEmpty())
        val other = LocalConversation(ProtectedChatStore(driver,2),"room-one")
        assertTrue(other.queue().isEmpty()); assertEquals("",other.draft()); assertTrue(other.page().first.isEmpty())
    }
    @Test fun quotedDeletedMessageHasNoSavedPreviewInProjection() {
        local.apply(event(1),body(ChatContent.Text("original")))
        local.apply(event(2,"reply"),body(ChatContent.Text("response",ChatQuote("message-one",2,"original"))))
        local.apply(event(3,"delete","delete","message-one",deleted=true),body(ChatContent.Delete))
        assertEquals("Message unavailable",(local.message("reply")!!.content as ChatContent.Text).quote!!.preview)
    }
    @Test fun historyPagingUsesStableSequenceBoundary() {
        for (i in 1L..125) local.apply(event(i,"message-$i"),body(ChatContent.Text("synthetic $i")))
        val (newest,more) = local.page()
        assertTrue(more); assertEquals(50,newest.size); assertEquals("message-76",newest.first().id)
        val previous = local.page(newest.first().timelineKey).first
        assertEquals("message-75",previous.last().id)
        assertTrue(newest.map { it.id }.intersect(previous.map { it.id }.toSet()).isEmpty())
    }
    @Test fun unknownServerAcceptanceIsAlwaysRetryable() {
        fun failure(status: Int?) = ApiError(NetworkAPIResult.ErrorType.SERVER,"server","unavailable",status=status)
        assertTrue(failure(null).chatRetryable()); assertTrue(failure(500).chatRetryable()); assertTrue(failure(503).chatRetryable())
        assertFalse(failure(403).chatRetryable()); assertFalse(failure(409).chatRetryable())
    }
    @Test fun cancellingBeforeSealingCannotLeaveAQueuedSendOrRestoreItsBody() {
        val operation=local.enqueue("message",ChatContent.Text("cancelled synthetic"))
        local.sending(operation)
        assertEquals(ChatTransport.SENDING,local.message(operation.id)!!.transport)
        local.recoverSending()
        assertEquals(ChatTransport.QUEUED,local.message(operation.id)!!.transport)
        local.cancelUnsealed(operation)
        assertTrue(local.queue().isEmpty());assertNull(store.read(local.ownPayloadKey(operation.id)))
        assertTrue(local.message(operation.id)!!.hidden);assertNull(local.message(operation.id)!!.content)
    }
    @Test fun retryDoesNotEraseAnUnrelatedComposerDraft() {
        store.put(local.draftKey,"new composition")
        local.enqueue("message",ChatContent.Text("retry"),clearComposer=false)
        assertEquals("new composition",local.draft())
        val marker=local.cancelPrefix+"target"
        store.put(marker,true)
        local.enqueue("delete",ChatContent.Delete,"target",completedMarker=marker)
        assertNull(store.read(marker))
    }
    @Test fun asksKeepLatestAcceptedPartnerResponseAndNeverResurrectAfterDeletion() {
        local.apply(event(1),body(ChatContent.Card(ChatCard.Ask("A synthetic question",listOf("First","Second")))))
        local.apply(event(2,"vote-new","card.response","message-one",actor=1),body(ChatContent.CardResponse(CardReply(vote=1))))
        local.apply(event(3,"vote-old","card.response","message-one",actor=1,applied=false),body(ChatContent.CardResponse(CardReply(vote=0))))
        assertEquals(1,local.message("message-one")!!.cardResponses[1]?.vote)
        local.apply(event(4,"delete","delete","message-one",deleted=true),body(ChatContent.Delete))
        assertTrue(local.message("message-one")!!.cardResponses.isEmpty())
    }
    @Test fun foregroundUnreadMarkersAreReplaySafeAndLocalDeletionClearsThem() {
        local.apply(event(1),body(ChatContent.Text("synthetic")))
        assertEquals(1,store.count(local.unreadPrefix))
        store.put(local.cursorKey,0L)
        local.apply(event(1),body(ChatContent.Text("synthetic")))
        assertEquals(1,store.count(local.unreadPrefix))
        local.hide("message-one")
        assertEquals(0,store.count(local.unreadPrefix))
    }
    @Test fun malformedPollVoteCannotReplaceAValidPartnerVote() {
        local.apply(event(1),body(ChatContent.Card(ChatCard.Ask("Question",listOf("First","Second")))))
        local.apply(event(2,"valid","card.response","message-one",actor=1),body(ChatContent.CardResponse(CardReply(vote=1))))
        local.apply(event(3,"invalid","card.response","message-one",actor=1),body(ChatContent.CardResponse(CardReply(vote=3))))
        assertEquals(1,local.message("message-one")!!.cardResponses[1]?.vote)
        assertFalse(validAskReply(ChatCard.Ask("Question"),CardReply(vote=0)))
        assertFalse(validAskReply(ChatCard.Ask("Question"),CardReply(answer=" ")))
    }
    @Test fun unsealedReadReceiptCanBeWithdrawnWithoutSuppressingFutureConsent() {
        val marker="app/receipt/room-one/receipt.read/message-one"
        val operation=local.enqueue("receipt.read",ChatContent.Receipt,"message-one",receiptMarker=marker)
        assertNotNull(store.read(marker))
        local.dropUnsealedReceipt(operation)
        assertTrue(local.queue().isEmpty());assertNull(store.read(marker))
        assertNull(store.read(local.ownPayloadKey(operation.id)))
    }
    @Test fun attachmentPreparationMarkerIsRemovedOnlyWithDurableQueueCommit() {
        val marker=local.preparingMediaPrefix+"synthetic-file-id"
        store.put(marker,true)
        driver.failWrite=true
        assertFailsWith<ChatStorageFailure> {local.enqueue("message",ChatContent.Text("synthetic"),completedMarkers=listOf(marker))}
        driver.failWrite=false
        assertNotNull(store.read(marker));assertTrue(local.queue().isEmpty())
        local.enqueue("message",ChatContent.Text("synthetic"),completedMarkers=listOf(marker))
        assertNull(store.read(marker));assertEquals(1,local.queue().size)
    }
    @Test fun acceptedAndFailedAttachmentsKeepCrashSafeCleanupMarkers() {
        val descriptor=AttachmentDescriptor("file-one","room-one","message-one","aes256gcm-v1","synthetic",40,"synthetic")
        val content=ChatContent.Media(listOf(ChatMedia(descriptor,"photo","image/jpeg")))
        val sent=local.enqueue("message",content,messageId="message-one")
        driver.failWrite=true
        assertFailsWith<ChatStorageFailure> {local.accepted(sent,ChatAcceptance(sent.id,1,true,"2026-10-10T01:00:00Z"))}
        driver.failWrite=false
        assertNotNull(local.queued(sent.id));assertNull(store.read(local.releaseMediaPrefix+descriptor.id))
        local.accepted(sent,ChatAcceptance(sent.id,1,true,"2026-10-10T01:00:00Z"))
        assertNull(local.queued(sent.id));assertNotNull(store.read(local.releaseMediaPrefix+descriptor.id))
        local.hide(sent.id)
        assertNotNull(store.read(local.preparingMediaPrefix+descriptor.id))
        val failed=local.enqueue("message",content.copy(items=listOf(ChatMedia(descriptor.copy(id="file-two"),"photo","image/jpeg"))))
        local.failed(failed,"room_revoked",true,0)
        assertNotNull(store.read(local.releaseMediaPrefix+"file-two"))
        local.hide(failed.id)
        assertNull(store.read("app/failed-operation/room-one/${failed.id}"))
        assertNotNull(store.read(local.preparingMediaPrefix+"file-two"))
    }
    @Test fun uncertainSendRetainsMediaUntilItsAcknowledgement() {
        val media=ChatMedia(AttachmentDescriptor("file-one","room-one","message-one","aes256gcm-v1","synthetic",40,"synthetic"),"photo","image/jpeg")
        val operation=local.enqueue("message",ChatContent.Media(listOf(media)),messageId="message-one")
        local.hide(operation.id)
        assertNull(store.read(local.preparingMediaPrefix+media.descriptor.id))
        assertNotNull(local.queued(operation.id))
        local.apply(event(1,operation.id,actor=1),operation.content)
        assertNotNull(store.read(local.preparingMediaPrefix+media.descriptor.id))
        assertNull(local.queued(operation.id));assertNull(local.message(operation.id)!!.content)
    }
}
