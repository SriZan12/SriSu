package com.srisu.srisu.features.chat

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.srisu.srisu.features.chat.v2.data.local.ChatAttachmentTransferEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatCheckpointEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatConversationEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatDatabase
import com.srisu.srisu.features.chat.v2.data.local.ChatMessageEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatOutboxEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatReactionEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatReceiptEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatStorageStates
import com.srisu.srisu.features.chat.v2.data.local.ChatTombstoneEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ChatV2DatabaseTest {
    private fun open(path: String) = Room.databaseBuilder<ChatDatabase>(RuntimeEnvironment.getApplication(), path)
        .setDriver(AndroidSQLiteDriver()).build()

    @Test fun opaqueProjectionAndSyncMetadataSurviveReopenThenClearAsOneAccountScope() = runBlocking {
        val path = RuntimeEnvironment.getApplication().getDatabasePath("chat-v2-${UUID.randomUUID()}.db").absolutePath
        var database = open(path)
        val dao = database.chatV2()
        val message = message()
        val operation = operation()
        dao.enqueue(message, operation)
        dao.advanceCheckpoint(checkpoint(), conversation())
        dao.putTombstone(ChatTombstoneEntity(ENV, ACCOUNT, ROOM, "8", 2, 4, "delete_for_me", 20))
        dao.putReaction(ChatReactionEntity(ENV, ACCOUNT, ROOM, "8", 2, "{\"ciphertext\":\"reaction\"}", 2, 5, 20))
        dao.putReceipt(ChatReceiptEntity(ENV, ACCOUNT, ROOM, 2, "read", 8, 6, 20))
        dao.putAttachmentTransfer(ChatAttachmentTransferEntity(ENV, ACCOUNT, ROOM, ATTACHMENT, OPERATION, "upload", "pending", null, 0, 100, null, 20))
        database.close()

        database = open(path)
        val reopened = database.chatV2()
        assertEquals("{\"ciphertext\":\"opaque\"}", reopened.message(ENV, ACCOUNT, ROOM, OPERATION)?.opaqueEnvelope)
        assertEquals(ChatStorageStates.OUTBOX_PENDING, reopened.operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        assertEquals(3, reopened.checkpoint(ENV, ACCOUNT, ROOM)?.changeSequence)
        assertNotNull(reopened.tombstone(ENV, ACCOUNT, ROOM, "8"))
        assertEquals(1, reopened.reactions(ENV, ACCOUNT, ROOM, "8").size)
        assertEquals(1, reopened.receipts(ENV, ACCOUNT, ROOM).size)
        assertNotNull(reopened.attachmentTransfer(ENV, ACCOUNT, ROOM, ATTACHMENT))

        val claimed = assertNotNull(reopened.claim(requireNotNull(reopened.operation(ENV, ACCOUNT, ROOM, OPERATION)), 30))
        assertEquals(ChatStorageStates.OUTBOX_SENDING, claimed.state)
        assertEquals(ChatStorageStates.SENDING, reopened.message(ENV, ACCOUNT, ROOM, OPERATION)?.deliveryState)
        reopened.retry(claimed, 50, "network", 40)
        assertEquals(ChatStorageStates.OUTBOX_PENDING, reopened.operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        val reclaimed = assertNotNull(reopened.claim(requireNotNull(reopened.operation(ENV, ACCOUNT, ROOM, OPERATION)), 60))
        reopened.fail(reclaimed, "validation_failed", 70)
        assertEquals(ChatStorageStates.OUTBOX_FAILED, reopened.operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        assertEquals(ChatStorageStates.FAILED, reopened.message(ENV, ACCOUNT, ROOM, OPERATION)?.deliveryState)
        val failed = requireNotNull(reopened.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertEquals(1, reopened.retryFailedOperations(ENV, ACCOUNT, ROOM, 80))
        val manuallyRetried = requireNotNull(reopened.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertEquals(failed.operationId, manuallyRetried.operationId)
        assertEquals(failed.opaqueEnvelope, manuallyRetried.opaqueEnvelope)
        assertEquals(ChatStorageStates.OUTBOX_PENDING, manuallyRetried.state)
        assertEquals(ChatStorageStates.PENDING, reopened.message(ENV, ACCOUNT, ROOM, OPERATION)?.deliveryState)

        reopened.clearAccount(ENV, ACCOUNT)
        assertNull(reopened.message(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(reopened.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(reopened.conversation(ENV, ACCOUNT, ROOM))
        assertNull(reopened.attachmentTransfer(ENV, ACCOUNT, ROOM, ATTACHMENT))
        database.close()
    }

    @Test fun staleHistoryAndAckCannotRegressRevisionOrDeliveryState() = runBlocking {
        val path = RuntimeEnvironment.getApplication().getDatabasePath("chat-v2-revision-${UUID.randomUUID()}.db").absolutePath
        val database = open(path)
        val dao = database.chatV2()
        dao.enqueue(message(), operation())
        val revisionTwo = message().copy(
            messageId = "9",
            operationId = OPERATION,
            sequence = 9,
            revision = 2,
            opaqueEnvelope = "{\"ciphertext\":\"new\"}",
            deliveryState = ChatStorageStates.READ,
        )
        dao.mergeRemoteMessages(listOf(revisionTwo))
        val staleRevisionOne = revisionTwo.copy(
            revision = 1,
            opaqueEnvelope = "{\"ciphertext\":\"stale\"}",
            deliveryState = ChatStorageStates.SENT,
        )
        dao.acknowledge(operation(), staleRevisionOne, 30)

        val merged = requireNotNull(dao.message(ENV, ACCOUNT, ROOM, "9"))
        assertEquals(2, merged.revision)
        assertEquals("{\"ciphertext\":\"new\"}", merged.opaqueEnvelope)
        assertEquals(ChatStorageStates.READ, merged.deliveryState)
        assertNull(dao.message(ENV, ACCOUNT, ROOM, OPERATION))
        assertEquals(ChatStorageStates.OUTBOX_ACKNOWLEDGED, dao.operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        database.close()
    }

    @Test fun onlyPartnerReceiptsAdvanceOwnOutgoingDeliveryState() = runBlocking {
        val path = RuntimeEnvironment.getApplication().getDatabasePath("chat-v2-receipts-${UUID.randomUUID()}.db").absolutePath
        val database = open(path)
        val dao = database.chatV2()
        val outgoing = message().copy(
            messageId = "9",
            operationId = null,
            sequence = 9,
            revision = 1,
            deliveryState = ChatStorageStates.SENT,
        )
        dao.mergeRemoteMessages(listOf(outgoing))
        dao.applyDelta(
            messages = emptyList(),
            tombstones = emptyList(),
            receipts = listOf(ChatReceiptEntity(ENV, ACCOUNT, ROOM, ACCOUNT, "read", 9, 1, 20)),
            checkpoint = ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 1, 20),
            conversation = conversation().copy(latestChangeSequence = 1),
        )
        assertEquals(ChatStorageStates.SENT, dao.message(ENV, ACCOUNT, ROOM, "9")?.deliveryState)

        dao.applyDelta(
            messages = emptyList(),
            tombstones = emptyList(),
            receipts = listOf(ChatReceiptEntity(ENV, ACCOUNT, ROOM, 2, "read", 9, 2, 30)),
            checkpoint = ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 2, 30),
            conversation = conversation().copy(latestChangeSequence = 2),
        )
        assertEquals(ChatStorageStates.READ, dao.message(ENV, ACCOUNT, ROOM, "9")?.deliveryState)
        dao.putReceipt(ChatReceiptEntity(ENV, ACCOUNT, ROOM, 2, "read", 3, 1, 10))
        val persisted = dao.receipts(ENV, ACCOUNT, ROOM).single { it.actorId == 2L && it.receiptKind == "read" }
        assertEquals(9L, persisted.throughSequence)
        assertEquals(2L, persisted.changeSequence)
        database.close()
    }

    @Test fun tombstoneRevisionAndCursorNeverRegressOutOfOrder() = runBlocking {
        val path = RuntimeEnvironment.getApplication().getDatabasePath("chat-v2-tombstone-order-${UUID.randomUUID()}.db").absolutePath
        val database = open(path)
        val dao = database.chatV2()
        val current = message().copy(
            messageId = "9",
            operationId = null,
            sequence = 9,
            revision = 5,
            deliveryState = ChatStorageStates.SENT,
        )
        dao.mergeRemoteMessages(listOf(current))
        dao.applyDelta(
            messages = emptyList(),
            tombstones = listOf(ChatTombstoneEntity(ENV, ACCOUNT, ROOM, "9", 4, 4, "message_tombstoned", 20)),
            receipts = emptyList(),
            checkpoint = ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 4, 20),
            conversation = conversation().copy(latestChangeSequence = 4),
        )
        assertEquals(false, dao.message(ENV, ACCOUNT, ROOM, "9")?.isTombstone)
        assertEquals(5L, dao.message(ENV, ACCOUNT, ROOM, "9")?.revision)

        dao.applyDelta(
            messages = emptyList(),
            tombstones = listOf(ChatTombstoneEntity(ENV, ACCOUNT, ROOM, "9", 6, 10, "message_tombstoned", 30)),
            receipts = emptyList(),
            checkpoint = ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 10, 30),
            conversation = conversation().copy(latestChangeSequence = 10),
        )
        dao.putTombstone(ChatTombstoneEntity(ENV, ACCOUNT, ROOM, "9", 5, 8, "delete_for_me", 25))
        val deletion = requireNotNull(dao.tombstone(ENV, ACCOUNT, ROOM, "9"))
        assertEquals(6L, deletion.messageRevision)
        assertEquals(10L, deletion.changeSequence)
        assertEquals("message_tombstoned", deletion.deletionScope)
        assertEquals(true, dao.message(ENV, ACCOUNT, ROOM, "9")?.isTombstone)
        assertEquals(6L, dao.message(ENV, ACCOUNT, ROOM, "9")?.revision)
        database.close()
    }

    @Test fun viewerHiddenRevisionOneDominatesLaterPartnerEditRevisionTwo() = runBlocking {
        val path = RuntimeEnvironment.getApplication().getDatabasePath("chat-v2-viewer-hidden-${UUID.randomUUID()}.db").absolutePath
        val database = open(path)
        val dao = database.chatV2()
        val revisionOne = message().copy(
            messageId = "9",
            operationId = null,
            sequence = 9,
            revision = 1,
            deliveryState = ChatStorageStates.SENT,
        )
        dao.mergeRemoteMessages(listOf(revisionOne))
        dao.applyDelta(
            messages = emptyList(),
            tombstones = listOf(ChatTombstoneEntity(ENV, ACCOUNT, ROOM, "9", 1, 1, "message_hidden", 20)),
            receipts = emptyList(),
            checkpoint = ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 1, 20),
            conversation = conversation().copy(latestChangeSequence = 1),
        )
        assertNull(dao.message(ENV, ACCOUNT, ROOM, "9"))

        dao.mergeRemoteMessages(listOf(revisionOne.copy(
            sequence = 10,
            revision = 2,
            opaqueEnvelope = "{\"ciphertext\":\"partner-edit\"}",
            editedAt = "2026-10-07T01:00:00Z",
        )))

        assertNull(dao.message(ENV, ACCOUNT, ROOM, "9"))
        assertEquals("message_hidden", dao.tombstone(ENV, ACCOUNT, ROOM, "9")?.deletionScope)
        database.close()
    }

    private fun message() = ChatMessageEntity(
        ENV, ACCOUNT, ROOM, OPERATION, OPERATION, 0, 0, ACCOUNT, "text",
        "{\"ciphertext\":\"opaque\"}", null, false, null, null, ChatStorageStates.PENDING, 10,
    )

    private fun operation() = ChatOutboxEntity(
        ENV, ACCOUNT, ROOM, OPERATION, "send_message", "text", "{\"ciphertext\":\"opaque\"}",
        null, null, null, null, 1, ChatStorageStates.OUTBOX_PENDING, 0, 10, null, 10, 10,
    )

    private fun checkpoint() = ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 3, 20)
    private fun conversation() = ChatConversationEntity(ENV, ACCOUNT, ROOM, "relationship-1", 1, 8, 3, 0, 20)

    companion object {
        private const val ENV = "https://example.test/"
        private const val ACCOUNT = 1L
        private const val ROOM = "123e4567-e89b-12d3-a456-426614174000"
        private const val OPERATION = "123e4567-e89b-42d3-a456-426614174001"
        private const val ATTACHMENT = "123e4567-e89b-42d3-a456-426614174002"
    }
}
