package com.srisu.srisu.core.data.repository.chat


import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.websocket.chat.ChatEvent
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
import com.srisu.srisu.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.let

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageMap =
        MutableStateFlow<LinkedHashMap<Int, ChatMessage>>(linkedMapOf())

    val messages: StateFlow<List<ChatMessage>> =
        messageMap
            .map { it.values.toList() }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {

        webSocketClient.connect(roomId = "7fe512b9-548b-4a21-93cd-0a25d1aed5b4")

        scope.launch {
            webSocketClient.events.collect { event ->
                when (event) {
                    is ChatEvent.Error ->
                        _error.value = event.throwable.message

                    else -> applyEvent(event)
                }
            }
        }
    }

    private fun applyEvent(event: ChatEvent) {
        messageMap.update { old ->
            var newMap = LinkedHashMap(old)

            when (event) {
                is ChatEvent.FetchMessages -> {
                    newMap.clear()
                    event.messages?.forEach {
                        it?.id?.let { id -> newMap[id] = it }
                    }
                }

                is ChatEvent.SendMessage -> {
                    AppLogger.log("New Message sent = ${event.message}")
                    event.message.id?.let { id ->
                        newMap = linkedMapOf(id to event.message).apply {
                            putAll(newMap)
                        }
                    }
                }


                is ChatEvent.MessageEdited -> {
                    AppLogger.log("Message edited = ${event.message}")
                    event.message.id?.let { newMap[it] = event.message }
                }

                is ChatEvent.MessageDeleted ->
                    newMap.remove(event.messageId)

                else -> Unit
            }
            newMap
        }
    }

    suspend fun sendRequest(chatMessage: ChatMessage?) {
        val payload = Json.encodeToString(value = chatMessage)
        webSocketClient.send(rawPayload = payload)
    }

    fun clearError() {
        _error.value = null
    }
}
