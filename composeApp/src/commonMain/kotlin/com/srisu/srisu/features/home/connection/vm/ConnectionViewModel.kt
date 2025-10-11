package com.srisu.srisu.features.home.connection.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.cash.paging.PagingData
import app.cash.paging.filter
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.dto.couple.SingleConnectionDTO
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.repository.connection.ConnectionRepository
import com.srisu.srisu.core.data.repository.profile.ProfileRepository
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.data.response.connection.MyCrushListResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.sin

class ConnectionViewModel(
    private val connectivityObserver: ConnectivityObserver,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _connectionUiState: MutableStateFlow<ConnectionUIState> =
        MutableStateFlow(ConnectionUIState())

    val connectionUiState = _connectionUiState.asStateFlow()

    // Separate flow for crush list paging
    private val myCrushPagingFlow =
        MutableStateFlow<PagingData<MyCrushListResponse.Result>>(PagingData.empty())

    /*Exposes a reactive PagingData stream of crush list results
    Automatically filters out any items whose IDs are in the cancelledRequestIds set
    Updates in real-time when either the paging data OR the cancelled set changes*/

    val myCrushList: StateFlow<PagingData<MyCrushListResponse.Result>> =
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

    fun MyCrushListResponse.Result.Receiver.toUser(): User {
        return User(
            bio = bio,
            city = city,
            country = country,
            createdDate = createdDate,
            dateJoined = dateJoined,
            dob = dob,
            email = email,
            firstName = firstName,
            fullName = fullName,
            gender = gender,
            id = id,
            isActive = isActive,
            isPhoneVerified = isPhoneVerified,
            isProfileComplete = isProfileComplete,
            isStaff = isStaff,
            isSuperuser = isSuperuser,
            lastName = lastName,
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

    fun getUserProfile(userProfile: MyCrushListResponse.Result.Receiver?): String? {
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

                    var items: List<MyCrushListResponse.Result?> = emptyList()

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


    fun cancelCrushRequest(
        crushRequestId: Int?,
        senderNumber: String?,
        receiverNumber: String?
    ) {
        val requestId = crushRequestId?.toLong() ?: return

        // Optimistic UI update
        if (requestId !in _connectionUiState.value.cancelledRequestIds) {
            _connectionUiState.update { state ->
                state.copy(cancelledRequestIds = state.cancelledRequestIds + requestId)
            }
        }

        viewModelScope.launch {
            try {
                connectionRepository.cancelCrushRequest(
                    crushRequestId = crushRequestId,
                    singleConnectionDTO = SingleConnectionDTO(
                        senderNumber = senderNumber,
                        receiverNumber = receiverNumber,
                        connectionStatus = "NOTHING"
                    )
                ).onSuccess { response, _ ->
                    AppLogger.log("CANCEL REQUEST SUCCESS = $response")
                    _connectionUiState.update {
                        it.copy(baseUIState = BaseUIState.Success("Request cancelled"))
                    }
                }.onError { error, errorType ->
                    AppLogger.log("CANCEL REQUEST ERROR = $error")
                    _connectionUiState.update { state ->
                        state.copy(
                            cancelledRequestIds = state.cancelledRequestIds - requestId,
                            baseUIState = BaseUIState.Error(
                                errorType = errorType.toString(),
                                message = error
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("EXCEPTION: ${e.message}")
                _connectionUiState.update { state ->
                    state.copy(
                        cancelledRequestIds = state.cancelledRequestIds - requestId,
                        baseUIState = BaseUIState.Error(
                            errorType = "",
                            message = e.message ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }


}