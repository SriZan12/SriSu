package com.srisu.srisu.features.home.connection.singleconnection.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.cash.paging.PagingData
import app.cash.paging.filter
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.TabItem
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.repository.connection.ConnectionRepository
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.data.response.connection.SingleConnectionResponse
import com.srisu.srisu.features.home.connection.singleconnection.state.ConnectionUIState
import com.srisu.srisu.utils.ConnectivityObserver
import com.srisu.srisu.utils.Constants.ConnectionStatus.ACCEPTED
import com.srisu.srisu.utils.Constants.ConnectionStatus.REJECTED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SingleConnectionViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _connectionUiState: MutableStateFlow<ConnectionUIState> =
        MutableStateFlow(ConnectionUIState())

    val connectionUiState = _connectionUiState.asStateFlow()

    // Separate flow for crush list paging
    private val myCrushPagingFlow =
        MutableStateFlow<PagingData<SingleConnectionResponse.Result>>(PagingData.empty())

    private val crushOnMePagingFlow =
        MutableStateFlow<PagingData<SingleConnectionResponse.Result>>(PagingData.empty())


    /*Exposes a reactive PagingData stream of crush list results
    Automatically filters out any items whose IDs are in the cancelledRequestIds set
    Updates in real-time when either the paging data OR the cancelled set changes*/

    val myCrushList: StateFlow<PagingData<SingleConnectionResponse.Result>> =
        combine(
            // Combine the live paging data stream...
            myCrushPagingFlow,

            // ...with the UI state's cancelled request IDs set
            _connectionUiState.map { it.cancelledRequestIds }
        ) { pagingData, cancelledIds ->
            // Filter out any results whose ID is in the cancelled list
            pagingData.filter { it.id?.toLong() !in cancelledIds }
        }
            // Convert the combined Flow into a StateFlow for UI consumption
            .stateIn(
                scope = viewModelScope,

                // Keeps collecting the flow while there are active collectors (e.g., Composable)
                // and stops automatically when there are none for 5 seconds
                // This prevents unnecessary work when the screen is not visible
                started = SharingStarted.WhileSubscribed(5000),

                initialValue = PagingData.empty()
            )

    val crushOnMeList: StateFlow<PagingData<SingleConnectionResponse.Result>> =
        combine(
            crushOnMePagingFlow,
            _connectionUiState.map { it.acceptedRejectedIds }
        ) { pagingData, cancelledIds ->
            pagingData.filter { it.id?.toLong() !in cancelledIds }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PagingData.empty()
            )


    init {
        initTabs()
        getMyCrushList()
        getCrushOnMeList()
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
                TabItem(
                    title = "My Crush"
                ),
                TabItem(
                    title = "Crush on Me"
                )
            )
        )
    }

    fun updateCurrentTab(tab: TabItem) {
        _connectionUiState.value = _connectionUiState.value.copy(
            currentTab = tab
        )

    }

    fun SingleConnectionResponse.Result.Receiver.toUser(): User {
        return User(
            bio = bio,
            city = city,
            country = country,
            dob = dob,
            fullName = fullName,
            gender = gender,
            id = id,
            isPhoneVerified = isPhoneVerified,
            isProfileComplete = isProfileComplete,
            mood = mood,
            phoneNumber = phoneNumber,
            profilePhoto = profilePhoto,
            updatedDate = updatedDate,
            username = username,
            zodiacSign = zodiacSign,
            userInterests = userInterests?.map { receiverInterest ->
                receiverInterest?.let {
                    User.UserInterest(
                        id = it.id,
                        name = it.name,
                        user = it.user,
                        interest = it.interest?.interest, // map nested interest id
                        removed = it.removed
                    )
                }
            },
            userPhotos = userPhotos?.map { receiverPhoto ->
                receiverPhoto?.let {
                    User.UserPhoto(
                        createdDate = it.createdDate,
                        id = it.id,
                        photo = it.photo,
                        updatedDate = it.updatedDate,
                        user = it.user,
                        removed = it.removed
                    )
                }
            }
        )
    }

    fun getUserProfile(userProfile: SingleConnectionResponse.Result.Receiver?): String? {
        return Json.encodeToString(userProfile?.toUser())
    }


    fun getMyCrushList() {

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

                    var items: List<SingleConnectionResponse.Result?> = emptyList()

                    resultHandler.onSuccess { response, _ ->
                        items = response?.results ?: emptyList()
                    }.onError { error, errorType ->
                        throw Exception("API Error: $error, Type: $errorType")
                    }

                    items
                }
            }
        ).flow.cachedIn(viewModelScope)

        viewModelScope.launch {
            pagerFlow.collectLatest {
                myCrushPagingFlow.value = it
            }
        }
    }

    fun getCrushOnMeList() {

        val pagerFlow = Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    val resultHandler =
                        connectionRepository.getCrushOnMeRequest(pageSize = 20, page = page)

                    var items: List<SingleConnectionResponse.Result?> = emptyList()

                    resultHandler.onSuccess { response, _ ->
                        items = response?.results ?: emptyList()
                    }.onError { error, errorType ->
                        throw Exception("API Error: $error, Type: $errorType")
                    }

                    items
                }
            }
        ).flow.cachedIn(viewModelScope)

        viewModelScope.launch {
            pagerFlow.collectLatest {
                crushOnMePagingFlow.value = it
            }
        }
    }

    fun updateCrushRequest(
        crushRequestId: Int?,
        senderNumber: String?,
        receiverNumber: String?,
        connectionStatus: String?
    ) {
        val requestId = crushRequestId?.toLong() ?: return

        when (connectionStatus) {
            ACCEPTED, REJECTED -> markAsAcceptedOrRejected(requestId)
            else -> markAsCancelled(requestId)
        }

        viewModelScope.launch {
            try {
                connectionRepository.updateCrushRequest(
                    crushRequestId = crushRequestId,
                    singleConnectionDTO = SingleConnectionDTO(
                        senderNumber = senderNumber,
                        receiverNumber = receiverNumber,
                        connectionStatus = connectionStatus
                    )
                ).onSuccess { response, _ ->
                    _connectionUiState.update {
                        it.copy(baseUIState = BaseUIState.Success("Request updated successfully"))
                    }

                }.onError { error, errorType ->
                    rollbackRequests(
                        connectionStatus = connectionStatus ?: "",
                        requestId = requestId,
                        message = error.toString(),
                        errorType = errorType.toString()
                    )
                }

            } catch (e: Exception) {
                rollbackRequests(
                    connectionStatus = connectionStatus ?: "",
                    requestId = requestId,
                    message = e.message ?: "Unknown error",
                    errorType = "Exception"
                )
            }
        }
    }

    private fun markAsAcceptedOrRejected(requestId: Long) {
        val currentIds = _connectionUiState.value.acceptedRejectedIds
        if (requestId !in currentIds) {
            _connectionUiState.update { state ->
                state.copy(acceptedRejectedIds = state.acceptedRejectedIds + requestId)
            }
        }
    }

    private fun markAsCancelled(requestId: Long) {
        val currentIds = _connectionUiState.value.cancelledRequestIds
        if (requestId !in currentIds) {
            _connectionUiState.update { state ->
                state.copy(cancelledRequestIds = state.cancelledRequestIds + requestId)
            }
        }
    }

    private fun rollbackRequests(
        connectionStatus: String,
        requestId: Long,
        message: String,
        errorType: String
    ) {
        if (connectionStatus == ACCEPTED || connectionStatus == REJECTED) {
            rollbackAcceptedRejectedRequest(
                requestId,
                message,
                errorType.toString()
            )
        } else {
            rollbackCancelledRequest(requestId, message = message, errorType = errorType)
        }
    }

    private fun rollbackCancelledRequest(
        requestId: Long,
        message: String,
        errorType: String
    ) {
        _connectionUiState.update { state ->
            state.copy(
                cancelledRequestIds = state.cancelledRequestIds - requestId,
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
        }
    }

    private fun rollbackAcceptedRejectedRequest(
        requestId: Long,
        message: String,
        errorType: String
    ) {
        _connectionUiState.update { state ->
            state.copy(
                acceptedRejectedIds = state.acceptedRejectedIds - requestId,
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
        }
    }

}