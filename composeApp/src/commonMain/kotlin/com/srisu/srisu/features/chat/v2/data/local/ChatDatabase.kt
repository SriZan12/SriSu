package com.srisu.srisu.features.chat.v2.data.local

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Chat v2 persists opaque protocol envelopes and synchronization metadata only.
 * It must never contain message plaintext, captions, filenames, or key material.
 */
@Entity(
    tableName = "chat_v2_conversation",
    primaryKeys = ["environmentKey", "accountId", "roomId"],
)
data class ChatConversationEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val relationshipId: String?,
    val membershipEpoch: Long,
    val latestMessageSequence: Long,
    val latestChangeSequence: Long,
    val unreadCount: Int,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_v2_message",
    primaryKeys = ["environmentKey", "accountId", "roomId", "messageId"],
    indices = [
        Index(value = ["environmentKey", "accountId", "roomId", "sequence"]),
        Index(value = ["environmentKey", "accountId", "roomId", "operationId"]),
    ],
)
data class ChatMessageEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    /** Server id, or the operation UUID while the message is local-only. */
    val messageId: String,
    val operationId: String?,
    val sequence: Long,
    val revision: Long,
    val senderId: Long?,
    val contentKind: String,
    /** Opaque authenticated protocol payload. It is never parsed by the storage layer. */
    val opaqueEnvelope: String?,
    val replyToId: String?,
    val isTombstone: Boolean,
    val createdAt: String?,
    val editedAt: String?,
    val deliveryState: String,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_v2_checkpoint",
    primaryKeys = ["environmentKey", "accountId", "roomId"],
)
data class ChatCheckpointEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val changeSequence: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_v2_outbox",
    primaryKeys = ["environmentKey", "accountId", "roomId", "operationId"],
    indices = [Index(value = ["environmentKey", "accountId", "state", "nextAttemptAtMillis"])],
)
data class ChatOutboxEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val operationId: String,
    val operationKind: String,
    val contentKind: String?,
    /** Exact wire envelope retried for this idempotency key. Never replace it during retry. */
    val opaqueEnvelope: String?,
    val messageId: String?,
    val expectedRevision: Long?,
    val replyToId: String?,
    /** JSON containing identifiers only. It must not contain captions or filenames. */
    val attachmentIdsJson: String?,
    val membershipEpoch: Long,
    val state: String,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long,
    val lastErrorCode: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_v2_tombstone",
    primaryKeys = ["environmentKey", "accountId", "roomId", "messageId"],
)
data class ChatTombstoneEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val messageId: String,
    val messageRevision: Long,
    val changeSequence: Long,
    val deletionScope: String,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_v2_reaction",
    primaryKeys = ["environmentKey", "accountId", "roomId", "messageId", "actorId"],
)
data class ChatReactionEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val messageId: String,
    val actorId: Long,
    /** Reaction content is an opaque protocol envelope, never a plaintext emoji. */
    val opaqueEnvelope: String,
    val messageRevision: Long,
    val changeSequence: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_v2_receipt",
    primaryKeys = ["environmentKey", "accountId", "roomId", "actorId", "receiptKind"],
)
data class ChatReceiptEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val actorId: Long,
    val receiptKind: String,
    val throughSequence: Long,
    val changeSequence: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_v2_attachment_transfer",
    primaryKeys = ["environmentKey", "accountId", "roomId", "attachmentId"],
    indices = [Index(value = ["environmentKey", "accountId", "roomId", "operationId"])],
)
data class ChatAttachmentTransferEntity(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val attachmentId: String,
    val operationId: String?,
    val direction: String,
    val state: String,
    /** Opaque server upload/download handle; never a public URL or local filename. */
    val serverHandle: String?,
    val transferredBytes: Long,
    val totalBytes: Long?,
    val failureCode: String?,
    val updatedAtMillis: Long,
)

object ChatStorageStates {
    const val PENDING = "pending"
    const val SENDING = "sending"
    const val SENT = "sent"
    const val DELIVERED = "delivered"
    const val READ = "read"
    const val FAILED = "failed"

    const val OUTBOX_PENDING = "pending"
    const val OUTBOX_SENDING = "sending"
    const val OUTBOX_ACKNOWLEDGED = "acknowledged"
    const val OUTBOX_FAILED = "failed"

    fun strongest(first: String, second: String): String {
        val rank = mapOf(PENDING to 0, SENDING to 1, FAILED to 2, SENT to 3, DELIVERED to 4, READ to 5)
        return if ((rank[first] ?: -1) >= (rank[second] ?: -1)) first else second
    }
}

@Dao
abstract class ChatV2Dao {
    @Query("SELECT * FROM chat_v2_conversation WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    abstract fun observeConversation(environmentKey: String, accountId: Long, roomId: String): Flow<ChatConversationEntity?>

    @Query("SELECT * FROM chat_v2_message WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId ORDER BY sequence ASC, createdAt ASC, messageId ASC")
    abstract fun observeMessages(environmentKey: String, accountId: Long, roomId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_v2_outbox WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId ORDER BY createdAtMillis ASC")
    abstract fun observeOutbox(environmentKey: String, accountId: Long, roomId: String): Flow<List<ChatOutboxEntity>>

    @Query("SELECT * FROM chat_v2_checkpoint WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    abstract suspend fun checkpoint(environmentKey: String, accountId: Long, roomId: String): ChatCheckpointEntity?

    @Query("SELECT * FROM chat_v2_conversation WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    abstract suspend fun conversation(environmentKey: String, accountId: Long, roomId: String): ChatConversationEntity?

    @Query("SELECT * FROM chat_v2_message WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND messageId = :messageId")
    abstract suspend fun message(environmentKey: String, accountId: Long, roomId: String, messageId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_v2_outbox WHERE environmentKey = :environmentKey AND accountId = :accountId AND state = 'pending' AND nextAttemptAtMillis <= :nowMillis ORDER BY createdAtMillis ASC LIMIT :limit")
    abstract suspend fun readyOperations(environmentKey: String, accountId: Long, nowMillis: Long, limit: Int): List<ChatOutboxEntity>

    @Query("SELECT * FROM chat_v2_outbox WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND operationId = :operationId")
    abstract suspend fun operation(environmentKey: String, accountId: Long, roomId: String, operationId: String): ChatOutboxEntity?

    @Query("SELECT * FROM chat_v2_tombstone WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND messageId = :messageId")
    abstract suspend fun tombstone(environmentKey: String, accountId: Long, roomId: String, messageId: String): ChatTombstoneEntity?

    @Query("SELECT * FROM chat_v2_reaction WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND messageId = :messageId")
    abstract suspend fun reactions(environmentKey: String, accountId: Long, roomId: String, messageId: String): List<ChatReactionEntity>

    @Query("SELECT * FROM chat_v2_receipt WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    abstract suspend fun receipts(environmentKey: String, accountId: Long, roomId: String): List<ChatReceiptEntity>

    @Query("SELECT * FROM chat_v2_receipt WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND actorId = :actorId AND receiptKind = :receiptKind")
    protected abstract suspend fun receipt(environmentKey: String, accountId: Long, roomId: String, actorId: Long, receiptKind: String): ChatReceiptEntity?

    @Query("SELECT * FROM chat_v2_attachment_transfer WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND attachmentId = :attachmentId")
    abstract suspend fun attachmentTransfer(environmentKey: String, accountId: Long, roomId: String, attachmentId: String): ChatAttachmentTransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putConversation(entity: ChatConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putMessage(entity: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOperation(entity: ChatOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putCheckpoint(entity: ChatCheckpointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceTombstone(entity: ChatTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putReaction(entity: ChatReactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceReceipt(entity: ChatReceiptEntity)

    @Query("UPDATE chat_v2_message SET deliveryState = 'delivered', updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND senderId = :accountId AND sequence > 0 AND sequence <= :throughSequence AND deliveryState = 'sent'")
    protected abstract suspend fun markDeliveredThrough(environmentKey: String, accountId: Long, roomId: String, throughSequence: Long, nowMillis: Long)

    @Query("UPDATE chat_v2_message SET deliveryState = 'read', updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND senderId = :accountId AND sequence > 0 AND sequence <= :throughSequence AND deliveryState IN ('sent', 'delivered')")
    protected abstract suspend fun markReadThrough(environmentKey: String, accountId: Long, roomId: String, throughSequence: Long, nowMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putAttachmentTransfer(entity: ChatAttachmentTransferEntity)

    @Query("UPDATE chat_v2_outbox SET state = 'sending', attemptCount = attemptCount + 1, updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND operationId = :operationId AND state = 'pending'")
    protected abstract suspend fun claimOperation(environmentKey: String, accountId: Long, roomId: String, operationId: String, nowMillis: Long): Int

    @Query("UPDATE chat_v2_message SET deliveryState = :state, updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND operationId = :operationId")
    protected abstract suspend fun updateMessageState(environmentKey: String, accountId: Long, roomId: String, operationId: String, state: String, nowMillis: Long)

    @Query("UPDATE chat_v2_outbox SET state = 'pending', nextAttemptAtMillis = :nextAttemptAtMillis, lastErrorCode = :errorCode, updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND operationId = :operationId AND state = 'sending'")
    protected abstract suspend fun releaseForRetry(environmentKey: String, accountId: Long, roomId: String, operationId: String, nextAttemptAtMillis: Long, errorCode: String, nowMillis: Long)

    @Query("UPDATE chat_v2_outbox SET state = 'failed', lastErrorCode = :errorCode, updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND operationId = :operationId")
    protected abstract suspend fun failOperation(environmentKey: String, accountId: Long, roomId: String, operationId: String, errorCode: String, nowMillis: Long)

    @Query("UPDATE chat_v2_outbox SET state = 'pending', attemptCount = 0, nextAttemptAtMillis = :nowMillis, lastErrorCode = NULL, updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND state = 'failed'")
    protected abstract suspend fun resetFailedOperations(environmentKey: String, accountId: Long, roomId: String, nowMillis: Long): Int

    @Query("UPDATE chat_v2_message SET deliveryState = 'pending', updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND deliveryState = 'failed' AND operationId IN (SELECT operationId FROM chat_v2_outbox WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND state = 'pending')")
    protected abstract suspend fun resetFailedMessageStates(environmentKey: String, accountId: Long, roomId: String, nowMillis: Long)

    @Query("UPDATE chat_v2_outbox SET state = 'acknowledged', lastErrorCode = NULL, updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND operationId = :operationId")
    protected abstract suspend fun acknowledgeOperation(environmentKey: String, accountId: Long, roomId: String, operationId: String, nowMillis: Long)

    @Query("UPDATE chat_v2_outbox SET state = 'pending', nextAttemptAtMillis = :nowMillis, updatedAtMillis = :nowMillis WHERE environmentKey = :environmentKey AND accountId = :accountId AND state = 'sending'")
    abstract suspend fun recoverInterruptedOperations(environmentKey: String, accountId: Long, nowMillis: Long)

    @Query("DELETE FROM chat_v2_message WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND messageId = :provisionalId")
    protected abstract suspend fun deleteProvisionalMessage(environmentKey: String, accountId: Long, roomId: String, provisionalId: String)

    @Query("DELETE FROM chat_v2_message WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId AND messageId = :messageId")
    protected abstract suspend fun deleteMessage(environmentKey: String, accountId: Long, roomId: String, messageId: String)

    @Query("DELETE FROM chat_v2_outbox WHERE state = 'acknowledged' AND updatedAtMillis < :beforeMillis")
    abstract suspend fun pruneAcknowledged(beforeMillis: Long)

    @Query("DELETE FROM chat_v2_conversation WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteConversations(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_message WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteMessages(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_checkpoint WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteCheckpoints(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_outbox WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteOutbox(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_tombstone WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteTombstones(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_reaction WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteReactions(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_receipt WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteReceipts(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_attachment_transfer WHERE environmentKey = :environmentKey AND accountId = :accountId")
    protected abstract suspend fun deleteAttachmentTransfers(environmentKey: String, accountId: Long)

    @Query("DELETE FROM chat_v2_conversation WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomConversation(environmentKey: String, accountId: Long, roomId: String)

    @Query("DELETE FROM chat_v2_message WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomMessages(environmentKey: String, accountId: Long, roomId: String)

    @Query("DELETE FROM chat_v2_checkpoint WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomCheckpoint(environmentKey: String, accountId: Long, roomId: String)

    @Query("DELETE FROM chat_v2_outbox WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomOutbox(environmentKey: String, accountId: Long, roomId: String)

    @Query("DELETE FROM chat_v2_tombstone WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomTombstones(environmentKey: String, accountId: Long, roomId: String)

    @Query("DELETE FROM chat_v2_reaction WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomReactions(environmentKey: String, accountId: Long, roomId: String)

    @Query("DELETE FROM chat_v2_receipt WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomReceipts(environmentKey: String, accountId: Long, roomId: String)

    @Query("DELETE FROM chat_v2_attachment_transfer WHERE environmentKey = :environmentKey AND accountId = :accountId AND roomId = :roomId")
    protected abstract suspend fun deleteRoomAttachmentTransfers(environmentKey: String, accountId: Long, roomId: String)

    @Transaction
    open suspend fun enqueue(message: ChatMessageEntity, operation: ChatOutboxEntity) {
        require(message.environmentKey == operation.environmentKey && message.accountId == operation.accountId && message.roomId == operation.roomId)
        require(message.operationId == operation.operationId)
        putMessage(message)
        insertOperation(operation)
    }

    @Transaction
    open suspend fun claim(operation: ChatOutboxEntity, nowMillis: Long): ChatOutboxEntity? {
        if (claimOperation(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, nowMillis) != 1) return null
        updateMessageState(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, ChatStorageStates.SENDING, nowMillis)
        return operation(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId)
    }

    @Transaction
    open suspend fun retry(operation: ChatOutboxEntity, nextAttemptAtMillis: Long, errorCode: String, nowMillis: Long) {
        releaseForRetry(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, nextAttemptAtMillis, errorCode, nowMillis)
        updateMessageState(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, ChatStorageStates.PENDING, nowMillis)
    }

    @Transaction
    open suspend fun fail(operation: ChatOutboxEntity, errorCode: String, nowMillis: Long) {
        failOperation(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, errorCode, nowMillis)
        updateMessageState(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, ChatStorageStates.FAILED, nowMillis)
    }

    @Transaction
    open suspend fun putTombstone(entity: ChatTombstoneEntity) {
        val current = tombstone(entity.environmentKey, entity.accountId, entity.roomId, entity.messageId)
        if (current != null && entity.messageRevision < current.messageRevision) return
        replaceTombstone(if (current == null) entity else entity.copy(
            messageRevision = maxOf(current.messageRevision, entity.messageRevision),
            changeSequence = maxOf(current.changeSequence, entity.changeSequence),
            deletionScope = strongestDeletionScope(current.deletionScope, entity.deletionScope),
            updatedAtMillis = maxOf(current.updatedAtMillis, entity.updatedAtMillis),
        ))
    }

    @Transaction
    open suspend fun putReceipt(entity: ChatReceiptEntity) {
        val current = receipt(entity.environmentKey, entity.accountId, entity.roomId, entity.actorId, entity.receiptKind)
        replaceReceipt(if (current == null) entity else entity.copy(
            throughSequence = maxOf(current.throughSequence, entity.throughSequence),
            changeSequence = maxOf(current.changeSequence, entity.changeSequence),
            updatedAtMillis = maxOf(current.updatedAtMillis, entity.updatedAtMillis),
        ))
    }

    /** Manual retry preserves the original operation UUID and exact opaque envelope. */
    @Transaction
    open suspend fun retryFailedOperations(environmentKey: String, accountId: Long, roomId: String, nowMillis: Long): Int {
        val reset = resetFailedOperations(environmentKey, accountId, roomId, nowMillis)
        if (reset > 0) resetFailedMessageStates(environmentKey, accountId, roomId, nowMillis)
        return reset
    }

    @Transaction
    open suspend fun acknowledge(operation: ChatOutboxEntity, serverMessage: ChatMessageEntity, nowMillis: Long) {
        mergeRemoteMessages(listOf(serverMessage.copy(
            operationId = serverMessage.operationId ?: operation.operationId,
            deliveryState = ChatStorageStates.SENT,
            updatedAtMillis = nowMillis,
        )))
        acknowledgeOperation(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, nowMillis)
    }

    @Transaction
    open suspend fun acknowledgeProjection(
        operation: ChatOutboxEntity,
        serverMessage: ChatMessageEntity?,
        deletion: ChatTombstoneEntity?,
        nowMillis: Long,
    ) {
        deleteProvisionalMessage(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId)
        deletion?.let {
            putTombstone(it)
            applyTombstone(it)
        }
        serverMessage?.let {
            mergeRemoteMessages(listOf(it.copy(
                operationId = it.operationId ?: operation.operationId,
                deliveryState = ChatStorageStates.SENT,
                updatedAtMillis = nowMillis,
            )))
        }
        acknowledgeOperation(operation.environmentKey, operation.accountId, operation.roomId, operation.operationId, nowMillis)
    }

    @Transaction
    open suspend fun mergeRemoteMessages(messages: List<ChatMessageEntity>) {
        messages.forEach { remote ->
            remote.operationId?.let { acknowledgedOperationId ->
                if (acknowledgedOperationId != remote.messageId) {
                    deleteProvisionalMessage(remote.environmentKey, remote.accountId, remote.roomId, acknowledgedOperationId)
                }
                operation(remote.environmentKey, remote.accountId, remote.roomId, acknowledgedOperationId)?.let {
                    acknowledgeOperation(remote.environmentKey, remote.accountId, remote.roomId, acknowledgedOperationId, remote.updatedAtMillis)
                }
            }
            val deletion = tombstone(remote.environmentKey, remote.accountId, remote.roomId, remote.messageId)
            if (deletion != null) {
                if (deletion.deletionScope in VIEWER_HIDDEN_SCOPES) {
                    deleteMessage(remote.environmentKey, remote.accountId, remote.roomId, remote.messageId)
                    return@forEach
                }
                if (deletion.messageRevision >= remote.revision && !remote.isTombstone) {
                    applyTombstone(deletion)
                    return@forEach
                }
            }
            val local = message(remote.environmentKey, remote.accountId, remote.roomId, remote.messageId)
            putMessage(if (local == null) remote else mergeRevisionedMessage(local, remote))
        }
    }

    @Transaction
    open suspend fun mergeHistory(messages: List<ChatMessageEntity>, conversation: ChatConversationEntity) {
        mergeRemoteMessages(messages)
        putConversation(conversation)
    }

    @Transaction
    open suspend fun applyDelta(
        messages: List<ChatMessageEntity>,
        tombstones: List<ChatTombstoneEntity>,
        receipts: List<ChatReceiptEntity>,
        checkpoint: ChatCheckpointEntity,
        conversation: ChatConversationEntity,
    ) {
        tombstones.forEach { deletion ->
            val current = tombstone(deletion.environmentKey, deletion.accountId, deletion.roomId, deletion.messageId)
            if (current != null && deletion.messageRevision < current.messageRevision &&
                deletion.deletionScope !in VIEWER_HIDDEN_SCOPES) return@forEach
            putTombstone(deletion)
            applyTombstone(deletion)
        }
        mergeRemoteMessages(messages)
        receipts.forEach { receipt ->
            putReceipt(receipt)
            // A user's own receipt acknowledges partner messages. Only a partner's
            // receipt advances delivery/read state on this account's outgoing rows.
            if (receipt.actorId != receipt.accountId) when (receipt.receiptKind) {
                "delivered" -> markDeliveredThrough(receipt.environmentKey, receipt.accountId, receipt.roomId, receipt.throughSequence, receipt.updatedAtMillis)
                "read" -> markReadThrough(receipt.environmentKey, receipt.accountId, receipt.roomId, receipt.throughSequence, receipt.updatedAtMillis)
            }
        }
        advanceCheckpoint(checkpoint, conversation)
    }

    private suspend fun applyTombstone(tombstone: ChatTombstoneEntity) {
        val existing = message(tombstone.environmentKey, tombstone.accountId, tombstone.roomId, tombstone.messageId)
        if (tombstone.deletionScope in VIEWER_HIDDEN_SCOPES) {
            deleteMessage(tombstone.environmentKey, tombstone.accountId, tombstone.roomId, tombstone.messageId)
        } else if (existing != null && tombstone.messageRevision >= existing.revision) {
            putMessage(existing.copy(
                revision = maxOf(existing.revision, tombstone.messageRevision),
                opaqueEnvelope = null,
                replyToId = null,
                isTombstone = true,
                updatedAtMillis = tombstone.updatedAtMillis,
            ))
        }
    }

    /** Revision order owns message content; delivery state is monotonic across every source. */
    private fun mergeRevisionedMessage(local: ChatMessageEntity, remote: ChatMessageEntity): ChatMessageEntity {
        val strongestDelivery = ChatStorageStates.strongest(local.deliveryState, remote.deliveryState)
        if (remote.revision < local.revision) return local.copy(
            deliveryState = strongestDelivery,
            updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
        )
        if (remote.revision > local.revision) return remote.copy(
            operationId = remote.operationId ?: local.operationId,
            deliveryState = strongestDelivery,
            updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
        )

        val isTombstone = local.isTombstone || remote.isTombstone
        return remote.copy(
            operationId = stableValue(local.operationId, remote.operationId),
            sequence = maxOf(local.sequence, remote.sequence),
            senderId = listOfNotNull(local.senderId, remote.senderId).minOrNull(),
            contentKind = stableValue(local.contentKind, remote.contentKind) ?: remote.contentKind,
            opaqueEnvelope = if (isTombstone) null else stableValue(local.opaqueEnvelope, remote.opaqueEnvelope),
            replyToId = if (isTombstone) null else stableValue(local.replyToId, remote.replyToId),
            isTombstone = isTombstone,
            createdAt = listOfNotNull(local.createdAt, remote.createdAt).minOrNull(),
            editedAt = listOfNotNull(local.editedAt, remote.editedAt).maxOrNull(),
            deliveryState = strongestDelivery,
            updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
        )
    }

    private fun stableValue(first: String?, second: String?): String? =
        listOfNotNull(first, second).maxOrNull()

    private fun strongestDeletionScope(first: String, second: String): String {
        val global = setOf("message_tombstoned", "message_deleted", "delete_for_everyone")
        return when {
            first in global -> first
            second in global -> second
            else -> stableValue(first, second) ?: first
        }
    }

    /** Explicit logout/account-switch policy: no private projection or queued write crosses accounts. */
    @Transaction
    open suspend fun clearAccount(environmentKey: String, accountId: Long) {
        deleteAttachmentTransfers(environmentKey, accountId)
        deleteReactions(environmentKey, accountId)
        deleteReceipts(environmentKey, accountId)
        deleteTombstones(environmentKey, accountId)
        deleteOutbox(environmentKey, accountId)
        deleteMessages(environmentKey, accountId)
        deleteCheckpoints(environmentKey, accountId)
        deleteConversations(environmentKey, accountId)
    }

    /** Removes every private artifact for one revoked room without affecting other rooms. */
    @Transaction
    open suspend fun clearRoom(environmentKey: String, accountId: Long, roomId: String) {
        deleteRoomAttachmentTransfers(environmentKey, accountId, roomId)
        deleteRoomReactions(environmentKey, accountId, roomId)
        deleteRoomReceipts(environmentKey, accountId, roomId)
        deleteRoomTombstones(environmentKey, accountId, roomId)
        deleteRoomOutbox(environmentKey, accountId, roomId)
        deleteRoomMessages(environmentKey, accountId, roomId)
        deleteRoomCheckpoint(environmentKey, accountId, roomId)
        deleteRoomConversation(environmentKey, accountId, roomId)
    }

    @Transaction
    open suspend fun advanceCheckpoint(checkpoint: ChatCheckpointEntity, conversation: ChatConversationEntity) {
        val current = checkpoint(checkpoint.environmentKey, checkpoint.accountId, checkpoint.roomId)
        if (current == null || checkpoint.changeSequence > current.changeSequence) putCheckpoint(checkpoint)
        putConversation(conversation)
    }

    private companion object {
        val VIEWER_HIDDEN_SCOPES = setOf("message_hidden", "delete_for_me")
    }
}

@Database(
    entities = [
        ChatConversationEntity::class,
        ChatMessageEntity::class,
        ChatCheckpointEntity::class,
        ChatOutboxEntity::class,
        ChatTombstoneEntity::class,
        ChatReactionEntity::class,
        ChatReceiptEntity::class,
        ChatAttachmentTransferEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatV2(): ChatV2Dao
}

@Suppress("KotlinNoActualForExpect")
expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}
