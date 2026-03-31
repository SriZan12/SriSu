package com.srisu.srisu.features.home.connection.presentation.coupleconnection.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.TabItem
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.CoupleConnectionRequestResponse
import kotlinx.coroutines.flow.Flow

data class CoupleConnectionUiState(
    val loveRequestTabList: List<TabItem> = emptyList(),
    val currentTab: TabItem? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,
    var loveRequests: Flow<PagingData<CoupleConnectionRequestResponse.Result>>? = null,
    val handledRequestIds: Set<Long> = emptySet(),
    val rejectedIds: Set<Long> = emptySet()
)