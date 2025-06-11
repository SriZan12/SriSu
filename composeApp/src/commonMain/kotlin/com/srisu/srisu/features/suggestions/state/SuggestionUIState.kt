package com.srisu.srisu.features.suggestions.state

import app.cash.paging.PagingData
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import kotlinx.coroutines.flow.Flow

data class SuggestionUIStates(
    var suggestions: Flow<PagingData<UserSuggestionResponse.Result>>? = null,
    val baseUIState: BaseUIState = BaseUIState.Idle,
)