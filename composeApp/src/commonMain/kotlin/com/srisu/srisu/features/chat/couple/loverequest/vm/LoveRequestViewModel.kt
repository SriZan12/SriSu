package com.srisu.srisu.features.chat.couple.loverequest.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.cash.paging.PagingData
import app.cash.paging.filter
import com.srisu.srisu.components.TabItem
import com.srisu.srisu.core.data.network.BasePagingSource
import com.srisu.srisu.core.data.repository.connection.ConnectionRepository
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.data.response.connection.LoveRequestResponse
import com.srisu.srisu.core.data.response.connection.SingleConnectionResponse
import com.srisu.srisu.features.chat.couple.loverequest.state.LoveRequestListState
import com.srisu.srisu.features.home.connection.state.ConnectionUIState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.contains
import kotlin.collections.map

class LoveRequestViewModel(
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _loveRequestListState: MutableStateFlow<LoveRequestListState> =
        MutableStateFlow(LoveRequestListState())

    val loveRequestListState = _loveRequestListState.asStateFlow()

    init {
        initTabs()
    }

    private fun initTabs() {
        _loveRequestListState.value = _loveRequestListState.value.copy(
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
        MutableStateFlow<PagingData<LoveRequestResponse.Result>>(PagingData.empty())

    private val loveRequestFlow =
        MutableStateFlow<PagingData<LoveRequestResponse.Result>>(PagingData.empty())


    /*Exposes a reactive PagingData stream of crush list results
    Automatically filters out any items whose IDs are in the cancelledRequestIds set
    Updates in real-time when either the paging data OR the cancelled set changes*/

    val sentLoveRequests: StateFlow<PagingData<LoveRequestResponse.Result>> =
        combine(
            // Combine the live paging data stream...
            sentLoveRequestFlow,

            // ...with the UI state's cancelled request IDs set
            _loveRequestListState.map { it.cancelledRequestIds }
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

    val loveRequests: StateFlow<PagingData<LoveRequestResponse.Result>> =
        combine(
            loveRequestFlow,
            _loveRequestListState.map { it.rejectedIds }
        ) { pagingData, cancelledIds ->
            pagingData.filter { it.id?.toLong() !in cancelledIds }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PagingData.empty()
            )

    fun updateCurrentTab(tab: TabItem) {
        _loveRequestListState.value = _loveRequestListState.value.copy(
            currentTab = tab
        )

    }

    fun LoveRequestResponse.Result.Receiver.toUser(): User {
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

                    var items: List<LoveRequestResponse.Result?> = emptyList()

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

                    var items: List<LoveRequestResponse.Result?> = emptyList()

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
}