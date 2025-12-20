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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.filter

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


    private fun handleIncomingMessages(messages: List<ChatMessage?>?) {
        if (messages.isNullOrEmpty()) return

        _messages.update { current ->
            val currentMap = current
                ?.filterNotNull()
                ?.associateBy { it.id }
                ?.toMutableMap()
                ?: mutableMapOf()

            for (msg in messages) {
                msg?.id?.let {
                    // INSERT OR REPLACE
                    currentMap[it] = msg
                }
            }

            // Sort if needed (newest first)
            currentMap.values.sortedByDescending { it.timestamp }
        }
    }



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