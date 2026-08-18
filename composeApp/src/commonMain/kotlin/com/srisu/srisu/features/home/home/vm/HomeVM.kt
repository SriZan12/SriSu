package com.srisu.srisu.features.home.home.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.auth.presentation.state.AuthUIStates
import com.srisu.srisu.features.home.home.state.HomeUiState
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class HomeVM(
    private val sessionStorage: SessionStorage
) : ViewModel() {

    private val _homeUiState = MutableStateFlow(value = HomeUiState())
    val homeUiState = _homeUiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeUiState()
    )

    init {
        getAndUpdateSession()
    }

    private fun updateState(transform: (HomeUiState) -> HomeUiState) {
        _homeUiState.value = transform(_homeUiState.value)
    }

    private fun updateSession(session: Session?) {
        updateState { it.copy(session = session) }
    }

    private fun updateIsEngaged(isEngaged: Boolean) {
        updateState { it.copy(isEngaged = isEngaged) }
    }

    private fun getAndUpdateSession() {
        val session = sessionStorage.getSession(sessionKey = SESSION_KEY) ?: return

        val sessionData = Json.decodeFromString<Session>(session)
        updateSession(session = sessionData)
        updateIsEngaged(isEngaged = sessionData.isEngaged == true)
        AppLogger.log("SESSIONS = ${session.let { Json.decodeFromString<Session>(it) }}")
    }


}