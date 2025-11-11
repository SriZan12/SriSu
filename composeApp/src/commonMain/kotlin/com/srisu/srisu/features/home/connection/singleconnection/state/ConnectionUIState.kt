package com.srisu.srisu.features.home.connection.singleconnection.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.connection.SingleConnectionResponse
import kotlinx.coroutines.flow.Flow

data class ConnectionUIState(
    val connectionTabList: List<Tab> = emptyList(),
    val currentTab: Tab? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,
    var myCrushList: Flow<PagingData<SingleConnectionResponse.Result>>? = null,
    val cancelledRequestIds: Set<Long> = emptySet(),
    val acceptedRejectedIds: Set<Long> = emptySet()

) {
    data class Tab(
        val title: String
    )
}
