package com.srisu.srisu.features.chat.matrix

import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.TypingEventContent
import de.connect2x.trixnity.core.model.events.roomIdOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

internal data class MatrixPartnerActivity(
    val presence: MatrixPartnerPresence = MatrixPartnerPresence.UNKNOWN,
    val typing: Boolean = false,
)

internal fun Presence?.toPartnerPresence(): MatrixPartnerPresence = when (this) {
    Presence.ONLINE -> MatrixPartnerPresence.ONLINE
    Presence.OFFLINE -> MatrixPartnerPresence.OFFLINE
    Presence.UNAVAILABLE -> MatrixPartnerPresence.AWAY
    else -> MatrixPartnerPresence.UNKNOWN
}

/** Installed before sync begins, so opening a room retains its initial typing snapshot.
 * Unlike the SDK cache, these transient sets are erased on disconnection/background. */
internal fun trackMatrixTyping(
    scope: CoroutineScope,
    events: Flow<ClientEvent<TypingEventContent>>,
    sync: StateFlow<SyncState>,
    foreground: StateFlow<Boolean>,
): StateFlow<Map<RoomId, Set<UserId>>> {
    val users = MutableStateFlow<Map<RoomId, Set<UserId>>>(emptyMap())
    fun accepting() = foreground.value && sync.value in setOf(SyncState.STARTED, SyncState.INITIAL_SYNC, SyncState.RUNNING)
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        combine(sync, foreground) { _, _ -> accepting() }.collect { if (!it) users.value = emptyMap() }
    }
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        events.collect { event ->
            val room = event.roomIdOrNull
            if (room != null && accepting()) users.update { it + (room to event.content.users) }
        }
    }
    return users.asStateFlow()
}

internal fun partnerActivity(
    room: RoomId,
    partner: UserId,
    presence: Flow<MatrixPartnerPresence>,
    typing: Flow<Map<RoomId, Set<UserId>>>,
    sync: Flow<SyncState>,
    foreground: Flow<Boolean>,
): Flow<MatrixPartnerActivity> = combine(presence, typing, sync, foreground) { status, users, state, visible ->
    if (state == SyncState.RUNNING && visible) MatrixPartnerActivity(status, partner in users[room].orEmpty())
    else MatrixPartnerActivity()
}.distinctUntilChanged()

/**
 * Keystrokes only refresh an idle timer. Network signals are serialized, bounded and
 * refreshed while editing; draft text never enters this publisher. A failed stop is
 * bounded by the homeserver's 10-second typing lease.
 */
internal class MatrixTypingPublisher<T : Any>(
    private val scope: CoroutineScope,
    private val isCurrent: (T) -> Boolean,
    private val publish: suspend (T, Boolean) -> Unit,
) {
    private data class Signal<T>(val target: T, val typing: Boolean)
    private val signals = Channel<Signal<T>>(Channel.UNLIMITED)
    private var target: T? = null
    private var refresh: Job? = null
    private var idle: Job? = null

    init {
        scope.launch {
            for (signal in signals) {
                if (signal.typing && (target != signal.target || !isCurrent(signal.target))) continue
                try {
                    withTimeout(2_000) { publish(signal.target, signal.typing) }
                } catch (_: TimeoutCancellationException) {
                    // Ephemeral activity never turns a valid message into a send error.
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* The next refresh may succeed; the server lease expires. */ }
            }
        }
    }

    fun activity(next: T) {
        if (!isCurrent(next)) { stop(); return }
        if (target != next) {
            stop()
            target = next
            refresh = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                while (isActive && target == next) {
                    if (!isCurrent(next)) { stop(); break }
                    signals.trySend(Signal(next, true))
                    delay(3_000)
                }
            }
        }
        idle?.cancel()
        idle = scope.launch { delay(5_000); stop() }
    }

    fun stop() {
        refresh?.cancel(); refresh = null
        idle?.cancel(); idle = null
        target?.let { signals.trySend(Signal(it, false)) }
        target = null
    }
}
