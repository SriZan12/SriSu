package com.srisu.srisu.features.chat.matrix

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.media.mappings.EventContentMediaMappings
import de.connect2x.trixnity.client.room.OutboxMessageEventHandler
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.client.store.RoomOutboxMessageStore
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.user.UserService
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.UserInfo
import kotlin.time.Clock
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trixnity starts its outbox handler while constructing a client, independently
 * of sync. Keep its durable sender canceled until SriSu has freshly validated the
 * Django relationship and the Matrix room's encryption and membership state.
 */
internal class MatrixOutboxGate : SynchronizedObject() {
    private var hostScope: CoroutineScope? = null
    private var delegate: EventHandler? = null
    private var worker: Job? = null
    private val stopping = mutableSetOf<Job>()
    private var enabled = false

    fun bind(scope: CoroutineScope, handler: EventHandler) = synchronized(this) {
        check(hostScope == null && delegate == null) { "Matrix outbox gate was already bound." }
        hostScope = scope
        delegate = handler
        if (enabled) startLocked()
    }

    fun enable() = synchronized(this) {
        enabled = true
        if (worker?.isActive != true) startLocked()
    }

    /** Signals cancellation synchronously; use [disableAndJoin] before crossing a
     * lifecycle/security boundary that must observe the sender fully stopped.
     */
    fun disable() = synchronized(this) { disableLocked() }

    suspend fun disableAndJoin() {
        val jobs = synchronized(this) {
            disableLocked()
            stopping.toList()
        }
        jobs.joinAll()
        synchronized(this) { stopping.removeAll(jobs.toSet()) }
    }

    fun isEnabled(): Boolean = synchronized(this) { enabled }

    private fun disableLocked() {
        enabled = false
        worker?.let { active ->
            worker = null
            stopping += active
            active.cancel()
        }
    }

    private fun startLocked() {
        val scope = hostScope ?: return
        val handler = delegate ?: return
        if (!scope.isActive || !enabled || worker?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            coroutineScope {
                handler.startInCoroutineScope(this)
                awaitCancellation()
            }
        }
        worker = job
        job.invokeOnCompletion {
            synchronized(this) {
                if (worker === job) worker = null
                stopping -= job
            }
        }
        job.start()
    }
}

internal class GatedMatrixOutboxEventHandler(
    private val gate: MatrixOutboxGate,
    private val delegate: EventHandler,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) = gate.bind(scope, delegate)
}

/**
 * Runs the room's continuous policy observation with the outbox as a strict
 * child resource. Cancellation, normal completion, and failures all return
 * only after the sender worker has been cancelled and joined.
 */
internal suspend fun <T> collectWithOutboxGateFailClosed(
    gate: MatrixOutboxGate,
    source: Flow<T>,
    onEach: suspend (T) -> Unit,
    onUnexpectedTermination: suspend (Throwable?) -> Unit,
) {
    var unexpectedTermination = false
    var failure: Throwable? = null
    try {
        source.collect(onEach)
        unexpectedTermination = true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (caught: Exception) {
        unexpectedTermination = true
        failure = caught
    } finally {
        withContext(NonCancellable) {
            gate.disable()
            gate.disableAndJoin()
            if (unexpectedTermination) onUnexpectedTermination(failure)
        }
    }
}

internal fun createGatedMatrixOutboxEventHandler(
    gate: MatrixOutboxGate,
    config: MatrixClientConfiguration,
    api: MatrixClientServerApiClient,
    roomStore: RoomStore,
    roomEventEncryptionServices: List<RoomEventEncryptionService>,
    userService: UserService,
    mediaService: MediaService,
    roomOutboxMessageStore: RoomOutboxMessageStore,
    eventContentMediaMappings: EventContentMediaMappings,
    currentSyncState: CurrentSyncState,
    userInfo: UserInfo,
    transactionManager: StoreTransactionManager,
    clock: Clock,
): EventHandler = GatedMatrixOutboxEventHandler(
    gate = gate,
    delegate = OutboxMessageEventHandler(
        config = config,
        api = api,
        roomStore = roomStore,
        roomEventEncryptionServices = roomEventEncryptionServices,
        userService = userService,
        mediaService = mediaService,
        roomOutboxMessageStore = roomOutboxMessageStore,
        eventContentMediaMappings = eventContentMediaMappings,
        currentSyncState = currentSyncState,
        userInfo = userInfo,
        tm = transactionManager,
        clock = clock,
    ),
)
