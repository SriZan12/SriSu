package com.srisu.srisu.features.home.connection.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.connection.MyCrushListResponse
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import kotlinx.coroutines.flow.Flow

data class ConnectionUIState(
    val connectionTabList: List<Tab> = emptyList(),
    val currentTab: Tab? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,
    var myCrushList: Flow<PagingData<MyCrushListResponse.Result>>? = null,


    ) {
    data class Tab(
        val title: String
    )
}
