package com.srisu.srisu.features.home.connection.presentation.singleconnection.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.TabItem
import com.srisu.srisu.features.home.connection.coupleconnection.data.remote.response.SingleConnectionResponse
import kotlinx.coroutines.flow.Flow

data class ConnectionUIState(
    val connectionTabList: List<TabItem> = emptyList(),
    val currentTab: TabItem? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,
    var myCrushList: Flow<PagingData<SingleConnectionResponse.Result>>? = null,
    val cancelledRequestIds: Set<Long> = emptySet(),
    val acceptedRejectedIds: Set<Long> = emptySet()

)
