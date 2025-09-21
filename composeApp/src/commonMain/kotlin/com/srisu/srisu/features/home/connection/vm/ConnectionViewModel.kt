package com.srisu.srisu.features.home.connection.vm

import androidx.lifecycle.ViewModel
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val sessionStorage: SessionStorage,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _connectionUiState: MutableStateFlow<ConnectionUIState> =
        MutableStateFlow(ConnectionUIState())

    val connectionUiState = _connectionUiState.asStateFlow()


    init {
        initTabs()
    }

    private fun initTabs() {
        _connectionUiState.value = _connectionUiState.value.copy(
            connectionTabList = listOf(
                ConnectionUIState.Tab(
                    title = "My Crush"
                ),
                ConnectionUIState.Tab(
                    title = "Crush on Me"
                )
            )
        )
    }

    fun updateCurrentTab(tab: ConnectionUIState.Tab) {
        _connectionUiState.value = _connectionUiState.value.copy(
            currentTab = tab
        )

    }
}