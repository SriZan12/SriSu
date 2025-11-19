package com.srisu.srisu.core.data.repository.chat


// ChatRepository.kt
import com.srisu.srisu.core.data.response.chat.ChatMessage
import com.srisu.srisu.core.data.websocket.ChatWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class ChatRepository(
    private val socketClient: ChatWebSocketClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json: Json = Json { ignoreUnknownKeys = true }

    // Expose incoming messages as parsed domain objects
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // For one-time events like sent ACKs or errors
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun start() {
        socketClient.start()
        scope.launch {
            socketClient.incomingMessages.collectLatest { raw ->
                // Server may wrap real msg under {"action": "...","data": {...}} or {"message":"...", "data": {...}}
                try {
                    // Try to decode as a map to inspect keys
                    val jsonElement = json.parseToJsonElement(raw)
                    val root = jsonElement.jsonObject

                    // When server sends {"data": serialized_message, "message": "..."} or {"action":"send_message","data":{...}}
                    if (root["data"] != null) {
                        val dataElem = root["data"]!!
                        // data could be list or object
                        if (dataElem is kotlinx.serialization.json.JsonArray) {
                            // list of messages
                            val list = dataElem.map { json.decodeFromJsonElement<ChatMessage>(it) }
                            _messages.update { old -> (old + list).sortedBy { it.timestamp } }
                        } else {
                            val msg = json.decodeFromJsonElement<ChatMessage>(dataElem)
                            _messages.update { old -> (old + msg).sortedBy { it.timestamp } }
                        }
                    } else {
                        // fallback: try to decode raw to ChatMessage
                        val maybe = runCatching { json.decodeFromString<ChatMessage>(raw) }.getOrNull()
                        if (maybe != null) {
                            _messages.update { old -> (old + maybe).sortedBy { it.timestamp } }
                        } else {
                            _events.emit("unhandled_payload")
                        }
                    }
                } catch (e: Exception) {
                    // ignore or emit error
                }
            }
        }
    }

    fun stop() {
        socketClient.stop()
        scope.cancel()
    }

    suspend fun sendMessage(meId: Int,peerId: Int, text: String) {
        val msg = ChatMessage(
            id = null,
            sender= meId,
            receiver = peerId,
            text = text,
            couple = 0
        )

        // optimistic add locally
        _messages.update { old -> (old + msg).sortedBy { it.timestamp } }

        // send over socket
        socketClient.sendChatMessage(msg)
    }
}
