package com.srisu.srisu.features.chat.matrix

import com.srisu.srisu.features.couplechat.data.PreparedChatMedia
import de.connect2x.trixnity.client.room.message.reply
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.client.room.message.redact
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.ImageInfo
import de.connect2x.trixnity.core.model.events.m.room.VideoInfo
import de.connect2x.trixnity.core.model.events.m.room.AudioInfo
import de.connect2x.trixnity.utils.toByteArrayFlow
import kotlin.io.encoding.Base64

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionChangedException
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.flattenNotNull
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.client.room.getState
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.room.OutboxMessageEventHandler
import de.connect2x.trixnity.client.room.toFlowList
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.isEncrypted
import de.connect2x.trixnity.client.store.Authentication
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository
import de.connect2x.trixnity.client.store.repository.InMemoryAuthenticationRepository
import de.connect2x.trixnity.clientserverapi.client.ClassicMatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLogin
import de.connect2x.trixnity.clientserverapi.model.authentication.LoginType
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.Url
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class TrixnityMatrixChatRepository(
    private val remote: MatrixSessionRemote,
    private val sessions: SessionCoordinator,
    private val environment: ApiEnvironment,
    private val engine: HttpClientEngine,
    private val platformStore: MatrixPlatformStore,
    private val lifetime: ApplicationLifetime,
    private val now: () -> Instant = { Clock.System.now() },
    private val matrixClientCreator: suspend (createDefault: suspend () -> MatrixClient) -> MatrixClient =
        { createDefault -> createDefault() },
) : MatrixChatRepository {
    private val scope = CoroutineScope(lifetime.scope.coroutineContext + SupervisorJob(lifetime.scope.coroutineContext[Job]))
    private val clientMutex = Mutex()
    private val _state = MutableStateFlow(MatrixChatRoomState(roomId = ""))
    private var activeRoom: ActiveRoom? = null
    private var validatedRoom: ActiveRoom? = null
    private var client: MatrixClient? = null
    private var clientStamp: SessionStamp? = null
    private var clientOperation: Long? = null
    private var clientStorageScope: MatrixStorageScope? = null
    private var clientOutboxGate: MatrixOutboxGate? = null
    private var roomJob: Job? = null
    private var loginJob: Job? = null
    private var recoveryJob: Job? = null
    private var roomOperation: Long = 0
    private var roomOwner: MatrixChatRoomOwner? = null
    private val pendingAccountWipes = mutableSetOf<Long>()
    private var cleanupBlocked = true

    init {
        scope.launch {
            var previous = sessions.stamp()
            cleanupBlocked = runCatching {
                // Repairs a process death between credential removal and the
                // asynchronous account wipe. Signed-out startup removes all exact
                // account directories; signed-in startup preserves only the current
                // account unless its durable tombstone requires deletion too.
                platformStore.reconcileAccountData(previous.accountId)
            }.isFailure
            if (cleanupBlocked) {
                _state.value = MatrixChatRoomState(
                    roomId = _state.value.roomId,
                    availability = MatrixChatAvailability.Error(
                        "Private chat data could not be cleared. Try signing out again.",
                        retryable = true,
                    ),
                )
            }
            sessions.state.collect { current ->
                val previousAccount = previous.accountId
                if (current.accountId != previousAccount) {
                    cleanupBlocked = true
                    roomOperation += 1
                    clientOutboxGate?.disable()
                    roomJob?.cancel()
                    loginJob?.cancel()
                    recoveryJob?.cancel()
                    activeRoom = null
                    validatedRoom = null
                    val roomId = _state.value.roomId
                    // Account changes are a destructive privacy boundary. Complete the
                    // local close/wipe/identity rotation even if the lifetime is canceled
                    // while the best-effort homeserver logout is in flight.
                    previousAccount?.let { accountId ->
                        runCatching { queueAccountWipe(accountId) }
                    }
                    val cleanupSucceeded = withContext(NonCancellable) {
                        val stopped = runCatching {
                            shutdownClient(logout = true, deleteScope = clientStorageScope)
                        }.isSuccess
                        if (stopped) retryPendingAccountWipes() else retryBlockedCleanup()
                    }
                    cleanupBlocked = !cleanupSucceeded
                    _state.value = MatrixChatRoomState(
                        roomId = roomId,
                        availability = if (cleanupSucceeded) {
                            MatrixChatAvailability.Unavailable(
                                if (current.accountId == null) "Sign in to use secure chat."
                                else "Open conversations again for this account."
                            )
                        } else {
                            MatrixChatAvailability.Error(
                                "Private chat data could not be cleared. Try signing out again.",
                                retryable = true,
                            )
                        },
                    )
                } else if (current != previous && clientStamp?.accountId == current.accountId) {
                    // Django access-token rotation is not a Matrix account switch.
                    clientStamp = current
                    activeRoom = activeRoom?.copy(stamp = current)
                    validatedRoom = validatedRoom?.copy(stamp = current)
                    client?.let { observeLoginState(it, current) }
                }
                previous = current
            }
        }
        scope.launch {
            lifetime.foreground.collectLatest { foreground ->
                try {
                    if (foreground) validatedRoom?.let { revalidateOnForeground(it) }
                    else {
                        clientOutboxGate?.disableAndJoin()
                        client?.stopSync()
                    }
                } catch (_: SessionChangedException) {
                    clientOutboxGate?.disableAndJoin()
                    client?.stopSync()
                    _state.update {
                        it.copy(availability = MatrixChatAvailability.Error(
                            "Secure chat authorization changed. Please retry.",
                            retryable = true,
                        ))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    clientOutboxGate?.disableAndJoin()
                    runCatching { client?.stopSync() }
                    _state.update {
                        it.copy(availability = MatrixChatAvailability.Error(
                            "Secure chat could not be paused safely. Please retry.",
                            retryable = true,
                        ))
                    }
                }
            }
        }
    }

    override fun observeRoom(roomId: String): Flow<MatrixChatRoomState> {
        val normalized = normalizeDjangoRoomIdOrNull(roomId) ?: return flowOf(
            MatrixChatRoomState(
                roomId = roomId,
                availability = MatrixChatAvailability.Unavailable("This conversation link is invalid."),
            )
        )
        return _state.map { state ->
            if (state.roomId == normalized) state else MatrixChatRoomState(normalized)
        }.distinctUntilChanged()
    }

    override fun openRoom(roomId: String, owner: MatrixChatRoomOwner) {
        roomOwner = owner
        val normalized = normalizeDjangoRoomIdOrNull(roomId) ?: run {
            val operation = ++roomOperation
            roomJob?.cancel()
            recoveryJob?.cancel()
            clientOutboxGate?.disable()
            activeRoom = null
            validatedRoom = null
            _state.value = MatrixChatRoomState(
                roomId = roomId,
                availability = MatrixChatAvailability.Unavailable("This conversation link is invalid."),
            )
            val currentClient = client
            val currentGate = clientOutboxGate
            scope.launch {
                currentGate?.disableAndJoin()
                if (roomOperation == operation && activeRoom == null) currentClient?.stopSync()
            }
            return
        }
        if (cleanupBlocked) {
            clientOutboxGate?.disable()
            val operation = ++roomOperation
            roomJob?.cancel()
            recoveryJob?.cancel()
            activeRoom = null
            validatedRoom = null
            val requestedStamp = sessions.stamp()
            _state.value = MatrixChatRoomState(normalized)
            roomJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val cleaned = retryBlockedCleanup()
                cleanupBlocked = !cleaned
                if (cleaned && operation == roomOperation && sessions.stamp() == requestedStamp) {
                    openRoomNow(normalized, requestedStamp, operation)
                } else if (operation == roomOperation) {
                    _state.value = MatrixChatRoomState(
                        roomId = normalized,
                        availability = MatrixChatAvailability.Error(
                            "Private chat data could not be cleared. Try signing out again.",
                            retryable = true,
                        ),
                    )
                }
            }
            return
        }
        if (activeRoom?.djangoRoomId == normalized && roomJob?.isActive == true) return
        val operation = ++roomOperation
        roomJob?.cancel()
        recoveryJob?.cancel()
        clientOutboxGate?.disable()
        activeRoom = null
        validatedRoom = null
        _state.value = MatrixChatRoomState(normalized)
        roomJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // The gate was closed synchronously above so the prior relationship's
            // sender is canceled before this bootstrap can suspend. stopSync alone
            // does not pause Trixnity's outbox worker.
            clientOutboxGate?.disableAndJoin()
            client?.stopSync()
            openRoomNow(normalized, sessions.stamp(), operation)
        }
    }

    override fun closeRoom(roomId: String, owner: MatrixChatRoomOwner) {
        if (roomOwner !== owner) return
        roomOwner = null
        val normalized = normalizeDjangoRoomIdOrNull(roomId) ?: return
        if (activeRoom?.djangoRoomId != normalized && _state.value.roomId != normalized) return
        val operation = ++roomOperation
        roomJob?.cancel()
        recoveryJob?.cancel()
        clientOutboxGate?.disable()
        roomJob = null
        activeRoom = null
        _state.value = MatrixChatRoomState(normalized)
        val currentClient = client
        val currentGate = clientOutboxGate
        scope.launch {
            currentGate?.disableAndJoin()
            if (roomOperation == operation && activeRoom == null) currentClient?.stopSync()
        }
    }

    override fun retryRoom(roomId: String, owner: MatrixChatRoomOwner) {
        if (roomOwner !== owner) return
        closeRoom(roomId, owner)
        openRoom(roomId, owner)
    }

    override suspend fun sendText(roomId: String, text: String): Result<String> = runCatching {
        val normalized = validatedDjangoRoomId(roomId)
        val body = text.trim()
        require(body.isNotEmpty()) { "A message cannot be blank." }
        require(body.encodeToByteArray().size <= MATRIX_TEXT_MAX_PLAINTEXT_BYTES) { "Message is too large." }
        val active = activeRoom?.takeIf { it.djangoRoomId == normalized }
            ?: error("Secure conversation is not ready.")
        check(_state.value.roomId == normalized && _state.value.availability is MatrixChatAvailability.Ready) {
            "Secure conversation is not ready."
        }
        sessions.ensureCurrent(active.stamp)
        val currentClient = checkNotNull(client) { "Secure conversation is not ready." }
        requireSecureRoomNow(currentClient, active)
        check(clientOutboxGate?.isEnabled() == true) { "Secure conversation is not ready." }
        _state.update { it.copy(sending = true, composerError = null) }
        try {
            currentClient.room.sendMessage(active.matrixRoomId) { text(body) }
        } finally {
            if (activeRoom == active) _state.update { it.copy(sending = false) }
        }
    }.onFailure { failure ->
        if (failure !is CancellationException && failure !is SessionChangedException) {
            _state.update { it.copy(sending = false, composerError = "Message was not sent. Try again.") }
        }
    }


    private val timelineLimit = MutableStateFlow(TIMELINE_SIZE)

    private suspend fun richRoom(roomId: String): Pair<MatrixClient, ActiveRoom> {
        val normalized = validatedDjangoRoomId(roomId)
        val active = activeRoom?.takeIf { it.djangoRoomId == normalized }
            ?: error("Secure conversation is not ready.")
        check(_state.value.roomId == normalized && _state.value.availability is MatrixChatAvailability.Ready)
        sessions.ensureCurrent(active.stamp)
        val current = checkNotNull(client)
        requireSecureRoomNow(current, active)
        ensureRichRoomCurrent(current, active)
        return current to active
    }

    private fun ensureRichRoomCurrent(current: MatrixClient, active: ActiveRoom) {
        sessions.ensureCurrent(active.stamp)
        check(activeRoom == active && client === current && clientOutboxGate?.isEnabled() == true)
    }

    override suspend fun sendMomentReply(roomId: String, text: String, reference: com.srisu.srisu.features.moments.data.MomentReference): Result<String> = runCatching {
        val (current, active) = richRoom(roomId)
        require(reference.id > 0 && reference.couple == active.relationshipId)
        require(text.isNotBlank() && text.encodeToByteArray().size <= MATRIX_TEXT_MAX_PLAINTEXT_BYTES)
        ensureRichRoomCurrent(current, active)
        // The optional external URL lives inside the encrypted Matrix event. It contains only IDs.
        current.room.sendMessage(active.matrixRoomId) {
            content(RoomMessageEventContent.TextBased.Text(body = text.trim(), externalUrl = reference.url))
        }
    }

    override suspend fun sendReply(roomId: String, text: String, replyTo: String): Result<String> = runCatching {
        val (current, active) = richRoom(roomId)
        require(text.isNotBlank() && text.encodeToByteArray().size <= MATRIX_TEXT_MAX_PLAINTEXT_BYTES)
        val original = current.room.getTimelineEvent(active.matrixRoomId, EventId(replyTo)).first()
        check(original != null && original.event.sender.full in setOf(active.ownMatrixId, active.partnerMatrixId))
        ensureRichRoomCurrent(current, active)
        current.room.sendMessage(active.matrixRoomId) { reply(original); text(text.trim()) }
    }

    override suspend fun sendMedia(roomId: String, media: PreparedChatMedia, caption: String, replyTo: String?): Result<String> = runCatching {
        val (current, active) = richRoom(roomId)
        require(media.bytes.isNotEmpty() && media.bytes.size <= 16 * 1024 * 1024)
        require(media.kind in setOf("photo", "video", "voice"))
        require(caption.encodeToByteArray().size <= MATRIX_TEXT_MAX_PLAINTEXT_BYTES)
        require(media.durationMs in 0..120_000)
        val file = current.media.prepareUploadEncryptedMedia(media.bytes.toByteArrayFlow())
        val thumbnail = media.thumbnail?.takeIf { it.length <= 8192 }?.let {
            current.media.prepareUploadEncryptedMedia(Base64.decode(it).toByteArrayFlow())
        }
        // Never use the SDK's plaintext-media branch: couple attachments always carry an EncryptedFile.
        val relation = replyTo?.let { RelatesTo.Reply(RelatesTo.ReplyTo(EventId(it))) }
        val content = when (media.kind) {
            "photo" -> RoomMessageEventContent.FileBased.Image(
                body = caption.ifBlank { "Photo" }, fileName = "photo", file = file,
                info = ImageInfo(width = media.width, height = media.height, mimeType = media.mime,
                    size = media.bytes.size.toLong(), thumbnailFile = thumbnail), relatesTo = relation)
            "video" -> RoomMessageEventContent.FileBased.Video(
                body = caption.ifBlank { "Video" }, fileName = "video", file = file,
                info = VideoInfo(width = media.width, height = media.height, duration = media.durationMs,
                    mimeType = media.mime, size = media.bytes.size.toLong(), thumbnailFile = thumbnail), relatesTo = relation)
            else -> RoomMessageEventContent.FileBased.Audio(
                body = caption.ifBlank { "Voice message" }, fileName = "voice.wav", file = file,
                info = AudioInfo(duration = media.durationMs, mimeType = media.mime, size = media.bytes.size.toLong()), relatesTo = relation)
        }
        ensureRichRoomCurrent(current, active)
        current.room.sendMessage(active.matrixRoomId) { content(content) }
    }

    override suspend fun react(roomId: String, messageId: String, reaction: String?): Result<Unit> = runCatching {
        val (current, active) = richRoom(roomId)
        _state.value.messages.firstOrNull { it.id == messageId && !it.unavailable && !it.deleted }
            ?: error("Message unavailable.")
        require(reaction == null || reaction in setOf("❤️", "😂", "🥰", "😮", "😢", "👍"))
        // Trixnity deliberately sends m.reaction in plaintext. Keep the annotation inside
        // an encrypted room-message event instead; an empty key removes this sender's reaction.
        current.room.sendMessage(active.matrixRoomId) {
            content(RoomMessageEventContent.TextBased.Text(body = reaction?.let { "Reacted $it" } ?: "Removed a reaction", relatesTo = RelatesTo.Annotation(EventId(messageId), reaction.orEmpty())))
        }
    }

    override suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> = runCatching {
        val (current, active) = richRoom(roomId)
        check(_state.value.messages.any { it.id == messageId && it.mine && it.delivery == MatrixMessageDelivery.SENT })
        current.room.sendMessage(active.matrixRoomId) { redact(EventId(messageId)) }
    }

    override suspend fun attachment(roomId: String, messageId: String, thumbnail: Boolean): Result<ByteArray> = runCatching {
        val (current, active) = richRoom(roomId)
        val media = checkNotNull(_state.value.messages.firstOrNull { it.id == messageId }?.attachment)
        val file = if (thumbnail) media.thumbnail ?: media.file.takeIf { media.kind == "photo" }
            ?: error("No thumbnail.") else media.file
        val limit = if (thumbnail) 512 * 1024L else 16 * 1024 * 1024L
        val bytes = current.media.getEncryptedMedia(file, maxSize = limit).getOrThrow()
            .toByteArray(maxSize = limit) ?: error("Attachment is too large.")
        sessions.ensureCurrent(active.stamp)
        check(activeRoom == active && client === current)
        bytes
    }

    private val receiptMutex = Mutex()
    private val lastRead = mutableMapOf<String, Long>()
    override suspend fun markRead(roomId: String, messageId: String): Result<Unit> = runCatching {
        receiptMutex.withLock {
            val (current, active) = richRoom(roomId)
            val message = _state.value.messages.firstOrNull { it.id == messageId && it.delivery == MatrixMessageDelivery.SENT }
                ?: return@withLock
            val key = active.ownMatrixId + active.matrixRoomId.full
            val knownReceipt = current.user.getReceiptsById(active.matrixRoomId, UserId(active.ownMatrixId)).first()
                ?.receipts?.get(ReceiptType.Read)?.eventId
            val knownTime = knownReceipt?.let { id ->
                kotlinx.coroutines.withTimeoutOrNull(1500) { current.room.getTimelineEvent(active.matrixRoomId, id).first()?.event?.originTimestamp }
            }
            if (knownReceipt != null && knownTime == null) return@withLock
            if (message.timestampMillis <= maxOf(lastRead[key] ?: 0, knownTime ?: 0)) return@withLock
            ensureRichRoomCurrent(current, active)
            current.api.room.setReceipt(active.matrixRoomId, EventId(messageId), ReceiptType.Read).getOrThrow()
            lastRead[key] = message.timestampMillis
        }
    }

    override fun loadOlder(roomId: String) {
        if (_state.value.roomId == roomId && _state.value.availability is MatrixChatAvailability.Ready)
            timelineLimit.value = (timelineLimit.value + TIMELINE_SIZE).coerceAtMost(2000)
    }

    override suspend fun retryMessage(roomId: String, transactionId: String): Result<Unit> = runCatching {
        val normalized = validatedDjangoRoomId(roomId)
        require(transactionId.isNotBlank())
        val active = activeRoom?.takeIf { it.djangoRoomId == normalized }
            ?: error("Secure conversation is not ready.")
        check(_state.value.roomId == normalized && _state.value.availability is MatrixChatAvailability.Ready) {
            "Secure conversation is not ready."
        }
        sessions.ensureCurrent(active.stamp)
        val currentClient = checkNotNull(client)
        requireSecureRoomNow(currentClient, active)
        check(clientOutboxGate?.isEnabled() == true) { "Secure conversation is not ready." }
        currentClient.room.retrySendMessage(active.matrixRoomId, transactionId)
    }

    override fun close() {
        roomJob?.cancel()
        loginJob?.cancel()
        recoveryJob?.cancel()
        clientOutboxGate?.disable()
        activeRoom = null
        validatedRoom = null
        roomOwner = null
        client?.close()
        client = null
        scope.cancel()
    }

    private suspend fun openRoomNow(
        djangoRoomId: String,
        requestedStamp: SessionStamp,
        operation: Long,
        allowSameAccountRestart: Boolean = true,
    ) {
        if (operation != roomOperation) return
        val accountId = requestedStamp.accountId ?: run {
            _state.value = MatrixChatRoomState(
                djangoRoomId,
                availability = MatrixChatAvailability.Unavailable("Sign in to use secure chat."),
            )
            return
        }
        try {
            sessions.ensureCurrent(requestedStamp)
            val result = createMatrixSessionWithRetry(djangoRoomId, accountId, operation) ?: return
            val bootstrap = when (result) {
                is NetworkAPIResult.Success -> requireNotNull(result.response) { "The Matrix session response was empty." }
                is NetworkAPIResult.Error -> {
                    val unavailable = result.failure.kind in setOf(
                        NetworkAPIResult.ErrorType.UNAUTHORIZED,
                        NetworkAPIResult.ErrorType.FORBIDDEN,
                        NetworkAPIResult.ErrorType.NOT_FOUND,
                    )
                    _state.value = MatrixChatRoomState(
                        djangoRoomId,
                        availability = if (unavailable) MatrixChatAvailability.Unavailable(result.failure.message)
                        else MatrixChatAvailability.Error(result.failure.message, result.failure.retryable),
                    )
                    return
                }
            }.validated(accountId, allowInsecureHomeserver = environment.development)
            if (operation != roomOperation) return
            // An authenticated request can rotate Django credentials. Accept the new
            // generation only while it still belongs to this exact account.
            val bootstrapStamp = sessions.stamp().also {
                if (it.accountId != accountId) throw SessionChangedException()
            }
            val expiresAt = Instant.parse(bootstrap.loginTokenExpiresAt)
            require(expiresAt > now() + 5.seconds) { "Matrix login token expired." }

            val currentClient = ensureClient(bootstrap, bootstrapStamp, operation)
            sessions.ensureCurrent(bootstrapStamp)
            if (operation != roomOperation) {
                discardStaleClient(currentClient, operation)
                return
            }
            if (!startSyncForOperation(currentClient, operation)) {
                discardStaleClient(currentClient, operation)
                return
            }
            val matrixRoomId = RoomId(bootstrap.roomId)
            awaitSecureRoom(
                matrixClient = currentClient,
                roomId = matrixRoomId,
                ownMatrixId = bootstrap.selfMatrixUserId,
                partnerMatrixId = bootstrap.partnerMatrixUserId,
            )
            sessions.ensureCurrent(bootstrapStamp)
            if (operation != roomOperation) {
                discardStaleClient(currentClient, operation)
                return
            }
            val active = ActiveRoom(
                djangoRoomId = djangoRoomId,
                matrixRoomId = matrixRoomId,
                ownMatrixId = bootstrap.selfMatrixUserId,
                partnerMatrixId = bootstrap.partnerMatrixUserId,
                stamp = bootstrapStamp,
                relationshipId = bootstrap.relationshipId,
                membershipEpoch = bootstrap.membershipEpoch,
                operation = operation,
            )
            activeRoom = active
            validatedRoom = active
            observeClientRoom(currentClient, active)
        } catch (_: SessionChangedException) {
            val current = sessions.stamp()
            if (current.accountId == accountId) {
                if (allowSameAccountRestart && operation == roomOperation) {
                    openRoomNow(djangoRoomId, current, operation, allowSameAccountRestart = false)
                } else if (_state.value.roomId == djangoRoomId) {
                    _state.value = MatrixChatRoomState(
                        djangoRoomId,
                        availability = MatrixChatAvailability.Error("Your session changed. Please retry."),
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            // A nested connection/verification deadline is recoverable. Cancellation
            // of the owning room operation must still propagate normally.
            currentCoroutineContext().ensureActive()
            if (operation == roomOperation &&
                _state.value.roomId == djangoRoomId &&
                sessions.stamp().accountId == accountId
            ) {
                _state.value = MatrixChatRoomState(
                    djangoRoomId,
                    availability = MatrixChatAvailability.Error(
                        "Secure chat took too long to verify. Please retry.",
                        retryable = true,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (operation == roomOperation &&
                _state.value.roomId == djangoRoomId &&
                sessions.stamp().accountId == accountId
            ) {
                _state.value = MatrixChatRoomState(
                    djangoRoomId,
                    availability = MatrixChatAvailability.Error(
                        if (cleanupBlocked) "Private chat data could not be cleared. Try signing out again."
                        else "Secure chat could not connect. Please retry.",
                    ),
                )
            }
        }
    }

    /**
     * Relationship acceptance commits before asynchronous Matrix provisioning, so
     * the bootstrap endpoint can briefly return a retryable 503 while the mapping
     * is becoming ready. Keep that expected window in Loading, but never turn this
     * into a general write retry: every other status and every non-retryable 503 is
     * returned immediately to the existing authorization/error handling.
     */
    private suspend fun createMatrixSessionWithRetry(
        djangoRoomId: String,
        accountId: Long,
        operation: Long,
    ): NetworkAPIResult<MatrixSessionDto?>? {
        repeat(MATRIX_BOOTSTRAP_MAX_ATTEMPTS) { attempt ->
            currentCoroutineContext().ensureActive()
            if (!isCurrentBootstrapOperation(operation, accountId)) return null

            val result = remote.create(djangoRoomId)

            // A transport implementation that completes after cancellation must
            // still be unable to revive a closed room or a different account.
            currentCoroutineContext().ensureActive()
            if (!isCurrentBootstrapOperation(operation, accountId)) return null
            if (!result.isRetryableMatrixBootstrap503() || attempt == MATRIX_BOOTSTRAP_MAX_ATTEMPTS - 1) {
                return result
            }

            delay(MATRIX_BOOTSTRAP_RETRY_DELAYS[attempt])
        }
        error("Unreachable Matrix bootstrap retry state")
    }

    private fun isCurrentBootstrapOperation(operation: Long, accountId: Long): Boolean =
        operation == roomOperation && sessions.stamp().accountId == accountId

    private suspend fun ensureClient(
        bootstrap: MatrixSessionDto,
        stamp: SessionStamp,
        operation: Long,
    ): MatrixClient = clientMutex.withLock {
        if (operation != roomOperation) throw CancellationException("Superseded Matrix room operation.")
        val accountId = requireNotNull(stamp.accountId)
        val storageScope = MatrixStorageScope.create(
            accountId = accountId,
            apiBaseUrl = environment.baseUrl,
            homeserverUrl = bootstrap.homeserverUrl,
            relationshipId = bootstrap.relationshipId,
            membershipEpoch = bootstrap.membershipEpoch,
            matrixRoomId = bootstrap.roomId,
        )
        val candidateDeviceId = platformStore.deviceId(storageScope)
        val existing = client
        val sameIdentity = existing != null &&
            clientStamp?.accountId == stamp.accountId &&
                clientStorageScope == storageScope &&
                existing.userId.full == bootstrap.selfMatrixUserId &&
                existing.baseUrl == Url(bootstrap.homeserverUrl) &&
                existing.deviceId == candidateDeviceId
        when (existing?.let { matrixClientReuseDecision(sameIdentity, it.loginState.value) }) {
            MatrixClientReuseDecision.REUSE -> {
                clientOutboxGate?.disableAndJoin()
                clientStamp = stamp
                clientOperation = operation
                return@withLock existing
            }
            MatrixClientReuseDecision.REAUTHENTICATE -> {
                // The volatile token expired while the room was closed. Keep the same
                // crypto/timeline/outbox database and reopen it with the fresh bootstrap.
                shutdownClientLocked(logout = false, deleteScope = null)
            }
            MatrixClientReuseDecision.REPLACE -> {
                shutdownClientLocked(logout = true, deleteScope = clientStorageScope)
            }
            null -> Unit
        }
        if (client == null && clientStorageScope != null && clientStorageScope != storageScope) {
            shutdownClientLocked(logout = false, deleteScope = clientStorageScope)
        }
        // A hard logout/identity replacement above deletes crypto and rotates the
        // persisted Matrix device id. Never log in with the pre-rotation value.
        val deviceId = platformStore.deviceId(storageScope)
        sessions.ensureCurrent(stamp)
        if (operation != roomOperation) throw CancellationException("Superseded Matrix room operation.")
        // Removes relationship stores that can no longer be selected, including after
        // process restart when no in-memory client/scope exists. The selected scope is
        // retained so same-relationship reauthentication preserves crypto and outbox.
        platformStore.deleteAllAccountData(accountId, preserving = storageScope)
        sessions.ensureCurrent(stamp)
        if (operation != roomOperation) throw CancellationException("Superseded Matrix room operation.")

        val outboxGate = MatrixOutboxGate()
        val created = matrixClientCreator {
            // The short-lived bootstrap JWT is intentionally confined to this expression and never logged or persisted.
            val auth = MatrixClientAuthProviderData.classicLogin(
                baseUrl = Url(bootstrap.homeserverUrl),
                token = bootstrap.loginToken,
                loginType = LoginType.Unknown(bootstrap.loginType, JsonObject(emptyMap())),
                deviceId = deviceId,
                initialDeviceDisplayName = "SriSu",
                refreshToken = false,
                httpClientEngine = engine,
            ).getOrThrow()
            sessions.ensureCurrent(stamp)
            if (operation != roomOperation) throw CancellationException("Superseded Matrix room operation.")
            clientStorageScope = storageScope
            val repositories = platformStore.repositories(storageScope).withVolatileAuthentication(auth)
            MatrixClient.create(
                repositoriesModule = repositories,
                mediaStoreModule = platformStore.media(storageScope),
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
                authProviderData = auth,
                configuration = {
                    name = "SriSu-Matrix-$accountId"
                    httpClientEngine = engine
                    storeTimelineEventContentUnencrypted = false
                    // The upstream sender otherwise starts during MatrixClient.create and
                    // can drain a persisted outbox before SriSu's relationship/room gate.
                    modulesFactories += {
                        module {
                            single<EventHandler>(named<OutboxMessageEventHandler>()) {
                                createGatedMatrixOutboxEventHandler(
                                    gate = outboxGate,
                                    config = get(),
                                    api = get(),
                                    roomStore = get(),
                                    roomEventEncryptionServices = getAll(),
                                    userService = get(),
                                    mediaService = get(),
                                    roomOutboxMessageStore = get(),
                                    eventContentMediaMappings = get(),
                                    currentSyncState = get(),
                                    userInfo = get(),
                                    transactionManager = get(),
                                    clock = get(),
                                )
                            }
                        }
                    }
                },
            ).getOrThrow()
        }
        // Keep the storage boundary attached to every returned candidate. The default
        // path also sets it before store initialization so create failures remain recoverable.
        clientStorageScope = storageScope
        try {
            sessions.ensureCurrent(stamp)
            check(created.userId.full == bootstrap.selfMatrixUserId) { "Matrix user mismatch." }
            check(created.deviceId == deviceId) { "Matrix device mismatch." }
            check(created.baseUrl == Url(bootstrap.homeserverUrl)) { "Matrix homeserver mismatch." }
            if (operation != roomOperation) throw CancellationException("Superseded Matrix room operation.")
        } catch (failure: Throwable) {
            val stopped = withContext(NonCancellable) {
                runCatching { closeClientAndAwait(created) }.isSuccess
            }
            if (!stopped) {
                // Retain the failed candidate as the only shutdown target. Dropping
                // this reference would let cleanup delete its Room/crypto store while
                // the candidate's writers may still be alive.
                outboxGate.disable()
                client = created
                clientStamp = stamp
                clientOperation = operation
                clientOutboxGate = outboxGate
                cleanupBlocked = true
                runCatching { queueAccountWipe(accountId) }
            }
            throw failure
        }
        client = created
        clientStamp = stamp
        clientOperation = operation
        clientOutboxGate = outboxGate
        observeLoginState(created, stamp)
        created
    }

    private fun observeClientRoom(matrixClient: MatrixClient, active: ActiveRoom) {
        val observedGate = checkNotNull(clientOutboxGate) { "Matrix outbox gate is unavailable." }
        timelineLimit.value = TIMELINE_SIZE
        val timeline = matrixClient.room.getLastTimelineEvents(active.matrixRoomId) {
            minSize = 1
            maxSize = 2000
            decryptionTimeout = TIMELINE_EVENT_TIMEOUT
            fetchTimeout = TIMELINE_EVENT_TIMEOUT
        }.toFlowList(timelineLimit).resolveTimeline()
        val outbox = matrixClient.room.getOutbox(active.matrixRoomId).flatten().flatMapLatest { pending ->
            if (pending.isEmpty()) flowOf(emptyList())
            else combine(pending.map { it.mediaUploadProgress }) { pending }
        }
        val receipts = matrixClient.user.getReceiptsById(active.matrixRoomId, UserId(active.partnerMatrixId))
        val security = secureRoomState(matrixClient, active)
        roomJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var hasVerifiedSnapshot = false
            var lastVerifiedMessages = emptyList<MatrixChatMessage>()
            val observations = combine(timeline, outbox, matrixClient.syncState, security, receipts) { events, pending, sync, secure, receipt ->
                val ready = matrixOutboxAllowed(secure, sync, foreground = true)
                val timelineMessages = if (ready) matrixTimelineMessages(events, active.ownMatrixId, active.partnerMatrixId,
                    receipt?.receipts?.get(ReceiptType.Read)?.eventId?.full) else emptyList()
                val pendingMessages = if (ready) pending.mapNotNull { it.toMessage(active.ownMatrixId) }
                    .filter { candidate ->
                        timelineMessages.none { it.id == candidate.id || it.stableKey == candidate.stableKey }
                    }
                else emptyList()
                RoomObservation(
                    secure = secure,
                    syncState = sync,
                    pendingTransactionIds = pending.map { it.transactionId },
                    state = MatrixChatRoomState(
                        roomId = active.djangoRoomId,
                        accountId = active.stamp.accountId,
                        hasOlder = events.size >= timelineLimit.value && timelineLimit.value < 2000,
                        failedChangeIds = if (ready) pending.filter { it.sendError != null && it.toMessage(active.ownMatrixId) == null }.map { it.transactionId } else emptyList(),
                        messages = (timelineMessages + pendingMessages)
                            .distinctBy(MatrixChatMessage::id)
                            .distinctBy(MatrixChatMessage::stableKey)
                            .sortedBy(MatrixChatMessage::timestampMillis),
                        availability = when {
                            !secure -> MatrixChatAvailability.Unavailable(
                                "This conversation is no longer a private encrypted couple room."
                            )
                            sync == SyncState.RUNNING -> MatrixChatAvailability.Ready()
                            sync == SyncState.INITIAL_SYNC || sync == SyncState.STARTED ->
                                MatrixChatAvailability.Loading
                            else -> MatrixChatAvailability.Error(
                                "Secure chat is not synchronized. Retry to reconnect.",
                                retryable = true,
                            )
                        },
                        syncing = sync == SyncState.INITIAL_SYNC || sync == SyncState.STARTED,
                        // Pending outbox work does not disable composing another message
                        // while this room remains securely synchronized.
                        sending = false,
                        composerError = null,
                    ),
                )
            }
            collectWithOutboxGateFailClosed(
                gate = observedGate,
                source = observations,
                onEach = { observation ->
                    val current = activeRoom
                    if (client === matrixClient &&
                        clientOutboxGate === observedGate &&
                        current?.djangoRoomId == active.djangoRoomId &&
                        current.matrixRoomId == active.matrixRoomId &&
                        current.operation == active.operation &&
                        roomOperation == active.operation &&
                        sessions.stamp() == current.stamp
                    ) {
                        val synchronized = observation.secure && observation.syncState == SyncState.RUNNING
                        if (synchronized) {
                            hasVerifiedSnapshot = true
                            lastVerifiedMessages = observation.state.messages
                        }
                        if (!observation.secure) {
                            observedGate.disable()
                            _state.value = observation.state
                            observedGate.disableAndJoin()
                            failClosedForSecurityLoss(matrixClient, active, observation.pendingTransactionIds)
                            throw CancellationException("Matrix couple-room security gate changed.")
                        } else if (matrixOutboxAllowed(
                                secure = true,
                                syncState = observation.syncState,
                                foreground = lifetime.foreground.value,
                            )
                        ) {
                            // Open the durable sender only from the continuously observed
                            // secure and synchronized state, before Ready becomes visible.
                            observedGate.enable()
                            _state.value = observation.state
                        } else {
                            observedGate.disable()
                            observedGate.disableAndJoin()
                            _state.value = if (hasVerifiedSnapshot) {
                                observation.state.copy(
                                    messages = lastVerifiedMessages,
                                    availability = MatrixChatAvailability.Offline(
                                        "Secure chat is offline. Sending is paused until a fresh sync completes."
                                    ),
                                    sending = false,
                                    composerError = null,
                                )
                            } else observation.state
                        }
                    }
                },
                onUnexpectedTermination = {
                    val current = activeRoom
                    if (client === matrixClient &&
                        clientOutboxGate === observedGate &&
                        current?.djangoRoomId == active.djangoRoomId &&
                        current.matrixRoomId == active.matrixRoomId &&
                        current.operation == active.operation &&
                        roomOperation == active.operation
                    ) {
                        roomOperation += 1
                        roomJob = null
                        activeRoom = null
                        validatedRoom = null
                        recoveryJob?.cancel()
                        recoveryJob = null
                        runCatching { matrixClient.stopSync() }
                        _state.value = MatrixChatRoomState(
                            roomId = active.djangoRoomId,
                            messages = emptyList(),
                            availability = MatrixChatAvailability.Error(
                                "Secure chat monitoring stopped. Please retry.",
                                retryable = true,
                            ),
                        )
                    }
                },
            )
        }
    }

    /** Every foreground transition closes the sender gate, then re-authorizes the
     * retained relationship before sync and outbox processing may resume.
     */
    private suspend fun revalidateOnForeground(
        expected: ActiveRoom,
        allowSameAccountRestart: Boolean = true,
    ) {
        if (validatedRoom?.sameConversation(expected) != true) return
        val wasOpen = activeRoom?.sameConversation(expected) == true
        val operation = ++roomOperation
        clientOutboxGate?.disableAndJoin()
        roomJob?.cancel()
        roomJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        activeRoom = if (wasOpen) expected.copy(operation = operation) else null
        if (wasOpen) {
            _state.value = MatrixChatRoomState(
                expected.djangoRoomId,
                availability = MatrixChatAvailability.Loading,
            )
        }
        client?.stopSync()

        val accountId = sessions.stamp().accountId
        if (accountId == null || accountId != expected.stamp.accountId) {
            revokeConversation(expected, "Sign in to use secure chat.")
            return
        }

        val result = try {
            createMatrixSessionWithRetry(expected.djangoRoomId, accountId, operation) ?: return
        } catch (_: SessionChangedException) {
            restartForegroundValidationAfterSessionChange(expected, accountId, allowSameAccountRestart)
            return
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            pauseAfterValidationFailure(expected, operation, "Secure chat took too long to verify. Please retry.")
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pauseAfterValidationFailure(expected, operation, "Secure chat could not reconnect. Please retry.")
            return
        }
        if (operation != roomOperation || validatedRoom?.sameConversation(expected) != true) return

        val bootstrap = when (result) {
            is NetworkAPIResult.Success -> result.response
            is NetworkAPIResult.Error -> {
                if (result.failure.kind in setOf(
                        NetworkAPIResult.ErrorType.UNAUTHORIZED,
                        NetworkAPIResult.ErrorType.FORBIDDEN,
                        NetworkAPIResult.ErrorType.NOT_FOUND,
                    )
                ) {
                    revokeConversation(expected, result.failure.message)
                } else {
                    pauseAfterValidationFailure(expected, operation, result.failure.message)
                }
                return
            }
        }
        val validated = runCatching {
            requireNotNull(bootstrap) { "The Matrix session response was empty." }
                .validated(accountId, allowInsecureHomeserver = environment.development)
        }.getOrElse {
            revokeConversation(expected, "Secure chat authorization changed.")
            return
        }
        if (!validated.matches(expected)) {
            revokeConversation(expected, "This relationship no longer owns the conversation.")
            return
        }
        val freshStamp = sessions.stamp()
        if (freshStamp.accountId != accountId) {
            revokeConversation(expected, "Sign in to use secure chat.")
            return
        }
        val expiresAt = runCatching { Instant.parse(validated.loginTokenExpiresAt) }.getOrNull()
        if (expiresAt == null || expiresAt <= now() + 5.seconds) {
            pauseAfterValidationFailure(expected, operation, "Secure chat authorization expired. Please retry.")
            return
        }

        try {
            val currentClient = ensureClient(validated, freshStamp, operation)
            if (!startSyncForOperation(currentClient, operation)) {
                discardStaleClient(currentClient, operation)
                return
            }
            awaitSecureRoom(
                matrixClient = currentClient,
                roomId = expected.matrixRoomId,
                ownMatrixId = expected.ownMatrixId,
                partnerMatrixId = expected.partnerMatrixId,
            )
            if (operation != roomOperation || validatedRoom?.sameConversation(expected) != true) {
                discardStaleClient(currentClient, operation)
                return
            }
            val refreshed = expected.copy(stamp = freshStamp, operation = operation)
            validatedRoom = refreshed
            if (wasOpen) {
                activeRoom = refreshed
                observeClientRoom(currentClient, refreshed)
            } else {
                // Reauthorization may wipe a revoked conversation, but a closed UI
                // never keeps sync/outbox work running without a live security observer.
                currentClient.stopSync()
            }
        } catch (_: SessionChangedException) {
            restartForegroundValidationAfterSessionChange(expected, accountId, allowSameAccountRestart)
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            pauseAfterValidationFailure(expected, operation, "Secure chat took too long to verify. Please retry.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pauseAfterValidationFailure(expected, operation, "Secure chat could not reconnect. Please retry.")
        }
    }

    private suspend fun restartForegroundValidationAfterSessionChange(
        expected: ActiveRoom,
        accountId: Long,
        allowRestart: Boolean,
    ) {
        val freshStamp = sessions.stamp()
        if (allowRestart &&
            freshStamp.accountId == accountId &&
            validatedRoom?.sameConversation(expected) == true
        ) {
            revalidateOnForeground(expected.copy(stamp = freshStamp), allowSameAccountRestart = false)
        } else if (freshStamp.accountId == accountId) {
            pauseAfterValidationFailure(
                expected,
                roomOperation,
                "Secure chat authorization changed. Please retry.",
            )
        } else {
            revokeConversation(expected, "Sign in to use secure chat.")
        }
    }

    private suspend fun pauseAfterValidationFailure(expected: ActiveRoom, operation: Long, message: String) {
        if (operation != roomOperation || validatedRoom?.sameConversation(expected) != true) return
        roomJob?.cancel()
        roomJob = null
        activeRoom = null
        clientOutboxGate?.disableAndJoin()
        client?.stopSync()
        if (_state.value.roomId == expected.djangoRoomId) {
            _state.value = MatrixChatRoomState(
                expected.djangoRoomId,
                availability = MatrixChatAvailability.Error(message, retryable = true),
            )
        }
    }

    private suspend fun retryPendingAccountWipes(): Boolean {
        val queued = pendingAccountWipes.toSet()
        val cleanupAttempts = queued.associateWith { accountId ->
            val journaled = runCatching {
                platformStore.recordAccountCleanupRequired(accountId)
            }.isSuccess
            val deletedDirectly = runCatching {
                platformStore.deleteAllAccountData(accountId)
            }.isSuccess
            journaled to deletedDirectly
        }
        val reconciliationSucceeded = runCatching {
            platformStore.reconcileAccountData(sessions.stamp().accountId)
        }.isSuccess
        // A successful reconciliation is authoritative for a journaled account:
        // it enumerated the protected Matrix root, consumed the durable marker,
        // and completed the exact account deletion even if the earlier direct
        // attempt hit a transient filesystem error. If journaling itself failed,
        // only a successful direct deletion can satisfy the privacy boundary.
        val queuedAccountsCleaned = cleanupAttempts.values.all { (journaled, deletedDirectly) ->
            deletedDirectly || (journaled && reconciliationSucceeded)
        }
        if (reconciliationSucceeded && queuedAccountsCleaned) {
            pendingAccountWipes.removeAll(queued)
            if (client == null && clientStorageScope?.accountId in queued) {
                clientStorageScope = null
            }
        }
        return reconciliationSucceeded && queuedAccountsCleaned && pendingAccountWipes.isEmpty()
    }

    /** Never touches a protected store until the previous Matrix client's job has stopped. */
    private suspend fun retryBlockedCleanup(): Boolean {
        val stopped = runCatching {
            if (client != null) shutdownClient(logout = false, deleteScope = null)
        }.isSuccess
        return stopped && retryPendingAccountWipes()
    }

    private fun queueAccountWipe(accountId: Long) {
        pendingAccountWipes += accountId
        platformStore.recordAccountCleanupRequired(accountId)
    }

    private suspend fun failClosedForSecurityLoss(
        matrixClient: MatrixClient,
        active: ActiveRoom,
        transactionIds: List<String>,
    ) {
        revokeConversation(
            expected = active,
            message = "This conversation is no longer a private encrypted couple room.",
            knownClient = matrixClient,
            knownTransactionIds = transactionIds,
        )
    }

    private suspend fun revokeConversation(
        expected: ActiveRoom,
        message: String,
        knownClient: MatrixClient? = client,
        knownTransactionIds: List<String>? = null,
    ) {
        if (validatedRoom?.sameConversation(expected) != true && activeRoom?.sameConversation(expected) != true) return
        roomOperation += 1
        val caller = currentCoroutineContext()[Job]
        roomJob?.takeIf { it !== caller }?.cancel()
        roomJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        clientOutboxGate?.disable()
        activeRoom = null
        validatedRoom = null
        _state.value = MatrixChatRoomState(
            expected.djangoRoomId,
            availability = MatrixChatAvailability.Unavailable(message),
        )
        val deleteScope = clientStorageScope
        val cleanupSucceeded = withContext(NonCancellable) {
            val directCleanup = runCatching {
                clientOutboxGate?.disableAndJoin()
                if (knownClient != null) {
                    cancelRoomOutbox(knownClient, expected.matrixRoomId, knownTransactionIds)
                }
                shutdownClient(logout = true, deleteScope = deleteScope)
            }.isSuccess
            if (directCleanup) true else {
                expected.stamp.accountId?.let { runCatching { queueAccountWipe(it) } }
                retryBlockedCleanup()
            }
        }
        cleanupBlocked = !cleanupSucceeded
        if (!cleanupSucceeded) {
            _state.value = MatrixChatRoomState(
                expected.djangoRoomId,
                availability = MatrixChatAvailability.Error(
                    "Private chat data could not be cleared. Try signing out again.",
                    retryable = true,
                ),
            )
        }
    }

    private suspend fun cancelRoomOutbox(
        matrixClient: MatrixClient,
        roomId: RoomId,
        knownTransactionIds: List<String>?,
    ) {
        val transactionIds = knownTransactionIds ?: runCatching {
            withTimeout(OUTBOX_CANCEL_TIMEOUT) {
                matrixClient.room.getOutbox(roomId).flatten().first().map { it.transactionId }
            }
        }.getOrDefault(emptyList())
        transactionIds.distinct().forEach { transactionId ->
            runCatching { matrixClient.room.cancelSendMessage(roomId, transactionId) }
        }
    }

    private fun MatrixSessionDto.matches(active: ActiveRoom): Boolean =
        roomId == active.matrixRoomId.full &&
            selfMatrixUserId == active.ownMatrixId &&
            partnerMatrixUserId == active.partnerMatrixId &&
            relationshipId == active.relationshipId &&
            membershipEpoch == active.membershipEpoch

    private fun ActiveRoom.sameConversation(other: ActiveRoom): Boolean =
        djangoRoomId == other.djangoRoomId &&
            matrixRoomId == other.matrixRoomId &&
            ownMatrixId == other.ownMatrixId &&
            partnerMatrixId == other.partnerMatrixId &&
            relationshipId == other.relationshipId &&
            membershipEpoch == other.membershipEpoch

    private fun observeLoginState(matrixClient: MatrixClient, stamp: SessionStamp) {
        loginJob?.cancel()
        loginJob = scope.launch {
            matrixClient.loginState.filterNotNull().distinctUntilChanged().collect { state ->
                if (state !in setOf(
                        MatrixClient.LoginState.LOGGED_OUT,
                        MatrixClient.LoginState.LOGGED_OUT_SOFT,
                        MatrixClient.LoginState.LOCKED,
                    )
                ) return@collect
                if (client === matrixClient) clientOutboxGate?.disableAndJoin()
                val active = activeRoom ?: return@collect
                if (client !== matrixClient || sessions.stamp() != stamp || active.stamp != stamp) return@collect
                if (recoveryJob?.isActive == true) return@collect
                val operation = active.operation
                recoveryJob = scope.launch {
                    try {
                        roomJob?.cancel()
                        roomJob = null
                        clientMutex.withLock {
                            if (client === matrixClient && activeRoom == active && roomOperation == operation) {
                                // A hard logout has already deleted Trixnity's Olm identity.
                                // Never reuse its Matrix device id with replacement keys.
                                val deleteScope = clientStorageScope.takeIf {
                                    state == MatrixClient.LoginState.LOGGED_OUT
                                }
                                shutdownClientLocked(logout = false, deleteScope = deleteScope)
                            }
                        }
                        if (sessions.stamp() == stamp && activeRoom == active && roomOperation == operation) {
                            _state.update { it.copy(availability = MatrixChatAvailability.Loading, composerError = null) }
                            openRoomNow(active.djangoRoomId, stamp, operation)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        clientOutboxGate?.disable()
                        cleanupBlocked = true
                        stamp.accountId?.let { runCatching { queueAccountWipe(it) } }
                        val cleaned = withContext(NonCancellable) { retryBlockedCleanup() }
                        cleanupBlocked = !cleaned
                        if (activeRoom == active && roomOperation == operation) {
                            activeRoom = null
                            _state.update {
                                it.copy(
                                    messages = emptyList(),
                                    availability = MatrixChatAvailability.Error(
                                        "Secure chat recovery could not finish safely. Please retry.",
                                        retryable = true,
                                    ),
                                    sending = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun shutdownClient(logout: Boolean, deleteScope: MatrixStorageScope?) = clientMutex.withLock {
        shutdownClientLocked(logout, deleteScope)
    }

    private suspend fun shutdownClientLocked(logout: Boolean, deleteScope: MatrixStorageScope?) {
        val old = client
        val oldGate = clientOutboxGate
        oldGate?.disable()
        loginJob?.cancel()
        loginJob = null
        oldGate?.disableAndJoin()
        var deferredCancellation: CancellationException? = null
        if (old != null) {
            if (logout) runCatching {
                withTimeout(CLIENT_NETWORK_SHUTDOWN_TIMEOUT) { old.logout().getOrThrow() }
            }
            deferredCancellation = closeClientAndAwait(old)
        }
        if (client === old) {
            client = null
            clientStamp = null
            clientOperation = null
            clientOutboxGate = null
        }
        if (deferredCancellation != null) {
            cleanupBlocked = true
            deleteScope?.accountId?.let { runCatching { queueAccountWipe(it) } }
            throw deferredCancellation
        }
        if (deleteScope != null) {
            try {
                platformStore.deleteAccountData(deleteScope)
                // Local crypto deletion invalidates the old Matrix device identity even
                // when the best-effort remote logout could not reach the homeserver.
                platformStore.rotateDeviceId(deleteScope)
            } catch (failure: Throwable) {
                cleanupBlocked = true
                runCatching { queueAccountWipe(deleteScope.accountId) }
                throw failure
            }
            if (clientStorageScope == deleteScope) clientStorageScope = null
        }
    }

    /**
     * [MatrixClient.close] cancels immediately but does not wait for store writers.
     * A timed-out suspending close therefore falls back to close and then joins the
     * client job before any protected files may be deleted or reused.
     */
    private suspend fun closeClientAndAwait(old: MatrixClient): CancellationException? {
        val clientJob = runCatching {
            old.di.get<CoroutineScope>().coroutineContext[Job]
        }.getOrNull()
        return try {
            withTimeout(CLIENT_NETWORK_SHUTDOWN_TIMEOUT) { old.closeSuspending() }
            null
        } catch (failure: Throwable) {
            val fallback = withContext(NonCancellable) {
                runCatching {
                    old.close()
                    val job = checkNotNull(clientJob) { "Matrix client shutdown job is unavailable." }
                    withTimeout(CLIENT_NETWORK_SHUTDOWN_TIMEOUT) { job.join() }
                    check(job.isCompleted) { "Matrix client shutdown did not complete." }
                }
            }
            fallback.exceptionOrNull()?.let(failure::addSuppressed)
            if (fallback.isFailure) {
                cleanupBlocked = true
                throw failure
            }
            failure.takeIf {
                it is CancellationException && it !is TimeoutCancellationException
            } as? CancellationException
        }
    }

    private suspend fun startSyncForOperation(matrixClient: MatrixClient, operation: Long): Boolean {
        if (operation != roomOperation || client !== matrixClient || clientOperation != operation) return false
        matrixClient.startSync()
        if (operation == roomOperation && client === matrixClient && clientOperation == operation) return true
        runCatching { matrixClient.stopSync() }
        return false
    }

    private suspend fun discardStaleClient(matrixClient: MatrixClient, operation: Long) {
        clientMutex.withLock {
            if (client === matrixClient && clientOperation == operation) {
                val staleGate = clientOutboxGate
                staleGate?.disable()
                loginJob?.cancel()
                loginJob = null
                staleGate?.disableAndJoin()
                runCatching { matrixClient.stopSync() }
                val stopped = withContext(NonCancellable) {
                    runCatching { closeClientAndAwait(matrixClient) }.isSuccess
                }
                if (stopped && client === matrixClient) {
                    client = null
                    clientStamp = null
                    clientOperation = null
                    clientOutboxGate = null
                } else if (!stopped) {
                    cleanupBlocked = true
                    clientStorageScope?.accountId?.let { runCatching { queueAccountWipe(it) } }
                }
            }
        }
    }

    private suspend fun awaitSecureRoom(
        matrixClient: MatrixClient,
        roomId: RoomId,
        ownMatrixId: String,
        partnerMatrixId: String,
    ) = withTimeout(SECURE_ROOM_TIMEOUT) {
        matrixClient.syncState.first { it == SyncState.RUNNING }
        val room = matrixClient.room.getById(roomId).filterNotNull().first()
        check(room.membership == Membership.JOIN && room.encrypted) { "Matrix room is not joined and encrypted." }
        val encryption = matrixClient.room.getState<EncryptionEventContent>(roomId).filterNotNull().first()
        check(encryption.content.algorithm == EncryptionAlgorithm.Megolm) { "Matrix room does not use Megolm." }
        matrixClient.user.loadMembers(roomId, wait = true)
        matrixClient.room.getById(roomId).filterNotNull().first { it.membersLoaded }
        val users = matrixClient.user.getAll(roomId).flattenNotNull(Duration.ZERO).first()
        val memberships = users.values.associate { it.userId.full to it.event.content.membership }
        check(coupleOnlyMembership(memberships, ownMatrixId, partnerMatrixId)) {
            "Matrix room membership is not couple-only."
        }
    }

    private suspend fun requireSecureRoomNow(matrixClient: MatrixClient, active: ActiveRoom) {
        val room = checkNotNull(matrixClient.room.getById(active.matrixRoomId).first())
        check(room.membership == Membership.JOIN && room.encrypted) { "Secure conversation is no longer available." }
        val encryption = checkNotNull(matrixClient.room.getState<EncryptionEventContent>(active.matrixRoomId).first())
        check(encryption.content.algorithm == EncryptionAlgorithm.Megolm) { "Secure conversation is no longer available." }
        val users = matrixClient.user.getAll(active.matrixRoomId).flattenNotNull(Duration.ZERO).first()
        val memberships = users.values.associate { it.userId.full to it.event.content.membership }
        check(coupleOnlyMembership(memberships, active.ownMatrixId, active.partnerMatrixId)) {
            "Secure conversation is no longer couple-only."
        }
    }

    private fun secureRoomState(matrixClient: MatrixClient, active: ActiveRoom): Flow<Boolean> = combine(
        matrixClient.room.getById(active.matrixRoomId),
        matrixClient.room.getState<EncryptionEventContent>(active.matrixRoomId),
        matrixClient.user.getAll(active.matrixRoomId).flattenNotNull(Duration.ZERO),
    ) { room, encryption, users ->
        val memberships = users.values.associate { it.userId.full to it.event.content.membership }
        room?.membership == Membership.JOIN &&
            room.encrypted &&
            encryption?.content?.algorithm == EncryptionAlgorithm.Megolm &&
            coupleOnlyMembership(memberships, active.ownMatrixId, active.partnerMatrixId)
    }.distinctUntilChanged()

    private fun RepositoriesModule.withVolatileAuthentication(
        auth: ClassicMatrixClientAuthProviderData,
    ): RepositoriesModule {
        val volatileAuthentication = InMemoryAuthenticationRepository().apply {
            content.value = mapOf(
                1L to Authentication(
                    providerId = "classic",
                    providerData = Json.encodeToString(
                        ClassicMatrixClientAuthProviderData.serializer(),
                        auth,
                    ),
                    logoutInfo = null,
                )
            )
        }
        val delegate = this
        return RepositoriesModule {
            val persistentRepositories = delegate.create()
            module {
                includes(
                    persistentRepositories,
                    module {
                        single<AuthenticationRepository> { volatileAuthentication }
                    },
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Flow<List<Flow<TimelineEvent>>>.resolveTimeline(): Flow<List<TimelineEvent>> =
        flatMapLatest { flows ->
            if (flows.isEmpty()) flowOf(emptyList())
            else combine(flows) { values -> values.toList() }
        }

    private data class RoomObservation(
        val state: MatrixChatRoomState,
        val secure: Boolean,
        val syncState: SyncState,
        val pendingTransactionIds: List<String>,
    )

    private data class ActiveRoom(
        val djangoRoomId: String,
        val matrixRoomId: RoomId,
        val ownMatrixId: String,
        val partnerMatrixId: String,
        val stamp: SessionStamp,
        val relationshipId: Long,
        val membershipEpoch: Long,
        val operation: Long,
    )

    private companion object {
        const val MATRIX_BOOTSTRAP_MAX_ATTEMPTS = 4
        const val TIMELINE_SIZE = 100
        val MATRIX_BOOTSTRAP_RETRY_DELAYS = listOf(1.seconds, 2.seconds, 4.seconds)
        val SECURE_ROOM_TIMEOUT = 30.seconds
        val TIMELINE_EVENT_TIMEOUT = 5.seconds
        val OUTBOX_CANCEL_TIMEOUT = 5.seconds
        val CLIENT_NETWORK_SHUTDOWN_TIMEOUT = 5.seconds
    }
}

internal enum class MatrixClientReuseDecision { REUSE, REAUTHENTICATE, REPLACE }

private fun NetworkAPIResult<*>.isRetryableMatrixBootstrap503(): Boolean =
    this is NetworkAPIResult.Error && failure.status == 503 && failure.retryable

internal fun matrixOutboxAllowed(
    secure: Boolean,
    syncState: SyncState,
    foreground: Boolean,
): Boolean = secure && syncState == SyncState.RUNNING && foreground

internal fun matrixClientReuseDecision(
    sameIdentity: Boolean,
    state: MatrixClient.LoginState?,
): MatrixClientReuseDecision = when {
    !sameIdentity -> MatrixClientReuseDecision.REPLACE
    state == MatrixClient.LoginState.LOGGED_IN -> MatrixClientReuseDecision.REUSE
    state == MatrixClient.LoginState.LOGGED_OUT -> MatrixClientReuseDecision.REPLACE
    else -> MatrixClientReuseDecision.REAUTHENTICATE
}

internal fun coupleOnlyMembership(
    memberships: Map<String, Membership>,
    ownMatrixId: String,
    partnerMatrixId: String,
): Boolean {
    if (ownMatrixId == partnerMatrixId) return false
    val activeMemberships = memberships.filterValues { it != Membership.LEAVE }
    return activeMemberships.keys == setOf(ownMatrixId, partnerMatrixId) &&
        activeMemberships.values.all { it == Membership.JOIN }
}
