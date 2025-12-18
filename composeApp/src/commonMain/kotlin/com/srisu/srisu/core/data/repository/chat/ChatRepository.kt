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


    /**
     * Appends new messages while deduplicating by id (if available)
     * */

    private fun handleIncomingMessages(messages: List<ChatMessage?>?) {
        if (messages.isNullOrEmpty()) return

        _messages.update { current ->
            val currentList = current?.toMutableList() ?: mutableListOf()

            // dedupe by ID
            val existingIds = currentList.mapNotNull { it?.id }.toSet()

            val newMessages = messages.filter { msg ->
                msg?.id == null || msg.id !in existingIds
            }

            // Add at the beginning
            currentList.apply {
                addAll(0,newMessages)
            }
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
    suspend fun fetchMessages(page: Int, pageSize: Int) {
        try {
            webSocketClient.fetchMessages(page, pageSize)
        } catch (exception: Exception) {
            AppLogger.log("Error fetching messages: ${exception.message}")
            throw exception

        }
    }

    fun clearMessages(){
        _messages.value = emptyList()
        AppLogger.log("Messages cleared")
    }

    fun reconnect(){
        stop()

        webSocketClient.connect()
        start()
    }


}