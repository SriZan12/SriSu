package com.srisu.srisu.features.chat.v2.data

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.ApiJson
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionChangedException
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import com.srisu.srisu.core.session.accessHasDeviceSession
import com.srisu.srisu.features.chat.v2.crypto.E2eeEngine
import com.srisu.srisu.features.chat.v2.crypto.E2eeEngineAvailability
import com.srisu.srisu.features.chat.v2.crypto.E2eeSealContext
import com.srisu.srisu.features.chat.v2.data.local.ChatCheckpointEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatConversationEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatMessageEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatOutboxEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatReceiptEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatStorageStates
import com.srisu.srisu.features.chat.v2.data.local.ChatTombstoneEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatV2Dao
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2CapabilitiesDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2ChangeDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2MessageDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2OperationResultDto
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2Remote
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2Routes
import com.srisu.srisu.features.chat.v2.data.remote.ChatV2SendMessageRequest
import com.srisu.srisu.features.chat.v2.domain.ChatV2RoomState
import com.srisu.srisu.features.chat.v2.domain.ChatV2Scope
import com.srisu.srisu.features.chat.v2.domain.SecureChatAvailability
import com.srisu.srisu.features.chat.v2.domain.toDomain
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketClient
import com.srisu.srisu.features.chat.data.remote.websocket.ChatCommandFailure
import com.srisu.srisu.features.chat.data.remote.websocket.ChatWebSocketEvent
import com.srisu.srisu.core.data.remote.SocketState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Offline-first chat-v2 coordinator. Room is the observable source of truth;
 * HTTP and socket hints only reconcile data into Room.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class ChatV2Repository(
    private val dao: ChatV2Dao,
    private val remote: ChatV2Remote,
    private val sessions: SessionCoordinator,
    private val environment: ApiEnvironment,
    private val e2ee: E2eeEngine,
    lifetime: ApplicationLifetime,
    private val webSocketClient: ChatWebSocketClient? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val operationId: () -> String = { Uuid.random().toString() },
    /** Test interlock; production uses the no-op default. Runs after session validation under the DB mutation lock. */
    private val mutationInterlock: suspend () -> Unit = {},
) {
    private val scope = CoroutineScope(lifetime.scope.coroutineContext + SupervisorJob(lifetime.scope.coroutineContext[Job]))
    private val environmentKey = environment.baseUrl
    private val activeRoom = MutableStateFlow<ScopedRoom?>(null)
    private val gate = MutableStateFlow<GateSnapshot?>(null)
    private val activity = MutableStateFlow<Map<RoomKey, RoomActivity>>(emptyMap())
    private val revokedRooms = MutableStateFlow<Set<RoomKey>>(emptySet())
    private val revokedAccountScopes = MutableStateFlow<Set<AccountKey>>(emptySet())
    private val syncHints = Channel<ScopedRoom>(Channel.CONFLATED)
    private val syncMutex = Mutex()
    private val outboxMutex = Mutex()
    private val capabilitiesMutex = Mutex()
    private val databaseMutationMutex = Mutex()
    private val subscriptionMutex = Mutex()
    private var subscribedRoom: ScopedRoom? = null

    init {
        var previousStamp = sessions.stamp()
        scope.launch {
            sessions.state.collect { stamp ->
                if (stamp != previousStamp) {
                    val previousAccount = previousStamp.accountId
                    if (previousAccount != null && previousAccount != stamp.accountId) {
                        databaseMutationMutex.withLock {
                            dao.clearAccount(environmentKey, previousAccount)
                        }
                    }
                    previousStamp = stamp
                    activeRoom.value = null
                    subscribedRoom = null
                    gate.value = null
                    activity.value = emptyMap()
                    revokedRooms.value = emptySet()
                    revokedAccountScopes.value = emptySet()
                }
            }
        }
        scope.launch {
            for (hint in syncHints) {
                if (hint.stamp == sessions.stamp()) syncRoom(hint.roomId)
            }
        }
        webSocketClient?.let { socket ->
            scope.launch {
                socket.events.collect { scoped ->
                    when (val event = scoped.event) {
                        ChatWebSocketEvent.Connected -> activeRoom.value?.let { subscribeActiveRoom(it) }
                        is ChatWebSocketEvent.Disconnected -> subscribedRoom = null
                        is ChatWebSocketEvent.ChatV2Changed -> {
                            val active = activeRoom.value ?: return@collect
                            if (scoped.session == sessions.stamp() && active.stamp == scoped.session && active.roomId == event.roomId) {
                                val accountId = active.stamp.accountId ?: return@collect
                                val cursor = dao.checkpoint(environmentKey, accountId, active.roomId)?.changeSequence ?: 0
                                if (event.highWatermark > cursor) syncHints.trySend(active)
                            }
                        }
                        is ChatWebSocketEvent.AccessRevoked -> handleAccessRevoked(scoped.session, event.roomId)
                        is ChatWebSocketEvent.RelationshipChanged -> handleRelationshipChanged(scoped.session, event)
                        else -> Unit
                    }
                }
            }
        }
        scope.launch {
            lifetime.foreground.collectLatest { foreground ->
                if (!foreground) return@collectLatest
                // Capabilities are an authorization/rollout gate, not a forever cache.
                // Refresh once per foreground transition before any queued write drains.
                gate.value = null
                activeRoom.value?.takeIf { it.stamp == sessions.stamp() }?.let { syncHints.trySend(it) }
                drainOutbox(maxOperations = MAX_OUTBOX_PER_PASS)
            }
        }
    }

    fun observeRoom(roomId: String): Flow<ChatV2RoomState> {
        val normalizedRoomId = ChatV2Routes.validatedRoomId(roomId)
        return sessions.state.flatMapLatest { stamp ->
            val accountId = stamp.accountId ?: return@flatMapLatest flowOf(
                ChatV2RoomState(
                    roomId = normalizedRoomId,
                    availability = SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.SIGN_IN_REQUIRED),
                )
            )
            val key = RoomKey(environmentKey, accountId, normalizedRoomId, stamp.generation)
            val revocations = combine(revokedRooms, revokedAccountScopes) { rooms, accounts -> rooms to accounts }
            combine(
                dao.observeMessages(environmentKey, accountId, normalizedRoomId),
                dao.observeOutbox(environmentKey, accountId, normalizedRoomId),
                gate,
                activity,
                revocations,
            ) { messages, outbox, currentGate, activities, currentRevocations ->
                val (revoked, revokedAccounts) = currentRevocations
                val roomActivity = activities[key] ?: RoomActivity()
                ChatV2RoomState(
                    roomId = normalizedRoomId,
                    messages = messages.map { it.toDomain() },
                    availability = if (key in revoked || AccountKey(environmentKey, accountId, stamp.generation) in revokedAccounts)
                        SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.ACCESS_REVOKED)
                    else availability(stamp, currentGate),
                    syncing = roomActivity.syncing,
                    queuedOperationCount = outbox.count { it.state != ChatStorageStates.OUTBOX_ACKNOWLEDGED },
                    errorMessage = roomActivity.error,
                )
            }
        }
    }

    fun activateRoom(roomId: String) {
        val normalized = ChatV2Routes.validatedRoomId(roomId)
        val stamp = sessions.stamp()
        if (stamp.accountId == null) return
        val scoped = ScopedRoom(stamp, normalized)
        activeRoom.value = scoped
        if (RoomKey(environmentKey, stamp.accountId, normalized, stamp.generation) in revokedRooms.value ||
            AccountKey(environmentKey, stamp.accountId, stamp.generation) in revokedAccountScopes.value) return
        webSocketClient?.connect()
        if (webSocketClient?.connectionState?.value == SocketState.Connected) {
            scope.launch { subscribeActiveRoom(scoped) }
        }
        scope.launch {
            ensureCapabilities(stamp)
            // A room-authorizing read still runs when encrypted writes/crypto are
            // gated off, so a missed revoke cannot leave cached private state.
            syncHints.trySend(scoped)
            if (availability(stamp, gate.value) is SecureChatAvailability.Ready) {
                drainOutbox(MAX_OUTBOX_PER_PASS)
            }
        }
    }

    fun deactivateRoom(roomId: String) {
        val normalized = ChatV2Routes.validatedRoomId(roomId)
        val removed = activeRoom.value?.takeIf { it.roomId == normalized }
        activeRoom.update { if (it?.roomId == normalized) null else it }
        if (removed != null) scope.launch { unsubscribeRoom(removed) }
    }

    /** A socket notification is only a reconciliation hint; socket payloads never mutate Room. */
    fun onSocketHint(roomId: String) {
        val normalized = ChatV2Routes.validatedRoomId(roomId)
        val stamp = sessions.stamp()
        if (stamp.accountId != null) syncHints.trySend(ScopedRoom(stamp, normalized))
    }

    fun retry(roomId: String) {
        val stamp = sessions.stamp()
        gate.value = null
        clearRoomError(stamp, ChatV2Routes.validatedRoomId(roomId))
        activateRoom(roomId)
    }

    /** Explicit user retry for terminal outbox failures; identity and ciphertext stay unchanged. */
    fun retryFailedMessages(roomId: String) {
        scope.launch { retryFailedMessagesNow(roomId) }
    }

    internal suspend fun retryFailedMessagesNow(roomId: String): Int {
        val normalized = ChatV2Routes.validatedRoomId(roomId)
        val stamp = sessions.stamp()
        val accountId = stamp.accountId ?: return 0
        sessions.ensureCurrent(stamp)
        val reset = mutateFor(stamp, normalized) {
            dao.retryFailedOperations(environmentKey, accountId, normalized, nowMillis())
        }
        if (reset > 0) drainOutbox(MAX_OUTBOX_PER_PASS)
        return reset
    }

    suspend fun queueText(roomId: String, plaintext: String, replyToId: Long? = null): Result<String> = runCatching {
        require(plaintext.isNotBlank()) { "A message cannot be blank." }
        val normalized = ChatV2Routes.validatedRoomId(roomId)
        val stamp = sessions.stamp()
        val accountId = requireNotNull(stamp.accountId) { "Sign in to send messages." }
        ensureCapabilities(stamp)
        val capabilities = (gate.value as? GateSnapshot.Available)?.takeIf { it.stamp == stamp }?.value
            ?: error("Secure messaging capabilities are unavailable.")
        check(availability(stamp, gate.value) is SecureChatAvailability.Ready) { "Secure messaging is unavailable." }
        check("text" in capabilities.supportedKinds) { "Secure text messages are not supported by the server." }
        capabilities.maxMessageBytes?.let { limit ->
            require(plaintext.encodeToByteArray().size.toLong() <= limit) { "Message is too large." }
        }
        val id = operationId()
        require(id.matches(UUID_PATTERN)) { "Operation identifier must be a UUID." }
        val now = nowMillis()
        val conversation = dao.conversation(environmentKey, accountId, normalized)
        val chatScope = ChatV2Scope(environmentKey, accountId, normalized, stamp.generation)
        val sealContext = E2eeSealContext(
            scope = chatScope,
            operationId = id,
            conversationId = conversation?.relationshipId ?: normalized,
            membershipEpoch = conversation?.membershipEpoch ?: 0,
            contentKind = "text",
            replyToId = replyToId?.toString(),
            attachmentIds = emptyList(),
        )
        e2ee.sealTextAndPersist(sealContext, plaintext) { encrypted ->
            val envelopeLimit = requireNotNull(capabilities.maxEnvelopeBytes) { "The server did not provide an encrypted-envelope limit." }
            require(encrypted.wireJson.encodeToByteArray().size.toLong() <= envelopeLimit) { "Encrypted message is too large." }
            parseOpaqueEnvelope(encrypted.wireJson) // Validate structure without rewriting the adapter-owned bytes.
            val storedEnvelope = encrypted.wireJson
            sessions.ensureCurrent(stamp)
            val message = ChatMessageEntity(
                environmentKey = environmentKey,
                accountId = accountId,
                roomId = normalized,
                messageId = id,
                operationId = id,
                sequence = 0,
                revision = 0,
                senderId = accountId,
                contentKind = "text",
                opaqueEnvelope = storedEnvelope,
                replyToId = replyToId?.toString(),
                isTombstone = false,
                createdAt = null,
                editedAt = null,
                deliveryState = ChatStorageStates.PENDING,
                updatedAtMillis = now,
            )
            val operation = ChatOutboxEntity(
                environmentKey = environmentKey,
                accountId = accountId,
                roomId = normalized,
                operationId = id,
                operationKind = OP_SEND_MESSAGE,
                contentKind = "text",
                opaqueEnvelope = storedEnvelope,
                messageId = null,
                expectedRevision = null,
                replyToId = replyToId?.toString(),
                attachmentIdsJson = null,
                membershipEpoch = sealContext.membershipEpoch,
                state = ChatStorageStates.OUTBOX_PENDING,
                attemptCount = 0,
                nextAttemptAtMillis = now,
                lastErrorCode = null,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            mutateFor(stamp, normalized) { dao.enqueue(message, operation) }
        }.getOrThrow()
        scope.launch { drainOutbox(MAX_OUTBOX_PER_PASS) }
        id
    }

    internal suspend fun ensureCapabilities(stamp: SessionStamp = sessions.stamp()) = capabilitiesMutex.withLock {
        if (stamp.accountId == null) return
        if (gate.value?.stamp == stamp && gate.value !is GateSnapshot.Loading) return
        gate.value = GateSnapshot.Loading(stamp)
        when (val result = remote.capabilities()) {
            is NetworkAPIResult.Success -> {
                sessions.ensureCurrent(stamp)
                val value = result.response
                gate.value = if (value == null) GateSnapshot.Failed(stamp, "The server returned empty chat capabilities.")
                else GateSnapshot.Available(stamp, value)
            }
            is NetworkAPIResult.Error -> {
                sessions.ensureCurrent(stamp)
                gate.value = GateSnapshot.Failed(stamp, result.failure.message)
            }
        }
    }

    internal suspend fun drainOutbox(maxOperations: Int) = outboxMutex.withLock {
        require(maxOperations in 1..MAX_OUTBOX_PER_PASS)
        val stamp = sessions.stamp()
        val accountId = stamp.accountId ?: return@withLock
        ensureCapabilities(stamp)
        if (availability(stamp, gate.value) !is SecureChatAvailability.Ready) return@withLock
        val now = nowMillis()
        mutateFor(stamp) { dao.recoverInterruptedOperations(environmentKey, accountId, now) }
        val ready = dao.readyOperations(environmentKey, accountId, now, maxOperations)
        for (candidate in ready) {
            sessions.ensureCurrent(stamp)
            if (RoomKey(environmentKey, accountId, candidate.roomId, stamp.generation) in revokedRooms.value ||
                AccountKey(environmentKey, accountId, stamp.generation) in revokedAccountScopes.value) continue
            val claimed = mutateFor(stamp, candidate.roomId) { dao.claim(candidate, nowMillis()) } ?: continue
            sendClaimed(stamp, claimed)
        }
    }

    private suspend fun sendClaimed(stamp: SessionStamp, operation: ChatOutboxEntity) {
        if (operation.operationKind != OP_SEND_MESSAGE || operation.contentKind != "text" || operation.opaqueEnvelope == null) {
            mutateFor(stamp, operation.roomId) { dao.fail(operation, "unsupported_operation", nowMillis()) }
            return
        }
        val request = ChatV2SendMessageRequest(
            operationId = operation.operationId,
            contentKind = operation.contentKind,
            envelope = parseOpaqueEnvelope(operation.opaqueEnvelope),
            replyToId = operation.replyToId?.toLongOrNull(),
            attachmentIds = emptyList(),
        )
        when (val result = remote.sendMessage(operation.roomId, request)) {
            is NetworkAPIResult.Success -> {
                sessions.ensureCurrent(stamp)
                val response = result.response
                if (response == null || response.operationId != operation.operationId) {
                    handleSendFailure(stamp, operation, "invalid_ack", retryable = false)
                    return
                }
                acknowledge(stamp, operation, response)
            }
            is NetworkAPIResult.Error -> {
                sessions.ensureCurrent(stamp)
                if (result.failure.kind.isAuthoritativeRoomDenial()) {
                    purgeRevokedRoom(stamp, operation.roomId, requireActive = false)
                    return
                }
                handleSendFailure(stamp, operation, result.failure.code, result.failure.retryable || result.failure.kind in RETRYABLE_ERRORS)
            }
        }
    }

    private suspend fun acknowledge(stamp: SessionStamp, operation: ChatOutboxEntity, response: ChatV2OperationResultDto) {
        val deletionScope = when {
            response.message.hiddenForViewer -> "message_hidden"
            response.message.isTombstone -> "message_tombstoned"
            else -> null
        }
        val deletion = deletionScope?.let {
            ChatTombstoneEntity(
                environmentKey = operation.environmentKey,
                accountId = operation.accountId,
                roomId = operation.roomId,
                messageId = response.message.id.toString(),
                messageRevision = response.message.revision.toLong(),
                changeSequence = response.changeSequence ?: 0,
                deletionScope = it,
                updatedAtMillis = nowMillis(),
            )
        }
        val message = response.message.toEntity(
            environmentKey = operation.environmentKey,
            accountId = operation.accountId,
            roomId = operation.roomId,
            acknowledgedOperationId = operation.operationId,
            deliveryState = ChatStorageStates.SENT,
            now = nowMillis(),
        )
        if (message == null && deletion == null) {
            handleSendFailure(stamp, operation, "invalid_message_ack", retryable = false)
            return
        }
        mutateFor(stamp, operation.roomId) { dao.acknowledgeProjection(operation, message, deletion, nowMillis()) }
    }

    private suspend fun handleSendFailure(stamp: SessionStamp, operation: ChatOutboxEntity, code: String, retryable: Boolean) {
        val now = nowMillis()
        if (retryable && operation.attemptCount < MAX_ATTEMPTS) {
            val backoff = chatV2RetryDelayMillis(operation.operationId, operation.attemptCount)
            mutateFor(stamp, operation.roomId) { dao.retry(operation, now + backoff, code, now) }
        } else {
            mutateFor(stamp, operation.roomId) { dao.fail(operation, code, now) }
        }
    }

    internal suspend fun syncRoom(roomId: String) = syncMutex.withLock {
        val normalized = ChatV2Routes.validatedRoomId(roomId)
        val stamp = sessions.stamp()
        val accountId = stamp.accountId ?: return@withLock
        ensureCapabilities(stamp)
        val key = RoomKey(environmentKey, accountId, normalized, stamp.generation)
        activity.update { it + (key to RoomActivity(syncing = true)) }
        try {
            if (availability(stamp, gate.value) !is SecureChatAvailability.Ready) {
                validateRoomAccess(stamp, normalized)
                return@withLock
            }
            syncLatestMessages(stamp, normalized)
            syncChanges(stamp, normalized)
            clearRoomError(stamp, normalized)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (changed: SessionChangedException) {
            throw changed
        } catch (_: ChatV2AccessRevokedException) {
            // The authoritative denial already purged and deactivated the room.
        } catch (failure: Exception) {
            setRoomError(stamp, normalized, failure.message ?: "Secure chat could not be synchronized.")
        } finally {
            if (sessions.stamp() == stamp) activity.update { current ->
                val existing = current[key] ?: RoomActivity()
                current + (key to existing.copy(syncing = false))
            }
        }
    }

    /**
     * Membership validation is deliberately independent from the encrypted-write
     * capability gate. The response body is discarded; only an authoritative
     * room denial is allowed to purge local state.
     */
    private suspend fun validateRoomAccess(stamp: SessionStamp, roomId: String) {
        when (val result = remote.messages(roomId, limit = 1)) {
            is NetworkAPIResult.Success -> {
                sessions.ensureCurrent(stamp)
                result.response?.let { page ->
                    require(ChatV2Routes.validatedRoomId(page.roomId) == roomId) {
                        "The server returned a different conversation."
                    }
                }
            }
            is NetworkAPIResult.Error -> {
                if (result.failure.kind.isAuthoritativeRoomDenial()) {
                    handleAccessRevoked(stamp, roomId)
                    throw ChatV2AccessRevokedException()
                }
                throw ChatV2SyncException(result.failure.message)
            }
        }
    }

    private suspend fun syncLatestMessages(stamp: SessionStamp, roomId: String) {
        val accountId = requireNotNull(stamp.accountId)
        when (val result = remote.messages(roomId, limit = HISTORY_PAGE_SIZE)) {
            is NetworkAPIResult.Success -> {
                sessions.ensureCurrent(stamp)
                val page = requireNotNull(result.response) { "The server returned an empty message page." }
                require(ChatV2Routes.validatedRoomId(page.roomId) == roomId) { "The server returned a different conversation." }
                val entities = page.messages.asReversed().mapNotNull {
                    it.toEntity(environmentKey, accountId, roomId, deliveryState = ChatStorageStates.SENT, now = nowMillis())
                }
                val existing = dao.conversation(environmentKey, accountId, roomId)
                mutateFor(stamp, roomId) {
                    dao.mergeHistory(
                        entities,
                        (existing ?: emptyConversation(accountId, roomId)).copy(
                            latestMessageSequence = maxOf(existing?.latestMessageSequence ?: 0, page.highWatermark),
                            updatedAtMillis = nowMillis(),
                        ),
                    )
                }
            }
            is NetworkAPIResult.Error -> {
                if (result.failure.kind.isAuthoritativeRoomDenial()) {
                    handleAccessRevoked(stamp, roomId)
                    throw ChatV2AccessRevokedException()
                }
                throw ChatV2SyncException(result.failure.message)
            }
        }
    }

    private suspend fun syncChanges(stamp: SessionStamp, roomId: String) {
        val accountId = requireNotNull(stamp.accountId)
        var after = dao.checkpoint(environmentKey, accountId, roomId)?.changeSequence ?: 0
        repeat(MAX_CHANGE_PAGES_PER_PASS) {
            when (val result = remote.changes(roomId, after, CHANGE_PAGE_SIZE)) {
                is NetworkAPIResult.Error -> {
                    if (result.failure.kind.isAuthoritativeRoomDenial()) {
                        handleAccessRevoked(stamp, roomId)
                        throw ChatV2AccessRevokedException()
                    }
                    throw ChatV2SyncException(result.failure.message)
                }
                is NetworkAPIResult.Success -> {
                    sessions.ensureCurrent(stamp)
                    val page = requireNotNull(result.response) { "The server returned an empty change page." }
                    require(ChatV2Routes.validatedRoomId(page.roomId) == roomId) { "The server returned a different conversation." }
                    val previousAfter = after
                    val ordered = validateAndOrderChanges(page.changes, after)
                    val next = page.nextAfterSequence ?: ordered.lastOrNull()?.sequence ?: after
                    require(next >= after) { "The server returned a regressing change cursor." }
                    require(next >= (ordered.lastOrNull()?.sequence ?: after)) { "The server skipped a returned change in its cursor." }
                    require(next <= page.highWatermark) { "The server returned a cursor beyond its high watermark." }
                    val messages = ordered.mapNotNull { change ->
                        change.message?.toEntity(environmentKey, accountId, roomId, deliveryState = ChatStorageStates.SENT, now = nowMillis())
                    }
                    val tombstones = ordered.mapNotNull { it.toTombstone(environmentKey, accountId, roomId, nowMillis()) }
                    val receipts = ordered.flatMap { it.toReceipts(environmentKey, accountId, roomId, nowMillis()) }
                    val existing = dao.conversation(environmentKey, accountId, roomId)
                    mutateFor(stamp, roomId) {
                        dao.applyDelta(
                            messages = messages,
                            tombstones = tombstones,
                            receipts = receipts,
                            checkpoint = ChatCheckpointEntity(environmentKey, accountId, roomId, next, nowMillis()),
                            conversation = (existing ?: emptyConversation(accountId, roomId)).copy(
                                latestChangeSequence = maxOf(existing?.latestChangeSequence ?: 0, next),
                                updatedAtMillis = nowMillis(),
                            ),
                        )
                    }
                    after = next
                    if (!page.hasMore) return
                    if (next == previousAfter && ordered.isEmpty())
                        throw ChatV2SyncException("The server returned a non-advancing change cursor.")
                }
            }
        }
        syncHints.trySend(ScopedRoom(stamp, roomId))
    }

    private fun validateAndOrderChanges(changes: List<ChatV2ChangeDto>, after: Long): List<ChatV2ChangeDto> {
        changes.zipWithNext().forEach { (first, second) ->
            if (second.sequence <= first.sequence) throw ChatV2SyncException("Chat changes are not strictly ordered.")
        }
        return changes.filter { it.sequence > after }
    }

    private fun availability(stamp: SessionStamp, snapshot: GateSnapshot?): SecureChatAvailability {
        if (stamp.accountId == null) return SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.SIGN_IN_REQUIRED)
        if (snapshot == null || snapshot.stamp != stamp || snapshot is GateSnapshot.Loading) return SecureChatAvailability.Loading
        if (snapshot is GateSnapshot.Failed) return SecureChatAvailability.Error(snapshot.message)
        val capabilities = (snapshot as GateSnapshot.Available).value
        if (capabilities.apiVersion != 2) return SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.UNSUPPORTED_PROTOCOL)
        if (!capabilities.encryptedWritesEnabled) return SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.SERVER_DISABLED)
        if (capabilities.protocolStatus != "ready") return SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.CRYPTO_ADAPTER_REQUIRED)
        if (capabilities.requiresDeviceSession && !accessHasDeviceSession(sessions.currentSession()?.access))
            return SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.DEVICE_SESSION_REQUIRED)
        if (capabilities.maxEnvelopeBytes == null || capabilities.maxEnvelopeBytes <= 0)
            return SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.UNSUPPORTED_PROTOCOL)
        return when (val engine = e2ee.availability) {
            is E2eeEngineAvailability.Ready -> SecureChatAvailability.Ready(engine.protocolId)
            is E2eeEngineAvailability.Unavailable -> SecureChatAvailability.Unavailable(SecureChatAvailability.Reason.CRYPTO_ADAPTER_REQUIRED)
        }
    }

    private fun emptyConversation(accountId: Long, roomId: String) = ChatConversationEntity(
        environmentKey = environmentKey,
        accountId = accountId,
        roomId = roomId,
        relationshipId = null,
        membershipEpoch = 0,
        latestMessageSequence = 0,
        latestChangeSequence = 0,
        unreadCount = 0,
        updatedAtMillis = nowMillis(),
    )

    private fun setRoomError(stamp: SessionStamp, roomId: String, message: String) {
        val account = stamp.accountId ?: return
        val key = RoomKey(environmentKey, account, roomId, stamp.generation)
        activity.update { current -> current + (key to (current[key] ?: RoomActivity()).copy(error = message)) }
    }

    private fun clearRoomError(stamp: SessionStamp, roomId: String) {
        val account = stamp.accountId ?: return
        val key = RoomKey(environmentKey, account, roomId, stamp.generation)
        activity.update { current -> current + (key to (current[key] ?: RoomActivity()).copy(error = null)) }
    }

    private suspend fun subscribeActiveRoom(room: ScopedRoom) {
        val socket = webSocketClient ?: return
        var authoritativeDenial = false
        var catchUpAfterAcknowledgment = false
        subscriptionMutex.withLock {
            if (sessions.stamp() != room.stamp || activeRoom.value != room || subscribedRoom == room) return
            subscribedRoom?.let { previous -> runCatching { socket.unsubscribe(previous.roomId) } }
            subscribedRoom = null
            try {
                socket.subscribeRoomV2(room.roomId)
                val accountId = room.stamp.accountId
                val revoked = accountId != null &&
                    (RoomKey(environmentKey, accountId, room.roomId, room.stamp.generation) in revokedRooms.value ||
                        AccountKey(environmentKey, accountId, room.stamp.generation) in revokedAccountScopes.value)
                if (sessions.stamp() == room.stamp && activeRoom.value == room && !revoked) {
                    subscribedRoom = room
                    // Close the snapshot-to-subscription handoff: anything that
                    // committed before this ACK is recovered even if no later
                    // socket event occurs.
                    catchUpAfterAcknowledgment = true
                } else {
                    // The server accepted the subscription after this room was
                    // deactivated/switched/revoked. Explicitly undo it; local
                    // `subscribedRoom` was never set, so the queued owner cleanup
                    // cannot infer this server-side subscription.
                    runCatching { socket.unsubscribe(room.roomId) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ChatCommandFailure) {
                authoritativeDenial = failure.failure.kind.isAuthoritativeRoomDenial()
            } catch (_: Exception) {
                // One-shot HTTP catch-up remains available after transport failure.
            }
        }
        if (authoritativeDenial) handleAccessRevoked(room.stamp, room.roomId)
        if (catchUpAfterAcknowledgment) {
            room.stamp.accountId?.let { accountId ->
                val revoked = RoomKey(environmentKey, accountId, room.roomId, room.stamp.generation) in revokedRooms.value ||
                    AccountKey(environmentKey, accountId, room.stamp.generation) in revokedAccountScopes.value
                if (sessions.stamp() == room.stamp && activeRoom.value == room && !revoked) syncRoom(room.roomId)
            }
        }
    }

    private suspend fun unsubscribeRoom(room: ScopedRoom) {
        val socket = webSocketClient ?: return
        subscriptionMutex.withLock {
            if (subscribedRoom != room) return
            runCatching { socket.unsubscribe(room.roomId) }
            if (subscribedRoom == room) subscribedRoom = null
        }
    }

    internal suspend fun handleAccessRevoked(stamp: SessionStamp, roomId: String) {
        // User-group reauthorization can revoke a room that is cached but not
        // currently open/subscribed; the exact room identifier is authoritative.
        purgeRevokedRoom(stamp, roomId, requireActive = false)
    }

    internal suspend fun handleRelationshipChanged(stamp: SessionStamp, event: ChatWebSocketEvent.RelationshipChanged) {
        val accountId = stamp.accountId ?: return
        if (stamp != sessions.stamp()) return
        val accountKey = AccountKey(environmentKey, accountId, stamp.generation)
        if (event.status == "ACCEPTED") {
            revokedAccountScopes.update { it - accountKey }
            return
        }
        if (event.status !in setOf("ENDED", "BREAK-UP")) return
        event.roomId?.let {
            purgeRevokedRoom(stamp, it, requireActive = false)
            return
        }

        // Older servers did not identify the ended room. A relationship end is
        // rare and privacy-sensitive, so fail closed and erase this account's
        // entire v2 cache rather than retaining an unidentifiable private room.
        val active = activeRoom.value?.takeIf { it.stamp == stamp }
        databaseMutationMutex.withLock {
            sessions.ensureCurrent(stamp)
            revokedAccountScopes.update { it + accountKey }
            dao.clearAccount(environmentKey, accountId)
        }
        if (active != null) {
            activeRoom.update { if (it == active) null else it }
            val roomKey = RoomKey(environmentKey, accountId, active.roomId, stamp.generation)
            activity.update { current ->
                current + (roomKey to RoomActivity(syncing = false, error = "You no longer have access to this conversation."))
            }
            unsubscribeRoom(active)
        }
    }

    private suspend fun purgeRevokedRoom(stamp: SessionStamp, roomId: String, requireActive: Boolean) {
        val normalized = runCatching { ChatV2Routes.validatedRoomId(roomId) }.getOrNull() ?: return
        val accountId = stamp.accountId ?: return
        val active = activeRoom.value?.takeIf { it.stamp == stamp && it.roomId == normalized }
        if (stamp != sessions.stamp() || (requireActive && active == null)) return
        val key = RoomKey(environmentKey, accountId, normalized, stamp.generation)
        databaseMutationMutex.withLock {
            sessions.ensureCurrent(stamp)
            revokedRooms.update { it + key }
            dao.clearRoom(environmentKey, accountId, normalized)
        }
        if (active != null) activeRoom.update { if (it == active) null else it }
        activity.update { current ->
            current + (key to RoomActivity(syncing = false, error = "You no longer have access to this conversation."))
        }
        active?.let { unsubscribeRoom(it) }
    }

    /**
     * Serializes every account-scoped write with logout/account-switch erasure.
     * If identity changes while Room is suspended, the old scope is cleared
     * before releasing the lock, so a stale response cannot repopulate it.
     */
    private suspend fun <T> mutateFor(
        stamp: SessionStamp,
        roomId: String? = null,
        block: suspend () -> T,
    ): T =
        databaseMutationMutex.withLock {
            sessions.ensureCurrent(stamp)
            if (roomId != null && stamp.accountId != null &&
                RoomKey(environmentKey, stamp.accountId, roomId, stamp.generation) in revokedRooms.value) {
                throw ChatV2AccessRevokedException()
            }
            if (stamp.accountId != null &&
                AccountKey(environmentKey, stamp.accountId, stamp.generation) in revokedAccountScopes.value) {
                throw ChatV2AccessRevokedException()
            }
            mutationInterlock()
            val result = block()
            try {
                sessions.ensureCurrent(stamp)
            } catch (changed: SessionChangedException) {
                stamp.accountId?.let { dao.clearAccount(environmentKey, it) }
                throw changed
            }
            result
        }

    fun close() {
        scope.coroutineContext[Job]?.cancel()
    }

    private data class ScopedRoom(val stamp: SessionStamp, val roomId: String)
    private data class RoomKey(val environment: String, val accountId: Long, val roomId: String, val generation: Long)
    private data class AccountKey(val environment: String, val accountId: Long, val generation: Long)
    private data class RoomActivity(val syncing: Boolean = false, val error: String? = null)

    private sealed interface GateSnapshot {
        val stamp: SessionStamp
        data class Loading(override val stamp: SessionStamp) : GateSnapshot
        data class Available(override val stamp: SessionStamp, val value: ChatV2CapabilitiesDto) : GateSnapshot
        data class Failed(override val stamp: SessionStamp, val message: String) : GateSnapshot
    }

    companion object {
        private const val OP_SEND_MESSAGE = "send_message"
        private const val HISTORY_PAGE_SIZE = 50
        private const val CHANGE_PAGE_SIZE = 100
        private const val MAX_CHANGE_PAGES_PER_PASS = 4
        private const val MAX_OUTBOX_PER_PASS = 8
        private const val MAX_ATTEMPTS = 5
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        private val RETRYABLE_ERRORS = setOf(
            NetworkAPIResult.ErrorType.NETWORK,
            NetworkAPIResult.ErrorType.TIMEOUT,
            NetworkAPIResult.ErrorType.SERVER,
            NetworkAPIResult.ErrorType.RATE_LIMITED,
        )
    }
}

/** Stable per-operation ±20% jitter; retrying never changes the operation id or envelope. */
internal fun chatV2RetryDelayMillis(operationId: String, attemptCount: Int): Long {
    val exponent = (attemptCount - 1).coerceIn(0, 5)
    val base = (500L * (1L shl exponent)).coerceAtMost(30_000L)
    var hash = 0x811c9dc5u
    operationId.encodeToByteArray().forEach { byte ->
        hash = (hash xor byte.toUByte().toUInt()) * 0x01000193u
    }
    val spread = (base / 5).coerceAtLeast(1)
    val offset = (hash.toLong() % (spread * 2 + 1)) - spread
    return (base + offset).coerceIn(1, 30_000)
}

private fun parseOpaqueEnvelope(value: String): JsonObject =
    ApiJson.parseToJsonElement(value) as? JsonObject ?: throw IllegalArgumentException("Protocol envelope must be a JSON object.")

private fun ChatV2MessageDto.toEntity(
    environmentKey: String,
    accountId: Long,
    roomId: String,
    acknowledgedOperationId: String? = null,
    deliveryState: String,
    now: Long,
): ChatMessageEntity? {
    if (hiddenForViewer || legacyPlaintext) return null
    val wireEnvelope = envelope?.toString()
    if (!isTombstone && wireEnvelope == null) return null
    return ChatMessageEntity(
        environmentKey = environmentKey,
        accountId = accountId,
        roomId = roomId,
        messageId = id.toString(),
        operationId = acknowledgedOperationId ?: operationId,
        sequence = sequence ?: 0,
        revision = revision,
        senderId = senderId,
        contentKind = contentKind,
        opaqueEnvelope = wireEnvelope,
        replyToId = replyToId?.toString(),
        isTombstone = isTombstone,
        createdAt = createdAt,
        editedAt = editedAt,
        deliveryState = deliveryState,
        updatedAtMillis = now,
    )
}

private fun ChatV2ChangeDto.toTombstone(
    environmentKey: String,
    accountId: Long,
    roomId: String,
    now: Long,
): ChatTombstoneEntity? {
    val id = messageId ?: return null
    if (kind !in setOf(
            "message_hidden",
            "message_tombstoned",
            "message_deleted",
            "delete_for_me",
            "delete_for_everyone",
        ) && message?.isTombstone != true) return null
    return ChatTombstoneEntity(
        environmentKey = environmentKey,
        accountId = accountId,
        roomId = roomId,
        messageId = id.toString(),
        messageRevision = messageRevision ?: message?.revision ?: 0,
        changeSequence = sequence,
        deletionScope = kind,
        updatedAtMillis = now,
    )
}

private fun ChatV2ChangeDto.toReceipts(
    environmentKey: String,
    accountId: Long,
    roomId: String,
    now: Long,
): List<ChatReceiptEntity> {
    val receiptActor = actorId ?: return emptyList()
    return listOfNotNull(
        (payload["delivered_through"] as? JsonPrimitive)?.longOrNull?.let { through ->
            ChatReceiptEntity(environmentKey, accountId, roomId, receiptActor, "delivered", through, sequence, now)
        },
        (payload["read_through"] as? JsonPrimitive)?.longOrNull?.let { through ->
            ChatReceiptEntity(environmentKey, accountId, roomId, receiptActor, "read", through, sequence, now)
        },
    )
}

private class ChatV2SyncException(message: String) : IllegalStateException(message)
private class ChatV2AccessRevokedException : IllegalStateException("Conversation access was revoked.")

private fun NetworkAPIResult.ErrorType.isAuthoritativeRoomDenial(): Boolean =
    this == NetworkAPIResult.ErrorType.FORBIDDEN || this == NetworkAPIResult.ErrorType.NOT_FOUND
