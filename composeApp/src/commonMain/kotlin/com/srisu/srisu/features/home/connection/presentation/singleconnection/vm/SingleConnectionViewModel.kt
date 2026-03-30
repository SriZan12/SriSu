package com.srisu.srisu.features.home.connection.presentation.singleconnection.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.cash.paging.PagingData
import app.cash.paging.filter
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.TabItem
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.SingleConnectionDTO
import com.srisu.srisu.core.data.remote.BasePagingSource
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.connection.coupleconnection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.SingleConnectionResponse
import com.srisu.srisu.features.home.connection.data.remote.mappers.toUser
import com.srisu.srisu.features.home.connection.presentation.singleconnection.state.ConnectionUIState
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import com.srisu.srisu.utils.Constants.ConnectionStatus.REJECTED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SingleConnectionViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _connectionUiState = MutableStateFlow(ConnectionUIState())
    val connectionUiState: StateFlow<ConnectionUIState> = _connectionUiState.asStateFlow()

    init {
        initTabs()
    }

    private val myCrushRefreshTrigger = MutableStateFlow(0)
    private val crushOnMeRefreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val myCrushPagingFlow =
        myCrushRefreshTrigger
            .flatMapLatest {
                createPagingFlow(
                    pageSize = 20,
                    fetch = { page ->
                        connectionRepository.getMyCrushList(pageSize = 20, page = page)
                    }
                )
            }
            .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val crushOnMePagingFlow =
        crushOnMeRefreshTrigger
            .flatMapLatest {
                createPagingFlow(
                    pageSize = 20,
                    fetch = { page ->
                        connectionRepository.getCrushOnMeRequest(pageSize = 20, page = page)
                    }
                )
            }
            .cachedIn(viewModelScope)

    val myCrushList: StateFlow<PagingData<SingleConnectionResponse.Result>> =
        combine(
            myCrushPagingFlow,
            _connectionUiState.map { it.cancelledRequestIds }
        ) { pagingData, cancelledIds ->
            pagingData.filter { item ->
                item.id?.toLong() !in cancelledIds
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PagingData.empty()
        )

    val crushOnMeList: StateFlow<PagingData<SingleConnectionResponse.Result>> =
        combine(
            crushOnMePagingFlow,
            _connectionUiState.map { it.acceptedRejectedIds }
        ) { pagingData, acceptedRejectedIds ->
            pagingData.filter { item ->
                item.id?.toLong() !in acceptedRejectedIds
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PagingData.empty()
        )

    private fun updateState(transform: (ConnectionUIState) -> ConnectionUIState) {
        _connectionUiState.update(transform)
    }

    private fun setBaseUiState(baseUIState: BaseUIState) {
        updateState { it.copy(baseUIState = baseUIState) }
    }

    private fun showLoading() {
        setBaseUiState(BaseUIState.Loading)
    }

    private fun showSuccess(message: String = "") {
        setBaseUiState(BaseUIState.Success(message))
    }

    private fun showError(
        message: String,
        errorType: String? = null
    ) {
        setBaseUiState(
            BaseUIState.Error(
                errorType = errorType,
                message = message
            )
        )
    }

    fun idleScreen() {
        setBaseUiState(BaseUIState.Idle)
    }

    private fun showNoInternetConnection(isOffline: Boolean) {
        setBaseUiState(BaseUIState.NoInternetConnection(isOffline = isOffline))
    }

    private fun isInternetAvailable(): Boolean {
        return connectivityObserver.isConnected.value
    }

    private fun initTabs() {
        updateState {
            it.copy(
                connectionTabList = listOf(
                    TabItem(title = "My Crush"),
                    TabItem(title = "Crush on Me")
                )
            )
        }
    }

    fun updateCurrentTab(tab: TabItem) {
        updateState { it.copy(currentTab = tab) }
    }

    private fun createPagingFlow(
        pageSize: Int,
        fetch: suspend (page: Int) -> ResultHandler<SingleConnectionResponse?>
    ): Flow<PagingData<SingleConnectionResponse.Result>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                prefetchDistance = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    var items: List<SingleConnectionResponse.Result?> = emptyList()

                    fetch(page)
                        .onSuccess { response, _ ->
                            items = response?.results ?: emptyList()
                        }
                        .onError { error, errorType ->
                            throw Exception("API Error: $error, Type: $errorType")
                        }

                    items
                }
            }
        ).flow
    }

    fun refreshMyCrushList() {
        myCrushRefreshTrigger.update { it + 1 }
    }

    fun refreshCrushOnMeList() {
        crushOnMeRefreshTrigger.update { it + 1 }
    }
    fun getUserProfile(userProfile: SingleConnectionResponse.Result.Receiver?): String? {
        return runCatching {
            Json.encodeToString(userProfile?.toUser())
        }.getOrNull()
    }

    fun updateCrushRequest(
        crushRequestId: Int?,
        senderNumber: String?,
        receiverNumber: String?,
        connectionStatus: String?
    ) {
        if (!isInternetAvailable()) {
            showNoInternetConnection(isOffline = true)
            return
        }

        val requestId = crushRequestId?.toLong() ?: return
        val status = connectionStatus.orEmpty()

        applyOptimisticUpdate(
            requestId = requestId,
            connectionStatus = status
        )

        viewModelScope.launch {
            runCatching {
                connectionRepository.updateCrushRequest(
                    crushRequestId = crushRequestId,
                    singleConnectionDTO = SingleConnectionDTO(
                        senderNumber = senderNumber,
                        receiverNumber = receiverNumber,
                        connectionStatus = connectionStatus
                    )
                )
            }.onSuccess { result ->
                result.onSuccess { _, _ ->
                    showSuccess("Request updated successfully")
                }.onError { error, errorType ->
                    rollbackRequest(
                        connectionStatus = status,
                        requestId = requestId,
                        message = error.toString(),
                        errorType = errorType.toString()
                    )
                }
            }.onFailure { exception ->
                rollbackRequest(
                    connectionStatus = status,
                    requestId = requestId,
                    message = exception.message ?: "Unknown error",
                    errorType = "Exception"
                )
            }
        }
    }

    private fun applyOptimisticUpdate(
        requestId: Long,
        connectionStatus: String
    ) {
        when (connectionStatus) {
            ACCEPTED, REJECTED -> {
                updateState { state ->
                    if (requestId in state.acceptedRejectedIds) {
                        state
                    } else {
                        state.copy(
                            acceptedRejectedIds = state.acceptedRejectedIds + requestId
                        )
                    }
                }
            }

            else -> {
                updateState { state ->
                    if (requestId in state.cancelledRequestIds) {
                        state
                    } else {
                        state.copy(
                            cancelledRequestIds = state.cancelledRequestIds + requestId
                        )
                    }
                }
            }
        }
    }

    private fun rollbackRequest(
        connectionStatus: String,
        requestId: Long,
        message: String,
        errorType: String
    ) {
        when (connectionStatus) {
            ACCEPTED, REJECTED -> {
                updateState { state ->
                    state.copy(
                        acceptedRejectedIds = state.acceptedRejectedIds - requestId,
                        baseUIState = BaseUIState.Error(
                            errorType = errorType,
                            message = message
                        )
                    )
                }
            }

            else -> {
                updateState { state ->
                    state.copy(
                        cancelledRequestIds = state.cancelledRequestIds - requestId,
                        baseUIState = BaseUIState.Error(
                            errorType = errorType,
                            message = message
                        )
                    )
                }
            }
        }
    }
}