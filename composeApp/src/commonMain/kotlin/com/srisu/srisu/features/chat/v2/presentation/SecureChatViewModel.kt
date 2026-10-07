package com.srisu.srisu.features.chat.v2.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.chat.v2.data.ChatV2Repository
import com.srisu.srisu.features.chat.v2.domain.ChatV2RoomState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SecureChatViewModel(private val repository: ChatV2Repository) : ViewModel() {
    private val _state = MutableStateFlow<ChatV2RoomState?>(null)
    val state: StateFlow<ChatV2RoomState?> = _state.asStateFlow()
    private var roomId: String? = null
    private var observer: Job? = null

    fun openRoom(value: String) {
        if (roomId == value && observer?.isActive == true) return
        roomId?.let(repository::deactivateRoom)
        observer?.cancel()
        roomId = value
        observer = viewModelScope.launch {
            repository.observeRoom(value).collect { _state.value = it }
        }
        repository.activateRoom(value)
    }

    fun retry() {
        roomId?.let(repository::retry)
    }

    fun retryFailedMessages() {
        roomId?.let(repository::retryFailedMessages)
    }

    fun closeRoom() {
        observer?.cancel()
        observer = null
        roomId?.let(repository::deactivateRoom)
        roomId = null
        _state.value = null
    }

    override fun onCleared() {
        closeRoom()
        super.onCleared()
    }
}
