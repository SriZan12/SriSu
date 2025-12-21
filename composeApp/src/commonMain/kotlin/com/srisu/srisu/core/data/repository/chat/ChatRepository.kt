package com.srisu.srisu.core.data.repository.chat

import com.srisu.srisu.core.data.dto.chatdto.ChatMessage
import com.srisu.srisu.core.data.response.chat.FetchMessageResponse
import com.srisu.srisu.core.data.websocket.chat.ChatWebSocketClient
import com.srisu.srisu.core.data.websocket.chat.ConnectionState
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.filter
import kotlin.collections.forEach

class ChatRepository(
    private val webSocketClient: ChatWebSocketClient
) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _messages =
        MutableStateFlow<List<ChatMessage?>?>(emptyList())

    val messages: StateFlow<List<ChatMessage?>?> =
        _messages.asStateFlow()

    private var collectJob: Job? = null

    //-------------------------
    // LifeCycle Management
    //-------------------------

    /**
     * Start WebSocket connection and begin collecting messages
     * */

    fun start() {
        //prevent double start

        if (collectJob?.isActive == true) {
            AppLogger.log("Repository already Collectin messages")
            return
        }

        webSocketClient.connect()

        collectJob = repoScope.launch {
            try {
                webSocketClient.chatMessages.collect { incoming ->
                    try {
                        handleIncomingMessages(incoming)
                    } catch (exception: Exception) {
                        AppLogger.log("Error collecting messages: ${exception.message}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("Error collecting messages: ${e.message}")
            }
        }
    }

    /**
     * Stop WebSocket connection and cancel message collection
     **/

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        webSocketClient.disconnect()

        //Cancel repoScope to avoid leaks if repository is disposed
        repoScope.cancel()
    }

    //--------------------
    // Message Handling
    //--------------------

    private val _messagesMap = MutableStateFlow<LinkedHashMap<String, ChatMessage>>(LinkedHashMap())


//    private fun handleIncomingMessages(messages: List<ChatMessage?>?) {
//        if (messages.isNullOrEmpty()) return
//
//        _messages.update { current ->
//            // 1. Use a LinkedHashMap to preserve order and allow O(1) updates
//            // We initialize it with the current items.
//            val map = LinkedHashMap<Int, ChatMessage>()
//            current?.filterNotNull()?.forEach { msg -> msg.id?.let { map[it] = msg } }
//
//            messages.filterNotNull().forEach { msg ->
//                val id = msg.id ?: return@forEach
//                if (msg.isDeleted == true) {
//                    map.remove(id)
//                } else {
//                    // This replaces if exists, or adds to the end if new
//                    map[id] = msg
//                }
//            }
//
//            // 2. Convert back to list and sort once if necessary
//            // Sorting is O(N log N), which is better than multiple O(N) shifts
//            map.values.sortedByDescending { it.timestamp }
//        }
//    }

//    private fun handleIncomingMessages(messages: List<ChatMessage?>?) {
//        if (messages.isNullOrEmpty()) return
//
//        _messages.update { current ->
//            val currentList = current?.toMutableList() ?: mutableListOf()
//
//            // Build a map of existing messages for O(1) lookup
//            val existingMap = currentList.associateBy { it?.id }
//            val validMessages = messages.filterNotNull()
//
//            // Separate messages by operation type in a single pass
//            val deletedIds = mutableSetOf<Int>()
//            val toUpdate = mutableListOf<ChatMessage>()
//            val toAdd = mutableListOf<ChatMessage>()
//
//            validMessages.forEach { msg ->
//                val id = msg.id
//                when {
//                    msg.isDeleted == true && id != null -> deletedIds.add(id)
//                    !msg.isDeleted!! && id != null -> {
//                        if (existingMap.containsKey(id)) {
//                            toUpdate.add(msg)
//                        } else {
//                            toAdd.add(msg)
//                        }
//                    }
//                }
//            }
//
//            // Apply operations
//            // 1. Remove deleted messages (single pass with removeIf)
//            if (deletedIds.isNotEmpty()) {
//                currentList.removeAll { it?.id in deletedIds }
//            }
//
//            // 2. Update existing messages (single pass)
//            if (toUpdate.isNotEmpty()) {
//                val updateMap = toUpdate.associateBy { it.id }
//                for (i in currentList.indices) {
//                    currentList[i]?.id?.let { id ->
//                        updateMap[id]?.let { updatedMsg ->
//                            currentList[i] = updatedMsg
//                        }
//                    }
//                }
//            }
//
//            // 3. Add new messages at the beginning
//            if (toAdd.isNotEmpty()) {
//                currentList.addAll(0, toAdd)
//            }
//
//            currentList
//        }
//    }

//    private fun handleIncomingMessages(messages: List<ChatMessage?>?) {
//        if (messages.isNullOrEmpty()) return
//
//        _messages.update { current ->
//            val currentMap = current
//                ?.filterNotNull()
//                ?.associateBy { it.id }
//                ?.toMutableMap()
//                ?: mutableMapOf()
//
//            for (msg in messages) {
//                msg?.id?.let {
//                    // INSERT OR REPLACE
//                    currentMap[it] = msg
//                }
//            }
//
//            // Sort if needed (newest first)
//            currentMap.values.sortedByDescending { it.timestamp }
//        }
//    }


    //---------------
    // Actions
    //---------------


    @Throws(Exception::class)
    suspend fun sendMessage(chatMessage: ChatMessage) {
        try {
            webSocketClient.sendMessage(chatMessage)
        } catch (exception: Exception) {
            AppLogger.log("Error sending message: ${exception.message}")
            throw exception
        }
    }

    @Throws(Exception::class)
    suspend fun editMessage(chatMessage: ChatMessage) {
        try {
            webSocketClient.editMessage(chatMessage)
        } catch (exception: Exception) {
            AppLogger.log("Error sending message: ${exception.message}")
            throw exception
        }
    }

    @Throws(Exception::class)
    suspend fun deleteMessage(chatMessage: ChatMessage) {
        try {
            webSocketClient.deleteMessage(chatMessage)
        } catch (exception: Exception) {
            AppLogger.log("Error sending message: ${exception.message}")
            throw exception
        }
    }


    @Throws(Exception::class)
    suspend fun fetchMessages(page: Int, pageSize: Int) {
        try {
            webSocketClient.fetchMessages(page, pageSize)
        } catch (exception: Exception) {
            AppLogger.log("Error fetching messages: ${exception.message}")
            throw exception

        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        AppLogger.log("Messages cleared")
    }

    fun reconnect() {
        stop()

        webSocketClient.connect()
        start()
    }


}