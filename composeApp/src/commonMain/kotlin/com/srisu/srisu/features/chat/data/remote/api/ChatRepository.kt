package com.srisu.srisu.features.chat.data.remote.api

import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.features.chat.data.remote.dto.ChatMessage
import com.srisu.srisu.features.chat.data.remote.response.*
import com.srisu.srisu.features.chat.data.remote.websocket.*
import com.srisu.srisu.utils.Constants
import com.srisu.srisu.utils.MediaFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Account-owned in-memory projection. HTTP snapshots are authoritative; socket events invalidate it. */
class ChatRepository(
    private val webSocketClient: ChatWebSocketClient,
    private val chatApiService: ChatApiService,
    private val sessions: SessionCoordinator,
    lifetime: ApplicationLifetime,
) {
    private val scope = CoroutineScope(lifetime.scope.coroutineContext + SupervisorJob(lifetime.scope.coroutineContext[Job]))
    private val activeRoom = MutableStateFlow<String?>(null)
    val activeChatRoomId = activeRoom.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _chatRooms = MutableStateFlow<List<ChatRoomItemDto>>(emptyList())
    val chatRoomsList = _chatRooms.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val connectionState = webSocketClient.connectionState
    private var messageCursor: Long? = null
    private var moreMessages = false
    private var roomCursor: String? = null
    private var roomGeneration = 0L
    private var historyJob: Job? = null
    private var roomsJob: Job? = null
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private var connected = false

    init {
        scope.launch {
            sessions.state.collect {
                historyJob?.cancel(); roomsJob?.cancel()
                roomGeneration++
                activeRoom.value = null
                _messages.value = emptyList(); _chatRooms.value = emptyList(); _error.value = null
                messageCursor = null; roomCursor = null; moreMessages = false
            }
        }
        scope.launch {
            webSocketClient.events.collect { scoped ->
                if (scoped.session != sessions.stamp()) return@collect
                when (val event = scoped.event) {
                    ChatWebSocketEvent.Connected -> {
                        refreshes.trySend(Unit)
                        activeRoom.value?.let { room -> subscribe(room) }
                    }
                    is ChatWebSocketEvent.Disconnected -> {
                        historyJob?.cancel(); roomsJob?.cancel(); roomGeneration++
                        _messages.value = emptyList(); _chatRooms.value = emptyList()
                    }
                    is ChatWebSocketEvent.AccessRevoked -> {
                        _chatRooms.update { it.filterNot { room -> room.id == event.roomId } }
                        if (activeRoom.value == event.roomId) clearActiveChatRoom()
                    }
                    is ChatWebSocketEvent.MessageTyping -> _chatRooms.update { rooms -> rooms.map { room ->
                        if (room.id == event.data.chatRoomId) room.copy(isTyping = event.data.typingUsers) else room
                    } }
                    is ChatWebSocketEvent.FetchMessages, is ChatWebSocketEvent.GetChatRooms -> Unit // Legacy subscription acknowledgment, not an authoritative snapshot.
                    is ChatWebSocketEvent.Error -> {
                        _error.value = event.throwable.message
                        refreshes.trySend(Unit)
                    }
                    else -> refreshes.trySend(Unit) // Duplicates/out-of-order changes converge through HTTP.
                }
            }
        }
        scope.launch {
            for (ignored in refreshes) {
                delay(100) // Coalesce bursts into bounded reads, not one request per receipt/event.
                if (connected && sessions.stamp().accountId != null) {
                    fetchInitialChatRooms()
                    activeRoom.value?.let { refreshHistory(it) }
                }
            }
        }
        scope.launch {
            connectionState.collect { state ->
                if (state is SocketState.Terminal) {
                    if (state.code in setOf("unauthenticated", "forbidden")) {
                        historyJob?.cancel(); roomsJob?.cancel(); roomGeneration++
                        _messages.value = emptyList(); _chatRooms.value = emptyList(); activeRoom.value = null
                    }
                    _error.value = if (state.code == "unauthenticated") "Sign in to reconnect to chat." else "Chat is disconnected. Retry when connectivity is restored."
                }
            }
        }
        scope.launch {
            lifetime.foreground.collectLatest { foreground ->
                if (foreground) while (isActive) {
                    if (connected) refreshes.trySend(Unit)
                    delay(30_000) // Repair a lost final publication even when the socket stays connected.
                }
            }
        }
    }

    fun connect() { connected = true; webSocketClient.connect(); refreshes.trySend(Unit) }
    fun disconnect(reason: String? = null) { connected = false; webSocketClient.disconnect(reason); historyJob?.cancel(); roomsJob?.cancel() }
    fun close() { scope.cancel() }
    fun clearError() { _error.value = null }
    fun retry() { webSocketClient.retryConnection(); refreshes.trySend(Unit) }

    fun setActiveChatRoom(chatRoomId: String?) {
        if (activeRoom.value == chatRoomId) return
        val previous = activeRoom.value
        historyJob?.cancel(); roomGeneration++
        activeRoom.value = chatRoomId; _messages.value = emptyList(); messageCursor = null; moreMessages = false
        previous?.let { scope.launch { try { webSocketClient.unsubscribe(it) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {} } }
    }
    fun clearActiveChatRoom() = setActiveChatRoom(null)

    suspend fun fetchInitialMessages(chatRoomId: String) = withContext(scope.coroutineContext.minusKey(Job)) {
        setActiveChatRoom(chatRoomId)
        subscribe(chatRoomId)
        refreshHistory(chatRoomId)
    }
    private fun subscribe(room: String) { scope.launch {
        try { webSocketClient.fetchMessages(room) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* HTTP reads remain usable during reconnection. */ }
    } }

    private fun refreshHistory(room: String, older: Boolean = false) {
        historyJob?.cancel()
        val generation = ++roomGeneration
        val stamp = sessions.stamp()
        val cursor = if (older) messageCursor else null
        historyJob = scope.launch {
            val result = chatApiService.history(room, cursor).result
            sessions.ensureCurrent(stamp)
            if (roomGeneration != generation || activeRoom.value != room) return@launch
            when (result) {
                is NetworkAPIResult.Success -> result.response?.let { page ->
                    if (page.chatRoomId != room || page.messages.any { it.chatRoomId != room }) {
                        _error.value = "The service returned an unexpected conversation."
                        return@launch
                    }
                    _messages.value = (if (older) _messages.value + page.messages else page.messages).distinctBy { it.id }.sortedByDescending { it.id }
                    messageCursor = page.nextCursor; moreMessages = page.hasMore; _error.value = null
                } ?: run { _error.value = "The service returned an empty conversation response." }
                is NetworkAPIResult.Error -> {
                    _error.value = result.failure.message
                    if (result.failure.kind in setOf(NetworkAPIResult.ErrorType.UNAUTHORIZED, NetworkAPIResult.ErrorType.FORBIDDEN, NetworkAPIResult.ErrorType.NOT_FOUND)) {
                        _messages.value = emptyList()
                        _chatRooms.update { it.filterNot { item -> item.id == room } }
                        clearActiveChatRoom()
                    }
                }
            }
        }
    }
    fun fetchOlderMessages(chatRoomId: String) {
        if (activeRoom.value == chatRoomId && moreMessages && historyJob?.isActive != true) refreshHistory(chatRoomId, older = true)
    }
    suspend fun fetchInitialChatRooms() { refreshRooms(null) }
    fun fetchOlderChatRooms() { if (roomsJob?.isActive != true) roomCursor?.let(::refreshRooms) }
    private fun refreshRooms(cursor: String?) {
        roomsJob?.cancel()
        val stamp = sessions.stamp()
        roomsJob = scope.launch {
            when (val result = chatApiService.rooms(cursor).result) {
                is NetworkAPIResult.Success -> {
                    sessions.ensureCurrent(stamp)
                    result.response?.let { page ->
                        _chatRooms.value = (if (cursor == null) page.chatRooms else _chatRooms.value + page.chatRooms).distinctBy { it.id }
                        roomCursor = page.nextCursor
                        // Paginated absence is not evidence of revocation; room HTTP authorization decides that.
                    }
                }
                is NetworkAPIResult.Error -> {
                    sessions.ensureCurrent(stamp); _error.value = result.failure.message
                    if (result.failure.kind in setOf(NetworkAPIResult.ErrorType.UNAUTHORIZED, NetworkAPIResult.ErrorType.FORBIDDEN)) {
                        _chatRooms.value = emptyList(); clearActiveChatRoom()
                    }
                }
            }
        }
    }

    suspend fun sendMessage(
        chatRoomId: String,
        text: String? = null,
        messageType: String = Constants.ChatConstants.TEXT,
        mediaIds: List<Long> = emptyList(),
        replyToId: Long? = null,
        mediaUrl: String? = null,
        stickerUrl: String? = null,
    ) {
        webSocketClient.sendMessage(
            chatRoomId = chatRoomId,
            text = text,
            messageType = messageType,
            mediaIds = mediaIds,
            replyToId = replyToId,
            mediaUrl = mediaUrl,
            stickerUrl = stickerUrl,
        )
    }

    suspend fun editMessage(
        messageId: Long,
        text: String,
    ) {
        webSocketClient.editMessage(
            messageId = messageId,
            text = text,
        )
    }

    suspend fun deleteMessage(
        messageId: Long,
        deleteOption: String,
    ) {
        val activeRoomId = activeRoom.value

        webSocketClient.deleteMessage(
            messageId = messageId,
            deleteOption = deleteOption,
        )

        if (deleteOption == Constants.ChatConstants.DELETE_FOR_ME && activeRoomId != null) {
            removeMessageLocally(
                chatRoomId = activeRoomId,
                messageId = messageId,
            )
        }
    }

    suspend fun markRead(chatRoomId: String) {
        webSocketClient.markRead(chatRoomId)
    }

    suspend fun markDelivered(chatRoomId: String) {
        webSocketClient.markDelivered(chatRoomId)
    }

    suspend fun reactToMessage(
        messageId: Long,
        reaction: String,
    ) {
        webSocketClient.reactToMessage(
            messageId = messageId,
            reaction = reaction,
        )
    }

    suspend fun setTyping(
        chatRoomId: String,
        isTyping: Boolean,
    ) {
        webSocketClient.setTyping(
            chatRoomId = chatRoomId,
            isTyping = isTyping,
        )
    }

    suspend fun uploadMedias(
        medias: List<MediaFile?>?,
    ): ResultHandler<ChatMediaResponse?> {
        return chatApiService.uploadMedias(medias = medias)
    }

    fun addLocalMessage(message: ChatMessage) {
        if (message.chatRoomId == activeRoom.value && message.id != null) _messages.update { listOf(message) + it.filterNot { old -> old.id == message.id } }
    }
    fun clearMessagesForRoom(chatRoomId: String) { if (activeRoom.value == chatRoomId) _messages.value = emptyList() }
    private fun removeMessageLocally(chatRoomId: String, messageId: Long) {
        if (activeRoom.value == chatRoomId) _messages.update { it.filterNot { message -> message.id == messageId } }
    }

}
