package com.srisu.srisu.features.home.connection.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.repository.connection.ConnectionRepository
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.core.data.response.connection.MyCrushListResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _connectionUiState: MutableStateFlow<ConnectionUIState> =
        MutableStateFlow(ConnectionUIState())

    val connectionUiState = _connectionUiState.asStateFlow()


    init {
        initTabs()
        getMyCrushList()
    }


    private fun success(message: String = "") {
        _connectionUiState.value =
            _connectionUiState.value.copy(baseUIState = BaseUIState.Success(message))
    }

    private fun showLoading() {
        _connectionUiState.value =
            _connectionUiState.value.copy(baseUIState = BaseUIState.Loading)
    }

    private fun <T> showSuccessMessage(data: T? = null, message: String) {
        this._connectionUiState.value =
            this._connectionUiState.value.copy(
                baseUIState = BaseUIState.Success(
                    data = data,
                    message = message
                )
            )
    }

    private fun showErrorMessage(errorType: String?, message: String?) {
        this._connectionUiState.value =
            this._connectionUiState.value.copy(
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
    }

    fun idleScreen() {
        _connectionUiState.value = _connectionUiState.value.copy(baseUIState = BaseUIState.Idle)
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        _connectionUiState.value =
            _connectionUiState.value.copy(baseUIState = BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

    private fun isInternetAvailable(): Boolean {
        return connectivityObserver.isConnected.value
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

    fun getMyCrushList() {

        showLoading()

        val pagerFlow = Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    val resultHandler =
                        connectionRepository.getMyCrushList(pageSize = 20, page = page)

                    var items: List<MyCrushListResponse.Result?>? = emptyList()

                    AppLogger.log("resultHandler: ${resultHandler.result}")

                    resultHandler.onSuccess { response, _ ->
                        items = response?.results
                        idleScreen()
                    }.onError { error, errorType ->
                        idleScreen()
                        throw Exception("API Error: $error, Type: $errorType")
                    }

                    items
                }
            }).flow.cachedIn(viewModelScope)

        _connectionUiState.value = _connectionUiState.value.copy(myCrushList = pagerFlow)

    }
}