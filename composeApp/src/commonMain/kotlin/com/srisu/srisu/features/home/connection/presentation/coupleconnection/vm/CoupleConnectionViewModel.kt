package com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.cash.paging.PagingData
import app.cash.paging.filter
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.TabItem
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.dto.CoupleConnectionDTO
import com.srisu.srisu.core.data.remote.BasePagingSource
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.connection.coupleconnection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.data.remote.mappers.toUser
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.state.CoupleConnectionUiState
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import com.srisu.srisu.utils.Constants.ConnectionStatus.REJECTED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.collections.contains

class CoupleConnectionViewModel(
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _coupleConnectionUiState = MutableStateFlow(CoupleConnectionUiState())
    val coupleConnectionUiState: StateFlow<CoupleConnectionUiState> = _coupleConnectionUiState.asStateFlow()

    init {
        initTabs()
    }

    // Refresh triggers
    private val loveRequestRefreshTrigger = MutableStateFlow(0)
    private val sentLoveRequestRefreshTrigger = MutableStateFlow(0)

    //Paging flows
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loveRequestPagingFlow =
        loveRequestRefreshTrigger
            .flatMapLatest {
                createPagingFlow { page ->
                    connectionRepository.getLoveRequests(pageSize = 20, page = page)
                }
            }
            .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sentLoveRequestPagingFlow =
        sentLoveRequestRefreshTrigger
            .flatMapLatest {
                createPagingFlow { page ->
                    connectionRepository.getSentLoveRequests(pageSize = 20, page = page)
                }
            }
            .cachedIn(viewModelScope)

    // Filtered UI flows
    val loveRequests: StateFlow<PagingData<CoupleConnectionRequestResponse.Result>> =
        combine(
            loveRequestPagingFlow,
            _coupleConnectionUiState.map { it.handledRequestIds } // renamed
        ) { pagingData, handledIds ->
            pagingData.filter { it.id?.toLong() !in handledIds }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PagingData.empty()
        )

    val sentLoveRequests: StateFlow<PagingData<CoupleConnectionRequestResponse.Result>> =
        combine(
            sentLoveRequestPagingFlow,
            _coupleConnectionUiState.map { it.handledRequestIds }
        ) { pagingData, cancelledIds ->
            pagingData.filter { it.id?.toLong() !in cancelledIds }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PagingData.empty()
        )

    // ---------------------------
    // State helpers
    // ---------------------------

    private fun updateState(transform: (CoupleConnectionUiState) -> CoupleConnectionUiState) {
        _coupleConnectionUiState.update(transform)
    }

    private fun setBaseState(baseUIState: BaseUIState) {
        updateState { it.copy(baseUIState = baseUIState) }
    }

    private fun showSuccess(message: String) {
        setBaseState(BaseUIState.Success(message))
    }

    private fun showError(message: String, errorType: String? = null) {
        setBaseState(
            BaseUIState.Error(
                errorType = errorType,
                message = message
            )
        )
    }

    // ---------------------------
    // Tabs
    // ---------------------------

    private fun initTabs() {
        updateState {
            it.copy(
                loveRequestTabList = listOf(
                    TabItem("Requests"),
                    TabItem("Sent")
                )
            )
        }
    }

    fun updateCurrentTab(tab: TabItem) {
        updateState { it.copy(currentTab = tab) }
    }

    // ---------------------------
    // Paging builder
    // ---------------------------

    private fun createPagingFlow(
        fetch: suspend (page: Int) -> ResultHandler<CoupleConnectionRequestResponse?>
    ): Flow<PagingData<CoupleConnectionRequestResponse.Result>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    var items: List<CoupleConnectionRequestResponse.Result?> = emptyList()

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

    // ---------------------------
    // Refresh
    // ---------------------------

    fun refreshLoveRequests() {
        loveRequestRefreshTrigger.update { it + 1 }
    }

    fun refreshSentLoveRequests() {
        sentLoveRequestRefreshTrigger.update { it + 1 }
    }

    // ---------------------------
    // Actions
    // ---------------------------

    fun updateLoveRequest(
        loveRequestId: Int?,
        senderNumber: String?,
        receiverNumber: String?,
        connectionStatus: String?
    ) {
        val requestId = loveRequestId?.toLong() ?: return
        val status = connectionStatus.orEmpty()

        applyOptimisticUpdate(requestId, status)

        viewModelScope.launch {
            runCatching {
                connectionRepository.updateLoveRequest(
                    loveRequestId = loveRequestId,
                    coupleConnectionDTO = CoupleConnectionDTO(
                        senderNumber = senderNumber,
                        receiverNumber = receiverNumber,
                        connectionStatus = connectionStatus
                    )
                )
            }.onSuccess { result ->
                result.onSuccess { _, _ ->
                    showSuccess("Request updated successfully")

                    // refresh after action
//                    refreshLoveRequests()
//                    refreshSentLoveRequests()
                }.onError { error, errorType ->
                    rollback(requestId, status, error.toString(), errorType.toString())
                }
            }.onFailure {
                rollback(requestId, status, it.message ?: "Unknown error", "Exception")
            }
        }
    }

    private fun applyOptimisticUpdate(requestId: Long, status: String) {
        when (status) {
            ACCEPTED, REJECTED -> {
                updateState {
                    it.copy(
                        handledRequestIds = it.handledRequestIds + requestId
                    )
                }
            }

            else -> {
                updateState {
                    it.copy(
                        handledRequestIds = it.handledRequestIds + requestId
                    )
                }
            }
        }
    }

    private fun rollback(
        requestId: Long,
        status: String,
        message: String,
        errorType: String
    ) {
        when (status) {
            ACCEPTED, REJECTED -> {
                updateState {
                    it.copy(
                        handledRequestIds = it.handledRequestIds - requestId,
                        baseUIState = BaseUIState.Error(errorType, message)
                    )
                }
            }

            else -> {
                updateState {
                    it.copy(
                        handledRequestIds = it.handledRequestIds - requestId,
                        baseUIState = BaseUIState.Error(errorType, message)
                    )
                }
            }
        }
    }

    // ---------------------------
    // Profile mapping (TEMP)
    // ---------------------------

    fun getUserProfile(userProfile: CoupleConnectionRequestResponse.Result.Receiver?): String? {
        return runCatching {
            Json.encodeToString(userProfile?.toUser())
        }.getOrNull()
    }
}