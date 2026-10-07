@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.srisu.srisu.features.chat

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiError
import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.data.remote.SocketConnection
import com.srisu.srisu.core.data.remote.SocketConnector
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.chat.v2.crypto.E2eeEngine
import com.srisu.srisu.features.chat.v2.crypto.E2eeEngineAvailability
import com.srisu.srisu.features.chat.v2.crypto.E2eeSealContext
import com.srisu.srisu.features.chat.v2.crypto.OpaqueE2eeEnvelope
import com.srisu.srisu.features.chat.v2.crypto.UnavailableE2eeEngine
import com.srisu.srisu.features.chat.v2.data.ChatV2Repository
import com.srisu.srisu.features.chat.v2.data.local.ChatCheckpointEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatConversationEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatDatabase
import com.srisu.srisu.features.chat.v2.data.local.ChatMessageEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatOutboxEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatReceiptEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatStorageStates
import com.srisu.srisu.features.chat.v2.data.local.ChatTombstoneEntity
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2CapabilitiesDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2ChangeDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2ChangesPageDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2MessageDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2MessagesPageDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2OperationRequest
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2OperationResultDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2ReceiptRequest
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2ReceiptResultDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2Remote
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2SendMessageRequest
import com.srisu.srisu.features.chat.v2.domain.SecureChatAvailability
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketEvent
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ChatV2RepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: ChatDatabase
    private lateinit var storage: MemorySessionStorage
    private lateinit var sessions: SessionCoordinator
    private lateinit var lifetime: ApplicationLifetime
    private var clock = 1_000L

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val path = RuntimeEnvironment.getApplication().getDatabasePath("chat-v2-repository-${UUID.randomUUID()}.db").absolutePath
        database = Room.databaseBuilder<ChatDatabase>(RuntimeEnvironment.getApplication(), path)
            .setDriver(AndroidSQLiteDriver()).build()
        storage = MemorySessionStorage(ApiJson.encodeToString(Session(id = ACCOUNT, access = "synthetic")))
        sessions = SessionCoordinator(storage)
        lifetime = ApplicationLifetime()
    }

    @After fun tearDown() {
        lifetime.close()
        database.close()
        Dispatchers.resetMain()
    }

    @Test fun lostAckRetriesExactOperationAndEnvelopeThenReconcilesOneMessage() = runTest(dispatcher) {
        database.chatV2().advanceCheckpoint(
            ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 0, clock),
            ChatConversationEntity(ENV, ACCOUNT, ROOM, "relationship-7", 7, 0, 0, 0, clock),
        )
        val remote = FakeRemote().apply {
            sendResults += NetworkAPIResult.Error(ApiError(
                NetworkAPIResult.ErrorType.NETWORK, "network", "offline", retryable = true,
            ))
            sendResults += NetworkAPIResult.Success(operationResult(OPERATION))
        }
        val engine = FakeE2eeEngine()
        val repository = repository(remote, engine)

        assertEquals(OPERATION, repository.queueText(ROOM, "synthetic secret", replyToId = 12).getOrThrow())
        repository.drainOutbox(8)
        advanceUntilIdle()
        val pending = database.chatV2().operation(ENV, ACCOUNT, ROOM, OPERATION)
        assertEquals(ChatStorageStates.OUTBOX_PENDING, pending?.state)
        assertEquals(1, pending?.attemptCount)
        assertEquals(1, remote.sent.size)
        assertEquals(OPERATION, engine.contexts.single().operationId)
        assertEquals("relationship-7", engine.contexts.single().conversationId)
        assertEquals(7L, engine.contexts.single().membershipEpoch)
        assertEquals("12", engine.contexts.single().replyToId)

        clock = requireNotNull(pending).nextAttemptAtMillis
        repository.drainOutbox(8)
        assertEquals(2, remote.sent.size)
        assertEquals(remote.sent[0].operationId, remote.sent[1].operationId)
        assertEquals(remote.sent[0].envelope, remote.sent[1].envelope)
        assertEquals(ChatStorageStates.OUTBOX_ACKNOWLEDGED,
            database.chatV2().operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        assertEquals(ChatStorageStates.SENT, database.chatV2().message(ENV, ACCOUNT, ROOM, "9")?.deliveryState)
        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, OPERATION))
        repository.close()
    }

    @Test fun historyOperationIdClosesLostAckWithoutWaitingForPostReplay() = runTest(dispatcher) {
        database.chatV2().enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        val remote = FakeRemote().apply {
            messagePages[ROOM] = ChatV2MessagesPageDto(
                roomId = ROOM,
                highWatermark = 9,
                messages = listOf(serverMessage(id = 88, sequence = 9, revision = 1).copy(
                    operationId = OPERATION,
                    senderId = ACCOUNT,
                )),
            )
        }
        val repository = repository(remote, FakeE2eeEngine())

        repository.syncRoom(ROOM)

        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, OPERATION))
        assertEquals(OPERATION, database.chatV2().message(ENV, ACCOUNT, ROOM, "88")?.operationId)
        assertEquals(ChatStorageStates.OUTBOX_ACKNOWLEDGED,
            database.chatV2().operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        repository.close()
    }

    @Test fun manualRetryPreservesFailedOperationIdentityAndExactEnvelope() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        val claimed = requireNotNull(dao.claim(requireNotNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION)), clock))
        dao.fail(claimed, "validation_failed", clock)
        val failed = requireNotNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION))
        val remote = FakeRemote().apply { sendResults += NetworkAPIResult.Success(operationResult(OPERATION)) }
        val repository = repository(remote, FakeE2eeEngine())

        assertEquals(1, repository.retryFailedMessagesNow(ROOM))

        assertEquals(1, remote.sent.size)
        assertEquals(failed.operationId, remote.sent.single().operationId)
        assertEquals(parseEnvelope(failed.opaqueEnvelope), remote.sent.single().envelope)
        assertEquals(ChatStorageStates.OUTBOX_ACKNOWLEDGED,
            dao.operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        repository.close()
    }

    @Test fun replayedHiddenSuccessStillClosesOutboxAndSuppressesProvisionalMessage() = runTest(dispatcher) {
        database.chatV2().enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        val hidden = operationResult(OPERATION).let { result ->
            result.copy(
                replayed = true,
                message = result.message.copy(
                    operationId = OPERATION,
                    envelope = null,
                    hiddenForViewer = true,
                ),
            )
        }
        val remote = FakeRemote().apply { sendResults += NetworkAPIResult.Success(hidden) }
        val repository = repository(remote, FakeE2eeEngine())

        repository.drainOutbox(8)

        assertEquals(ChatStorageStates.OUTBOX_ACKNOWLEDGED,
            database.chatV2().operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, OPERATION))
        assertEquals("message_hidden", database.chatV2().tombstone(ENV, ACCOUNT, ROOM, "9")?.deletionScope)
        repository.close()
    }

    @Test fun audienceSequenceGapsAdvanceScannedCursorButOutOfOrderPageDoesNot() = runTest(dispatcher) {
        database.chatV2().mergeRemoteMessages(listOf(localMessage(ACCOUNT).copy(
            messageId = "3", operationId = null, sequence = 3, deliveryState = ChatStorageStates.SENT,
        )))
        val remote = FakeRemote().apply {
            changes[ROOM] = ChatV2ChangesPageDto(
                roomId = ROOM,
                highWatermark = 5,
                changes = listOf(change(2), change(5, actorId = 2, payload = buildJsonObject { put("read_through", 3) })),
                nextAfterSequence = 5,
            )
            changes[ROOM_2] = ChatV2ChangesPageDto(
                roomId = ROOM_2,
                highWatermark = 5,
                changes = listOf(change(5), change(2)),
                nextAfterSequence = 5,
            )
            changes[ROOM_3] = ChatV2ChangesPageDto(
                roomId = ROOM_3,
                highWatermark = 9,
                changes = emptyList(),
                nextAfterSequence = 9,
            )
        }
        val repository = repository(remote, FakeE2eeEngine())
        repository.syncRoom(ROOM)
        assertEquals(5, database.chatV2().checkpoint(ENV, ACCOUNT, ROOM)?.changeSequence)
        assertEquals(ChatStorageStates.READ, database.chatV2().message(ENV, ACCOUNT, ROOM, "3")?.deliveryState)
        repository.syncRoom(ROOM) // duplicated already-scanned changes do not regress or duplicate.
        assertEquals(5, database.chatV2().checkpoint(ENV, ACCOUNT, ROOM)?.changeSequence)
        repository.syncRoom(ROOM_2)
        assertNull(database.chatV2().checkpoint(ENV, ACCOUNT, ROOM_2))
        repository.syncRoom(ROOM_3)
        assertEquals(9, database.chatV2().checkpoint(ENV, ACCOUNT, ROOM_3)?.changeSequence)
        repository.close()
    }

    @Test fun accountSwitchClearsOldProjectionAndObserverCannotLeakIt() = runTest(dispatcher) {
        val repository = repository(FakeRemote(), FakeE2eeEngine())
        database.chatV2().enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        advanceUntilIdle()
        storage.value = ApiJson.encodeToString(Session(id = 2, access = "second"))
        sessions.saveSession(requireNotNull(storage.value), SESSION_KEY)
        advanceUntilIdle()

        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, OPERATION))
        val secondAccountState = repository.observeRoom(ROOM).first()
        assertTrue(secondAccountState.messages.isEmpty())
        repository.close()
    }

    @Test fun sessionSwitchDuringSuspendedDatabaseWriteCannotRepopulateOldAccount() = runTest(dispatcher) {
        val mutationEntered = CompletableDeferred<Unit>()
        val releaseMutation = CompletableDeferred<Unit>()
        var interceptNextMutation = true
        val repository = repository(FakeRemote(), FakeE2eeEngine(), mutationInterlock = {
            if (interceptNextMutation) {
                interceptNextMutation = false
                mutationEntered.complete(Unit)
                releaseMutation.await()
            }
        })
        val queueResult = async { repository.queueText(ROOM, "synthetic secret") }
        mutationEntered.await()

        storage.value = ApiJson.encodeToString(Session(id = 2, access = "second"))
        sessions.saveSession(requireNotNull(storage.value), SESSION_KEY)
        runCurrent() // The session-clear collector is now waiting on the same mutation lock.
        releaseMutation.complete(Unit)
        advanceUntilIdle()

        assertTrue(queueResult.await().isFailure)
        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(database.chatV2().operation(ENV, ACCOUNT, ROOM, OPERATION))
        repository.close()
    }

    @Test fun accessRevocationPurgesActiveRoomAndBlocksStaleRepopulation() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        dao.advanceCheckpoint(
            ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 4, clock),
            ChatConversationEntity(ENV, ACCOUNT, ROOM, "relationship-7", 7, 9, 4, 0, clock),
        )
        dao.putTombstone(ChatTombstoneEntity(ENV, ACCOUNT, ROOM, "8", 1, 3, "message_hidden", clock))
        dao.putReceipt(ChatReceiptEntity(ENV, ACCOUNT, ROOM, 2, "read", 9, 4, clock))
        val remote = FakeRemote().apply {
            messagePages[ROOM] = ChatV2MessagesPageDto(
                roomId = ROOM,
                highWatermark = 10,
                messages = listOf(serverMessage(id = 99, sequence = 10, revision = 1)),
            )
        }
        val repository = repository(remote, FakeE2eeEngine())
        val stamp = sessions.stamp()
        repository.activateRoom(ROOM)

        repository.handleAccessRevoked(stamp, ROOM)
        advanceUntilIdle()

        assertNull(dao.message(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.checkpoint(ENV, ACCOUNT, ROOM))
        assertNull(dao.conversation(ENV, ACCOUNT, ROOM))
        assertNull(dao.tombstone(ENV, ACCOUNT, ROOM, "8"))
        assertTrue(dao.receipts(ENV, ACCOUNT, ROOM).isEmpty())
        assertNull(dao.message(ENV, ACCOUNT, ROOM, "99"))
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM).first().availability)
        assertEquals(SecureChatAvailability.Reason.ACCESS_REVOKED, unavailable.reason)

        repository.syncRoom(ROOM)
        assertNull(dao.message(ENV, ACCOUNT, ROOM, "99"))
        repository.close()
    }

    @Test fun userGroupAccessRevocationPurgesInactiveCachedRoom() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        dao.advanceCheckpoint(
            ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 4, clock),
            ChatConversationEntity(ENV, ACCOUNT, ROOM, "relationship-7", 7, 9, 4, 0, clock),
        )
        val repository = repository(FakeRemote(), FakeE2eeEngine())

        repository.handleAccessRevoked(sessions.stamp(), ROOM)

        assertNull(dao.message(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.checkpoint(ENV, ACCOUNT, ROOM))
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM).first().availability)
        assertEquals(SecureChatAvailability.Reason.ACCESS_REVOKED, unavailable.reason)
        repository.close()
    }

    @Test fun foregroundNotFoundAfterMissedRevocationPurgesRoomEvenWhenWritesAreGated() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        dao.advanceCheckpoint(
            ChatCheckpointEntity(ENV, ACCOUNT, ROOM, 4, clock),
            ChatConversationEntity(ENV, ACCOUNT, ROOM, "relationship-7", 7, 9, 4, 0, clock),
        )
        dao.putTombstone(ChatTombstoneEntity(ENV, ACCOUNT, ROOM, "8", 1, 3, "message_hidden", clock))
        dao.putReceipt(ChatReceiptEntity(ENV, ACCOUNT, ROOM, 2, "read", 9, 4, clock))
        val remote = FakeRemote().apply { encryptedWritesEnabled = false }
        val repository = repository(remote, FakeE2eeEngine())
        repository.activateRoom(ROOM)
        advanceUntilIdle()
        assertEquals(ChatStorageStates.OUTBOX_PENDING, dao.operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)

        remote.messageFailure = ApiError(
            kind = NetworkAPIResult.ErrorType.NOT_FOUND,
            code = "not_found",
            message = "This resource is unavailable.",
            status = 404,
        )
        lifetime.setForeground(true)
        advanceUntilIdle()

        assertNull(dao.message(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.checkpoint(ENV, ACCOUNT, ROOM))
        assertNull(dao.conversation(ENV, ACCOUNT, ROOM))
        assertNull(dao.tombstone(ENV, ACCOUNT, ROOM, "8"))
        assertTrue(dao.receipts(ENV, ACCOUNT, ROOM).isEmpty())
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM).first().availability)
        assertEquals(SecureChatAvailability.Reason.ACCESS_REVOKED, unavailable.reason)
        repository.close()
    }

    @Test fun authoritativeSendDenialPurgesInactiveRoomOutbox() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        val remote = FakeRemote().apply {
            sendResults += NetworkAPIResult.Error(ApiError(
                kind = NetworkAPIResult.ErrorType.FORBIDDEN,
                code = "forbidden",
                message = "This action is not allowed.",
                status = 403,
            ))
        }
        val repository = repository(remote, FakeE2eeEngine())

        repository.drainOutbox(8)

        assertNull(dao.message(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION))
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM).first().availability)
        assertEquals(SecureChatAvailability.Reason.ACCESS_REVOKED, unavailable.reason)
        repository.close()
    }

    @Test fun endedRelationshipPurgesOnlyIdentifiedInactiveRoom() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        dao.advanceCheckpoint(
            ChatCheckpointEntity(ENV, ACCOUNT, ROOM_2, 1, clock),
            ChatConversationEntity(ENV, ACCOUNT, ROOM_2, "relationship-8", 1, 1, 1, 0, clock),
        )
        val repository = repository(FakeRemote(), FakeE2eeEngine())

        repository.handleRelationshipChanged(
            sessions.stamp(),
            ChatWebSocketEvent.RelationshipChanged(7, 2, "ENDED", ROOM),
        )

        assertNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.message(ENV, ACCOUNT, ROOM, OPERATION))
        assertEquals(1L, dao.checkpoint(ENV, ACCOUNT, ROOM_2)?.changeSequence)
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM).first().availability)
        assertEquals(SecureChatAvailability.Reason.ACCESS_REVOKED, unavailable.reason)
        repository.close()
    }

    @Test fun legacyEndedRelationshipWithoutRoomFailsClosedAndClearsAccountCache() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        dao.advanceCheckpoint(
            ChatCheckpointEntity(ENV, ACCOUNT, ROOM_2, 1, clock),
            ChatConversationEntity(ENV, ACCOUNT, ROOM_2, "relationship-8", 1, 1, 1, 0, clock),
        )
        val repository = repository(FakeRemote(), FakeE2eeEngine())
        repository.ensureCapabilities()

        repository.handleRelationshipChanged(
            sessions.stamp(),
            ChatWebSocketEvent.RelationshipChanged(7, 2, "BREAK-UP"),
        )

        assertNull(dao.operation(ENV, ACCOUNT, ROOM, OPERATION))
        assertNull(dao.checkpoint(ENV, ACCOUNT, ROOM_2))
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM_2).first().availability)
        assertEquals(SecureChatAvailability.Reason.ACCESS_REVOKED, unavailable.reason)

        repository.handleRelationshipChanged(
            sessions.stamp(),
            ChatWebSocketEvent.RelationshipChanged(9, 1, "ACCEPTED", ROOM_3),
        )
        assertIs<SecureChatAvailability.Ready>(repository.observeRoom(ROOM_3).first().availability)
        repository.close()
    }

    @Test fun transientServerSendFailureRetainsExactOutboxForRetry() = runTest(dispatcher) {
        val dao = database.chatV2()
        dao.enqueue(localMessage(ACCOUNT), localOperation(ACCOUNT))
        val remote = FakeRemote().apply {
            sendResults += NetworkAPIResult.Error(ApiError(
                kind = NetworkAPIResult.ErrorType.SERVER,
                code = "temporarily_unavailable",
                message = "The service could not complete this request.",
                status = 503,
                retryable = true,
            ))
        }
        val repository = repository(remote, FakeE2eeEngine())

        repository.drainOutbox(8)

        assertEquals(ChatStorageStates.OUTBOX_PENDING, dao.operation(ENV, ACCOUNT, ROOM, OPERATION)?.state)
        assertEquals(ChatStorageStates.PENDING, dao.message(ENV, ACCOUNT, ROOM, OPERATION)?.deliveryState)
        repository.close()
    }

    @Test fun subscribeAcknowledgmentTriggersCatchUpAcrossSnapshotHandoffGap() = runTest(dispatcher) {
        val changesSnapshotTaken = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        val postAckCatchUpRequested = CompletableDeferred<Unit>()
        val remote = FakeRemote().apply {
            changes[ROOM] = ChatV2ChangesPageDto(
                roomId = ROOM,
                highWatermark = 1,
                changes = listOf(change(1)),
                nextAfterSequence = 1,
            )
            pauseNextChangesAtSnapshot = changesSnapshotTaken to releaseSnapshot
            notifyChangesAfter = 1L to postAckCatchUpRequested
        }
        val connection = TestSocketConnection()
        val socket = ChatWebSocketClient(SocketConnector { connection }, sessions, lifetime)
        val repository = repository(remote, FakeE2eeEngine(), webSocketClient = socket)
        // Settle the foreground transition before activation. Otherwise the
        // foreground recovery and activation can enqueue two pre-ACK syncs,
        // allowing the second to consume N+1 before this test observes N.
        lifetime.setForeground(true)
        runCurrent()
        repository.activateRoom(ROOM)
        runCurrent()

        val subscribe = ApiJson.parseToJsonElement(connection.sent.receive()).jsonObject
        assertEquals("subscribe_room", subscribe["action"]?.jsonPrimitive?.content)
        assertEquals(0L, testScheduler.currentTime, "Subscription setup must not advance virtual time")
        // Do not suspend the test scheduler while the ACK timeout is pending:
        // pumping only the current virtual instant lets Room reach the snapshot
        // without incorrectly advancing the socket clock by ten seconds.
        runCurrentUntil("initial HTTP sync did not capture snapshot N") { changesSnapshotTaken.isCompleted }
        changesSnapshotTaken.await()

        remote.changes[ROOM] = ChatV2ChangesPageDto(
            roomId = ROOM,
            highWatermark = 2,
            changes = listOf(ChatV2ChangeDto(
                sequence = 2,
                kind = "message_created",
                messageId = 77,
                messageRevision = 1,
                actorId = 2,
                createdAt = "2026-10-07T00:00:01Z",
                message = serverMessage(id = 77, sequence = 2, revision = 1),
            )),
            nextAfterSequence = 2,
        )
        val postAckProjectionApplied = async {
            database.chatV2().observeMessages(ENV, ACCOUNT, ROOM).first { rows ->
                rows.any { it.messageId == "77" }
            }
        }
        runCurrent() // Install the Room observer before acknowledging the subscription.
        val requestId = requireNotNull(subscribe["request_id"]?.jsonPrimitive?.content)
        connection.incoming.send(
            """{"type":"success","protocol_version":2,"action":"subscribe_room","request_id":"$requestId","data":{}}"""
        )
        // ACK after N+1 exists but while the initial N response is still held.
        // The catch-up must serialize behind N, then request from cursor N.
        runCurrent()
        releaseSnapshot.complete(Unit)
        runCurrentUntil("subscribe ACK did not start its authoritative catch-up") {
            postAckCatchUpRequested.isCompleted
        }
        runCurrentUntil("post-ACK catch-up did not apply N+1") { postAckProjectionApplied.isCompleted }
        postAckProjectionApplied.await()

        assertEquals(listOf(0L, 1L), remote.changeRequestCursors.take(2),
            "Subscribe ACK must request changes after snapshot cursor N")
        assertEquals(2L, database.chatV2().checkpoint(ENV, ACCOUNT, ROOM)?.changeSequence)
        assertEquals(2L, database.chatV2().message(ENV, ACCOUNT, ROOM, "77")?.sequence)
        repository.close()
        socket.close()
    }

    @Test fun deactivateWhileSubscribeAckIsPendingExplicitlyUnsubscribesServerRoom() = runTest(dispatcher) {
        val connection = TestSocketConnection()
        val socket = ChatWebSocketClient(SocketConnector { connection }, sessions, lifetime)
        val repository = repository(FakeRemote(), FakeE2eeEngine(), webSocketClient = socket)
        lifetime.setForeground(true)
        repository.activateRoom(ROOM)
        runCurrent()

        val subscribe = ApiJson.parseToJsonElement(connection.sent.receive()).jsonObject
        assertEquals("subscribe_room", subscribe["action"]?.jsonPrimitive?.content)
        repository.deactivateRoom(ROOM)
        val subscribeRequestId = subscribe["request_id"]?.jsonPrimitive?.content
        connection.incoming.send(
            """{"type":"success","protocol_version":2,"action":"subscribe_room","request_id":"$subscribeRequestId","data":{}}"""
        )
        runCurrent()

        val unsubscribe = ApiJson.parseToJsonElement(connection.sent.receive()).jsonObject
        assertEquals("unsubscribe_room", unsubscribe["action"]?.jsonPrimitive?.content)
        assertEquals(ROOM, unsubscribe["payload"]?.jsonObject?.get("chat_room_id")?.jsonPrimitive?.content)
        val unsubscribeRequestId = unsubscribe["request_id"]?.jsonPrimitive?.content
        connection.incoming.send(
            """{"type":"success","action":"unsubscribe_room","request_id":"$unsubscribeRequestId","data":{}}"""
        )
        advanceUntilIdle()
        repository.close()
        socket.close()
    }

    @Test fun unavailableCryptoGateNeverQueuesOrFallsBack() = runTest(dispatcher) {
        val repository = repository(FakeRemote(), UnavailableE2eeEngine())
        repository.ensureCapabilities()
        val state = repository.observeRoom(ROOM).first()
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(state.availability)
        assertEquals(SecureChatAvailability.Reason.CRYPTO_ADAPTER_REQUIRED, unavailable.reason)
        assertTrue(repository.queueText(ROOM, "must not send").isFailure)
        assertNull(database.chatV2().operation(ENV, ACCOUNT, ROOM, OPERATION))
        repository.close()
    }

    @Test fun requiredDeviceSessionFailsClosedBeforeQueueing() = runTest(dispatcher) {
        val remote = FakeRemote().apply { requiresDeviceSession = true }
        val repository = repository(remote, FakeE2eeEngine())
        repository.ensureCapabilities()
        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM).first().availability)
        assertEquals(SecureChatAvailability.Reason.DEVICE_SESSION_REQUIRED, unavailable.reason)
        assertTrue(repository.queueText(ROOM, "must not send").isFailure)
        assertTrue(remote.sent.isEmpty())
        repository.close()
    }

    @Test fun foregroundResumeRefreshesServerCapabilityGate() = runTest(dispatcher) {
        val remote = FakeRemote()
        val repository = repository(remote, FakeE2eeEngine())
        repository.ensureCapabilities()
        assertIs<SecureChatAvailability.Ready>(repository.observeRoom(ROOM).first().availability)
        remote.encryptedWritesEnabled = false

        lifetime.setForeground(true)
        runCurrent()

        val unavailable = assertIs<SecureChatAvailability.Unavailable>(repository.observeRoom(ROOM).first().availability)
        assertEquals(SecureChatAvailability.Reason.SERVER_DISABLED, unavailable.reason)
        assertTrue(remote.capabilityCalls >= 2)
        lifetime.setForeground(false)
        repository.close()
    }

    @Test fun legacyPlaintextMarkerIsNeverWrittenToV2Storage() = runTest(dispatcher) {
        val remote = FakeRemote().apply {
            messagePages[ROOM] = ChatV2MessagesPageDto(
                roomId = ROOM,
                highWatermark = 1,
                messages = listOf(ChatV2MessageDto(
                    id = 77,
                    sequence = 1,
                    revision = 1,
                    senderId = 2,
                    contentKind = "text",
                    envelope = null,
                    legacyPlaintext = true,
                    createdAt = "2026-10-07T00:00:00Z",
                )),
            )
        }
        val repository = repository(remote, FakeE2eeEngine())
        repository.syncRoom(ROOM)
        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, "77"))
        repository.close()
    }

    @Test fun hiddenAndGlobalTombstonesDominateLaterStaleHistory() = runTest(dispatcher) {
        val hidden = serverMessage(id = 80, sequence = 1, revision = 1)
        val global = serverMessage(id = 90, sequence = 2, revision = 1)
        val remote = FakeRemote().apply {
            messagePages[ROOM] = ChatV2MessagesPageDto(ROOM, 2, listOf(global, hidden))
            changes[ROOM] = ChatV2ChangesPageDto(
                roomId = ROOM,
                highWatermark = 2,
                changes = listOf(
                    ChatV2ChangeDto(1, "message_hidden", messageId = 80, messageRevision = 1, actorId = ACCOUNT, createdAt = "2026-10-07T00:00:00Z"),
                    ChatV2ChangeDto(2, "message_tombstoned", messageId = 90, messageRevision = 2, actorId = 2, createdAt = "2026-10-07T00:00:01Z"),
                ),
                nextAfterSequence = 2,
            )
        }
        val repository = repository(remote, FakeE2eeEngine())
        repository.syncRoom(ROOM)
        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, "80"))
        val tombstone = database.chatV2().message(ENV, ACCOUNT, ROOM, "90")
        assertEquals(true, tombstone?.isTombstone)
        assertNull(tombstone?.opaqueEnvelope)
        assertEquals(2, tombstone?.revision)

        remote.messagePages[ROOM] = ChatV2MessagesPageDto(
            ROOM,
            3,
            listOf(serverMessage(id = 80, sequence = 3, revision = 3), global),
        )
        repository.syncRoom(ROOM) // hidden-for-viewer remains hidden even after a higher server revision.
        assertNull(database.chatV2().message(ENV, ACCOUNT, ROOM, "80"))
        assertEquals(true, database.chatV2().message(ENV, ACCOUNT, ROOM, "90")?.isTombstone)
        assertEquals(2, database.chatV2().message(ENV, ACCOUNT, ROOM, "90")?.revision)
        repository.close()
    }

    /**
     * Lets Room's real executor finish while only draining work at the current
     * virtual instant. Advancing virtual time here would expire the deliberately
     * pending WebSocket ACK and invalidate the handoff scenario under test.
     */
    private fun kotlinx.coroutines.test.TestScope.runCurrentUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        val deadlineNanos = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            assertTrue(System.nanoTime() < deadlineNanos, description)
            runCurrent()
            Thread.yield()
        }
        runCurrent()
    }

    private fun repository(
        remote: FakeRemote,
        engine: E2eeEngine,
        mutationInterlock: suspend () -> Unit = {},
        webSocketClient: ChatWebSocketClient? = null,
    ) = ChatV2Repository(
        dao = database.chatV2(),
        remote = remote,
        sessions = sessions,
        environment = ApiEnvironment(ENV),
        e2ee = engine,
        lifetime = lifetime,
        webSocketClient = webSocketClient,
        nowMillis = { clock },
        operationId = { OPERATION },
        mutationInterlock = mutationInterlock,
    )

    private fun localMessage(account: Long) = ChatMessageEntity(
        ENV, account, ROOM, OPERATION, OPERATION, 0, 0, account, "text", "{\"ciphertext\":\"x\"}",
        null, false, null, null, ChatStorageStates.PENDING, clock,
    )

    private fun localOperation(account: Long) = ChatOutboxEntity(
        ENV, account, ROOM, OPERATION, "send_message", "text", "{\"ciphertext\":\"x\"}", null,
        null, null, null, 0, ChatStorageStates.OUTBOX_PENDING, 0, clock, null, clock, clock,
    )

    private fun parseEnvelope(value: String?) = requireNotNull(value).let {
        ApiJson.parseToJsonElement(it) as JsonObject
    }

    private class MemorySessionStorage(var value: String?) : SessionStorage {
        override fun getSession(sessionKey: String) = value
        override fun saveSession(credentials: String, sessionKey: String) { value = credentials }
        override fun clearSession(): Boolean { value = null; return true }
        override fun clearOnReinstall(key: String) = Unit
    }

    private class TestSocketConnection : SocketConnection {
        val incoming = Channel<String>(16)
        val sent = Channel<String>(16)
        override suspend fun receive(): String? = incoming.receiveCatching().getOrNull()
        override suspend fun send(text: String) { sent.send(text) }
        override suspend fun close() { incoming.close() }
        override suspend fun closeCode(): Int? = null
    }

    private class FakeE2eeEngine : E2eeEngine {
        override val availability = E2eeEngineAvailability.Ready("test-only")
        val contexts = mutableListOf<E2eeSealContext>()
        override suspend fun sealTextAndPersist(
            context: E2eeSealContext,
            plaintext: String,
            persistExactEnvelope: suspend (OpaqueE2eeEnvelope) -> Unit,
        ): Result<Unit> = runCatching {
            contexts += context
            val envelope = buildJsonObject {
                put("test_ciphertext", "opaque")
                put("bound_operation_id", context.operationId)
                put("bound_membership_epoch", context.membershipEpoch)
            }
            persistExactEnvelope(OpaqueE2eeEnvelope("test-only", envelope.toString()))
        }
    }

    private class FakeRemote : ChatV2Remote {
        val sent = mutableListOf<ChatV2SendMessageRequest>()
        val sendResults = mutableListOf<NetworkAPIResult<ChatV2OperationResultDto?>>()
        val changes = mutableMapOf<String, ChatV2ChangesPageDto>()
        val messagePages = mutableMapOf<String, ChatV2MessagesPageDto>()
        var requiresDeviceSession = false
        var encryptedWritesEnabled = true
        var capabilityCalls = 0
        var messageFailure: ApiError? = null
        var pauseNextChangesAtSnapshot: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
        var notifyChangesAfter: Pair<Long, CompletableDeferred<Unit>>? = null
        val changeRequestCursors = mutableListOf<Long>()

        override suspend fun capabilities(): NetworkAPIResult<ChatV2CapabilitiesDto?> {
            capabilityCalls += 1
            return NetworkAPIResult.Success(ChatV2CapabilitiesDto(
                2,
                encryptedWritesEnabled = encryptedWritesEnabled,
                protocolStatus = "ready",
                maxMessageBytes = 4_096,
                maxEnvelopeBytes = 65_536,
                supportedKinds = listOf("text"),
                requiresDeviceSession = requiresDeviceSession,
            ))
        }

        override suspend fun messages(roomId: String, beforeSequence: Long?, limit: Int): NetworkAPIResult<ChatV2MessagesPageDto?> =
            messageFailure?.let { NetworkAPIResult.Error(it) }
                ?: NetworkAPIResult.Success(messagePages[roomId] ?: ChatV2MessagesPageDto(roomId, 0, emptyList()))

        override suspend fun changes(roomId: String, afterSequence: Long, limit: Int): NetworkAPIResult<ChatV2ChangesPageDto?> {
            changeRequestCursors += afterSequence
            notifyChangesAfter?.takeIf { it.first == afterSequence }?.second?.complete(Unit)
            val snapshot = changes[roomId] ?: ChatV2ChangesPageDto(roomId, afterSequence, nextAfterSequence = afterSequence)
            pauseNextChangesAtSnapshot?.also { pauseNextChangesAtSnapshot = null }?.let { (entered, release) ->
                entered.complete(Unit)
                release.await()
            }
            return NetworkAPIResult.Success(snapshot)
        }

        override suspend fun sendMessage(roomId: String, request: ChatV2SendMessageRequest): NetworkAPIResult<ChatV2OperationResultDto?> {
            sent += request
            return if (sendResults.isEmpty()) NetworkAPIResult.Success(operationResult(request.operationId)) else sendResults.removeAt(0)
        }

        override suspend fun operation(roomId: String, request: ChatV2OperationRequest): NetworkAPIResult<ChatV2OperationResultDto?> =
            error("Not used")

        override suspend fun receipts(roomId: String, request: ChatV2ReceiptRequest): NetworkAPIResult<ChatV2ReceiptResultDto?> =
            error("Not used")
    }

    companion object {
        private const val ENV = "https://example.test/"
        private const val ACCOUNT = 1L
        private const val ROOM = "123e4567-e89b-12d3-a456-426614174000"
        private const val ROOM_2 = "123e4567-e89b-12d3-a456-426614174010"
        private const val ROOM_3 = "123e4567-e89b-12d3-a456-426614174020"
        private const val OPERATION = "123e4567-e89b-42d3-a456-426614174001"

        private fun operationResult(operationId: String) = ChatV2OperationResultDto(
            operationId = operationId,
            changeSequence = 1,
            message = ChatV2MessageDto(
                id = 9,
                sequence = 1,
                revision = 1,
                senderId = ACCOUNT,
                contentKind = "text",
                envelope = buildJsonObject { put("test_ciphertext", "opaque") },
                createdAt = "2026-10-07T00:00:00Z",
            ),
        )

        private fun serverMessage(id: Long, sequence: Long, revision: Long) = ChatV2MessageDto(
            id = id,
            sequence = sequence,
            revision = revision,
            senderId = 2,
            contentKind = "text",
            envelope = buildJsonObject { put("test_ciphertext", "stale") },
            createdAt = "2026-10-07T00:00:00Z",
        )

        private fun change(
            sequence: Long,
            actorId: Long? = null,
            payload: JsonObject = JsonObject(emptyMap()),
        ) = ChatV2ChangeDto(
            sequence = sequence,
            kind = "metadata",
            actorId = actorId,
            createdAt = "2026-10-07T00:00:00Z",
            payload = payload,
        )
    }
}
