package com.srisu.srisu.features.home.home.vm

import androidx.lifecycle.ViewModel
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.home.home.state.HomeUiState
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class HomeVM(
    private val sessionStorage: SessionStorage
) : ViewModel() {

    private val _homeUiState = MutableStateFlow(value = HomeUiState())
    val homeUiState = _homeUiState.asStateFlow()

    init {
        getAndUpdateSession()
    }

    private fun getAndUpdateSession() {
        val serializedSession = sessionStorage.getSession(sessionKey = SESSION_KEY) ?: return
        val isEngaged = runCatching {
            Json.decodeFromString<Session>(serializedSession).isEngaged == true
        }.onFailure {
            AppLogger.log("Failed to decode session: ${it.message}")
        }.getOrDefault(false)

        _homeUiState.update { it.copy(isEngaged = isEngaged) }
    }
}
