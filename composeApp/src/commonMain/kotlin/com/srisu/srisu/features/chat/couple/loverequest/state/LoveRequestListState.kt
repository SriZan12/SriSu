package com.srisu.srisu.features.chat.couple.loverequest.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.components.TabItem
import com.srisu.srisu.core.data.response.connection.LoveRequestResponse
import kotlinx.coroutines.flow.Flow

data class LoveRequestListState(
    val loveRequestTabList: List<TabItem> = emptyList(),
    val currentTab: TabItem? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,
    var loveRequests: Flow<PagingData<LoveRequestResponse.Result>>? = null,
    val cancelledRequestIds: Set<Long> = emptySet(),
    val rejectedIds: Set<Long> = emptySet()
)