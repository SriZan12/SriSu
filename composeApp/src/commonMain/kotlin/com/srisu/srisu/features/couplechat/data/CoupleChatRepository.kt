@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.srisu.srisu.features.couplechat.data

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.time.Clock

data class PartnerChatActivity(val roomId: String? = null, val online: Boolean = false, val typing: Boolean = false)
private data class OwnChatActivity(val room: String, val device: String, val visible: Boolean, val typingUntil: Long)

sealed interface CoupleChatState {
    data object Loading : CoupleChatState
    data object NotConnected : CoupleChatState
    data class SetupRequired(val room: CoupleChatRoom, val devices: ChatDevices? = null) : CoupleChatState
    data class Unavailable(val message: String, val retryable: Boolean) : CoupleChatState
}

/** Owns only room/setup metadata. Private history must come from protected storage. */
class CoupleChatRepository(
    private val api: CoupleChatApi,
    connector: SocketConnector,
    private val sessions: SessionCoordinator,
    lifetime: ApplicationLifetime,
) {
    private val scope = CoroutineScope(lifetime.scope.coroutineContext + SupervisorJob(lifetime.scope.coroutineContext[Job]))
    private val active = MutableStateFlow(false)
    private var owners = 0
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private val _state = MutableStateFlow<CoupleChatState>(CoupleChatState.Loading)
    val state = _state.asStateFlow()
    private val _activity = MutableStateFlow(PartnerChatActivity())
    val activity = _activity.asStateFlow()
    private val ownActivity = MutableStateFlow<OwnChatActivity?>(null)
    private var expiryJob: Job? = null
    private val socket = object : BaseWebSocketClient(connector, sessions, lifetime) {
        suspend fun publish(value: OwnChatActivity, typing: Boolean) = send(buildJsonObject {
            put("version",1);put("type","activity");put("room_id",value.room);put("device_id",value.device)
            put("online",value.visible);put("typing",value.visible && typing)
        }.toString())
        override suspend fun onDisconnected(stamp: SessionStamp) { expiryJob?.cancel();_activity.value=PartnerChatActivity() }
        override suspend fun onIncoming(raw: String, stamp: SessionStamp) {
            if (raw.length > 512) throw SocketProtocolFailure()
            val event = try { ApiJson.parseToJsonElement(raw).jsonObject }
                catch (_: kotlinx.serialization.SerializationException) { throw SocketProtocolFailure() }
            if (event["version"]?.jsonPrimitive?.intOrNull != 1) throw SocketProtocolFailure()
            sessions.ensureCurrent(stamp)
            when(event["type"]?.jsonPrimitive?.content) {
                "state.changed" -> refreshes.trySend(Unit)
                "partner.activity" -> {
                    val room=event["room_id"]?.jsonPrimitive?.content ?: throw SocketProtocolFailure()
                    val online=event["online"]?.jsonPrimitive?.booleanOrNull ?: throw SocketProtocolFailure()
                    val typing=event["typing"]?.jsonPrimitive?.booleanOrNull ?: throw SocketProtocolFailure()
                    val expiry=event["expires_in_ms"]?.jsonPrimitive?.longOrNull ?: throw SocketProtocolFailure()
                    val typingExpiry=event["typing_expires_in_ms"]?.jsonPrimitive?.longOrNull ?: throw SocketProtocolFailure()
                    if(expiry !in 0..25000 || typingExpiry !in 0..5000 || typingExpiry>expiry)throw SocketProtocolFailure()
                    expiryJob?.cancel();_activity.value=PartnerChatActivity(room,online && expiry>0,typing && typingExpiry>0)
                    expiryJob=scope.launch {
                        delay(typingExpiry);_activity.update {it.copy(typing=false)}
                        delay(expiry-typingExpiry);_activity.value=PartnerChatActivity()
                    }
                }
                else -> throw SocketProtocolFailure()
            }
        }
    }

    init {
        scope.launch {
            combine(sessions.state,lifetime.foreground,active,ownActivity,socket.connectionState) {stamp,foreground,wanted,own,connection ->
                if(stamp.accountId!=null && foreground && wanted && connection==SocketState.Connected) own else null
            }.collectLatest { own ->
                if(own==null)return@collectLatest
                var previous: Boolean?=null;var last=0L
                while(isActive) {
                    val now=Clock.System.now().toEpochMilliseconds();val typing=own.visible && now<own.typingUntil
                    if(previous!=typing || now-last>=20000) {
                        try {socket.publish(own,typing);last=now;previous=typing}
                        catch(cancelled: CancellationException) {throw cancelled}
                        catch(_: SocketUnavailable) {break}
                        catch(_: Exception) {break} // Ephemeral hints are never queued/replayed.
                    }
                    delay(1000)
                }
            }
        }
        scope.launch {
            socket.connectionState.collectLatest { connection ->
                if(connection==SocketState.Terminal("reconnect_exhausted")) {delay(30000);if(active.value && lifetime.foreground.value)socket.retryConnection()}
            }
        }
        scope.launch {
            combine(sessions.state, lifetime.foreground, active) { stamp, foreground, wanted -> stamp to (foreground && wanted) }
                .collectLatest { (stamp, enabled) ->
                    _state.value = CoupleChatState.Loading
                    socket.disconnect()
                    if (!enabled || stamp.accountId == null) return@collectLatest
                    socket.connect()
                    refreshes.trySend(Unit)
                    try {
                        for (ignored in refreshes) {
                            when (val result = api.snapshot().result) {
                                is NetworkAPIResult.Success -> {
                                    sessions.ensureCurrent(stamp)
                                    val snapshot = result.response
                                    val room = snapshot?.room
                                    _state.value = when {
                                        snapshot == null || snapshot.contract != "couple-chat-1" -> CoupleChatState.Unavailable("Chat returned an unexpected response.", true)
                                        room == null -> CoupleChatState.NotConnected
                                        room.participantIds.size != 2 || stamp.accountId !in room.participantIds -> CoupleChatState.Unavailable("This conversation is unavailable.", false)
                                        // A future server capability alone cannot enable unfinished client cryptography.
                                        else -> CoupleChatState.SetupRequired(room, snapshot.devices)
                                    }
                                }
                                is NetworkAPIResult.Error -> {
                                    sessions.ensureCurrent(stamp)
                                    _state.value = CoupleChatState.Unavailable(result.error, result.failure.retryable)
                                }
                            }
                        }
                    } finally { socket.disconnect() }
                }
        }
    }

    fun enter() { owners += 1; active.value = true }
    fun leave() { owners = (owners - 1).coerceAtLeast(0); active.value = owners > 0;if(owners==0)ownActivity.value=null }
    fun activity(room: String, device: String, visible: Boolean, typing: Boolean? = null) {
        val old=ownActivity.value
        val now=Clock.System.now().toEpochMilliseconds()
        // At most one status change per second while typing. The heartbeat loop
        // sends an automatic stop after the last keystroke expires.
        if(typing==true && old?.room==room && old.visible==visible && old.typingUntil-now>3000)return
        ownActivity.value=OwnChatActivity(room,device,visible,when(typing) {true->now+4500;false->0;null->if(old?.room==room)old.typingUntil else 0})
    }
    fun stopActivity() {ownActivity.value=ownActivity.value?.copy(visible=false,typingUntil=0);_activity.value=PartnerChatActivity()}
    fun refresh() { refreshes.trySend(Unit); socket.retryConnection() }
    fun close() { socket.close(); scope.cancel(); refreshes.close() }
}
