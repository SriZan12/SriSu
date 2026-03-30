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
import com.srisu.srisu.features.home.connection.coupleconnection.domain.repository.ConnectionRepository
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.CoupleConnectionRequestResponse
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.state.CoupleConnectionListState
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
import kotlin.collections.contains
import kotlin.collections.map

class CoupleConnectionViewModel(
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _coupleConnectionListState: MutableStateFlow<CoupleConnectionListState> =
        MutableStateFlow(CoupleConnectionListState())

    val coupleConnectionListState = _coupleConnectionListState.asStateFlow()

    private fun initTabs() {
        _coupleConnectionListState.value = _coupleConnectionListState.value.copy(
            loveRequestTabList = listOf(
                TabItem(
                    title = "Requests"
                ),
                TabItem(
                    title = "Sent"
                )
            )
        )
    }

    // Separate flow for crush list paging
    private val sentLoveRequestFlow =
        MutableStateFlow<PagingData<CoupleConnectionRequestResponse.Result>>(PagingData.empty())

    private val loveRequestFlow =
        MutableStateFlow<PagingData<CoupleConnectionRequestResponse.Result>>(PagingData.empty())


    /*Exposes a reactive PagingData stream of crush list results
    Automatically filters out any items whose IDs are in the cancelledRequestIds set
    Updates in real-time when either the paging data OR the cancelled set changes*/

    val sentLoveRequests: StateFlow<PagingData<CoupleConnectionRequestResponse.Result>> =
        combine(
            // Combine the live paging data stream...
            sentLoveRequestFlow,

            // ...with the UI state's cancelled request IDs set
            _coupleConnectionListState.map { it.cancelledRequestIds }
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

    val loveRequests: StateFlow<PagingData<CoupleConnectionRequestResponse.Result>> =
        combine(
            loveRequestFlow,
            _coupleConnectionListState.map { it.rejectedIds }
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
    }

    fun updateCurrentTab(tab: TabItem) {
        _coupleConnectionListState.value = _coupleConnectionListState.value.copy(
            currentTab = tab
        )

    }

    fun CoupleConnectionRequestResponse.Result.Receiver.toUser(): User {
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
                        interest = it.interest, // map nested interest id
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


    fun getLoveRequestList() {
        val pagerFlow = Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    val resultHandler =
                        connectionRepository.getLoveRequests(pageSize = 20, page = page)

                    var items: List<CoupleConnectionRequestResponse.Result?> = emptyList()

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
                loveRequestFlow.value = it
            }
        }
    }

    fun getSentLoveRequestList() {

        val pagerFlow = Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                BasePagingSource { page ->
                    val resultHandler =
                        connectionRepository.getSentLoveRequests(pageSize = 20, page = page)

                    var items: List<CoupleConnectionRequestResponse.Result?> = emptyList()

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
                sentLoveRequestFlow.value = it
            }
        }
    }

    fun updateLoveRequest(
        loveRequestId: Int?,
        senderNumber: String?,
        receiverNumber: String?,
        connectionStatus: String?
    ) {
        val requestId = loveRequestId?.toLong() ?: return

        when (connectionStatus) {
            ACCEPTED, REJECTED -> markAsAcceptedOrRejected(requestId)
            else -> markAsCancelled(requestId)
        }

        viewModelScope.launch {
            try {
                connectionRepository.updateLoveRequest(
                    loveRequestId = loveRequestId,
                    coupleConnectionDTO = CoupleConnectionDTO(
                        senderNumber = senderNumber,
                        receiverNumber = receiverNumber,
                        connectionStatus = connectionStatus
                    ),
                ).onSuccess { _, _ ->
                    _coupleConnectionListState.update {
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

    fun getUserProfile(userProfile: CoupleConnectionRequestResponse.Result.Receiver?): String? {
        return Json.encodeToString(userProfile?.toUser())
    }

    private fun markAsAcceptedOrRejected(requestId: Long) {
        val currentIds = _coupleConnectionListState.value.rejectedIds
        if (requestId !in currentIds) {
            _coupleConnectionListState.update { state ->
                state.copy(rejectedIds = state.rejectedIds + requestId)
            }
        }
    }

    private fun markAsCancelled(requestId: Long) {
        val currentIds = _coupleConnectionListState.value.cancelledRequestIds
        if (requestId !in currentIds) {
            _coupleConnectionListState.update { state ->
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
                errorType
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
        _coupleConnectionListState.update { state ->
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
        _coupleConnectionListState.update { state ->
            state.copy(
                rejectedIds = state.rejectedIds - requestId,
                baseUIState = BaseUIState.Error(
                    errorType = errorType,
                    message = message
                )
            )
        }
    }
}