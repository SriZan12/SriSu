package com.srisu.srisu.features.chat.matrix.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.ChatPartnerSource
import com.srisu.srisu.features.chat.matrix.MatrixChatRepository
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomOwner
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomState
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import com.srisu.srisu.features.chat.matrix.MATRIX_TEXT_MAX_PLAINTEXT_BYTES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SecureChatViewModel(
    private val repository: MatrixChatRepository,
    private val partners: ChatPartnerSource? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<MatrixChatRoomState?>(null)
    val state: StateFlow<MatrixChatRoomState?> = _state.asStateFlow()
    private val _partner = MutableStateFlow<ChatPartnerIdentity?>(null)
    /** Header identity; null while unknown, in which case the screen shows a neutral title. */
    val partner: StateFlow<ChatPartnerIdentity?> = _partner.asStateFlow()
    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()
    private val _composerValidationError = MutableStateFlow<String?>(null)
    val composerValidationError: StateFlow<String?> = _composerValidationError.asStateFlow()
    private var roomId: String? = null
    private var observer: Job? = null
    private var sendJob: Job? = null
    private var partnerJob: Job? = null
    private var roomOperation: Long = 0
    private val roomOwner = MatrixChatRoomOwner()

    fun openRoom(value: String) {
        if (roomId == value && observer?.isActive == true) return
        val operation = ++roomOperation
        sendJob?.cancel()
        sendJob = null
        roomId?.let { repository.closeRoom(it, roomOwner) }
        observer?.cancel()
        _messageInput.value = ""
        _composerValidationError.value = null
        _state.value = null
        partnerJob?.cancel()
        _partner.value = null
        roomId = value
        observer = viewModelScope.launch {
            repository.observeRoom(value).collect {
                if (roomId == value && roomOperation == operation) _state.value = it
            }
        }
        repository.openRoom(value, roomOwner)
        loadPartner(value, operation)
    }

    private fun loadPartner(room: String, operation: Long) {
        val source = partners ?: return
        partnerJob = viewModelScope.launch {
            val identity = try {
                source.partnerFor(room)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The header falls back to a neutral title; chat availability is unaffected.
                null
            }
            if (roomId == room && roomOperation == operation) _partner.value = identity
        }
    }

    fun updateMessageInput(value: String) {
        if (value.encodeToByteArray().size <= MATRIX_TEXT_MAX_PLAINTEXT_BYTES) {
            _messageInput.value = value
            _composerValidationError.value = null
        } else {
            _composerValidationError.value = "Message is too long."
        }
    }

    fun sendMessage() {
        if (sendJob?.isActive == true) return
        val activeRoom = roomId ?: return
        val operation = roomOperation
        val body = _messageInput.value.trim().takeIf(String::isNotEmpty) ?: return
        sendJob = viewModelScope.launch {
            if (repository.sendText(activeRoom, body).isSuccess &&
                roomId == activeRoom &&
                roomOperation == operation &&
                _messageInput.value.trim() == body
            ) {
                _messageInput.value = ""
            }
        }
    }

    fun retry() {
        roomId?.let { repository.retryRoom(it, roomOwner) }
    }

    fun retryMessage(transactionId: String) {
        val activeRoom = roomId ?: return
        val operation = roomOperation
        viewModelScope.launch {
            val result = repository.retryMessage(activeRoom, transactionId)
            if (roomId == activeRoom && roomOperation == operation) {
                _composerValidationError.value = result.fold(
                    onSuccess = { null },
                    onFailure = { "Message retry failed. Try again." },
                )
            }
        }
    }

    fun retryFailedMessages() {
        _state.value?.messages.orEmpty()
            .filter { it.delivery == MatrixMessageDelivery.FAILED }
            .mapNotNull { it.retryTransactionId }
            .forEach(::retryMessage)
    }

    fun closeRoom() {
        roomOperation += 1
        sendJob?.cancel()
        sendJob = null
        observer?.cancel()
        observer = null
        partnerJob?.cancel()
        partnerJob = null
        _partner.value = null
        roomId?.let { repository.closeRoom(it, roomOwner) }
        roomId = null
        _messageInput.value = ""
        _composerValidationError.value = null
        _state.value = null
    }

    override fun onCleared() {
        closeRoom()
        super.onCleared()
    }
}
