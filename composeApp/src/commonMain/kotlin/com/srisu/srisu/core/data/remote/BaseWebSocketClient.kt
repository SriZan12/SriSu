package com.srisu.srisu.core.data.remote

import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

val SocketHandshakeKey = io.ktor.util.AttributeKey<Boolean>("SriSuSocketHandshake")
class SocketProtocolFailure : Exception("Socket protocol limit exceeded")
class SocketAccessDenied(val status: Int) : Exception("Socket access denied")

sealed interface SocketState {
    data object Disconnected : SocketState
    data object Connecting : SocketState
    data object Connected : SocketState
    data class Reconnecting(val attempt: Int) : SocketState
    data class Terminal(val code: String) : SocketState
}

interface SocketConnection {
    suspend fun receive(): String?
    suspend fun send(text: String)
    suspend fun close()
    suspend fun closeCode(): Int?
}
fun interface SocketConnector { suspend fun open(): SocketConnection }

class KtorSocketConnector(private val client: HttpClient, private val environment: ApiEnvironment) : SocketConnector {
    override suspend fun open(): SocketConnection {
        val socket = client.webSocketSession { url(environment.socketUrl); attributes.put(SocketHandshakeKey, true) }
        return object : SocketConnection {
            override suspend fun receive(): String? {
                while (true) {
                    val frame = socket.incoming.receiveCatching().getOrNull() ?: return null
                    if (frame.data.size > 1_048_576) {
                        socket.close(CloseReason(CloseReason.Codes.TOO_BIG, "Frame exceeds client limit"))
                        throw SocketProtocolFailure()
                    }
                    if (frame is Frame.Text) return frame.readText()
                    if (frame is Frame.Close) return null
                }
            }
            override suspend fun send(text: String) { socket.send(Frame.Text(text)) }
            override suspend fun close() { socket.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed")); socket.cancel() }
            override suspend fun closeCode(): Int? = socket.closeReason.await()?.code?.toInt()
        }
    }
}

class SocketUnavailable : Exception("Chat is disconnected. Reconnect before sending.")

/** One owned connection, no offline write queue, and no automatic command replay. */
abstract class BaseWebSocketClient(
    private val connector: SocketConnector,
    protected val sessions: SessionCoordinator,
    lifetime: ApplicationLifetime,
    private val jitter: () -> Double = { Random.nextDouble(0.8, 1.2) },
) {
    protected val scope = CoroutineScope(lifetime.scope.coroutineContext + SupervisorJob(lifetime.scope.coroutineContext[Job]))
    private val wanted = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)
    private val _state = MutableStateFlow<SocketState>(SocketState.Disconnected)
    val connectionState = _state.asStateFlow()
    private var connection: SocketConnection? = null
    private var connectionStamp: SessionStamp? = null
    private val sends = Mutex()

    init {
        scope.launch {
            combine(sessions.state, lifetime.foreground, wanted, retry) { session, foreground, desired, revision ->
                Triple(session, foreground && desired && session.accountId != null, revision)
            }.collectLatest { (stamp, enabled, _) ->
                if (!enabled) { _state.value = SocketState.Disconnected; return@collectLatest }
                runConnection(stamp)
            }
        }
    }

    fun connect() { wanted.value = true }
    fun retryConnection() { retry.value += 1 }
    fun disconnect(reason: String? = null) { wanted.value = false }
    fun close() { scope.cancel() }

    private suspend fun runConnection(stamp: SessionStamp) {
        try {
            for (attempt in 0..MAX_RECONNECTS) {
                sessions.ensureCurrent(stamp)
                _state.value = if (attempt == 0) SocketState.Connecting else SocketState.Reconnecting(attempt)
                if (attempt > 0) delay(backoffMillis(attempt, jitter()))
                var current: SocketConnection? = null
                try {
                    current = withTimeout(10_000) { connector.open() }
                    sessions.ensureCurrent(stamp)
                    connection = current
                    connectionStamp = stamp
                    _state.value = SocketState.Connected
                    com.srisu.srisu.core.logger.AppLogger.socket(com.srisu.srisu.core.logger.SocketDiagnostic.CONNECTED)
                    onConnected(stamp)
                    while (currentCoroutineContext().isActive) {
                        val raw = current.receive() ?: break
                        sessions.ensureCurrent(stamp)
                        onIncoming(raw, stamp)
                    }
                    val code = withTimeoutOrNull(1_000) { current.closeCode() }
                    if (code in listOf(4401, 4403, 1008)) {
                        _state.value = SocketState.Terminal(if (code == 4401) "unauthenticated" else "forbidden")
                        return
                    }
                } catch (cancelled: CancellationException) {
                    // Our connection timeout is recoverable; owner cancellation always propagates.
                    if (cancelled !is TimeoutCancellationException) throw cancelled
                    currentCoroutineContext().ensureActive()
                } catch (_: SocketProtocolFailure) {
                    _state.value = SocketState.Terminal("protocol_failure")
                    return
                } catch (denied: SocketAccessDenied) {
                    _state.value = SocketState.Terminal(if (denied.status == 401) "unauthenticated" else "forbidden")
                    return
                } catch (_: Exception) {
                    // No exception text: engines may include the authenticated request URL.
                } finally {
                    com.srisu.srisu.core.logger.AppLogger.socket(com.srisu.srisu.core.logger.SocketDiagnostic.DISCONNECTED)
                    connection = null
                    connectionStamp = null
                    onDisconnected(stamp)
                    withContext(NonCancellable) { withTimeoutOrNull(1_000) { current?.close() } }
                }
            }
            _state.value = SocketState.Terminal("reconnect_exhausted")
        } finally {
            if (_state.value !is SocketState.Terminal) _state.value = SocketState.Disconnected
        }
    }

    protected suspend fun send(rawPayload: String) = sends.withLock {
        require(rawPayload.encodeToByteArray().size <= 65_536) { "Command is too large" }
        val current = connection ?: throw SocketUnavailable()
        val stamp = connectionStamp ?: throw SocketUnavailable()
        sessions.ensureCurrent(stamp)
        // An accepted write is not an acknowledgment; the feature protocol handles that.
        withTimeout(2_000) { current.send(rawPayload) }
        sessions.ensureCurrent(stamp)
    }

    protected abstract suspend fun onIncoming(raw: String, stamp: SessionStamp)
    protected open suspend fun onConnected(stamp: SessionStamp) {}
    protected open suspend fun onDisconnected(stamp: SessionStamp) {}

    companion object {
        const val MAX_RECONNECTS = 5
        fun backoffMillis(attempt: Int, jitter: Double): Long =
            ((250L * (1L shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(8_000) * jitter.coerceIn(0.8, 1.2)).toLong()
    }
}
